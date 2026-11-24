package de.notchedmusic.watch.dsp

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.util.UnstableApi

/**
 * Die eine Kette, die [androidx.media3.exoplayer.audio.DefaultAudioSink] vor der Ausgabe
 * durchläuft: nur [EffectAudioProcessor].
 *
 * Eine eigene Kette ist nötig, weil der Standard-Chain zusätzlich Tonhöhen-/Tempo- (Sonic) und
 * Stille-Überspring-Processor enthält. Beides wollen wir bewusst nicht:
 *
 * * [applyPlaybackParameters] gibt die Parameter unverändert zurück. Tempo- oder Pitch-Änderung
 *   und die Filterkette würden sich gegenseitig verschieben (der Notch wandert mit der Tonhöhe
 *   mit) – bei einem Tinnitus-Retraining wäre das falsch. Deshalb wird der Parameter hier
 *   unverändert durchgereicht.
 * * [applySkipSilenceEnabled] ergibt immer `false`: Stille zu überspringen würde die
 *   Positionsrechnung verfälschen.
 *
 * Deshalb sind auch [getMediaDuration] und [getSkippedOutputFrameCount] Identität bzw. 0 – die
 * Ausgabe des Filters ist zeitlich exakt das Eingangssignal.
 */
@OptIn(UnstableApi::class)
class EffectAudioProcessorChain(effectProcessor: EffectAudioProcessor) : AudioProcessorChain {

  private val audioProcessors = arrayOf<AudioProcessor>(effectProcessor)

  override fun getAudioProcessors(): Array<AudioProcessor> = audioProcessors.clone()

  override fun applyPlaybackParameters(
    playbackParameters: PlaybackParameters,
  ): PlaybackParameters = playbackParameters

  override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = false

  override fun getMediaDuration(playoutDuration: Long): Long = playoutDuration

  override fun getSkippedOutputFrameCount(): Long = 0L

}
