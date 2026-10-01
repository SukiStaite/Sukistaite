package com.sukistaite.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukistaite.reader.data.Bookmark
import com.sukistaite.reader.data.BookmarkStore
import com.sukistaite.reader.ui.components.AppTopBar
import kotlinx.coroutines.launch

/**
 * 书签页 v1.4：多选批量删除 / 重命名 / 排序（行号·时间）/ 导出 JSON。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkPage(onOpen: (Int) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { BookmarkStore(ctx) }
    val scope = rememberCoroutineScope()
    val bookmarks by store.bookmarks.collectAsState(initial = emptyList())

    var selectMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Int>() }
    var sortMode by remember { mutableStateOf("line") }   // line | time
    var renameTarget by remember { mutableStateOf<Bookmark?>(null) }

    val sorted = remember(bookmarks, sortMode) {
        if (sortMode == "time") bookmarks.sortedByDescending { it.createdAt } else bookmarks.sortedBy { it.line }
    }

    fun exportJson() {
        val json = kotlinx.serialization.json.Json { prettyPrint = true }
            .encodeToString(kotlinx.serialization.builtins.ListSerializer(com.sukistaite.reader.data.Bookmark.serializer()), sorted)
        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_TEXT, json)
            putExtra(android.content.Intent.EXTRA_TITLE, "SukiReader 书签导出")
        }
        ctx.startActivity(android.content.Intent.createChooser(share, "导出书签"))
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (selectMode) "已选 ${selected.size}" else "我的书签",
                actions = {
                    if (!selectMode) {
                        IconButton(onClick = { selectMode = true }) {
                            Icon(Icons.Default.DoneAll, "批量管理")
                        }
                        IconButton(onClick = {
                            sortMode = if (sortMode == "line") "time" else "line"
                        }) {
                            Icon(Icons.Default.Sort, if (sortMode == "line") "按行号·切换时间" else "按时间·切换行号")
                        }
                        if (bookmarks.isNotEmpty()) {
                            IconButton(onClick = { exportJson() }) {
                                Icon(Icons.Default.IosShare, "导出JSON")
                            }
                        }
                    } else {
                        TextButton(onClick = {
                            scope.launch {
                                selected.forEach { store.remove(it) }
                                selected.clear()
                                selectMode = false
                            }
                        }) { Text("删除(${selected.size})") }
                        TextButton(onClick = { selected.clear(); selectMode = false }) { Text("取消") }
                    }
                }
            )
        }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有书签。阅读时长按段落选「收藏」。")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start=16.dp, end=16.dp, top=16.dp, bottom=110.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sorted, key = { it.line }) { bm ->
                    val isSel = bm.line in selected
                    BookmarkRow(
                        bm,
                        selectMode = selectMode,
                        isSelected = isSel,
                        onClick = {
                            if (selectMode) {
                                if (isSel) selected.remove(bm.line) else selected.add(bm.line)
                            } else onOpen(bm.line + 1)
                        },
                        onRename = { renameTarget = bm },
                        onDelete = if (selectMode) ({ /* 批量模式单独走顶部按钮 */ }) else ({ scope.launch { store.remove(bm.line) } })
                    )
                }
            }
        }
    }

    // 重命名对话框
    renameTarget?.let { target ->
        var text by remember(target) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名书签") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { store.rename(target.line, text.ifBlank { target.title }) }
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bm: Bookmark,
    selectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            }
            Column(Modifier.weight(1f)) {
                Text(bm.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            if (!selectMode) {
                IconButton(onClick = onRename, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Edit, "重命名", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, "删除书签", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
