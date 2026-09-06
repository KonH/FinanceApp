import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

// The shared domain is compiled to CommonJS together with the main process.
const require = createRequire(import.meta.url);
const balance = require('../dist/main/shared/balance.js');
const category = require('../dist/main/shared/category.js');
const dates = require('../dist/main/shared/mmexDate.js');
const recurrence = require('../dist/main/shared/recurrence.js');
const format = require('../dist/main/shared/format.js');

test('balance formula matches ComputeBalanceUseCase', () => {
  assert.equal(
    balance.computeBalance({
      initialBal: 100,
      totalDeposits: 50,
      totalWithdrawals: 20,
      transfersIn: 10,
      transfersOut: 5
    }),
    135
  );
});

test('budget usage reports actual and expected pace', () => {
  const [usage] = balance.computeBudgetUsage({ 2: 1000 }, { 2: 250 }, 10, 20);
  assert.equal(usage.currencyId, 2);
  assert.equal(usage.actualPercent, 25);
  assert.equal(usage.expectedPercent, 50);
});

test('category tree keeps full paths (TC-12)', () => {
  const categories = [
    { id: 1, name: 'Food', parentId: -1 },
    { id: 2, name: 'Groceries', parentId: 1 },
    { id: 3, name: 'Fruit', parentId: 2 },
    { id: 4, name: 'Transport', parentId: -1 }
  ];
  const roots = category.toTree(categories);
  assert.equal(roots.length, 2);
  assert.equal(category.buildPath(categories, 3), 'Food/Groceries/Fruit');
  assert.equal(category.buildPath(categories, 4), 'Transport');
  assert.equal(category.flattenWithDepth(roots[0]).length, 3);
  assert.deepEqual([...category.descendantIds(categories, 1)].sort(), [1, 2, 3]);
});

test('MMEX date formatting', () => {
  assert.equal(dates.formatTransDate('2026-02-03'), '2026-02-03T00:00:00');
  assert.equal(dates.datePart('2026-02-03T12:34:56'), '2026-02-03');
  assert.equal(dates.formatLastUpdated(new Date(2026, 1, 3, 4, 5, 6)), '2026-02-03T04:05:06');
});

test('date arithmetic clamps to month length', () => {
  assert.equal(dates.addMonths('2026-01-31', 1), '2026-02-28');
  assert.equal(dates.addMonths('2024-01-31', 1), '2024-02-29');
  assert.equal(dates.addDays('2026-12-31', 1), '2027-01-01');
  assert.equal(dates.lastDayOfMonth('2026-02-10'), '2026-02-28');
  // 2026-05-31 is a Sunday, so the last business day is Friday the 29th.
  assert.equal(dates.lastBusinessDayOfMonth('2026-05-02'), '2026-05-29');
  assert.equal(dates.daysInMonth(2026, 2), 28);
});

test('repeats codes round-trip through the recurrence model', () => {
  const cases = [
    [recurrence.MmexRepeats.ONCE, 'ONCE'],
    [recurrence.MmexRepeats.DAILY, 'DAILY'],
    [recurrence.MmexRepeats.MONTHLY, 'MONTHLY'],
    [recurrence.MmexRepeats.WEEKLY, 'DAY_OF_WEEK'],
    [recurrence.MmexRepeats.QUARTERLY, 'CUSTOM']
  ];
  for (const [code, mode] of cases) {
    const rec = recurrence.recurrenceFromRepeats(code, null);
    assert.equal(rec.mode, mode);
    const [repeats] = recurrence.toRepeatsAndOccurrences(rec, -1);
    assert.equal(recurrence.repeatsBase(repeats), code);
  }
});

test('auto-mode bits above 100 do not change the base code', () => {
  assert.equal(recurrence.repeatsBase(103), recurrence.MmexRepeats.MONTHLY);
  assert.equal(recurrence.repeatsBase(207), recurrence.MmexRepeats.ANNUALLY);
});

test('advance follows each MMEX repeat type', () => {
  assert.equal(recurrence.advance('2026-01-15', recurrence.MmexRepeats.WEEKLY, null), '2026-01-22');
  assert.equal(recurrence.advance('2026-01-15', recurrence.MmexRepeats.BIWEEKLY, null), '2026-01-29');
  assert.equal(recurrence.advance('2026-01-15', recurrence.MmexRepeats.MONTHLY, null), '2026-02-15');
  assert.equal(recurrence.advance('2026-01-15', recurrence.MmexRepeats.EVERY_X_DAYS, 10), '2026-01-25');
  assert.equal(recurrence.advance('2026-01-31', recurrence.MmexRepeats.MONTHLY_LAST_DAY, null), '2026-02-28');
  assert.equal(recurrence.advance('2026-01-15', recurrence.MmexRepeats.ONCE, null), null);
});

test('countRemaining counts inclusive occurrences', () => {
  assert.equal(
    recurrence.countRemaining('2026-01-01', '2026-06-01', recurrence.MmexRepeats.MONTHLY, null),
    6
  );
  assert.equal(recurrence.countRemaining('2026-01-01', '2025-01-01', recurrence.MmexRepeats.MONTHLY, null), 0);
  assert.equal(recurrence.countRemaining('2026-01-01', '2026-06-01', recurrence.MmexRepeats.ONCE, null), 1);
});

test('end date rides along in TRANSACTIONNUMBER', () => {
  assert.equal(recurrence.encodeEndDate('2026-12-31'), 'END:2026-12-31');
  assert.equal(recurrence.decodeEndDate('END:2026-12-31'), '2026-12-31');
  assert.equal(recurrence.decodeEndDate('cheque 42'), null);
  assert.equal(recurrence.decodeEndDate(null), null);
});

test('custom intervals decide whether NUMOCCURRENCES is an interval', () => {
  const everyThreeWeeks = { mode: 'CUSTOM', everyN: 3, customPeriod: 'WEEK' };
  assert.equal(recurrence.usesOccurrencesAsInterval(everyThreeWeeks), true);
  const [repeats, occurrences] = recurrence.toRepeatsAndOccurrences(everyThreeWeeks, -1);
  assert.equal(repeats, recurrence.MmexRepeats.EVERY_X_DAYS);
  assert.equal(occurrences, 21);

  const everyTwoMonths = { mode: 'CUSTOM', everyN: 2, customPeriod: 'MONTH' };
  assert.equal(recurrence.usesOccurrencesAsInterval(everyTwoMonths), false);
  assert.equal(recurrence.toRepeatsAndOccurrences(everyTwoMonths, 5)[0], recurrence.MmexRepeats.BIMONTHLY);
});

test('amount formatting mirrors CurrencyFormatter', () => {
  const eur = {
    id: 2,
    name: 'Euro',
    pfxSymbol: null,
    sfxSymbol: null,
    decimalPoint: '.',
    groupSeparator: ' ',
    scale: 100,
    currencySymbol: 'EUR'
  };
  assert.equal(format.formatAmount(334857.57, eur), 'EUR 334 857.57');
  assert.equal(format.formatAmount(-1234.5, eur), 'EUR -1 234.50');
  assert.equal(format.formatAmount(12, null), '12.00');
  assert.equal(format.amountToInput(12), '12');
  assert.equal(format.amountToInput(12.5), '12.5');
});
