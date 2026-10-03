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

    /** 解析章节标题：第X章 = level 1；第X节/一、 = level 2（v1.5 严格模式） */
    fun loadHeadings(context: Context): List<Heading> {
        cachedHeadings?.let { return it }
        // v1.5 严格判定规则（修复目录里混入正文/目录条目的问题）：
        // 1. 章标题必须是「第X章：」——章名后紧跟全角冒号，且冒号后 2~40 字内无句号
        //    （「第一章：林晴祈（祈宝）」✓；附录目录条目「第二章 苏清辞…：9节——…」带空格无冒号紧跟 ✗；
        //     「第1章「初来乍到」（大一）：正文…」阿拉伯数字 ✗；「第二章附录·全章总目录」自引用排除）
        // 2. 节标题「第X节：」「一、」同样要求冒号/顿号后无句号
        val chapterRe = Regex("^第[一二三四五六七八九十百千零]+章[：:]")
        val sectionNumRe = Regex("^第[一二三四五六七八九十百千零]+节[：:]")
        val sectionCnRe = Regex("^[一二三四五六七八九十]+、")
        val headings = mutableListOf<Heading>()
        var inAppendixDir = false   // 附录目录区间标记（目录条目行不收）
        loadLines(context).forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed

            // 进入/退出附录目录列表区（「第X章：附录」章起，到下一个真章标题止）
            if (chapterRe.containsMatchIn(line)) {
                inAppendixDir = line.contains("附录") && line.contains("目录")
            }

            // ── 章标题 ──
            val isChapterLine = chapterRe.containsMatchIn(line) && line.length <= 60
            val isDirEntry = isChapterLine && (
                line.contains("——") ||
                Regex("章[：:][^：]{0,30}[：:]\\s*\\d+节").containsMatchIn(line) ||
                (inAppendixDir && !line.contains("附录"))
                )
            when {
                isChapterLine && !isDirEntry ->
                    headings.add(Heading(i, 1, line, raw))
                // ── 节标题（仅非章标题行判定）──
                // 排除含句号/叹号/问号的「标题样正文」（如「八、朋友圈：重要时刻帮祈宝发朋友圈。闲时刷一刷。」）
                !isChapterLine &&
                (sectionNumRe.containsMatchIn(line) || sectionCnRe.containsMatchIn(line)) &&
                line.length <= 30 &&
                !line.contains('。') && !line.contains('！') && !line.contains('？') ->
                    headings.add(Heading(i, 2, line, raw))
            }
        }
        cachedHeadings = headings
        return headings
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
