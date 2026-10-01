package com.sukistaite.reader.data

/**
 * 音量键翻页事件总线。
 * MainActivity.onKeyDown 捕获音量键后推入事件，阅读器收集执行滚动。
 * 1 = 音量上（向上滚），2 = 音量下（向下滚），0 = 空闲。
 */
object VolumeKeyBus {
    @Volatile var event: Int = 0
        private set
    @Volatile var enabled: Boolean = false

    fun push(e: Int) { event = e }
    fun consume() { event = 0 }
}
