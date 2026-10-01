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

// 粉白主题（默认）：呼应文档里祈宝的樱花粉
private val PinkLight = lightColorScheme(
    primary = Color(0xFFB8455F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9DE),
    onPrimaryContainer = Color(0xFF3F0019),
    secondary = Color(0xFF74565C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9DE),
    onSecondaryContainer = Color(0xFF2B1519),
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

@Composable
fun SukiReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,   // Android 12+ 用系统动态取色（Material You）
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> PinkDark
        else -> PinkLight
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
