package de.notchedmusic.watch.dsp

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Umwandlung zwischen 16-Bit-PCM (little endian) und `Float`, identisch zur Phone-App. */
object Pcm {

  const val BYTES_PER_SAMPLE = 2

  fun shortsToFloats(input: ByteBuffer, out: FloatArray, sampleCount: Int) {
    input.order(ByteOrder.LITTLE_ENDIAN)
    var index = 0
    while (index < sampleCount) {
      out[index] = input.short.toFloat() / 32768f
      index++
    }
  }

  /** Schreibt einen Kanal aus einer Mono-Reihe in einen interleaved Stereo-Puffer. */
  fun writeChannel(
    buffer: FloatArray,
    channelIndex: Int,
    channelCount: Int,
    values: FloatArray,
  ) {
    var frame = 0
    while (frame < values.size) {
      buffer[frame * channelCount + channelIndex] = values[frame]
      frame++
    }
  }

  /** Liest einen Kanal aus einem interleaved Puffer als eigene Reihe. */
  fun channel(buffer: FloatArray, channelIndex: Int, channelCount: Int): FloatArray {
    val frames = buffer.size / channelCount
    return FloatArray(frames) { frame -> buffer[frame * channelCount + channelIndex] }
  }

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
