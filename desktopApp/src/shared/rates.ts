import { addDays, datePart, lastDayOfMonth } from './mmexDate';
import type { Transaction } from './types';

/**
 * Port of `domain/rates` (ExchangeRates + FilteredBalance).
 *
 * Daily exchange rates cached against a single pivot currency (EUR):
 * `rates[code][date]` is how many units of `code` one EUR bought on that day.
 * Converting A → B is `rate(B) / rate(A)`, so picking another target currency
 * never needs a new download.
 *
 * `coverage[code][month]` (`YYYY-MM`) records the last day of that month already
 * requested for `code`. Days without a published rate (weekends, holidays) fall
 * back to the closest earlier day within `LOOKBACK_DAYS`.
 */
export interface RateTable {
  rates: Record<string, Record<string, number>>;
  coverage: Record<string, Record<string, string>>;
}

/** A rate the caller needs: `code` on `date` (`YYYY-MM-DD`). */
export interface RateNeed {
  code: string;
  date: string;
}

/** One download: every day in `from`..`to` for `codes`; it completes `month` up to `to`. */
export interface RateFetch {
  month: string;
  from: string;
  to: string;
  codes: string[];
}

export interface RateRow {
  date: string;
  code: string;
  rate: number;
}

export const PIVOT = 'EUR';
export const LOOKBACK_DAYS = 7;

export const emptyRateTable = (): RateTable => ({ rates: {}, coverage: {} });

/** ISO code of an MMEX currency (`CURRENCY_SYMBOL`), falling back to its name. */
export function codeOf(currency: { currencySymbol: string | null; name: string }): string {
  return currency.currencySymbol?.trim().toUpperCase() || currency.name;
}

/**
 * Downloads still missing for `needs`: one per calendar month, carrying every
 * code that month lacks. Dates after `today` use `today`'s rate. Codes outside
 * `supported` (when known) are left out — they can never be fetched.
 */
export function planFetches(
  table: RateTable,
  needs: Iterable<RateNeed>,
  today: string,
  supported: Set<string> | null = null
): RateFetch[] {
  const byMonth = new Map<string, Set<string>>();
  for (const need of needs) {
    if (need.code === PIVOT) continue;
    if (supported && !supported.has(need.code)) continue;
    const date = need.date < today ? need.date : today;
    const month = date.substring(0, 7);
    const covered = table.coverage[need.code]?.[month];
    if (covered === undefined || covered < date) {
      if (!byMonth.has(month)) byMonth.set(month, new Set());
      byMonth.get(month)!.add(need.code);
    }
  }
  return [...byMonth.keys()].sort().map((month) => {
    const first = `${month}-01`;
    const last = lastDayOfMonth(first);
    return {
      month,
      from: addDays(first, -LOOKBACK_DAYS),
      to: last < today ? last : today,
      codes: [...byMonth.get(month)!].sort()
    };
  });
}

/** Folds a finished `fetch` into `table`. Requested codes count as covered even when no rows came back. */
export function mergeRates(table: RateTable, fetch: RateFetch, rows: RateRow[]): RateTable {
  const rates = { ...table.rates };
  for (const row of rows) {
    rates[row.code] = { ...(rates[row.code] ?? {}), [row.date]: row.rate };
  }
  const coverage = { ...table.coverage };
  for (const code of fetch.codes) {
    const months = coverage[code] ?? {};
    const current = months[fetch.month];
    if (current === undefined || current < fetch.to) coverage[code] = { ...months, [fetch.month]: fetch.to };
  }
  return { rates, coverage };
}

/** Units of `code` per one EUR on `date`, or on the closest earlier day within `LOOKBACK_DAYS`. */
export function pivotRate(table: RateTable, code: string, date: string): number | null {
  if (code === PIVOT) return 1;
  const byDate = table.rates[code];
  if (!byDate) return null;
  let day = date;
  for (let i = 0; i <= LOOKBACK_DAYS; i++) {
    const rate = byDate[day];
    if (rate !== undefined) return rate;
    day = addDays(day, -1);
  }
  return null;
}

