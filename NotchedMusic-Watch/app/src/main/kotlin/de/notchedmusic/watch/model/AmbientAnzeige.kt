package de.notchedmusic.watch.model

/**
 * Wie sich die App im **gedimmten** Zustand der Uhr (Ambient / Always-On) verhält.
 *
 * * [SCHMAL] – die Spielseite zeigt nur Fortschrittsbogen, Titel und Laufzeit. Standard.
 * * [VOLL] – die Spielseite bleibt so stehen, wie sie ist.
 * * [AUS] – die App nimmt **nicht** am Always-On teil: beim Dimmen übernimmt sofort das
 *   Zifferblatt. Für ein OLED die schonendste Stufe, weil das Zifferblatt selbst parkt.
 */
enum class AmbientAnzeige {
  AUS,
  SCHMAL,
  VOLL,
  ;

  /** Knopf-Reihenfolge: schmal → voll → aus → schmal. */
  fun naechste(): AmbientAnzeige =
    when (this) {
      SCHMAL -> VOLL
      VOLL -> AUS
      AUS -> SCHMAL
    }

  /** Beschriftung des Knopfs auf der Einstellungsseite. */
  fun label(): String =
    "Dimmed: " +
      when (this) {
        AUS -> "off"
        SCHMAL -> "narrow"
        VOLL -> "full"
      }

  companion object {
    /** Der Altbestand kannte nur „gedimmt an/aus": an → schmal, aus → voll. */
    fun ausAltbestand(dimmed: Boolean): AmbientAnzeige = if (dimmed) SCHMAL else VOLL
  }
}
