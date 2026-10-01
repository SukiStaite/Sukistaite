package com.sukistaite.reader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukistaite.reader.data.Heading

/**
 * 阅读器目录侧滑抽屉（v1.2）：
 * - 全部章节列表（章 level1 加粗、节 level2 缩进）
 * - 目录内搜索框即时过滤
 * - 当前阅读位置高亮 + 「回到我的位置」按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTocDrawer(
    visible: Boolean,
    headings: List<Heading>,
    currentLine: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    if (!visible) return
    var filter by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("目录", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onJump(currentLine) }) { Text("回到我的位置") }
            }
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("在目录中搜索…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            val shown = if (filter.isBlank()) headings
                else headings.filter { filter.trim() in it.title }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(shown, key = { it.line }) { h ->
                    val isCurrent = h.line == currentLine ||
                        (h.level == 1 && h.line <= currentLine && shown.none { it.level == 1 && it.line in (h.line + 1) until currentLine })
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(h.line) }
                            .padding(start = if (h.level == 1) 4.dp else 24.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isCurrent) "▸ " else if (h.level == 1) "📖 " else "　　",
                            fontSize = 13.sp
                        )
                        Text(
                            h.title,
                            fontSize = if (h.level == 1) 14.sp else 12.sp,
                            fontWeight = if (h.level == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
