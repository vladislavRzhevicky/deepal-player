package com.deepal.videocast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import java.io.File

/**
 * Read-only диагностика для проверки гипотезы из gallery-reverse REPORT.md:
 * vold уже монтит USB-флешку каждому user'у в `/storage/<UUID>`, и обычное
 * `StorageManager.getStorageVolumes()` + `File()` должно работать без
 * libusb / libaums и без single-owner проблемы.
 *
 * Запускаем при старте MainActivity и подписываемся на MEDIA_MOUNTED, чтобы
 * увидеть как меняется картина после физического подключения флешки.
 *
 * После того как полевые данные подтвердят (или опровергнут) гипотезу,
 * этот файл превратится в полноценный StorageVolumeBrowser, а пока — просто
 * пишет в logcat. Никакой работающий код не трогаем.
 *
 * Поиск в logcat: `adb logcat -s VideoCast.Probe`.
 */
object StorageVolumeProbe {

    private const val TAG = "VideoCast.Probe"
    @Volatile private var registered = false

    fun start(ctx: Context) {
        if (registered) return
        registered = true
        dump(ctx, "init")
        // Все три события: добавление, проверка, удаление. Подписываемся
        // через ApplicationContext, чтобы не зависеть от lifecycle Activity.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_CHECKING)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        val flags = if (Build.VERSION.SDK_INT >= 34) Context.RECEIVER_EXPORTED else 0
        ctx.applicationContext.registerReceiver(receiver, filter, flags)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            Log.i(TAG, "broadcast ${intent.action} data=${intent.data}")
            dump(c, intent.action ?: "?")
        }
    }

    private fun dump(ctx: Context, reason: String) {
        val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val myUid = android.os.Process.myUid()
        val myUserId = runCatching {
            Class.forName("android.os.UserHandle").getMethod("myUserId").invoke(null) as Int
        }.getOrElse { myUid / 100000 }
        Log.i(TAG, "==== probe trigger=$reason user=$myUserId uid=$myUid ====")

        val volumes: List<StorageVolume> = sm.storageVolumes
        Log.i(TAG, "getStorageVolumes() returned ${volumes.size}")

        for (v in volumes) {
            // Reflection для тех геттеров, которых нет в публичном API
            // (uuid getter появился в 30, isRemovable в 24, directory в 30,
            // mediaStoreVolumeName в 30).
            val state = runCatching { v.state }.getOrNull()
            val isRemovable = runCatching { v.isRemovable }.getOrNull()
            val isPrimary = runCatching { v.isPrimary }.getOrNull()
            val isEmulated = runCatching { v.isEmulated }.getOrNull()
            val uuid = runCatching { v.uuid }.getOrNull()
            val description = runCatching { v.getDescription(ctx) }.getOrNull()
            val dir = if (Build.VERSION.SDK_INT >= 30) runCatching { v.directory }.getOrNull() else null
            val msvn = if (Build.VERSION.SDK_INT >= 30)
                runCatching { v.mediaStoreVolumeName }.getOrNull() else null

            Log.i(
                TAG,
                "  volume uuid=$uuid desc=$description state=$state " +
                "removable=$isRemovable primary=$isPrimary emulated=$isEmulated " +
                "dir=$dir mediaStore=$msvn"
            )

            // Самый главный тест: реально ли мы можем читать каталог?
            val target = dir ?: uuid?.let { File("/storage/$it") }
            if (target != null) {
                val exists = runCatching { target.exists() }.getOrNull()
                val canRead = runCatching { target.canRead() }.getOrNull()
                val children = runCatching { target.list()?.size }.getOrNull()
                Log.i(
                    TAG,
                    "    File($target) exists=$exists canRead=$canRead children=$children"
                )
            }
        }

        // Сравнение: что вернёт Environment.getExternalStorageDirectory()
        // и канонический /storage. На S09 могут отличаться.
        Log.i(TAG, "  Environment.externalStorageDirectory=" +
            Environment.getExternalStorageDirectory())
        Log.i(TAG, "  /storage children=${runCatching { File("/storage").list()?.joinToString() }.getOrNull()}")
        Log.i(TAG, "==== probe end ====")
    }
}
