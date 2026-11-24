package de.notchedmusic.watch.playback

/** Wie oft sich die Oberfläche die Wiedergabestelle holt. */
enum class Anzeigetakt {
  /** App ist sichtbar und hell: 4 Hz für eine flüssige Fortschrittsanzeige. */
  VOLL,

  /** App ist sichtbar, aber der Bildschirm ist gedimmt (Ambient): nur die Minutenanzeige. */
  REDUZIERT,

  /** App ist nicht mehr sichtbar: gar nicht fragen, der Spieler arbeitet ja trotzdem. */
  PAUSIERT,
}

/**
 * Die Entscheidung steckt bewusst hier drin – ohne Android, damit sie im JVM-Test scharf geprüft
 * werden kann.
 *
 * Grund: Der Ticker war bisher immer 4 Hz, auch wenn niemand hinsah. Auf der Uhr kostet jeder
 * Weckruf Zeit und ein Neuzeichnen des OLED.
 */
object TaktPlan {

  const val VOLL_MS = 250L
  const val REDUZIERT_MS = 5_000L

  /** Sichtbarkeit (Activity) und gedimmter Bildschirm ergeben zusammen den Takt. */
  fun takt(sichtbar: Boolean, reduziert: Boolean): Anzeigetakt =
    when {
      !sichtbar -> Anzeigetakt.PAUSIERT
      reduziert -> Anzeigetakt.REDUZIERT
      else -> Anzeigetakt.VOLL
    }

  /** Millisekunden zwischen zwei Abfragen; `null` heißt: gar nicht abfragen. */
  fun intervallMs(takt: Anzeigetakt): Long? =
    when (takt) {
      Anzeigetakt.VOLL -> VOLL_MS
      Anzeigetakt.REDUZIERT -> REDUZIERT_MS
      Anzeigetakt.PAUSIERT -> null
    }
}
