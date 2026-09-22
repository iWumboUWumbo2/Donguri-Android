package world.wumbo.donguri.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = DonguriBrown,
    onPrimary = AcornCream,
    primaryContainer = AcornCreamDim,
    onPrimaryContainer = DonguriBrownDark,
    secondary = LeafGreen,
    onSecondary = AcornCream,
    background = AcornCream,
    onBackground = AcornInk,
    surface = AcornCream,
    onSurface = AcornInk,
)

private val DarkColors = darkColorScheme(
    primary = DonguriBrownLight,
    onPrimary = AcornInk,
    primaryContainer = DonguriBrownDark,
    onPrimaryContainer = AcornCreamDim,
    secondary = LeafGreenLight,
    onSecondary = AcornInk,
    background = AcornInk,
    onBackground = AcornCreamDim,
    surface = AcornInkDim,
    onSurface = AcornCreamDim,
)

@Composable
fun DonguriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DonguriTypography,
        content = content,
    )
}
