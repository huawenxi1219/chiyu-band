package com.chiyu.sync.ui.compoments

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiyu.sync.R.drawable
import com.chiyu.sync.R.color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EbookScaffold(
    title: String,
    onback: () -> Unit = {},
    onmenu: (() -> Unit)? = null,
    children: @Composable (modifier: Modifier) -> Unit
) {
    Scaffold(
        // 禁用默认的内容安全区
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize(),
//        containerColor = colorResource(R.color.card_bg),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorResource(color.card_bg)),
                title = { Text(
                    color = colorResource(color.title),
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    text = title
                ) } ,
                navigationIcon = {
                    IconButton(onClick = onback, modifier = Modifier.padding(10.dp)) {
                        Icon(painter = painterResource(drawable.back_ic), contentDescription = null,
                            modifier = Modifier.size(24.dp))
                    }
                },
                actions = {
                    if (onmenu != null) {
                        IconButton(onClick = onmenu, modifier = Modifier.padding(10.dp)) {
                            Icon(painter = painterResource(drawable.info_ic), contentDescription = null,
                                modifier = Modifier.size(24.dp))
                        }
                    }
                },
                expandedHeight = 60.dp,
//                modifier = Modifier.height(60.dp)
            )
        }
    ) {
        children(Modifier.padding(it))
    }
}
@Preview
@Composable
fun PreviewEbookScaffold() {
    EbookScaffold("PreviewEbookScaffold") {
        Text(text = "PreviewEbookScaffold")
    }
}