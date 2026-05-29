package com.deepal.videocast

import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.deepal.videocast.ui.ExoVideoSurface
import com.deepal.videocast.ui.PlayerScreen
import com.deepal.videocast.ui.PlayerScreenInfo
import com.deepal.videocast.ui.theme.ViaTheme
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.delay

/**
 * Плеер с Compose-overlay поверх ExoPlayer surface.
 * usb_token → ContentProvider URI (ProxyFileDescriptor стримит UsbFile).
 * Иначе — playUri как есть.
 */
class PlayerActivity : ComponentActivity() {

    private var usbToken: Long = 0L
    private var targetDisplayId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usbToken = intent?.getLongExtra("usb_token", 0L) ?: 0L
        val targetName = intent?.getStringExtra("target_name") ?: "driver"
        targetDisplayId = intent?.getIntExtra("target_display", -1) ?: -1

        // MainActivity подняла ref до startActivity → если токен нашёлся в map,
        // мы уже держим ссылку. Если нет — сессия отменена, закрываемся.
        val pinnedAtStart = if (usbToken != 0L) UsbSession.get(usbToken) else null
        if (usbToken != 0L && pinnedAtStart == null) {
            usbToken = 0L
            finish()
            return
        }

        val initialUri: android.net.Uri? = if (pinnedAtStart != null) {
            UsbContentProvider.buildUri(usbToken, pinnedAtStart.currentIndex, pinnedAtStart.name)
        } else {
            intent?.data
        }
        if (initialUri == null) { finish(); return }
        val initialFileName = pinnedAtStart?.name ?: (initialUri.lastPathSegment ?: "")

        setContent {
            ViaTheme {
                val ctx = this@PlayerActivity
                val exo = remember {
                    val renderers = NextRenderersFactory(ctx).apply {
                        setExtensionRendererMode(
                            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                        )
                        setEnableDecoderFallback(true)
                    }
                    ExoPlayer.Builder(ctx, renderers).build().apply {
                        setMediaItem(MediaItem.fromUri(initialUri))
                        repeatMode = Player.REPEAT_MODE_OFF
                        prepare()
                        playWhenReady = true
                    }
                }
                var volumePct by remember { mutableFloatStateOf(1f) }
                var currentFileName by remember { mutableStateOf(initialFileName) }

                /**
                 * Свопает media item на файл по индексу из плейлиста UsbSession.
                 * Каждому индексу — отдельный content:// URI (`/<token>/<idx>/<name>`),
                 * ExoPlayer корректно сбрасывает demuxer/cache.
                 */
                fun playIndex(newIndex: Int) {
                    val p = UsbSession.get(usbToken) ?: return
                    val moved = p.moveTo(newIndex) ?: return
                    val file = p.fileAt(moved) ?: return
                    currentFileName = file.name
                    val uri = UsbContentProvider.buildUri(usbToken, moved, file.name)
                    exo.setMediaItem(MediaItem.fromUri(uri))
                    exo.prepare()
                    exo.playWhenReady = true
                }

                DisposableEffect(Unit) {
                    val handle = object : PlayerRegistry.Handle {
                        override val displayId: Int = targetDisplayId
                        override val targetName: String = targetName
                        override val fileName: String get() = currentFileName
                        override fun isPlaying() = exo.isPlaying
                        override fun currentMs() = exo.currentPosition
                        override fun durationMs() = exo.duration.coerceAtLeast(0L)
                        override fun playPause(play: Boolean) {
                            if (play) exo.play() else exo.pause()
                        }
                        override fun seekTo(ms: Long) { exo.seekTo(ms) }
                        override fun skip(deltaMs: Long) {
                            exo.seekTo((exo.currentPosition + deltaMs).coerceAtLeast(0L))
                        }
                        override fun volume(): Float = exo.volume
                        override fun setVolume(v: Float) {
                            val clamped = v.coerceIn(0f, 1f)
                            volumePct = clamped
                            exo.volume = clamped
                        }
                        override fun hasNext(): Boolean =
                            UsbSession.get(usbToken)?.hasNext() == true
                        override fun hasPrev(): Boolean =
                            UsbSession.get(usbToken)?.hasPrev() == true
                        override fun next() {
                            val p = UsbSession.get(usbToken) ?: return
                            if (p.hasNext()) playIndex(p.currentIndex + 1)
                        }
                        override fun prev() {
                            val p = UsbSession.get(usbToken) ?: return
                            if (p.hasPrev()) playIndex(p.currentIndex - 1)
                        }
                        override fun close() { finish() }
                    }
                    if (targetDisplayId >= 0) PlayerRegistry.register(handle)
                    onDispose {
                        if (targetDisplayId >= 0) PlayerRegistry.unregister(targetDisplayId)
                        exo.release()
                    }
                }

                var currentMs by remember { mutableLongStateOf(0L) }
                var durationMs by remember { mutableLongStateOf(0L) }
                var playing by remember { mutableStateOf(true) }
                var controlsVisible by remember { mutableStateOf(true) }
                var hideKey by remember { mutableStateOf(0) }

                LaunchedEffect(Unit) {
                    while (true) {
                        currentMs = exo.currentPosition
                        durationMs = exo.duration.coerceAtLeast(0)
                        playing = exo.isPlaying
                        delay(250)
                    }
                }

                LaunchedEffect(hideKey) {
                    if (controlsVisible) {
                        delay(3000)
                        controlsVisible = false
                    }
                }

                fun bump() {
                    controlsVisible = true
                    hideKey++
                }

                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerScreen(
                        fileName = currentFileName,
                        screens = listOf(
                            PlayerScreenInfo(targetName, true),
                        ),
                        currentMs = currentMs,
                        durationMs = durationMs,
                        playing = playing,
                        volume = volumePct,
                        controlsVisible = controlsVisible,
                        onToggleStageTap = {
                            if (controlsVisible) { controlsVisible = false } else { bump() }
                        },
                        onTogglePlay = {
                            if (exo.isPlaying) exo.pause() else exo.play()
                            bump()
                        },
                        onSeekTo = { ms -> exo.seekTo(ms); bump() },
                        onPrev = {
                            val p = UsbSession.get(usbToken)
                            if (p != null && p.hasPrev()) playIndex(p.currentIndex - 1)
                            bump()
                        },
                        onNext = {
                            val p = UsbSession.get(usbToken)
                            if (p != null && p.hasNext()) playIndex(p.currentIndex + 1)
                            bump()
                        },
                        onBack = { finish() },
                        onVolumeChange = { v ->
                            val clamped = v.coerceIn(0f, 1f)
                            volumePct = clamped
                            exo.volume = clamped
                            bump()
                        },
                    ) {
                        ExoVideoSurface(exo)
                    }
                }
            }
        }

        // setContent создаёт DecorView; до этого момента window.insetsController
        // null-ит и getInsetsController() NPE'ит. Делаем fullscreen после.
        applyFullscreenUi()
    }

    private fun applyFullscreenUi() {
        runCatching {
            val w = window ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val ctrl: WindowInsetsController = w.insetsController ?: return
                ctrl.hide(WindowInsets.Type.systemBars())
                ctrl.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }.onFailure { android.util.Log.w("VideoCast.Player", "fullscreen ui", it) }
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (usbToken != 0L) UsbSession.release(usbToken)
    }
}
