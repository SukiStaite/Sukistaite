package com.sukistaite.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
fun ChaptersPage(onOpen: (Int) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var headings by remember { mutableStateOf<List<Heading>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        headings = DocRepository.loadHeadings(ctx)
        loading = false
    }

    Scaffold(
        topBar = { AppTopBar(title = "Sukistaite · 章节目录") }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(headings, key = { it.line }) { h ->
                    val isChapter = h.level == 1
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(h.line) },
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
                            if (isChapter) {
                                Icon(
                                    Icons.AutoMirrored.Filled.MenuBook, null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.width(10.dp))
                            } else {
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                text = h.title,
                                fontSize = if (isChapter) 16.sp else 14.sp,
                                fontWeight = if (isChapter) FontWeight.Bold else FontWeight.Normal,
                                color = if (isChapter)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
