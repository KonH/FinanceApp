import React, { useEffect, useMemo, useState } from 'react';
import type { Navigator } from '../App';
import { api } from '../api';
import { useStore } from '../store';
import { ListItem, TopBar } from '../components/ui';
import { IconButton } from '../components/Icon';
import { computeBudgetUsage, type BudgetUsage } from '../../shared/balance';
import { currentYearMonth, daysInMonth } from '../../shared/mmexDate';
import { currencyLabel, formatAmount } from '../../shared/format';
import type { Account, Currency } from '../../shared/types';

interface AccountGroup {
  type: string;
  accounts: Account[];
  totals: Array<[Currency, number]>;
}

/** Port of `MainScreen` — accounts grouped by type with per-currency totals. */
export function MainScreen({ nav }: { nav: Navigator }): React.ReactElement {
  const store = useStore();
  const [budgetOpen, setBudgetOpen] = useState(false);
  const [usages, setUsages] = useState<BudgetUsage[]>([]);

  const budgets = store.snapshot?.settings.budgets ?? {};

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const entries = Object.entries(budgets).map(([id, amount]) => [Number(id), amount] as const);
      if (entries.length === 0) {
        setUsages([]);
        return;
      }
      const expenses = await api.expensesByMonth(currentYearMonth());
      if (cancelled) return;
      const today = new Date();
      setUsages(
        computeBudgetUsage(
          Object.fromEntries(entries),
          expenses,
          today.getDate(),
          daysInMonth(today.getFullYear(), today.getMonth() + 1)
        )
      );
    })();
    return () => {
      cancelled = true;
    };
    // Re-runs on every snapshot, so spending bars follow new transactions too.
  }, [JSON.stringify(budgets), store.snapshot]);

  const groups = useMemo<AccountGroup[]>(() => {
    const byType = new Map<string, Account[]>();
    for (const account of store.visibleAccounts) {
      const list = byType.get(account.type);
      if (list) list.push(account);
      else byType.set(account.type, [account]);
    }
    return [...byType.entries()].map(([type, accounts]) => ({
      type,
      accounts,
      totals: totalsByCurrency(accounts, store.currencyById)
    }));
  }, [store.currencyById, store.visibleAccounts]);

  const grandTotals = useMemo(
    () => totalsByCurrency(store.visibleAccounts, store.currencyById),
    [store.currencyById, store.visibleAccounts]
  );

  const money = (value: number, currency: Currency | undefined): string =>
    store.balanceVisible ? formatAmount(value, currency) : '•••';

  const pending = store.syncState.kind === 'PendingSync';

  return (
    <div className="screen">
      <TopBar
        title="Accounts"
        actions={
          <>
            {store.isReadOnly && <span className="badge">Read-only</span>}
            <IconButton
              icon={store.balanceVisible ? 'eye' : 'eye-off'}
              title={store.balanceVisible ? 'Hide balances' : 'Show balances'}
              onClick={store.toggleBalanceVisible}
            />
            <IconButton
              icon="filter"
              title="Filter transactions"
              onClick={() => nav.push({ name: 'filter', accountId: null })}
            />
            <IconButton
              icon="sync"
              title={pending ? 'Sync (pending changes)' : 'Sync'}
              className={pending ? 'warn' : ''}
              onClick={() => void store.run(() => api.syncNow())}
            />
            <IconButton icon="settings" title="Settings" onClick={() => nav.push({ name: 'settings' })} />
          </>
        }
      />

      <div className="content">
        {groups.length === 0 && <div className="empty">No accounts yet — add one in Settings → Accounts.</div>}
        {groups.map((group) => (
          <section key={group.type}>
            <div className="group-header">{group.type}</div>
            {group.accounts.map((account) => (
              <ListItem
                key={account.id}
                title={account.name}
                trailing={
                  <span className="amount">{money(account.balance, store.currencyById.get(account.currencyId))}</span>
                }
                onClick={() => nav.push({ name: 'account', accountId: account.id })}
              />
            ))}
            {group.totals.length > 0 && (
              <div className="totals" style={{ padding: '6px 16px' }}>
                {group.totals.map(([currency, total]) => (
                  <span key={currency.id} className="muted small amount">
                    {money(total, currency)}
                  </span>
                ))}
              </div>
            )}
          </section>
        ))}
      </div>

      <div className="footer">
        {grandTotals.length > 0 && (
          <div className="totals" style={{ marginBottom: 6 }}>
            <span className="field-label">Total</span>
            {grandTotals.map(([currency, total]) => (
              <span key={currency.id} className="amount">
                {money(total, currency)}
              </span>
            ))}
          </div>
        )}

        {usages.length > 0 && (
          <>
            <button
              type="button"
              className={`chip ${budgetOpen ? 'selected' : ''}`}
              onClick={() => setBudgetOpen((open) => !open)}
            >
              Budget
            </button>
            {budgetOpen && (
              <div className="stack" style={{ marginTop: 8, gap: 6 }}>
                {usages.map((usage) => {
                  const currency = store.currencyById.get(usage.currencyId);
                  const over = usage.actualPercent > usage.expectedPercent;
                  return (
                    <div className="row" key={usage.currencyId}>
                      <span style={{ minWidth: 90 }}>
                        {currencyLabel(currency)} {Math.round(usage.actualPercent)}%
                      </span>
                      <div className={`progress ${over ? 'over' : ''}`}>
                        <span style={{ width: `${Math.max(0, Math.min(100, usage.actualPercent))}%` }} />
                      </div>
                      {over && (
                        <span className="muted small">expected {Math.round(usage.expectedPercent)}%</span>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </>
        )}

        {store.syncState.kind === 'Error' && (
          <div className="error-text small">Sync error: {store.syncState.message}</div>
        )}
      </div>
    </div>
  );
}

function totalsByCurrency(
  accounts: Account[],
  currencyById: Map<number, Currency>
): Array<[Currency, number]> {
  const sums = new Map<number, number>();
  for (const account of accounts) {
    sums.set(account.currencyId, (sums.get(account.currencyId) ?? 0) + account.balance);
  }
  return [...sums.entries()]
    .map(([currencyId, total]) => {
      const currency = currencyById.get(currencyId);
      return currency ? ([currency, total] as [Currency, number]) : null;
    })
    .filter((entry): entry is [Currency, number] => entry !== null);
}
