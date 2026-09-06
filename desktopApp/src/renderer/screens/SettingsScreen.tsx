import React, { useMemo, useState } from 'react';
import type { Navigator } from '../App';
import { api } from '../api';
import { useStore } from '../store';
import { Dialog, ListItem, Segmented, TextField, TopBar } from '../components/ui';
import { Icon, IconButton } from '../components/Icon';
import { CategoryPickerDialog } from '../components/CategoryPickerDialog';
import { buildPath } from '../../shared/category';
import { currencyLabel } from '../../shared/format';
import { TRANSACTION_TYPES, type AccessMode, type TransactionType } from '../../shared/types';

/** Port of `SettingsScreen` — Drive, file/access mode, data screens, defaults, budgets. */
export function SettingsScreen({ nav }: { nav: Navigator }): React.ReactElement {
  const store = useStore();
  const settings = store.snapshot?.settings;
  const file = store.snapshot?.file;
  const [pickerType, setPickerType] = useState<TransactionType | null>(null);
  const [clientDialogOpen, setClientDialogOpen] = useState(false);
  const [clientId, setClientId] = useState('');
  const [clientSecret, setClientSecret] = useState('');
  const [busy, setBusy] = useState(false);

  const budgetCurrencies = useMemo(() => {
    const inUse = new Map<number, string>();
    for (const account of store.accounts) {
      const currency = store.currencyById.get(account.currencyId);
      if (currency) inUse.set(currency.id, currency.name);
    }
    return store.currencies.filter((c) => inUse.has(c.id)).sort((a, b) => a.name.localeCompare(b.name));
  }, [store.accounts, store.currencies, store.currencyById]);

  return (
    <div className="screen">
      <TopBar title="Settings" onBack={nav.back} />

      <div className="content">
        <div className="section-title">About</div>
        <ListItem
          title="Version"
          trailing={<span className="muted">{store.appInfo?.version ?? ''}</span>}
        />
        <ListItem
          title="Runtime"
          trailing={
            <span className="muted small">
              Electron {store.appInfo?.electron} · Chromium {store.appInfo?.chrome}
            </span>
          }
        />

        <div className="section-title">Google Drive</div>
        {!store.driveStatus.hasClientCredentials && (
          <ListItem
            title="OAuth client not configured"
            subtitle="Desktop sign-in needs your own Google OAuth client id and secret"
            trailing={
              <button type="button" className="btn" onClick={() => setClientDialogOpen(true)}>
                Set up
              </button>
            }
          />
        )}
        {store.driveStatus.hasClientCredentials && !store.driveStatus.connected && (
          <ListItem
            title="Not connected"
            subtitle="Connect to sync with Google Drive"
            trailing={
              <>
                <button type="button" className="btn" onClick={() => setClientDialogOpen(true)}>
                  Change client
                </button>
                <button
                  type="button"
                  className="btn filled"
                  disabled={busy}
                  onClick={() =>
                    void (async () => {
                      setBusy(true);
                      const result = await api.driveConnect();
                      if (!result.ok) store.showToast(result.error ?? 'Google sign-in failed');
                      await store.refreshDrive();
                      setBusy(false);
                    })()
                  }
                >
                  Connect
                </button>
              </>
            }
          />
        )}
        {store.driveStatus.connected && (
          <>
            <ListItem
              title="Connected"
              subtitle={store.driveStatus.email ?? ''}
              trailing={
                <button
                  type="button"
                  className="btn"
                  onClick={() =>
                    void (async () => {
                      await api.driveDisconnect();
                      await store.refreshDrive();
                    })()
                  }
                >
                  Disconnect
                </button>
              }
            />
            {file?.driveFileId && (
              <ListItem
                title="Last sync"
                subtitle={file.lastSyncDisplay}
                trailing={
                  <IconButton
                    icon="sync"
                    title="Sync now"
                    onClick={() => void store.run(() => api.syncNow())}
                  />
                }
              />
            )}
          </>
        )}

        <div className="section-title">Database</div>
        <div className="content-pad stack">
          <div>
            <div className="field-label">{file?.sourceType}</div>
            <div className="muted small path-note">{file?.path ?? 'No file open'}</div>
          </div>
          <div>
            <div className="field-label">Access mode</div>
            <Segmented
              value={settings?.accessMode ?? 'READ_WRITE'}
              options={[
                { value: 'READ_WRITE' as AccessMode, label: 'Read-write' },
                { value: 'READ_ONLY' as AccessMode, label: 'Read-only' }
              ]}
              onChange={(mode) => void store.run(() => api.setAccessMode(mode))}
            />
          </div>
          <div className="row" style={{ justifyContent: 'flex-end', gap: 8 }}>
            <button type="button" className="btn outlined" onClick={() => void api.showInFolder()}>
              Show in folder
            </button>
            <button
              type="button"
              className="btn danger"
              onClick={() =>
                void store.run(() => api.closeFile()).then(() => nav.resetTo({ name: 'main' }))
              }
            >
              Close database
            </button>
          </div>
        </div>

        <div className="section-title">Data</div>
        <ListItem
          title="Accounts"
          trailing={<Icon name="chevron-right" />}
          onClick={() => nav.push({ name: 'accounts' })}
        />
        <ListItem
          title="Categories"
          trailing={<Icon name="chevron-right" />}
          onClick={() => nav.push({ name: 'categories' })}
        />
        <ListItem
          title="Currencies"
          trailing={<Icon name="chevron-right" />}
          onClick={() => nav.push({ name: 'currencies' })}
        />
        <ListItem
          title="Scheduled transactions"
          trailing={<Icon name="chevron-right" />}
          onClick={() => nav.push({ name: 'scheduled' })}
        />

        <div className="section-title">Options</div>
        {TRANSACTION_TYPES.map((type) => {
          const defaultId = settings?.defaultCategoryIds?.[type] ?? null;
          const useLatest = settings?.useLatestCategory?.[type] ?? false;
          return (
            <div key={type}>
              <div className="field-label" style={{ padding: '10px 16px 0' }}>
                {type}
              </div>
              <ListItem
                title="Default category"
                subtitle={defaultId !== null ? buildPath(store.categories, defaultId) : 'Not set'}
                onClick={() => setPickerType(type)}
                trailing={
                  defaultId !== null ? (
                    <IconButton
                      icon="close"
                      title="Clear default category"
                      onClick={() => void store.run(() => api.setDefaultCategory(type, null))}
                    />
                  ) : undefined
                }
              />
              <label className="checkbox-row">
                <input
                  type="checkbox"
                  checked={useLatest}
                  onChange={(event) =>
                    void store.run(() => api.setUseLatestCategory(type, event.target.checked))
                  }
                />
                <span>Use latest category for {type.toLowerCase()}</span>
              </label>
            </div>
          );
        })}

        {budgetCurrencies.length > 0 && (
          <>
            <div className="section-title">Budget</div>
            <div className="content-pad stack">
              <span className="muted small">Monthly spending limit per currency</span>
              {budgetCurrencies.map((currency) => (
                <BudgetField
                  key={currency.id}
                  label={currencyLabel(currency)}
                  value={settings?.budgets?.[String(currency.id)] ?? null}
                  onChange={(amount) => void store.run(() => api.setBudget(currency.id, amount))}
                />
              ))}
            </div>
          </>
        )}
        <div style={{ height: 24 }} />
      </div>

      {pickerType && (
        <CategoryPickerDialog
          categories={store.categories}
          onSelect={(category) => {
            const type = pickerType;
            setPickerType(null);
            void store.run(() => api.setDefaultCategory(type, category.id));
          }}
          onDismiss={() => setPickerType(null)}
        />
      )}

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
            Create an OAuth client of type “Desktop app” in the Google Cloud Console and enable the Drive
            API. The credentials are stored only on this machine.
          </p>
          <TextField label="Client ID" value={clientId} onChange={setClientId} />
          <TextField label="Client secret" value={clientSecret} onChange={setClientSecret} />
        </Dialog>
      )}
    </div>
  );
}

function BudgetField({
  label,
  value,
  onChange
}: {
  label: string;
  value: number | null;
  onChange: (amount: number | null) => void;
}): React.ReactElement {
  const [text, setText] = useState(value === null ? '' : String(value));
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      <input
        className="input"
        inputMode="decimal"
        value={text}
        onChange={(event) => {
          const next = event.target.value;
          setText(next);
          if (next.trim() === '') onChange(null);
          else if (Number.isFinite(Number(next))) onChange(Number(next));
        }}
      />
    </label>
  );
}
