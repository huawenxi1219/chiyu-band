package com.chiyu.sync

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiyu.sync.logic.InterHandshake
import com.chiyu.sync.ui.compoments.EbookScaffold
import com.chiyu.sync.ui.compoments.NormalButton
import com.chiyu.sync.ui.theme.EbookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlin.math.min

/**
 * 词表推送页：读取池鱼Study 导出的 JSON 词表 → 按 word_push 协议分块推送到手环
 * 协议见 docs/同步协议.md
 */
class WordPushActivity : ComponentActivity() {
    private lateinit var conn: InterHandshake
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
    data class AckPayload(
        val tag: String = "",
        val count: Int = 0,
        val total: Int = 0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        conn = (application as App).conn
        enableEdgeToEdge()
        setContent { WordPushPage() }
    }

    override fun onStop() {
        super.onStop()
    }

    @Composable
    private fun WordPushPage() {
        EbookTheme {
            EbookScaffold(
                title = "推送词表",
                onback = { finish() }
            ) {
                PushContent(it.fillMaxSize())
            }
        }
    }

    @Composable
    private fun PushContent(modifier: Modifier) {
        var filename by remember { mutableStateOf("词表文件") }
        var wordCount by remember { mutableStateOf(0) }
        var progress by remember { mutableDoubleStateOf(0.0) }
        var statusText by remember { mutableStateOf("准备推送...") }
        var imgSrc by remember { mutableIntStateOf(R.drawable.uploading) }
        var btnText by remember { mutableStateOf("取消") }
        var done by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val httpMode = intent.getBooleanExtra("http_mode", false)
            val uri = intent.data
            if (!httpMode && uri == null) {
                statusText = "缺少词表文件"
                imgSrc = R.drawable.fail
                btnText = "返回"
                return@LaunchedEffect
            }

            val words: List<WordItem>
            if (httpMode) {
                // 一键模式：从池鱼Study本地服务直接拉词表，无需选文件
                val scope = intent.getStringExtra("scope") ?: "due"
                val count = intent.getIntExtra("count", 50)
                filename = "池鱼Study · 一键推送"
                statusText = "正在从池鱼Study拉取词表..."
                val fetched = withContext(Dispatchers.IO) {
                    HttpBridge.fetchWordlist(scope, count)
                }
                if (fetched.isNullOrEmpty()) {
                    statusText = "拉取失败：请先打开池鱼Study并「🔗启动本地同步」"
                    imgSrc = R.drawable.fail
                    btnText = "返回"
                    return@LaunchedEffect
                }
                words = fetched
            } else {
                // 文件模式：解析用户选择的词表 JSON
                val name = contentResolver.query(uri!!, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else "词表.json"
                } ?: "词表.json"
                filename = name
                words = withContext(Dispatchers.IO) {
                    try {
                        val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                        when {
                            text.trimStart().startsWith("[") ->
                                json.decodeFromString<List<WordItem>>(text)
                            else -> json.decodeFromString<WordListPayload>(text).words
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                if (words.isEmpty()) {
                    statusText = "词表为空或解析失败"
                    imgSrc = R.drawable.fail
                    btnText = "返回"
                    return@LaunchedEffect
                }
            }
            wordCount = words.size
            statusText = "已解析 ${words.size} 词，正在连接..."

            // 2. 连接 + 推送
            try {
                conn.init()
                statusText = "连接成功，开始推送..."
                val chunkSize = 20
                val total = (words.size + chunkSize - 1) / chunkSize
                var acked = false

                // 注册确认监听
                conn.addListener("push_ack") { _ ->
                    acked = true
                }

                for (batch in 0 until total) {
                    val sub = words.subList(
                        batch * chunkSize,
                        min((batch + 1) * chunkSize, words.size)
                    )
                    val payload = buildString {
                        append("{\"tag\":\"word_push\",\"batch\":").append(batch)
                        append(",\"total\":").append(total)
                        append(",\"words\":[")
                        sub.forEachIndexed { i, w ->
                            if (i > 0) append(",")
                            append("{\"w\":\"")
                            append(w.w.replace("\\", "\\\\").replace("\"", "\\\""))
                            append("\",\"m\":\"")
                            append(w.m.replace("\\", "\\\\").replace("\"", "\\\""))
                            append("\",\"e\":").append(w.e)
                            append(",\"i\":").append(w.i)
                            append(",\"lvl\":").append(w.lvl)
                            append(",\"c\":").append(w.c)
                            append(",\"wc\":").append(w.wc)
                            append(",\"next\":").append(w.next)
                            append("}")
                        }
                        append("]}")
                    }
                    conn.sendMessage(payload).await()
                    progress = (batch + 1).toDouble() / total
                    statusText = "正在推送 ${batch + 1}/$total 块"
                    delay(60) // 让手环处理，避免 BLE 消息堆积
                }
                // 等待确认（最多 3 秒）
                var waited = 0
                while (!acked && waited < 30) {
                    delay(100)
                    waited++
                }
                done = true
                if (acked) {
                    statusText = "✅ 推送完成，手环已接收 $wordCount 词"
                    imgSrc = R.drawable.success
                } else {
                    statusText = "已发送完毕（未收到手环确认，可打开手环应用查看）"
                    imgSrc = R.drawable.success
                }
                btnText = "完成"
            } catch (e: Exception) {
                statusText = "推送失败：${e.message ?: "未知错误"}"
                imgSrc = R.drawable.fail
                btnText = "返回"
            }
        }

        Column(
            modifier
                .fillMaxSize()
                .background(colorResource(R.color.page_bg))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                modifier = Modifier
                    .padding(top = 40.dp)
                    .size(130.dp)
                    .clip(RoundedCornerShape(100))
                    .background(colorResource(R.color.card_bg))
                    .padding(20.dp),
                painter = painterResource(id = imgSrc),
                contentDescription = null
            )
            Text(
                text = filename,
                modifier = Modifier.padding(top = 20.dp),
                color = colorResource(R.color.text_main),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (wordCount > 0) "共 $wordCount 词" else "",
                color = colorResource(R.color.text_sub),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = statusText,
                color = colorResource(R.color.title),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 16.dp),
                textAlign = TextAlign.Center
            )
            if (!done) {
                LinearProgressIndicator(
                    progress = { progress.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                )
            }
            NormalButton(
                modifier = Modifier.padding(top = 10.dp),
                text = btnText,
                onClick = { finish() }
            )
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
