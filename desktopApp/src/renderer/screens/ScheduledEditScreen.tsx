import React, { useEffect, useMemo, useState } from 'react';
import type { Navigator } from '../App';
import { api } from '../api';
import { useStore } from '../store';
import { Field, SelectField, Segmented, TextField, TopBar } from '../components/ui';
import { AmountField } from '../components/AmountField';
import { CategoryPickerDialog } from '../components/CategoryPickerDialog';
import { buildPath } from '../../shared/category';
import { amountToInput } from '../../shared/format';
import { datePart, formatTransDate, todayIso } from '../../shared/mmexDate';
import {
  CUSTOM_PERIODS,
  SCHEDULE_MODES,
  countRemaining,
  customPeriodLabel,
  decodeEndDate,
  encodeEndDate,
  recurrenceFromRepeats,
  scheduleModeLabel,
  toRepeatsAndOccurrences,
  usesOccurrencesAsInterval,
  type CustomPeriod,
  type ScheduleMode
} from '../../shared/recurrence';
import { TRANSACTION_TYPES, type ScheduledTransaction, type TransactionType } from '../../shared/types';

/** Port of `ScheduledEditScreen` + `ScheduledEditViewModel`. */
export function ScheduledEditScreen({
  nav,
  editBdId
}: {
  nav: Navigator;
  editBdId: number | null;
}): React.ReactElement {
  const store = useStore();
  const isEdit = editBdId !== null;

  const [type, setType] = useState<TransactionType>('Withdrawal');
  const [accountId, setAccountId] = useState<number | null>(null);
  const [toAccountId, setToAccountId] = useState<number | null>(null);
  const [categId, setCategId] = useState<number | null>(null);
  const [nextDate, setNextDate] = useState(todayIso());
  const [mode, setMode] = useState<ScheduleMode>('MONTHLY');
  const [everyN, setEveryN] = useState('1');
  const [customPeriod, setCustomPeriod] = useState<CustomPeriod>('MONTH');
  const [amount, setAmount] = useState('');
  const [toAmount, setToAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [endDate, setEndDate] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);

  const accountOptions = useMemo(
    () => store.accounts.map((account) => ({ value: account.id, label: account.name })),
    [store.accounts]
  );

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      if (editBdId === null) {
        setAccountId(store.accounts[0]?.id ?? null);
        setCategId(store.snapshot?.settings.defaultCategoryIds?.Withdrawal ?? null);
        return;
      }
      const item = await api.getScheduled(editBdId);
      if (cancelled || !item) return;
      const recurrence = recurrenceFromRepeats(item.repeats, item.numOccurrences);
      setType(item.type);
      setAccountId(item.accountId);
      setToAccountId(item.toAccountId !== -1 ? item.toAccountId : null);
      setCategId(item.categId);
      setNextDate(datePart(item.nextOccurrenceDate) || todayIso());
      setMode(recurrence.mode);
      setEveryN(String(recurrence.everyN));
      setCustomPeriod(recurrence.customPeriod);
      setAmount(amountToInput(item.transAmount));
      setToAmount(amountToInput(item.toTransAmount));
      setNotes(item.notes ?? '');
      setEndDate(decodeEndDate(item.transactionNumber));
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editBdId]);

  const onTypeChange = (next: TransactionType): void => {
    setType(next);
    if (!isEdit) {
      const fallback = store.snapshot?.settings.defaultCategoryIds?.[next];
      if (fallback !== undefined && fallback !== null) setCategId(fallback);
    }
  };

  const save = async (): Promise<void> => {
    setError(null);
    if (accountId === null) return setError('Account required');
    if (categId === null) return setError('Category required');
    const parsedAmount = Number(amount);
    if (!amount.trim() || !Number.isFinite(parsedAmount)) return setError('Invalid amount');
    if (type === 'Transfer' && toAccountId === null) return setError('To-account required for transfer');
    const parsedToAmount = Number(toAmount);
    const resolvedToAmount = Number.isFinite(parsedToAmount) ? parsedToAmount : 0;

    const recurrence = {
      mode,
      everyN: Math.max(1, Number(everyN) || 1),
      customPeriod
    };
    const [baseRepeats] = toRepeatsAndOccurrences(recurrence, -1);
    const usesInterval = usesOccurrencesAsInterval(recurrence);

    let numOccurrences: number;
    if (mode === 'ONCE') {
      numOccurrences = 1;
    } else if (usesInterval) {
      const n = recurrence.everyN;
      numOccurrences = customPeriod === 'WEEK' ? n * 7 : customPeriod === 'MONTH' ? n : n * 12;
    } else if (endDate) {
      numOccurrences = Math.max(1, countRemaining(nextDate, endDate, baseRepeats, null));
    } else {
      numOccurrences = -1;
    }

    const [repeats] = toRepeatsAndOccurrences(recurrence, numOccurrences);

    const item: ScheduledTransaction = {
      bdId: editBdId ?? 0,
      accountId,
      toAccountId: type === 'Transfer' ? (toAccountId as number) : -1,
      payeeId: -1, // filled in by the main process from the file's default payee
      type,
      transAmount: parsedAmount,
      toTransAmount: type === 'Transfer' ? resolvedToAmount : 0,
      categId,
      notes: notes.trim() ? notes.trim() : null,
      nextOccurrenceDate: formatTransDate(nextDate),
      repeats,
      numOccurrences,
      status: '',
      transactionNumber: usesInterval ? encodeEndDate(endDate) : null,
      color: -1,
      followupId: -1
    };

    setSaving(true);
    const okResult = await store.run(() => api.saveScheduled(item, editBdId === null));
    setSaving(false);
    if (okResult) nav.back();
  };

  const categoryLabel = categId !== null ? buildPath(store.categories, categId) : '';

  return (
    <div className="screen">
      <TopBar title={isEdit ? 'Edit scheduled' : 'Add scheduled'} onBack={nav.back} />

      <div className="content content-pad">
        <div className="stack" style={{ maxWidth: 560 }}>
          {error && <div className="error-text">{error}</div>}

          <Field label="Next date">
            <input
              className="input"
              type="date"
              value={nextDate}
              onChange={(event) => setNextDate(event.target.value)}
            />
          </Field>

          <Field label="Mode">
            <Segmented
              value={mode}
              options={SCHEDULE_MODES.map((m) => ({ value: m, label: scheduleModeLabel(m) }))}
              onChange={setMode}
            />
          </Field>

          {mode === 'CUSTOM' && (
            <>
              <TextField
                label="Every N"
                value={everyN}
                onChange={(value) => setEveryN(value.replace(/\D/g, ''))}
              />
              <Segmented
                value={customPeriod}
                options={CUSTOM_PERIODS.map((p) => ({ value: p, label: customPeriodLabel(p) }))}
                onChange={setCustomPeriod}
              />
              <p className="muted small" style={{ margin: 0 }}>
                Weekday follows the next date (one schedule per weekday).
              </p>
            </>
          )}

          {mode === 'DAY_OF_WEEK' && (
            <p className="muted small" style={{ margin: 0 }}>
              Repeats weekly on the weekday of the next date.
            </p>
          )}

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
              onChange={setToAccountId}
            />
          )}

          <Field label="Category">
            <button type="button" className="btn outlined wide-left" onClick={() => setPickerOpen(true)}>
              {categoryLabel || 'Select category'}
            </button>
          </Field>

          <AmountField label="Amount" value={amount} onChange={setAmount} />

          {type === 'Transfer' && (
            <AmountField label="Destination amount" value={toAmount} onChange={setToAmount} />
          )}

          <TextField label="Description" value={notes} onChange={setNotes} multiline />

          {mode !== 'ONCE' && (
            <Field label="End date (optional)">
              <div className="row">
                <input
                  className="input grow"
                  type="date"
                  value={endDate ?? ''}
                  onChange={(event) => setEndDate(event.target.value || null)}
                />
                {endDate && (
                  <button type="button" className="btn" onClick={() => setEndDate(null)}>
                    Clear
                  </button>
                )}
              </div>
            </Field>
          )}

          <div className="row" style={{ justifyContent: 'flex-end', gap: 8 }}>
            <button type="button" className="btn" onClick={nav.back}>
              Cancel
            </button>
            <button type="button" className="btn filled" disabled={saving} onClick={() => void save()}>
              {isEdit ? 'Save' : 'Add'}
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
    </div>
  );
}
