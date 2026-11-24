package de.notchedmusic.core.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Limiter mit Decke −1 dBFS, stereo-verkoppelt.
 *
 * Die Spitze beider Kanäle bestimmt **einen** Gain für beide Kanäle; getrennt geregelt würde
 * das Stereobild bei lauten Stellen zusammenklappen. Der Angriff ist sofort, die Erholung
 * exponentiell über 50 ms. Es gibt keine Vorausschau: kein Latenzaufbau, keine komplizierte
 * Zustandsmaschine. Der Limiter steht vor der 16-Bit-Wandlung, damit dahinter nichts übersteuern
 * kann.
 */
class Limiter {

  private var ceiling = DEFAULT_CEILING
  private var releaseFactor = 0f
  private var gain = 1f
  private var channelCount = 0

  /** Richtet den Limiter auf Sample-Rate und Kanalzahl ein. */
  fun configure(sampleRateHz: Double, channelCount: Int) {
    require(sampleRateHz > 0.0) { "Sample-Rate muss größer als 0 Hz sein, war $sampleRateHz Hz" }
    require(channelCount > 0) { "Kanalanzahl muss größer als 0 sein, war $channelCount" }
    this.channelCount = channelCount
    releaseFactor = exp(-1.0 / (sampleRateHz * RELEASE_SECONDS)).toFloat()
    ceiling = (10.0.pow(CEILING_DB / 20.0)).toFloat()
    gain = 1f
  }

  /**
   * Begrenzt [buffer] (interleaved) an Ort und Stelle.
   *
   * Der harte Deckel am Ende fängt nur Rundungsreste im ersten Sample eines Angriffs ab.
   */
  fun process(buffer: FloatArray) {
    if (channelCount <= 0 || buffer.isEmpty()) return
    val channels = channelCount
    val frames = buffer.size / channels
    val limit = ceiling
    val release = releaseFactor
    var currentGain = gain

    var frame = 0
    while (frame < frames) {
      val base = frame * channels
      var peak = 0f
      var channel = 0
      while (channel < channels) {
        val value = abs(buffer[base + channel])
        if (value > peak) peak = value
        channel++
      }

      val targetGain = if (peak > limit) limit / peak else 1f
      currentGain =
        if (targetGain < currentGain) targetGain
        else targetGain + (currentGain - targetGain) * release

      channel = 0
      while (channel < channels) {
        val index = base + channel
        val limited = buffer[index] * currentGain
        buffer[index] =
          when {
            limited > limit -> limit
            limited < -limit -> -limit
            limited.isFinite() -> limited
            else -> 0f
          }
        channel++
      }
      frame++
    }
    gain = currentGain
  }

  /** Aktueller Linear-Gain (1.0 = kein Eingriff) – für die Clip-Anzeige. */
  fun currentGain(): Float = gain

  /** Setzt den Limiter auf ungedämpft zurück. */
  fun reset() {
    gain = 1f
  }

  companion object {
    /** Decke in dBFS; 1 dB Luft bleibt für Codec- und Rundungsreste. */
    const val CEILING_DB = -1.0

    /** Nach 50 ms ist die Dämpfung auf etwa 37 % gefallen. */
    const val RELEASE_SECONDS = 0.05

    private val DEFAULT_CEILING = (10.0.pow(CEILING_DB / 20.0)).toFloat()
  }
}
