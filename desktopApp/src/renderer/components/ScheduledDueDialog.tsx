import React from 'react';
import { Dialog } from './ui';
import { formatAmount } from '../../shared/format';
import { datePart } from '../../shared/mmexDate';
import type { Currency, ScheduledTransaction } from '../../shared/types';

export interface DueDialogInfo {
  scheduled: ScheduledTransaction;
  accountName: string;
  toAccountName: string | null;
  categoryPath: string;
  currency?: Currency;
  toCurrency?: Currency;
}

const amountClass = (type: string): string =>
  type === 'Deposit' ? 'deposit' : type === 'Withdrawal' ? 'withdrawal' : 'transfer';

/** Port of `ScheduledDueDialog` — approve, skip this occurrence, or delete the rule. */
export function ScheduledDueDialog({
  info,
  busy,
  onApprove,
  onCancelOnce,
  onDeleteScheduled
}: {
  info: DueDialogInfo;
  busy: boolean;
  onApprove: () => void;
  onCancelOnce: () => void;
  onDeleteScheduled: () => void;
}): React.ReactElement {
  const s = info.scheduled;
  const amountLine =
    s.type === 'Transfer'
      ? `${formatAmount(s.transAmount, info.currency)} → ${formatAmount(s.toTransAmount, info.toCurrency)}`
      : formatAmount(s.transAmount, info.currency);

  return (
    <Dialog
      title="Scheduled transaction due"
      dismissible={false}
      actions={
        <>
          <button type="button" className="btn danger" disabled={busy} onClick={onDeleteScheduled}>
            Delete
          </button>
          <button type="button" className="btn" disabled={busy} onClick={onCancelOnce}>
            Cancel once
          </button>
          <button type="button" className="btn filled" disabled={busy} onClick={onApprove}>
            Approve
          </button>
        </>
      }
    >
      <div className="stack" style={{ gap: 4 }}>
        <div className="muted">{datePart(s.nextOccurrenceDate)}</div>
        <div>
          {s.type} · {info.accountName}
          {s.type === 'Transfer' && info.toAccountName ? ` → ${info.toAccountName}` : ''}
        </div>
        <div>{info.categoryPath || '—'}</div>
        <div className={`amount ${amountClass(s.type)}`} style={{ fontSize: 18 }}>
          {amountLine}
        </div>
        {s.notes ? <div className="muted">{s.notes}</div> : null}
      </div>
    </Dialog>
  );
}
