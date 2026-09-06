import React, { useCallback, useEffect, useMemo, useState } from 'react';
import type { Navigator } from '../App';
import { api } from '../api';
import { useStore } from '../store';
import { ConfirmDialog, Fab, TopBar } from '../components/ui';
import { Icon, IconButton } from '../components/Icon';
import { TransactionRow } from '../components/TransactionRow';
import { buildPath } from '../../shared/category';
import { formatAmount } from '../../shared/format';
import { datePart, todayIso } from '../../shared/mmexDate';
import type { Transaction } from '../../shared/types';

/** Port of `AccountScreen` — one account's ledger, plus balance as of a date. */
export function AccountScreen({
  nav,
  accountId
}: {
  nav: Navigator;
  accountId: number;
}): React.ReactElement {
  const store = useStore();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [asOfDate, setAsOfDate] = useState<string | null>(null);
  const [balanceAtDate, setBalanceAtDate] = useState<number | null>(null);
  const [datePickerOpen, setDatePickerOpen] = useState(false);

  const account = store.accounts.find((a) => a.id === accountId) ?? null;
  const currency = account ? store.currencyById.get(account.currencyId) : undefined;

  const load = useCallback(async () => {
    setTransactions(await api.transactionsByAccount(accountId));
  }, [accountId]);

  useEffect(() => {
    void load();
  }, [load, store.snapshot]);

  const categoryPaths = useMemo(() => {
    const map = new Map<number, string>();
    for (const category of store.categories) map.set(category.id, buildPath(store.categories, category.id));
    return map;
  }, [store.categories]);

  const visible = useMemo(
    () =>
      asOfDate === null
        ? transactions
        : transactions.filter((tx) => datePart(tx.transDate) <= asOfDate),
    [asOfDate, transactions]
  );

  const shownBalance = balanceAtDate ?? account?.balance ?? 0;

  return (
    <div className="screen">
      <TopBar
        title={account?.name ?? ''}
        subtitle={store.balanceVisible ? formatAmount(shownBalance, currency) : '•••'}
        onBack={nav.back}
        actions={
          <>
            <IconButton
              icon="filter"
              title="Filter transactions"
              onClick={() => nav.push({ name: 'filter', accountId })}
            />
            <IconButton icon="calendar" title="Balance at date" onClick={() => setDatePickerOpen(true)} />
          </>
        }
      />

      <div className="content">
        {asOfDate !== null && (
          <div className="row" style={{ padding: '8px 16px' }}>
            <span className="chip selected">As of {asOfDate}</span>
            <button
              type="button"
              className="icon-btn"
              title="Clear date"
              onClick={() => {
                setAsOfDate(null);
                setBalanceAtDate(null);
              }}
            >
              <Icon name="close" />
            </button>
          </div>
        )}

        {visible.length === 0 && <div className="empty">No transactions</div>}
        {visible.map((tx) => (
          <TransactionRow
            key={tx.transId}
            transaction={tx}
            categoryPath={tx.categId !== null ? categoryPaths.get(tx.categId) ?? '' : ''}
            currency={store.accountCurrency(tx.accountId)}
            toCurrency={tx.toAccountId !== -1 ? store.accountCurrency(tx.toAccountId) : undefined}
            onEdit={
              store.isReadOnly
                ? undefined
                : () => nav.push({ name: 'transaction', accountId: null, transId: tx.transId })
            }
            onDelete={store.isReadOnly ? undefined : () => setDeleteId(tx.transId)}
          />
        ))}
      </div>

      {!store.isReadOnly && (
        <Fab
          title="Add transaction"
          onClick={() => nav.push({ name: 'transaction', accountId, transId: null })}
        />
      )}

      {deleteId !== null && (
        <ConfirmDialog
          title="Delete transaction?"
          message="This cannot be undone."
          onCancel={() => setDeleteId(null)}
          onConfirm={() => {
            const id = deleteId;
            setDeleteId(null);
            void store.run(() => api.deleteTransaction(id)).then(() => load());
          }}
        />
      )}

      {datePickerOpen && (
        <DatePickDialog
          initial={asOfDate ?? todayIso()}
          title="Balance at date"
          onCancel={() => setDatePickerOpen(false)}
          onConfirm={(date) => {
            setDatePickerOpen(false);
            setAsOfDate(date);
            void api.balanceAtDate(accountId, date).then(setBalanceAtDate);
          }}
        />
      )}
    </div>
  );
}

export function DatePickDialog({
  initial,
  title,
  onConfirm,
  onCancel
}: {
  initial: string;
  title: string;
  onConfirm: (date: string) => void;
  onCancel: () => void;
}): React.ReactElement {
  const [value, setValue] = useState(initial);
  return (
    <div className="scrim" onMouseDown={(event) => event.target === event.currentTarget && onCancel()}>
      <div className="dialog" style={{ width: 320 }}>
        <h2>{title}</h2>
        <input
          className="input"
          type="date"
          value={value}
          autoFocus
          onChange={(event) => setValue(event.target.value)}
        />
        <div className="dialog-actions">
          <button type="button" className="btn" onClick={onCancel}>
            Cancel
          </button>
          <button type="button" className="btn filled" disabled={!value} onClick={() => onConfirm(value)}>
            OK
          </button>
        </div>
      </div>
    </div>
  );
}
