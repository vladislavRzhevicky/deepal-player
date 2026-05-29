package com.deepal.videocast.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// styles.css токены 1-в-1
object Via {
    val Bg = Color(0xFF0D0F12)
    val Bg2 = Color(0xFF14181D)
    val Surface = Color(0xFF1B2027)
    val Surface2 = Color(0xFF232A33)
    val Line = Color(0xFF2A323D)
    val Line2 = Color(0xFF3A4654)
    val Ink = Color(0xFFF1F3F6)
    val Ink2 = Color(0xFFB6BEC9)
    val Ink3 = Color(0xFF7A8492)
    val Accent = Color(0xFFFFB547)
    val AccentInk = Color(0xFF1A1100)
    val AccentDim = Color(0xFF5A3F10)
    val AccentPress = Color(0xFFFFA620)
    val Danger = Color(0xFFFF5A5F)
    val Ok = Color(0xFF6FE3A5)

    // sized in dp, multiples of 2
    object R {
        val Sm: Dp = 8.dp
        val Md: Dp = 12.dp
        val Lg: Dp = 18.dp
        val Btn: Dp = 16.dp
        val Chip: Dp = 20.dp
        val Modal: Dp = 24.dp
        val Pill: Dp = 999.dp
    }

    val Display: FontFamily = FontFamily.Default        // Space Grotesk substitute
    val Mono: FontFamily = FontFamily.Monospace          // JetBrains Mono substitute

    // typography scale (px @ 1440 reference → sp 1:1 для head unit)
    object Type {
        val tinyMono = TextStyle(fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        val microUpper = TextStyle(fontSize = 12.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium)
        val metaMono = TextStyle(fontFamily = Mono, fontSize = 13.sp)
        val meta = TextStyle(fontSize = 13.sp)
        val supporting = TextStyle(fontSize = 14.sp)
        val sub = TextStyle(fontFamily = Mono, fontSize = 15.sp)
        val time = TextStyle(fontFamily = Mono, fontSize = 16.sp)
        val body = TextStyle(fontSize = 16.sp)
        val card = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)
        val chipName = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        val primary = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        val brand = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
        val modalTitle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
        val gear = TextStyle(fontFamily = Mono, fontSize = 18.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        val button = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
    }
}

private val ViaScheme = darkColorScheme(
    background = Via.Bg,
    surface = Via.Surface,
    primary = Via.Accent,
    onPrimary = Via.AccentInk,
    onSurface = Via.Ink,
    onBackground = Via.Ink,
    error = Via.Danger,
)

@Composable
fun ViaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ViaScheme,
        content = content,
    )
}
