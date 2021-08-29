package eu.lopaciuk.covidcertificateverifier.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

private val DarkColorPalette = darkColors(
        primary = Gray200,
        primaryVariant = Gray700,
        secondary = AlmostBlue
)

private val LightColorPalette = lightColors(
        primary = Gray500,
        primaryVariant = Gray700,
        secondary = AlmostBlue

        /* Other default colors to override
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    */
)

@Composable
fun CovidCertificateVerifierTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable() () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
            colors = colors,
            typography = Typography,
            shapes = Shapes,
            content = content
    )
}

val badColors = lightColors(
    background = BadLight,
    onBackground = BadDark
)

val badColorsDark = darkColors(
    background = BadDark,
    onBackground = BadLight
)

val goodColors = lightColors(
    background = GoodLight,
    onBackground = GoodDark
)

val goodColorsDark = darkColors(
    background = GoodDark,
    onBackground = GoodLight
)

@Composable
fun VerificationResultTheme(darkTheme: Boolean = isSystemInDarkTheme(), good: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colors = when(good) {
            true -> when(darkTheme) {
                true -> goodColorsDark
                false -> goodColors
            }
            false -> when(darkTheme) {
                true -> badColorsDark
                false -> badColors
            }
        },
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}