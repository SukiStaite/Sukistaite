package com.sukistaite.reader.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage

/**
 * 自定义背景层（v1.1）：
 * 支持照片（AsyncImage）与视频（MediaPlayer 循环静音播放），
 * 媒体层透明度由 opacity 控制，上面叠一层轻微遮罩保证文字可读。
 * bgUri 为空时不渲染任何东西（透明，露出主题背景色）。
 */
@Composable
fun BackgroundLayer(
    bgUri: String?,
    bgIsVideo: Boolean,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    if (bgUri.isNullOrBlank()) return
    val ctx = LocalContext.current

    Box(modifier) {
        if (bgIsVideo) {
            val player = remember(bgUri) {
                try {
                    MediaPlayer.create(ctx, Uri.parse(bgUri))?.apply {
                        isLooping = true
                        setVolume(0f, 0f)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            DisposableEffect(bgUri) {
                player?.start()
                onDispose {
                    player?.stop()
                    player?.release()
                }
            }
            AndroidView(
                factory = { viewCtx ->
                    SurfaceView(viewCtx)
                },
                update = { sv ->
                    if (sv.holder.surface != null && player != null && !player.isPlaying) {
                        player.setSurface(sv.holder.surface)
                        player.start()
                    }
                },
                modifier = Modifier.fillMaxSize().alpha(opacity)
            )
        } else {
            AsyncImage(
                model = Uri.parse(bgUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(opacity)
            )
        }
        // 轻遮罩：保证文字在任何背景上可读
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.05f))
        )
    }
}
