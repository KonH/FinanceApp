import React, { useState } from 'react';
import type { Navigator } from '../App';
import { api } from '../api';
import { useStore } from '../store';
import { ConfirmDialog, Dialog, Fab, ListItem, TextField, TopBar } from '../components/ui';
import { IconButton } from '../components/Icon';
import type { Currency } from '../../shared/types';

interface DialogState {
  id: number | null;
  name: string;
  symbol: string;
  pfxSymbol: string;
}

/** Port of `CurrenciesScreen` — CURRENCYFORMATS_V1 rows used by accounts. */
export function CurrenciesScreen({ nav }: { nav: Navigator }): React.ReactElement {
  const store = useStore();
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  const save = async (): Promise<void> => {
    if (!dialog) return;
    const existing = store.currencies.find((c) => c.id === dialog.id);
    const currency: Currency = {
      id: dialog.id ?? 0,
      name: dialog.name.trim(),
      currencySymbol: dialog.symbol.trim() || null,
      pfxSymbol: dialog.pfxSymbol.trim() || null,
      sfxSymbol: existing?.sfxSymbol ?? null,
      decimalPoint: existing?.decimalPoint ?? null,
      groupSeparator: existing?.groupSeparator ?? null,
      scale: existing?.scale ?? null
    };
    const okResult = await store.run(() => api.saveCurrency(currency, dialog.id === null));
    if (okResult) setDialog(null);
  };

  return (
    <div className="screen">
      <TopBar title="Currencies" onBack={nav.back} />

      <div className="content">
        {store.currencies.length === 0 && <div className="empty">No currencies in this file</div>}
        {store.currencies.map((currency) => (
          <ListItem
            key={currency.id}
            title={currency.name}
            subtitle={[currency.currencySymbol, currency.pfxSymbol].filter(Boolean).join(' · ')}
            trailing={
              <>
                <IconButton
                  icon="edit"
                  title="Edit"
                  disabled={store.isReadOnly}
                  onClick={() =>
                    setDialog({
                      id: currency.id,
                      name: currency.name,
                      symbol: currency.currencySymbol ?? '',
                      pfxSymbol: currency.pfxSymbol ?? ''
                    })
                  }
                />
                <IconButton
                  icon="delete"
                  title="Delete"
                  disabled={store.isReadOnly}
                  onClick={() => setDeleteId(currency.id)}
                />
              </>
            }
          />
        ))}
      </div>

      {!store.isReadOnly && (
        <Fab
          title="Add currency"
          onClick={() => setDialog({ id: null, name: '', symbol: '', pfxSymbol: '' })}
        />
      )}

      {dialog && (
        <Dialog
          title={dialog.id === null ? 'Add currency' : 'Edit currency'}
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
          <TextField
            label="Currency symbol"
            value={dialog.symbol}
            onChange={(symbol) => setDialog({ ...dialog, symbol })}
          />
          <TextField
            label="Prefix symbol"
            value={dialog.pfxSymbol}
            onChange={(pfxSymbol) => setDialog({ ...dialog, pfxSymbol })}
          />
        </Dialog>
      )}

      {deleteId !== null && (
        <ConfirmDialog
          title="Delete currency?"
          message="Currencies used by an account cannot be deleted."
          onCancel={() => setDeleteId(null)}
          onConfirm={() => {
            const id = deleteId;
            setDeleteId(null);
            void store.run(() => api.deleteCurrency(id));
          }}
        />
      )}
    </div>
  );
}
