package com.sukistaite.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.sukistaite.reader.R
import com.sukistaite.reader.data.AppSettings
import com.sukistaite.reader.data.ThemeMode

// ── 粉色主题（默认，呼应祈宝的樱花粉）────────────────────────────
private val PinkLight = lightColorScheme(
    primary = Color(0xFFB8455F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9DE),
    onPrimaryContainer = Color(0xFF3F0019),
    secondary = Color(0xFF74565C),
    secondaryContainer = Color(0xFFFFD9DE),
    tertiary = Color(0xFF7C5636),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF22191A),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF22191A),
    surfaceVariant = Color(0xFFF2DDDF),
    onSurfaceVariant = Color(0xFF514345),
)

private val PinkDark = darkColorScheme(
    primary = Color(0xFFFFB1BF),
    onPrimary = Color(0xFF65002F),
    primaryContainer = Color(0xFF87253F),
    onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = Color(0xFFE3BDC2),
    background = Color(0xFF1A1112),
    onBackground = Color(0xFFF1DEDF),
    surface = Color(0xFF1A1112),
    onSurface = Color(0xFFF1DEDF),
    surfaceVariant = Color(0xFF514345),
    onSurfaceVariant = Color(0xFFD5C2C4),
)

// ── 浅色 / 深色（标准中性 Material 3）────────────────────────────
private val NeutralLight = lightColorScheme()
private val NeutralDark = darkColorScheme()

// ── 内置字体 ────────────────────────────────────────────────────
val WenKai = FontFamily(Font(R.font.lxgw_wenkai, FontWeight.Normal))

object AppFonts {
    val options = listOf("default" to "系统默认", "wenkai" to "霞鹜文楷")

    fun resolve(name: String?): FontFamily? = when (name) {
        "wenkai" -> WenKai
        else -> null
    }
}

/**
 * v1.1 主题入口：
 * - themeMode = PINK / LIGHT / DARK 三色并存（PINK 为默认）
 * - 动态取色仅在 LIGHT/DARK 模式下参与（PINK 保持品牌粉）
 * - fontScale 全局字体缩放；fontFamily 全局字体切换
 */
@Composable
fun SukiReaderTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.PINK -> systemDark
    }

    val colorScheme = when (settings.themeMode) {
        ThemeMode.PINK -> if (darkTheme) PinkDark else PinkLight
        ThemeMode.LIGHT, ThemeMode.DARK -> {
            // Android 12+ 跟随系统动态取色，低版本用标准色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val ctx = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else {
                if (darkTheme) NeutralDark else NeutralLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
