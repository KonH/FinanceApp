import React from 'react';
import { ListItem } from './ui';
import { Icon, IconButton } from './Icon';
import { formatAmount } from '../../shared/format';
import { datePart } from '../../shared/mmexDate';
import { fromSchedule, type Currency, type Transaction } from '../../shared/types';

export const amountClass = (type: string): string =>
  type === 'Deposit' ? 'deposit' : type === 'Withdrawal' ? 'withdrawal' : 'transfer';

/** Shared ledger row — used by the account ledger and the filter results. */
export function TransactionRow({
  transaction,
  categoryPath,
  currency,
  toCurrency,
  accountName,
  onEdit,
  onDelete
}: {
  transaction: Transaction;
  categoryPath: string;
  currency?: Currency;
  toCurrency?: Currency;
  accountName?: string;
  onEdit?: () => void;
  onDelete?: () => void;
}): React.ReactElement {
  const title = (
    <span className="row" style={{ gap: 6 }}>
      {fromSchedule(transaction) && (
        <span className="muted" title="From schedule">
          <Icon name="repeat" size={14} />
        </span>
      )}
      <span className="list-item-title">{categoryPath || transaction.notes || ''}</span>
    </span>
  );

  const subtitle = accountName
    ? `${datePart(transaction.transDate)} · ${accountName}`
    : datePart(transaction.transDate);

  return (
    <ListItem
      title={title}
      subtitle={subtitle}
      trailing={
        <>
          {transaction.type === 'Transfer' ? (
            <span className={`amount ${amountClass(transaction.type)}`} style={{ textAlign: 'right' }}>
              {formatAmount(transaction.transAmount, currency)}
              <br />
              <span className="small">→ {formatAmount(transaction.toTransAmount, toCurrency)}</span>
            </span>
          ) : (
            <span className={`amount ${amountClass(transaction.type)}`}>
              {formatAmount(transaction.transAmount, currency)}
            </span>
          )}
          {onEdit && <IconButton icon="edit" title="Edit" onClick={onEdit} />}
          {onDelete && <IconButton icon="delete" title="Delete" onClick={onDelete} />}
        </>
      }
    />
  );
}
