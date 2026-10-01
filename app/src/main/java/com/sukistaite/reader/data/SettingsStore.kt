package com.sukistaite.reader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "suki_settings")

enum class ThemeMode {
    LIGHT, DARK, PINK;
    companion object {
        fun from(s: String?): ThemeMode = entries.firstOrNull { it.name == s } ?: PINK
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.PINK,
    val fontScale: Float = 1.0f,
    val fontFamily: String = "default",
    val novelMode: Boolean = false,
    val animations: Boolean = true,
    val bgUri: String? = null,
    val bgIsVideo: Boolean = false,
    val bgOpacity: Float = 0.25f,
    val ghToken: String = "",
    val gistId: String = "",
    val progress: Map<String, Int> = emptyMap(),
    val lastChapter: String = "",
    val contentVersion: String = "V3.1",
    val highlights: Set<Int> = emptySet(),
    val volumeKeyPaging: Boolean = true,
    val searchHistory: List<String> = emptyList()
)

/** 阅读进度 JSON 编解码（chapterKey -> 原始行号） */
object ProgressCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun decode(s: String?): Map<String, Int> = try {
        json.decodeFromString<Map<String, Int>>(s ?: "{}")
    } catch (e: Exception) {
        emptyMap()
    }

    fun encode(m: Map<String, Int>): String = json.encodeToString(m)
}

/** 全应用设置持久化（DataStore） */
class SettingsStore(private val context: Context) {

    private object K {
        val theme = stringPreferencesKey("theme_mode")
        val fontScale = floatPreferencesKey("font_scale")
        val fontFamily = stringPreferencesKey("font_family")
        val novelMode = booleanPreferencesKey("novel_mode")
        val animations = booleanPreferencesKey("animations")
        val bgUri = stringPreferencesKey("bg_uri")
        val bgIsVideo = booleanPreferencesKey("bg_is_video")
        val bgOpacity = floatPreferencesKey("bg_opacity")
        val ghToken = stringPreferencesKey("gh_token")
        val gistId = stringPreferencesKey("gist_id")
        val progress = stringPreferencesKey("progress_json")
        val lastChapter = stringPreferencesKey("last_chapter")
        val contentVersion = stringPreferencesKey("content_version")
        val highlights = stringPreferencesKey("highlights_json")
        val volumeKeyPaging = booleanPreferencesKey("volume_key_paging")
        val searchHistory = stringPreferencesKey("search_history_json")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            themeMode = ThemeMode.from(p[K.theme]),
            fontScale = p[K.fontScale] ?: 1.0f,
            fontFamily = p[K.fontFamily] ?: "default",
            novelMode = p[K.novelMode] ?: false,
            animations = p[K.animations] ?: true,
            bgUri = p[K.bgUri]?.takeIf { it.isNotBlank() },
            bgIsVideo = p[K.bgIsVideo] ?: false,
            bgOpacity = p[K.bgOpacity] ?: 0.25f,
            ghToken = p[K.ghToken] ?: "",
            gistId = p[K.gistId] ?: "",
            progress = ProgressCodec.decode(p[K.progress]),
            lastChapter = p[K.lastChapter] ?: "",
            contentVersion = p[K.contentVersion] ?: "V3.1",
            highlights = HighlightCodec.decode(p[K.highlights]),
            volumeKeyPaging = p[K.volumeKeyPaging] ?: true,
            searchHistory = HistoryCodec.decode(p[K.searchHistory])
        )
    }

    suspend fun snapshot(): AppSettings = settings.first()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit(block)
    }

    suspend fun setTheme(mode: ThemeMode) = edit { it[K.theme] = mode.name }
    suspend fun setFontScale(v: Float) = edit { it[K.fontScale] = v }
    suspend fun setFontFamily(v: String) = edit { it[K.fontFamily] = v }
    suspend fun setNovelModeDefault(v: Boolean) = edit { it[K.novelMode] = v }
    suspend fun setAnimations(v: Boolean) = edit { it[K.animations] = v }

    suspend fun setBackground(uri: String, isVideo: Boolean) = edit {
        it[K.bgUri] = uri
        it[K.bgIsVideo] = isVideo
    }

    suspend fun clearBackground() = edit {
        it[K.bgUri] = ""
        it[K.bgIsVideo] = false
    }

    suspend fun setBgOpacity(v: Float) = edit { it[K.bgOpacity] = v }
    suspend fun setToken(v: String) = edit { it[K.ghToken] = v.trim() }
    suspend fun setGistId(v: String) = edit { it[K.gistId] = v.trim() }
    suspend fun setContentVersion(v: String) = edit { it[K.contentVersion] = v }

    suspend fun saveProgress(chapter: String, line: Int) = edit { p ->
        val cur = ProgressCodec.decode(p[K.progress]).toMutableMap()
        cur[chapter] = line
        p[K.progress] = ProgressCodec.encode(cur)
    }

    suspend fun restoreProgress(m: Map<String, Int>) = edit { it[K.progress] = ProgressCodec.encode(m) }
    suspend fun setLastChapter(v: String) = edit { it[K.lastChapter] = v }

    suspend fun toggleHighlight(line: Int) = edit { p ->
        val cur = HighlightCodec.decode(p[K.highlights]).toMutableSet()
        if (!cur.add(line)) cur.remove(line)
        p[K.highlights] = HighlightCodec.encode(cur)
    }

    suspend fun setVolumeKeyPaging(v: Boolean) = edit { it[K.volumeKeyPaging] = v }

    suspend fun pushSearchHistory(q: String) = edit { p ->
        if (q.isNotBlank()) {
            val cur = HistoryCodec.decode(p[K.searchHistory]).filter { it != q }.toMutableList()
            cur.add(0, q)
            p[K.searchHistory] = HistoryCodec.encode(cur.take(20))
        }
    }

    suspend fun removeSearchHistory(q: String) = edit { p ->
        p[K.searchHistory] = HistoryCodec.encode(HistoryCodec.decode(p[K.searchHistory]).filter { it != q })
    }

    suspend fun clearSearchHistory() = edit { it[K.searchHistory] = "[]" }
}

/** 高亮行号集合 JSON 编解码 */
object HighlightCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun decode(s: String?): Set<Int> = try {
        json.decodeFromString<List<Int>>(s ?: "[]").toSet()
    } catch (e: Exception) { emptySet() }
    fun encode(s: Set<Int>): String = json.encodeToString(s.toList())
}

/** 搜索历史 JSON 编解码 */
object HistoryCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun decode(s: String?): List<String> = try {
        json.decodeFromString<List<String>>(s ?: "[]")
    } catch (e: Exception) { emptyList() }
    fun encode(l: List<String>): String = json.encodeToString(l)
}
