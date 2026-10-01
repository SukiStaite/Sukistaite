package com.sukistaite.reader.data

import kotlinx.serialization.Serializable

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 一条解析后的章/节标题 */
data class Heading(
    val line: Int,      // 在全文中的行号（0-based）
    val level: Int,     // 1 = 章，2 = 节
    val title: String,
    val fullText: String
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
 * 若用户在关于页下载过内容更新（filesDir/sukistaite_content.txt），优先使用它。
 * 单例，整个应用共享一份解析结果。
 */
object DocRepository {
    @Volatile private var cachedLines: List<String>? = null
    @Volatile private var cachedHeadings: List<Heading>? = null

    /** assets 原始内容文件（用于下载更新时覆盖写入） */
    fun contentFile(context: Context): File = java.io.File(context.filesDir, "sukistaite_content.txt")

    fun invalidateCache() {
        cachedLines = null
        cachedHeadings = null
    }

    /** 优先读下载更新的内容文件，否则读 assets */
    fun loadLines(context: Context): List<String> {
        cachedLines?.let { return it }
        val f = contentFile(context)
        val text = if (f.exists()) f.readText() else context.assets.open("sukistaite.txt").bufferedReader().use { it.readText() }
        val lines = text.lines()
        cachedLines = lines
        return lines
    }

    /** 解析章节标题：第X章/章X = level 1；第X节/一、二、 = level 2 */
    fun loadHeadings(context: Context): List<Heading> {
        cachedHeadings?.let { return it }
        val chapterRe = Regex("^第[一二三四五六七八九十百千0-9]+章")
        val sectionRe = Regex("^(第[一二三四五六七八九十百千0-9]+节|[一二三四五六七八九十]+、)")
        val headings = mutableListOf<Heading>()
        loadLines(context).forEachIndexed { i, line ->
            when {
                chapterRe.containsMatchIn(line) -> headings.add(Heading(i, 1, line.trim(), line))
                sectionRe.containsMatchIn(line) -> headings.add(Heading(i, 2, line.trim(), line))
            }
        }
        cachedHeadings = headings
        headings
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

    /** 全半角宽松归一化（仅 fuzzy 模式用） */
    private fun normalize(s: String, caseSensitive: Boolean): String {
        val t = s.replace('：', ':').replace('（', '(').replace('）', ')').replace('，', ',')
        return if (caseSensitive) t else t.lowercase()
    }

    /**
     * v1.2 统一搜索入口。
     * mode: "exact" 精确包含 / "fuzzy" 忽略大小写+全半角宽松 / "regex" 正则
     * scope: "all" 全文 / "titles" 仅标题 / "bookmarks" 仅书签内容
     * wholeWord / caseSensitive: 仅 exact/fuzzy 生效
     */
    suspend fun search(
        context: Context,
        query: String,
        mode: String = "exact",
        scope: String = "all",
        wholeWord: Boolean = false,
        caseSensitive: Boolean = false
    ): List<SearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val headings = loadHeadings(context)
        val bookmarkLines = if (scope == "bookmarks") {
            BookmarkStore(context).bookmarksList().map { it.line }.toSet()
        } else emptySet()

        fun lineMatchesScope(i: Int, raw: String): Boolean = when (scope) {
            "titles" -> headings.any { it.line == i }
            "bookmarks" -> i in bookmarkLines
            else -> true
        }

        // 构造匹配函数（显式类型标注，避免类型推断歧义）
        val matches: (String) -> Boolean = when (mode) {
            "regex" -> {
                val re = try { Regex(q, if (caseSensitive) setOf<RegexOption>() else setOf(RegexOption.IGNORE_CASE)) } catch (e: Exception) { null }
                if (re == null) ({ _: String -> false }) else ({ s: String -> re.containsMatchIn(s) })
            }
            "fuzzy" -> {
                val norm = normalize(q, caseSensitive)
                ({ s: String -> normalize(s, caseSensitive).contains(norm) })
            }
            else -> {
                if (wholeWord) {
                    val re = Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(q) + "($|[^\\p{L}\\p{N}])",
                        if (caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE))
                    ({ s: String -> re.containsMatchIn(s) })
                } else if (caseSensitive) {
                    ({ s: String -> s.contains(q) })
                } else {
                    ({ s: String -> s.lowercase().contains(q.lowercase()) })
                }
            }
        }

        val hits = mutableListOf<SearchHit>()
        outer@ for ((i, raw) in loadLines(context).withIndex()) {
            if (!lineMatchesScope(i, raw)) continue
            if (matches(raw)) {
                hits.add(SearchHit(i, raw.trim(), chapterOf(headings, i)))
                if (hits.size >= 500) break@outer
            }
        }
        hits
    }
}
