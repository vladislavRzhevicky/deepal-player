package com.deepal.videocast

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.fs.FileSystem
import me.jahnen.libaums.core.fs.UsbFile
import me.jahnen.libaums.core.usb.UsbCommunicationFactory
import me.jahnen.libaums.libusbcommunication.LibusbCommunicationCreator
import kotlin.concurrent.thread

/**
 * Состояние USB-flow для Compose: статус, файлы, навигация. Вытаскиваем
 * libaums init + browsing из старой UsbPickerActivity, оборачиваем в
 * наблюдаемые поля. UI-слой только подписан на state, никакой работы с
 * UsbDevice прямо не делает.
 */
class UsbBrowser(private val activity: Activity) {

    private val tag = "VideoCast.UsbBrowser"
    private val ACTION_USB_PERMISSION = "com.deepal.videocast.USB_PERMISSION"

    // --- observable state ----
    var status by mutableStateOf("Ищу USB...")
        private set
    var driveLabel by mutableStateOf("")
        private set
    /** Свободно % от capacity. По спеке pill в header показывает именно free. */
    var capacityPct by mutableStateOf(0)
        private set
    var mounted by mutableStateOf(false)
        private set
    /** "/" корень, "/Folder/Sub" — путь до текущей директории. */
    var path by mutableStateOf("/")
        private set
    val entries: MutableList<UsbEntry> = mutableStateListOf()

    private var device: UsbMassStorageDevice? = null
    private var fs: FileSystem? = null
    private var rootDir: UsbFile? = null
    private var currentDir: UsbFile? = null

    data class UsbEntry(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val raw: UsbFile,
    )

