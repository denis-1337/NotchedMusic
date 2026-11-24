package de.notchedmusic.phone.sync

import android.content.Context
import android.util.Base64
import androidx.core.net.toUri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Zu übertragende Audiodatei; die reale Umsetzung öffnet einen Dateideskriptor. */
data class WearAttachment(val key: String, val uri: String)

/** Schmale Abstraktion über die Wear-Data-Layer-API – so ist der Transport auf der JVM testbar. */
interface WearGateway {

  suspend fun findNode(capability: String): String?

  /** Schreibt ein DataItem und liefert seine URI (der Host ist der Zielknoten). */
  suspend fun putDataItem(
    path: String,
    values: Map<String, Any>,
    attachment: WearAttachment? = null,
  ): String

  suspend fun deleteDataItem(uri: String)

  suspend fun sendMessage(nodeId: String, path: String, data: ByteArray): Boolean

  /** Räumt alte, verwaiste DataItems unterhalb eines Pfads auf. */
  suspend fun deleteStaleDataItems(pathPrefix: String, olderThanMs: Long)
}

/** Reale Umsetzung über Google Play Services. */
class PlayServicesWearGateway(private val context: Context) : WearGateway {

  private val dataClient get() = Wearable.getDataClient(context.applicationContext)
  private val messageClient get() = Wearable.getMessageClient(context.applicationContext)
  private val capabilityClient get() = Wearable.getCapabilityClient(context.applicationContext)

  override suspend fun findNode(capability: String): String? =
    withContext(Dispatchers.IO) {
      // Erst die direkt erreichbaren Knoten (Bluetooth/WLAN), dann alle. Der Fallback ist wichtig:
      // Der Filter kann kurz nach einem Reconnect leer sein, obwohl der Knoten schon sichtbar ist.
      repeat(NODE_LOOKUP_ATTEMPTS) { attempt ->
        val node =
          queryCapability(capability, CapabilityClient.FILTER_REACHABLE)
            ?: queryCapability(capability, CapabilityClient.FILTER_ALL)
        if (node != null) return@withContext node
        if (attempt < NODE_LOOKUP_ATTEMPTS - 1) delay(NODE_RETRY_MS)
      }
      // Letzter Fallback: verbundene Knoten. Die Capability-Liste kann direkt nach einem
      // Reconnect leer sein, obwohl die Data-Layer-Verbindung bereits steht.
      try {
        val nodes =
          Tasks.await(
            Wearable.getNodeClient(context.applicationContext).connectedNodes,
            API_TIMEOUT_SECONDS,
            TimeUnit.SECONDS,
          )
        nodes.firstOrNull { it.isNearby }?.id
      } catch (exception: Exception) {
        Log.w(TAG, "Verbundene Knoten nicht abgefragt", exception)
        null
      }
    }

  private suspend fun queryCapability(capability: String, filter: Int): String? =
    try {
      val result =
        Tasks.await(
          capabilityClient.getCapability(capability, filter),
          API_TIMEOUT_SECONDS,
          TimeUnit.SECONDS,
        )
      result.nodes.firstOrNull()?.id
    } catch (exception: Exception) {
      Log.w(TAG, "Capability $capability (Filter $filter) nicht abgefragt", exception)
      null
    }

