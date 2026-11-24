package de.notchedmusic.phone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Dunkles, OLED-freundliches Schema aus den Mockups: schwarz, kräftig blau, weiße Typografie. */
val Blue = Color(0xFF1E88E5)
val BlueDark = Color(0xFF0D47A1)
val NightBlack = Color(0xFF000000)
val SurfaceDark = Color(0xFF121212)
val SurfaceRaised = Color(0xFF1E1E1E)
val ActiveGreen = Color(0xFF43A047)
val WarnOrange = Color(0xFFFB8C00)
val WarnYellow = Color(0xFFFDD835)
val DangerRed = Color(0xFFE53935)
val MutedGrey = Color(0xFF9E9E9E)

private val DarkColors =
  darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueDark,
    onPrimaryContainer = Color.White,
    secondary = Blue,
    onSecondary = Color.White,
    background = NightBlack,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF616161),
    error = DangerRed,
    onError = Color.White,
  )

private val NotchedTypography =
  Typography(
    displaySmall = Typography().displaySmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 18.sp),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.Bold),
  )

@Composable
fun NotchedMusicTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = DarkColors, typography = NotchedTypography, content = content)
}
