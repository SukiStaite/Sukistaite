package com.sukistaite.reader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukistaite.reader.data.AppSettings
import com.sukistaite.reader.data.BookmarkStore
import com.sukistaite.reader.data.DocRepository
import com.sukistaite.reader.data.Heading
import com.sukistaite.reader.data.SettingsStore
import com.sukistaite.reader.data.VolumeKeyBus
import com.sukistaite.reader.ui.components.AppTopBar
import com.sukistaite.reader.ui.components.ParaActionSheet
import com.sukistaite.reader.ui.components.ReaderTocDrawer
import com.sukistaite.reader.ui.components.GlassFab
import com.sukistaite.reader.ui.theme.AppFonts
import kotlinx.coroutines.launch

/**
 * 阅读器 v1.2：
 * - 双模式：普通/小说，随时切换
 * - 章节内容隔离 + 阅读记忆
 * - 进度百分比 + 可拖动跳转进度条（顶部）
 * - 双击屏幕显示/隐藏工具栏（沉浸模式）
 * - 音量键翻页（上/下滚动一屏）
 * - 长按段落：复制/收藏/高亮菜单
 * - 目录侧滑抽屉（章节列表 + 目录内搜索 + 当前位置高亮）
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderPage(
    chapterId: String,
    settings: AppSettings,
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onJumpToLine: (Int) -> Unit = {},
    flashLine: Int? = null
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val bookmarkStore = remember { BookmarkStore(ctx) }
    val bookmarks by bookmarkStore.bookmarks.collectAsState(initial = emptyList())
    val highlights by settingsStore.highlights.collectAsState(initial = emptySet())

    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var headings by remember { mutableStateOf<List<Heading>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var novelMode by remember { mutableStateOf(settings.novelMode) }
    var chromeVisible by remember { mutableStateOf(true) }   // 沉浸模式：双击切换
    var drawerOpen by remember { mutableStateOf(false) }
    var menuParaLine by remember { mutableStateOf<Int?>(null) } // 长按菜单

    DisposableEffect(Unit) {
        VolumeKeyBus.enabled = true
        onDispose { VolumeKeyBus.enabled = false }
    }

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

    val flashLine2 = if (chapterId.startsWith("line-")) explicitTarget else null
    var flashActive by remember(chapterId) { mutableStateOf(flashLine2 != null) } // 搜索跳转闪烁

    val chapterTitle = remember(headings, explicitTarget) {
        if (headings.isEmpty()) ""
        else {
            val t = (explicitTarget ?: 0).coerceAtLeast(0)
            headings.firstOrNull { it.line == t }?.title
                ?: DocRepository.chapterOf(headings, t)
        }
    }
    val chapterKey = chapterTitle.ifBlank { "文档开头" }

    LaunchedEffect(chapterId) {
        if (flashLine2 != null) {
            kotlinx.coroutines.delay(1800)
            flashActive = false
        }
    }

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

    // 定位
    LaunchedEffect(paragraphs, chapterKey) {
        if (paragraphs.isEmpty()) return@LaunchedEffect
        val target = explicitTarget ?: settings.progress[chapterKey] ?: 0
        if (explicitTarget != null || target > 0) {
            val clamped = target.coerceIn(chapterRange.first, chapterRange.last)
            val idx = paragraphs.indexOfFirst { it.startLine >= clamped }
            if (idx >= 0) listState.scrollToItem(idx.coerceAtMost(paragraphs.size - 1))
        }
    }

    // 阅读进度自动保存
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

    // 音量键翻页
    val volumePage = settings.volumeKeyPaging
    LaunchedEffect(volumePage) {
        if (!volumePage) return@LaunchedEffect
        snapshotFlow { VolumeKeyBus.event }.collect { ev ->
            if (ev == 0) return@collect
            VolumeKeyBus.consume()
            val layout = listState.layoutInfo
            val viewport = layout.viewportEndOffset - layout.viewportStartOffset
            when (ev) {
                1 -> listState.animateScrollBy(-viewport * 0.9f)
                2 -> listState.animateScrollBy(viewport * 0.9f)
            }
        }
    }

    val font = AppFonts.resolve(settings.fontFamily) ?: FontFamily.Default
    val baseSize = (15 * settings.fontScale).sp
    val novelSize = (18 * settings.fontScale).sp

    val totalParas = paragraphs.size
    val visibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val progressPct = if (totalParas <= 1) 0f else visibleIndex.toFloat() / (totalParas - 1).coerceAtLeast(1)
    val currentParagraph = paragraphs.getOrNull(visibleIndex)
    val isMarked = bookmarks.any { it.line == currentParagraph?.startLine }
    val curLine = currentParagraph?.startLine ?: 0

    fun jumpToFraction(f: Float) {
        if (totalParas <= 0) return
        val idx = (f * (totalParas - 1)).toInt().coerceIn(0, totalParas - 1)
        scope.launch { listState.scrollToItem(idx) }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (chromeVisible) AppTopBar(
                    title = chapterTitle.ifBlank { "阅读" },
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { drawerOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, "目录")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Filled.Settings, "设置")
                        }
                    }
                )
            },
            bottomBar = {
                if (chromeVisible && totalParas > 0) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${(progressPct * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${visibleIndex + 1}/$totalParas 段",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = progressPct,
                            onValueChange = { jumpToFraction(it) },
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (!loaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // 双击切换工具栏（检测双击：两次点击间隔 < 300ms）
                    var lastTap by remember { mutableStateOf(0L) }
                    // 纸张层（在列表之下、主题背景之上）
                    com.sukistaite.reader.ui.components.PaperLayer(
                        settings.paperStyle,
                        Modifier.matchParentSize()
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = (800 * settings.contentMaxWidth).dp)
                            .fillMaxHeight()
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) {
                                val now = System.currentTimeMillis()
                                if (now - lastTap < 300) {
                                    chromeVisible = !chromeVisible
                                    lastTap = 0
                                } else lastTap = now
                            },
                        contentPadding = PaddingValues(
                            start = if (novelMode) 22.dp else 16.dp,
                            end = if (novelMode) 22.dp else 16.dp,
                            top = 12.dp,
                            bottom = 130.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(if (novelMode) 14.dp else 6.dp)
                    ) {
                        itemsIndexed(paragraphs, key = { _, p -> p.startLine }) { _, para ->
                            val highlighted = para.startLine in highlights
                            val isMenuTarget = menuParaLine == para.startLine ||
                                (flashActive && flashLine2 == para.startLine)
                            Column {
                                if (para.isHeading) {
                                    Text(
                                        para.lines.first().trim(),
                                        fontSize = if (para.headingLevel == 1) baseSize * 1.15f else baseSize,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = font,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuParaLine = para.startLine },
                                                onClick = {}
                                            )
                                            .padding(vertical = 4.dp)
                                    )
                                } else {
                                    SelectionContainer {
                                        Text(
                                            para.lines.joinToString(if (novelMode) "" else "\n"),
                                            fontSize = if (novelMode) novelSize else baseSize,
                                            lineHeight = (if (novelMode) novelSize.value * 1.75f else baseSize.value * 1.6f).sp,
                                            fontFamily = font,
                                            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (highlighted) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedClickable(
                                                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuParaLine = para.startLine },
                                                    onClick = {}
                                                )
                                                .let { m ->
                                                    if (isMenuTarget) m.background(
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                        RoundedCornerShape(6.dp)
                                                    ) else m
                                                }
                                                .padding(vertical = 2.dp, horizontal = if (isMenuTarget) 6.dp else 0.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 长按段落菜单
                    menuParaLine?.let { pl ->
                        val para = paragraphs.firstOrNull { it.startLine == pl }
                        if (para != null) {
                            ParaActionSheet(
                                paraText = para.lines.joinToString(" ").take(80),
                                marked = bookmarks.any { it.line == pl },
                                highlighted = pl in highlights,
                                onDismiss = { menuParaLine = null },
                                onCopy = {
                                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("suki", para.lines.joinToString("\n")))
                                    android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                                    menuParaLine = null
                                },
                                onBookmark = {
                                    scope.launch {
                                        bookmarkStore.toggle(pl, chapterKey, para.lines.firstOrNull()?.take(40) ?: "")
                                    }
                                    menuParaLine = null
                                },
                                onHighlight = {
                                    scope.launch { settingsStore.toggleHighlight(pl) }
                                    menuParaLine = null
                                }
                            )
                        }
                    }
                }

                // 边缘渐隐遮罩（v1.3，可在设置关闭）
                if (settings.edgeFade) {
                    val fade = MaterialTheme.colorScheme.background
                    Column(Modifier.matchParentSize()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(
                                    Brush.verticalGradient(listOf(fade, Color.Transparent))
                                )
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(
                                    Brush.verticalGradient(listOf(Color.Transparent, fade))
                                )
                        )
                    }
                }

                // 悬浮按钮组（沉浸模式下隐藏）
                if (chromeVisible) {
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
                    }
                }
            }
        }

        // 目录侧滑抽屉
        ReaderTocDrawer(
            visible = drawerOpen,
            headings = headings,
            currentLine = curLine,
            onDismiss = { drawerOpen = false },
            onJump = { line ->
                drawerOpen = false
                onJumpToLine(line)
            }
        )
    }
}
