package de.notchedmusic.watch.dsp

import de.notchedmusic.watch.model.FilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Biquad zweiten Grades nach dem RBJ-Cookbook in transponierter Direktform II.
 *
 * Alle sieben Filtertypen werden exakt wie in der Phone-App gerechnet. [FilterType.OFF] lässt das
 * Signal unverändert; Koeffizientenwechsel werden über [SMOOTHING_SAMPLES] Samples überblendet.
 */
class Biquad(initialType: FilterType = FilterType.PEAKING) {

  private var type = initialType
  private var sampleRateHzValue = 0.0
  private var frequencyHzValue = 1_000.0
  private var qValue = 1.0
  private var gainDbValue = 0.0
  private var bypassed = false

  private var b0 = 1f
  private var b1 = 0f
  private var b2 = 0f
  private var a1 = 0f
  private var a2 = 0f

  private var targetB0 = 1f
  private var targetB1 = 0f
  private var targetB2 = 0f
  private var targetA1 = 0f
  private var targetA2 = 0f
  private var stepB0 = 0f
  private var stepB1 = 0f
  private var stepB2 = 0f
  private var stepA1 = 0f
  private var stepA2 = 0f
  private var rampRemaining = 0

  private var state1 = 0f
  private var state2 = 0f

  fun configure(
    sampleRateHz: Double,
    frequencyHz: Double,
    q: Double,
    gainDb: Double = 0.0,
    type: FilterType? = null,
  ) {
    validate(sampleRateHz, frequencyHz, q)
    this.sampleRateHzValue = sampleRateHz
    this.frequencyHzValue = frequencyHz
    this.qValue = q
    this.gainDbValue = gainDb
    this.type = type ?: this.type
    this.bypassed = this.type == FilterType.OFF
    computeTargetCoefficients()
    snapToTarget()
    reset()
  }

  fun retarget(
    sampleRateHz: Double,
    frequencyHz: Double,
    q: Double,
    gainDb: Double = 0.0,
    type: FilterType? = null,
  ) {
    validate(sampleRateHz, frequencyHz, q)
    this.sampleRateHzValue = sampleRateHz
    this.frequencyHzValue = frequencyHz
    this.qValue = q
    this.gainDbValue = gainDb
    this.type = type ?: this.type
    this.bypassed = this.type == FilterType.OFF
    computeTargetCoefficients()
    startTransition()
  }

  fun process(x: Float): Float {
    if (bypassed) return x
    applyRampStep()
    val y = b0 * x + state1
    state1 = b1 * x - a1 * y + state2
    state2 = b2 * x - a2 * y
    return y
  }

  fun reset() {
    state1 = 0f
    state2 = 0f
  }

