package com.deepal.videocast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.deepal.videocast.ui.theme.Via
import com.deepal.videocast.ui.theme.ViaTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp

/** Имя экрана + on/off для now-playing блока в правом верхнем углу. */
data class PlayerScreenInfo(val label: String, val on: Boolean)

@Composable
fun PlayerScreen(
    fileName: String,
    screens: List<PlayerScreenInfo>,
    currentMs: Long,
    durationMs: Long,
    playing: Boolean,
    volume: Float,
    controlsVisible: Boolean,
    onToggleStageTap: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    surface: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggleStageTap() })
            }
    ) {
        surface()

        // controls overlay
        val ctlAlpha = if (controlsVisible) 1f else 0f
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f * ctlAlpha),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.78f * ctlAlpha),
                        ),
                        startY = 0f, endY = Float.POSITIVE_INFINITY
                    )
                )
                .padding(horizontal = 26.dp, vertical = 22.dp),
        ) {
            if (controlsVisible) {
                // Top row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BackButton(onBack)
                    NowPlaying(fileName, screens)
                }

                // Bottom block
                Column(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SeekRow(currentMs, durationMs, onSeekTo)
                    ButtonRow(
                        playing = playing,
                        volume = volume,
                        onTogglePlay = onTogglePlay,
                        onPrev = onPrev,
                        onNext = onNext,
                        onVolumeChange = onVolumeChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(Via.R.Btn))
            .background(Color(0xB314181D))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(Via.R.Btn))
            .clickable { onBack() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ViaIcon(ViaIcons.Back, 14.dp, Via.Ink)
        Text("BACK", style = Via.Type.meta.copy(color = Via.Ink))
    }
}

@Composable
private fun NowPlaying(name: String, screens: List<PlayerScreenInfo>) {
    Column(horizontalAlignment = Alignment.End) {
        Text(name, style = Via.Type.card.copy(color = Via.Ink))
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            screens.forEach { s ->
                Text(
                    text = "${if (s.on) "●" else "○"} ${s.label.uppercase()}",
                    style = Via.Type.metaMono.copy(
                        color = if (s.on) Via.Accent else Via.Ink2,
                        letterSpacing = 1.sp,
                    )
                )
            }
        }
    }
}

@Composable
private fun SeekRow(
    currentMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
) {
    val safeDur = if (durationMs > 0) durationMs else 1
    val pct = (currentMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            fmtTime(currentMs),
            style = Via.Type.metaMono.copy(color = Via.Ink),
            modifier = Modifier.width(48.dp)
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .pointerInput(durationMs) {
                    detectTapGestures(onTap = { off ->
                        val w = size.width.coerceAtLeast(1)
                        val p = (off.x / w).coerceIn(0f, 1f)
                        onSeekTo((p * safeDur).toLong())
                    })
                },
        ) {
            val barWidth = maxWidth
            // track
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(Via.R.Pill))
                    .background(Color.White.copy(alpha = 0.18f))
                    .align(Alignment.CenterStart)
            )
            // fill
            Box(
                Modifier
                    .width(barWidth * pct)
                    .height(4.dp)
                    .clip(RoundedCornerShape(Via.R.Pill))
                    .background(Via.Accent)
                    .align(Alignment.CenterStart)
            )
            // thumb
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = barWidth * pct - 8.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Via.Accent)
                    .border(6.dp, Via.Accent.copy(alpha = 0.18f), CircleShape)
            )
        }
        Text(
            "−${fmtTime((safeDur - currentMs).coerceAtLeast(0))}",
            style = Via.Type.metaMono.copy(color = Via.Ink3),
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
private fun ButtonRow(
    playing: Boolean,
    volume: Float,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(ViaIcons.Prev, onPrev)
        IconBtnPlay(playing, onTogglePlay)
        IconBtn(ViaIcons.Next, onNext)
        Box(Modifier.width(1.dp).height(20.dp).background(Via.Line))
        VolumeSlider(volume = volume, onChange = onVolumeChange)
    }
}

@Composable
private fun VolumeSlider(volume: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(Via.R.Btn))
            .background(Color(0xB314181D))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(Via.R.Btn))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ViaIcon(
            // Перечёркнутый громкоговоритель когда mute — берём ту же иконку,
            // но цвет приглушённый.
            ViaIcons.Volume,
            16.dp,
            if (volume < 0.01f) Via.Ink3 else Via.Ink,
        )
        // Touch-зона вынесена в отдельный Box размером 220×44 — это
        // полный hit-area. Визуальная дорожка внутри (140dp) уже, чтобы
        // не растягивать UI: палец ловит в большой коробке, рисуем в
        // меньшей. Драг идёт по абсолютной позиции пальца (change.position.x),
        // не по дельте — финка не «уплывает» от точки касания.
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(44.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { off ->
                        val w = size.width.coerceAtLeast(1)
                        onChange((off.x / w).coerceIn(0f, 1f))
                    })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { off ->
                            val w = size.width.coerceAtLeast(1)
                            onChange((off.x / w).coerceIn(0f, 1f))
                        },
                        onHorizontalDrag = { change, _ ->
                            val w = size.width.coerceAtLeast(1)
                            onChange((change.position.x / w).coerceIn(0f, 1f))
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) {
                val barWidth = maxWidth
                // track
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(Via.R.Pill))
                        .background(Color.White.copy(alpha = 0.18f))
                        .align(Alignment.CenterStart)
                )
                // fill
                Box(
                    Modifier
                        .width(barWidth * volume.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(Via.R.Pill))
                        .background(Via.Accent)
                        .align(Alignment.CenterStart)
                )
                // thumb — крупный (20dp) с широким полупрозрачным кольцом
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = barWidth * volume.coerceIn(0f, 1f) - 10.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Via.Accent)
                        .border(8.dp, Via.Accent.copy(alpha = 0.22f), CircleShape)
                )
            }
        }
        Text(
            "${(volume.coerceIn(0f, 1f) * 100).toInt()}%",
            style = Via.Type.tinyMono.copy(color = Via.Ink2),
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun IconBtn(vec: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xB314181D))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        ViaIcon(vec, 19.dp, Via.Ink)
    }
}

@Composable
private fun IconBtnPlay(playing: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Via.Accent)
            .border(4.dp, Via.Accent.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        ViaIcon(if (playing) ViaIcons.Pause else ViaIcons.Play, 24.dp, Via.AccentInk)
    }
}

/** Видео-поверхность через AndroidView от Media3 PlayerView. */
@Composable
fun ExoVideoSurface(player: ExoPlayer) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { view -> view.player = player }
    )
}

// --- helpers ---

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%d:%02d".format(m, ss)
}

@Preview(widthDp = 1440, heightDp = 810)
@Composable
private fun PlayerPreview() {
    ViaTheme {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerScreen(
                fileName = "tbilisi-day1.mp4",
                screens = listOf(
                    PlayerScreenInfo("driver", true),
                    PlayerScreenInfo("passenger", false),
                    PlayerScreenInfo("hud", true),
                ),
                currentMs = 184_000,
                durationMs = 720_000,
                playing = true,
                volume = 0.65f,
                controlsVisible = true,
                onToggleStageTap = {}, onTogglePlay = {}, onSeekTo = {},
                onPrev = {}, onNext = {},
                onBack = {}, onVolumeChange = {},
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(listOf(Color(0xFF1A2330), Color(0xFF050709)))
                        )
                )
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun PlayerHudPreview() {
    PlayerPreview()
}
