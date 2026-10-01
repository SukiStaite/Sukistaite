package com.sukistaite.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sukistaite.reader.data.PaperStyle
import com.sukistaite.reader.ui.theme.PaperStyles

/**
 * 阅读页纸张背景层（v1.3）。
 * NONE 透明 / PAPER 仿纸 / PARCHMENT 仿羊皮 / GRADIENT 粉色渐变。
 * 叠加顺序：主题背景 → 纸张色 → 用户自定义照片/视频背景（MainActivity 的 BackgroundLayer 在最底）。
 */
@Composable
fun PaperLayer(
    style: PaperStyle,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.background.luminanceOf() < 0.5f
    val pc = PaperStyles.colors(style, dark)
    Box(
        modifier
            .fillMaxSize()
            .background(pc.bg)
    ) {
        if (pc.overlay != Color.Transparent) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(pc.overlay, Color.Transparent, pc.overlay))
                    )
            )
        }
    }
}

private fun Color.luminanceOf(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue
