package com.sukistaite.reader.data

import kotlinx.serialization.Serializable

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一条解析后的章/节标题 */
data class Heading(
    val line: Int,      // 在全文中的行号（0-based）
    val level: Int,     // 1 = 章，2 = 节
    val title: String,
    val fullText: String // 原始行文本（含装饰线判断用）
)

/** 一条搜索结果 */
data class SearchHit(
    val line: Int,
    val text: String,
    val chapterTitle: String
)

/** 一条书签 */
@Serializable
data class Bookmark(
    val line: Int,
    val title: String,
    val snippet: String,
    val createdAt: Long
)

/**
 * 文档仓库：加载 assets/sukistaite.txt 并解析章节结构。
 * 单例，整个应用共享一份解析结果。
 */
object DocRepository {
    @Volatile private var cachedLines: List<String>? = null
    @Volatile private var cachedHeadings: List<Heading>? = null

    suspend fun loadLines(context: Context): List<String> = withContext(Dispatchers.IO) {
        cachedLines ?: run {
            val text = context.assets.open("sukistaite.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val lines = text.split('\n')
            cachedLines = lines
            lines
        }
    }

    suspend fun loadHeadings(context: Context): List<Heading> = withContext(Dispatchers.IO) {
        cachedHeadings ?: run {
            val lines = loadLines(context)
            val headings = mutableListOf<Heading>()
            val chapterRe = Regex("""^第[零一二三四五六七八九十百]+章[：:]""")
            val sectionRe = Regex("""^第[一二三四五六七八九十百]+节[：:]""")
            lines.forEachIndexed { i, raw ->
                val line = raw.trimEnd()
                when {
                    chapterRe.containsMatchIn(line) -> headings.add(Heading(i, 1, line.trim(), line))
                    sectionRe.containsMatchIn(line) -> headings.add(Heading(i, 2, line.trim(), line))
                }
            }
            cachedHeadings = headings
            headings
        }
    }

    /** 返回某行所属的章标题（向上找最近的 level=1 标题） */
    fun chapterOf(headings: List<Heading>, line: Int): String {
        var found: Heading? = null
        for (h in headings) {
            if (h.line > line) break
            if (h.level == 1) found = h
        }
        return found?.title ?: "文档开头"
    }

    suspend fun search(context: Context, query: String, limit: Int = 300): List<SearchHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val lines = loadLines(context)
        val headings = loadHeadings(context)
        val hits = mutableListOf<SearchHit>()
        val q = query.trim()
        for ((i, raw) in lines.withIndex()) {
            if (q in raw) {
                hits.add(SearchHit(i, raw.trim(), chapterOf(headings, i)))
                if (hits.size >= limit) break
            }
        }
        hits
    }
}
