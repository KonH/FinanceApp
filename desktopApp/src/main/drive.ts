import crypto from 'node:crypto';
import fs from 'node:fs';
import http from 'node:http';
import { shell } from 'electron';
import { settingsStore } from './settings';
import type { DriveFileEntry, DriveStatus } from '../shared/types';

/**
 * Google Drive access for the desktop app.
 *
 * Android uses Play Services sign-in, which does not exist on desktop, so this
 * is the standard OAuth 2.0 "installed app" flow: a loopback redirect with PKCE.
 * The user supplies a Desktop OAuth client id/secret once (Settings → Google
 * Drive); tokens are kept in the app's settings file, never in the .mmb.
 *
 * Scope matches `DriveAuthManager`: full Drive, so a file created by any client
 * can be opened.
 */

const SCOPES = ['https://www.googleapis.com/auth/drive', 'https://www.googleapis.com/auth/userinfo.email'];
const AUTH_ENDPOINT = 'https://accounts.google.com/o/oauth2/v2/auth';
const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const USERINFO_ENDPOINT = 'https://www.googleapis.com/oauth2/v3/userinfo';
const API = 'https://www.googleapis.com/drive/v3';
const UPLOAD_API = 'https://www.googleapis.com/upload/drive/v3';

function base64Url(buffer: Buffer): string {
  return buffer.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function driveStatus(): DriveStatus {
  const drive = settingsStore.drive;
  return {
    connected: Boolean(drive.refreshToken),
    email: drive.email,
    hasClientCredentials: Boolean(drive.clientId && drive.clientSecret)
  };
}

export function isSignedIn(): boolean {
  return Boolean(settingsStore.drive.refreshToken);
}

export function setClientCredentials(clientId: string, clientSecret: string): void {
  settingsStore.patchDrive({
    clientId: clientId.trim() || null,
    clientSecret: clientSecret.trim() || null
  });
}

export function disconnect(): void {
  settingsStore.patchDrive({
    refreshToken: null,
    accessToken: null,
    accessTokenExpiry: null,
    email: null
  });
}

/** Runs the loopback OAuth flow; resolves once tokens are stored. */
export async function connect(): Promise<DriveStatus> {
  const { clientId, clientSecret } = settingsStore.drive;
  if (!clientId || !clientSecret) {
    throw new Error('Set a Google OAuth client id and secret first (Settings → Google Drive).');
  }

  const codeVerifier = base64Url(crypto.randomBytes(32));
  const codeChallenge = base64Url(crypto.createHash('sha256').update(codeVerifier).digest());
  const expectedState = base64Url(crypto.randomBytes(16));

  const { code, redirectUri } = await new Promise<{ code: string; redirectUri: string }>(
    (resolve, reject) => {
      const server = http.createServer((req, res) => {
        const url = new URL(req.url ?? '/', `http://127.0.0.1`);
        if (url.pathname !== '/') {
          res.writeHead(404).end();
          return;
        }
        const error = url.searchParams.get('error');
        const returnedState = url.searchParams.get('state');
        const returnedCode = url.searchParams.get('code');
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(
          `<html><body style="font-family:system-ui;padding:40px">
             <h2>${error ? 'Google Drive connection failed' : 'FinanceApp is connected'}</h2>
             <p>${error ? error : 'You can close this tab and return to the app.'}</p>
           </body></html>`
        );
        server.close();
        if (error) reject(new Error(`Google sign-in failed: ${error}`));
        else if (returnedState !== expectedState) reject(new Error('Google sign-in failed: state mismatch'));
        else if (!returnedCode) reject(new Error('Google sign-in failed: no authorization code'));
        else resolve({ code: returnedCode, redirectUri: `http://127.0.0.1:${port}` });
      });

      let port = 0;
      server.on('error', reject);
      server.listen(0, '127.0.0.1', () => {
        const address = server.address();
        if (address === null || typeof address === 'string') {
          reject(new Error('Could not start the local OAuth listener'));
          return;
        }
        port = address.port;
        const authUrl = new URL(AUTH_ENDPOINT);
        authUrl.searchParams.set('client_id', clientId);
        authUrl.searchParams.set('redirect_uri', `http://127.0.0.1:${port}`);
        authUrl.searchParams.set('response_type', 'code');
        authUrl.searchParams.set('scope', SCOPES.join(' '));
        authUrl.searchParams.set('access_type', 'offline');
        authUrl.searchParams.set('prompt', 'consent');
        authUrl.searchParams.set('state', expectedState);
        authUrl.searchParams.set('code_challenge', codeChallenge);
        authUrl.searchParams.set('code_challenge_method', 'S256');
        void shell.openExternal(authUrl.toString());
      });

      setTimeout(() => {
        server.close();
        reject(new Error('Google sign-in timed out'));
      }, 5 * 60 * 1000).unref();
    }
  );

  const tokenResponse = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      code,
      code_verifier: codeVerifier,
      grant_type: 'authorization_code',
      redirect_uri: redirectUri
    })
  });
  if (!tokenResponse.ok) {
    throw new Error(`Token exchange failed: ${tokenResponse.status} ${await tokenResponse.text()}`);
  }
  const tokens = (await tokenResponse.json()) as {
    access_token: string;
    refresh_token?: string;
    expires_in: number;
  };

  settingsStore.patchDrive({
    accessToken: tokens.access_token,
    accessTokenExpiry: Date.now() + tokens.expires_in * 1000,
    refreshToken: tokens.refresh_token ?? settingsStore.drive.refreshToken
  });

  try {
    const userInfo = await fetch(USERINFO_ENDPOINT, {
      headers: { Authorization: `Bearer ${tokens.access_token}` }
    });
    if (userInfo.ok) {
      const profile = (await userInfo.json()) as { email?: string };
      settingsStore.patchDrive({ email: profile.email ?? null });
    }
  } catch {
    // Email is cosmetic; a failure here must not fail the connection.
  }

  return driveStatus();
}

