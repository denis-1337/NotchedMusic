package de.notchedmusic.watch.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Farbschema für das OLED-Display der Uhr.
 *
 * Bei OLED ist ein schwarzer Pixel ein *ausgeschalteter* Pixel – er kostet keinen Strom. Google
 * verlangt für Wear-OS-Oberflächen deshalb: mindestens 85 % der Fläche schwarz, keine großen
 * Farbflächen, große Elemente eher als Kontur statt als Füllung.
 *
 * Das Standard-Schema von Wear Compose hat zwar schon schwarzen Hintergrund, aber hell gefüllte
 * Buttons – eine gefüllte Titelliste füllt damit schnell ein Drittel des runden Schirms. Deshalb
 * sind alle Füllfarben hier nachtdunkel: übrig bleiben helle Schrift auf fast ganz schwarzem Grund.
 *
 * Der Akzent (primary) bleibt hell, damit das gerade aktive Element sofort ins Auge fällt.
 */
private val NachtSchema =
  ColorScheme(
    primary = Color(0xFFA8C7FA),
    primaryDim = Color(0xFF16202E),
    primaryContainer = Color(0xFF12161D),
    onPrimary = Color(0xFF06284D),
    onPrimaryContainer = Color(0xFFE3E6EC),
    secondaryContainer = Color(0xFF14181F),
    onSecondaryContainer = Color(0xFFDDE1E8),
    tertiaryContainer = Color(0xFF14181F),
    onTertiaryContainer = Color(0xFFDDE1E8),
    surfaceContainerLow = Color(0xFF08090C),
    surfaceContainer = Color(0xFF0B0D11),
    surfaceContainerHigh = Color(0xFF101318),
    outline = Color(0xFF9AA0AA),
    outlineVariant = Color(0xFF33363D),
  )

@Composable
fun NotchedMusicTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = NachtSchema, content = content)
}

/** Akzent für kleine Elemente (aktiver Titel, Fortschrittsbogen, Regler). */
val Akzent = Color(0xFFA8C7FA)

/**
 * Schrift der Ambient-Darstellung: dieselbe Helligkeit wie die ausgegrauten Knöpfe.
 *
 * Im gedimmten Zustand zählt jeder helle Pixel – der Akzent wäre dort zu teuer, lesbar soll die
 * Zeile trotzdem bleiben. Deshalb ein Grau zwischen Nacht und Akzent statt eines zweiten Schemas.
 */
val AmbientSchrift = Color(0xFF767C86)

/**
 * Chips in Listen: fast schwarz, helle Schrift.
 *
 * Ein gefuelltes `Button`-Element übernimmt sonst die *primary*-Farbe – auf einem 454x454-Schirm
 * sind drei solche Chips sofort ein Drittel helle Flaeche. Fuer OLED genau das, was man vermeiden
 * will (Vorgabe: mindestens 85 % schwarz). Also wird die Fuelle explizit nachtdunkel gesetzt.
 */
@Composable
fun chipButtonColors(): ButtonColors =
  ButtonDefaults.buttonColors(
    containerColor = Color(0xFF0E1116),
    contentColor = Color(0xFFE6E9EF),
    iconColor = Akzent,
    secondaryContentColor = Color(0xFF9AA0AA),
    disabledContainerColor = Color(0xFF0A0C10),
    disabledContentColor = Color(0xFF5A5F68),
    disabledIconColor = Color(0xFF5A5F68),
  )
