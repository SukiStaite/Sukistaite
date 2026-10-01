package com.sukistaite.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.sukistaite.reader.R
import com.sukistaite.reader.data.AppSettings
import com.sukistaite.reader.data.PaperStyle
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

// ── AMOLED 纯黑 ────────────────────────────────────────────────
private val AmoledDark = darkColorScheme(
    primary = Color(0xFFFFB1BF),
    onPrimary = Color(0xFF65002F),
    primaryContainer = Color(0xFF3A0A1C),
    onPrimaryContainer = Color(0xFFFFD9DE),
    background = Color.Black,
    onBackground = Color(0xFFE8E0E1),
    surface = Color.Black,
    onSurface = Color(0xFFE8E0E1),
    surfaceVariant = Color(0xFF1E1A1B),
    onSurfaceVariant = Color(0xFFB8A8AA),
)

// ── 浅色 / 深色（标准中性 Material 3）────────────────────────────
private val NeutralLight = lightColorScheme()
private val NeutralDark = darkColorScheme()

// ── 阅读页纸张风格颜色 ─────────────────────────────────────────
data class PaperColors(
    val bg: Color,
    val overlay: Color = Color.Transparent
)

object PaperStyles {
    fun colors(style: PaperStyle, dark: Boolean): PaperColors = when (style) {
        PaperStyle.PAPER -> if (dark) PaperColors(Color(0xFF2A2620)) else PaperColors(Color(0xFFF7F2E7))
        PaperStyle.PARCHMENT -> if (dark) PaperColors(Color(0xFF33291D)) else PaperColors(Color(0xFFEFE3C8))
        PaperStyle.GRADIENT -> if (dark)
            PaperColors(Color.Transparent, Color(0xFF2A1520).copy(alpha = 0.5f))
        else
            PaperColors(Color.Transparent, Color(0xFFFFE4EC).copy(alpha = 0.5f))
        PaperStyle.NONE -> PaperColors(Color.Transparent)
    }
}

/** 全局毛玻璃强度（0 清透 → 1 磨砂），GlassFab/顶栏/底栏统一读取 */
val LocalGlassStrength = staticCompositionLocalOf { 0.7f }

// ── 内置字体 ────────────────────────────────────────────────────
val WenKai = FontFamily(Font(R.font.lxgw_wenkai, FontWeight.Normal))

object AppFonts {
    val options = listOf("default" to "系统默认", "wenkai" to "霞鹜文楷")

    fun resolve(name: String?): FontFamily? = when (name) {
        "wenkai" -> WenKai
        else -> null
    }
}

/** 把 "#RRGGBB" 应用到 scheme 的 primary 系（onPrimary/containers 同步派生） */
private fun applyCustomPrimary(scheme: ColorScheme, hex: String): ColorScheme {
    val c = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        return scheme
    }
    val dark = scheme.background.luminance() < 0.5f
    return if (dark) scheme.copy(
        primary = c.copy(alpha = 1f).lerp(Color.White, 0.25f),
        onPrimary = Color.Black,
        primaryContainer = c.copy(alpha = 0.35f),
        onPrimaryContainer = c.copy(alpha = 1f).lerp(Color.White, 0.8f)
    ) else scheme.copy(
        primary = c,
        onPrimary = Color.White,
        primaryContainer = c.copy(alpha = 0.18f),
        onPrimaryContainer = c.copy(alpha = 1f).lerp(Color.Black, 0.75f)
    )
}

private fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun Color.lerp(to: Color, f: Float): Color = Color(
    red = red + (to.red - red) * f,
    green = green + (to.green - green) * f,
    blue = blue + (to.blue - blue) * f,
    alpha = alpha
)

/**
 * v1.3 主题入口：
 * - PINK / LIGHT / DARK / AMOLED 四色并存（AMOLED 纯黑省电）
 * - 自定义主色（取色板）覆盖 primary 系
 * - 动态取色仅在 LIGHT/DARK 且未设自定义主色时参与
 * - LocalGlassStrength 提供全局毛玻璃强度
 */
@Composable
fun SukiReaderTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.PINK -> systemDark
    }

    val base = when (settings.themeMode) {
        ThemeMode.PINK -> if (darkTheme) PinkDark else PinkLight
        ThemeMode.AMOLED -> AmoledDark
        ThemeMode.LIGHT, ThemeMode.DARK -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings.customPrimary.isBlank()) {
                val ctx = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else {
                if (darkTheme) NeutralDark else NeutralLight
            }
        }
    }

    val colorScheme = if (settings.customPrimary.isNotBlank()) {
        applyCustomPrimary(base, settings.customPrimary)
    } else base

    androidx.compose.runtime.CompositionLocalProvider(
        LocalGlassStrength provides settings.glassStrength
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
