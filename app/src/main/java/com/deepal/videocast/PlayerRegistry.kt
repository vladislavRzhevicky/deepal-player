package com.deepal.videocast

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Singleton реестр живых [PlayerActivity] инстансов. Каждый плеер,
 * стартовавший через [MainActivity.playOn], регистрируется по своему
 * `targetDisplayId`. Это позволяет водительскому экрану (на котором
 * остаётся MainActivity) дёргать play/pause/seek/volume у плееров,
 * запущенных на пассажирском или заднем дисплее.
 *
 * Никаких binder'ов / сервисов — всё в одном процессе (см. playOn:
 * запуск идёт `launchDisplayId` из user'a водителя, плеер в том же
 * процессе, что и MainActivity). Это самый дешёвый вариант RPC.
 */
object PlayerRegistry {

    /**
     * Управляющий handle одного PlayerActivity. Все геттеры/сеттеры
     * безопасно вызывать из любого потока — реализация (PlayerActivity)
     * либо thread-safe-делегирует ExoPlayer (он сам обрабатывает
     * вызовы с любого потока), либо обёртывает в main-Handler.
     */
    interface Handle {
        val displayId: Int
        val targetName: String
        val fileName: String
        fun isPlaying(): Boolean
        fun currentMs(): Long
        fun durationMs(): Long
        fun playPause(play: Boolean)
        fun seekTo(ms: Long)
        fun skip(deltaMs: Long)
        fun volume(): Float
        fun setVolume(v: Float)
        fun hasPrev(): Boolean = false
        fun hasNext(): Boolean = false
        /** Переключить на предыдущий файл плейлиста. No-op если hasPrev() = false. */
        fun prev() {}
        /** Переключить на следующий файл плейлиста. No-op если hasNext() = false. */
        fun next() {}
        /** Просит плеер завершиться (finish()). Реестр очистится в onDestroy. */
        fun close()
    }

    private val map = ConcurrentHashMap<Int, Handle>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun register(handle: Handle) {
        map[handle.displayId] = handle
        notifyChange()
    }

    fun unregister(displayId: Int) {
        if (map.remove(displayId) != null) notifyChange()
    }

    fun get(displayId: Int): Handle? = map[displayId]

    fun byDisplays(displayIds: Set<Int>): List<Handle> =
        displayIds.mapNotNull { map[it] }

    fun all(): List<Handle> = map.values.toList()

    /** Подписаться на изменения состава. Подписчик может вызвать [unsubscribe]. */
    fun subscribe(cb: () -> Unit): () -> Unit {
        listeners += cb
        return { listeners -= cb }
    }

    private fun notifyChange() {
        listeners.toList().forEach { runCatching { it() } }
    }
}
