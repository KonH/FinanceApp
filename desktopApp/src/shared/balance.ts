export interface BalanceComponents {
  initialBal: number;
  totalDeposits: number;
  totalWithdrawals: number;
  transfersIn: number;
  transfersOut: number;
}

/**
 * MMEX balance formula — must stay identical to `ComputeBalanceUseCase`:
 *
 * balance = INITIALBAL + deposits - withdrawals + transfers in - transfers out
 */
export function computeBalance(c: BalanceComponents): number {
  return c.initialBal + c.totalDeposits - c.totalWithdrawals + c.transfersIn - c.transfersOut;
}

export interface BudgetUsage {
  currencyId: number;
  spent: number;
  budget: number;
  actualPercent: number;
  expectedPercent: number;
}

/** Port of `ComputeBudgetUsageUseCase`. */
export function computeBudgetUsage(
  budgets: Record<number, number>,
  expenses: Record<number, number>,
  dayOfMonth: number,
  daysInMonth: number
): BudgetUsage[] {
  const expectedPercent = (dayOfMonth / daysInMonth) * 100;
  return Object.entries(budgets)
    .filter(([, budget]) => budget > 0)
    .map(([currencyId, budget]) => {
      const id = Number(currencyId);
      const spent = expenses[id] ?? 0;
      return {
        currencyId: id,
        spent,
        budget,
        actualPercent: (spent / budget) * 100,
        expectedPercent
      };
    });
}
