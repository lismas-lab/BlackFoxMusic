package com.blackfox.music.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackfox.music.R
import com.blackfox.music.presentation.theme.GlassBorder
import com.blackfox.music.presentation.theme.TextPrimary

/**
 * Светящаяся иконка Черного Лиса в стиле оригинального концепта
 */
@Composable
fun GlowingFoxLogo(
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // Премиальный Liquid Glass бейдж логотипа
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = GlassBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo_liquid),
                contentDescription = "Black Fox Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = "BLACK FOX",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}
