package com.konhit.financeapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.Transaction
import com.konhit.financeapp.domain.model.TransactionType
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext

class TransactionRepositoryImpl(
    private val holder: DatabaseHolder,
    private val settings: SettingsRepository
) : TransactionRepository {

    override fun observeAnyChange(): Flow<Unit> =
        holder.requireDb().transactionQueries.countAll()
            .asFlow().mapToOne(Dispatchers.IO).drop(1).map { }

    override fun observeByAccount(accountId: Long): Flow<List<Transaction>> =
        holder.requireDb().transactionQueries
            .selectByAccount(accountId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { row -> mapRow(row.TRANSID, row.ACCOUNTID, row.TOACCOUNTID, row.PAYEEID, row.TRANSCODE, row.TRANSAMOUNT, row.TOTRANSAMOUNT, row.CATEGID, row.TRANSDATE, row.LASTUPDATEDTIME, row.NOTES) } }

    override suspend fun getByAccount(accountId: Long): List<Transaction> = withContext(Dispatchers.IO) {
        holder.requireDb().transactionQueries.selectByAccount(accountId).executeAsList().map { row ->
            mapRow(row.TRANSID, row.ACCOUNTID, row.TOACCOUNTID, row.PAYEEID, row.TRANSCODE, row.TRANSAMOUNT, row.TOTRANSAMOUNT, row.CATEGID, row.TRANSDATE, row.LASTUPDATEDTIME, row.NOTES)
        }
    }

    override suspend fun getById(transId: Long): Transaction? = withContext(Dispatchers.IO) {
        holder.requireDb().transactionQueries.selectById(transId).executeAsOneOrNull()?.let { row ->
            mapRow(row.TRANSID, row.ACCOUNTID, row.TOACCOUNTID, row.PAYEEID, row.TRANSCODE, row.TRANSAMOUNT, row.TOTRANSAMOUNT, row.CATEGID, row.TRANSDATE, row.LASTUPDATEDTIME, row.NOTES)
        }
    }

    override suspend fun insert(transaction: Transaction) = withContext(Dispatchers.IO) {
        checkWritable()
        holder.requireDb().transactionQueries.insert(
            transId         = transaction.transId,
            accountId       = transaction.accountId,
            toAccountId     = transaction.toAccountId,
            payeeId         = transaction.payeeId,
            transCode       = transaction.type.mmexCode,
            transAmount     = transaction.transAmount,
            toTransAmount   = transaction.toTransAmount,
            notes           = transaction.notes,
            categId         = transaction.categId,
            transDate       = transaction.transDate,
            lastUpdatedTime = transaction.lastUpdatedTime
        )
    }

    override suspend fun update(transaction: Transaction) = withContext(Dispatchers.IO) {
        checkWritable()
        holder.requireDb().transactionQueries.update(
            transCode       = transaction.type.mmexCode,
            accountId       = transaction.accountId,
            toAccountId     = transaction.toAccountId,
            transAmount     = transaction.transAmount,
            toTransAmount   = transaction.toTransAmount,
            categId         = transaction.categId,
            transDate       = transaction.transDate,
            notes           = transaction.notes,
            lastUpdatedTime = transaction.lastUpdatedTime,
            transId         = transaction.transId
        )
    }

    override suspend fun delete(transId: Long) = withContext(Dispatchers.IO) {
        checkWritable()
        holder.requireDb().transactionQueries.delete(transId)
    }

    override suspend fun search(query: String): List<Transaction> = withContext(Dispatchers.IO) {
        holder.requireDb().transactionQueries.search(query).executeAsList().map { row ->
            mapRow(row.TRANSID, row.ACCOUNTID, row.TOACCOUNTID, row.PAYEEID, row.TRANSCODE, row.TRANSAMOUNT, row.TOTRANSAMOUNT, row.CATEGID, row.TRANSDATE, row.LASTUPDATEDTIME, row.NOTES)
        }
    }

    private suspend fun checkWritable() {
        check(settings.getAccessMode() == AccessMode.READ_WRITE) {
            "Cannot write in read-only mode"
        }
    }

    private fun mapRow(
        transId: Long, accountId: Long, toAccountId: Long, payeeId: Long,
        transCode: String, transAmount: Double, toTransAmount: Double,
        categId: Long?, transDate: String, lastUpdatedTime: String?, notes: String?
    ) = Transaction(
        transId         = transId,
        accountId       = accountId,
        toAccountId     = toAccountId,
        payeeId         = payeeId,
        type            = TransactionType.fromMmex(transCode),
        transAmount     = transAmount,
        toTransAmount   = toTransAmount,
        categId         = categId,
        transDate       = transDate,
        lastUpdatedTime = lastUpdatedTime,
        notes           = notes
    )
}
