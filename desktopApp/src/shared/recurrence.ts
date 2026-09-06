import { addDays, addMonths, compareIso, datePart, lastBusinessDayOfMonth, lastDayOfMonth } from './mmexDate';

/**
 * MMEX BILLSDEPOSITS_V1.REPEATS base codes (value % 100).
 * Auto-mode bits live in value / 100 and are ignored — the app always prompts.
 */
export const MmexRepeats = {
  ONCE: 0,
  WEEKLY: 1,
  BIWEEKLY: 2,
  MONTHLY: 3,
  BIMONTHLY: 4,
  QUARTERLY: 5,
  SEMIANNUALLY: 6,
  ANNUALLY: 7,
  FOUR_MONTHS: 8,
  FOUR_WEEKS: 9,
  DAILY: 10,
  IN_X_DAYS: 11,
  IN_X_MONTHS: 12,
  EVERY_X_DAYS: 13,
  EVERY_X_MONTHS: 14,
  MONTHLY_LAST_DAY: 15,
  MONTHLY_LAST_BUSINESS_DAY: 16
} as const;

export function repeatsBase(repeats: number): number {
  return (((repeats % 100) + 100) % 100) | 0;
}

export function isEveryX(repeats: number): boolean {
  const b = repeatsBase(repeats);
  return (
    b === MmexRepeats.EVERY_X_DAYS ||
    b === MmexRepeats.EVERY_X_MONTHS ||
    b === MmexRepeats.IN_X_DAYS ||
    b === MmexRepeats.IN_X_MONTHS
  );
}

export type ScheduleMode = 'ONCE' | 'DAILY' | 'MONTHLY' | 'DAY_OF_WEEK' | 'CUSTOM';
export const SCHEDULE_MODES: ScheduleMode[] = ['ONCE', 'DAILY', 'MONTHLY', 'DAY_OF_WEEK', 'CUSTOM'];

export type CustomPeriod = 'WEEK' | 'MONTH' | 'YEAR';
export const CUSTOM_PERIODS: CustomPeriod[] = ['WEEK', 'MONTH', 'YEAR'];

export interface ScheduleRecurrence {
  mode: ScheduleMode;
  /** For CUSTOM: every N periods. Ignored for fixed modes. */
  everyN: number;
  customPeriod: CustomPeriod;
}

const END_PREFIX = 'END:';

/** Port of `ScheduleRecurrence.toRepeatsAndOccurrences`. */
export function toRepeatsAndOccurrences(
  rec: ScheduleRecurrence,
  remainingOrInterval: number
): [number, number] {
  switch (rec.mode) {
    case 'ONCE':
      return [MmexRepeats.ONCE, 1];
    case 'DAILY':
      return [MmexRepeats.DAILY, remainingOrInterval];
    case 'MONTHLY':
      return [MmexRepeats.MONTHLY, remainingOrInterval];
    case 'DAY_OF_WEEK':
      return [MmexRepeats.WEEKLY, remainingOrInterval];
    case 'CUSTOM':
      return customToRepeats(rec, remainingOrInterval);
  }
}

function customToRepeats(rec: ScheduleRecurrence, remainingOrInterval: number): [number, number] {
  const n = Math.max(1, rec.everyN);
  if (rec.customPeriod === 'WEEK') {
    if (n === 1) return [MmexRepeats.WEEKLY, remainingOrInterval];
    if (n === 2) return [MmexRepeats.BIWEEKLY, remainingOrInterval];
    if (n === 4) return [MmexRepeats.FOUR_WEEKS, remainingOrInterval];
    return [MmexRepeats.EVERY_X_DAYS, n * 7];
  }
  if (rec.customPeriod === 'MONTH') {
    if (n === 1) return [MmexRepeats.MONTHLY, remainingOrInterval];
    if (n === 2) return [MmexRepeats.BIMONTHLY, remainingOrInterval];
    if (n === 3) return [MmexRepeats.QUARTERLY, remainingOrInterval];
    if (n === 4) return [MmexRepeats.FOUR_MONTHS, remainingOrInterval];
    if (n === 6) return [MmexRepeats.SEMIANNUALLY, remainingOrInterval];
    if (n === 12) return [MmexRepeats.ANNUALLY, remainingOrInterval];
    return [MmexRepeats.EVERY_X_MONTHS, n];
  }
  if (n === 1) return [MmexRepeats.ANNUALLY, remainingOrInterval];
  return [MmexRepeats.EVERY_X_MONTHS, n * 12];
}

/** True when NUMOCCURRENCES stores the interval X rather than remaining payments. */
export function usesOccurrencesAsInterval(rec: ScheduleRecurrence): boolean {
  if (rec.mode !== 'CUSTOM') return false;
  const n = Math.max(1, rec.everyN);
  if (rec.customPeriod === 'WEEK') return ![1, 2, 4].includes(n);
  if (rec.customPeriod === 'MONTH') return ![1, 2, 3, 4, 6, 12].includes(n);
  return n !== 1;
}

