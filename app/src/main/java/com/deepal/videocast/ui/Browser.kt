package com.deepal.videocast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import com.deepal.videocast.UsbBrowser
import com.deepal.videocast.ui.theme.Via
import com.deepal.videocast.ui.theme.ViaTheme

private val FilePalette = listOf(
    Color(0xFF2E4453),
    Color(0xFF3C2E53),
    Color(0xFF53462E),
    Color(0xFF2E5346),
    Color(0xFF532E3C),
    Color(0xFF2E3853),
)

@Composable
fun BrowserScreen(
    usb: UsbBrowser,
    selected: UsbBrowser.UsbEntry?,
    onSelect: (UsbBrowser.UsbEntry) -> Unit,
    onOpenScenes: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Via.Bg)) {
        Header(
            mounted = usb.mounted,
            driveLabel = usb.driveLabel.ifEmpty { "—" },
            capacityPct = usb.capacityPct,
        )
        Breadcrumbs(
            path = usb.path,
            onCrumb = { usb.navigateTo(it) },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (usb.entries.isEmpty()) {
                Text(
                    text = if (!usb.mounted) usb.status else "Empty folder",
                    style = Via.Type.body.copy(color = Via.Ink3),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    // 140 dp adaptive ≈ 6 карточек в ряд на 2560×1440/426 dpi
                    // driver-экране. Если экран уже — сжимается до 4-5.
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val list = usb.entries.toList()
                    itemsIndexed(list) { idx, e ->
                        if (e.isDirectory) {
                            FolderCard(e) { usb.open(e) }
                        } else {
                            FileCard(
                                entry = e,
                                tone = FilePalette[idx % FilePalette.size],
                                selected = selected?.name == e.name && selected.raw === e.raw,
                                onClick = { onSelect(e) }
                            )
                        }
                    }
                }
            }
        }
        BottomBar(selected = selected, onContinue = onOpenScenes)
    }
}

@Composable
private fun Header(
    mounted: Boolean,
    driveLabel: String,
    capacityPct: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Via.Bg)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            // brand mark
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Via.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("▷", style = Via.Type.meta.copy(color = Via.AccentInk, fontSize = 11.sp))
            }
            Column {
                Text(
                    "VIA Player",
                    style = Via.Type.meta.copy(
                        color = Via.Ink,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                )
                Text(
                    "USB · MEDIA",
                    style = Via.Type.microUpper.copy(color = Via.Ink3, fontSize = 9.sp)
                )
            }
        }
        UsbPill(mounted, driveLabel, capacityPct)
    }
    // bottom border
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Via.Line)
    )
}

@Composable
private fun UsbPill(mounted: Boolean, driveLabel: String, capacityPct: Int) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(Via.R.Pill))
            .background(Via.Surface)
            .border(1.dp, Via.Line, RoundedCornerShape(Via.R.Pill))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (mounted) Via.Ok else Via.Ink3)
        )
        ViaIcon(ViaIcons.Usb, 12.dp, Via.Ink2)
        Text(driveLabel, style = Via.Type.meta.copy(color = Via.Ink2))
        if (mounted) {
            Text("· $capacityPct% free", style = Via.Type.tinyMono.copy(color = Via.Ink3))
        }
    }
}

@Composable
private fun Breadcrumbs(
    path: String,
    onCrumb: (String) -> Unit,
) {
    val parts = if (path == "/") emptyList() else path.trim('/').split("/")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Crumb(
            label = "USB",
            icon = ViaIcons.Usb,
            current = parts.isEmpty(),
            onClick = { onCrumb("/") }
        )
        var acc = ""
        parts.forEachIndexed { i, p ->
            acc = if (acc.isEmpty()) "/$p" else "$acc/$p"
            val sub = acc
            ViaIcon(ViaIcons.Chevron, 10.dp, Via.Ink3)
            Crumb(
                label = p,
                icon = null,
                current = i == parts.lastIndex,
                onClick = { onCrumb(sub) }
            )
        }
    }
}

@Composable
private fun Crumb(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    current: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (current) Via.Surface else Color.Transparent
    val border = if (current) Via.Line else Color.Transparent
    val ink = if (current) Via.Ink else Via.Ink2
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) ViaIcon(icon, 12.dp, ink)
        Text(label, style = Via.Type.meta.copy(color = ink))
    }
}

