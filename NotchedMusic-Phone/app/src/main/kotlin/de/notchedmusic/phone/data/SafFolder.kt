package de.notchedmusic.phone.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Prüft und hält die persistierbare Leseberechtigung für den Musik-Root.
 *
 * Die App fordert **keine** breite Medienberechtigung an: Der Nutzer wählt den Ordner selbst über
 * SAF. Entzieht er die Freigabe, wird die Bibliothek als „nicht verfügbar“ markiert – nicht als
 * leer.
 */
class SafFolder(private val context: Context) {

  fun hasPersistedReadPermission(uri: Uri): Boolean =
    context.contentResolver.persistedUriPermissions.any {
      it.uri == uri && it.isReadPermission
    }

  /** Baut das Intent für den System-Ordner-Picker (vom UI aus gestartet). */
  fun openDocumentTreeIntent(initialUri: Uri? = null): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
      addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
          Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
          Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
      )
      if (initialUri != null) putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
    }

  /**
   * Übernimmt das Ergebnis des Pickers.
   *
   * Wenn der Nutzer den Root wechselt, wird die alte Freigabe freigegeben – sonst sammelten sich
   * unbegrenzt Berechtigungen im System. Die neue bleibt über Neustarts erhalten.
   */
  fun takePersistableReadPermission(newTreeUri: Uri, previousUri: Uri?): Boolean {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    return try {
      context.contentResolver.takePersistableUriPermission(newTreeUri, flags)
      if (previousUri != null && previousUri != newTreeUri && hasPersistedReadPermission(previousUri)) {
        context.contentResolver.releasePersistableUriPermission(previousUri, flags)
      }
      true
    } catch (_: SecurityException) {
      false
    }
  }
}
