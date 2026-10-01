package com.sukistaite.reader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukistaite.reader.data.AppMeta
import com.sukistaite.reader.data.DocRepository
import com.sukistaite.reader.data.SettingsStore
import com.sukistaite.reader.data.UpdateChecker
import com.sukistaite.reader.data.UpdateInfo
import com.sukistaite.reader.ui.components.AppTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SettingsStore(ctx) }
    val s by store.settings.collectAsState(initial = com.sukistaite.reader.data.AppSettings())

    var checking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var resultMsg by remember { mutableStateOf("") }
    var lastCheck by remember { mutableStateOf("从未") }

    fun check() {
        checking = true
        resultMsg = ""
        scope.launch {
            info = UpdateChecker.check(s.contentVersion)
            checking = false
            lastCheck = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            val i = info
            resultMsg = when {
                i == null -> "检查失败，网络不可用或接口无响应"
                i.appUpdate && i.contentUpdate -> "↑ 软件有新版本 ${i.latestApp}，内容有新版本 ${i.latestContent}"
                i.appUpdate -> "↑ 软件有新版本 ${i.latestApp}"
                i.contentUpdate -> "↑ 发现新内容 ${i.latestContent}"
                else -> "✓ 软件与内容均为最新"
            }
        }
    }

    Scaffold(topBar = { AppTopBar(title = "关于", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Text("SukiReader", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("v${AppMeta.VERSION}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("当前版本", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("v${AppMeta.VERSION}", fontSize = 14.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("内容版本", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.contentVersion, fontSize = 14.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("仓库", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            AppMeta.REPO,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("构建时间", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(AppMeta.BUILD_TIME, fontSize = 13.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("设备", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("系统", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Android ${android.os.Build.VERSION.RELEASE}", fontSize = 13.sp)
                    }
                }
            }

            // ── 版本检查 ──
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "版本检查",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    if (resultMsg.isNotEmpty()) {
                        Text(resultMsg, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    info?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Text(
                            notes,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        "上次检查 · $lastCheck",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { check() }, enabled = !checking) {
                            if (checking) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (checking) "检查中…" else "立即检查")
                        }
                        // 内容更新下载按钮
                        if (info?.contentUpdate == true && info?.contentAssetUrl != null) {
                            Button(onClick = {
                                scope.launch {
                                    resultMsg = "下载中…"
                                    try {
                                        val f = UpdateChecker.downloadContent(
                                            info!!.contentAssetUrl!!,
                                            DocRepository.contentFile(ctx)
                                        )
                                        DocRepository.invalidateCache()
                                        // 从文件头解析内容版本号
                                        val head = f.readLines().take(5).joinToString(" ")
                                        val ver = Regex("V([0-9]+\\.[0-9]+)").find(head)?.groupValues?.get(0)
                                            ?: info!!.latestContent ?: "V?"
                                        store.setContentVersion(ver)
                                        resultMsg = "✓ 内容已更新到 $ver，重新打开阅读器生效"
                                    } catch (e: Exception) {
                                        resultMsg = "✗ 下载失败：${e.message}"
                                    }
                                }
                            }) {
                                Text("更新内容到 ${info!!.latestContent}")
                            }
                        }
                    }
                }
            }

            Text(
                "内容著作权归原作者 糖宝 / sukistaite 所有\n仅供私下查阅使用 · 18+",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}