@Composable
private fun FolderCard(entry: UsbBrowser.UsbEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .heightIn(min = 110.dp)
            .clip(RoundedCornerShape(Via.R.Md))
            .background(Via.Surface)
            .border(1.dp, Via.Line, RoundedCornerShape(Via.R.Md))
            .clickable { onClick() }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .drawWithCache {
                    val stripe = 7f
                    val color = Via.Accent.copy(alpha = 0.06f)
                    onDrawBehind {
                        val w = size.width; val h = size.height
                        val step = stripe * 2
                        // diagonal repeating-linear-gradient
                        var d = -h
                        while (d < w + h) {
                            drawLine(
                                color = color,
                                start = Offset(d, h),
                                end = Offset(d + h, 0f),
                                strokeWidth = stripe
                            )
                            d += step
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            ViaIcon(ViaIcons.Folder, 28.dp, Via.Accent)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Via.Line))
        Column(
            Modifier.padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 8.dp)
        ) {
            Text(entry.name, style = Via.Type.meta.copy(color = Via.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text("folder", style = Via.Type.tinyMono.copy(color = Via.Ink3))
        }
    }
}

@Composable
private fun FileCard(
    entry: UsbBrowser.UsbEntry,
    tone: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) Via.Surface.blend(Via.Accent, 0.08f) else Via.Surface
    val border = if (selected) Via.Accent else Via.Line
    Column(
        modifier = Modifier
            .heightIn(min = 110.dp)
            .clip(RoundedCornerShape(Via.R.Md))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(Via.R.Md))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(tone, tone.copy(alpha = 0.55f)),
                        start = Offset(0f, 0f),
                        end = Offset(400f, 200f)
                    )
                )
        ) {
            // ext badge
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = entry.name.substringAfterLast('.', "").uppercase().take(4).ifEmpty { "FILE" },
                    style = Via.Type.tinyMono.copy(color = Via.Ink)
                )
            }
            // play indicator
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                ViaIcon(ViaIcons.Play, 10.dp, Color.White)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Via.Line))
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 8.dp)) {
            Text(entry.name, style = Via.Type.meta.copy(color = Via.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(formatSize(entry.size), style = Via.Type.tinyMono.copy(color = Via.Ink3))
        }
    }
}

@Composable
private fun BottomBar(
    selected: UsbBrowser.UsbEntry?,
    onContinue: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Via.Bg2.copy(alpha = 0f), Via.Bg2)
                )
            )
    ) {
        Box(
            Modifier.fillMaxWidth().height(1.dp).background(Via.Line).align(Alignment.TopCenter)
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                if (selected == null) {
                    Text(
                        "Tap a file to choose it",
                        style = Via.Type.body.copy(color = Via.Ink3),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        selected.name,
                        style = Via.Type.body.copy(color = Via.Ink),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatSize(selected.size),
                        style = Via.Type.tinyMono.copy(color = Via.Ink3),
                    )
                }
            }
            PrimaryButton(
                text = "SCREENS",
                leadingIcon = ViaIcons.Screens,
                trailingIcon = ViaIcons.Chevron,
                enabled = selected != null,
                onClick = onContinue,
                compact = true,
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (enabled) Via.Accent else Via.Bg2
    val ink = if (enabled) Via.AccentInk else Via.Ink3
    val minH = if (compact) 40.dp else 80.dp
    val padH = if (compact) 18.dp else 36.dp
    val gap = if (compact) 8.dp else 12.dp
    val leadSize = if (compact) 16.dp else 24.dp
    val trailSize = if (compact) 14.dp else 20.dp
    val textStyle = if (compact)
        Via.Type.body.copy(
            color = ink,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
    else Via.Type.button.copy(color = ink)
    Row(
        modifier = Modifier
            .heightIn(min = minH)
            .clip(RoundedCornerShape(Via.R.Btn))
            .background(bg)
            .border(1.dp, if (enabled) Via.Accent else Via.Line, RoundedCornerShape(Via.R.Btn))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = padH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (leadingIcon != null) ViaIcon(leadingIcon, leadSize, ink)
        Text(text, style = textStyle)
        if (trailingIcon != null) ViaIcon(trailingIcon, trailSize, ink)
    }
}

private fun Color.blend(other: Color, t: Float): Color = Color(
    red = red + (other.red - red) * t,
    green = green + (other.green - green) * t,
    blue = blue + (other.blue - blue) * t,
    alpha = alpha,
)

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var b = bytes.toDouble(); var i = 0
    while (b >= 1024 && i < units.lastIndex) { b /= 1024; i++ }
    return "%.1f %s".format(b, units[i])
}

@Preview(widthDp = 1440, heightDp = 810, showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun BrowserPreview() {
    ViaTheme {
        // Statically previewable header layout (без libaums)
        Column(Modifier.fillMaxSize().background(Via.Bg)) {
            Header(true, "SanDisk 64GB", 78)
            Breadcrumbs("/Trips 2026/Tbilisi → Kazbegi") {}
            Spacer(Modifier.weight(1f))
            BottomBar(selected = null) {}
        }
    }
}
