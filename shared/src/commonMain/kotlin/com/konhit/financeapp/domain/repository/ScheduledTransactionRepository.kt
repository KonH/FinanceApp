package com.konhit.financeapp.domain.repository

import com.konhit.financeapp.domain.model.ScheduledTransaction
import kotlinx.coroutines.flow.Flow

interface ScheduledTransactionRepository {
    fun observeAllOrdered(): Flow<List<ScheduledTransaction>>
    suspend fun getAllOrdered(): List<ScheduledTransaction>
    suspend fun getById(bdId: Long): ScheduledTransaction?
    suspend fun getDue(todayIsoDate: String): List<ScheduledTransaction>
    suspend fun insert(scheduled: ScheduledTransaction)
    suspend fun update(scheduled: ScheduledTransaction)
    suspend fun delete(bdId: Long)
}