/** `amount` of `from` expressed in `to` at `date`'s rate; null when a rate is not cached. */
export function convert(table: RateTable, amount: number, from: string, to: string, date: string): number | null {
  if (from === to) return amount;
  const fromRate = pivotRate(table, from, date);
  const toRate = pivotRate(table, to, date);
  if (fromRate === null || toRate === null) return null;
  return (amount * toRate) / fromRate;
}

/** Sum of `amounts` (currency code, amount) expressed in `to` at `date`'s rates; null when a rate is missing. */
export function convertSum(table: RateTable, amounts: Array<[string, number]>, to: string, date: string): number | null {
  let sum = 0;
  for (const [code, amount] of amounts) {
    const converted = convert(table, amount, code, to, date);
    if (converted === null) return null;
    sum += converted;
  }
  return sum;
}

/** Currencies that stop `codes` from being converted into `to` on `date`. */
export function missingCodes(table: RateTable, codes: string[], to: string, date: string): string[] {
  const foreign = [...new Set(codes.filter((code) => code !== to))];
  if (foreign.length === 0) return [];
  return [...foreign, to].filter((code) => pivotRate(table, code, date) === null).sort();
}

// ------------------------------------------------------ filtered balance

/** One signed movement of money, in the currency (`code`) of the account it touched. */
export interface BalanceLeg {
  code: string;
  date: string;
  amount: number;
}

export interface BaseCurrencyBalance {
  income: number;
  expense: number;
  net: number;
}

/** `balance` is null while any rate is missing; `missingCodes` then names the currencies without one. */
export interface BaseBalanceResult {
  balance: BaseCurrencyBalance | null;
  missingCodes: string[];
}

/**
 * Deposits add, withdrawals subtract. Transfers only count when the list is
 * limited to one account (`accountFilterId`): incoming adds the destination
 * amount, outgoing subtracts the source amount — the account balance formula.
 * Without an account filter a transfer just moves money inside the set.
 * Dates after `today` are valued at `today`'s rate.
 */
export function balanceLegs(
  transactions: Transaction[],
  accountFilterId: number | null,
  accountCodes: Map<number, string>,
  today: string
): BalanceLeg[] {
  const legs: BalanceLeg[] = [];
  for (const tx of transactions) {
    const txDate = datePart(tx.transDate);
    const date = txDate < today ? txDate : today;
    const push = (accountId: number, amount: number): void => {
      const code = accountCodes.get(accountId);
      if (code !== undefined) legs.push({ code, date, amount });
    };
    if (tx.type === 'Deposit') push(tx.accountId, tx.transAmount);
    else if (tx.type === 'Withdrawal') push(tx.accountId, -tx.transAmount);
    else if (accountFilterId !== null) {
      if (tx.toAccountId === accountFilterId) push(tx.toAccountId, tx.toTransAmount);
      else if (tx.accountId === accountFilterId) push(tx.accountId, -tx.transAmount);
    }
  }
  return legs;
}

/** Rates needed to convert `legs` into `baseCode`: the leg's currency and the base, per day. */
export function balanceNeeds(legs: BalanceLeg[], baseCode: string): RateNeed[] {
  const seen = new Map<string, RateNeed>();
  for (const leg of legs) {
    if (leg.code === baseCode) continue;
    for (const code of [leg.code, baseCode]) seen.set(`${code}|${leg.date}`, { code, date: leg.date });
  }
  return [...seen.values()];
}

export function computeBaseBalance(legs: BalanceLeg[], baseCode: string, table: RateTable): BaseBalanceResult {
  let income = 0;
  let expense = 0;
  const missing = new Set<string>();
  for (const leg of legs) {
    const converted = convert(table, leg.amount, leg.code, baseCode, leg.date);
    if (converted === null) {
      missing.add(pivotRate(table, leg.code, leg.date) === null ? leg.code : baseCode);
      continue;
    }
    if (converted >= 0) income += converted;
    else expense -= converted;
  }
  return missing.size === 0
    ? { balance: { income, expense, net: income - expense }, missingCodes: [] }
    : { balance: null, missingCodes: [...missing].sort() };
}
