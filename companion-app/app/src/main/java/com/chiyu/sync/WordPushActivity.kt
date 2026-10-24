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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min

/**
 * 词表推送页：读取池鱼Study 导出的 JSON 词表 → 按 word_push 协议分块推送到手环
 * 协议见 docs/同步协议.md
 */
class WordPushActivity : ComponentActivity() {
    private lateinit var conn: InterHandshake
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class AckPayload(
        val tag: String = "",
        val count: Int = 0,
        val total: Int = 0
    )

    /** 解析词表文件：兼容三种形态 + 两种字段命名
     *  形态：JSONL（每行一词）、JSON 数组、{words:[...]} 对象
     *  字段：手环协议(w/m) 或 池鱼Study 原格式(word/meaning)
     */
    private fun parseWordList(text: String): List<HttpBridge.WordItem> {
        val trimmed = text.trim()
        val objects: List<JsonObject> = when {
            trimmed.startsWith("[") ->
                json.parseToJsonElement(trimmed).jsonArray.map { it.jsonObject }
            trimmed.startsWith("{") && trimmed.contains("\n") ->
                text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    .map { json.parseToJsonElement(it).jsonObject }
            else -> {
                val payload = json.parseToJsonElement(trimmed).jsonObject
                payload["words"]?.jsonArray?.map { it.jsonObject } ?: listOf(payload)
            }
        }
        return objects.map { obj ->
            if (obj.containsKey("w")) {
                json.decodeFromJsonElement<HttpBridge.WordItem>(obj)
            } else {
                HttpBridge.WordItem(
                    w = obj["word"]?.jsonPrimitive?.contentOrNull ?: "",
                    m = obj["meaning"]?.jsonPrimitive?.contentOrNull ?: ""
                )
            }
        }.filter { it.w.isNotBlank() }
    }

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

