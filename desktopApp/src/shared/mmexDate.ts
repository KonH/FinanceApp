/**
 * MMEX date helpers — port of `MmexDateFormat` plus the calendar arithmetic the
 * Android app gets from kotlinx-datetime.
 *
 * Dates are handled as plain `YYYY-MM-DD` strings; arithmetic runs on UTC
 * timestamps so the local timezone can never shift a stored day.
 */

const pad = (n: number): string => String(n).padStart(2, '0');

/** `YYYY-MM-DD` for the machine's current local day. */
export function todayIso(): string {
  const now = new Date();
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** MMEX TRANSDATE: `YYYY-MM-DDT00:00:00`. */
export function formatTransDate(isoDate: string): string {
  return `${datePart(isoDate)}T00:00:00`;
}

/** MMEX LASTUPDATEDTIME: `YYYY-MM-DDTHH:mm:ss` (local time). */
export function formatLastUpdated(date: Date = new Date()): string {
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

/** Strips the time component of an MMEX date string. */
export function datePart(mmexDate: string): string {
  return (mmexDate ?? '').split('T')[0];
}

/** `YYYY-MM` of the current local month. */
export function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}`;
}

export function daysInMonth(year: number, month1Based: number): number {
  return new Date(Date.UTC(year, month1Based, 0)).getUTCDate();
}

function toUtc(isoDate: string): Date {
  const [y, m, d] = datePart(isoDate).split('-').map(Number);
  return new Date(Date.UTC(y, (m || 1) - 1, d || 1));
}

function fromUtc(date: Date): string {
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

export function addDays(isoDate: string, days: number): string {
  const d = toUtc(isoDate);
  d.setUTCDate(d.getUTCDate() + days);
  return fromUtc(d);
}

/**
 * Adds calendar months, clamping the day to the length of the target month —
 * the same behaviour as `kotlinx.datetime.LocalDate.plus(n, MONTH)`.
 */
export function addMonths(isoDate: string, months: number): string {
  const d = toUtc(isoDate);
  const day = d.getUTCDate();
  const target = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + months, 1));
  const maxDay = daysInMonth(target.getUTCFullYear(), target.getUTCMonth() + 1);
  target.setUTCDate(Math.min(day, maxDay));
  return fromUtc(target);
}

export function addYears(isoDate: string, years: number): string {
  return addMonths(isoDate, years * 12);
}

export function lastDayOfMonth(isoDate: string): string {
  const d = toUtc(isoDate);
  return fromUtc(new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)));
}

/** 0 = Sunday … 6 = Saturday. */
export function dayOfWeek(isoDate: string): number {
  return toUtc(isoDate).getUTCDay();
}

export function lastBusinessDayOfMonth(isoDate: string): string {
  let d = lastDayOfMonth(isoDate);
  while (dayOfWeek(d) === 0 || dayOfWeek(d) === 6) {
    d = addDays(d, -1);
  }
  return d;
}

/** Lexicographic comparison works for `YYYY-MM-DD`; kept explicit for intent. */
export function compareIso(a: string, b: string): number {
  return datePart(a) < datePart(b) ? -1 : datePart(a) > datePart(b) ? 1 : 0;
}

export function isValidIsoDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(toUtc(value).getTime());
}
