package com.chiyu.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 本地同步桥：与「池鱼Study」的手机内 HTTP 服务（127.0.0.1:8765）直连，
 * 无需手动传文件。协议见 docs/同步协议.md 第 6 节。
 */
object HttpBridge {
    private const val BASE = "http://127.0.0.1:8765"
    private const val TIMEOUT = 5000
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class WordItem(
        val w: String,
        val m: String = "",
        val e: Double = 2.5,
        val i: Int = 0,
        val lvl: Int = 0,
        val c: Int = 0,
        val wc: Int = 0,
        val next: Long = 0
    )

    @Serializable
    data class WordListPayload(
        val version: Int = 1,
        val words: List<WordItem> = emptyList()
    )

    @Serializable
    data class MergeResult(
        val merged: Int = 0,
        val updated: Int = 0,
        val new_due: Int = 0,
        val bad: List<String> = emptyList()
    )

    /** 检查池鱼Study 本地服务是否在线 */
    fun isServerUp(): Boolean {
        return try {
            val conn = URL("$BASE/health").openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (e: Exception) {
            false
        }
    }

    /** 从池鱼Study 拉取词表（scope: due/low/new/all） */
    fun fetchWordlist(scope: String, count: Int): List<WordItem>? {
        return try {
            val url = "$BASE/api/wordlist?scope=${URLEncoder.encode(scope, "UTF-8")}&count=$count"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()
            json.decodeFromString<WordListPayload>(text).words
        } catch (e: Exception) {
            null
        }
    }

    /** 把手环记录回传池鱼Study，返回合并结果（失败返回 null） */
    fun postRecords(recordsJson: String): MergeResult? {
        return try {
            val conn = URL("$BASE/api/records").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(recordsJson.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()
            json.decodeFromString<MergeResult>(text)
        } catch (e: Exception) {
            null
        }
    }
}
