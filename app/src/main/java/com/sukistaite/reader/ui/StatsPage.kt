package com.sukistaite.reader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukistaite.reader.data.ReadingStats
import com.sukistaite.reader.data.ReadingStatsStore
import com.sukistaite.reader.ui.components.AppTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 阅读统计页（v1.4）：累计/今日时长、章节停留 Top5、最近 50 条阅读历史时间线。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsPage(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ReadingStatsStore(ctx) }
    val stats by store.stats.collectAsState(initial = ReadingStats())

    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val todaySec = stats.daily[today] ?: 0

    fun fmt(sec: Int): String = when {
        sec < 60 -> "${sec}秒"
        sec < 3600 -> "${sec / 60}分${if (sec % 60 > 0) "${sec % 60}秒" else ""}"
        else -> "${sec / 3600}小时${(sec % 3600) / 60}分"
    }

    val topChapters = stats.chapters.entries.sortedByDescending { it.value }.take(5)

    Scaffold(topBar = { AppTopBar(title = "阅读统计", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("累计阅读", fmt(stats.totalSeconds), Modifier.weight(1f))
                StatCard("今日", fmt(todaySec), Modifier.weight(1f))
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("章节停留 · Top5", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    if (topChapters.isEmpty()) {
                        Text("暂无数据", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        topChapters.forEach { (name, sec) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    name,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(fmt(sec), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("阅读历史 · 最近 ${stats.history.size} 条", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    if (stats.history.isEmpty()) {
                        Text("暂无记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        stats.history.take(50).forEach { h ->
                            Column(Modifier.padding(vertical = 5.dp)) {
                                Text(
                                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(h.time)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(h.chapter, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
