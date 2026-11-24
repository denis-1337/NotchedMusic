package de.notchedmusic.core.protocol

import java.time.DateTimeException
import java.time.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Eine Tages-Summe der EQ-Hörzeit. `date` ist lokal als `yyyy-MM-dd` gespeichert. */
@Serializable
data class EqStatsDay(
  val date: String = "",
  val activeMs: Long = 0L,
)

/**
 * Statistik-Paket zwischen Uhr und Phone.
 *
 * Die Uhr sendet ihre noch nicht übernommenen Tages-Summen. `reportId` bleibt für einen
 * Wiederholungsversuch stabil, damit das Phone dasselbe Paket nicht doppelt zählt.
 */
@Serializable
data class StatsReport(
  val version: Int = StatsProtocol.VERSION,
  val reportId: String = "",
  val generatedAtMs: Long = 0L,
  val days: List<EqStatsDay> = emptyList(),
)

/** Bestätigung des Phones nach dem erfolgreichen Verdichten und Speichern eines Reports. */
@Serializable
data class StatsAck(
  val protocol: Int = StatsProtocol.VERSION,
  val reportId: String = "",
  val status: String = StatsProtocol.STATUS_REJECTED,
  val appliedAtMs: Long = 0L,
  val error: String? = null,
  val mergedDays: Int? = null,
  val mergedMs: Long? = null,
) {

  val isApplied: Boolean
    get() = status == StatsProtocol.STATUS_APPLIED
}

/**
 * Lesen und Schreiben der Statistik-Pakete.
 *
 * Wie beim Preset-/Musik-Sync gilt: fremde oder kaputte Daten werden nie als Exception
 * weitergereicht. Tageswerte werden auf 0 … 24 h geklemmt, doppelte Tage werden zusammengefasst.
 */
object StatsProtocol {

  const val VERSION = 1

  /** Ein Report enthält höchstens ein paar Jahre Tageswerte; 512 KiB sind dafür großzügig. */
  const val MAX_REPORT_BYTES = 512 * 1024

  const val MAX_ACK_BYTES = 16 * 1024

  /** Mehr Tage als ein realistischer Nutzungshorizont; schützt vor aufgeblähten Reports. */
  const val MAX_DAYS = 10_000

  /** Eine Tages-Summe kann nie mehr als 24 Stunden sein. */
  const val MAX_DAY_MS = 24L * 60L * 60L * 1_000L

  const val STATUS_APPLIED = "APPLIED"
  const val STATUS_REJECTED = "REJECTED"

  /** Feste Data-Layer-Pfade. */
  const val PATH_REQUEST = "/stats/request"
  const val PATH_REPORT = "/stats/report"
  const val PATH_ACK = "/stats/ack"

  const val KEY_REPORT_JSON = "report_json"
  const val KEY_REQUEST_ID = "request_id"
  const val KEY_SENT_AT_MS = "sent_at_ms"

  private val json =
    Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      isLenient = false
    }

  fun encodeReport(report: StatsReport): String {
    val normalized = normalize(report) ?: return ""
    return json.encodeToString(StatsReport.serializer(), normalized)
  }

  /** Liest einen Report; `null` bei ungültigen, zu großen oder widersprüchlichen Daten. */
  fun decodeReport(text: String): StatsReport? {
    if (text.isBlank() || text.toByteArray(Charsets.UTF_8).size > MAX_REPORT_BYTES) return null
    return try {
      val decoded = json.decodeFromString(StatsReport.serializer(), text)
      normalize(decoded)
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  fun encodeAck(ack: StatsAck): String =
    json.encodeToString(StatsAck.serializer(), ack)

  fun decodeAck(text: String): StatsAck? {
    if (text.isBlank() || text.toByteArray(Charsets.UTF_8).size > MAX_ACK_BYTES) return null
    return try {
      json.decodeFromString(StatsAck.serializer(), text)
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  /**
   * Bringt einen Report in die verbindliche Form.
   *
   * Ein leerer Report ist gültig – die Uhr kann „noch keine Daten“ bestätigen, ohne dass das
   * Phone etwas addiert. Ungültige Daten- oder Report-IDs werden dagegen abgelehnt.
   */
  fun normalize(report: StatsReport): StatsReport? {
    if (report.version > VERSION) return null
    if (report.reportId.isBlank()) return null
    if (report.days.size > MAX_DAYS) return null
    val merged = LinkedHashMap<String, Long>()
    for (day in report.days) {
      val date =
        try {
          LocalDate.parse(day.date).toString()
        } catch (_: DateTimeException) {
          return null
        }
      if (day.activeMs < 0L) return null
      val previous = merged[date] ?: 0L
      merged[date] = (previous + day.activeMs).coerceAtMost(MAX_DAY_MS)
    }
    return report.copy(
      version = VERSION,
      days = merged.map { (date, activeMs) -> EqStatsDay(date = date, activeMs = activeMs) },
    )
  }
}