/** Port of `ScheduleRecurrence.fromRepeats`. */
export function recurrenceFromRepeats(repeats: number, numOccurrences: number | null): ScheduleRecurrence {
  const b = repeatsBase(repeats);
  const n = numOccurrences ?? -1;
  const rec = (mode: ScheduleMode, everyN = 1, customPeriod: CustomPeriod = 'MONTH'): ScheduleRecurrence => ({
    mode,
    everyN,
    customPeriod
  });
  switch (b) {
    case MmexRepeats.ONCE:
      return rec('ONCE');
    case MmexRepeats.DAILY:
      return rec('DAILY');
    case MmexRepeats.MONTHLY:
      return rec('MONTHLY');
    case MmexRepeats.WEEKLY:
      return rec('DAY_OF_WEEK');
    case MmexRepeats.BIWEEKLY:
      return rec('CUSTOM', 2, 'WEEK');
    case MmexRepeats.FOUR_WEEKS:
      return rec('CUSTOM', 4, 'WEEK');
    case MmexRepeats.BIMONTHLY:
      return rec('CUSTOM', 2, 'MONTH');
    case MmexRepeats.QUARTERLY:
      return rec('CUSTOM', 3, 'MONTH');
    case MmexRepeats.FOUR_MONTHS:
      return rec('CUSTOM', 4, 'MONTH');
    case MmexRepeats.SEMIANNUALLY:
      return rec('CUSTOM', 6, 'MONTH');
    case MmexRepeats.ANNUALLY:
      return rec('CUSTOM', 1, 'YEAR');
    case MmexRepeats.EVERY_X_DAYS: {
      const days = Math.max(1, n);
      return days % 7 === 0 ? rec('CUSTOM', days / 7, 'WEEK') : rec('CUSTOM', days, 'WEEK');
    }
    case MmexRepeats.EVERY_X_MONTHS: {
      const months = Math.max(1, n);
      return months % 12 === 0 ? rec('CUSTOM', months / 12, 'YEAR') : rec('CUSTOM', months, 'MONTH');
    }
    default:
      return rec('MONTHLY');
  }
}

/** Port of `ScheduleDateMath.advance`; returns null when the schedule ends. */
export function advance(fromIso: string, repeats: number, numOccurrences: number | null): string | null {
  const b = repeatsBase(repeats);
  const n = Math.max(1, numOccurrences ?? 1);
  const from = datePart(fromIso);
  switch (b) {
    case MmexRepeats.ONCE:
      return null;
    case MmexRepeats.WEEKLY:
      return addDays(from, 7);
    case MmexRepeats.BIWEEKLY:
      return addDays(from, 14);
    case MmexRepeats.FOUR_WEEKS:
      return addDays(from, 28);
    case MmexRepeats.DAILY:
      return addDays(from, 1);
    case MmexRepeats.MONTHLY:
      return addMonths(from, 1);
    case MmexRepeats.BIMONTHLY:
      return addMonths(from, 2);
    case MmexRepeats.QUARTERLY:
      return addMonths(from, 3);
    case MmexRepeats.FOUR_MONTHS:
      return addMonths(from, 4);
    case MmexRepeats.SEMIANNUALLY:
      return addMonths(from, 6);
    case MmexRepeats.ANNUALLY:
      return addMonths(from, 12);
    case MmexRepeats.MONTHLY_LAST_DAY:
      return lastDayOfMonth(addMonths(from, 1));
    case MmexRepeats.MONTHLY_LAST_BUSINESS_DAY:
      return lastBusinessDayOfMonth(addMonths(from, 1));
    case MmexRepeats.IN_X_DAYS:
    case MmexRepeats.EVERY_X_DAYS:
      return addDays(from, n);
    case MmexRepeats.IN_X_MONTHS:
    case MmexRepeats.EVERY_X_MONTHS:
      return addMonths(from, n);
    default:
      return addMonths(from, 1);
  }
}

/**
 * Occurrences from `start` through `end` inclusive, for schedule types where
 * NUMOCCURRENCES means remaining payments (not an interval).
 */
export function countRemaining(
  startIso: string,
  endIso: string,
  repeats: number,
  intervalForEveryX: number | null
): number {
  if (repeatsBase(repeats) === MmexRepeats.ONCE) return 1;
  if (compareIso(endIso, startIso) < 0) return 0;
  let count = 0;
  let cursor: string | null = datePart(startIso);
  while (cursor !== null && compareIso(cursor, endIso) <= 0 && count < 10_000) {
    count++;
    cursor = advance(cursor, repeats, intervalForEveryX);
  }
  return count;
}

export function encodeEndDate(endDate: string | null): string | null {
  return endDate ? `${END_PREFIX}${endDate}` : null;
}

export function decodeEndDate(transactionNumber: string | null | undefined): string | null {
  if (!transactionNumber || !transactionNumber.startsWith(END_PREFIX)) return null;
  const raw = transactionNumber.slice(END_PREFIX.length);
  return /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : null;
}

export function scheduleModeLabel(mode: ScheduleMode): string {
  switch (mode) {
    case 'ONCE':
      return 'Once';
    case 'DAILY':
      return 'Daily';
    case 'MONTHLY':
      return 'Monthly';
    case 'DAY_OF_WEEK':
      return 'Weekly';
    case 'CUSTOM':
      return 'Custom';
  }
}

export function customPeriodLabel(period: CustomPeriod): string {
  switch (period) {
    case 'WEEK':
      return 'Week';
    case 'MONTH':
      return 'Month';
    case 'YEAR':
      return 'Year';
  }
}