    private val permReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val dev = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            status = "permission granted=$granted device=${dev?.deviceName}"
            if (granted && dev != null) openDevice(dev)
        }
    }

    fun start() {
        // Регистрируем libusb-backend (быстрее, обходит JNI overhead Android'а)
        // как _первичный_, но при init() умеем откатиться на встроенный
        // DEVICE_CONNECTION. На пассажирском user'е S09 libusb падает EIO
        // на control transfer (kernel-driver usb-storage / Cockpit vold
        // удерживает устройство), а Android backend проходит — потому что
        // он маршрутизирует через системный USB-сервис, которому даны
        // нужные capabilities.
        runCatching {
            UsbCommunicationFactory.registerCommunication(LibusbCommunicationCreator())
        }
        useLibusbBackend()
        val flags = if (Build.VERSION.SDK_INT >= 34) Context.RECEIVER_EXPORTED else 0
        activity.registerReceiver(permReceiver, IntentFilter(ACTION_USB_PERMISSION), flags)
        discover()
    }

    private fun useLibusbBackend() {
        UsbCommunicationFactory.underlyingUsbCommunication =
            UsbCommunicationFactory.UnderlyingUsbCommunication.OTHER
    }

    private fun useAndroidBackend() {
        UsbCommunicationFactory.underlyingUsbCommunication =
            UsbCommunicationFactory.UnderlyingUsbCommunication.DEVICE_CONNECTION_SYNC
    }

    fun stop() {
        runCatching { activity.unregisterReceiver(permReceiver) }
        // Не закрываем device здесь — может быть pin'нут в UsbSession для плеера.
    }

    fun rescan() {
        if (device == null) discover()
    }

    private fun discover() {
        val um = activity.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = um.deviceList

        if (deviceList.isEmpty()) {
            status = "USB-устройств нет. Воткни флешку."
            mounted = false
            return
        }

        val rawDevice = deviceList.values.firstOrNull { d ->
            d.deviceClass == 8 || (0 until d.interfaceCount).any {
                d.getInterface(it).interfaceClass == 8
            }
        } ?: deviceList.values.first()

        if (!um.hasPermission(rawDevice)) {
            // FLAG_MUTABLE — иначе broadcast приходит с device=null.
            val piFlags = if (Build.VERSION.SDK_INT >= 31)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getBroadcast(
                activity, 0,
                Intent(ACTION_USB_PERMISSION).setPackage(activity.packageName),
                piFlags
            )
            status = "Запрос permission ${rawDevice.deviceName}..."
            um.requestPermission(rawDevice, pi)
        } else {
            openDevice(rawDevice)
        }
    }

    private fun openDevice(rawDevice: UsbDevice) {
        thread(isDaemon = true) {
            // Android backend первым: на Cockpit'е под пассажирским user'ом
            // libusb-попытка ломает USB endpoint (control transfer EIO),
            // и Android backend потом тоже падает с MAX_RECOVERY_ATTEMPTS.
            // Android backend идёт через системный USB-сервис → детачит
            // kernel-driver сам и не воюет с Cockpit vold.
            val backends = listOf("android" to ::useAndroidBackend, "libusb" to ::useLibusbBackend)
            var lastError: Throwable? = null
            var sawNoPartitions = false
            for ((tag2, switchBackend) in backends) {
                try {
                    switchBackend()
                    activity.runOnUiThread { status = "Инициализация ($tag2)..." }
                    val msds = UsbMassStorageDevice.getMassStorageDevices(activity)
                    val msd = msds.firstOrNull { it.usbDevice.deviceName == rawDevice.deviceName }
                        ?: throw IllegalStateException("MSD не найден")
                    msd.init()
                    // libaums поддерживает только FAT12/16/32 и exFAT, и только
                    // MBR partition table. NTFS / ext4 / APFS / GPT → partitions
                    // приходят пустые. Раньше тут летел IndexOutOfBoundsException
                    // на partitions[0]; теперь объясняем причину человеку.
                    if (msd.partitions.isEmpty()) {
                        sawNoPartitions = true
                        runCatching { msd.close() }
                        throw IllegalStateException("Файловая система не FAT32/exFAT")
                    }
                    device = msd
                    val partition = msd.partitions[0]
                    val filesystem = partition.fileSystem
                    fs = filesystem
                    rootDir = filesystem.rootDirectory
                    activity.runOnUiThread {
                        driveLabel = filesystem.volumeLabel.ifEmpty { "USB" }
                        val cap = filesystem.capacity
                        val free = (cap - filesystem.occupiedSpace).coerceAtLeast(0)
                        capacityPct = if (cap > 0) ((free * 100) / cap).toInt() else 0
                        mounted = true
                        status = "FS ${filesystem.type} ($tag2)"
                        cd(filesystem.rootDirectory)
                    }
                    return@thread
                } catch (e: Throwable) {
                    Log.w(tag, "openDevice via $tag2 failed", e)
                    lastError = e
                    // переходим к следующему backend'у
                }
            }
            Log.e(tag, "all backends failed", lastError)
            activity.runOnUiThread {
                val e = lastError
                status = when {
                    sawNoPartitions ->
                        "Флешка не FAT32/exFAT. Переформатируй её " +
                        "(Windows: «Этот компьютер» → правой кнопкой по флешке → " +
                        "Форматировать → exFAT)."
                    e is java.io.IOException || e?.message?.contains("EIO", true) == true ->
                        "init упал: ${e?.javaClass?.simpleName}: ${e?.message ?: "—"} " +
                        "— перевоткни флешку"
                    else ->
                        "init упал: ${e?.javaClass?.simpleName}: ${e?.message ?: "—"}"
                }
                mounted = false
            }
        }
    }

    /** Перейти в директорию (по UsbFile из текущего списка). */
    fun open(entry: UsbEntry) {
        if (!entry.isDirectory) return
        cd(entry.raw)
    }

    /** Подняться вверх по path до указанного префикса. "/" = корень. */
    fun navigateTo(targetPath: String) {
        val root = rootDir ?: return
        if (targetPath == "/") { cd(root); return }
        val segments = targetPath.trim('/').split("/").filter { it.isNotEmpty() }
        var dir: UsbFile = root
        for (seg in segments) {
            val next = runCatching { dir.listFiles().firstOrNull { it.isDirectory && it.name == seg } }
                .getOrNull() ?: break
            dir = next
        }
        cd(dir)
    }

    private fun cd(dir: UsbFile) {
        currentDir = dir
        path = dir.absolutePath.ifEmpty { "/" }
        entries.clear()
        runCatching {
            dir.listFiles()
                .filter { !isHidden(it.name) }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .forEach { f ->
                    entries += UsbEntry(
                        name = f.name,
                        isDirectory = f.isDirectory,
                        size = if (f.isDirectory) 0 else f.length,
                        raw = f
                    )
                }
        }.onFailure {
            Log.w(tag, "listFiles failed", it)
        }
    }

    /**
     * Скрытые/мусорные имена. Совпадение точное (без учёта регистра) — Windows и
     * macOS пишут эти каталоги в верхнем регистре в одних версиях и в смешанном в
     * других. Точку-префикс ловим отдельно (FAT не хранит dotfile-bit, но
     * соглашение Unix всё равно соблюдается всеми камерами/телефонами).
     */
    private fun isHidden(name: String): Boolean {
        if (name.startsWith(".")) return true
        return name.lowercase() in HIDDEN_NAMES
    }

    companion object {
        private val PLAYABLE_EXTS = setOf(
            "mp4", "mkv", "avi", "mov", "webm", "m4v", "ts", "mpg", "mpeg",
            "3gp", "flv", "wmv", "ogv",
        )
        private val HIDDEN_NAMES = setOf(
            // Windows
            "system volume information",
            "\$recycle.bin",
            "recycler",
            "recycled",
            "msocache",
            "\$winreagent",
            "\$getcurrent",
            "config.msi",
            // macOS
            ".trashes",
            ".spotlight-v100",
            ".fseventsd",
            ".temporaryitems",
            ".documentrevisions-v100",
            ".trash",
            ".apdisk",
            // Linux / fsck artefacts
            "lost+found",
            "lost.dir",
            "found.000",
            "found.001",
            "found.002",
            "found.003",
            // Huawei head-unit мусор на флешке
            "huawei",
            "huaweisystem",
            // CD/DVD junk
            "autorun.inf",
        )
    }

    /**
     * Pin'ит выбранный файл + сиблингов-медиа из текущей директории в
     * UsbSession; возвращает Pinned с плейлистом. Index — позиция [entry]
     * в плейлисте, чтобы PlayerActivity начал именно с него.
     */
    fun pinForPlayback(entry: UsbEntry): UsbSession.Pinned? {
        val dev = device ?: return null
        val mediaSiblings = entries
            .filter { !it.isDirectory && isPlayableExt(it.name) }
            .ifEmpty { listOf(entry) }
        val startIndex = mediaSiblings.indexOfFirst { it.raw === entry.raw }
            .takeIf { it >= 0 } ?: 0
        val pinned = UsbSession.pinPlaylist(
            device = dev,
            files = mediaSiblings.map { it.raw },
            startIndex = startIndex,
        )
        // Device теперь принадлежит сессии — браузер дальше использовать
        // его не будет (выйдем в плеер). Releas'нется по token из плеера.
        device = null
        mounted = false
        return pinned
    }

    /**
     * Файлы которые имеет смысл крутить как плейлист видео. Минимальный
     * список — расширяем по запросу. Аудио сейчас не поддерживаем (ExoPlayer
     * умеет, но UI-токены заточены под видео).
     */
    private fun isPlayableExt(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in PLAYABLE_EXTS
    }
}
