package com.deepal.videocast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepal.videocast.PlayerRegistry
import com.deepal.videocast.ui.theme.Via
import kotlinx.coroutines.delay

/**
 * Экран дистанционного управления: рендерится у водителя на месте
 * Browser'a после нажатия PLAY. Полностью переиспользует [PlayerScreen],
 * но `surface` — это placeholder "NOW PLAYING ON ...", а коллбеки
 * fan-out'ятся через [PlayerRegistry] на запущенные плееры на пассажирском
 * / заднем дисплеях.
 *
 * Если PlayerActivity сам по себе финишируется (например, юзер на заднем
 * экране тапнул back) — registry опустеет, и мы автоматически вызовем
 * [onStop], возвращая водительский экран в браузер.
 */
@Composable
fun RemoteControlScreen(
    targets: List<RemoteTarget>,
    onStop: () -> Unit,
) {
    val ids = remember(targets) { targets.map { it.displayId }.toSet() }

    var currentMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var volume by remember { mutableFloatStateOf(1f) }
    var fileName by remember { mutableStateOf("") }
    var controlsVisible by remember { mutableStateOf(true) }
    var hideKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(ids) {
        // Опрос состояния каждые 250 мс — то же что делает PlayerActivity
        // у себя, только через registry handle. Если все handle'ы отжили
        // ПОСЛЕ того как мы их увидели — выходим в браузер. До первого
        // появления handle'а допускаем grace-period 5 секунд (Activity ещё
        // не успела стартануть на чужом дисплее).
        var sawAny = false
        var emptyTicks = 0
        while (true) {
            val handles = PlayerRegistry.byDisplays(ids)
            if (handles.isEmpty()) {
                emptyTicks++
                if (sawAny || emptyTicks > 20 /* ~5s */) {
                    onStop()
                    return@LaunchedEffect
                }
                delay(250)
                continue
            }
            sawAny = true
            emptyTicks = 0
            val leader = handles.first()
            currentMs = leader.currentMs()
            durationMs = leader.durationMs()
            playing = leader.isPlaying()
            volume = leader.volume()
            fileName = leader.fileName
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

    fun fanOut(action: (PlayerRegistry.Handle) -> Unit) {
        PlayerRegistry.byDisplays(ids).forEach { runCatching { action(it) } }
    }

    PlayerScreen(
        fileName = fileName,
        screens = targets.map { PlayerScreenInfo(it.name, true) },
        currentMs = currentMs,
        durationMs = durationMs,
        playing = playing,
        volume = volume,
        controlsVisible = controlsVisible,
        onToggleStageTap = {
            if (controlsVisible) controlsVisible = false else bump()
        },
        onTogglePlay = {
            val nextPlay = !playing
            fanOut { it.playPause(nextPlay) }
            bump()
        },
        onSeekTo = { ms ->
            fanOut { it.seekTo(ms) }
            bump()
        },
        onPrev = {
            fanOut { it.prev() }
            bump()
        },
        onNext = {
            fanOut { it.next() }
            bump()
        },
        onBack = {
            fanOut { it.close() }
            onStop()
        },
        onVolumeChange = { v ->
            volume = v
            fanOut { it.setVolume(v) }
            bump()
        },
        surface = { RemoteStage(targets, fileName) },
    )
}

/**
 * UI-side представление куда мы кастуем. Не дублирует [com.deepal.videocast.ui.SceneTarget]
 * чтобы не тащить весь pickup-data в реестр.
 */
data class RemoteTarget(val displayId: Int, val name: String)

@Composable
private fun RemoteStage(targets: List<RemoteTarget>, fileName: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Via.Bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(40.dp),
        ) {
            Text(
                "NOW PLAYING ON",
                style = Via.Type.microUpper.copy(color = Via.Ink3, fontSize = 13.sp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                targets.joinToString(" + ") { it.name.uppercase() },
                style = Via.Type.modalTitle.copy(color = Via.Accent, fontSize = 36.sp),
            )
            Spacer(Modifier.height(16.dp))
            if (fileName.isNotEmpty()) {
                Text(
                    fileName,
                    style = Via.Type.body.copy(color = Via.Ink2),
                )
            }
        }
    }
}
