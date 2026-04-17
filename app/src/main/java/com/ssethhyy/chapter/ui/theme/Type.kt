package com.ssethhyy.chapter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ssethhyy.chapter.R
import com.ssethhyy.chapter.data.repository.SettingsRepository

val FigtreeFontFamily = FontFamily(
    Font(R.font.figtree_normal, FontWeight.Normal),
    Font(R.font.figtree_italic, FontWeight.Normal, FontStyle.Italic)
)

val MontserratFontFamily = FontFamily(
    Font(R.font.montserrat_normal, FontWeight.Normal),
    Font(R.font.montserrat_italic, FontWeight.Normal, FontStyle.Italic)
)

val GoogleSansFontFamily = FontFamily(
    Font(R.font.google_sans_flex, FontWeight.Normal)
)

fun getTypography(appFont: SettingsRepository.AppFont): Typography {
    val fontFamily = when (appFont) {
        SettingsRepository.AppFont.FIGTREE -> FigtreeFontFamily
        SettingsRepository.AppFont.MONTSERRAT -> MontserratFontFamily
        SettingsRepository.AppFont.GOOGLE_SANS -> GoogleSansFontFamily
        SettingsRepository.AppFont.SYSTEM -> FontFamily.Default
    }

    // Montserrat and Figtree are now Bolder
    val defaultWeight = if (appFont == SettingsRepository.AppFont.FIGTREE || appFont == SettingsRepository.AppFont.MONTSERRAT) {
        FontWeight.W900 // Black weight for maximum readability
    } else if (appFont == SettingsRepository.AppFont.GOOGLE_SANS) {
        FontWeight.Bold
    } else {
        FontWeight.Normal
    }

    return Typography(
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = defaultWeight,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = defaultWeight,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = defaultWeight,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Black, // Extra bold for titles
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        displayLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Black),
        displayMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Black),
        displaySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Black)
    )
}

// Default Typography (can be kept or removed if always using getTypography)
val Typography = getTypography(SettingsRepository.AppFont.SYSTEM)

