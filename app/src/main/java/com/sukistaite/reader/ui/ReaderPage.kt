package com.sukistaite.reader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukistaite.reader.data.AppSettings
import com.sukistaite.reader.data.BookmarkStore
import com.sukistaite.reader.data.DocRepository
import com.sukistaite.reader.data.Heading
import com.sukistaite.reader.data.SettingsStore
import com.sukistaite.reader.ui.components.AppTopBar
import com.sukistaite.reader.ui.components.GlassFab
import com.sukistaite.reader.ui.theme.AppFonts
import kotlinx.coroutines.launch

/**
 * 阅读器 v1.1：
 * - 双模式：普通模式（逐行还原原文）/ 小说模式（段落重排+大字号+宽边距），随时切换
 * - 文字可复制（SelectionContainer）
 * - 章节阅读记忆（滚动停止自动保存，重新进入自动恢复）
 * - 章节内容隔离：一章只显示本章内容，滚动不越过章节边界
 * - 悬浮毛玻璃按钮组（返回/收藏/模式/设置）
 */
data class ReaderParagraph(
    val startLine: Int,
    val lines: List<String>,
    val isHeading: Boolean,
    val headingLevel: Int
)

private fun buildParagraphs(lines: List<String>, headings: List<Heading>): List<ReaderParagraph> {
    val headingSet = headings.associateBy { it.line }
    val out = mutableListOf<ReaderParagraph>()
    var buf = mutableListOf<String>()
    var bufStart = -1

    fun flush() {
        if (buf.isNotEmpty()) {
            out.add(ReaderParagraph(bufStart, buf.toList(), false, 0))
            buf = mutableListOf()
            bufStart = -1
        }
    }

    lines.forEachIndexed { i, raw ->
        val h = headingSet[i]
        if (h != null) {
            flush()
            out.add(ReaderParagraph(i, listOf(raw), true, h.level))
        } else if (raw.isBlank()) {
            flush()
        } else {
            if (bufStart < 0) bufStart = i
            buf.add(raw.trim())
        }
    }
    flush()
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPage(
    chapterId: String,
    settings: AppSettings,
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bookmarkStore = remember { BookmarkStore(ctx) }
    val bookmarks by bookmarkStore.bookmarks.collectAsState(initial = emptyList())

    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var headings by remember { mutableStateOf<List<Heading>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var novelMode by remember { mutableStateOf(settings.novelMode) }

    LaunchedEffect(Unit) {
        lines = DocRepository.loadLines(ctx)
        headings = DocRepository.loadHeadings(ctx)
        loaded = true
    }

    val explicitTarget = remember(chapterId) {
        when {
            chapterId.startsWith("line-") -> chapterId.removePrefix("line-").toIntOrNull()?.minus(1)
            else -> chapterId.toIntOrNull()?.minus(1)
        }
    }

    val chapterTitle = remember(headings, explicitTarget) {
        if (headings.isEmpty()) ""
        else DocRepository.chapterOf(headings, (explicitTarget ?: 0).coerceAtLeast(0))
    }
    val chapterKey = chapterTitle.ifBlank { "文档开头" }

    // ── 章节范围：只显示当前章节内的内容，不越过边界 ──
    val chapterRange = remember(loaded, headings, explicitTarget, lines) {
        if (!loaded || lines.isEmpty()) 0..0
        else {
            val target = (explicitTarget ?: 0).coerceAtLeast(0)
            val start = headings.filter { it.line <= target }.maxByOrNull { it.line }?.line ?: 0
            val end = headings.filter { it.line > start }.minOfOrNull { it.line }?.minus(1) ?: (lines.size - 1)
            start..end
        }
    }

    val paragraphs = remember(loaded, lines, headings, chapterRange) {
        if (!loaded) emptyList()
        else buildParagraphs(lines, headings).filter { it.startLine in chapterRange }
    }

    val listState = rememberLazyListState()

    // ── 定位：显式跳转 > 章节阅读记忆 ──
    LaunchedEffect(paragraphs, chapterKey) {
        if (paragraphs.isEmpty()) return@LaunchedEffect
        val target = explicitTarget ?: settings.progress[chapterKey] ?: 0
        if (explicitTarget != null || target > 0) {
            // 钳制到当前章节范围内（章节现在只显示自己的内容）
            val clamped = target.coerceIn(chapterRange.first, chapterRange.last)
            val idx = paragraphs.indexOfFirst { it.startLine >= clamped }
            if (idx >= 0) listState.scrollToItem(idx.coerceAtMost(paragraphs.size - 1))
        }
    }

    // ── 阅读进度自动保存（滚动停止时记录当前顶部行；首次发射跳过，防覆盖恢复的进度）──
    LaunchedEffect(paragraphs, chapterKey) {
        var firstEmit = true
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (firstEmit) { firstEmit = false; return@collect }
            if (!scrolling && paragraphs.isNotEmpty()) {
                paragraphs.getOrNull(listState.firstVisibleItemIndex)?.let {
                    settingsStore.saveProgress(chapterKey, it.startLine)
                }
            }
        }
    }

    val font = AppFonts.resolve(settings.fontFamily) ?: FontFamily.Default
    val baseSize = (15 * settings.fontScale).sp
    val novelSize = (18 * settings.fontScale).sp

    val currentStartLine by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    val currentParagraph = paragraphs.getOrNull(currentStartLine)
    val isMarked = bookmarks.any { it.line == currentParagraph?.startLine }

    Scaffold(
        topBar = { AppTopBar(title = chapterTitle.ifBlank { "阅读" }, onBack = onBack) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = if (novelMode) 22.dp else 16.dp,
                        end = if (novelMode) 22.dp else 16.dp,
                        top = 12.dp,
                        bottom = 130.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (novelMode) 14.dp else 6.dp)
                ) {
                    itemsIndexed(paragraphs, key = { _, p -> p.startLine }) { _, para ->
                        if (para.isHeading) {
                            Text(
                                para.lines.first().trim(),
                                fontSize = if (para.headingLevel == 1) baseSize * 1.15f else baseSize,
                                fontWeight = FontWeight.Bold,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = if (para.headingLevel == 1) 18.dp else 10.dp)
                            )
                        } else if (novelMode) {
                            SelectionContainer {
                                Text(
                                    para.lines.joinToString(""),
                                    fontSize = novelSize,
                                    lineHeight = novelSize * 1.75f,
                                    fontFamily = font,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Column {
                                para.lines.forEach { text ->
                                    SelectionContainer {
                                        Text(
                                            text,
                                            fontSize = baseSize,
                                            lineHeight = baseSize * 1.6f,
                                            fontFamily = font,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 悬浮毛玻璃按钮组 ──
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlassFab(
                        icon = if (isMarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (isMarked) "取消收藏" else "收藏当前位置",
                        tint = if (isMarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            val para = currentParagraph ?: return@GlassFab
                            scope.launch {
                                bookmarkStore.toggle(
                                    para.startLine,
                                    chapterKey,
                                    para.lines.firstOrNull()?.take(40) ?: ""
                                )
                            }
                        }
                    )
                    GlassFab(
                        icon = Icons.Filled.AutoStories,
                        contentDescription = if (novelMode) "切换到普通模式" else "切换到小说模式",
                        tint = if (novelMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { novelMode = !novelMode }
                    )
                    GlassFab(
                        icon = Icons.Filled.Settings,
                        contentDescription = "设置",
                        onClick = onSettings
                    )
                }
            }
        }
    }
}
