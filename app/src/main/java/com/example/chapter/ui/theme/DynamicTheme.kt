package com.example.chapter.ui.theme

import android.graphics.Bitmap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette

@Composable
fun DynamicBookTheme(
    bitmap: Bitmap?,
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val seedColor = remember(bitmap) {
        bitmap?.let {
            val palette = Palette.from(it).generate()
            Color(palette.getVibrantColor(palette.getMutedColor(PrimaryLight.toArgb())))
        } ?: PrimaryLight
    }

    val colorScheme = if (bitmap != null) {
        if (darkTheme) {
            dynamicDarkColorScheme(seedColor)
        } else {
            dynamicLightColorScheme(seedColor)
        }
    } else {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

fun dynamicLightColorScheme(seedColor: Color): ColorScheme {
    return lightColorScheme(
        primary = seedColor,
        onPrimary = Color.White,
        primaryContainer = seedColor.copy(alpha = 0.2f),
        onPrimaryContainer = seedColor,
        secondary = seedColor,
        surface = seedColor.copy(alpha = 0.05f),
        onSurface = seedColor.copy(alpha = 0.9f),
        surfaceVariant = seedColor.copy(alpha = 0.1f),
        onSurfaceVariant = seedColor.copy(alpha = 0.7f)
    )
}

fun dynamicDarkColorScheme(seedColor: Color): ColorScheme {
    return darkColorScheme(
        primary = seedColor,
        onPrimary = Color.Black,
        primaryContainer = seedColor.copy(alpha = 0.3f),
        onPrimaryContainer = seedColor,
        secondary = seedColor,
        surface = Color(0xFF121212), // Very dark
        onSurface = seedColor.copy(alpha = 0.8f),
        surfaceVariant = seedColor.copy(alpha = 0.15f),
        onSurfaceVariant = seedColor.copy(alpha = 0.6f)
    )
}
