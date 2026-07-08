package com.konhit.financeapp.domain.usecase

data class BudgetUsage(
    val currencyId: Long,
    val spent: Double,
    val budget: Double,
    val actualPercent: Double,
    val expectedPercent: Double
)

class ComputeBudgetUsageUseCase {
    operator fun invoke(
        budgets: Map<Long, Double>,
        expenses: Map<Long, Double>,
        dayOfMonth: Int,
        daysInMonth: Int
    ): List<BudgetUsage> {
        val expectedPercent = (dayOfMonth.toDouble() / daysInMonth) * 100.0
        return budgets
            .filterValues { it > 0 }
            .map { (currencyId, budget) ->
                val spent = expenses[currencyId] ?: 0.0
                BudgetUsage(
                    currencyId      = currencyId,
                    spent           = spent,
                    budget          = budget,
                    actualPercent   = (spent / budget) * 100.0,
                    expectedPercent = expectedPercent
                )
            }
    }
}
