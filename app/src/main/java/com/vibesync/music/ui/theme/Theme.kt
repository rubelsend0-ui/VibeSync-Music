package com.vibesync.music.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.vibesync.music.data.model.ThemePref

data class AccentOption(val name: String, val primary: Color, val secondary: Color)

val AccentOptions = listOf(
    AccentOption("Electric blue", Color(0xFF3D7BFF), Color(0xFF8B5CF6)),
    AccentOption("Violet", Color(0xFF8B5CF6), Color(0xFF3D7BFF)),
    AccentOption("Magenta", Color(0xFFE040FB), Color(0xFF7C4DFF)),
    AccentOption("Emerald", Color(0xFF1ED6A1), Color(0xFF3D7BFF)),
    AccentOption("Amber", Color(0xFFFFB020), Color(0xFFFF5C7A)),
)

val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun VibeSyncTheme(theme: ThemePref, accentIndex: Int, content: @Composable () -> Unit) {
    val dark = when (theme) {
        ThemePref.DARK -> true
        ThemePref.LIGHT -> false
        ThemePref.SYSTEM -> isSystemInDarkTheme()
    }
    val accent = AccentOptions.getOrElse(accentIndex) { AccentOptions[0] }
    val onAccent = if (accent.primary.luminance() > 0.5f) Color.Black else Color.White
    val scheme = if (dark) {
        val container = accent.primary.copy(alpha = 0.24f).compositeOver(Color.Black)
        darkColorScheme(
            primary = accent.primary, onPrimary = onAccent, secondary = accent.secondary, onSecondary = Color.White,
            primaryContainer = container, onPrimaryContainer = Color.White,
            secondaryContainer = container, onSecondaryContainer = Color.White,
            background = Color.Black, onBackground = Color(0xFFF2F2F7),
            surface = Color(0xFF0A0A0F), onSurface = Color(0xFFF2F2F7),
            surfaceVariant = Color(0xFF16161F), onSurfaceVariant = Color(0xFFA6A6B8),
            outline = Color(0xFF3A3A48), error = Color(0xFFFF6B6B),
        )
    } else {
        val container = accent.primary.copy(alpha = 0.16f).compositeOver(Color.White)
        lightColorScheme(
            primary = accent.primary, onPrimary = onAccent, secondary = accent.secondary,
            primaryContainer = container, secondaryContainer = container,
            background = Color(0xFFF7F7FB), onBackground = Color(0xFF111118),
            surface = Color.White, onSurface = Color(0xFF111118),
            surfaceVariant = Color(0xFFEAEAF2), onSurfaceVariant = Color(0xFF55556A),
            outline = Color(0xFFB8B8C8),
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