async function accessToken(): Promise<string> {
  const drive = settingsStore.drive;
  if (!drive.refreshToken) throw new Error('Not signed in to Google Drive');
  if (drive.accessToken && drive.accessTokenExpiry && drive.accessTokenExpiry - 60_000 > Date.now()) {
    return drive.accessToken;
  }
  if (!drive.clientId || !drive.clientSecret) throw new Error('Google OAuth client is not configured');

  const response = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: drive.clientId,
      client_secret: drive.clientSecret,
      refresh_token: drive.refreshToken,
      grant_type: 'refresh_token'
    })
  });
  if (!response.ok) {
    throw new Error(`Token refresh failed: ${response.status} ${await response.text()}`);
  }
  const tokens = (await response.json()) as { access_token: string; expires_in: number };
  settingsStore.patchDrive({
    accessToken: tokens.access_token,
    accessTokenExpiry: Date.now() + tokens.expires_in * 1000
  });
  return tokens.access_token;
}

async function driveFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const token = await accessToken();
  const headers = new Headers(init.headers);
  headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(url, { ...init, headers });
  if (!response.ok) {
    throw new Error(`Drive request failed: ${response.status} ${await response.text()}`);
  }
  return response;
}

export async function listMmbFiles(): Promise<DriveFileEntry[]> {
  const params = new URLSearchParams({
    q: "trashed = false and mimeType != 'application/vnd.google-apps.folder'",
    fields: 'files(id,name,modifiedTime)',
    orderBy: 'modifiedTime desc',
    pageSize: '200',
    spaces: 'drive'
  });
  const response = await driveFetch(`${API}/files?${params.toString()}`);
  const body = (await response.json()) as { files?: DriveFileEntry[] };
  return (body.files ?? []).filter((f) => f.name.toLowerCase().endsWith('.mmb'));
}

export async function findFileIdByName(name: string): Promise<string | null> {
  const escaped = name.replace(/'/g, "\\'");
  const params = new URLSearchParams({
    q: `name='${escaped}' and trashed=false`,
    fields: 'files(id)',
    spaces: 'drive'
  });
  const response = await driveFetch(`${API}/files?${params.toString()}`);
  const body = (await response.json()) as { files?: Array<{ id: string }> };
  return body.files?.[0]?.id ?? null;
}

export async function getMetadata(
  fileId: string,
  fields: string
): Promise<{ modifiedTime?: string; md5Checksum?: string; name?: string }> {
  const response = await driveFetch(`${API}/files/${encodeURIComponent(fileId)}?fields=${fields}`);
  return (await response.json()) as { modifiedTime?: string; md5Checksum?: string; name?: string };
}

export async function getRemoteModifiedTime(fileId: string): Promise<number> {
  const meta = await getMetadata(fileId, 'modifiedTime');
  return meta.modifiedTime ? Date.parse(meta.modifiedTime) : 0;
}

export async function getRemoteMd5(fileId: string): Promise<string | null> {
  const meta = await getMetadata(fileId, 'md5Checksum');
  return meta.md5Checksum ?? null;
}

export async function download(fileId: string, destPath: string): Promise<void> {
  const response = await driveFetch(`${API}/files/${encodeURIComponent(fileId)}?alt=media`);
  const buffer = Buffer.from(await response.arrayBuffer());
  fs.writeFileSync(destPath, buffer);
}

/** Uploads the file; creates it when `fileId` is null. Returns id + modifiedTime. */
export async function upload(
  srcPath: string,
  fileId: string | null
): Promise<{ fileId: string; modifiedTime: number }> {
  const content = fs.readFileSync(srcPath);
  if (fileId) {
    const response = await driveFetch(
      `${UPLOAD_API}/files/${encodeURIComponent(fileId)}?uploadType=media&fields=id,modifiedTime`,
      { method: 'PATCH', body: new Uint8Array(content), headers: { 'Content-Type': 'application/octet-stream' } }
    );
    const body = (await response.json()) as { id: string; modifiedTime: string };
    return { fileId: body.id, modifiedTime: Date.parse(body.modifiedTime) };
  }

  const boundary = `financeapp-${crypto.randomUUID()}`;
  const metadata = JSON.stringify({ name: srcPath.split(/[\\/]/).pop() });
  const body = Buffer.concat([
    Buffer.from(`--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${metadata}\r\n`),
    Buffer.from(`--${boundary}\r\nContent-Type: application/octet-stream\r\n\r\n`),
    content,
    Buffer.from(`\r\n--${boundary}--\r\n`)
  ]);
  const response = await driveFetch(`${UPLOAD_API}/files?uploadType=multipart&fields=id,modifiedTime`, {
    method: 'POST',
    body: new Uint8Array(body),
    headers: { 'Content-Type': `multipart/related; boundary=${boundary}` }
  });
  const created = (await response.json()) as { id: string; modifiedTime: string };
  return { fileId: created.id, modifiedTime: Date.parse(created.modifiedTime) };
}

export function localMd5(filePath: string): string | null {
  try {
    return crypto.createHash('md5').update(fs.readFileSync(filePath)).digest('hex');
  } catch {
    return null;
  }
}