  override suspend fun putDataItem(
    path: String,
    values: Map<String, Any>,
    attachment: WearAttachment?,
  ): String =
    withContext(Dispatchers.IO) {
      val dataMap = DataMap()
      for ((key, value) in values) {
        when (value) {
          is String -> dataMap.putString(key, value)
          is Int -> dataMap.putInt(key, value)
          is Long -> dataMap.putLong(key, value)
          is Float -> dataMap.putFloat(key, value)
          is Boolean -> dataMap.putBoolean(key, value)
          else -> error("Nicht unterstützter DataMap-Typ für $key: ${value::class.simpleName}")
        }
      }

      var descriptor: android.os.ParcelFileDescriptor? = null
      var assetCreated = false
      try {
        var asset: Asset? = null
        if (attachment != null) {
          descriptor =
            context.contentResolver.openFileDescriptor(attachment.uri.toUri(), "r")
              ?: throw FileNotFoundException(attachment.uri)
          // `createFromFd` übernimmt den Deskriptor und schließt ihn nach der Übertragung.
          // Bewusst nicht in die DataMap schreiben: Die Watch adressiert das Asset über `audio_digest`.
          asset = Asset.createFromFd(descriptor)
          assetCreated = true
          // Die Watch adressiert das Asset über den Play-Services-Digest (Base64-SHA-1).
          assetDigest(attachment.uri)?.let { dataMap.putString("${attachment.key}_digest", it) }
        }
        val request =
          PutDataRequest.createWithAutoAppendedId(path).apply {
            setData(dataMap.toByteArray())
            // Der Asset-Deskriptor liegt nicht im DataMap-ByteArray und muss separat beigefügt werden.
            if (asset != null && attachment != null) putAsset(attachment.key, asset)
            // Sofort synchronisieren; ohne Urgent wartet die Data Layer auf einen Idle-/Ladefenster.
            setUrgent()
          }
        val item =
          Tasks.await(dataClient.putDataItem(request), API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        item.uri.toString()
      } finally {
        // Nur schließen, wenn der Deskriptor nicht an Play Services übergeben wurde.
        if (descriptor != null && !assetCreated) {
          descriptor.close()
        }
      }
    }

  /** Play Services adressieren Assets über Base64(SHA-1); der Datei-SHA-256 bleibt davon unberührt. */
  private fun assetDigest(uri: String): String? =
    try {
      val digest = MessageDigest.getInstance("SHA-1")
      context.contentResolver.openInputStream(uri.toUri())?.use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
          val read = input.read(buffer)
          if (read < 0) break
          digest.update(buffer, 0, read)
        }
      } ?: return null
      Base64.encodeToString(
        digest.digest(),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
      )
    } catch (exception: Exception) {
      Log.w(TAG, "Asset-Digest für $uri nicht berechnet", exception)
      null
    }

  override suspend fun deleteDataItem(uri: String): Unit =
    withContext(Dispatchers.IO) {
      try {
        Tasks.await(dataClient.deleteDataItems(uri.toUri()), API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      } catch (exception: Exception) {
        Log.w(TAG, "DataItem $uri nicht gelöscht", exception)
      }
    }

  override suspend fun sendMessage(nodeId: String, path: String, data: ByteArray): Boolean =
    withContext(Dispatchers.IO) {
      try {
        Tasks.await(
          messageClient.sendMessage(nodeId, path, data),
          API_TIMEOUT_SECONDS,
          TimeUnit.SECONDS,
        )
        true
      } catch (exception: Exception) {
        Log.w(TAG, "Nachricht $path an $nodeId nicht zugestellt", exception)
        false
      }
    }

  override suspend fun deleteStaleDataItems(pathPrefix: String, olderThanMs: Long): Unit =
    withContext(Dispatchers.IO) {
      try {
        val items =
          Tasks.await(dataClient.getDataItems(), API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val threshold = System.currentTimeMillis() - olderThanMs
        for (item in items) {
          val path = item.uri.path ?: continue
          if (!path.startsWith(pathPrefix)) continue
          val sentAt = DataMapItem.fromDataItem(item).dataMap.getLong("sent_at_ms", 0L)
          if (sentAt in 1..threshold) {
            dataClient.deleteDataItems(item.uri)
          }
        }
      } catch (exception: Exception) {
        Log.w(TAG, "Aufräumen unter $pathPrefix fehlgeschlagen", exception)
      }
    }

  private companion object {
    const val TAG = "WearGateway"
    const val API_TIMEOUT_SECONDS = 30L
    const val NODE_LOOKUP_ATTEMPTS = 3
    const val NODE_RETRY_MS = 1_000L
  }
}
