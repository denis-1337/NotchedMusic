package de.notchedmusic.core.dsp

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Umwandlung zwischen 16-Bit-PCM (little endian) und `Float`.
 *
 * Gerundet, nicht abgeschnitten: Ein Abschneiden erzeugt bei leisen Signalen hörbaren
 * Gleichspannungs-Versatz. Die Umrechnung liest und schreibt direkt im [ByteBuffer], ohne
 * Zwischen-Array.
 */
object Pcm {

  const val BYTES_PER_SAMPLE = 2

  /** Liest [sampleCount] 16-Bit-Werte aus [input] und schreibt sie als Floats in [out]. */
  fun shortsToFloats(input: ByteBuffer, out: FloatArray, sampleCount: Int) {
    input.order(ByteOrder.LITTLE_ENDIAN)
    var index = 0
    while (index < sampleCount) {
      out[index] = input.short.toFloat() / 32768f
      index++
    }
  }

  /** Schreibt [sampleCount] Floats als 16-Bit-Werte in [output]. */
  fun floatsToShorts(input: FloatArray, output: ByteBuffer, sampleCount: Int) {
    output.order(ByteOrder.LITTLE_ENDIAN)
    var index = 0
    while (index < sampleCount) {
      val value = input[index]
      val scaled = (value * 32768f).roundToInt()
      output.putShort(scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
      index++
    }
  }
}
