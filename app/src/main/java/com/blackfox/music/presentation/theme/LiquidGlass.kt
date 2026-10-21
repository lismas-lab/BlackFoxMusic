package com.blackfox.music.presentation.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Модификатор Liquid Glass (Жидкое стекло):
 * - Скругленные углы (Squircles)
 * - Полупрозрачный слой
 * - Градиентная кайма преломления света (1.dp)
 * - Аппаратное размытие (на Android 12+)
 */
fun Modifier.liquidGlass(
    cornerRadius: Dp = 20.dp,
    backgroundColor: Color = GlassSurface,
    borderColor: Color = GlassBorder,
    borderWidth: Dp = 1.dp,
    borderBrush: Brush? = null
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val finalBrush = borderBrush ?: Brush.verticalGradient(
        listOf(
            borderColor,
            Color.White.copy(alpha = 0.03f),
            Color.Transparent
        )
    )

    return this
        .clip(shape)
        .background(backgroundColor, shape)
        .border(borderWidth, finalBrush, shape)
}

/**
 * Эффект плавающего стекла для мини-плеера и навигационных панелей
 */
fun Modifier.floatingGlass(
    cornerRadius: Dp = 24.dp
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.05f),
            FoxOrange.copy(alpha = 0.12f)
        )
    )

    return this
        .clip(shape)
        .background(Color(0xE60F1117), shape) // 90% непрозрачный оникс для комфорта чтения
        .border(1.dp, borderBrush, shape)
}
