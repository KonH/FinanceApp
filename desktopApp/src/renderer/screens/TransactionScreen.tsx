import React, { useEffect, useMemo, useState } from 'react';
import type { Navigator } from '../App';
import { api } from '../api';
import { useStore } from '../store';
import { Dialog, Field, SelectField, Segmented, TextField, TopBar } from '../components/ui';
import { AmountField } from '../components/AmountField';
import { CategoryPickerDialog } from '../components/CategoryPickerDialog';
import { buildPath } from '../../shared/category';
import { amountToInput } from '../../shared/format';
import { datePart, formatLastUpdated, formatTransDate, todayIso } from '../../shared/mmexDate';
import { TRANSACTION_TYPES, type Transaction, type TransactionType } from '../../shared/types';

/** Port of `TransactionScreen` + `TransactionViewModel`. */
export function TransactionScreen({
  nav,
  initialAccountId,
  editTransId
}: {
  nav: Navigator;
  initialAccountId: number | null;
  editTransId: number | null;
}): React.ReactElement {
  const store = useStore();
  const isEdit = editTransId !== null;

  const [type, setType] = useState<TransactionType>('Withdrawal');
  const [accountId, setAccountId] = useState<number | null>(initialAccountId);
  const [toAccountId, setToAccountId] = useState<number | null>(null);
  const [categId, setCategId] = useState<number | null>(null);
  const [date, setDate] = useState<string>(todayIso());
  const [amount, setAmount] = useState('');
  const [toAmount, setToAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [fromSchedule, setFromSchedule] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [confirmZero, setConfirmZero] = useState(false);
  const [loaded, setLoaded] = useState(false);

  const accountOptions = useMemo(
    () => store.accounts.map((account) => ({ value: account.id, label: account.name })),
    [store.accounts]
  );

  // Load the edited row, or seed the default category for a new one.
  useEffect(() => {
    let cancelled = false;
    void (async () => {
      if (editTransId !== null) {
        const tx = await api.getTransaction(editTransId);
        if (cancelled || !tx) {
          setLoaded(true);
          return;
        }
        setType(tx.type);
        setAccountId(tx.accountId);
        setToAccountId(tx.toAccountId !== -1 ? tx.toAccountId : null);
        setCategId(tx.categId);
        setDate(datePart(tx.transDate));
        setAmount(amountToInput(tx.transAmount));
        setToAmount(amountToInput(tx.toTransAmount));
        setNotes(tx.notes ?? '');
        setFromSchedule(tx.followupId !== -1);
        setLoaded(true);
        return;
      }
      setAccountId(initialAccountId ?? store.accounts[0]?.id ?? null);
      setCategId(await resolveDefaultCategory('Withdrawal'));
      setLoaded(true);
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editTransId]);

  const resolveDefaultCategory = async (forType: TransactionType): Promise<number | null> => {
    const settings = store.snapshot?.settings;
    if (settings?.useLatestCategory?.[forType]) {
      const latest = await api.latestCategoryId(forType);
      if (latest !== null) return latest;
    }
    return settings?.defaultCategoryIds?.[forType] ?? null;
  };

  const sameCurrency = (fromId: number | null, toId: number | null): boolean => {
    const from = store.accounts.find((a) => a.id === fromId);
    const to = store.accounts.find((a) => a.id === toId);
    return Boolean(from && to && from.currencyId === to.currencyId);
  };

  const onTypeChange = (next: TransactionType): void => {
    setType(next);
    if (next === 'Transfer' && sameCurrency(accountId, toAccountId)) setToAmount(amount);
    if (!isEdit) void resolveDefaultCategory(next).then((id) => id !== null && setCategId(id));
  };

  const onAmountChange = (value: string): void => {
    setAmount(value);
    if (type === 'Transfer' && sameCurrency(accountId, toAccountId)) setToAmount(value);
  };

  const save = async (skipZeroCheck = false): Promise<void> => {
    setError(null);
    const parsedAmount = Number(amount);
    if (accountId === null) return setError('Account required');
    if (categId === null) return setError('Category required');
    if (!amount.trim() || !Number.isFinite(parsedAmount)) return setError('Invalid amount');
    const parsedToAmount = Number(toAmount);
    const resolvedToAmount = Number.isFinite(parsedToAmount) ? parsedToAmount : 0;
    if (type === 'Transfer' && toAccountId === null) return setError('To-account required for transfer');
    if (type === 'Transfer' && resolvedToAmount === 0 && !skipZeroCheck) {
      setConfirmZero(true);
      return;
    }

    const tx: Transaction = {
      transId: editTransId ?? 0,
      accountId,
      toAccountId: type === 'Transfer' ? (toAccountId as number) : -1,
      payeeId: -1, // filled in by the main process from the file's default payee
      type,
      transAmount: parsedAmount,
      toTransAmount: type === 'Transfer' ? resolvedToAmount : 0,
      categId,
      transDate: formatTransDate(date),
      lastUpdatedTime: formatLastUpdated(),
      notes: notes.trim() ? notes.trim() : null,
      followupId: -1
    };

    setSaving(true);
    const okResult = await store.run(() => api.saveTransaction(tx, editTransId === null));
    setSaving(false);
    if (okResult) nav.back();
  };

  const categoryLabel = categId !== null ? buildPath(store.categories, categId) : '';

  return (
    <div className="screen">
      <TopBar
        title={isEdit ? 'Edit transaction' : 'Add transaction'}
        onBack={nav.back}
        actions={
          <button type="button" className="btn filled" disabled={saving || !loaded} onClick={() => void save()}>
            Save
          </button>
        }
      />

      <div className="content content-pad">
        <div className="stack" style={{ maxWidth: 560 }}>
          {error && <div className="error-text">{error}</div>}
          {fromSchedule && <span className="badge">From schedule</span>}

          <Segmented
            value={type}
            options={TRANSACTION_TYPES.map((t) => ({ value: t, label: t }))}
            onChange={onTypeChange}
          />

          <SelectField label="Account" value={accountId} options={accountOptions} onChange={setAccountId} />

          {type === 'Transfer' && (
            <SelectField
              label="To account"
              value={toAccountId}
              options={accountOptions}
              onChange={(id) => {
                setToAccountId(id);
                if (sameCurrency(accountId, id)) setToAmount(amount);
              }}
            />
          )}

          <Field label="Category">
            <button type="button" className="btn outlined wide-left" onClick={() => setPickerOpen(true)}>
              {categoryLabel || 'Select category'}
            </button>
          </Field>

          <Field label="Date">
            <input
              className="input"
              type="date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
            />
          </Field>

          <AmountField label="Amount" value={amount} onChange={onAmountChange} autoFocus={!isEdit} />

          {type === 'Transfer' && (
            <AmountField label="Destination amount" value={toAmount} onChange={setToAmount} />
          )}

          <TextField label="Notes" value={notes} onChange={setNotes} multiline />

          <div className="row" style={{ justifyContent: 'flex-end', gap: 8 }}>
            <button type="button" className="btn" onClick={nav.back}>
              Cancel
            </button>
            <button type="button" className="btn filled" disabled={saving} onClick={() => void save()}>
              Save
            </button>
          </div>
        </div>
      </div>

      {pickerOpen && (
        <CategoryPickerDialog
          categories={store.categories}
          onSelect={(category) => {
            setCategId(category.id);
            setPickerOpen(false);
          }}
          onDismiss={() => setPickerOpen(false)}
        />
      )}

      {confirmZero && (
        <Dialog
          title="Zero destination amount"
          onClose={() => setConfirmZero(false)}
          actions={
            <>
              <button type="button" className="btn" onClick={() => setConfirmZero(false)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn filled"
                onClick={() => {
                  setConfirmZero(false);
                  void save(true);
                }}
              >
                Save anyway
              </button>
            </>
          }
        >
          <p style={{ margin: 0 }}>The destination amount is 0. Save transfer anyway?</p>
        </Dialog>
      )}
    </div>
  );
}
