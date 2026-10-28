package com.dmeltz.englishpractice.ui

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

val Good = Color(0xFF2E7D32)
val Bad = Color(0xFFC62828)
val Brand = Color(0xFF2F5DA8)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme(primary = Color(0xFF9EB9F0))
        else -> lightColorScheme(primary = Brand)
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** A score from 0 to 1 as a color from red through amber to green. */
fun scoreColor(score: Double): Color = when {
    score >= 0.8 -> Good
    score >= 0.6 -> Color(0xFF9E9D24)
    score >= 0.45 -> Color(0xFFEF6C00)
    else -> Bad
}
