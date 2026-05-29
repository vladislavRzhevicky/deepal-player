package com.deepal.videocast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.sp
import com.deepal.videocast.ui.theme.Via
import com.deepal.videocast.ui.theme.ViaTheme

enum class SceneId { Driver, Passenger, Rear }

data class SceneTarget(
    val id: SceneId,
    val name: String,
    val res: String,
    val dpi: Int,
    val aspect: Float,        // 16:9 = 1.78, etc
    val visWidthFraction: Float, // 1.0 — full width
    val unavailable: Boolean,
    val userId: Int,
    val displayId: Int,
    /** Текст для красного badge, если unavailable. Дефолт — "NO OCCUPANT". */
    val unavailableBadge: String = "NO OCCUPANT",
)

@Composable
fun ScenesPickerOverlay(
    selectedFileName: String,
    selectedFileSize: String,
    scenes: List<SceneTarget>,
    selection: Set<SceneId>,
    onToggle: (SceneId) -> Unit,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    diagnostic: String? = null,
) {
    // scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC08090D))
            .clickable(enabled = true) { /* swallow */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 1180.dp)
                .padding(horizontal = 40.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Via.Bg2)
                .border(1.dp, Via.Line, RoundedCornerShape(24.dp))
                .padding(start = 44.dp, end = 44.dp, top = 40.dp, bottom = 36.dp)
        ) {
            // header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Where to play it?", style = Via.Type.modalTitle.copy(color = Via.Ink))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$selectedFileName · $selectedFileSize",
                        style = Via.Type.sub.copy(color = Via.Ink3)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Via.Surface)
                        .border(1.dp, Via.Line, RoundedCornerShape(14.dp))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    ViaIcon(ViaIcons.Close, 22.dp, Via.Ink2)
                }
            }

            if (!diagnostic.isNullOrEmpty()) {
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Via.Danger.copy(alpha = 0.10f))
                        .border(1.dp, Via.Danger.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        diagnostic,
                        style = Via.Type.meta.copy(color = Via.Danger, fontSize = 14.sp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // 3-column chip grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                scenes.forEach { sc ->
                    Box(Modifier.weight(1f)) {
                        ScreenChip(
                            target = sc,
                            on = sc.id in selection,
                            onToggle = { onToggle(sc.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // footer
            Box(Modifier.fillMaxWidth().height(1.dp).background(Via.Line))
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val activeNames = scenes.filter { it.id in selection }.map { it.name }
                val text = if (activeNames.isEmpty())
                    "Tap a screen to start playing"
                else
                    "Playing to ${activeNames.joinToString(" + ")} · ${activeNames.size} screen${if (activeNames.size > 1) "s" else ""}"
                Text(text, style = Via.Type.body.copy(color = Via.Ink2))
                PrimaryButton(
                    text = "PLAY",
                    leadingIcon = ViaIcons.Play,
                    enabled = activeNames.isNotEmpty(),
                    onClick = onPlay,
                )
            }
        }
    }
}

@Composable
private fun ScreenChip(
    target: SceneTarget,
    on: Boolean,
    onToggle: () -> Unit,
) {
    val bg = if (on) Via.Surface.blendTone(Via.Accent, 0.10f) else Via.Surface
    val border = if (on) Via.Accent else Via.Line
    val alpha = if (target.unavailable) 0.55f else 1f
    Box(
        modifier = Modifier
            .heightIn(min = 220.dp)
            .clip(RoundedCornerShape(Via.R.Chip))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(Via.R.Chip))
            .let { if (target.unavailable) it else it.clickable { onToggle() } }
    ) {
        Column(
            modifier = Modifier
                .padding(22.dp)
                .alpha(alpha),
        ) {
        // visualization rect with hatch pattern
        Box(
            modifier = Modifier
                .fillMaxWidth(target.visWidthFraction)
                .aspectRatio(target.aspect)
                .clip(RoundedCornerShape(10.dp))
                .background(if (on) Via.Bg.blendTone(Via.Accent, 0.14f) else Via.Bg)
                .border(
                    1.dp,
                    if (on) Via.Accent else Via.Line2,
                    RoundedCornerShape(10.dp)
                )
                .drawWithCache {
                    val stripeColor = Color.White.copy(alpha = 0.02f)
                    onDrawBehind {
                        val w = size.width; val h = size.height
                        val step = 16f
                        var d = -h
                        while (d < w + h) {
                            drawLine(
                                color = stripeColor,
                                start = Offset(d, 0f),
                                end = Offset(d + h, h),
                                strokeWidth = 8f
                            )
                            d += step
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = labelForChip(target),
                style = Via.Type.tinyMono.copy(color = if (on) Via.Accent else Via.Ink3),
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(target.name, style = Via.Type.chipName.copy(color = Via.Ink))
                Spacer(Modifier.height(2.dp))
                Text(
                    "${target.res} · ${target.dpi} dpi",
                    style = Via.Type.metaMono.copy(color = Via.Ink3, fontSize = 13.sp)
                )
            }
            // toggle dot
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (on) Via.Accent else Color.Transparent)
                    .border(2.dp, if (on) Via.Accent else Via.Line2, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (on) ViaIcon(ViaIcons.Check, 20.dp, Via.AccentInk)
            }
        }
    }
        if (target.unavailable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Via.Danger.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    target.unavailableBadge,
                    style = Via.Type.tinyMono.copy(color = Via.Danger, letterSpacing = 1.4.sp)
                )
            }
        }
    }
}

private fun labelForChip(t: SceneTarget): String = when (t.id) {
    SceneId.Driver -> "16:9 · DRIVER"
    SceneId.Passenger -> "16:9 · PASS."
    SceneId.Rear -> "16:9 · REAR"
}

private fun Color.blendTone(other: Color, t: Float): Color = Color(
    red = red + (other.red - red) * t,
    green = green + (other.green - green) * t,
    blue = blue + (other.blue - blue) * t,
    alpha = alpha,
)

@Preview(widthDp = 1440, heightDp = 810, showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ScenesPickerPreview() {
    ViaTheme {
        ScenesPickerOverlay(
            selectedFileName = "tbilisi-day1.mp4",
            selectedFileSize = "12:04 · 1.2 GB",
            scenes = listOf(
                SceneTarget(SceneId.Driver, "Driver", "2560×1440", 426, 16f/9f, 1f, false, 0, 0),
                SceneTarget(SceneId.Passenger, "Passenger", "2560×1440", 213, 16f/9f, 1f, true, 0, 6),
                SceneTarget(SceneId.Rear, "Rear", "3036×1708", 213, 16f/9f, 1f, false, 0, 4),
            ),
            selection = setOf(SceneId.Driver, SceneId.Rear),
            onToggle = {}, onClose = {}, onPlay = {}
        )
    }
}
