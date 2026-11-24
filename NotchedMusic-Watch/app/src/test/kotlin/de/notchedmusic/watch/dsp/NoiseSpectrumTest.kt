package de.notchedmusic.watch.dsp

import de.notchedmusic.watch.model.DefaultPresets
import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.FilterBand
import de.notchedmusic.watch.model.FilterType

import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Hörtest mit Zahlenbeweis: Weißes Rauschen enthält alle Frequenzen gleichzeitig, ein Notch
 * schneidet sein Band heraus. Wer in der Wellenform nur „Rauschen" sieht, sieht hier den
 * Unterschied trotzdem – genau dafür ist das Testrauschen da.
 *
 * Geprüft wird die Energie an der Mittenfrequenz mit dem Goertzel-Verfahren: ein Einpunkt-Diskreter
 * Spektralanteil, billiger als eine volle FFT und hier vollkommen ausreichend.
 */
class NoiseSpectrumTest {

  private val sampleRateHz = 48_000.0

  private val notchFrequenzHz = 8_000.0

  @Test
  fun ein_notch_nimmt_dem_rauschen_sein_band() {
    val rauschen = weissesRauschen(länge = 48_000)

    val ohneFilter = gefiltert(rauschen, testPreset())
    val mitNotch =
      gefiltert(
        rauschen,
        testPreset(
          bands = listOf(FilterBand(type = FilterType.NOTCH, frequencyHz = notchFrequenzHz, q = 8.0, gainDb = -20.0)),
        ),
      )

    val energieOhne = goertzelEnergie(ohneFilter, notchFrequenzHz)
    val energieMit = goertzelEnergie(mitNotch, notchFrequenzHz)

    assertTrue("Energie ohne Filter war $energieOhne", energieOhne > 0.0)
    // 20 dB Absenkung heißt: ein Hundertstel der Energie – großzügig bleibt Zehnerpotenz-Abstand.
    assertTrue(
      "Notch wirkt nicht: $energieMit von $energieOhne",
      energieMit < energieOhne * 0.1,
    )
  }

  @Test
  fun die_kette_ohne_baender_laesst_das_rauschen_in_ruhe() {
    val rauschen = weissesRauschen(länge = 48_000)

    val gefiltert = gefiltert(rauschen, testPreset())

    // Pegel und Form bleiben: kein Limiter-Eingriff, keine Vor-Verstärkung, keine Bänder.
    var abweichung = 0.0
    for (index in rauschen.indices) {
      abweichung += kotlin.math.abs(rauschen[index] - gefiltert[index])
    }
    val mittlereAbweichung = abweichung / rauschen.size

    assertTrue("Kette verändert das Signal ohne Filter: $mittlereAbweichung", mittlereAbweichung < 1e-6)
  }

  /** Weißes Rauschen mit fester Amplitude, deterministisch – derselbe Lauf, dieselbe Welle. */
  private fun weissesRauschen(länge: Int): FloatArray {
    val zufall = Random(7)
    return FloatArray(länge) { (zufall.nextDouble() * 2.0 - 1.0).toFloat() * 0.2f }
  }

  private fun gefiltert(rauschen: FloatArray, preset: EqPreset): FloatArray {
    val kette = EffectChain()
    kette.configure(sampleRateHz = sampleRateHz, channelCount = 1, preset = preset)
    val kopie = rauschen.copyOf()
    kette.process(kopie)
    return kopie
  }

  /** Rechenweg erst ab dem Abklingende, damit Filter-Einschwingen nicht mitzählt. */
  private fun goertzelEnergie(signal: FloatArray, frequencyHz: Double): Double {
    val start = (sampleRateHz * 0.1).roundToLong().toInt()
    val omega = 2.0 * Math.PI * frequencyHz / sampleRateHz
    val coeffizient = 2.0 * cos(omega)
    var vorher = 0.0
    var vorvorher = 0.0
    for (index in start until signal.size) {
      val aktuell = signal[index] + coeffizient * vorher - vorvorher
      vorvorher = vorher
      vorher = aktuell
    }
    return vorher * vorher + vorvorher * vorvorher - coeffizient * vorher * vorvorher
  }
}
