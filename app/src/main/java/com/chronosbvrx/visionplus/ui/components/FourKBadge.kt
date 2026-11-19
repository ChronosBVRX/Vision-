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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright

/**
 * La marca «4K» de Vision+: pequeña, oscura y con el azul oficial. No es un
 * botón; solo anuncia que la ficha tiene una fuente 4K sana en caché.
 */
@Composable
fun FourKBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) 6.dp else 7.dp)
    val hPad = if (compact) 6.dp else 8.dp
    val vPad = if (compact) 2.dp else 3.dp
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF06101E), Color(0xFF0B2038)),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(VisionBlue, VisionBlueBright)),
                shape = shape,
            )
            .padding(horizontal = hPad, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
    ) {
        Text(
            text = "4K",
            color = VisionBlueBright,
            fontSize = if (compact) 10.sp else 11.sp,
            lineHeight = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp,
        )
        Text(
            text = "ULTRA HD",
            color = Color.White.copy(alpha = 0.78f),
            fontSize = if (compact) 7.sp else 8.sp,
            lineHeight = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
        )
    }
}
