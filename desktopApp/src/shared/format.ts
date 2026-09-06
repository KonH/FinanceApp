import type { Currency } from './types';

/**
 * Port of `CurrencyFormatter.formatAmount` — MMEX per-currency separators,
 * two decimals, symbol prefix.
 */
export function formatAmount(amount: number, currency?: Currency | null): string {
  const decSep = currency?.decimalPoint || '.';
  const grpSep = currency?.groupSeparator || ' ';
  const symbol = currency?.currencySymbol ?? currency?.pfxSymbol ?? '';

  const abs = Math.abs(amount);
  const intPart = Math.trunc(abs);
  let fracPart = Math.round((abs - intPart) * 100);
  let carriedInt = intPart;
  if (fracPart === 100) {
    fracPart = 0;
    carriedInt += 1;
  }

  const withSeparators = String(carriedInt)
    .split('')
    .reverse()
    .join('')
    .replace(/(\d{3})(?=\d)/g, `$1${grpSep}`)
    .split('')
    .reverse()
    .join('');

  const formatted =
    (amount < 0 ? '-' : '') + withSeparators + decSep + String(fracPart).padStart(2, '0');

  return symbol ? `${symbol} ${formatted}` : formatted;
}

/** Whole numbers render without a decimal tail, matching the Android form fields. */
export function amountToInput(value: number): string {
  return Number.isFinite(value) && Math.floor(value) === value ? String(Math.trunc(value)) : String(value);
}

export function currencyLabel(currency: Currency | null | undefined): string {
  if (!currency) return '';
  return currency.currencySymbol || currency.pfxSymbol || currency.name;
}
