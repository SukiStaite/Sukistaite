package com.sukistaite.reader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "suki_reader")

/**
 * 书签存储：用 DataStore 持久化一个 JSON 列表。
 */
class BookmarkStore(private val context: Context) {

    companion object {
        private val KEY_BOOKMARKS = stringPreferencesKey("bookmarks_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val bookmarks: Flow<List<Bookmark>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_BOOKMARKS] ?: "[]"
        try {
            json.decodeFromString<List<Bookmark>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun add(bookmark: Bookmark) {
        context.dataStore.edit { prefs ->
            val current = try {
                json.decodeFromString<List<Bookmark>>(prefs[KEY_BOOKMARKS] ?: "[]")
            } catch (e: Exception) { emptyList() }
            val next = (current.filter { it.line != bookmark.line } + bookmark)
                .sortedBy { it.line }
            prefs[KEY_BOOKMARKS] = json.encodeToString(next)
        }
    }

    suspend fun remove(line: Int) {
        context.dataStore.edit { prefs ->
            val current = try {
                json.decodeFromString<List<Bookmark>>(prefs[KEY_BOOKMARKS] ?: "[]")
            } catch (e: Exception) { emptyList() }
            prefs[KEY_BOOKMARKS] = json.encodeToString(current.filter { it.line != line })
        }
    }

    /** 云同步恢复：整体替换本地书签（按行号排序去重） */
    suspend fun restoreAll(list: List<Bookmark>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BOOKMARKS] = json.encodeToString(list.distinctBy { it.line }.sortedBy { it.line })
        }
    }

    suspend fun toggle(line: Int, title: String, snippet: String): Boolean {
        val current = bookmarksList()
        return if (current.any { it.line == line }) {
            remove(line); false
        } else {
            add(Bookmark(line, title, snippet, System.currentTimeMillis())); true
        }
    }

    suspend fun bookmarksList(): List<Bookmark> = bookmarks.first()
}
