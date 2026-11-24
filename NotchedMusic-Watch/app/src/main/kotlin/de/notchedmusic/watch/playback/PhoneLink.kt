package de.notchedmusic.watch.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable

/**
 * Die Uhr-Seite des Data-Layer-Kanals zur Phone-App.
 *
 * Die Fähigkeit `de.notchedmusic.watch.filter_profiles` gehört der **Uhr** und wird vom Phone
 * abgefragt. Die Gegenrichtung fragt `de.notchedmusic.phone.sync` ab – wer das mit der eigenen
 * Fähigkeit verwechselt, bekommt als „erreichbar“ nur sich selbst und schickt ins Leere.
 */
object PhoneLink {

  /** Fähigkeit der **Uhr**; die Phone-App sucht sie. */
  const val WATCH_CAPABILITY = "de.notchedmusic.watch.filter_profiles"

  /** Fähigkeit des **Phones**; die Uhr fragt sie für Status und Anfragen ab. */
  const val PHONE_CAPABILITY = "de.notchedmusic.phone.sync"

  /** Pfad der Preset-Anforderung: leerer Inhalt, der Pfad ist die Botschaft. */
  const val PATH_REQUEST_PROFILES = "/filter/request"

  /**
   * Meldet die eigene Fähigkeit beim System an.
   *
   * Passiert nur, solange die Uhr wirklich empfangen kann (siehe
   * [de.notchedmusic.watch.data.SyncEmpfang]) – sonst würde das Handy Presets und Musik in einen
   * Empfänger schicken, der gerade zu ist.
   */
  fun meldeFaehigkeit(context: Context) {
    Wearable.getCapabilityClient(context.applicationContext)
      .addLocalCapability(WATCH_CAPABILITY)
      .addOnFailureListener { ausnahme -> Log.w(TAG, "Fähigkeit nicht gemeldet", ausnahme) }
  }

  /** Zieht die Fähigkeit wieder zurück: das Handy sieht die Uhr dann als nicht erreichbar. */
  fun entferneFaehigkeit(context: Context) {
    Wearable.getCapabilityClient(context.applicationContext)
      .removeLocalCapability(WATCH_CAPABILITY)
      .addOnFailureListener { ausnahme -> Log.w(TAG, "Fähigkeit nicht entfernt", ausnahme) }
  }

  /**
   * Bittet das Handy um die aktuellen Presets.
   *
   * Der Rückruf meldet, ob ein erreichbares Handy gefunden und die Nachricht angenommen wurde.
   * Fehler werden nie geworfen – Netzwerkfunk darf den Nutzer nicht aus der App werfen.
   */
  fun fordereProfileAn(context: Context, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
    val appContext = context.applicationContext
    findPhoneNode(appContext, attemptsLeft = 3) { nodeId ->
      if (nodeId == null) {
        onResult(false, "No phone reachable")
      } else {
        sendRequest(appContext, nodeId, onResult)
      }
    }
  }

  private fun sendRequest(context: Context, nodeId: String, onResult: (Boolean, String?) -> Unit) {
    Wearable.getMessageClient(context)
      .sendMessage(nodeId, PATH_REQUEST_PROFILES, ByteArray(0))
      .addOnSuccessListener { onResult(true, null) }
      .addOnFailureListener { ausnahme ->
        Log.w(TAG, "Anfrage nicht zugestellt", ausnahme)
        onResult(false, "Request not delivered")
      }
  }

  /**
   * Sucht den Phone-Knoten mit kurzen Wiederholungen.
   *
   * Direkt nach einem Reconnect kann der Capability-Cache für ein bis zwei Sekunden leer sein;
   * ein einzelner Abruf würde die Anfrage dann grundlos verwerfen.
   */
  private fun findPhoneNode(context: Context, attemptsLeft: Int, onFound: (String?) -> Unit) {
    val capabilityClient = Wearable.getCapabilityClient(context)
    capabilityClient
      .getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
      .addOnSuccessListener { reachable ->
        val node = reachable.nodes.firstOrNull()
        if (node != null) {
          onFound(node.id)
        } else {
          capabilityClient
            .getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_ALL)
            .addOnSuccessListener { all ->
              val fallback = all.nodes.firstOrNull()
              if (fallback != null) {
                onFound(fallback.id)
              } else if (attemptsLeft > 1) {
                Handler(Looper.getMainLooper()).postDelayed(
                  { findPhoneNode(context, attemptsLeft - 1, onFound) },
                  RETRY_DELAY_MS,
                )
              } else {
                connectedPhoneNode(context, attemptsLeft, onFound)
              }
            }
            .addOnFailureListener {
              if (attemptsLeft > 1) {
                Handler(Looper.getMainLooper()).postDelayed(
                  { findPhoneNode(context, attemptsLeft - 1, onFound) },
                  RETRY_DELAY_MS,
                )
              } else {
                connectedPhoneNode(context, attemptsLeft, onFound)
              }
            }
        }
      }
      .addOnFailureListener {
        if (attemptsLeft > 1) {
          Handler(Looper.getMainLooper()).postDelayed(
            { findPhoneNode(context, attemptsLeft - 1, onFound) },
            RETRY_DELAY_MS,
          )
        } else {
          connectedPhoneNode(context, attemptsLeft, onFound)
        }
      }
  }

  /** Letzter Fallback: verbundene Knoten – schützt gegen einen kurzzeitig leeren Capability-Cache. */
  private fun connectedPhoneNode(context: Context, attemptsLeft: Int, onFound: (String?) -> Unit) {
    Wearable.getNodeClient(context)
      .connectedNodes
      .addOnSuccessListener { nodes ->
        val node = nodes.firstOrNull { it.isNearby }
        if (node != null) {
          onFound(node.id)
        } else if (attemptsLeft > 1) {
          Handler(Looper.getMainLooper()).postDelayed(
            { findPhoneNode(context, attemptsLeft - 1, onFound) },
            RETRY_DELAY_MS,
          )
        } else {
          onFound(null)
        }
      }
      .addOnFailureListener {
        if (attemptsLeft > 1) {
          Handler(Looper.getMainLooper()).postDelayed(
            { findPhoneNode(context, attemptsLeft - 1, onFound) },
            RETRY_DELAY_MS,
          )
        } else {
          onFound(null)
        }
      }
  }

  /** Prüft den Verbindungsstatus für die Einstellungsseite. */
  fun pruefeStatus(context: Context, onResult: (Boolean) -> Unit) {
    findPhoneNode(context.applicationContext, attemptsLeft = 2) { nodeId -> onResult(nodeId != null) }
  }

  private const val TAG = "PhoneLink"
  private const val RETRY_DELAY_MS = 1_000L
}
