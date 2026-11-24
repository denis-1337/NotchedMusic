package de.notchedmusic.phone.stats

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import de.notchedmusic.core.protocol.StatsProtocol
import de.notchedmusic.core.protocol.StatsReport

/** Eine Zeile des aktiven Journal-Logs; sie wird beim Verdichten auf Tage aufgeteilt. */
@Serializable
internal data class JournalSegment(
  val startMs: Long = 0L,
  val endMs: Long = 0L,
)

/** Verdichteter Bestand plus die Grenze, bis zu der das Journal bereits verarbeitet ist. */
@Serializable
internal data class StatsDaysFile(
  val version: Int = 1,
  val days: Map<String, Long> = emptyMap(),
  val journalCompactedThroughMs: Long = 0L,
  val lastWatchReportId: String? = null,
)

/** Ergebnis eines Verdichtungs-/Sync-Schritts für die Oberfläche. */
data class StatsMergeResult(
  val applied: Boolean,
  val alreadyProcessed: Boolean = false,
  val mergedDays: Int = 0,
  val mergedMs: Long = 0L,
  val error: String? = null,
)

/**
 * Kleiner, atomar geschriebener Statistikbestand.
 *
 * `days.json` enthält verdichtete Tages-Summen. `journal.jsonl` nimmt die noch nicht verdichteten
 * Segmente auf. Das Journal wird nur nach erfolgreichem Schreiben der Tages-Datei geleert; die
 * Grenze `journalCompactedThroughMs` verhindert auch dann doppelte Werte, wenn die Datei nach
 * einem Absturz stehen bleibt.
 */
internal class StatsStore(
  private val directory: File,
  private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

  private val daysFile = File(directory, DAYS_FILE)
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

  @Synchronized
  fun snapshot(): StatsSnapshot {
    val file = readDaysFile()
    return StatsSnapshot(
      days = file.days,
      lastWatchReportId = file.lastWatchReportId,
    )
  }

  /** Liest das Journal und mischt alle noch nicht verarbeiteten Segmente in die Tages-Datei. */
  @Synchronized
  fun compact(): StatsSnapshot {
    val file = readDaysFile()
    val segments = readSegments().filter { it.endMs > file.journalCompactedThroughMs }
    if (segments.isEmpty()) {
      val current = file.days
      return StatsSnapshot(current, file.lastWatchReportId)
    }

    val days = file.days.toMutableMap()
    var maxEnd = file.journalCompactedThroughMs
    for (segment in segments) {
      maxEnd = maxOf(maxEnd, segment.endMs)
      splitByDay(segment.startMs, segment.endMs).forEach { (date, activeMs) ->
        days[date] = (days[date] ?: 0L) + activeMs
      }
    }
    val mergedFile =
      file.copy(
        days = days,
        journalCompactedThroughMs = maxEnd,
      )
    writeDaysFile(mergedFile)
    // Erst nach dem erfolgreichen atomaren Schreiben darf das Journal gekürzt werden. Die Grenze
    // im Tage-Format schützt zusätzlich gegen einen Absturz zwischen Write und Delete.
    if (journalFile.exists()) journalFile.delete()
    return StatsSnapshot(days, mergedFile.lastWatchReportId)
  }

  /** Übernimmt einen validierten Uhr-Report; dieselbe Report-ID wird nur einmal gezählt. */
  @Synchronized
  fun mergeWatchReport(report: StatsReport): StatsMergeResult {
    val file = readDaysFile()
    if (report.reportId == file.lastWatchReportId) {
      return StatsMergeResult(applied = true, alreadyProcessed = true)
    }
    val days = file.days.toMutableMap()
    var mergedDays = 0
    var mergedMs = 0L
    for (day in report.days) {
      if (day.activeMs <= 0L) continue
      days[day.date] = (days[day.date] ?: 0L) + day.activeMs.coerceAtMost(StatsProtocol.MAX_DAY_MS)
      mergedDays++
      mergedMs += day.activeMs
    }
    writeDaysFile(
      file.copy(
        days = days,
        lastWatchReportId = report.reportId,
      ),
    )
    return StatsMergeResult(applied = true, mergedDays = mergedDays, mergedMs = mergedMs)
  }

  private fun readDaysFile(): StatsDaysFile {
    val parsed = readStatsFile()
    if (parsed != null) return parsed
    return StatsDaysFile()
  }

  private fun readStatsFile(): StatsDaysFile? {
    if (!daysFile.isFile) return null
    val text =
      try {
        daysFile.readText()
      } catch (_: Exception) {
        return null
      }
    return try {
      json.decodeFromString(StatsDaysFile.serializer(), text).let { file ->
        val normalizedDays =
          file.days.mapValues { (_, activeMs) -> activeMs.coerceAtLeast(0L).coerceAtMost(StatsProtocol.MAX_DAY_MS) }
        file.copy(days = normalizedDays, journalCompactedThroughMs = file.journalCompactedThroughMs.coerceAtLeast(0L))
      }
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
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

  private fun writeDaysFile(file: StatsDaysFile) {
    directory.mkdirs()
    val temporary = File(directory, "$DAYS_FILE.tmp")
    val text = json.encodeToString(StatsDaysFile.serializer(), file)
    try {
      FileOutputStream(temporary).use { output ->
        output.write(text.toByteArray(Charsets.UTF_8))
        output.flush()
        output.fd.sync()
      }
      try {
        Files.move(
          temporary.toPath(),
          daysFile.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE,
        )
      } catch (_: Exception) {
        temporary.copyTo(daysFile, overwrite = true)
        temporary.delete()
      }
    } catch (error: Exception) {
      temporary.delete()
      throw error
    }
  }

  private companion object {
    const val FILE_VERSION = 1
    const val DAYS_FILE = "days.json"
    const val JOURNAL_FILE = "journal.jsonl"
  }
}

internal data class StatsSnapshot(
  val days: Map<String, Long> = emptyMap(),
  val lastWatchReportId: String? = null,
)
