import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from './api';
import { useStore } from './store';
import { ScheduledDueDialog, type DueDialogInfo } from './components/ScheduledDueDialog';
import { Dialog } from './components/ui';
import { AccountScreen } from './screens/AccountScreen';
import { AccountsScreen } from './screens/AccountsScreen';
import { CategoriesScreen } from './screens/CategoriesScreen';
import { CurrenciesScreen } from './screens/CurrenciesScreen';
import { FilterScreen } from './screens/FilterScreen';
import { FirstLaunchScreen } from './screens/FirstLaunchScreen';
import { MainScreen } from './screens/MainScreen';
import { ScheduledEditScreen } from './screens/ScheduledEditScreen';
import { ScheduledListScreen } from './screens/ScheduledListScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { TransactionScreen } from './screens/TransactionScreen';
import { buildPath } from '../shared/category';

export type Route =
  | { name: 'main' }
  | { name: 'account'; accountId: number }
  | { name: 'transaction'; accountId: number | null; transId: number | null }
  | { name: 'filter'; accountId: number | null }
  | { name: 'settings' }
  | { name: 'accounts' }
  | { name: 'categories' }
  | { name: 'currencies' }
  | { name: 'scheduled' }
  | { name: 'scheduledEdit'; bdId: number | null };

export interface Navigator {
  push: (route: Route) => void;
  back: () => void;
  resetTo: (route: Route) => void;
}

export function App(): React.ReactElement {
  const store = useStore();
  const [stack, setStack] = useState<Route[]>([{ name: 'main' }]);
  const [due, setDue] = useState<DueDialogInfo | null>(null);
  const [dueBusy, setDueBusy] = useState(false);

  const nav = useMemo<Navigator>(
    () => ({
      push: (route) => setStack((current) => [...current, route]),
      back: () => setStack((current) => (current.length > 1 ? current.slice(0, -1) : current)),
      resetTo: (route) => setStack([route])
    }),
    []
  );

  const checkDue = useCallback(async () => {
    if (!store.dbOpen || store.isReadOnly) {
      setDue(null);
      return;
    }
    const items = await api.dueScheduled();
    const next = items[0];
    if (!next) {
      setDue(null);
      return;
    }
    const accounts = store.accounts;
    const nameOf = (id: number): string => accounts.find((a) => a.id === id)?.name ?? '';
    setDue({
      scheduled: next,
      accountName: nameOf(next.accountId),
      toAccountName: next.toAccountId !== -1 ? nameOf(next.toAccountId) : null,
      categoryPath: buildPath(store.categories, next.categId),
      currency: store.accountCurrency(next.accountId),
      toCurrency: next.toAccountId !== -1 ? store.accountCurrency(next.toAccountId) : undefined
    });
  }, [store]);

  // Prompt for due schedules once the file is open, like MainActivity does on start.
  useEffect(() => {
    if (store.ready && store.dbOpen) void checkDue();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [store.ready, store.dbOpen, store.snapshot?.file.path]);

  useEffect(
    () =>
      api.onMenuCommand((command) => {
        if (command === 'refresh') void store.refresh().then(() => nav.resetTo({ name: 'main' }));
        if (command === 'sync') void store.run(() => api.syncNow());
        if (command === 'open') void store.run(() => api.openFileDialog()).then(() => nav.resetTo({ name: 'main' }));
        if (command === 'new') void store.run(() => api.createFile()).then(() => nav.resetTo({ name: 'main' }));
      }),
    [nav, store]
  );

  const runDueAction = async (action: () => Promise<{ ok: boolean; error?: string }>): Promise<void> => {
    setDueBusy(true);
    try {
      const result = await action();
      if (!result.ok) store.showToast(result.error ?? 'Action failed');
      await store.refresh();
      await checkDue();
    } finally {
      setDueBusy(false);
    }
  };

  if (!store.ready) {
    return <div className="center-page muted">Loading…</div>;
  }

  if (!store.dbOpen) {
    return (
      <>
        <FirstLaunchScreen onOpened={() => nav.resetTo({ name: 'main' })} />
        <Toast />
      </>
    );
  }

  const route = stack[stack.length - 1];

  return (
    <>
      {renderRoute(route, nav)}
      {store.syncState.kind === 'Conflict' && (
        <Dialog
          title="Sync conflict"
          dismissible={false}
          actions={
            <>
              <button type="button" className="btn" onClick={() => void store.run(() => api.conflictKeepLocal())}>
                Keep local
              </button>
              <button
                type="button"
                className="btn filled"
                onClick={() => void store.run(() => api.conflictKeepRemote())}
              >
                Use remote
              </button>
            </>
          }
        >
          <p style={{ margin: 0 }}>
            The remote file has been modified since your last sync. Which version do you want to keep?
          </p>
        </Dialog>
      )}
      {due && (
        <ScheduledDueDialog
          info={due}
          busy={dueBusy}
          onApprove={() => void runDueAction(() => api.approveScheduled(due.scheduled.bdId))}
          onCancelOnce={() => void runDueAction(() => api.cancelScheduledOnce(due.scheduled.bdId))}
          onDeleteScheduled={() => void runDueAction(() => api.deleteDueScheduled(due.scheduled.bdId))}
        />
      )}
      <Toast />
    </>
  );
}

function renderRoute(route: Route, nav: Navigator): React.ReactElement {
  switch (route.name) {
    case 'main':
      return <MainScreen nav={nav} />;
    case 'account':
      return <AccountScreen nav={nav} accountId={route.accountId} />;
    case 'transaction':
      return <TransactionScreen nav={nav} initialAccountId={route.accountId} editTransId={route.transId} />;
    case 'filter':
      return <FilterScreen nav={nav} initialAccountId={route.accountId} />;
    case 'settings':
      return <SettingsScreen nav={nav} />;
    case 'accounts':
      return <AccountsScreen nav={nav} />;
    case 'categories':
      return <CategoriesScreen nav={nav} />;
    case 'currencies':
      return <CurrenciesScreen nav={nav} />;
    case 'scheduled':
      return <ScheduledListScreen nav={nav} />;
    case 'scheduledEdit':
      return <ScheduledEditScreen nav={nav} editBdId={route.bdId} />;
  }
}

function Toast(): React.ReactElement | null {
  const store = useStore();
  if (!store.toast) return null;
  return (
    <div className="toast" role="status">
      <span className="grow">{store.toast}</span>
      <button type="button" className="btn" onClick={store.clearToast}>
        Dismiss
      </button>
    </div>
  );
}
