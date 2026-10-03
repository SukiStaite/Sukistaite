package com.sukistaite.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 应用元信息 */
object AppMeta {
    const val VERSION = "1.6.0"
    const val CONTENT_VERSION = "V3.4"
    const val REPO = "SukiStaite/Sukistaite"
    const val REPO_URL = "https://github.com/SukiStaite/Sukistaite"
    // 构建时间：CI 环境注入 BuildConfig，本地为未知
    val BUILD_TIME: String = try {
        Class.forName("com.sukistaite.reader.BuildConfig")
            .getDeclaredField("BUILD_TIME").get(null) as String
    } catch (e: Exception) { "未知" }
}

data class UpdateInfo(
    val latestApp: String,
    val appUpdate: Boolean,
    val latestContent: String?,
    val contentUpdate: Boolean,
    val notes: String,
    val releaseUrl: String,
    val contentAssetUrl: String?
)

/** 软件版本检查：读取 GitHub Releases（软件版本看 tag，内容版本看 Release 正文「内容版本: Vx.x」） */
object UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.str(k: String): String =
        (this[k] as? JsonPrimitive)?.contentOrNull ?: ""

    private fun v(s: String?): Double {
        val m = Regex("([0-9]+(?:\\.[0-9]+)*)").find(s ?: "") ?: return 0.0
        return m.groupValues[1].split('.').foldIndexed(0.0) { i, acc, part ->
            acc + (part.toIntOrNull() ?: 0) / Math.pow(1000.0, i.toDouble())
        }
    }

    private fun httpGet(url: String): String? = try {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SukiReader")
        }
        if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: Exception) {
        null
    }

    suspend fun check(currentContent: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val body = httpGet("https://api.github.com/repos/${AppMeta.REPO}/releases/latest")
            ?: return@withContext null
        val root = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) {
            return@withContext null
        }
        val tag = root.str("tag_name").removePrefix("v").removePrefix("V")
        val notes = root.str("body").trim()
        val contentVer = Regex("内容版本[:：]\\s*V?([0-9]+(?:\\.[0-9]+)?)").find(notes)?.groupValues?.get(1)
        val assets = (root["assets"] as? JsonArray) ?: JsonArray(emptyList())
        var assetUrl: String? = null
        for (a in assets) {
            val o = (a as? JsonObject) ?: continue
            val name = o.str("name")
            if (name.endsWith(".txt")) {
                assetUrl = o.str("browser_download_url")
                break
            }
        }
        UpdateInfo(
            latestApp = tag,
            appUpdate = v(tag) > v(AppMeta.VERSION),
            latestContent = contentVer?.let { "V$it" },
            contentUpdate = contentVer != null && v(contentVer) > v(currentContent),
            notes = notes.take(1200),
            releaseUrl = root.str("html_url").ifBlank { AppMeta.REPO_URL },
            contentAssetUrl = assetUrl
        )
    }

    /** 下载内容文件到应用私有目录（之后 DocRepository 会优先读取它） */
    suspend fun downloadContent(url: String, target: File): File = withContext(Dispatchers.IO) {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 30000
            setRequestProperty("User-Agent", "SukiReader")
        }
        if (c.responseCode !in 200..299) throw Exception("HTTP ${c.responseCode}")
        c.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target
    }
}
