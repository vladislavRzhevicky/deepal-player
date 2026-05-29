package com.deepal.videocast

import android.os.HandlerThread
import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.fs.UsbFile
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton, хранит открытые UsbFile между Activity. Picker открывает,
 * Player получает ссылку по token и стримит через ProxyFileDescriptor.
 *
 * Pinned теперь содержит **playlist** — массив сиблингов из текущей
 * директории. PlayerActivity может переключаться по индексу (prev/next),
 * не вытаскивая новый token каждый раз. URI ContentProvider'a кодирует
 * индекс, поэтому ExoPlayer корректно перезагружает media item.
 *
 * Refcount: pin() ставит 1 (на сам токен в map). Каждый плеер делает
 * acquire() в onCreate, release() в onDestroy. MainActivity тоже делает
 * release() для baseline ref после раздачи плеерам. Когда refs == 0 —
 * device.close().
 */
object UsbSession {

    class Pinned(
        val token: Long,
        val device: UsbMassStorageDevice,
        /** Список файлов плейлиста; индекс [startIndex] — стартовый. */
        val files: List<UsbFile>,
        startIndex: Int,
        internal val refs: AtomicInteger = AtomicInteger(1),
    ) {
        private val idx = AtomicInteger(startIndex.coerceIn(0, files.lastIndex))

        val currentIndex: Int get() = idx.get()
        val currentFile: UsbFile get() = files[idx.get()]
        /** Имя текущего файла. Используется UsbContentProvider для DISPLAY_NAME. */
        val name: String get() = currentFile.name
        /** Длина текущего файла. */
        val length: Long get() = currentFile.length
        val size: Int get() = files.size
        fun fileAt(index: Int): UsbFile? = files.getOrNull(index)

        fun hasPrev(): Boolean = idx.get() > 0
        fun hasNext(): Boolean = idx.get() + 1 < files.size

        /** Передвинуть указатель. Возвращает новый индекс или null если за границей. */
        fun moveTo(newIndex: Int): Int? {
            if (newIndex !in files.indices) return null
            idx.set(newIndex)
            return newIndex
        }
    }

    private val seq = AtomicLong(0)
    private val map = ConcurrentHashMap<Long, Pinned>()

    /** IO для ProxyFileDescriptorCallback — отдельный looper, не блокирует UI. */
    val ioHandlerThread: HandlerThread by lazy {
        HandlerThread("UsbSessionIO").also { it.start() }
    }

    /**
     * Пинит устройство с плейлистом. Если плейлист пустой/single —
     * деградирует до старого режима (один файл).
     */
    fun pin(device: UsbMassStorageDevice, file: UsbFile): Pinned =
        pinPlaylist(device, listOf(file), 0)

    fun pinPlaylist(
        device: UsbMassStorageDevice,
        files: List<UsbFile>,
        startIndex: Int,
    ): Pinned {
        require(files.isNotEmpty()) { "playlist must be non-empty" }
        val t = seq.incrementAndGet()
        val p = Pinned(t, device, files, startIndex)
        map[t] = p
        return p
    }

    fun get(token: Long): Pinned? = map[token]

    /** Увеличить refcount; вернуть null если токен уже отжил. */
    fun acquire(token: Long): Pinned? {
        val p = map[token] ?: return null
        // Защита от гонки: если кто-то уже спустил до 0, не воскрешаем.
        while (true) {
            val cur = p.refs.get()
            if (cur <= 0) return null
            if (p.refs.compareAndSet(cur, cur + 1)) return p
        }
    }

    /** Спустить refcount; на нуле закрываем device и убираем из map. */
    fun release(token: Long) {
        val p = map[token] ?: return
        val left = p.refs.decrementAndGet()
        if (left <= 0) {
            map.remove(token)
            runCatching { p.device.close() }
        }
    }
}
