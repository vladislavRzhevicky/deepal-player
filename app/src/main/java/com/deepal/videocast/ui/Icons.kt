package com.deepal.videocast.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.ColorFilter

// Vector icons из icons.jsx, перенесённые 1-в-1 (viewBox 24x24).
// Все рисуем в currentColor — реальный цвет задаёт ColorFilter при отрисовке.

private fun strokeVector(name: String, build: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).path(
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = build
    ).build()

private fun fillVector(name: String, build: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero,
        pathBuilder = build
    ).build()

object ViaIcons {
    val Folder = strokeVector("folder") {
        moveTo(3f, 7f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
        horizontalLineToRelative(4f)
        lineToRelative(2f, 2.5f)
        horizontalLineToRelative(8f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
        verticalLineToRelative(8f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
        horizontalLineTo(5f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
        close()
    }

    val Play = fillVector("play") {
        moveTo(7f, 4.5f)
        verticalLineToRelative(15f)
        lineToRelative(13f, -7.5f)
        lineTo(7f, 4.5f)
        close()
    }

    val Pause = fillVector("pause") {
        moveTo(6f, 4.5f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(15f)
        horizontalLineToRelative(-4f)
        close()
        moveTo(14f, 4.5f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(15f)
        horizontalLineToRelative(-4f)
        close()
    }

    val Back = strokeVector("back") {
        moveTo(14f, 6f); lineToRelative(-6f, 6f); lineToRelative(6f, 6f)
    }

    val Chevron = strokeVector("chevron") {
        moveTo(9f, 6f); lineToRelative(6f, 6f); lineToRelative(-6f, 6f)
    }

    val Usb = strokeVector("usb") {
        moveTo(12f, 3f); verticalLineToRelative(13f)
        moveTo(12f, 16f); lineToRelative(-3.5f, -4f)
        moveTo(12f, 16f); lineToRelative(3.5f, -4f)
        moveTo(9f, 20f); arcToRelative(3f, 3f, 0f, false, false, 6f, 0f); verticalLineToRelative(-2f)
        horizontalLineTo(9f); close()
    }

    val Check = strokeVector("check") {
        moveTo(5f, 12f); lineToRelative(5f, 5f); lineTo(20f, 7f)
    }

    val Close = strokeVector("close") {
        moveTo(6f, 6f); lineToRelative(12f, 12f)
        moveTo(18f, 6f); lineToRelative(-12f, 12f)
    }

    val Skip10Fwd = strokeVector("skip10fwd") {
        moveTo(12f, 5f); verticalLineTo(2f); lineTo(17f, 6f); lineToRelative(-5f, 4f); verticalLineTo(7f)
        arcToRelative(5f, 5f, 0f, true, false, 5f, 5f)
    }

    val Skip10Back = strokeVector("skip10back") {
        moveTo(12f, 5f); verticalLineTo(2f); lineTo(7f, 6f); lineToRelative(5f, 4f); verticalLineTo(7f)
        arcToRelative(5f, 5f, 0f, true, true, -5f, 5f)
    }

    // Prev/Next track — стандартный "skip-to" дизайн: треугольник + полоса.
    val Next = fillVector("next") {
        moveTo(7f, 5f)
        verticalLineToRelative(14f)
        lineTo(16f, 12f)
        close()
        moveTo(17f, 5f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(14f)
        horizontalLineToRelative(-2f)
        close()
    }

    val Prev = fillVector("prev") {
        moveTo(17f, 5f)
        verticalLineToRelative(14f)
        lineTo(8f, 12f)
        close()
        moveTo(5f, 5f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(14f)
        horizontalLineToRelative(-2f)
        close()
    }

    val Volume = strokeVector("volume") {
        moveTo(5f, 9f); verticalLineToRelative(6f); horizontalLineToRelative(3f); lineToRelative(5f, 4f)
        verticalLineTo(5f); lineTo(8f, 9f); close()
        moveTo(16f, 8f); arcToRelative(5f, 5f, 0f, false, true, 0f, 8f)
    }

    val Screens = strokeVector("screens") {
        moveTo(2f, 5f); horizontalLineToRelative(13f); verticalLineToRelative(9f); horizontalLineToRelative(-13f); close()
        moveTo(16f, 9f); horizontalLineToRelative(6f); verticalLineToRelative(5f); horizontalLineToRelative(-6f); close()
        moveTo(7f, 18f); horizontalLineToRelative(7f)
    }
}

/** Иконка с tint, размер задаётся параметром. */
@Composable
fun ViaIcon(
    vector: ImageVector,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val painter = rememberVectorPainter(vector)
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
