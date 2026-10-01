package com.sukistaite.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukistaite.reader.data.Bookmark
import com.sukistaite.reader.data.BookmarkStore
import com.sukistaite.reader.data.DocRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 阅读器：chapterId 支持两种格式
 *   "123"      → 跳到第 124 行（1-based，章/节标题行）
 *   "line-456" → 跳到第 456 行（1-based，搜索结果定位）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPage(chapterId: String, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { BookmarkStore(ctx) }
    val bookmarks by store.bookmarks.collectAsState(initial = emptyList())

    var allLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var headings by remember { mutableStateOf<List<com.sukistaite.reader.data.Heading>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        allLines = DocRepository.loadLines(ctx)
        headings = DocRepository.loadHeadings(ctx)
        loaded = true
    }

    val startLine = remember(chapterId) {
        (chapterId.removePrefix("line-").toIntOrNull() ?: 1).coerceAtLeast(1) - 1
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startLine.coerceAtMost(3000))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (headings.isEmpty()) "阅读" else DocRepository.chapterOf(headings, startLine),
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        Text("第 ${startLine + 1} 行 · 共 ${allLines.size} 行", fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    val bookmarked = bookmarks.any { it.line == startLine }
                    IconButton(onClick = {
                        scope.launch {
                            val snippet = allLines.getOrNull(startLine)?.trim()?.take(80) ?: ""
                            val title = if (headings.isEmpty()) "" else DocRepository.chapterOf(headings, startLine)
                            store.toggle(startLine, title, snippet)
                        }
                    }) {
                        Icon(
                            if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            "书签"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val from = (startLine - 30).coerceAtLeast(0)
                val to = (startLine + 400).coerceAtMost(allLines.size)
                items(from, to) { i ->
                    val raw = allLines[i]
                    val isHeading = headings.any { it.line == i }
                    Text(
                        text = raw.ifBlank { " " },
                        fontSize = if (isHeading) 15.sp else 14.sp,
                        fontWeight = if (isHeading) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 22.sp,
                        color = if (isHeading) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { /* 行点击：预留 */ }
                    )
                }
            }
        }
    }
}

/** 局部 items(range) 便捷封装 */
private fun androidx.compose.foundation.lazy.LazyListScope.items(from: Int, to: Int, row: @Composable (Int) -> Unit) {
    for (i in from until to) {
        item(key = i) { row(i) }
    }
}
