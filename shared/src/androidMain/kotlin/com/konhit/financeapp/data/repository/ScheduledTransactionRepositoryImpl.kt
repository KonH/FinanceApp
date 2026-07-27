package com.konhit.financeapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.konhit.financeapp.db.BILLSDEPOSITS_V1
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.ScheduledTransaction
import com.konhit.financeapp.domain.model.TransactionType
import com.konhit.financeapp.domain.repository.ScheduledTransactionRepository
import com.konhit.financeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ScheduledTransactionRepositoryImpl(
    private val holder: DatabaseHolder,
    private val settings: SettingsRepository
) : ScheduledTransactionRepository {

    override fun observeAllOrdered(): Flow<List<ScheduledTransaction>> =
        holder.requireDb().billsDepositsQueries.selectAllOrdered()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { mapRow(it) } }

    override suspend fun getAllOrdered(): List<ScheduledTransaction> = withContext(Dispatchers.IO) {
        holder.requireDb().billsDepositsQueries.selectAllOrdered().executeAsList().map { mapRow(it) }
    }

    override suspend fun getById(bdId: Long): ScheduledTransaction? = withContext(Dispatchers.IO) {
        holder.requireDb().billsDepositsQueries.selectById(bdId).executeAsOneOrNull()?.let { mapRow(it) }
    }

    override suspend fun getDue(todayIsoDate: String): List<ScheduledTransaction> = withContext(Dispatchers.IO) {
        holder.requireDb().billsDepositsQueries.selectDue(todayIsoDate).executeAsList().map { mapRow(it) }
    }

    override suspend fun insert(scheduled: ScheduledTransaction) = withContext(Dispatchers.IO) {
        checkWritable()
        holder.requireDb().billsDepositsQueries.insert(
            bdId = scheduled.bdId,
            accountId = scheduled.accountId,
            toAccountId = scheduled.toAccountId,
            payeeId = scheduled.payeeId,
            transCode = scheduled.type.mmexCode,
            transAmount = scheduled.transAmount,
            status = scheduled.status ?: "",
            transactionNumber = scheduled.transactionNumber,
            notes = scheduled.notes,
            categId = scheduled.categId,
            transDate = scheduled.nextOccurrenceDate,
            followupId = scheduled.followupId,
            toTransAmount = scheduled.toTransAmount,
            repeats = scheduled.repeats.toLong(),
            nextOccurrenceDate = scheduled.nextOccurrenceDate,
            numOccurrences = scheduled.numOccurrences?.toLong(),
            color = scheduled.color.toLong()
        )
    }

    override suspend fun update(scheduled: ScheduledTransaction) = withContext(Dispatchers.IO) {
        checkWritable()
        holder.requireDb().billsDepositsQueries.update(
            accountId = scheduled.accountId,
            toAccountId = scheduled.toAccountId,
            payeeId = scheduled.payeeId,
            transCode = scheduled.type.mmexCode,
            transAmount = scheduled.transAmount,
            status = scheduled.status ?: "",
            transactionNumber = scheduled.transactionNumber,
            notes = scheduled.notes,
            categId = scheduled.categId,
            transDate = scheduled.nextOccurrenceDate,
            followupId = scheduled.followupId,
            toTransAmount = scheduled.toTransAmount,
            repeats = scheduled.repeats.toLong(),
            nextOccurrenceDate = scheduled.nextOccurrenceDate,
            numOccurrences = scheduled.numOccurrences?.toLong(),
            color = scheduled.color.toLong(),
            bdId = scheduled.bdId
        )
    }

    override suspend fun delete(bdId: Long) = withContext(Dispatchers.IO) {
        checkWritable()
        holder.requireDb().billsDepositsQueries.delete(bdId)
    }

    private suspend fun checkWritable() {
        check(settings.getAccessMode() == AccessMode.READ_WRITE) {
            "Cannot write in read-only mode"
        }
    }

    private fun mapRow(row: BILLSDEPOSITS_V1) = ScheduledTransaction(
        bdId = row.BDID,
        accountId = row.ACCOUNTID,
        toAccountId = row.TOACCOUNTID ?: -1L,
        payeeId = row.PAYEEID,
        type = TransactionType.fromMmex(row.TRANSCODE),
        transAmount = row.TRANSAMOUNT,
        toTransAmount = row.TOTRANSAMOUNT ?: 0.0,
        categId = row.CATEGID,
        notes = row.NOTES,
        nextOccurrenceDate = row.NEXTOCCURRENCEDATE ?: row.TRANSDATE ?: "",
        repeats = row.REPEATS?.toInt() ?: 0,
        numOccurrences = row.NUMOCCURRENCES?.toInt(),
        status = row.STATUS,
        transactionNumber = row.TRANSACTIONNUMBER,
        color = row.COLOR?.toInt() ?: -1,
        followupId = row.FOLLOWUPID ?: -1L
    )
}