  private fun computeTargetCoefficients() {
    if (type == FilterType.OFF) {
      targetB0 = 1f
      targetB1 = 0f
      targetB2 = 0f
      targetA1 = 0f
      targetA2 = 0f
      return
    }

    val omega = 2.0 * PI * frequencyHzValue / sampleRateHzValue
    val cosW0 = cos(omega)
    val sinW0 = sin(omega)
    val alpha = sinW0 / (2.0 * qValue)
    val peakA = 10.0.pow(gainDbValue / 40.0)
    val sqrtA = kotlin.math.sqrt(peakA)
    val twoSqrtAAlpha = 2.0 * sqrtA * alpha

    val n0: Double
    val n1: Double
    val n2: Double
    val d0: Double
    val d1: Double
    val d2: Double

    when (type) {
      FilterType.NOTCH -> {
        n0 = 1.0
        n1 = -2.0 * cosW0
        n2 = 1.0
        d0 = 1.0 + alpha
        d1 = -2.0 * cosW0
        d2 = 1.0 - alpha
      }
      FilterType.PEAKING -> {
        n0 = 1.0 + alpha * peakA
        n1 = -2.0 * cosW0
        n2 = 1.0 - alpha * peakA
        d0 = 1.0 + alpha / peakA
        d1 = -2.0 * cosW0
        d2 = 1.0 - alpha / peakA
      }
      FilterType.LOW_SHELF -> {
        n0 = peakA * ((peakA + 1.0) - (peakA - 1.0) * cosW0 + twoSqrtAAlpha)
        n1 = 2.0 * peakA * ((peakA - 1.0) - (peakA + 1.0) * cosW0)
        n2 = peakA * ((peakA + 1.0) - (peakA - 1.0) * cosW0 - twoSqrtAAlpha)
        d0 = (peakA + 1.0) + (peakA - 1.0) * cosW0 + twoSqrtAAlpha
        d1 = -2.0 * ((peakA - 1.0) + (peakA + 1.0) * cosW0)
        d2 = (peakA + 1.0) + (peakA - 1.0) * cosW0 - twoSqrtAAlpha
      }
      FilterType.HIGH_SHELF -> {
        n0 = peakA * ((peakA + 1.0) + (peakA - 1.0) * cosW0 + twoSqrtAAlpha)
        n1 = -2.0 * peakA * ((peakA - 1.0) + (peakA + 1.0) * cosW0)
        n2 = peakA * ((peakA + 1.0) + (peakA - 1.0) * cosW0 - twoSqrtAAlpha)
        d0 = (peakA + 1.0) - (peakA - 1.0) * cosW0 + twoSqrtAAlpha
        d1 = 2.0 * ((peakA - 1.0) - (peakA + 1.0) * cosW0)
        d2 = (peakA + 1.0) - (peakA - 1.0) * cosW0 - twoSqrtAAlpha
      }
      FilterType.LOW_CUT -> {
        n0 = (1.0 + cosW0) / 2.0
        n1 = -(1.0 + cosW0)
        n2 = (1.0 + cosW0) / 2.0
        d0 = 1.0 + alpha
        d1 = -2.0 * cosW0
        d2 = 1.0 - alpha
      }
      FilterType.HIGH_CUT -> {
        n0 = (1.0 - cosW0) / 2.0
        n1 = 1.0 - cosW0
        n2 = (1.0 - cosW0) / 2.0
        d0 = 1.0 + alpha
        d1 = -2.0 * cosW0
        d2 = 1.0 - alpha
      }
      FilterType.OFF -> {
        n0 = 1.0
        n1 = 0.0
        n2 = 0.0
        d0 = 1.0
        d1 = 0.0
        d2 = 0.0
      }
    }

    targetB0 = (n0 / d0).toFloat()
    targetB1 = (n1 / d0).toFloat()
    targetB2 = (n2 / d0).toFloat()
    targetA1 = (d1 / d0).toFloat()
    targetA2 = (d2 / d0).toFloat()
  }

  private fun startTransition() {
    stepB0 = (targetB0 - b0) / SMOOTHING_SAMPLES
    stepB1 = (targetB1 - b1) / SMOOTHING_SAMPLES
    stepB2 = (targetB2 - b2) / SMOOTHING_SAMPLES
    stepA1 = (targetA1 - a1) / SMOOTHING_SAMPLES
    stepA2 = (targetA2 - a2) / SMOOTHING_SAMPLES
    rampRemaining = SMOOTHING_SAMPLES
  }

  private fun applyRampStep() {
    if (rampRemaining <= 0) return
    if (rampRemaining == 1) {
      snapToTarget()
      rampRemaining = 0
      return
    }
    b0 += stepB0
    b1 += stepB1
    b2 += stepB2
    a1 += stepA1
    a2 += stepA2
    rampRemaining--
  }

  private fun snapToTarget() {
    b0 = targetB0
    b1 = targetB1
    b2 = targetB2
    a1 = targetA1
    a2 = targetA2
    rampRemaining = 0
  }

  private fun validate(sampleRateHz: Double, frequencyHz: Double, q: Double) {
    require(sampleRateHz > 0.0) { "Sample-Rate muss größer als 0 Hz sein, war $sampleRateHz Hz" }
    require(frequencyHz > 0.0) { "Frequenz muss größer als 0 Hz sein, war $frequencyHz Hz" }
    require(frequencyHz < sampleRateHz / 2.0) {
      "Frequenz $frequencyHz Hz liegt über der Nyquist-Grenze (${sampleRateHz / 2.0} Hz)"
    }
    require(q > 0.0) { "Q muss größer als 0 sein, war $q" }
  }

  private companion object {
    const val SMOOTHING_SAMPLES = 128
  }
}
