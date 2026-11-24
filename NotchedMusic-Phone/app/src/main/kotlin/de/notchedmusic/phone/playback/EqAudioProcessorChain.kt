package de.notchedmusic.phone.playback

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.util.UnstableApi

/**
 * Die eine Kette, die der `DefaultAudioSink` durchläuft: nur [EqAudioProcessor].
 *
 * Tempo-/Pitch-Änderung und Stille-Überspringen werden bewusst abgeschaltet: Ersteres würde den
 * Notch mit der Tonhöhe verschieben, Letzteres die Positionsrechnung verfälschen.
 */
@OptIn(UnstableApi::class)
class EqAudioProcessorChain(processor: EqAudioProcessor) : AudioProcessorChain {

  private val processors = arrayOf<AudioProcessor>(processor)

  override fun getAudioProcessors(): Array<AudioProcessor> = processors.clone()

  override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters =
    playbackParameters

  override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = false

  override fun getMediaDuration(playoutDuration: Long): Long = playoutDuration

  override fun getSkippedOutputFrameCount(): Long = 0L
}
