package com.chiyu.sync.ui.compoments

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiyu.sync.R

@Composable
fun NormalButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit = {},
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colorResource(R.color.card_bg),
        ),
        modifier = modifier
            .padding(bottom = 10.dp)
            .height(54.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = text,
            color = colorResource(R.color.text_main),
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp
        )
    }
}
