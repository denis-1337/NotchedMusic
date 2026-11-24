package de.notchedmusic.core.dsp

import de.notchedmusic.core.model.EqPreset
import de.notchedmusic.core.model.FilterType
import kotlin.math.abs
import kotlin.math.pow

/**
 * Die komplette Klangkette, sample-genau in dieser Reihenfolge:
 *
 * 1. **Vor-Verstärkung** des Presets (nur wenn der EQ aktiv ist),
 * 2. **bis zu sechs Biquads pro Kanal**,
 * 3. **Hör-Lautstärke** der App (weich geregelt),
 * 4. **Limiter** mit Decke −1 dBFS.
 *
 * Im Bypass entfallen Vor-Verstärkung und Filter. Lautstärke und Limiter bleiben aktiv, damit
 * der Pegel beim Umschalten konstant bleibt und der Ausgang nicht übersteuern kann.
 *
 * Der Arbeitspuffer ist fest; im Sample-Pfad wird nicht allokiert. Neue Filterobjekte entstehen
 * nur, wenn sich Kanal- oder Bandanzahl ändert (also auch beim Bypass-Wechsel).
 */
class EqChain {

  private var filters: Array<Biquad> = emptyArray()
  private var channelCount = 0
  private var bandCount = 0
  private var preGain = 1f

  private var volumeTarget = 1f
  private var volumeCurrent = 1f
  private var volumeStep = 1f

  private val limiter = Limiter()

  /** Größte Spitze vor dem Limiter im zuletzt verarbeiteten Buffer (1.0 = 0 dBFS). */
  var lastPeakLinear: Float = 0f
    private set

  /** Linear-Gain des Limiters am Ende des Buffers (1.0 = kein Eingriff). */
  var lastLimiterGain: Float = 1f
    private set

  /**
   * Richtet die Kette auf ein Preset ein.
   *
   * Das Preset wird vorher auf die Sample-Rate zugeschnitten. [bypass] überspringt Filter und
   * Vor-Verstärkung, nicht aber Lautstärke und Limiter.
   */
  fun configure(
    sampleRateHz: Double,
    channelCount: Int,
    preset: EqPreset,
    bypass: Boolean,
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

  /**
   * App-eigene Hör-Lautstärke in dB (0 = voll, −60 = praktisch stumm).
   *
   * Der Pegel folgt einem Sprung in etwa 20 ms; ein direktes Setzen würde als Knacken hörbar.
   */
  fun setVolumeDb(volumeDb: Float) {
    val clamped = volumeDb.coerceIn(MIN_VOLUME_DB, MAX_VOLUME_DB)
    volumeTarget = if (clamped <= MIN_VOLUME_DB) 0f else dbToFactor(clamped.toDouble())
  }

  /** Filtert [buffer] (interleaved) an Ort und Stelle. */
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

  /** Löscht Filter- und Limiterzustand sowie die Pegelanzeige. */
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

    /** Weiche Lautstärke-Regelung: ein Sprung ist nach etwa 20 ms gehört. */
    const val VOLUME_RAMP_SECONDS = 0.02

    fun dbToFactor(db: Double): Float =
      if (db == 0.0) 1f else 10.0.pow(db / 20.0).toFloat()
  }
}
