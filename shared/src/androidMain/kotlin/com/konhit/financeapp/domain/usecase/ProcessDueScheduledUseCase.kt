package com.konhit.financeapp.domain.usecase

import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.MmexDateFormat
import com.konhit.financeapp.domain.model.MmexRepeats
import com.konhit.financeapp.domain.model.ScheduleDateMath
import com.konhit.financeapp.domain.model.ScheduledTransaction
import com.konhit.financeapp.domain.model.Transaction
import com.konhit.financeapp.domain.model.TransactionType
import com.konhit.financeapp.domain.repository.ScheduledTransactionRepository
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.domain.repository.TransactionRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Loads due schedules and applies approve / cancel-once / delete mutations.
 */
class ProcessDueScheduledUseCase(
    private val scheduledRepo: ScheduledTransactionRepository,
    private val transactionRepo: TransactionRepository,
    private val settings: SettingsRepository,
    private val dbHolder: DatabaseHolder
) {

    suspend fun listDueToday(): List<ScheduledTransaction> {
        if (settings.getAccessMode() != AccessMode.READ_WRITE) return emptyList()
        if (dbHolder.database == null) return emptyList()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return scheduledRepo.getDue(today.toString())
    }

    /**
     * Approve the current occurrence: insert ledger row, then advance or delete schedule.
     * @return updated schedule if still present (and possibly still due), else null
     */
    suspend fun approve(scheduled: ScheduledTransaction): ScheduledTransaction? {
        val occurrenceDate = scheduled.nextOccurrenceDate
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val tx = Transaction(
            transId = System.currentTimeMillis() * 1000L,
            accountId = scheduled.accountId,
            toAccountId = if (scheduled.type == TransactionType.TRANSFER) scheduled.toAccountId else -1L,
            payeeId = dbHolder.defaultPayeeId,
            type = scheduled.type,
            transAmount = scheduled.transAmount,
            toTransAmount = if (scheduled.type == TransactionType.TRANSFER) scheduled.toTransAmount else 0.0,
            categId = scheduled.categId,
            transDate = if (occurrenceDate.contains('T')) occurrenceDate
                else MmexDateFormat.formatTransDate(ScheduleDateMath.parseDate(occurrenceDate)),
            lastUpdatedTime = MmexDateFormat.formatLastUpdated(now),
            notes = scheduled.notes,
            followupId = scheduled.bdId
        )
        transactionRepo.insert(tx)
        return advanceAfterAction(scheduled)
    }

    /** Skip this occurrence without inserting; advance or delete. */
    suspend fun cancelOnce(scheduled: ScheduledTransaction): ScheduledTransaction? =
        advanceAfterAction(scheduled)

    suspend fun delete(scheduled: ScheduledTransaction) {
        scheduledRepo.delete(scheduled.bdId)
    }

    private suspend fun advanceAfterAction(scheduled: ScheduledTransaction): ScheduledTransaction? {
        val base = MmexRepeats.base(scheduled.repeats)
        if (base == MmexRepeats.ONCE) {
            scheduledRepo.delete(scheduled.bdId)
            return null
        }

        val current = ScheduleDateMath.parseDate(scheduled.nextOccurrenceDate)
        val usesInterval = MmexRepeats.isEveryX(scheduled.repeats)
        val next = ScheduleDateMath.advance(
            from = current,
            repeats = scheduled.repeats,
            numOccurrences = if (usesInterval) scheduled.numOccurrences else null
        ) ?: run {
            scheduledRepo.delete(scheduled.bdId)
            return null
        }

        val endDate = ScheduleDateMath.decodeEndDate(scheduled.transactionNumber)
        if (endDate != null && next > endDate) {
            scheduledRepo.delete(scheduled.bdId)
            return null
        }

        var newNum = scheduled.numOccurrences
        if (!usesInterval && newNum != null && newNum > 0) {
            newNum = newNum - 1
            if (newNum <= 0) {
                scheduledRepo.delete(scheduled.bdId)
                return null
            }
        }

        val updated = scheduled.copy(
            nextOccurrenceDate = ScheduleDateMath.formatOccurrence(next),
            numOccurrences = newNum
        )
        scheduledRepo.update(updated)
        return scheduledRepo.getById(scheduled.bdId)
    }
}
