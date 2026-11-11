package com.batteriatotal.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.batteriatotal.data.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1A6C3E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F2C8),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4E6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D8),
    onSecondaryContainer = Color(0xFF0B1F14),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDF9E),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF5F9F2),
    onBackground = Color(0xFF141E17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141E17),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717A72),
    outlineVariant = Color(0xFFC1C9C1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7EE2A0),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF07512B),
    onPrimaryContainer = Color(0xFFB8F5C8),
    secondary = Color(0xFFB3CCBD),
    onSecondary = Color(0xFF1E3528),
    secondaryContainer = Color(0xFF334B3C),
    onSecondaryContainer = Color(0xFFCFE9D8),
    tertiary = Color(0xFFFFD27A),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5D4300),
    onTertiaryContainer = Color(0xFFFFE2A3),
    background = Color(0xFF07110A),
    onBackground = Color(0xFFE0EBE2),
    surface = Color(0xFF0D1911),
    onSurface = Color(0xFFE0EBE2),
    surfaceVariant = Color(0xFF1E2B22),
    onSurfaceVariant = Color(0xFFBCC9BE),
    outline = Color(0xFF87968A),
    outlineVariant = Color(0xFF2C3A30),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun BateriaTotalTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
