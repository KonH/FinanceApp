import fs from 'node:fs';
import path from 'node:path';
import { app } from 'electron';
import * as drive from './drive';
import { MmexDatabase, dbHolder } from './db/database';
import { settingsStore } from './settings';
import type { SyncState } from '../shared/types';

/**
 * Port of `DriveConflictDetector` — a conflict dialog is only warranted when the
 * local file has unuploaded changes AND the remote moved since the last sync.
 */
export type ConflictResult =
  | { kind: 'NoConflict' }
  | { kind: 'KeepLocalAndUpload' }
  | { kind: 'Conflict'; remoteTime: number; localSyncTime: number };

export function checkConflict(
  remoteTime: number,
  lastSyncTime: number | null,
  pendingUpload: boolean
): ConflictResult {
  if (pendingUpload && lastSyncTime !== null && remoteTime > lastSyncTime) {
    return { kind: 'Conflict', remoteTime, localSyncTime: lastSyncTime };
  }
  if (pendingUpload) return { kind: 'KeepLocalAndUpload' };
  return { kind: 'NoConflict' };
}

type Listener = (state: SyncState) => void;

/**
 * Port of `SyncCoordinator`: opens files, uploads after every write and when the
 * window goes away, and surfaces conflicts to the UI.
 */
class SyncCoordinator {
  private state: SyncState = { kind: 'Idle' };
  private listeners = new Set<Listener>();

  get current(): SyncState {
    return this.state;
  }

  onChange(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private set(state: SyncState): void {
    this.state = state;
    for (const listener of this.listeners) listener(state);
  }

  /** Working copy for Drive-backed files — the desktop twin of Android's cacheDir. */
  cacheFilePath(): string {
    return path.join(app.getPath('userData'), 'drive-cache', 'current.mmb');
  }

  async openLocal(filePath: string): Promise<void> {
    const db = await MmexDatabase.openExisting(filePath);
    dbHolder.open(db);
    settingsStore.patch({ localFilePath: filePath });
  }

  async downloadAndOpen(fileId: string): Promise<void> {
    this.set({ kind: 'Syncing' });
    const localFile = this.cacheFilePath();
    fs.mkdirSync(path.dirname(localFile), { recursive: true });
    try {
      const remoteTime = await drive.getRemoteModifiedTime(fileId);
      const lastSyncTime = settingsStore.all.lastSyncTime;
      const pendingUpload = settingsStore.all.pendingUpload;
      const conflict = checkConflict(remoteTime, lastSyncTime, pendingUpload);

      if (conflict.kind === 'Conflict' && fs.existsSync(localFile)) {
        // Same bytes as Drive → false conflict (an upload landed but the sync
        // bookkeeping was not persisted).
        const remoteMd5 = await drive.getRemoteMd5(fileId);
        const local = drive.localMd5(localFile);
        if (remoteMd5 && local && remoteMd5.toLowerCase() === local.toLowerCase()) {
          await this.openLocal(localFile);
          settingsStore.patch({ lastSyncTime: remoteTime, pendingUpload: false });
          this.set({ kind: 'Idle' });
          return;
        }
        await this.openLocal(localFile);
        this.set({ kind: 'Conflict', remoteTime: conflict.remoteTime, localSyncTime: conflict.localSyncTime });
        return;
      }

      if (conflict.kind === 'KeepLocalAndUpload' && fs.existsSync(localFile)) {
        await this.openLocal(localFile);
        await this.uploadCurrent();
        return;
      }

      await drive.download(fileId, localFile);
      await this.openLocal(localFile);
      settingsStore.patch({ lastSyncTime: remoteTime, pendingUpload: false });
      this.set({ kind: 'Idle' });
    } catch (error) {
      if (fs.existsSync(localFile)) {
        await this.openLocal(localFile);
        this.set({ kind: 'PendingSync' });
        return;
      }
      this.set({ kind: 'Error', message: (error as Error).message });
      throw error;
    }
  }

  /** Called after every write, and when the app is closing. */
  async uploadCurrent(): Promise<void> {
    if (!drive.isSignedIn()) return;
    const file = dbHolder.filePath;
    const fileId = settingsStore.all.driveFileId;
    if (!file || !fileId) return;
    this.set({ kind: 'Syncing' });
    try {
      const { modifiedTime } = await drive.upload(file, fileId);
      settingsStore.patch({ lastSyncTime: modifiedTime, pendingUpload: false });
      this.set({ kind: 'Idle' });
    } catch {
      settingsStore.patch({ pendingUpload: true });
      this.set({ kind: 'PendingSync' });
    }
  }

  async resolveConflictKeepLocal(): Promise<void> {
    await this.uploadCurrent();
  }

  async resolveConflictKeepRemote(fileId: string): Promise<void> {
    this.set({ kind: 'Syncing' });
    try {
      const localFile = this.cacheFilePath();
      const remoteTime = await drive.getRemoteModifiedTime(fileId);
      await drive.download(fileId, localFile);
      await this.openLocal(localFile);
      settingsStore.patch({ lastSyncTime: remoteTime, pendingUpload: false });
      this.set({ kind: 'Idle' });
    } catch (error) {
      this.set({ kind: 'Error', message: (error as Error).message });
    }
  }

  /** Marks the local file as needing an upload when Drive is configured. */
  markPendingIfDriveBacked(): void {
    if (settingsStore.all.driveFileId && drive.isSignedIn()) {
      settingsStore.patch({ pendingUpload: true });
      if (this.state.kind === 'Idle') this.set({ kind: 'PendingSync' });
    }
  }

  reset(): void {
    this.set({ kind: 'Idle' });
  }
}

export const syncCoordinator = new SyncCoordinator();
