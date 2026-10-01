package com.sukistaite.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukistaite.reader.data.DocRepository
import com.sukistaite.reader.data.SearchHit
import com.sukistaite.reader.ui.components.AppTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(onJump: (Int) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    fun doSearch(q: String) {
        scope.launch {
            hits = DocRepository.search(ctx, q)
            searched = true
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "全文搜索") }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索关键词，例如：DLC9、/box、糖宝…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        TextButton(onClick = { doSearch(query) }) { Text("搜索") }
                    }
                }
            )
            when {
                !searched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                hits.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到「$query」", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "共 ${hits.size} 条结果",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(hits, key = { it.line }) { hit ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { onJump(hit.line + 1) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    hit.chapterTitle,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    hit.text.ifBlank { "（空行）" },
                                    fontSize = 14.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "第 ${hit.line + 1} 行",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
