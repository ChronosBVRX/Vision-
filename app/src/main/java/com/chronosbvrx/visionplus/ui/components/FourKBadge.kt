package com.chronosbvrx.visionplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright

/**
 * La marca «4K» de Vision+: un chip pequeño, oscuro y con borde azul fino,
 * en la misma línea visual que los distintivos de HDR/Dolby. No es un botón.
 */
@Composable
fun FourKBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) 4.dp else 5.dp)
    val hPad = if (compact) 5.dp else 6.dp
    val vPad = if (compact) 1.dp else 2.dp
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color(0xCC050B14))
            .border(
                width = 1.dp,
                color = VisionBlue.copy(alpha = 0.65f),
                shape = shape,
            )
            .padding(horizontal = hPad, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
    ) {
        Text(
            text = "4K",
            color = VisionBlueBright,
            fontSize = if (compact) 9.sp else 10.sp,
            lineHeight = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
        Text(
            text = "ULTRA HD",
            color = Color.White.copy(alpha = 0.62f),
            fontSize = if (compact) 6.sp else 7.sp,
            lineHeight = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}
