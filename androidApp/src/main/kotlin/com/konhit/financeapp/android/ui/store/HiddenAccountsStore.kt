package com.konhit.financeapp.android.ui.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HiddenAccountsStore(
    private val dataStore: DataStore<Preferences>
) {
    private val key = stringPreferencesKey("hidden_account_ids")

    val hiddenIds: Flow<Set<Long>> = dataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptySet()
        raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun toggle(id: Long) {
        dataStore.edit { prefs ->
            val current = (prefs[key] ?: "")
                .split(",").mapNotNull { it.toLongOrNull() }.toMutableSet()
            if (id in current) current.remove(id) else current.add(id)
            prefs[key] = current.joinToString(",")
        }
    }
}
