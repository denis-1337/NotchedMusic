package de.notchedmusic.watch.ui.album

import org.junit.Assert.assertEquals
import org.junit.Test

/** Die Krone muss sich wie ein Rasterregler anfühlen: ein Raster = ein Dezibel. */
class VolumeStepsTest {

  private val step = 40f

  private fun steps() = VolumeSteps(minDb = -60f, maxDb = 0f)

  @Test
  fun ein_volles_raster_ist_genau_ein_dezibel() {
    assertEquals(-11f, steps().advance(volumeDb = -12f, deltaPx = 40f, stepPx = step), 0.001f)
  }

  @Test
  fun zwei_halbe_raster_ergeben_ein_ganzes() {
    val volume = steps()
    assertEquals(-12f, volume.advance(volumeDb = -12f, deltaPx = 20f, stepPx = step), 0.001f)
    assertEquals(-11f, volume.advance(volumeDb = -12f, deltaPx = 20f, stepPx = step), 0.001f)
  }

  @Test
  fun drehung_im_uhrzeigersinn_wird_lauter() {
    assertEquals(-7f, steps().advance(volumeDb = -12f, deltaPx = 5 * 40f, stepPx = step), 0.001f)
  }

  @Test
  fun drehung_gegen_den_uhrzeigersinn_wird_leiser() {
    assertEquals(-17f, steps().advance(volumeDb = -12f, deltaPx = -5 * 40f, stepPx = step), 0.001f)
  }

  @Test
  fun der_obere_anschlag_wird_nicht_ueberschritten() {
    assertEquals(0f, steps().advance(volumeDb = -1f, deltaPx = 10 * 40f, stepPx = step), 0.001f)
  }

  @Test
  fun der_untere_anschlag_wird_nicht_unterschritten() {
    assertEquals(-60f, steps().advance(volumeDb = -50f, deltaPx = -10 * 40f, stepPx = step), 0.001f)
  }

  @Test
  fun am_anschlag_wirkt_die_gegenrichtung_sofort() {
    val volume = steps()
    // Bis zum Anschlag drehen und dort den Rest verwerfen … (kein „Vorrat")
    assertEquals(0f, volume.advance(volumeDb = 0f, deltaPx = 3 * 40f, stepPx = step), 0.001f)
    assertEquals(-1f, volume.advance(volumeDb = 0f, deltaPx = -40f, stepPx = step), 0.001f)
  }

  @Test
  fun ohne_raster_gibt_es_keine_stufen() {
    assertEquals(-12f, steps().advance(volumeDb = -12f, deltaPx = 400f, stepPx = 0f), 0.001f)
  }

  @Test
  fun eine_winzige_drehung_bleibt_erhalten_und_summiert_sich() {
    val volume = steps()
    // 3 × 12 px bleiben unter einem Raster (36 < 40) …
    repeat(3) {
      assertEquals(-12f, volume.advance(volumeDb = -12f, deltaPx = 12f, stepPx = step), 0.001f)
    }
    // … die vierte Drehung überschreitet es (48 ≥ 40).
    assertEquals(-11f, volume.advance(volumeDb = -12f, deltaPx = 12f, stepPx = step), 0.001f)
  }
}
