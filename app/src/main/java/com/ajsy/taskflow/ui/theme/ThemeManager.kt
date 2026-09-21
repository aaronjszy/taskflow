package com.ajsy.taskflow.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

class ThemeManager(context: Context) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    var currentTheme: AppTheme by mutableStateOf(getStoredTheme())
        private set

    private fun getStoredTheme(): AppTheme {
        val name = prefs.getString("selected_theme", "LIGHT") ?: "LIGHT"
        return try {
            AppTheme.valueOf(name)
        } catch (e: IllegalArgumentException) {
            AppTheme.LIGHT
        }
    }

    fun setTheme(theme: AppTheme) {
        currentTheme = theme
        prefs.edit().putString("selected_theme", theme.name).apply()
    }

    fun isDarkTheme() = currentTheme == AppTheme.DARK

    fun isEInkTheme() = currentTheme == AppTheme.E_INK
}

val LocalThemeManager: ProvidableCompositionLocal<ThemeManager> = staticCompositionLocalOf {
    error("No ThemeManager provided")
}

@Composable
fun rememberThemeManager(): ThemeManager = LocalThemeManager.current
