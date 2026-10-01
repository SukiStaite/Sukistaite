package com.sukistaite.reader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukistaite.reader.ui.theme.LocalGlassStrength
import kotlin.math.roundToInt

/**
 * 毛玻璃效果 Modifier（v1.3 核心封装）。
 * 全应用唯一的玻璃实现：顶栏、底栏、FAB、抽屉头部都走它，
 * 强度由 LocalGlassStrength 全局提供（设置页滑块 0~1），
 * 改质感只动这一个文件。
 *
 * 实现：底色 = surface 与 surfaceVariant 按强度插值，alpha 随强度降低（越强越「雾」），
 * 附带 blur 需要硬件层支持，为避免 API/性能坑用纯色渐变近似 Liquid Glass。
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(0.dp),
    baseAlpha: Float = 0.9f,
    highlight: Boolean = true
): Modifier = composedGlass(shape, baseAlpha, highlight)

private fun Modifier.composedGlass(
    shape: Shape,
    baseAlpha: Float,
    highlight: Boolean
): Modifier = this.then(
    androidx.compose.ui.composed {
        val strength = LocalGlassStrength.current
        val surface = MaterialTheme.colorScheme.surface
        val variant = MaterialTheme.colorScheme.surfaceVariant
        val alpha = (baseAlpha - strength * 0.45f).coerceIn(0.35f, 1f)
        val top = surface.copy(alpha = alpha).lerpTo(variant, strength * 0.4f)
        val bottom = variant.copy(alpha = alpha)

        val borderBrush = if (highlight)
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.30f + strength * 0.2f), Color.White.copy(alpha = 0.05f))
            )
        else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))

        this
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)), shape)
            .border(0.8.dp, borderBrush, shape)
    }
)

private fun Color.lerpTo(to: Color, f: Float): Color = Color(
    red = red + (to.red - red) * f,
    green = green + (to.green - green) * f,
    blue = blue + (to.blue - blue) * f,
    alpha = alpha
)

/**
 * 悬浮毛玻璃圆形按钮（v1.1 封装，v1.3 接入全局玻璃强度）。
 */
@Composable
fun GlassFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = tween(120),
        label = "fabScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .glass(shape = CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) {
                pressed = true
                onClick()
                pressed = false
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}
