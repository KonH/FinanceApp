package com.konhit.financeapp.domain.usecase

class ComputeBalanceUseCase {
    operator fun invoke(
        initialBal: Double,
        totalDeposits: Double,
        totalWithdrawals: Double,
        transfersIn: Double,
        transfersOut: Double
    ): Double = initialBal + totalDeposits - totalWithdrawals + transfersIn - transfersOut
}
