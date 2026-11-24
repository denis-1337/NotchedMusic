package de.notchedmusic.watch.data

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Ordner, in dem die Musik liegt: der private App-Bereich der Uhr.
 *
 * Warum nicht die gemeinsame Musik-Mediathek? Auf Wear OS gibt es keine Berechtigungs-Dialoge,
 * über die man READ_MEDIA_AUDIO erlauben könnte. Der private Ordner braucht dagegen gar keine
 * Berechtigung.
 *
 * **Wichtig:** `getExternalFilesDir` kann `null` zurückgeben (z.B. auf einem Emulator ohne
 * externes Storage, oder wenn das Volume gerade nicht eingebunden ist). Der frühere Code hat
 * dann auf `filesDir` zurückgefallen, aber das Verzeichnis nie angelegt – die App suchte an
 * einem Ort, den es gar nicht gab, und meldete „Keine Musik", obwohl Musik vorhanden war.
 *
 * Deshalb: erst externe, dann interne Ablage – und den gefundenen Ordner **immer** anlegen.
 * Dateien hineinkopieren geht so:
 *
 *   adb push lied.mp3 /sdcard/Android/data/de.notchedmusic.watch/files/Music/
 *
 * bzw. bei interner Ablage über `adb shell run-as de.notchedmusic.watch ...`.
 */
fun Context.musicFolder(): File {
  val external = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
  val folder = external ?: File(filesDir, Environment.DIRECTORY_MUSIC)
  folder.mkdirs()
  return folder
}
