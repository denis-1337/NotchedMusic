package de.notchedmusic.phone.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Album-Cover mit klarer Rangfolge: eingebettetes Bild → Speicher-Cache → Festplatten-Cache →
 * Online-Fallback → Platzhalter.
 *
 * Netzwerk läuft niemals im UI- oder Audio-Thread, hat ein 5-s-Timeout und höchstens eine Anfrage
 * pro Sekunde. Gesendet werden nur Album/Künstler/Titel, keine Dateipfade und keine Hörhistorie.
 */
class CoverRepository(private val context: Context) {

  private val memoryCache =
    object : LruCache<String, Bitmap>(maxCacheBytes()) {
      override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

  private val diskCacheDir = File(context.cacheDir, "covers").apply { mkdirs() }

  private val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

  @Volatile private var lastNetworkRequestMs = 0L

  /**
   * Lädt das Cover für [track].
   *
   * @return Bitmap oder `null`; der Aufrufer zeigt dann den Platzhalter. Abbrechbar: Bei einem
   * Titelwechsel verwirft Coil-ähnliche Aufrufer das Ergebnis, hier reicht die Ko-Routine.
   */
  suspend fun load(track: LibraryTrack): Bitmap? {
    val key = cacheKey(track)
    memoryCache.get(key)?.let { return it }

    return withContext(Dispatchers.IO) {
      readDisk(key)?.let { bitmap ->
        memoryCache.put(key, bitmap)
        return@withContext bitmap
      }
      embeddedCover(track)?.let { bitmap ->
        memoryCache.put(key, bitmap)
        writeDisk(key, bitmap)
        return@withContext bitmap
      }
      ensureActive()
      if (isNetworkAvailable()) {
        val bytes = fetchOnline(track)
        if (bytes != null) {
          val bitmap = decodeScaled(bytes) ?: return@withContext null
          memoryCache.put(key, bitmap)
          writeDisk(key, bitmap)
          return@withContext bitmap
        }
      }
      null
    }
  }

  private fun cacheKey(track: LibraryTrack): String {
    val raw =
      listOf(track.artist.orEmpty(), track.album.orEmpty(), track.title)
        .joinToString("|")
        .lowercase()
        .trim()
    return raw
  }

