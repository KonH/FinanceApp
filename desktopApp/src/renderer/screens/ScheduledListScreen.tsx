import React, { useEffect, useState } from 'react';
import type { Navigator } from '../App';
import { api } from '../api';
import { useStore } from '../store';
import { ConfirmDialog, Fab, ListItem, TopBar } from '../components/ui';
import { IconButton } from '../components/Icon';
import { amountClass } from '../components/TransactionRow';
import { buildPath } from '../../shared/category';
import { formatAmount } from '../../shared/format';
import { datePart } from '../../shared/mmexDate';
import { recurrenceFromRepeats, scheduleModeLabel } from '../../shared/recurrence';
import type { ScheduledTransaction } from '../../shared/types';

/** Port of `ScheduledListScreen` — the BILLSDEPOSITS_V1 rules. */
export function ScheduledListScreen({ nav }: { nav: Navigator }): React.ReactElement {
  const store = useStore();
  const [items, setItems] = useState<ScheduledTransaction[]>([]);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  useEffect(() => {
    void api.scheduledList().then(setItems);
  }, [store.snapshot]);

  return (
    <div className="screen">
      <TopBar title="Scheduled transactions" onBack={nav.back} />

      <div className="content">
        {items.length === 0 && <div className="empty">No scheduled transactions</div>}
        {items.map((item) => {
          const recurrence = recurrenceFromRepeats(item.repeats, item.numOccurrences);
          const accountName = store.accounts.find((a) => a.id === item.accountId)?.name ?? '';
          const categoryPath = buildPath(store.categories, item.categId);
          return (
            <ListItem
              key={item.bdId}
              title={categoryPath || item.notes || item.type}
              subtitle={`${datePart(item.nextOccurrenceDate)} · ${accountName} · ${scheduleModeLabel(
                recurrence.mode
              )}`}
              trailing={
                <>
                  <span className={`amount ${amountClass(item.type)}`}>
                    {formatAmount(item.transAmount, store.accountCurrency(item.accountId))}
                  </span>
                  <IconButton
                    icon="edit"
                    title="Edit"
                    disabled={store.isReadOnly}
                    onClick={() => nav.push({ name: 'scheduledEdit', bdId: item.bdId })}
                  />
                  <IconButton
                    icon="delete"
                    title="Delete"
                    disabled={store.isReadOnly}
                    onClick={() => setDeleteId(item.bdId)}
                  />
                </>
              }
            />
          );
        })}
      </div>

      {!store.isReadOnly && (
        <Fab
          title="Add scheduled transaction"
          onClick={() => nav.push({ name: 'scheduledEdit', bdId: null })}
        />
      )}

      {deleteId !== null && (
        <ConfirmDialog
          title="Delete scheduled transaction?"
          message="This cannot be undone."
          onCancel={() => setDeleteId(null)}
          onConfirm={() => {
            const id = deleteId;
            setDeleteId(null);
            void store
              .run(() => api.deleteScheduled(id))
              .then(() => api.scheduledList().then(setItems));
          }}
        />
      )}
    </div>
  );
}
