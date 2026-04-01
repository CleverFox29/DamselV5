package com.example.damselv5.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val D_C_S = darkColorScheme(
    primary = P80,
    secondary = PG80,
    tertiary = PK80
)

private val L_C_S = lightColorScheme(
    primary = P40,
    secondary = PG40,
    tertiary = PK40
)

@Composable
fun DamselV5Theme(
    dT: Boolean = isSystemInDarkTheme(),
    dC: Boolean = true,
    cnt: @Composable () -> Unit
) {
    val cS = when {
        dC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dT) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }

        dT -> D_C_S
        else -> L_C_S
    }

    MaterialTheme(
        colorScheme = cS,
        typography = Typography,
        content = cnt
    )
}