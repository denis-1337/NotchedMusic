package de.notchedmusic.watch.dsp

import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.FilterBand

/** Kurzschreibweise für DSP-Tests: Preset ohne IDs, wie es die Kette ohnehin nur liest. */
fun testPreset(bands: List<FilterBand> = emptyList(), preGainDb: Number = 0.0): EqPreset =
  EqPreset(
    id = "test",
    name = "Test",
    preGainDb = preGainDb.toDouble(),
    bands = bands,
  )
