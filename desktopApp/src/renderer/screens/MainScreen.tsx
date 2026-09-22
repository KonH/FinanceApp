import React, { useEffect, useMemo, useState } from 'react';
import type { Navigator } from '../App';
import { api } from '../api';
import { useStore } from '../store';
import { ListItem, TopBar } from '../components/ui';
import { IconButton } from '../components/Icon';
import { BaseCurrencySelect } from '../components/BaseCurrencySelect';
import { ensureRates } from '../rates';
import { computeBudgetUsage, type BudgetUsage } from '../../shared/balance';
import { currentYearMonth, daysInMonth, todayIso } from '../../shared/mmexDate';
import { currencyLabel, formatAmount } from '../../shared/format';
import { codeOf, convertSum, missingCodes, type RateNeed } from '../../shared/rates';
import type { Account, Currency } from '../../shared/types';

interface AccountGroup {
  type: string;
  accounts: Account[];
  totals: Array<[Currency, number]>;
}

/** Totals converted into the base currency: the grand total and one per account type. */
interface BaseTotals {
  total: number;
  groups: Map<string, number>;
}

/**
 * Port of `MainScreen` — accounts grouped by type with per-currency totals, or
 * totals converted into the persisted base currency at today's rate.
 */
export function MainScreen({ nav }: { nav: Navigator }): React.ReactElement {
  const store = useStore();
  const [budgetOpen, setBudgetOpen] = useState(false);
  const [usages, setUsages] = useState<BudgetUsage[]>([]);
  const [baseTotals, setBaseTotals] = useState<BaseTotals | null>(null);
  const [rateProgress, setRateProgress] = useState<number | null>(null);
  const [rateError, setRateError] = useState<string | null>(null);
  const [ratesAttempt, setRatesAttempt] = useState(0);

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

  const baseCurrencyId = store.snapshot?.settings.mainBaseCurrencyId ?? null;
  const baseCurrency = baseCurrencyId !== null ? store.currencyById.get(baseCurrencyId) : undefined;

  const baseCurrencyOptions = useMemo(() => {
    const ids = new Set(store.visibleAccounts.map((a) => a.currencyId));
    if (baseCurrencyId !== null) ids.add(baseCurrencyId);
    return store.currencies.filter((c) => ids.has(c.id)).sort((a, b) => a.name.localeCompare(b.name));
  }, [baseCurrencyId, store.currencies, store.visibleAccounts]);

  // Totals are current balances, so they convert at today's rate; account rows
  // keep their own currency.
  useEffect(() => {
    if (!baseCurrency) {
      setBaseTotals(null);
      setRateProgress(null);
      setRateError(null);
      return;
    }
    let cancelled = false;
    const today = todayIso();
    const baseCode = codeOf(baseCurrency);
    const groupAmounts = groups.map(
      (group) => [group.type, group.totals.map(([c, total]) => [codeOf(c), total] as [string, number])] as const
    );
    const allAmounts = groupAmounts.flatMap(([, amounts]) => amounts);
    const needs: RateNeed[] = [];
    for (const [code] of allAmounts) {
      if (code !== baseCode) needs.push({ code, date: today }, { code: baseCode, date: today });
    }

    void (async () => {
      const rates = await ensureRates(
        needs,
        today,
        (percent) => {
          setRateProgress(percent);
          setRateError(null);
        },
        () => cancelled
      );
      if (cancelled) return;
      const total = convertSum(rates.table, allAmounts, baseCode, today);
      const groupTotals = new Map<string, number>();
      if (total !== null) {
        for (const [type, amounts] of groupAmounts) {
          const converted = convertSum(rates.table, amounts, baseCode, today);
          if (converted !== null) groupTotals.set(type, converted);
        }
      }
      setBaseTotals(total !== null ? { total, groups: groupTotals } : null);
      setRateProgress(null);
      setRateError(
        total !== null
          ? null
          : rates.failed
            ? "Couldn't load exchange rates. Check the connection and retry."
            : `No exchange rate for ${missingCodes(rates.table, allAmounts.map(([code]) => code), baseCode, today).join(', ')}`
      );
    })();
    return () => {
      cancelled = true;
    };
  }, [baseCurrency, groups, ratesAttempt]);

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
                {baseTotals?.groups.has(group.type) ? (
                  <span className="muted small amount">
                    {money(baseTotals.groups.get(group.type)!, baseCurrency)}
                  </span>
                ) : (
                  group.totals.map(([currency, total]) => (
                    <span key={currency.id} className="muted small amount">
                      {money(total, currency)}
                    </span>
                  ))
                )}
              </div>
            )}
          </section>
        ))}
      </div>

      <div className="footer">
        {grandTotals.length > 0 && (
          <div className="totals" style={{ marginBottom: 6 }}>
            <span className="field-label">Total</span>
            {baseTotals ? (
              <span className="amount">{money(baseTotals.total, baseCurrency)}</span>
            ) : (
              grandTotals.map(([currency, total]) => (
                <span key={currency.id} className="amount">
                  {money(total, currency)}
                </span>
              ))
            )}
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

        <div style={{ marginTop: 8 }}>
          <BaseCurrencySelect
            currencies={baseCurrencyOptions}
            value={baseCurrencyId}
            progress={rateProgress}
            error={rateError}
            onChange={(id) => void store.run(() => api.setMainBaseCurrency(id))}
            onRetry={() => setRatesAttempt((n) => n + 1)}
          />
        </div>

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
