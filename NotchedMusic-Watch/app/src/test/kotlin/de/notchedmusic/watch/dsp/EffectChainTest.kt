package de.notchedmusic.watch.dsp

import de.notchedmusic.watch.model.DefaultPresets
import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.FilterBand
import de.notchedmusic.watch.model.FilterType

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Kette ist der Teil, der später im Audio-Pfad der Uhr sitzt – Interleaved, Lautstärke und
 * Limiter inklusive.
 */
class EffectChainTest {

  private val sampleRateHz = 48_000.0

  /** 1 kHz bei Q 4 = 250 Hz breit – ein typischer, breiter Tinnitus-Notch. */
  private val band =
    FilterBand(frequencyHz = 1_000.0, q = 4.0, gainDb = -20.0, type = FilterType.NOTCH)
  private val preset = testPreset(bands = listOf(band))

  @Test
  fun `leeres preset liefert den Eingang unterhalb der decke unveraendert`() {
    val chain = EffectChain().apply { configure(sampleRateHz, 2, testPreset()) }
    val buffer = stereoSine(band.frequencyHz, amplitude = 0.25f)
    val expected = buffer.copyOf()

    chain.process(buffer)

    assertArrayEquals(expected, buffer, 0f)
  }

  @Test
  fun `aktive kette laesst einen ton der bandfrequenz deutlich leiser werden`() {
    val chain = EffectChain().apply { configure(sampleRateHz, 1, preset) }
    val buffer = monoSine(band.frequencyHz)
    val before = energy(buffer.copyOf())

    chain.process(buffer)

    assertTrue("energie vorher $before, nachher ${energy(buffer)}", energy(buffer) < (before / 10))
  }

  @Test
  fun `stummes band wird uebersprungen - ohne neues presets`() {
    val chain = EffectChain().apply { configure(sampleRateHz, 1, preset) }
    val silentPreset = preset.copy(bands = listOf(band.copy(enabled = false)))

    chain.configure(sampleRateHz, 1, silentPreset)
    val buffer = monoSine(band.frequencyHz, amplitude = 0.25f)

    chain.process(buffer)

    assertEquals(1.0, energy(buffer) / energy(monoSine(band.frequencyHz, amplitude = 0.25f)), 0.01)
  }

  @Test
  fun `stereokanaele rechnen gleich - ein kanal ersetzt den Mono-Fall`() {
    val stereoChain = EffectChain().apply { configure(sampleRateHz, 2, preset) }
    val monoChain = EffectChain().apply { configure(sampleRateHz, 1, preset) }
    val signal = monoSine(band.frequencyHz, amplitude = 0.25f)

    val stereo = FloatArray(FRAMES * 2)
    Pcm.writeChannel(stereo, channelIndex = 0, channelCount = 2, values = signal)
    Pcm.writeChannel(stereo, channelIndex = 1, channelCount = 2, values = signal)
    val mono = signal.copyOf()

    stereoChain.process(stereo)
    monoChain.process(mono)

    assertArrayEquals(mono, Pcm.channel(stereo, channelIndex = 0, channelCount = 2), 1e-5f)
    assertArrayEquals(mono, Pcm.channel(stereo, channelIndex = 1, channelCount = 2), 1e-5f)
  }

  @Test
  fun `ein ruhiger Kanal bleibt ruhig - kein Uebersprechen`() {
    val chain = EffectChain().apply { configure(sampleRateHz, 2, preset) }
    val signal = monoSine(band.frequencyHz, amplitude = 0.25f)
    val buffer = FloatArray(FRAMES * 2)
    Pcm.writeChannel(buffer, channelIndex = 0, channelCount = 2, values = signal)

    chain.process(buffer)

    val right = Pcm.channel(buffer, channelIndex = 1, channelCount = 2)
    right.forEach { assertEquals(0f, it, 0f) }
    assertTrue(energy(Pcm.channel(buffer, channelIndex = 0, channelCount = 2)) < energy(signal) / 10)
  }

