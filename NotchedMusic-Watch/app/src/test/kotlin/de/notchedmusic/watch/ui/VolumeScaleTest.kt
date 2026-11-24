package de.notchedmusic.watch.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Der Lautstärkeregler muss links leise und rechts laut sein – inklusive Prozentanzeige.
 */
class VolumeScaleTest {

  @Test
  fun `regler links ist stumm und rechts voll`() {
    assertEquals(0f, VolumeScale.fraction(-60f), 0f)
    assertEquals(1f, VolumeScale.fraction(0f), 0f)
    assertEquals(0.5f, VolumeScale.fraction(-30f), 1e-6f)
  }

  @Test
  fun `prozent steigt mit der lautstaerke`() {
    assertEquals(0, VolumeScale.percent(-60f))
    assertEquals(40, VolumeScale.percent(-36f))
    assertEquals(100, VolumeScale.percent(0f))
  }

  @Test
  fun `reglerposition wird auf app-pegel zurueckgerechnet`() {
    assertEquals(-60f, VolumeScale.volumeDb(0f), 0f)
    assertEquals(-30f, VolumeScale.volumeDb(0.5f), 1e-6f)
    assertEquals(0f, VolumeScale.volumeDb(1f), 0f)
  }

  @Test
  fun `werte ausserhalb des bereichs werden geklemmt`() {
    assertEquals(0f, VolumeScale.fraction(-90f), 0f)
    assertEquals(1f, VolumeScale.fraction(20f), 0f)
    assertEquals(-60f, VolumeScale.volumeDb(-1f), 0f)
    assertEquals(0f, VolumeScale.volumeDb(2f), 0f)
  }
}
