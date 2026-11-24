package de.notchedmusic.watch.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Pegel-Fangnetz mit Decke −1 dBFS, stereo-verkoppelt.
 *
 * Der Limiter ist absichtlich **kein** Regelkreis mit Vorausschau: Er wartet nicht auf den
 * nächsten Block, sondern reagiert auf die Spitze des aktuellen Frames. Die Absenkung greift
 * sofort (kein Überschwingen), die Erholung läuft exponentiell über 50 ms. Das ist für einen
 * Musikplayer billig, ohne hörbare Pump-Effekte und ohne Latenz – und es fängt genau jene
 * Presets ab, deren überlappende Anheber im Komma-Bereich über 1,0 landen.
 *
 * Stereo-verkoppelt heißt: Die Spitze beider Kanäle bestimmt **einen** Gain für beide Kanäle.
 * Getrennt geregelt würde das Stereobild bei lauten Einsätzen in der Mitte zusammenklappen –
 * das ist der klassische Fehler einfacher Kanallimiter.
 *
 * Reihenfolge in der Kette: Filter → Lautstärke → **Limiter** → 16-Bit-Wandlung. Damit kann
 * hinter dem Limiter nichts mehr übersteuern; die harte Wandlung ist die einzige Grenze.
 *
 * Rein mathematisch, keine Android-Abhängigkeiten – testbar auf dem PC (LimiterTest).
 */
class Limiter {

  private var ceiling = DEFAULT_CEILING
  private var releaseFactor = 0f
  private var gain = 1f
  private var channelCount = 0

  /** Richtet den Limiter auf eine Sample-Rate und Kanalzahl ein. */
  fun configure(sampleRateHz: Double, channelCount: Int) {
    require(sampleRateHz > 0.0) { "Sample-Rate muss größer als 0 Hz sein, war $sampleRateHz Hz" }
    require(channelCount > 0) { "Kanalanzahl muss größer als 0 sein, war $channelCount" }

    this.channelCount = channelCount
    // Exponentielles Abklingen: nach 50 ms ist die Erholung zu ~63 % erfolgt.
    releaseFactor = exp(-1.0 / (sampleRateHz * RELEASE_SECONDS)).toFloat()
    ceiling = (10.0.pow(CEILING_DB / 20.0)).toFloat()
    gain = 1f
  }

  /**
   * Begrenzt [buffer] (interleaved, 16-Bit-Wanderung folgt) an Ort und Stelle.
   *
   * Die Spitze wird pro Frame über alle Kanäle gebildet, danach wird derselbe Gain auf alle
   * Kanäle angewendet. Am Ende steht zusätzlich ein harter Deckel: Rundungsreste im `Float`-Pfad
   * dürfen die Decke nicht überschreiten. Er greift praktisch nur im ersten Sample eines
   * Angriffs, nicht als Dauerzustand.
   */
  fun process(buffer: FloatArray) {
    if (channelCount <= 0 || buffer.isEmpty()) return
    val channels = channelCount
    val frames = buffer.size / channels
    val limit = ceiling
    val release = releaseFactor
    var currentGain = gain

    for (frame in 0 until frames) {
      val base = frame * channels
      var peak = 0f
      for (channel in 0 until channels) {
        val value = abs(buffer[base + channel])
        if (value > peak) peak = value
      }

      val targetGain = if (peak > limit) limit / peak else 1f
      currentGain =
        if (targetGain < currentGain) {
          // Sofortiger Angriff: der aktuelle Frame liegt danach garantiert unter der Decke.
          targetGain
        } else {
          // Exponentielle Erholung zum ungedämpften Zustand.
          targetGain + (currentGain - targetGain) * release
        }

      for (channel in 0 until channels) {
        val index = base + channel
        val limited = buffer[index] * currentGain
        buffer[index] =
          when {
            limited > limit -> limit
            limited < -limit -> -limit
            limited.isFinite() -> limited
            else -> 0f
          }
      }
    }
    gain = currentGain
  }

  /** Aktueller Linear-Gain (1.0 = kein Eingriff) – für Anzeige und Diagnose. */
  fun currentGain(): Float = gain

  /** Setzt den Limiter auf ungedämpft zurück – z. B. bei Flush oder Streamwechsel. */
  fun reset() {
    gain = 1f
  }

  companion object {
    /** Decke in dBFS – 1 dB Luft bleibt für Codec- und Rundungsreste. */
    const val CEILING_DB = -1.0

    /** Erholungszeit: nach 50 ms ist die Dämpfung auf etwa 37 % des Ausgangsbetrags gefallen. */
    const val RELEASE_SECONDS = 0.05

    private val DEFAULT_CEILING = (10.0.pow(CEILING_DB / 20.0)).toFloat()
  }
}