  @Test
  fun `liefert nie NaN oder Unendlich - und bleibt unter der limiter-decke`() {
    val heavyBands =
      (0 until 6).map { index ->
        FilterBand(
          frequencyHz = 1_000.0 + index * 1_000.0,
          q = 2.0,
          gainDb = 24.0,
          type = FilterType.PEAKING,
        )
      }
    val chain =
      EffectChain().apply {
        configure(
          sampleRateHz,
          2,
          testPreset(bands = heavyBands, preGainDb = EqPreset.MAX_PRE_GAIN_DB),
        )
        setVolumeDb(0f)
      }
    val buffer = FloatArray(FRAMES * 2) { if (it % 7 < 4) 1f else -1f }

    chain.process(buffer)

    val ceiling = 10.0.pow(Limiter.CEILING_DB / 20.0).toFloat()
    buffer.forEach { value ->
      assertTrue("Wert $value ist nicht endlich", value.isFinite())
      assertTrue("Wert $value liegt über der Decke $ceiling", value <= ceiling + 1e-6f)
      assertTrue("Wert $value liegt unter der Decke", value >= -ceiling - 1e-6f)
    }
  }

  @Test
  fun `aendert die Laenge des Buffers nicht - auch bei Reststueck`() {
    val chain = EffectChain().apply { configure(sampleRateHz, 2, preset) }
    val buffer = FloatArray(FRAMES * 2 + 1) { 0.5f }
    val size = buffer.size

    chain.process(buffer)

    assertEquals(size, buffer.size)
    buffer.forEach { assertTrue(it.isFinite()) }
  }

  @Test
  fun `neue Vorgabe wirkt sofort`() {
    val chain = EffectChain().apply { configure(sampleRateHz, 1, DefaultPresets.three().first()) }
    val farAway = monoSine(band.frequencyHz, amplitude = 0.25f)
    val untouched = energy(farAway.copyOf())

    // Der Standard-Notch bei 8 kHz fasst einen 1-kHz-Ton kaum an.
    chain.process(farAway)
    assertEquals(untouched, energy(farAway), untouched * 0.05)

    chain.configure(sampleRateHz, 1, preset)
    val afterSwitch = farAway.copyOf().also { chain.process(it) }

    assertTrue(energy(afterSwitch) < untouched / 10)
  }

  /**
   * Zweimal dieselbe Vorgabe setzen darf die Kette nicht umstellen – deshalb muss bei gleichem
   * Eingang auch gleiches Ausgangssignal herauskommen (von einem bewussten `reset` abgesehen).
   */
  @Test
  fun `unveraenderte Vorgabe nochmal setzen aendert die Filterwirkung nicht`() {
    val chain = EffectChain()
    chain.configure(sampleRateHz, 2, preset)
    val first = stereoSine(band.frequencyHz).also { chain.process(it) }

    chain.reset()
    chain.configure(sampleRateHz, 2, preset)
    val second = stereoSine(band.frequencyHz).also { chain.process(it) }

    assertArrayEquals(first, second, 0f)
  }

  @Test
  fun `reset liefert denselben Startpunkt`() {
    val chain = EffectChain().apply { configure(sampleRateHz, 1, preset) }
    val signal = monoSine(band.frequencyHz)

    val first = signal.copyOf().also { chain.process(it) }
    chain.reset()
    val second = signal.copyOf().also { chain.process(it) }

    assertArrayEquals(first, second, 0f)
  }

  @Test
  fun `hoehere app-lautstaerke macht lauter - ohne die systemstufe anzufassen`() {
    val quietChain = EffectChain().apply {
      configure(sampleRateHz, 1, testPreset())
      setVolumeDb(-20f)
    }
    val loudChain = EffectChain().apply {
      configure(sampleRateHz, 1, testPreset())
      setVolumeDb(0f)
    }
    val signal = monoSine(band.frequencyHz, amplitude = 0.25f)
    val quiet = signal.copyOf().also { quietChain.process(it) }
    val loud = signal.copyOf().also { loudChain.process(it) }

    assertTrue(energy(loud) > energy(quiet) * 10)
  }

  private fun monoSine(frequencyHz: Double, amplitude: Float = 1f): FloatArray =
    FloatArray(FRAMES) { index ->
      (amplitude * sin(2 * PI * frequencyHz * index / sampleRateHz)).toFloat()
    }

  private fun stereoSine(frequencyHz: Double, amplitude: Float = 1f): FloatArray {
    val signal = monoSine(frequencyHz, amplitude)
    return FloatArray(FRAMES * 2).also { buffer ->
      Pcm.writeChannel(buffer, channelIndex = 0, channelCount = 2, values = signal)
      Pcm.writeChannel(buffer, channelIndex = 1, channelCount = 2, values = signal)
    }
  }

  private fun energy(values: FloatArray): Double {
    var sum = 0.0
    values.forEach { sum += it.toDouble() * it.toDouble() }
    return sum
  }

  private companion object {
    /** Genug Samples, damit der Einschwingvorgang gegen die Messung kaum ins Gewicht fällt. */
    const val FRAMES = 8_192
  }
}
