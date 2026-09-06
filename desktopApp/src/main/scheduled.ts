import { dbHolder } from './db/database';
import { scheduledRepo, transactionRepo } from './repositories';
import { settingsStore } from './settings';
import { datePart, formatLastUpdated, formatTransDate, todayIso, compareIso } from '../shared/mmexDate';
import { MmexRepeats, advance, decodeEndDate, isEveryX, repeatsBase } from '../shared/recurrence';
import type { ScheduledTransaction, Transaction } from '../shared/types';

/**
 * Port of `ProcessDueScheduledUseCase` — lists due schedules and applies
 * approve / skip-once / delete, advancing (or ending) the schedule after each.
 */

export function listDueToday(): ScheduledTransaction[] {
  if (settingsStore.isReadOnly) return [];
  if (!dbHolder.isOpen) return [];
  return scheduledRepo.getDue(todayIso());
}

/** Inserts the ledger row for the current occurrence, then advances the schedule. */
export function approve(scheduled: ScheduledTransaction): ScheduledTransaction | null {
  const occurrenceDate = scheduled.nextOccurrenceDate;
  const tx: Transaction = {
    transId: Date.now() * 1000,
    accountId: scheduled.accountId,
    toAccountId: scheduled.type === 'Transfer' ? scheduled.toAccountId : -1,
    payeeId: dbHolder.defaultPayeeId,
    type: scheduled.type,
    transAmount: scheduled.transAmount,
    toTransAmount: scheduled.type === 'Transfer' ? scheduled.toTransAmount : 0,
    categId: scheduled.categId,
    transDate: occurrenceDate.includes('T') ? occurrenceDate : formatTransDate(occurrenceDate),
    lastUpdatedTime: formatLastUpdated(),
    notes: scheduled.notes,
    followupId: scheduled.bdId
  };
  transactionRepo.insert(tx);
  return advanceAfterAction(scheduled);
}

/** Skips this occurrence without inserting anything. */
export function cancelOnce(scheduled: ScheduledTransaction): ScheduledTransaction | null {
  return advanceAfterAction(scheduled);
}

export function deleteScheduled(scheduled: ScheduledTransaction): null {
  scheduledRepo.delete(scheduled.bdId);
  return null;
}

function advanceAfterAction(scheduled: ScheduledTransaction): ScheduledTransaction | null {
  if (repeatsBase(scheduled.repeats) === MmexRepeats.ONCE) {
    scheduledRepo.delete(scheduled.bdId);
    return null;
  }

  const current = datePart(scheduled.nextOccurrenceDate);
  const usesInterval = isEveryX(scheduled.repeats);
  const next = advance(current, scheduled.repeats, usesInterval ? scheduled.numOccurrences : null);
  if (next === null) {
    scheduledRepo.delete(scheduled.bdId);
    return null;
  }

  const endDate = decodeEndDate(scheduled.transactionNumber);
  if (endDate !== null && compareIso(next, endDate) > 0) {
    scheduledRepo.delete(scheduled.bdId);
    return null;
  }

  let newNum = scheduled.numOccurrences;
  if (!usesInterval && newNum !== null && newNum > 0) {
    newNum = newNum - 1;
    if (newNum <= 0) {
      scheduledRepo.delete(scheduled.bdId);
      return null;
    }
  }

  scheduledRepo.update({
    ...scheduled,
    nextOccurrenceDate: formatTransDate(next),
    numOccurrences: newNum
  });
  return scheduledRepo.getById(scheduled.bdId);
}
