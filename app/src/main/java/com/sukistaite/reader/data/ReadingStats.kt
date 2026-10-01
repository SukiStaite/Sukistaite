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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.statsStore: DataStore<Preferences> by preferencesDataStore(name = "suki_stats")

/** 一条阅读历史 */
@Serializable
data class HistoryEntry(
    val time: Long,
    val chapter: String,
    val line: Int
)

/** 阅读统计快照 */
data class ReadingStats(
    val totalSeconds: Int = 0,
    val daily: Map<String, Int> = emptyMap(),      // "yyyy-MM-dd" -> 秒
    val chapters: Map<String, Int> = emptyMap(),   // 章节名 -> 秒
    val history: List<HistoryEntry> = emptyList()
)

object StatsCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun decodeMap(s: String?): Map<String, Int> = try {
        json.decodeFromString<Map<String, Int>>(s ?: "{}")
    } catch (e: Exception) { emptyMap() }
    fun encodeMap(m: Map<String, Int>): String = json.encodeToString(m)
    fun decodeHistory(s: String?): List<HistoryEntry> = try {
        json.decodeFromString<List<HistoryEntry>>(s ?: "[]")
    } catch (e: Exception) { emptyList() }
    fun encodeHistory(l: List<HistoryEntry>): String = json.encodeToString(l.take(100))
}

/** v1.4 阅读统计：累计/每日/每章节时长 + 历史时间线（本地 DataStore） */
class ReadingStatsStore(private val context: Context) {

    private object K {
        val total = stringPreferencesKey("total_seconds")
        val daily = stringPreferencesKey("daily_json")
        val chapters = stringPreferencesKey("chapters_json")
        val history = stringPreferencesKey("history_json")
    }

    val stats: Flow<ReadingStats> = context.statsStore.data.map { p ->
        ReadingStats(
            totalSeconds = p[K.total]?.toIntOrNull() ?: 0,
            daily = StatsCodec.decodeMap(p[K.daily]),
            chapters = StatsCodec.decodeMap(p[K.chapters]),
            history = StatsCodec.decodeHistory(p[K.history])
        )
    }

    /** 累计阅读秒数（阅读器每 15 秒上报一次） */
    suspend fun addSeconds(chapter: String, sec: Int) {
        context.statsStore.edit { p ->
            val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            p[K.total] = ((p[K.total]?.toIntOrNull() ?: 0) + sec).toString()
            val d = StatsCodec.decodeMap(p[K.daily]).toMutableMap()
            d[day] = (d[day] ?: 0) + sec
            p[K.daily] = StatsCodec.encodeMap(d)
            val c = StatsCodec.decodeMap(p[K.chapters]).toMutableMap()
            c[chapter] = (c[chapter] ?: 0) + sec
            p[K.chapters] = StatsCodec.encodeMap(c)
        }
    }

    /** 记录一次打开（历史时间线，保留最近 100 条） */
    suspend fun addHistory(chapter: String, line: Int) {
        context.statsStore.edit { p ->
            val cur = StatsCodec.decodeHistory(p[K.history]).toMutableList()
            cur.add(0, HistoryEntry(System.currentTimeMillis(), chapter, line))
            p[K.history] = StatsCodec.encodeHistory(cur)
        }
    }

    suspend fun snapshot(): ReadingStats = stats.first()

    /** 整体恢复（备份导入用） */
    suspend fun restore(s: ReadingStats) {
        context.statsStore.edit { p ->
            p[K.total] = s.totalSeconds.toString()
            p[K.daily] = StatsCodec.encodeMap(s.daily)
            p[K.chapters] = StatsCodec.encodeMap(s.chapters)
            p[K.history] = StatsCodec.encodeHistory(s.history)
        }
    }
}

/**
 * v1.4 数据备份：书签 + 高亮 + 进度 + 主题外观 + 统计 + 搜索历史 一键导出/导入（JSON）。
 * 背景图片 URI 不导出（跨设备无效）。
 */
object BackupManager {

    @kotlinx.serialization.Serializable
    data class Backup(
        val app: String = "SukiReader",
        val version: Int = 1,
        val exportedAt: Long = 0,
        val theme: ThemeExport? = null,
        val bookmarks: List<Bookmark> = emptyList(),
        val highlights: List<Int> = emptyList(),
        val progress: Map<String, Int> = emptyMap(),
        val searchHistory: List<String> = emptyList(),
        val stats: StatsBackup? = null
    )

    @kotlinx.serialization.Serializable
    data class StatsBackup(
        val totalSeconds: Int = 0,
        val daily: Map<String, Int> = emptyMap(),
        val chapters: Map<String, Int> = emptyMap(),
        val history: List<HistoryEntry> = emptyList()
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun export(
        settingsStore: SettingsStore,
        bookmarkStore: BookmarkStore,
        statsStore: ReadingStatsStore
    ): String {
        val s = settingsStore.snapshot()
        val st = statsStore.snapshot()
        val backup = Backup(
            exportedAt = System.currentTimeMillis(),
            theme = ThemeExport(
                themeMode = s.themeMode.name,
                customPrimary = s.customPrimary,
                paperStyle = s.paperStyle.name,
                glassStrength = s.glassStrength,
                fontFamily = s.fontFamily,
                fontScale = s.fontScale,
                exportedAt = System.currentTimeMillis()
            ),
            bookmarks = bookmarkStore.bookmarksList(),
            highlights = s.highlights.toList(),
            progress = s.progress,
            searchHistory = s.searchHistory,
            stats = StatsBackup(st.totalSeconds, st.daily, st.chapters, st.history)
        )
        return json.encodeToString(backup)
    }

    /** 导入并应用。返回应用的条目数描述。 */
    suspend fun import(
        text: String,
        settingsStore: SettingsStore,
        bookmarkStore: BookmarkStore,
        statsStore: ReadingStatsStore
    ): Result<String> = try {
        val b = json.decodeFromString<Backup>(text.trim())
        b.theme?.let { t ->
            settingsStore.setTheme(ThemeMode.from(t.themeMode))
            settingsStore.setCustomPrimary(t.customPrimary)
            settingsStore.setPaperStyle(PaperStyle.from(t.paperStyle))
            settingsStore.setGlassStrength(t.glassStrength)
            settingsStore.setFontFamily(t.fontFamily)
            settingsStore.setFontScale(t.fontScale)
        }
        if (b.bookmarks.isNotEmpty()) bookmarkStore.replaceAll(b.bookmarks)
        if (b.highlights.isNotEmpty()) settingsStore.setHighlights(b.highlights.toSet())
        if (b.progress.isNotEmpty()) settingsStore.restoreProgress(b.progress)
        if (b.searchHistory.isNotEmpty()) settingsStore.setSearchHistory(b.searchHistory)
        b.stats?.let { statsStore.restore(ReadingStats(it.totalSeconds, it.daily, it.chapters, it.history)) }
        Result.success("书签${b.bookmarks.size} · 高亮${b.highlights.size} · 进度${b.progress.size}章")
    } catch (e: Exception) {
        Result.failure(e)
    }
}
