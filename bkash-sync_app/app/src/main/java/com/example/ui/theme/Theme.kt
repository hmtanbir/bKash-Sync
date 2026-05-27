package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = bKashPink,
    secondary = bKashPinkLight,
    tertiary = Color(0xFF10B981), // Success emerald green
    background = ExpertDarkBackground,
    surface = ExpertDarkSurface,
    surfaceVariant = ExpertDarkSurfaceVariant,
    onBackground = ExpertDarkOnSurface,
    onSurface = ExpertDarkOnSurface,
    onSurfaceVariant = ExpertDarkOnSurfaceVariant
  )

private val LightColorScheme =
  lightColorScheme(
    primary = bKashPink,
    secondary = bKashPink,
    tertiary = Color(0xFF16A34A), // Rich green for active status
    background = ExpertLightBackground,
    surface = ExpertLightSurface,
    surfaceVariant = ExpertLightCard,
    onPrimary = Color.White,
    onBackground = ExpertLightOnSurface,
    onSurface = ExpertLightOnSurface,
    onSurfaceVariant = ExpertLightOnSurfaceVariant
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to ensure precise Professional Polish color rendering
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
