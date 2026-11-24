package de.notchedmusic.watch.stats

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import de.notchedmusic.watch.protocol.EqStatsDay
import de.notchedmusic.watch.protocol.StatsProtocol
import de.notchedmusic.watch.protocol.StatsReport

/** Eine noch nicht verdichtete Journal-Zeile. */
@Serializable
internal data class JournalSegment(
  val startMs: Long = 0L,
  val endMs: Long = 0L,
)

/** Snapshot, der nach dem Phone-ACK von den lokalen Tages-Summen abgezogen wird. */
@Serializable
internal data class PendingStatsReport(
  val reportId: String = "",
  val days: List<EqStatsDay> = emptyList(),
  val createdAtMs: Long = 0L,
)

/** Lokaler Statistik-Bestand der Uhr; `pending` hält einen noch nicht bestätigten Report. */
@Serializable
internal data class WatchStatsFile(
  val version: Int = 1,
  val days: Map<String, Long> = emptyMap(),
  val journalCompactedThroughMs: Long = 0L,
  val pending: PendingStatsReport? = null,
)

/**
 * Statistik-Speicher der Uhr.
 *
 * Die Uhr hält ihre Daten bis zum Phone-ACK. Ein Report-Snapshot bleibt bei einer
 * Wiederholungsanfrage unverändert (stabile `reportId`), damit das Phone ihn nicht doppelt
 * zählt. Beim ACK werden nur die Werte des Snapshots abgezogen; neuere Segmente bleiben erhalten.
 */
internal class WatchStatsStore(
  private val directory: File,
  private val zoneId: ZoneId = ZoneId.systemDefault(),
  private val nowMs: () -> Long = { System.currentTimeMillis() },
) {

  private val file = File(directory, FILE_NAME)
  private val journalFile = File(directory, JOURNAL_FILE)
  private val json =
    Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
    }

  @Synchronized
  fun appendSegment(startMs: Long, endMs: Long) {
    if (endMs <= startMs) return
    directory.mkdirs()
    val line = json.encodeToString(JournalSegment.serializer(), JournalSegment(startMs, endMs))
    FileOutputStream(journalFile, true).use { output ->
      output.write((line + "\n").toByteArray(Charsets.UTF_8))
      output.flush()
      output.fd.sync()
    }
  }

  /** Verdichtet die offenen Segmente und liefert einen stabilen Report für einen Sync-Versuch. */
  @Synchronized
  fun buildReport(): StatsReport {
    compactJournal()
    val current = readFile()
    val pending = current.pending
    if (pending != null) {
      return StatsReport(
        version = StatsProtocol.VERSION,
        reportId = pending.reportId,
        generatedAtMs = pending.createdAtMs,
        days = pending.days,
      )
    }
    val snapshot =
      current.days.entries
        .filter { it.value > 0L }
        .sortedBy { it.key }
        .map { (date, activeMs) -> EqStatsDay(date = date, activeMs = activeMs) }
    val reportId = UUID.randomUUID().toString()
    val createdAtMs = nowMs()
    val report = StatsReport(
      version = StatsProtocol.VERSION,
      reportId = reportId,
      generatedAtMs = createdAtMs,
      days = snapshot,
    )
    writeFile(current.copy(pending = PendingStatsReport(reportId, snapshot, createdAtMs)))
    return report
  }

  /** Zieht den bestätigten Snapshot ab; Daten, die während des Syncs neu kamen, bleiben. */
  @Synchronized
  fun acknowledge(reportId: String): Boolean {
    val current = readFile()
    val pending = current.pending ?: return false
    if (pending.reportId != reportId) return false
    val remaining = current.days.toMutableMap()
    for (day in pending.days) {
      val left = (remaining[day.date] ?: 0L) - day.activeMs
      if (left > 0L) remaining[day.date] = left else remaining.remove(day.date)
    }
    writeFile(current.copy(days = remaining, pending = null))
    return true
  }

  private fun compactJournal() {
    val current = readFile()
    val segments = readSegments().filter { it.endMs > current.journalCompactedThroughMs }
    if (segments.isEmpty()) return
    val days = current.days.toMutableMap()
    var maxEnd = current.journalCompactedThroughMs
    for (segment in segments) {
      maxEnd = maxOf(maxEnd, segment.endMs)
      splitByDay(segment.startMs, segment.endMs).forEach { (date, activeMs) ->
        days[date] = (days[date] ?: 0L) + activeMs
      }
    }
    writeFile(
      current.copy(
        days = days,
        journalCompactedThroughMs = maxEnd,
      ),
    )
    if (journalFile.exists()) journalFile.delete()
  }

  private fun readFile(): WatchStatsFile {
    if (!file.isFile) return WatchStatsFile()
    val text =
      try {
        file.readText()
      } catch (_: Exception) {
        return WatchStatsFile()
      }
    return try {
      val decoded = json.decodeFromString(WatchStatsFile.serializer(), text)
      decoded.copy(
        days = decoded.days.mapValues { it.value.coerceAtLeast(0L).coerceAtMost(StatsProtocol.MAX_DAY_MS) },
        journalCompactedThroughMs = decoded.journalCompactedThroughMs.coerceAtLeast(0L),
      )
    } catch (_: SerializationException) {
      WatchStatsFile()
    } catch (_: IllegalArgumentException) {
      WatchStatsFile()
    }
  }

  private fun readSegments(): List<JournalSegment> {
    if (!journalFile.isFile) return emptyList()
    val lines =
      try {
        journalFile.readLines()
      } catch (_: Exception) {
        return emptyList()
      }
    return lines.mapNotNull { line ->
      if (line.isBlank()) return@mapNotNull null
      try {
        json.decodeFromString(JournalSegment.serializer(), line)
      } catch (_: SerializationException) {
        null
      } catch (_: IllegalArgumentException) {
        null
      }
    }
  }

  private fun splitByDay(startMs: Long, endMs: Long): Map<String, Long> {
    if (endMs <= startMs) return emptyMap()
    val result = mutableMapOf<String, Long>()
    var cursor = startMs
    while (cursor < endMs) {
      val date = Instant.ofEpochMilli(cursor).atZone(zoneId).toLocalDate()
      val nextDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
      val sliceEnd = minOf(endMs, nextDay)
      result[date.toString()] = (result[date.toString()] ?: 0L) + (sliceEnd - cursor)
      cursor = sliceEnd
    }
    return result
  }

  private fun writeFile(data: WatchStatsFile) {
    directory.mkdirs()
    val temporary = File(directory, "$FILE_NAME.tmp")
    val text = json.encodeToString(WatchStatsFile.serializer(), data)
    try {
      FileOutputStream(temporary).use { output ->
        output.write(text.toByteArray(Charsets.UTF_8))
        output.flush()
        output.fd.sync()
      }
      try {
        Files.move(
          temporary.toPath(),
          file.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE,
        )
      } catch (_: Exception) {
        temporary.copyTo(file, overwrite = true)
        temporary.delete()
      }
    } catch (error: Exception) {
      temporary.delete()
      throw error
    }
  }

  private companion object {
    const val FILE_NAME = "watch_stats.json"
    const val JOURNAL_FILE = "watch_stats_journal.jsonl"
  }
}
