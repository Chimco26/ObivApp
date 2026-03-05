package com.example.obivapp2.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = LiquidAccent,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = LiquidBackgroundStart,
    surface = GlassWhite
)

private val LightColorScheme = lightColorScheme(
    primary = LiquidAccent,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = LiquidBackgroundStart,
    surface = GlassWhite
)

@Composable
fun ObivApp2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled for specific Liquid Glass look
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // We also want to support Material 2 since the app uses it
    androidx.compose.material.MaterialTheme(
        colors = if (darkTheme) {
            androidx.compose.material.darkColors(
                primary = LiquidAccent,
                background = LiquidBackgroundStart,
                surface = Color(0x33FFFFFF)
            )
        } else {
            androidx.compose.material.lightColors(
                primary = LiquidAccent,
                background = LiquidBackgroundStart,
                surface = Color(0x33FFFFFF)
            )
        },
        content = {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = Typography,
                content = content
            )
        }
    )
}
