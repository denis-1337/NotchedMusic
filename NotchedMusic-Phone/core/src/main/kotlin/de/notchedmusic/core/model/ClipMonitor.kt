package de.notchedmusic.core.model

/**
 * Zustand der Pegelanzeige vor dem Limiter.
 *
 * Die Anzeige verstellt nichts; sie erklärt nur, was gerade passiert. [LIMITING] heißt: Der
 * Limiter greift hörbar ein, [CLIPPING]: Das Signal wäre ohne Limiter übersteuert.
 */
enum class ClipLevel {
  OK,
  LIMITING,
  CLIPPING,
}

/**
 * Hysterese für die Clip-Anzeige.
 *
 * Eine Anzeige, die bei jedem Buffer flackert, ist unlesbar. Deshalb bleibt ein hoher Zustand
 * für [holdMs] Millisekunden stehen, auch wenn die Spitze schon wieder darunter liegt. Die
 * Schwellen sind in dBFS angegeben und werden intern in lineare Werte umgerechnet.
 */
class ClipMonitor(
  private val limitingThresholdDb: Double = 0.5,
  private val clippingThresholdDb: Double = 3.0,
  private val overloadPeakDb: Double = 0.0,
  private val holdMs: Long = 400L,
) {

  private var current = ClipLevel.OK
  private var heldUntilMs = 0L

  /**
   * Wertet einen Buffer aus.
   *
   * @param peakLinear größte Spitze vor dem Limiter (1.0 = 0 dBFS)
   * @param limiterGain Linear-Gain des Limiters (1.0 = kein Eingriff)
   * @param nowMs monotone Zeit in Millisekunden
   */
  fun update(peakLinear: Float, limiterGain: Float, nowMs: Long): ClipLevel {
    val reductionDb = -20.0 * kotlin.math.log10(maxOf(1e-9, limiterGain.toDouble()))
    val peakDb = 20.0 * kotlin.math.log10(maxOf(1e-9, peakLinear.toDouble()))

    val measured =
      when {
        peakDb > overloadPeakDb || reductionDb > clippingThresholdDb -> ClipLevel.CLIPPING
        reductionDb > limitingThresholdDb -> ClipLevel.LIMITING
        else -> ClipLevel.OK
      }

    if (measured != ClipLevel.OK) {
      current = maxOf(current, measured)
      heldUntilMs = nowMs + holdMs
      return current
    }

    if (nowMs >= heldUntilMs) current = ClipLevel.OK
    return current
  }

  /** Setzt die Anzeige sofort zurück, z. B. bei einem Titelwechsel oder Stop. */
  fun reset() {
    current = ClipLevel.OK
    heldUntilMs = 0L
  }
}
