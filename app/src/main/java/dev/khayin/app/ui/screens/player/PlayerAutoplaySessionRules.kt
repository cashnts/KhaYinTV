package dev.khayin.app.ui.screens.player

internal fun nextConsecutiveAutoPlayCount(
    currentCount: Int,
    isAutoPlay: Boolean,
): Int = if (isAutoPlay) currentCount + 1 else 0