  private fun embeddedCover(track: LibraryTrack): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(context, track.uri)
      val bytes = retriever.embeddedPicture ?: return null
      decodeScaled(bytes)
    } catch (_: IllegalArgumentException) {
      null
    } catch (_: RuntimeException) {
      null
    } finally {
      retriever.release()
    }
  }

  private fun decodeScaled(bytes: ByteArray): Bitmap? {
    val options =
      BitmapFactory.Options().apply {
        inJustDecodeBounds = true
      }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null

    var sampleSize = 1
    while ((options.outWidth / sampleSize) > MAX_COVER_PX || (options.outHeight / sampleSize) > MAX_COVER_PX) {
      sampleSize *= 2
    }
    val decodeOptions =
      BitmapFactory.Options().apply {
        inSampleSize = sampleSize
      }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
  }

  private fun isNetworkAvailable(): Boolean {
    val manager = connectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  /**
   * Online-Fallback: MusicBrainz + Cover Art Archive, danach iTunes Search.
   *
   * Beide Dienste brauchen keinen Schlüssel, aber einen aussagekräftigen User-Agent und eine
   * schonende Anfragefolge.
   */
  private suspend fun fetchOnline(track: LibraryTrack): ByteArray? {
    val artist = track.artist?.takeIf { it.isNotBlank() }
    val album = track.album?.takeIf { it.isNotBlank() }
    val title = track.title.takeIf { it.isNotBlank() }
    // Titel allein sind als Suchbegriff wertlos („01 - Ton 220“ liefert beliebige Treffer).
    // Der Online-Fallback greift nur, wenn Künstler oder Album wirklich bekannt sind.
    if (artist == null && album == null) return null

    awaitRateLimit()
    musicBrainzCover(artist, album, title)?.let { return it }
    awaitRateLimit()
    return itunesCover(artist, album, title)
  }

  private suspend fun musicBrainzCover(artist: String?, album: String?, title: String?): ByteArray? {
    val query =
      buildString {
        if (album != null) append("release:\"$album\"").append(' ')
        if (artist != null) append("artist:\"$artist\"").append(' ')
        if (album == null && title != null) append("recording:\"$title\"")
      }.trim()
    if (query.isEmpty()) return null

    val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
    val searchUrl = "https://musicbrainz.org/ws/2/release?query=$encoded&fmt=json&limit=1"
    val searchBody = httpGet(searchUrl, accept = "application/json") ?: return null
    val releaseId =
      try {
        Json.parseToJsonElement(searchBody.decodeToString()).jsonObject["releases"]?.jsonArray?.firstOrNull()
          ?.jsonObject?.get("id")?.jsonPrimitive?.content
      } catch (_: Exception) {
        null
      } ?: return null

    val imageUrl = "https://coverartarchive.org/release/$releaseId/front"
    return httpGet(imageUrl, accept = "image/*", maxBytes = MAX_IMAGE_BYTES)
  }

  private suspend fun itunesCover(artist: String?, album: String?, title: String?): ByteArray? {
    val term =
      listOfNotNull(artist?.takeIf { it.isNotBlank() }, album?.takeIf { it.isNotBlank() } ?: title)
        .joinToString(" ")
        .trim()
    if (term.isEmpty()) return null

    val encoded = URLEncoder.encode(term, "UTF-8")
    val searchUrl = "https://itunes.apple.com/search?term=$encoded&media=music&limit=5"
    val body = httpGet(searchUrl, accept = "application/json") ?: return null
    val artworkUrl =
      try {
        Json.parseToJsonElement(body.decodeToString()).jsonObject["results"]?.jsonArray?.firstOrNull()
          ?.jsonObject?.get("artworkUrl100")?.jsonPrimitive?.content?.replace("100x100", "512x512")
      } catch (_: Exception) {
        null
      } ?: return null
    return httpGet(artworkUrl, accept = "image/*", maxBytes = MAX_IMAGE_BYTES)
  }

  private suspend fun awaitRateLimit() {
    val elapsed = System.currentTimeMillis() - lastNetworkRequestMs
    if (elapsed < MIN_REQUEST_INTERVAL_MS) {
      delay(MIN_REQUEST_INTERVAL_MS - elapsed)
    }
    lastNetworkRequestMs = System.currentTimeMillis()
  }

  private suspend fun httpGet(url: String, accept: String, maxBytes: Int = MAX_JSON_BYTES): ByteArray? =
    withContext(Dispatchers.IO) {
      var connection: HttpURLConnection? = null
      return@withContext try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
          requestMethod = "GET"
          connectTimeout = TIMEOUT_MS
          readTimeout = TIMEOUT_MS
          setRequestProperty("User-Agent", USER_AGENT)
          setRequestProperty("Accept", accept)
          instanceFollowRedirects = true
        }
        if (connection.responseCode !in 200..299) {
          Log.i(TAG, "Cover-Anfrage $url → ${connection.responseCode}")
          null
        } else {
          val bytes = connection.inputStream.use { it.readAtMost(maxBytes) }
          if (bytes == null) Log.i(TAG, "Cover zu groß oder leer: $url")
          bytes
        }
      } catch (error: IOException) {
        Log.i(TAG, "Cover-Anfrage fehlgeschlagen: $url (${error.message})")
        null
      } finally {
        connection?.disconnect()
      }
    }

  private fun java.io.InputStream.readAtMost(maxBytes: Int): ByteArray? {
    val buffer = ByteArray(8 * 1024)
    val output = java.io.ByteArrayOutputStream()
    var total = 0
    while (true) {
      val read = read(buffer)
      if (read <= 0) break
      total += read
      if (total > maxBytes) return null
      output.write(buffer, 0, read)
    }
    return output.toByteArray()
  }

  private fun readDisk(key: String): Bitmap? {
    val file = File(diskCacheDir, md5(key))
    if (!file.exists()) return null
    return try {
      BitmapFactory.decodeFile(file.absolutePath)
    } catch (_: Exception) {
      null
    }
  }

  private fun writeDisk(key: String, bitmap: Bitmap) {
    if (!diskCacheDir.exists()) diskCacheDir.mkdirs()
    try {
      File(diskCacheDir, md5(key)).outputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
      }
    } catch (error: IOException) {
      Log.w(TAG, "Cover-Cache nicht schreibbar", error)
    }
  }

  private fun md5(value: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  private fun maxCacheBytes(): Int {
    val memoryClass = (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
      ?.getMemoryClass() ?: 128
    return (memoryClass / 8) * 1024 * 1024
  }

  private companion object {
    const val TAG = "CoverRepository"
    const val MAX_COVER_PX = 512
    const val MAX_JSON_BYTES = 1 * 1024 * 1024
    const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
    const val TIMEOUT_MS = 5_000
    const val MIN_REQUEST_INTERVAL_MS = 1_000L
    const val USER_AGENT = "NotchedMusicPhone/1.0 (Tinnitus-Retraining-Player)"
  }
}
