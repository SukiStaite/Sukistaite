package com.sukistaite.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukistaite.reader.data.AppSettings
import com.sukistaite.reader.data.SettingsStore
import com.sukistaite.reader.data.ThemeMode
import com.sukistaite.reader.ui.components.AppTopBar
import com.sukistaite.reader.ui.theme.AppFonts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(onBack: () -> Unit, onAbout: () -> Unit = {}) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()
    val s by store.settings.collectAsState(initial = AppSettings())

    Scaffold(topBar = { AppTopBar(title = "设置", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── 外观 ──
            SectionCard("外观") {
                Text("主题", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                            ThemeMode.PINK -> "粉色"
                        }
                        FilterChip(
                            selected = s.themeMode == mode,
                            onClick = { scope.launch { store.setTheme(mode) } },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("字体大小 · ${(s.fontScale * 100).toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = s.fontScale,
                    onValueChange = { scope.launch { store.setFontScale(it) } },
                    valueRange = 0.8f..1.6f,
                    steps = 15
                )

                Spacer(Modifier.height(8.dp))
                Text("字体", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppFonts.options.forEach { (key, label) ->
                        FilterChip(
                            selected = s.fontFamily == key,
                            onClick = { scope.launch { store.setFontFamily(key) } },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("切换动画", fontSize = 15.sp)
                    Switch(checked = s.animations, onCheckedChange = { scope.launch { store.setAnimations(it) } })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("默认小说模式", fontSize = 15.sp)
                        Text(
                            "新开阅读器时段落重排的沉浸排版",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = s.novelMode, onCheckedChange = { scope.launch { store.setNovelModeDefault(it) } })
                }
            }

            // ── 自定义背景 ──
            SectionCard("自定义背景") {
                val launcher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        // 持久化权限，重启后仍可读
                        ctx.contentResolver.takePersistableUriPermission(
                            it,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        scope.launch { store.setBackground(it.toString(), false) }
                    }
                }
                val videoLauncher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        ctx.contentResolver.takePersistableUriPermission(
                            it,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        scope.launch { store.setBackground(it.toString(), true) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { launcher.launch(arrayOf("image/*")) }) { Text("选照片") }
                    Button(onClick = { videoLauncher.launch(arrayOf("video/*")) }) { Text("选视频") }
                    if (s.bgUri != null) {
                        Button(
                            onClick = { scope.launch { store.clearBackground() } },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) { Text("清除") }
                    }
                }
                if (s.bgUri != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("背景透明度 · ${(s.bgOpacity * 100).toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = s.bgOpacity,
                        onValueChange = { scope.launch { store.setBgOpacity(it) } },
                        valueRange = 0.05f..0.6f
                    )
                }
            }

            // ── 云同步 ──
            SectionCard("云同步（GitHub Gist）") {
                Text(
                    "收藏与阅读进度通过你的私有 Gist 同步，Token 只保存在本机。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                var token by remember(s.ghToken) { mutableStateOf(s.ghToken) }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                Spacer(Modifier.height(8.dp))
                var gistId by remember(s.gistId) { mutableStateOf(s.gistId) }
                OutlinedTextField(
                    value = gistId,
                    onValueChange = { gistId = it },
                    label = { Text("Gist ID（自动生成，一般不用改）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    var msg by remember { mutableStateOf("") }
                    Button(onClick = {
                        scope.launch {
                            store.setToken(token); store.setGistId(gistId)
                            msg = pushSync(ctx)
                        }
                    }) { Text("保存并上传") }
                    Button(onClick = {
                        scope.launch {
                            store.setToken(token); store.setGistId(gistId)
                            msg = pullSync(ctx)
                        }
                    }) { Text("从云端恢复") }
                    if (msg.isNotEmpty()) {
                        Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ── 关于 ──
            AboutEntry(onAbout)
        }
    }
}

private suspend fun pushSync(ctx: android.content.Context): String = try {
    val store = SettingsStore(ctx)
    val snap = store.snapshot()
    val sync = com.sukistaite.reader.data.CloudSync(ctx).apply { token = snap.ghToken }
    val bookmarks = com.sukistaite.reader.data.BookmarkStore(ctx).bookmarksList()
    val payload = com.sukistaite.reader.data.SyncPayload(
        bookmarks = bookmarks,
        progress = snap.progress,
        contentVersion = snap.contentVersion,
        syncedAt = System.currentTimeMillis()
    )
    sync.push(payload).fold(
        onSuccess = { "✓ 已上传 ${bookmarks.size} 条收藏" },
        onFailure = { "✗ ${it.message}" }
    )
} catch (e: Exception) {
    "✗ ${e.message}"
}

private suspend fun pullSync(ctx: android.content.Context): String = try {
    val store = SettingsStore(ctx)
    val snap = store.snapshot()
    val sync = com.sukistaite.reader.data.CloudSync(ctx).apply { token = snap.ghToken }
    when (val r = sync.pull().getOrNull()) {
        null -> "云端没有可恢复的数据"
        else -> {
            com.sukistaite.reader.data.BookmarkStore(ctx).restoreAll(r.bookmarks)
            store.restoreProgress(r.progress)
            "✓ 已恢复 ${r.bookmarks.size} 条收藏和阅读进度"
        }
    }
} catch (e: Exception) {
    "✗ ${e.message}"
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun AboutEntry(onAbout: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAbout() }
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("关于", fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("版本 · 检查更新 ›", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
