package com.chiyu.sync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.chiyu.sync.logic.InterHandshake
import com.chiyu.sync.ui.compoments.EbookCard
import com.chiyu.sync.ui.compoments.EbookScaffold
import com.chiyu.sync.ui.compoments.NormalButton
import com.chiyu.sync.ui.theme.EbookTheme
import com.chiyu.sync.utils.FileUtils
import com.chiyu.sync.utils.bytesToReadable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private lateinit var conn: InterHandshake
    private lateinit var filePicker: ActivityResultLauncher<Array<String>>
    private lateinit var fileHandler: (Uri?) -> Unit
    private var fileuri: Uri? = null
    private var isFileSelected = false
    private var isConnected = false
    private var updateDevice = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        conn = InterHandshake(this, lifecycleScope)
        (application as App).conn = conn
        filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            fileHandler(it)
        }
        setContent {
            MainActivityPage()
        }
        handleSyncLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSyncLink(intent)
    }

    /** 处理池鱼Study 的一键拉起：chiyu-sync://push?scope=due&count=50 */
    private fun handleSyncLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "chiyu-sync") return
        when (uri.host) {
            "push" -> {
                val scope = uri.getQueryParameter("scope") ?: "due"
                val count = uri.getQueryParameter("count")?.toIntOrNull() ?: 50
                startActivity(
                    Intent(this, WordPushActivity::class.java)
                        .putExtra("http_mode", true)
                        .putExtra("scope", scope)
                        .putExtra("count", count)
                )
            }
        }
    }

    override fun onDestroy() {
        conn.destroy()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updateDevice()
    }

    @Composable
    private fun MainActivityPage() {
        var showButton by remember { mutableStateOf(false) }
        EbookTheme {
            EbookScaffold(
                title = "池鱼手环同步",
                onback = { finish() }
            ) {
                Column(
                    modifier = it
                        .fillMaxSize()
                        .background(colorResource(R.color.page_bg))
                        .padding(10.dp)
                ) {
                    DeviceCard()
                    WordFileCard { show ->
                        showButton = show
                        isFileSelected = show
                    }

                    AnimatedVisibility(
                        visible = showButton,
                        modifier = Modifier.fillMaxWidth().zIndex(0f),
                        enter = slideInVertically(
                            initialOffsetY = { fullHeight -> -fullHeight },
                            animationSpec = tween(400)
                        ) + fadeIn(animationSpec = tween(400)),
                        exit = slideOutVertically(
                            targetOffsetY = { fullHeight -> -fullHeight },
                            animationSpec = tween(400)
                        ) + fadeOut(animationSpec = tween(400))
                    ) {
                        Column {
                            NormalButton(
                                text = "推送到手环",
                                onClick = {
                                    if (!isConnected) {
                                        Toast.makeText(this@MainActivity, "请先连接手环", Toast.LENGTH_SHORT).show()
                                        return@NormalButton
                                    }
                                    if (fileuri == null) {
                                        Toast.makeText(this@MainActivity, "请先选择词表文件", Toast.LENGTH_SHORT).show()
                                        return@NormalButton
                                    }
                                    val intent = Intent(this@MainActivity, WordPushActivity::class.java)
                                        .setData(fileuri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    startActivity(intent)
                                }
                            )
                            NormalButton(
                                text = "重新选择",
                                onClick = { filePicker.launch(arrayOf("*/*")) }
                            )
                        }
                    }

                    RecordCard()

                    Spacer(modifier = Modifier.weight(1f))

                    val context = androidx.compose.ui.platform.LocalContext.current
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    Text(
                        text = "v${packageInfo.versionName}",
                        color = colorResource(R.color.text_sub),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 10.dp)
                    )
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Composable
    private fun DeviceCard() {
        var imgSrc by remember { mutableIntStateOf(R.drawable.conn_false) }
        var connState by remember { mutableStateOf("手环连接中") }
        var connDesc by remember { mutableStateOf("请确保小米运动健康后台运行") }
        val scope = rememberCoroutineScope()

        suspend fun initConn() {
            try {
                conn.destroy().await()
                val deviceName = conn.connect().await().replace(" ", "")
                conn.auth()
                conn.registerListener().await()   // 修复：注册消息监听，握手/记录回传才能收到
                connState = "设备连接成功"
                connDesc = "$deviceName 已连接"
                imgSrc = R.drawable.conn_true
                isConnected = true
                // 自动拉起手环端应用：AstroBox 侧载的快应用无法用 launchWearApp 拉起，
                // 失败只作提示（请手动打开），不再把整个连接误判为失败。
                try {
                    conn.openApp()
                } catch (e: Exception) {
                    connDesc = "$deviceName 已连接（请在手表上手动打开「池鱼单词」）"
                }
            } catch (e: Exception) {
                Log.e("init", "connect fail ${e.message}")
                imgSrc = R.drawable.conn_false
                connState = "手环连接失败"
                connDesc = e.message ?: "未知错误"
                isConnected = false
            }
        }

        EbookCard(
            img = {
                Image(
                    painterResource(imgSrc),
                    modifier = it,
                    contentDescription = null
                )
            },
            title = connState,
            description = connDesc,
            onclick = {
                GlobalScope.launch { initConn() }
            }
        )
        LaunchedEffect(Unit) {
            initConn()
            updateDevice = {
                scope.launch { initConn() }
            }
        }
    }

    @Composable
    private fun WordFileCard(callback: (Boolean) -> Unit) {
        var imgSrc by remember { mutableIntStateOf(R.drawable.addfile) }
        var filename by remember { mutableStateOf("选择词表文件") }
        var fileDesc by remember { mutableStateOf("池鱼Study 导出的 JSON 词表") }
        fileHandler = fileHandler@{ uri ->
            if (uri == null) {
                if (!isFileSelected) return@fileHandler
                filename = "选择词表文件"
                fileDesc = "池鱼Study 导出的 JSON 词表"
                imgSrc = R.drawable.addfile
                callback(false)
                return@fileHandler
            }
            fileuri = uri
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    filename = it.getString(max(it.getColumnIndex(OpenableColumns.DISPLAY_NAME), 0))
                    imgSrc = R.drawable.file
                    val size = it.getLong(max(it.getColumnIndex(OpenableColumns.SIZE), 0))
                    fileDesc = if (size != (-1).toLong()) {
                        bytesToReadable(size)
                    } else {
                        "未知大小"
                    }
                    callback(true)
                }
            }
        }
        EbookCard(
            img = {
                Image(
                    painterResource(imgSrc),
                    modifier = it
                        .clip(RoundedCornerShape(100))
                        .background(Color.White),
                    contentDescription = null
                )
            },
            title = filename,
            description = fileDesc,
            onclick = {
                if (isFileSelected) {
                    Toast.makeText(this, FileUtils.getRealPath(this, fileuri), Toast.LENGTH_SHORT).show()
                } else {
                    filePicker.launch(arrayOf("*/*"))
                }
            }
        )
        LaunchedEffect(Unit) {
            intent.data?.let { fileHandler(it) }
        }
    }

    @Composable
    private fun RecordCard() {
        EbookCard(
            img = {
                Image(
                    painterResource(R.drawable.info_ic),
                    modifier = it,
                    contentDescription = null
                )
            },
            title = "收回学习记录",
            description = "从手环取回背词记录（保存到 Download/Chiyu）",
            onclick = {
                if (!isConnected) {
                    Toast.makeText(this, "请先连接手环", Toast.LENGTH_SHORT).show()
                    return@EbookCard
                }
                startActivity(Intent(this@MainActivity, RecordPullActivity::class.java))
            }
        )
    }
}
