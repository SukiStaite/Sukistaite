package com.sukistaite.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukistaite.reader.data.AppSettings
import com.sukistaite.reader.data.DocRepository
import com.sukistaite.reader.data.SearchHit
import com.sukistaite.reader.data.SettingsStore
import com.sukistaite.reader.ui.components.AppTopBar
import kotlinx.coroutines.launch

/**
 * 搜索页 v1.2：
 * - 三种模式：精确包含 / 模糊（全半角宽松）/ 正则
 * - 范围：全文 / 仅标题 / 仅书签
 * - 全词匹配 / 区分大小写 开关
 * - 搜索历史（本地保存，可单条删除）
 * - 跳转阅读器并高亮目标行
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(
    settings: AppSettings,
    settingsStore: SettingsStore,
    onJump: (Int, String?) -> Unit   // (行号+1, 高亮关键词)
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val history = settings.searchHistory

    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("exact") }
    var scopeMode by remember { mutableStateOf("all") }
    var wholeWord by remember { mutableStateOf(false) }
    var caseSensitive by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }

    fun doSearch(q: String) {
        if (q.isBlank()) return
        query = q
        scope.launch {
            searching = true
            hits = DocRepository.search(ctx, q, mode, scopeMode, wholeWord, caseSensitive)
            searching = false
            searched = true
            settingsStore.pushSearchHistory(q)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "全文搜索",
                actions = {
                    TextButton(onClick = { showOptions = !showOptions }) {
                        Text(if (showOptions) "收起选项" else "选项")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(
                    when (mode) {
                        "regex" -> "输入正则表达式…"
                        "fuzzy" -> "模糊搜索（全半角自动宽松）…"
                        else -> "搜索关键词，例如：DLC9、/box、糖宝…"
                    }
                ) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        TextButton(onClick = { doSearch(query) }) { Text("搜索") }
                    }
                }
            )

            // 选项区
            if (showOptions) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("匹配模式", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = mode == "exact", onClick = { mode = "exact" }, label = { Text("精确") })
                            FilterChip(selected = mode == "fuzzy", onClick = { mode = "fuzzy" }, label = { Text("模糊") })
                            FilterChip(selected = mode == "regex", onClick = { mode = "regex" }, label = { Text("正则") })
                        }
                        Text("搜索范围", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = scopeMode == "all", onClick = { scopeMode = "all" }, label = { Text("全文") })
                            FilterChip(selected = scopeMode == "titles", onClick = { scopeMode = "titles" }, label = { Text("仅标题") })
                            FilterChip(selected = scopeMode == "bookmarks", onClick = { scopeMode = "bookmarks" }, label = { Text("仅书签") })
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("全词匹配", fontSize = 14.sp)
                            Switch(checked = wholeWord, onCheckedChange = { wholeWord = it }, enabled = mode != "regex")
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("区分大小写", fontSize = 14.sp)
                            Switch(checked = caseSensitive, onCheckedChange = { caseSensitive = it }, enabled = mode != "fuzzy")
                        }
                    }
                }
            }

            // 搜索历史（未搜索时显示）
            if (!searched && history.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text("搜索历史", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { settingsStore.clearSearchHistory() } }) { Text("清空") }
                        }
                        history.take(10).forEach { h ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    h,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { doSearch(h) }
                                        .padding(vertical = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { scope.launch { settingsStore.removeSearchHistory(h) } }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, "删除", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            when {
                searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                !searched -> if (history.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                hits.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到「$query」", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start=16.dp, end=16.dp, top=16.dp, bottom=110.dp),
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
                        Card(modifier = Modifier.fillMaxWidth().clickable { onJump(hit.line + 1, query) }) {
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
                                HighlightedText(hit.text, query, mode)
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

/** 结果文本中高亮匹配部分 */
@Composable
private fun HighlightedText(text: String, query: String, mode: String) {
    val q = query.trim()
    val idx = when {
        q.isEmpty() -> -1
        mode == "regex" -> try { Regex(q, RegexOption.IGNORE_CASE).find(text)?.range?.first ?: -1 } catch (e: Exception) { -1 }
        else -> text.lowercase().indexOf(q.lowercase()).let { if (it >= 0) it else -1 }
    }
    if (idx < 0) {
        Text(text, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    } else {
        val len = if (mode == "regex") (try { Regex(q, RegexOption.IGNORE_CASE).find(text)?.value?.length ?: q.length } catch (e: Exception) { q.length }) else q.length
        Text(
            buildAnnotatedString {
                append(text.substring(0, idx))
                withStyle(SpanStyle(background = MaterialTheme.colorScheme.primaryContainer, fontWeight = FontWeight.Bold)) {
                    append(text.substring(idx, (idx + len).coerceAtMost(text.length)))
                }
                append(text.substring((idx + len).coerceAtMost(text.length)))
            },
            fontSize = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
