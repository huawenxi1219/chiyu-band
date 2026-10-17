package com.chiyu.sync.ui.compoments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiyu.sync.R.drawable
import com.chiyu.sync.R.color

@Composable
fun EbookCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    onclick: ()->Unit={},
    img:@Composable (modifier: Modifier)->Unit,

    ){
    Card(
        modifier = modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(color.card_bg),
        ),
        onClick = onclick
    ){
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            img(
                Modifier.size(48.dp)
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    color = colorResource(color.text_main),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = description,
                    color = colorResource(color.text_sub),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            }
        }
    }
}
@Preview
@Composable
fun PreviewEbookCard() {
    EbookCard(title = "PreviewEbookCard", description = "PreviewEbookCard") {
        Icon(painter = painterResource(drawable.conn_false), contentDescription = null,
            modifier = it)
    }
}