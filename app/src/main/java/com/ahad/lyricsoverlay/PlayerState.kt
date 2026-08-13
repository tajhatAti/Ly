package com.ahad.lyricsoverlay

import java.util.concurrent.CopyOnWriteArrayList

object PlayerState {
    @Volatile var queue: List<Song> = emptyList()
    @Volatile var index: Int = -1
    @Volatile var positionMs: Long = 0
    @Volatile var durationMs: Long = 0
    @Volatile var playing: Boolean = false

    val song: Song? get() = queue.getOrNull(index)

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    fun notifyChanged() {
        listeners.forEach { it.invoke() }
    }
}
