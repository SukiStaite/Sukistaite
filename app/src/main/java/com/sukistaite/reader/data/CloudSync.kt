package com.sukistaite.reader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL

private val Context.cloudStore: DataStore<Preferences> by preferencesDataStore(name = "suki_cloud")

/**
 * 收藏 + 阅读进度的云同步（GitHub Gist 作为后端）。
 * 单个 secret gist，一个文件 suki_sync.json。
 */
@Serializable
data class SyncPayload(
    val bookmarks: List<Bookmark> = emptyList(),
    val progress: Map<String, Int> = emptyMap(),
    val contentVersion: String = "",
    val syncedAt: Long = 0
)

class CloudSync(private val context: Context) {

    companion object {
        private val KEY_GIST = stringPreferencesKey("gist_id")
        private const val API = "https://api.github.com"
        private val json = Json { ignoreUnknownKeys = true }
    }

    var token: String = ""

    private suspend fun gistId(): String = context.cloudStore.data.first()[KEY_GIST] ?: ""

    private suspend fun setGistId(id: String) {
        context.cloudStore.edit { it[KEY_GIST] = id }
    }

    private fun http(method: String, url: String, body: String?): Pair<Int, String> {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SukiReader")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        return code to text
    }

    /** 从 gist 响应中提取 suki_sync.json 的 content 字段 */
    private fun extractContent(respBody: String): String? = try {
        val files = json.parseToJsonElement(respBody).jsonObject["files"]?.jsonObject ?: return null
        val f = files["suki_sync.json"]?.jsonObject ?: return null
        (f["content"] as? JsonPrimitive)?.content
    } catch (e: Exception) {
        null
    }

    /** 推送本地数据到 gist（无 gist 时自动创建） */
    suspend fun push(local: SyncPayload): Result<Unit> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result.failure(Exception("未配置 GitHub Token"))
        try {
            // 双重编码：content 字段必须是一个 JSON 字符串
            val contentStr = json.encodeToString(json.encodeToString(local))
            val id = gistId()
            if (id.isBlank()) {
                val createBody = """{"description":"SukiReader sync","public":false,"files":{"suki_sync.json":{"content":$contentStr}}}"""
                val (code, resp) = http("POST", "$API/gists", createBody)
                if (code !in 200..201) return@withContext Result.failure(Exception("创建 gist 失败: $code"))
                val newId = json.parseToJsonElement(resp).jsonObject["id"]?.let { (it as JsonPrimitive).content }
                    ?: return@withContext Result.failure(Exception("解析 gist id 失败"))
                setGistId(newId)
            } else {
                val patchBody = """{"files":{"suki_sync.json":{"content":$contentStr}}}"""
                val (code, _) = http("PATCH", "$API/gists/$id", patchBody)
                if (code != 200) return@withContext Result.failure(Exception("更新 gist 失败: $code"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 拉取云端数据。云端无数据时返回 null（不覆盖本地）。 */
    suspend fun pull(): Result<SyncPayload?> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result.failure(Exception("未配置 GitHub Token"))
        try {
            val id = gistId()
            if (id.isBlank()) return@withContext Result.success(null)
            val (code, resp) = http("GET", "$API/gists/$id", null)
            if (code == 404) return@withContext Result.success(null)
            if (code !in 200..299) return@withContext Result.failure(Exception("读取 gist 失败: $code"))
            val raw = extractContent(resp) ?: return@withContext Result.success(null)
            val payload = try {
                json.decodeFromString<SyncPayload>(raw)
            } catch (e: Exception) {
                null
            } ?: return@withContext Result.success(null)
            Result.success(payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
