package com.konhit.financeapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.usecase.ComputeBalanceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccountRepositoryImpl(
    private val holder: DatabaseHolder,
    private val computeBalance: ComputeBalanceUseCase
) : AccountRepository {

    override fun observeAll(): Flow<List<Account>> =
        holder.requireDb().accountListQueries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { row ->
                Account(
                    id         = row.ACCOUNTID,
                    name       = row.ACCOUNTNAME,
                    type       = row.ACCOUNTTYPE,
                    initialBal = row.INITIALBAL,
                    currencyId = row.CURRENCYID,
                    status     = row.STATUS
                )
            }}

    override suspend fun getAll(): List<Account> = withContext(Dispatchers.IO) {
        holder.requireDb().accountListQueries.selectAll().executeAsList().map { row ->
            Account(
                id         = row.ACCOUNTID,
                name       = row.ACCOUNTNAME,
                type       = row.ACCOUNTTYPE,
                initialBal = row.INITIALBAL,
                currencyId = row.CURRENCYID,
                status     = row.STATUS,
                balance    = getBalance(row.ACCOUNTID)
            )
        }
    }

    override suspend fun getById(id: Long): Account? = withContext(Dispatchers.IO) {
        holder.requireDb().accountListQueries.selectById(id).executeAsOneOrNull()?.let { row ->
            Account(
                id         = row.ACCOUNTID,
                name       = row.ACCOUNTNAME,
                type       = row.ACCOUNTTYPE,
                initialBal = row.INITIALBAL,
                currencyId = row.CURRENCYID,
                status     = row.STATUS,
                balance    = getBalance(row.ACCOUNTID)
            )
        }
    }

    override suspend fun getBalance(id: Long): Double = withContext(Dispatchers.IO) {
        // balanceComponents query uses AS aliases: initialBal, totalDeposits, totalWithdrawals, transfersIn, transfersOut
        val c = holder.requireDb().transactionQueries.balanceComponents(id).executeAsOne()
        computeBalance(c.initialBal, c.totalDeposits, c.totalWithdrawals, c.transfersIn, c.transfersOut)
    }

    override suspend fun insert(account: Account) = withContext(Dispatchers.IO) {
        holder.requireDb().accountListQueries.insert(
            accountId  = account.id,
            name       = account.name,
            type       = account.type,
            initialBal = account.initialBal,
            currencyId = account.currencyId
        )
    }

    override suspend fun update(account: Account) = withContext(Dispatchers.IO) {
        holder.requireDb().accountListQueries.update(
            name       = account.name,
            type       = account.type,
            initialBal = account.initialBal,
            currencyId = account.currencyId,
            accountId  = account.id
        )
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        holder.requireDb().accountListQueries.delete(id)
    }
}
