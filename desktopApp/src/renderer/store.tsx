import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api, type AppInfo, type Result, type Snapshot } from './api';
import type { Account, Category, Currency, DriveStatus, SyncState } from '../shared/types';

interface AppStore {
  ready: boolean;
  snapshot: Snapshot | null;
  syncState: SyncState;
  driveStatus: DriveStatus;
  appInfo: AppInfo | null;
  /** Session-scoped, like `BalanceVisibilityStore` on Android. */
  balanceVisible: boolean;
  toggleBalanceVisible: () => void;
  toast: string | null;
  showToast: (message: string) => void;
  clearToast: () => void;
  refresh: () => Promise<void>;
  refreshDrive: () => Promise<void>;
  /** Runs a mutating call, surfaces its error and refreshes shared data. */
  run: (call: () => Promise<Result<unknown>>) => Promise<boolean>;
  accounts: Account[];
  visibleAccounts: Account[];
  categories: Category[];
  currencies: Currency[];
  currencyById: Map<number, Currency>;
  accountCurrency: (accountId: number) => Currency | undefined;
  isReadOnly: boolean;
  dbOpen: boolean;
}

const StoreContext = createContext<AppStore | null>(null);

const EMPTY_DRIVE: DriveStatus = { connected: false, email: null, hasClientCredentials: false };

export function AppProvider({ children }: { children: React.ReactNode }): React.ReactElement {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [syncState, setSyncState] = useState<SyncState>({ kind: 'Idle' });
  const [driveStatus, setDriveStatus] = useState<DriveStatus>(EMPTY_DRIVE);
  const [appInfo, setAppInfo] = useState<AppInfo | null>(null);
  const [balanceVisible, setBalanceVisible] = useState(true);
  const [toast, setToast] = useState<string | null>(null);
  const [ready, setReady] = useState(false);
  const toastTimer = useRef<number | null>(null);

  const refresh = useCallback(async () => {
    setSnapshot(await api.snapshot());
  }, []);

  const refreshDrive = useCallback(async () => {
    setDriveStatus(await api.driveStatus());
  }, []);

  const showToast = useCallback((message: string) => {
    setToast(message);
    if (toastTimer.current !== null) window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 6000);
  }, []);

  const run = useCallback(
    async (call: () => Promise<Result<unknown>>): Promise<boolean> => {
      try {
        const result = await call();
        if (!result?.ok) {
          showToast(result?.error ?? 'Operation failed');
          return false;
        }
        await refresh();
        return true;
      } catch (error) {
        showToast(error instanceof Error ? error.message : String(error));
        return false;
      }
    },
    [refresh, showToast]
  );

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const info = await api.appInfo();
      // Re-opens the last file (Drive or local) before the first snapshot.
      await api.bootstrap();
      const [snap, sync, drive] = await Promise.all([api.snapshot(), api.syncState(), api.driveStatus()]);
      if (cancelled) return;
      setAppInfo(info);
      setSnapshot(snap);
      setSyncState(sync);
      setDriveStatus(drive);
      setReady(true);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => api.onSyncChanged((state) => setSyncState(state)), []);

  const value = useMemo<AppStore>(() => {
    const accounts = snapshot?.accounts ?? [];
    const currencies = snapshot?.currencies ?? [];
    const currencyById = new Map(currencies.map((c) => [c.id, c]));
    const hidden = new Set(snapshot?.settings.hiddenAccountIds ?? []);
    const accountById = new Map(accounts.map((a) => [a.id, a]));
    return {
      ready,
      snapshot,
      syncState,
      driveStatus,
      appInfo,
      balanceVisible,
      toggleBalanceVisible: () => setBalanceVisible((v) => !v),
      toast,
      showToast,
      clearToast: () => setToast(null),
      refresh,
      refreshDrive,
      run,
      accounts,
      visibleAccounts: accounts.filter((a) => !hidden.has(a.id)),
      categories: snapshot?.categories ?? [],
      currencies,
      currencyById,
      accountCurrency: (accountId: number) => {
        const account = accountById.get(accountId);
        return account ? currencyById.get(account.currencyId) : undefined;
      },
      isReadOnly: snapshot?.settings.accessMode === 'READ_ONLY',
      dbOpen: snapshot?.dbOpen ?? false
    };
  }, [
    appInfo,
    balanceVisible,
    driveStatus,
    ready,
    refresh,
    refreshDrive,
    run,
    showToast,
    snapshot,
    syncState,
    toast
  ]);

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

export function useStore(): AppStore {
  const store = useContext(StoreContext);
  if (!store) throw new Error('useStore must be used inside AppProvider');
  return store;
}
