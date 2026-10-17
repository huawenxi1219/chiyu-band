package com.chiyu.sync

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 收回学习记录：发 record_pull → 手环回 record_back → 保存到 Download/Chiyu 并展示统计
 */
class RecordPullActivity : ComponentActivity() {
    private lateinit var conn: InterHandshake
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        conn = (application as App).conn
        enableEdgeToEdge()
        setContent { PullPage() }
    }

    @Composable
    private fun PullPage() {
        EbookTheme {
            EbookScaffold(
                title = "收回记录",
                onback = { finish() }
            ) {
                PullContent(it.fillMaxSize())
            }
        }
    }

    @Composable
    private fun PullContent(modifier: Modifier) {
        var statusText by remember { mutableStateOf("正在向手环请求记录...") }
        var imgSrc by remember { mutableIntStateOf(R.drawable.uploading) }
        var btnText by remember { mutableStateOf("取消") }
        var resultText by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            var received = false
            var recordsJson: String? = null

            // 监听手环回复（payload 是完整 JSON 字符串）
            conn.addListener("record_back") { payload ->
                try {
                    recordsJson = payload
                    received = true
                } catch (e: Exception) {
                    statusText = "记录解析失败：${e.message}"
                    imgSrc = R.drawable.fail
                    btnText = "返回"
                }
            }

            try {
                conn.init()
                statusText = "连接成功，请求记录..."
                conn.sendMessage("{\"tag\":\"record_pull\"}").await()

                var waited = 0
                while (!received && waited < 50) {  // 最多等 5 秒
                    delay(100)
                    waited++
                }

                if (recordsJson == null) {
                    statusText = "手环没有返回记录（可能暂无学习记录）"
                    imgSrc = R.drawable.fail
                    btnText = "返回"
                    return@LaunchedEffect
                }

                // 解析 records 数组
                val obj = json.parseToJsonElement(recordsJson!!).jsonObject
                val recs = obj["records"]?.jsonArray ?: kotlinx.serialization.json.buildJsonArray { }
                val count = recs.size

                if (count == 0) {
                    statusText = "手环暂无学习记录"
                    imgSrc = R.drawable.fail
                    btnText = "返回"
                    return@LaunchedEffect
                }

                // 优先：自动回传池鱼Study（本地直连，无需导文件）
                val postResult = withContext(Dispatchers.IO) {
                    HttpBridge.postRecords(recordsJson!!)
                }
                if (postResult != null) {
                    statusText = "✅ 已收回 $count 条并回传池鱼Study"
                    val bad = postResult.bad.joinToString("、") { it }
                    resultText = buildString {
                        append("合并 ${postResult.merged} 条 · 更新 ${postResult.updated} 词 · 新增待复习 ${postResult.new_due} 词")
                        if (postResult.bad.isNotEmpty()) {
                            append("\n⚠️ 连续记错需再复习：$bad")
                        }
                        append("\n\n回传失败时本App也会保存文件兜底")
                    }
                    imgSrc = R.drawable.success
                } else {
                    // 兜底：保存到 Download/Chiyu/ 供手动导入
                    val savedPath = withContext(Dispatchers.IO) {
                        saveToDownloads(recordsJson!!)
                    }
                    if (savedPath != null) {
                        statusText = "✅ 已收回 $count 条（本地直连不可用，已存文件）"
                        resultText = "已保存到：\n$savedPath\n\n打开「池鱼Study」→ 我的 → 手环背单词 → 导入记录，选择此文件即可合并"
                        imgSrc = R.drawable.success
                    } else {
                        statusText = "记录已收回（$count 条），但保存失败"
                        resultText = "请检查存储权限后重试"
                        imgSrc = R.drawable.fail
                    }
                }
                btnText = "完成"
            } catch (e: Exception) {
                statusText = "收回失败：${e.message ?: "未知错误"}"
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
                text = statusText,
                color = colorResource(R.color.title),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 24.dp),
                textAlign = TextAlign.Center
            )
            if (resultText.isNotEmpty()) {
                Text(
                    text = resultText,
                    color = colorResource(R.color.text_sub),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            NormalButton(
                modifier = Modifier.padding(top = 10.dp),
                text = btnText,
                onClick = { finish() }
            )
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    /** 保存记录 JSON 到公共 Download/Chiyu 目录（Android 10+ MediaStore） */
    private fun saveToDownloads(jsonText: String): String? {
        return try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "池鱼手环记录_$ts.json"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Chiyu")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                contentResolver.openOutputStream(uri)?.use { it.write(jsonText.toByteArray()) }
                    ?: return null
                "Download/Chiyu/$fileName"
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
                val f = java.io.File(dir, fileName)
                f.writeText(jsonText)
                f.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
