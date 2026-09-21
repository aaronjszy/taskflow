package com.ajsy.taskflow.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppTheme { LIGHT, DARK, E_INK }

private val eink = EinkColors()

private val EInkThemeConfig = ThemeConfig(
    material = MaterialColors(
        primary = eink.black,
        secondary = eink.mediumGray,
        tertiary = eink.mediumGray,
        background = eink.white,
        surface = eink.white,
        surfaceVariant = eink.veryLightGray,
        onPrimary = eink.white,
        onSecondary = eink.white,
        onTertiary = eink.white,
        onBackground = eink.black,
        onSurface = eink.black,
        error = eink.black,
        onError = eink.white,
    ),
    custom = CustomColors(
        success = eink.lightGray,
        warning = eink.lightGray,
        error = eink.mediumGray,
        info = eink.lightGray,
        progress = eink.lightGray,
        progressBackground = eink.lightGray,
        timerRunning = eink.black,
        timerPaused = eink.lightGray,
        border = eink.black,
        divider = eink.mediumGray,
        button = eink.mediumGray,
        buttonText = eink.black,
        fab = eink.black,
        fabText = eink.black,
        lightSuccess = eink.veryLightGray,
        darkSuccess = eink.mediumGray,
        lateError = eink.black,
        darkError = eink.black,
        aboveColor = eink.lightGray,
        belowColor = eink.lightGray,
    ),
)

private val DarkThemeConfig = ThemeConfig(
    material = MaterialColors(
        primary = Color(0xFF5AFF54),
        secondary = Color(0xFFFCF758),
        tertiary = Color(0xFFFA5852),
    ),
    custom = CustomColors(
        success = Color(0xFF5AFF54),
        warning = Color(0xFFFCF758),
        error = Color(0xFFFA5852),
        info = Color(0xFF03DAC5),
        progress = Color(0xFF5AFF54),
        progressBackground = Color(0xFF2E4A2E),
        timerRunning = Color(0xFF5AFF54),
        timerPaused = Color(0xFF666666),
        border = Color(0xFF333333),
        divider = Color(0xFF444444),
        button = Color(0xFF5AFF54),
        buttonText = Color.White,
        fab = Color(0xFF5AFF54),
        fabText = Color.White,
        lightSuccess = Color(0xFF2E4A2E),
        darkSuccess = Color(0xFF1A5A1A),
        lateError = Color(0xFFCC4444),
        darkError = Color(0xFF660000),
        aboveColor = Color(0xFFCC6600),
        belowColor = Color(0xFFCCAA00),
    ),
)

private val LightThemeConfig = ThemeConfig(
    material = MaterialColors(
        primary = Color(0xFF5AFF54),
        secondary = Color(0xFFFCF758),
        tertiary = Color(0xFFFA5852),
    ),
    custom = CustomColors(
        success = Color(0xFF5AFF54),
        warning = Color(0xFFFCF758),
        error = Color(0xFFFA5852),
        info = Color(0xFF03DAC5),
        progress = Color(0xFF5AFF54),
        progressBackground = Color(0xFFC8E6C9),
        timerRunning = Color(0xFF5AFF54),
        timerPaused = Color(0xFF999999),
        border = Color.White,
        divider = Color(0xFFCCCCCC),
        button = Color(0xFF5AFF54),
        buttonText = Color.White,
        fab = Color(0xFF5AFF54),
        fabText = Color.White,
        lightSuccess = Color(0xFFC8E6C9),
        darkSuccess = Color(0xFF2E8B57),
        lateError = Color(0xFFE86767),
        darkError = Color(0xFF990000),
        aboveColor = Color(0xFFFF9900),
        belowColor = Color(0xFFFFD700),
    ),
)

val LocalThemeConfig: ProvidableCompositionLocal<ThemeConfig> = staticCompositionLocalOf {
    error("No ThemeConfig provided")
}

@Composable
fun rememberThemeConfig(): ThemeConfig = LocalThemeConfig.current

@androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun dynamicThemeConfig(isDark: Boolean): ThemeConfig {
    val context = LocalContext.current
    val dynamicScheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return ThemeConfig(
        material = MaterialColors(
            primary = dynamicScheme.primary,
            secondary = dynamicScheme.secondary,
            tertiary = dynamicScheme.tertiary,
        ),
        custom = if (isDark) DarkThemeConfig.custom else LightThemeConfig.custom,
    )
}

@Composable
fun AppTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themeManager = androidx.compose.runtime.remember { ThemeManager(context) }

    androidx.compose.runtime.CompositionLocalProvider(LocalThemeManager provides themeManager) {
        AppThemeContent(themeManager, dynamicColor, content)
    }
}

@Composable
private fun AppThemeContent(
    themeManager: ThemeManager,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val themeConfig = when (themeManager.currentTheme) {
        AppTheme.E_INK -> EInkThemeConfig
        AppTheme.DARK -> if (dynamicColor && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) {
            dynamicThemeConfig(isDark = true)
        } else {
            DarkThemeConfig
        }
        AppTheme.LIGHT -> if (dynamicColor && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) {
            dynamicThemeConfig(isDark = false)
        } else {
            LightThemeConfig
        }
    }

    val colorScheme = when (themeManager.currentTheme) {
        AppTheme.E_INK -> lightColorScheme(
            primary = themeConfig.material.primary,
            onPrimary = themeConfig.material.onPrimary,
            secondary = themeConfig.material.secondary,
            onSecondary = themeConfig.material.onSecondary,
            tertiary = themeConfig.material.tertiary,
            onTertiary = themeConfig.material.onTertiary,
            background = themeConfig.material.background,
            onBackground = themeConfig.material.onBackground,
            surface = themeConfig.material.surface,
            onSurface = themeConfig.material.onSurface,
            surfaceVariant = themeConfig.material.surfaceVariant,
            error = themeConfig.material.error,
            onError = themeConfig.material.onError,
        )
        AppTheme.DARK -> darkColorScheme(
            primary = themeConfig.material.primary,
            secondary = themeConfig.material.secondary,
            tertiary = themeConfig.material.tertiary,
        )
        AppTheme.LIGHT -> lightColorScheme(
            primary = themeConfig.material.primary,
            secondary = themeConfig.material.secondary,
            tertiary = themeConfig.material.tertiary,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                themeManager.currentTheme != AppTheme.DARK
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalThemeConfig provides themeConfig) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

