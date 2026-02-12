package com.larsaars.foldersync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_pairs")

data class SyncPairData(
    val id: Int,
    val sourceFolderUri: String? = null,
    val sourceFolderName: String? = null,
    val destFolderUri: String? = null,
    val destFolderName: String? = null
)

class SyncPairRepository(private val context: Context) {

    private val SYNC_PAIRS_KEY = stringPreferencesKey("sync_pairs")
    private val gson = Gson()

    val syncPairs: Flow<List<SyncPairData>> = context.dataStore.data.map { preferences ->
        val json = preferences[SYNC_PAIRS_KEY] ?: "[]"
        val type = object : TypeToken<List<SyncPairData>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    }

    suspend fun saveSyncPairs(pairs: List<SyncPairData>) {
        context.dataStore.edit { preferences ->
            val json = gson.toJson(pairs)
            preferences[SYNC_PAIRS_KEY] = json
        }
    }
}