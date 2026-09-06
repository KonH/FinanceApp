import React, { useState } from 'react';
import type { Navigator } from '../App';
import { api } from '../api';
import { useStore } from '../store';
import { ConfirmDialog, Dialog, Fab, ListItem, SelectField, TextField, TopBar } from '../components/ui';
import { IconButton } from '../components/Icon';
import { ACCOUNT_TYPES, type Account } from '../../shared/types';

interface DialogState {
  id: number | null;
  name: string;
  type: string;
  initialBal: string;
  currencyId: number | null;
}

/** Port of `AccountsScreen` — CRUD over ACCOUNTLIST_V1 plus main-screen visibility. */
export function AccountsScreen({ nav }: { nav: Navigator }): React.ReactElement {
  const store = useStore();
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const hidden = new Set(store.snapshot?.settings.hiddenAccountIds ?? []);

  const save = async (): Promise<void> => {
    if (!dialog) return;
    const account: Account = {
      id: dialog.id ?? 0,
      name: dialog.name.trim(),
      type: dialog.type,
      initialBal: Number(dialog.initialBal) || 0,
      currencyId: dialog.currencyId ?? store.currencies[0]?.id ?? 1,
      status: '',
      balance: 0
    };
    const okResult = await store.run(() => api.saveAccount(account, dialog.id === null));
    if (okResult) setDialog(null);
  };

  return (
    <div className="screen">
      <TopBar title="Accounts" onBack={nav.back} />

      <div className="content">
        {store.accounts.length === 0 && <div className="empty">No accounts yet</div>}
        {store.accounts.map((account) => (
          <ListItem
            key={account.id}
            title={account.name}
            subtitle={`${account.type} · ${store.currencyById.get(account.currencyId)?.name ?? 'No currency'}`}
            trailing={
              <>
                <IconButton
                  icon={hidden.has(account.id) ? 'eye-off' : 'eye'}
                  title={hidden.has(account.id) ? 'Show on main screen' : 'Hide from main screen'}
                  onClick={() => void store.run(() => api.toggleHiddenAccount(account.id))}
                />
                <IconButton
                  icon="edit"
                  title="Edit"
                  disabled={store.isReadOnly}
                  onClick={() =>
                    setDialog({
                      id: account.id,
                      name: account.name,
                      type: account.type,
                      initialBal: String(account.initialBal),
                      currencyId: account.currencyId
                    })
                  }
                />
                <IconButton
                  icon="delete"
                  title="Delete"
                  disabled={store.isReadOnly}
                  onClick={() => setDeleteId(account.id)}
                />
              </>
            }
          />
        ))}
      </div>

      {!store.isReadOnly && (
        <Fab
          title="Add account"
          onClick={() =>
            setDialog({
              id: null,
              name: '',
              type: 'Cash',
              initialBal: '0',
              currencyId: store.currencies[0]?.id ?? null
            })
          }
        />
      )}

      {dialog && (
        <Dialog
          title={dialog.id === null ? 'Add account' : 'Edit account'}
          onClose={() => setDialog(null)}
          actions={
            <>
              <button type="button" className="btn" onClick={() => setDialog(null)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn filled"
                disabled={!dialog.name.trim()}
                onClick={() => void save()}
              >
                Save
              </button>
            </>
          }
        >
          <TextField
            label="Name"
            value={dialog.name}
            autoFocus
            onChange={(name) => setDialog({ ...dialog, name })}
          />
          <SelectField
            label="Type"
            value={dialog.type}
            options={ACCOUNT_TYPES.map((type) => ({ value: type as string, label: type }))}
            onChange={(type) => setDialog({ ...dialog, type: type ?? 'Cash' })}
          />
          <TextField
            label="Initial balance"
            value={dialog.initialBal}
            onChange={(initialBal) => setDialog({ ...dialog, initialBal })}
          />
          <SelectField
            label="Currency"
            value={dialog.currencyId}
            options={store.currencies.map((c) => ({ value: c.id, label: c.name }))}
            onChange={(currencyId) => setDialog({ ...dialog, currencyId })}
          />
        </Dialog>
      )}

      {deleteId !== null && (
        <ConfirmDialog
          title="Delete account?"
          message="Transactions of this account stay in the file. This cannot be undone."
          onCancel={() => setDeleteId(null)}
          onConfirm={() => {
            const id = deleteId;
            setDeleteId(null);
            void store.run(() => api.deleteAccount(id));
          }}
        />
      )}
    </div>
  );
}