            val words: List<HttpBridge.WordItem>
            if (httpMode) {
                // 一键模式：先确认池鱼Study 本地服务在线，再拉词表
                val scope = intent.getStringExtra("scope") ?: "due"
                val count = intent.getIntExtra("count", 50)
                filename = "池鱼Study · 一键推送"
                statusText = "正在检查池鱼Study..."
                val serverUp = withContext(Dispatchers.IO) { HttpBridge.isServerUp() }
                if (!serverUp) {
                    statusText = "⚠ 无法连接池鱼Study：请先打开手机上的池鱼Study，点「🔗 启动本地同步」后再推送"
                    imgSrc = R.drawable.fail
                    btnText = "返回"
                    return@LaunchedEffect
                }
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
                        parseWordList(text)
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

            // 2. 先检查手环端状态，再推送（每一步都有明确反馈）
            try {
                statusText = "正在检查手环连接..."
                conn.connect().await()
                statusText = "已连接 ${conn.currentNode?.name ?: "手环"}，正在验证权限..."
                conn.auth().await()
                conn.registerListener().await()   // 注册消息监听：手环的握手/确认才能收到
                try {
                    statusText = "正在打开手表端「池鱼单词」..."
                    conn.openApp().await()
                } catch (e: Exception) {
                    statusText = "⚠ 无法自动打开手表端应用：请确认手表上已安装「池鱼单词」并手动打开它，再点一次推送"
                    imgSrc = R.drawable.conn_question
                    btnText = "返回"
                    return@LaunchedEffect
                }
                statusText = "连接成功，开始推送..."
                // 动态字节分块：小米 interconnect 单条消息有大小限制（约 1-2KB），
                // 实测小消息(start/hs/确认)全通、20词大块被静默丢弃——
                // 按 UTF-8 字节 ≤1536 分块，每块约 8-10 词
                val MAX_CHUNK_BYTES = 1536
                // JSON 字符串值完整转义：仅转义反斜杠/引号会漏掉换行符 \n 等，
                // 导致整块 JSON 非法被手环端静默丢弃（实测 147 词的释义含换行）
                fun jsonEsc(s: String): String = buildString {
                    for (ch in s) {
                        when {
                            ch == '\\' -> append("\\\\")
                            ch == '"' -> append("\\\"")
                            ch == '\n' -> append("\\n")
                            ch == '\r' -> append("\\r")
                            ch == '\t' -> append("\\t")
                            ch == '\b' -> append("\\b")
                            ch == '\u000C' -> append("\\f")
                            ch.code < 0x20 -> append("\\u%04x".format(ch.code))
                            else -> append(ch)
                        }
                    }
                }
                fun wordJson(w: HttpBridge.WordItem): String = buildString {
                    append("{\"w\":\"").append(jsonEsc(w.w))
                    append("\",\"m\":\"").append(jsonEsc(w.m))
                    append("\",\"e\":").append(if (w.e.isFinite()) w.e.toString() else "2.5")
                    append(",\"i\":").append(w.i)
                    append(",\"lvl\":").append(w.lvl)
                    append(",\"c\":").append(w.c)
                    append(",\"wc\":").append(w.wc)
                    append(",\"next\":").append(w.next)
                    append("}")
                }
                val chunks = mutableListOf<List<HttpBridge.WordItem>>()
                var curBatch = mutableListOf<HttpBridge.WordItem>()
                var curSize = 0
                for (w in words) {
                    val wb = wordJson(w).toByteArray().size
                    if (curBatch.isNotEmpty() && curSize + wb > MAX_CHUNK_BYTES) {
                        chunks.add(curBatch)
                        curBatch = mutableListOf()
                        curSize = 0
                    }
                    curBatch.add(w)
                    curSize += wb
                }
                if (curBatch.isNotEmpty()) chunks.add(curBatch)
                val total = chunks.size
                var acked = false
                var ackedCount = 0

                // 可靠协议：逐块确认（与手环端 app.ux 的 push_next/push_done 一一对应）
                // 发一块 → 等手环端确认 → 才发下一块；手环端缺块会回当前期望序号，手机端据此重发
                var gate: CompletableDeferred<Int>? = null
                conn.addListener("push_next") { payload ->
                    try {
                        val count = json.decodeFromString<AckPayload>(payload).count
                        gate?.complete(count)
                    } catch (e: Exception) { }
                }
                conn.addListener("push_done") { payload ->
                    try {
                        val count = json.decodeFromString<AckPayload>(payload).count
                        ackedCount = count
                        gate?.complete(-1)
                    } catch (e: Exception) { }
                }
                var readyGate: CompletableDeferred<Unit>? = null
                conn.addListener("push_ready") { _ ->
                    readyGate?.complete(Unit)
                }

                // 1. 通知手环端开始接收（重置接收状态），等手环端就绪确认；
                //    超时自动重新拉起手环应用（屏幕亮起）再重试，最多 3 次
                var readyOk = false
                for (attempt in 0 until 3) {
                    val rg = CompletableDeferred<Unit>()
                    readyGate = rg
                    conn.sendMessage("""{"tag":"word_push_start","total":$total}""").await()
                    val got = withTimeoutOrNull(6000) { rg.await() }
                    readyGate = null
                    if (got != null) { readyOk = true; break }
                    statusText = "手环端未就绪，第 ${attempt + 1} 次尝试拉起应用..."
                    try { conn.openApp().await() } catch (e: Exception) { }
                    delay(1200)
                }
                if (!readyOk) {
                    throw Exception("手环端未响应：请确认手环上「池鱼单词」显示 v0.8 且保持亮屏/在前台")
                }

                // 2. 逐块确认发送：cur = 下一个要发的块序号
                var cur = 0
                var retries = 0
                while (cur < total && !acked) {
                    val sub = chunks[cur]
                    val payload = buildString {
                        append("{\"tag\":\"word_push\",\"batch\":").append(cur)
                        append(",\"words\":[")
                        sub.forEachIndexed { i, w ->
                            if (i > 0) append(",")
                            append(wordJson(w))
                        }
                        append("]}")
                    }
                    statusText = "正在推送 ${cur + 1}/$total 块 [${sub.size}词 ${payload.toByteArray().size}B]，等待手环确认..."
                    conn.sendMessage(payload).await()
                    val g = CompletableDeferred<Int>()
                    gate = g
                    val next = withTimeoutOrNull(10000) { g.await() }
                    gate = null
                    if (next == null) {
                        retries++
                        if (retries >= 3) {
                            throw Exception("手环端确认超时（块 ${cur + 1}/$total 无回应）：请确认手环上「池鱼单词」显示 v0.8 且保持亮屏，然后重试")
                        }
                        statusText = "块 ${cur + 1}/$total 无确认，正在重发..."
                        continue
                    }
                    retries = 0
                    if (next == -1) {
                        acked = true
                        break
                    }
                    cur = next
                    progress = cur.toDouble() / total
                    statusText = "正在推送 ${min(cur, total)}/$total 块"
                }
                done = true
                // 一键模式（从池鱼Study拉词）推送完毕 → 回执标记"已同步"，
                // 池鱼Study 端"⚠ 未同步"提醒随之消失
                if (httpMode) {
                    val pushedWords = words.map { it.w }
                    withContext(Dispatchers.IO) {
                        HttpBridge.postSyncAck(pushedWords)
                    }
                }
                if (acked) {
                    statusText = "✅ 推送完成，手环已接收 $wordCount 词"
                    imgSrc = R.drawable.success
                } else {
                    statusText = "已发送完毕（未收到手环确认，可打开手环应用查看）"
                    imgSrc = R.drawable.success
                }
                btnText = "完成"
            } catch (e: Exception) {
                statusText = if ((e.message ?: "").contains("握手")) {
                    "手环端未就绪：请先在手表上打开「池鱼单词」再重试"
                } else if ((e.message ?: "").contains("未找到设备")) {
                    "未连接手环：请打开小米运动健康连接你的手环（登录账号需与手环配对）后再重试"
                } else {
                    "推送失败：${e.message ?: "未知错误"}"
                }
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
