package com.deepal.videocast

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.deepal.videocast.ui.BrowserScreen
import com.deepal.videocast.ui.RemoteControlScreen
import com.deepal.videocast.ui.RemoteTarget
import com.deepal.videocast.ui.SceneId
import com.deepal.videocast.ui.SceneTarget
import com.deepal.videocast.ui.ScenesPickerOverlay
import com.deepal.videocast.ui.theme.Via
import com.deepal.videocast.ui.theme.ViaTheme
import kotlin.concurrent.thread

/**
 * Главный экран приложения: Browser + Scenes picker overlay.
 * USB-flow живёт в UsbBrowser; backend для cross-user — CastClient/CastListenerService.
 */
class MainActivity : ComponentActivity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var usb: UsbBrowser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CastListenerService.start(this)
        usb = UsbBrowser(this)

        setContent {
            ViaTheme {
                Box(Modifier.fillMaxSize().background(Via.Bg)) {
                    var selected by remember { mutableStateOf<UsbBrowser.UsbEntry?>(null) }
                    var step by remember { mutableStateOf(Step.Browse) }
                    var scenes by remember { mutableStateOf<List<SceneTarget>>(emptyList()) }
                    var diagnostic by remember { mutableStateOf<String?>(null) }
                    var selection by remember { mutableStateOf(emptySet<SceneId>()) }
                    var remoteTargets by remember { mutableStateOf<List<RemoteTarget>>(emptyList()) }

                    LaunchedEffect(Unit) { usb.start() }

                    // Перезагружаем displays каждый раз когда открыли picker
                    LaunchedEffect(step) {
                        if (step == Step.Pick) {
                            scenes = emptyList()
                            diagnostic = "Ищу занятые места..."
                            thread(isDaemon = true) {
                                val pongs = CastClient.discoverUsers()
                                val built = buildScenes(pongs)
                                val diag = diagnosticFor(pongs)
                                main.post {
                                    scenes = built
                                    diagnostic = diag
                                }
                            }
                        } else {
                            diagnostic = null
                        }
                    }

                    when (step) {
                        Step.Remote -> {
                            RemoteControlScreen(
                                targets = remoteTargets,
                                onStop = {
                                    remoteTargets = emptyList()
                                    selection = emptySet()
                                    selected = null
                                    step = Step.Browse
                                },
                            )
                        }
                        Step.Browse, Step.Pick -> {
                            BrowserScreen(
                                usb = usb,
                                selected = selected,
                                onSelect = { selected = it },
                                onOpenScenes = {
                                    if (selected != null) step = Step.Pick
                                },
                            )

                            if (step == Step.Pick && selected != null) {
                                ScenesPickerOverlay(
                                    selectedFileName = selected!!.name,
                                    selectedFileSize = formatSize(selected!!.size),
                                    scenes = scenes.ifEmpty { fallbackScenes() },
                                    selection = selection,
                                    onToggle = { id ->
                                        selection = if (id in selection) selection - id else selection + id
                                    },
                                    onClose = {
                                        step = Step.Browse
                                        selection = emptySet()
                                    },
                                    onPlay = {
                                        val entry = selected ?: return@ScenesPickerOverlay
                                        val targets = scenes.filter { it.id in selection && !it.unavailable }
                                            .ifEmpty {
                                                fallbackScenes().filter { it.id in selection && !it.unavailable }
                                            }
                                        if (targets.isEmpty()) return@ScenesPickerOverlay
                                        val launched = playOn(entry, targets)
                                        if (launched.isNotEmpty()) {
                                            remoteTargets = launched.map {
                                                RemoteTarget(it.displayId, it.name)
                                            }
                                            step = Step.Remote
                                        }
                                    },
                                    diagnostic = diagnostic,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        usb.stop()
    }

    private enum class Step { Browse, Pick, Remote }

    /** Возвращает список таргетов, для которых плеер реально стартовал. */
    private fun playOn(entry: UsbBrowser.UsbEntry, targets: List<SceneTarget>): List<SceneTarget> {
        // pin() ставит baseline ref=1. Под каждый target — acquire (ref++)
        // ДО startActivity, чтобы плеер гарантированно нашёл токен живым.
        // В конце релизим baseline; refs обнулится после onDestroy всех плееров.
        val pinned = usb.pinForPlayback(entry) ?: run {
            Toast.makeText(this, "USB device больше не доступен", Toast.LENGTH_LONG).show()
            return emptyList()
        }
        val launched = mutableListOf<SceneTarget>()
        for (t in targets) {
            val acquired = UsbSession.acquire(pinned.token) ?: continue
            try {
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra("usb_token", acquired.token)
                    putExtra("usb_name", acquired.name)
                    putExtra("target_name", t.name)
                    putExtra("target_display", t.displayId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                // launchDisplayId стартует Activity на указанном дисплее в нашем
                // же user-контексте. Для S09 ожидаем что 6 (co-driver) и 4
                // (central_rear) откроются — они FLAG_PRESENTATION/FLAG_DISPLAY_CREATE_USER,
                // но не приватны к чужому user'у. Если AM отобьёт — поймаем.
                val opts = ActivityOptions.makeBasic().apply { launchDisplayId = t.displayId }
                startActivity(intent, opts.toBundle())
                launched += t
            } catch (e: Exception) {
                Toast.makeText(this, "${t.name}: ${e.message}", Toast.LENGTH_LONG).show()
                UsbSession.release(pinned.token)
            }
        }
        // Baseline ref — всегда отпускаем.
        UsbSession.release(pinned.token)
        if (launched.isEmpty()) {
            Toast.makeText(this, "Не удалось запустить плеер ни на одном экране",
                Toast.LENGTH_LONG).show()
        }
        return launched
    }

    private fun buildScenes(pongs: List<CastProtocol.Pong>): List<SceneTarget> {
        val myUserId = currentUserId()
        // S09 displayId layout (confirmed via dumpsys display 2026-05-30):
        //   0 = control_panel       — driver,    2560×1440 @426dpi
        //   3 = hud_panel           — HUD projection (мы его не используем)
        //   4 = central_rear_panel  — rear,      3036×1708 @213dpi
        //   6 = co-driver_panel     — passenger, 2560×1440 @213dpi
        //
        // Подход v2: запускаем PlayerActivity из нашего же процесса с
        // launchDisplayId, не заморачиваясь с cross-user. ActivityManager
        // либо позволит (тогда видео идёт на чужом экране в нашем user'е),
        // либо отобьёт SecurityException — поймаем в playOn и Toast'нем.
        // Discovery теперь нужно только чтобы вытащить живые res/dpi.
        fun findInfo(displayId: Int): CastProtocol.DisplayInfo? =
            pongs.firstNotNullOfOrNull { p ->
                p.displays.firstOrNull { it.id == displayId }
            }

        val driver = findInfo(0)
        val passenger = findInfo(6)
        val rear = findInfo(4)

        fun resOf(i: CastProtocol.DisplayInfo?, fallback: String): String {
            if (i == null) return fallback
            return if (i.widthPx > 0 && i.heightPx > 0) "${i.widthPx}×${i.heightPx}" else fallback
        }
        fun dpiOf(i: CastProtocol.DisplayInfo?, fallback: Int): Int =
            i?.densityDpi?.takeIf { it > 0 } ?: fallback

        return listOf(
            SceneTarget(
                id = SceneId.Driver, name = "Driver",
                res = resOf(driver, "2560×1440"),
                dpi = dpiOf(driver, 426),
                aspect = 16f / 9f, visWidthFraction = 1f,
                unavailable = false,
                userId = myUserId,
                displayId = 0,
            ),
            SceneTarget(
                id = SceneId.Passenger, name = "Passenger",
                res = resOf(passenger, "2560×1440"),
                dpi = dpiOf(passenger, 213),
                aspect = 16f / 9f, visWidthFraction = 1f,
                unavailable = false,
                userId = myUserId,
                displayId = 6,
            ),
            SceneTarget(
                id = SceneId.Rear, name = "Rear",
                res = resOf(rear, "3036×1708"),
                dpi = dpiOf(rear, 213),
                aspect = 16f / 9f, visWidthFraction = 1f,
                unavailable = false,
                userId = myUserId,
                displayId = 4,
            ),
        )
    }

    /**
     * Возвращает строку для diagnostic panel в picker. Null — когда discovery
     * прошло чисто (нашли своего user'а с хотя бы одним display'ом). Иначе
     * объясняем что именно не так — чтобы Влад понял "сервис у пассажира не
     * запущен" vs "вообще никого нет".
     */
    private fun diagnosticFor(pongs: List<CastProtocol.Pong>): String? {
        if (pongs.isEmpty()) {
            return "Не нашёл ни одного пользователя. CastListenerService не запущен — " +
                "либо мы установлены только в нашем user'е, либо boot-receiver не отработал."
        }
        val myUserId = currentUserId()
        val others = pongs.filter { it.userId != myUserId }
        val crossUserDisplays = others.flatMap { it.displays.map { d -> d.id } }.toSet()
        if (crossUserDisplays.isNotEmpty() && others.isNotEmpty()) {
            return "Найдены экраны у другого user (${others.joinToString { it.userId.toString() }}). " +
                "Для USB-плейбэка нужен наш экземпляр на том user'е (cross-user токен USB не работает)."
        }
        return null
    }

    /** Если discovery ничего не нашло — рисуем все три unavailable но видимыми. */
    private fun fallbackScenes(): List<SceneTarget> = listOf(
        SceneTarget(SceneId.Driver, "Driver", "2560×1440", 426, 16f/9f, 1f, true, currentUserId(), 0),
        SceneTarget(SceneId.Passenger, "Passenger", "2560×1440", 213, 16f/9f, 1f, true, currentUserId(), 6),
        SceneTarget(SceneId.Rear, "Rear", "3036×1708", 213, 16f/9f, 1f, true, currentUserId(), 4),
    )

    private fun currentUserId(): Int = runCatching {
        val cls = Class.forName("android.os.UserHandle")
        cls.getMethod("myUserId").invoke(null) as Int
    }.getOrElse { Process.myUid() / 100000 }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var b = bytes.toDouble(); var i = 0
        while (b >= 1024 && i < units.lastIndex) { b /= 1024; i++ }
        return "%.1f %s".format(b, units[i])
    }
}
