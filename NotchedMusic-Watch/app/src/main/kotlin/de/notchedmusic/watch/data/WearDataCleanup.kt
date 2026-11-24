package de.notchedmusic.watch.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Räumt verwaiste DataItems auf der Uhr auf.
 *
 * Das Phone löscht Items nach erfolgreichem ACK selbst. Bleibt es auf halbem Weg liegen, würde
 * der Speicher der Uhr sonst mit Preset- und Upload-Paketen wachsen. Die Uhr entfernt deshalb
 * alles, was älter als eine Stunde ist.
 */
object WearDataCleanup {

  private const val TAG = "WearDataCleanup"
  private const val TTL_MS = 60L * 60L * 1000L

  fun schedule(context: Context, scope: CoroutineScope) {
    scope.launch {
      withContext(Dispatchers.IO) {
        try {
          val items =
            Tasks.await(
              Wearable.getDataClient(context.applicationContext).getDataItems(),
              30L,
              TimeUnit.SECONDS,
            )
          val threshold = System.currentTimeMillis() - TTL_MS
          for (item in items) {
            val path = item.uri.path ?: continue
            if (CLEANABLE_PATHS.none { path.startsWith(it) }) continue
            val sentAt = DataMapItem.fromDataItem(item).dataMap.getLong("sent_at_ms", 0L)
            if (sentAt in 1..threshold) {
              Wearable.getDataClient(context.applicationContext).deleteDataItems(item.uri)
            }
          }
        } catch (exception: Exception) {
          Log.w(TAG, "Aufräumen fehlgeschlagen", exception)
        }
      }
    }
  }

  private val CLEANABLE_PATHS = listOf("/filter", "/music", "/sync", "/stats")
}
