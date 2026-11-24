package de.notchedmusic.watch.dsp

import de.notchedmusic.watch.model.EqPreset
import kotlin.math.abs
import kotlin.math.pow

/**
 * Komplette Klangkette der Uhr, identisch zur Phone-Kette:
 *
 * 1. Vor-Verstärkung (nur wenn der EQ aktiv ist),
 * 2. bis zu sechs Biquads pro Kanal,
 * 3. Hör-Lautstärke (weich geregelt),
 * 4. Limiter mit Decke −1 dBFS.
 *
 * Im Bypass entfallen Vor-Verstärkung und Filter. Lautstärke und Limiter bleiben aktiv, damit
 * der Pegel beim Umschalten konstant bleibt und der Ausgang nicht übersteuern kann.
 */
class EffectChain {

  private var filters: Array<Biquad> = emptyArray()
  private var channelCount = 0
  private var bandCount = 0
  private var preGain = 1f

  private var volumeTarget = 1f
  private var volumeCurrent = 1f
  private var volumeStep = 1f

  private val limiter = Limiter()

  var lastPeakLinear: Float = 0f
    private set

  var lastLimiterGain: Float = 1f
    private set

  fun configure(
    sampleRateHz: Double,
    channelCount: Int,
    preset: EqPreset,
    bypass: Boolean = false,
  ) {
    require(sampleRateHz > 0.0) { "Sample-Rate muss größer als 0 Hz sein, war $sampleRateHz Hz" }
    require(channelCount > 0) { "Kanalanzahl muss größer als 0 sein, war $channelCount" }

    val usable = preset.validate(sampleRateHz)
    val bands = if (bypass) emptyList() else usable.activeBands()

    this.channelCount = channelCount
    this.preGain = if (bypass) 1f else dbToFactor(usable.preGainDb)
    this.bandCount = bands.size
    this.limiter.configure(sampleRateHz, channelCount)
    this.volumeStep = (1.0 / (sampleRateHz * VOLUME_RAMP_SECONDS)).toFloat()

    if (filters.size != channelCount * bands.size) {
      filters =
        Array(channelCount * bands.size) { position ->
          val band = bands[position % bands.size]
          Biquad().apply {
            configure(sampleRateHz, band.frequencyHz, band.q, band.gainDb, band.type)
          }
        }
      reset()
    } else {
      for (channel in 0 until channelCount) {
        val offset = channel * bands.size
        for (index in bands.indices) {
          val band = bands[index]
          filters[offset + index].retarget(
            sampleRateHz,
            band.frequencyHz,
            band.q,
            band.gainDb,
            band.type,
          )
        }
      }
    }
  }

  fun setVolumeDb(volumeDb: Float) {
    val clamped = volumeDb.coerceIn(MIN_VOLUME_DB, MAX_VOLUME_DB)
    volumeTarget = if (clamped <= MIN_VOLUME_DB) 0f else dbToFactor(clamped.toDouble())
  }

  fun process(buffer: FloatArray) {
    if (buffer.isEmpty() || channelCount <= 0) return

    val channels = channelCount
    val frames = buffer.size / channels
    var volume = volumeCurrent
    val targetVolume = volumeTarget
    val step = volumeStep
    val gain = preGain
    var peak = 0f

    var frame = 0
    while (frame < frames) {
      var channel = 0
      while (channel < channels) {
        val index = frame * channels + channel
        var value = buffer[index] * gain
        val filterOffset = channel * bandCount
        var bandIndex = 0
        while (bandIndex < bandCount) {
          value = filters[filterOffset + bandIndex].process(value)
          bandIndex++
        }
        val scaled = value * volume
        buffer[index] = scaled
        val absolute = abs(scaled)
        if (absolute > peak) peak = absolute
        channel++
      }

      if (volume != targetVolume) {
        val difference = targetVolume - volume
        volume =
          when {
            abs(difference) <= step -> targetVolume
            difference > 0f -> volume + step
            else -> volume - step
          }
      }
      frame++
    }
    volumeCurrent = volume
    lastPeakLinear = peak

    limiter.process(buffer)
    lastLimiterGain = limiter.currentGain()
  }

  fun reset() {
    for (filter in filters) filter.reset()
    limiter.reset()
    volumeCurrent = volumeTarget
    lastPeakLinear = 0f
    lastLimiterGain = 1f
  }

  companion object {
    const val MIN_VOLUME_DB = -60f
    const val MAX_VOLUME_DB = 0f
    const val VOLUME_RAMP_SECONDS = 0.02

    fun dbToFactor(db: Double): Float =
      if (db == 0.0) 1f else 10.0.pow(db / 20.0).toFloat()
  }
}
