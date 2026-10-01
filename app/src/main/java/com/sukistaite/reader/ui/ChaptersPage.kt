package com.sukistaite.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukistaite.reader.data.DocRepository
import com.sukistaite.reader.data.Heading
import com.sukistaite.reader.ui.components.AppTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersPage(onOpen: (Int) -> Unit, onSettings: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var headings by remember { mutableStateOf<List<Heading>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        headings = DocRepository.loadHeadings(ctx)
        loading = false
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Sukistaite · 章节目录",
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, "设置")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start=16.dp, end=16.dp, top=16.dp, bottom=110.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(headings, key = { it.line }) { h ->
                    val isChapter = h.level == 1
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(h.line + 1) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChapter)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isChapter) "📖" else "　",
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                h.title,
                                fontSize = if (isChapter) 15.sp else 13.sp,
                                fontWeight = if (isChapter) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
