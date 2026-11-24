package de.notchedmusic.core.protocol

/**
 * Fristen und Pfade einer Musik-Sitzung – bewusst **keine** feste Datei-Timeout-Zeit.
 *
 * Eine Audiodatei wandert über die Bluetooth-Brücke der Begleit-App; real gemessen rund
 * 0,12–0,16 MB/s. Eine Datei mit der erlaubten Obergrenze von 40 MB braucht damit fünf bis sechs
 * Minuten. Ein Timeout, das daran gemessen wäre, würde gesunde Übertragungen abbrechen – deshalb
 * wird auf die Bestätigung der Uhr gewartet, solange die Sitzung lebt.
 *
 * Die Werte hier sind deshalb **Rückfall-Fristen für Stille**, keine Größen-Timeouts:
 *
 * * Kommt von der Uhr gar nichts mehr (kein ACK, kein Fehler, kein Abbruch), gilt die Sitzung nach
 *   [DATEI_MS] als tot.
 * * Solange die Sitzung sendet, schickt das Phone alle [HALTEZEIT_MS] ein Lebenszeichen auf
 *   [HALTEZEICHEN_PFAD]. Die Uhr hält damit ihren Empfänger offen und schließt ihn von selbst,
 *   wenn das Phone aufhört, abstürzt oder abbricht.
 */
object SyncFristen {

  /** Presets sind klein – hier passt eine kurze Frist. */
  const val PRESET_MS = 60_000L

  /** Rückfall für das Manifest (bis zu einige hundert Einträge). */
  const val MANIFEST_MS = 30L * 60L * 1_000L

  /** Rückfall pro Datei-ACK: deutlich länger als die langsamste denkbare Datei braucht. */
  const val DATEI_MS = 30L * 60L * 1_000L

  /** Rückfall für den Commit (löscht Dateien auf der Uhr). */
  const val COMMIT_MS = 10L * 60L * 1_000L

  /** Abstand der Lebenszeichen während einer laufenden Musik-Sitzung. */
  const val HALTEZEIT_MS = 2L * 60L * 1_000L

  /**
   * Pfad des Lebenszeichens. Er liegt bewusst unter `/music`, weil die Uhr-App in ihrem Manifest
   * genau dieses Präfix als Nachrichtenquelle anmeldet.
   */
  const val HALTEZEICHEN_PFAD = "/music/keepalive"
}
