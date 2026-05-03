package com.konhit.financeapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.domain.model.Category
import com.konhit.financeapp.domain.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CategoryRepositoryImpl(private val holder: DatabaseHolder) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        holder.requireDb().categoryQueries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { Category(it.CATEGID, it.CATEGNAME, it.PARENTID) } }

    override suspend fun getAll(): List<Category> = withContext(Dispatchers.IO) {
        holder.requireDb().categoryQueries.selectAll().executeAsList()
            .map { Category(it.CATEGID, it.CATEGNAME, it.PARENTID) }
    }

    override suspend fun insert(category: Category) = withContext(Dispatchers.IO) {
        holder.requireDb().categoryQueries.insert(category.id, category.name, category.parentId)
    }

    override suspend fun update(category: Category) = withContext(Dispatchers.IO) {
        holder.requireDb().categoryQueries.update(category.name, category.parentId, category.id)
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        holder.requireDb().categoryQueries.delete(id)
    }

    override suspend fun hasTransactions(id: Long): Boolean = withContext(Dispatchers.IO) {
        holder.requireDb().categoryQueries.hasTransactions(id).executeAsOne() > 0
    }
}
