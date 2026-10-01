package com.sukistaite.reader.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 长按段落操作菜单（v1.2）：复制 / 收藏 / 高亮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParaActionSheet(
    paraText: String,
    marked: Boolean,
    highlighted: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onBookmark: () -> Unit,
    onHighlight: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(
                paraText,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionItem(Icons.Filled.ContentCopy, "复制", onCopy)
                ActionItem(
                    if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    if (marked) "取消收藏" else "收藏",
                    onBookmark
                )
                ActionItem(
                    Icons.Filled.FormatColorFill,
                    if (highlighted) "取消高亮" else "高亮",
                    onHighlight
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp)
    }
}
