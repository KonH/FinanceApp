import React, { useState } from 'react';
import { api } from '../api';
import { useStore } from '../store';
import { Dialog, TextField } from '../components/ui';
import type { DriveFileEntry } from '../../shared/types';

/** Port of `FirstLaunchScreen`: create, open local, or pick a file from Drive. */
export function FirstLaunchScreen({ onOpened }: { onOpened: () => void }): React.ReactElement {
  const store = useStore();
  const [busy, setBusy] = useState(false);
  const [clientDialogOpen, setClientDialogOpen] = useState(false);
  const [clientId, setClientId] = useState('');
  const [clientSecret, setClientSecret] = useState('');
  const [driveFiles, setDriveFiles] = useState<DriveFileEntry[] | null>(null);

  const withBusy = async (action: () => Promise<void>): Promise<void> => {
    setBusy(true);
    try {
      await action();
    } finally {
      setBusy(false);
    }
  };

  const openIfReady = async (): Promise<void> => {
    await store.refresh();
    const snapshot = await api.snapshot();
    if (snapshot.dbOpen) onOpened();
  };

  return (
    <div className="center-page">
      <h1 style={{ margin: 0 }}>FinanceApp</h1>
      <p className="muted" style={{ marginTop: 0 }}>
        Open a MoneyManagerEx <code>.mmb</code> file, or create a new one.
      </p>

      <div className="stack" style={{ width: 360, marginTop: 16 }}>
        <button
          type="button"
          className="btn filled block"
          disabled={busy}
          onClick={() =>
            void withBusy(async () => {
              const result = await api.createFile();
              if (!result.ok) store.showToast(result.error ?? 'Could not create the file');
              await openIfReady();
            })
          }
        >
          Create new file
        </button>

        <button
          type="button"
          className="btn outlined block"
          disabled={busy}
          onClick={() =>
            void withBusy(async () => {
              const result = await api.openFileDialog();
              if (!result.ok) store.showToast(result.error ?? 'Could not open the file');
              await openIfReady();
            })
          }
        >
          Open local file
        </button>

        <hr className="divider" />

        {!store.driveStatus.hasClientCredentials && (
          <button type="button" className="btn outlined block" onClick={() => setClientDialogOpen(true)}>
            Set up Google Drive…
          </button>
        )}

        {store.driveStatus.hasClientCredentials && !store.driveStatus.connected && (
          <button
            type="button"
            className="btn outlined block"
            disabled={busy}
            onClick={() =>
              void withBusy(async () => {
                const result = await api.driveConnect();
                if (!result.ok) store.showToast(result.error ?? 'Google sign-in failed');
                await store.refreshDrive();
              })
            }
          >
            Connect Google Drive
          </button>
        )}

        {store.driveStatus.connected && (
          <button
            type="button"
            className="btn outlined block"
            disabled={busy}
            onClick={() =>
              void withBusy(async () => {
                const result = await api.driveListFiles();
                if (!result.ok) {
                  store.showToast(result.error ?? 'Could not list Drive files');
                  return;
                }
                setDriveFiles(result.value ?? []);
              })
            }
          >
            Open file from Drive
          </button>
        )}

        {store.driveStatus.connected && (
          <div className="muted small">Signed in as {store.driveStatus.email ?? 'Google account'}</div>
        )}
      </div>

      {busy && <div className="muted">Working…</div>}

      {clientDialogOpen && (
        <Dialog
          title="Google Drive setup"
          onClose={() => setClientDialogOpen(false)}
          actions={
            <>
              <button type="button" className="btn" onClick={() => setClientDialogOpen(false)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn filled"
                disabled={!clientId.trim() || !clientSecret.trim()}
                onClick={() =>
                  void (async () => {
                    await api.driveSetClient(clientId, clientSecret);
                    await store.refreshDrive();
                    setClientDialogOpen(false);
                  })()
                }
              >
                Save
              </button>
            </>
          }
        >
          <p className="muted small" style={{ marginTop: 0 }}>
            Desktop sign-in uses your own Google OAuth client (Google Cloud Console → Credentials → OAuth
            client ID → Desktop app). Enable the Drive API for that project. The id and secret stay on this
            machine.
          </p>
          <TextField label="Client ID" value={clientId} onChange={setClientId} />
          <TextField label="Client secret" value={clientSecret} onChange={setClientSecret} />
        </Dialog>
      )}

      {driveFiles && (
        <Dialog
          title="Open from Google Drive"
          onClose={() => setDriveFiles(null)}
          actions={
            <button type="button" className="btn" onClick={() => setDriveFiles(null)}>
              Cancel
            </button>
          }
        >
          {driveFiles.length === 0 && <div className="empty">No .mmb files found in your Drive</div>}
          {driveFiles.map((file) => (
            <button
              key={file.id}
              type="button"
              className="btn outlined wide-left"
              onClick={() =>
                void withBusy(async () => {
                  setDriveFiles(null);
                  const result = await api.driveOpen(file.id);
                  if (!result.ok) store.showToast(result.error ?? 'Could not open the Drive file');
                  await openIfReady();
                })
              }
            >
              {file.name}
              <span className="muted small" style={{ marginLeft: 8 }}>
                {new Date(file.modifiedTime).toLocaleString()}
              </span>
            </button>
          ))}
        </Dialog>
      )}
    </div>
  );
}
