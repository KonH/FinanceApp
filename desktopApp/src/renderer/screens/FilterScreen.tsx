import React, { useEffect, useMemo, useState } from 'react';
import type { Navigator } from '../App';
import { api } from '../api';
import { useStore } from '../store';
import { Field, SelectField, TopBar } from '../components/ui';
import { Icon, IconButton } from '../components/Icon';
import { CategoryPickerDialog } from '../components/CategoryPickerDialog';
import { TransactionRow } from '../components/TransactionRow';
import { buildPath } from '../../shared/category';
import { formatAmount } from '../../shared/format';
import { datePart } from '../../shared/mmexDate';
import {
  EMPTY_FILTER,
  TRANSACTION_TYPES,
  type Currency,
  type Transaction,
  type TransactionFilter
} from '../../shared/types';

interface FlowSummary {
  currency: Currency;
  income: number;
  expense: number;
  net: number;
}

/** Port of `FilterScreen` — cross-file search with a per-currency net flow. */
export function FilterScreen({
  nav,
  initialAccountId
}: {
  nav: Navigator;
  initialAccountId: number | null;
}): React.ReactElement {
  const store = useStore();
  const [filter, setFilter] = useState<TransactionFilter>({ ...EMPTY_FILTER, accountId: initialAccountId });
  const [expanded, setExpanded] = useState(true);
  const [all, setAll] = useState<Transaction[]>([]);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [query, setQuery] = useState('');

  useEffect(() => {
    void api.allTransactions().then(setAll);
  }, [store.snapshot]);

  const categoryPaths = useMemo(() => {
    const map = new Map<number, string>();
    for (const category of store.categories) map.set(category.id, buildPath(store.categories, category.id));
    return map;
  }, [store.categories]);

  const patch = (change: Partial<TransactionFilter>): void => setFilter((current) => ({ ...current, ...change }));

  const filtered = useMemo(() => {
    const text = query.trim().toLowerCase();
    return all.filter((tx) => {
      const currency = store.accountCurrency(tx.accountId);
      if (filter.type !== null && tx.type !== filter.type) return false;
      if (
        filter.accountId !== null &&
        tx.accountId !== filter.accountId &&
        tx.toAccountId !== filter.accountId
      ) {
        return false;
      }
      if (filter.currencyId !== null && currency?.id !== filter.currencyId) return false;
      if (filter.categoryId !== null && tx.categId !== filter.categoryId) return false;
      if (filter.startDate !== null && datePart(tx.transDate) < filter.startDate) return false;
      if (filter.endDate !== null && datePart(tx.transDate) > filter.endDate) return false;
      if (filter.minAmount !== null && tx.transAmount < filter.minAmount) return false;
      if (filter.maxAmount !== null && tx.transAmount > filter.maxAmount) return false;
      if (text) {
        const haystack = [
          tx.notes ?? '',
          String(tx.transAmount),
          tx.categId !== null ? categoryPaths.get(tx.categId) ?? '' : '',
          store.accounts.find((a) => a.id === tx.accountId)?.name ?? ''
        ]
          .join(' ')
          .toLowerCase();
        if (!haystack.includes(text)) return false;
      }
      return true;
    });
  }, [all, categoryPaths, filter, query, store]);

  const summaries = useMemo<FlowSummary[]>(() => {
    const income = new Map<number, number>();
    const expense = new Map<number, number>();
    const currencies = new Map<number, Currency>();
    for (const tx of filtered) {
      const currency = store.accountCurrency(tx.accountId);
      if (!currency) continue;
      currencies.set(currency.id, currency);
      if (tx.type === 'Deposit') income.set(currency.id, (income.get(currency.id) ?? 0) + tx.transAmount);
      if (tx.type === 'Withdrawal') expense.set(currency.id, (expense.get(currency.id) ?? 0) + tx.transAmount);
    }
    return [...new Set([...income.keys(), ...expense.keys()])]
      .map((id) => {
        const currency = currencies.get(id);
        if (!currency) return null;
        const inc = income.get(id) ?? 0;
        const exp = expense.get(id) ?? 0;
        return { currency, income: inc, expense: exp, net: inc - exp };
      })
      .filter((entry): entry is FlowSummary => entry !== null);
  }, [filtered, store]);

  const categoryLabel =
    filter.categoryId !== null ? buildPath(store.categories, filter.categoryId) || 'All categories' : 'All categories';

  return (
    <div className="screen">
      <TopBar
        title="Filter"
        onBack={nav.back}
        actions={
          <IconButton
            icon={expanded ? 'chevron-down' : 'chevron-right'}
            title={expanded ? 'Collapse filters' : 'Expand filters'}
            onClick={() => setExpanded((value) => !value)}
          />
        }
      />

      <div className="content">
        {expanded && (
          <div className="content-pad stack">
            <Field label="Search">
              <input
                className="input"
                placeholder="Notes, amount, category, account"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
            </Field>

            <div>
              <span className="field-label">Type</span>
              <div className="row-wrap" style={{ marginTop: 4 }}>
                <button
                  type="button"
                  className={`chip ${filter.type === null ? 'selected' : ''}`}
                  onClick={() => patch({ type: null })}
                >
                  All
                </button>
                {TRANSACTION_TYPES.map((type) => (
                  <button
                    key={type}
                    type="button"
                    className={`chip ${filter.type === type ? 'selected' : ''}`}
                    onClick={() => patch({ type: filter.type === type ? null : type })}
                  >
                    {type}
                  </button>
                ))}
              </div>
            </div>

            <div className="row-wrap" style={{ gap: 12 }}>
              <div style={{ minWidth: 220, flex: '1 1 220px' }}>
                <SelectField
                  label="Account"
                  value={filter.accountId}
                  placeholder="All accounts"
                  options={store.accounts.map((a) => ({ value: a.id, label: a.name }))}
                  onChange={(id) => patch({ accountId: id })}
                />
              </div>
              <div style={{ minWidth: 220, flex: '1 1 220px' }}>
                <SelectField
                  label="Currency"
                  value={filter.currencyId}
                  placeholder="All currencies"
                  options={store.currencies.map((c) => ({
                    value: c.id,
                    label: c.currencySymbol ? `${c.name} (${c.currencySymbol})` : c.name
                  }))}
                  onChange={(id) => patch({ currencyId: id })}
                />
              </div>
            </div>

            <Field label="Category">
              <div className="row">
                <button type="button" className="btn outlined wide-left" onClick={() => setPickerOpen(true)}>
                  {categoryLabel}
                </button>
                {filter.categoryId !== null && (
                  <button
                    type="button"
                    className="icon-btn"
                    title="Clear category"
                    onClick={() => patch({ categoryId: null })}
                  >
                    <Icon name="close" />
                  </button>
                )}
              </div>
            </Field>

            <div className="row-wrap" style={{ gap: 12 }}>
              <div style={{ flex: '1 1 160px' }}>
                <Field label="From">
                  <input
                    className="input"
                    type="date"
                    value={filter.startDate ?? ''}
                    onChange={(event) => patch({ startDate: event.target.value || null })}
                  />
                </Field>
              </div>
              <div style={{ flex: '1 1 160px' }}>
                <Field label="To">
                  <input
                    className="input"
                    type="date"
                    value={filter.endDate ?? ''}
                    onChange={(event) => patch({ endDate: event.target.value || null })}
                  />
                </Field>
              </div>
              <div style={{ flex: '1 1 120px' }}>
                <Field label="Min amount">
                  <input
                    className="input"
                    inputMode="decimal"
                    value={filter.minAmount === null ? '' : String(filter.minAmount)}
                    onChange={(event) =>
                      patch({ minAmount: event.target.value === '' ? null : Number(event.target.value) })
                    }
                  />
                </Field>
              </div>
              <div style={{ flex: '1 1 120px' }}>
                <Field label="Max amount">
                  <input
                    className="input"
                    inputMode="decimal"
                    value={filter.maxAmount === null ? '' : String(filter.maxAmount)}
                    onChange={(event) =>
                      patch({ maxAmount: event.target.value === '' ? null : Number(event.target.value) })
                    }
                  />
                </Field>
              </div>
            </div>

            <div>
              <button
                type="button"
                className="btn"
                onClick={() => {
                  setFilter({ ...EMPTY_FILTER });
                  setQuery('');
                }}
              >
                Reset filters
              </button>
            </div>
          </div>
        )}

        <hr className="divider" />
        <div className="field-label" style={{ padding: '8px 16px' }}>
          {filtered.length} transactions
        </div>

        {filtered.map((tx) => (
          <TransactionRow
            key={tx.transId}
            transaction={tx}
            categoryPath={tx.categId !== null ? categoryPaths.get(tx.categId) ?? '' : ''}
            currency={store.accountCurrency(tx.accountId)}
            toCurrency={tx.toAccountId !== -1 ? store.accountCurrency(tx.toAccountId) : undefined}
            accountName={store.accounts.find((a) => a.id === tx.accountId)?.name ?? ''}
            onEdit={
              store.isReadOnly
                ? undefined
                : () => nav.push({ name: 'transaction', accountId: null, transId: tx.transId })
            }
          />
        ))}
      </div>

      {summaries.length > 0 && (
        <div className="footer">
          <span className="field-label">Net flow</span>
          {summaries.map((summary) => (
            <div key={summary.currency.id} style={{ marginTop: 4 }}>
              <div className="amount">{formatAmount(summary.net, summary.currency)}</div>
              <div className="muted small">
                Income {formatAmount(summary.income, summary.currency)} · Expenses{' '}
                {formatAmount(summary.expense, summary.currency)}
              </div>
            </div>
          ))}
        </div>
      )}

      {pickerOpen && (
        <CategoryPickerDialog
          categories={store.categories}
          onSelect={(category) => {
            patch({ categoryId: category.id });
            setPickerOpen(false);
          }}
          onDismiss={() => setPickerOpen(false)}
        />
      )}
    </div>
  );
}
