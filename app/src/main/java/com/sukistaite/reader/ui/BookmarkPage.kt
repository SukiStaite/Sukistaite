package com.sukistaite.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukistaite.reader.data.Bookmark
import com.sukistaite.reader.data.BookmarkStore
import com.sukistaite.reader.ui.components.AppTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkPage(onOpen: (Int) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { BookmarkStore(ctx) }
    val scope = rememberCoroutineScope()
    val bookmarks by store.bookmarks.collectAsState(initial = emptyList())

    Scaffold(
        topBar = { AppTopBar(title = "我的书签") }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有书签。阅读时点右上角的书签图标即可收藏。")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bookmarks, key = { it.line }) { bm ->
                    BookmarkRow(
                        bm,
                        onOpen = { onOpen(bm.line + 1) },
                        onDelete = { scope.launch { store.remove(bm.line) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(bm: Bookmark, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(bm.title, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    bm.snippet,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("第 ${bm.line + 1} 行", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除书签", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
