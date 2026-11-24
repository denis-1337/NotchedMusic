package de.notchedmusic.watch.data

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import de.notchedmusic.watch.playback.PhoneFilterReceiver
import de.notchedmusic.watch.playback.PhoneLink

/**
 * Wann die Data-Layer-Empfänger der Uhr zuhören dürfen – reiner Zustandsautomat ohne
 * Android-Anteile, deshalb im JVM-Test prüfbar.
 *
 * Hintergrund: jeder `WearableListenerService` weckt den App-Prozess, sobald die Gegenstelle Daten
 * schickt – auch nachts, wenn niemand die Uhr anfasst. Deshalb stehen beide Empfänger im Manifest
 * auf `android:enabled="false"` und werden nur eingeschaltet, solange etwas sie braucht:
 *
 * * eine sichtbare Activity (App ist offen),
 * * oder eingehende Daten (dann mit [NACHLAUF_MS] Nachlauf, damit ein laufender Upload in Ruhe
 *   fertig werden kann).
 *
 * Nach einem Neustart der Uhr oder des Prozesses ist der Empfang aus, weil [zwingeAus] beim
 * Prozessstart greift.
 */
class Empfangsfenster(
  private val jetztMs: () -> Long = { System.currentTimeMillis() },
  private val nachlaufMs: Long = NACHLAUF_MS,
) {

  /** Wie viele Activities gerade sichtbar sind. */
  private var sichtbar = 0

  /** Ab dieser Zeit (absolute Millisekunden) darf geprüft werden; `null` = keine Frist offen. */
  private var fristMs: Long? = null

  /** Momentaner Zustand der Komponenten: empfangen die Empfänger? */
  var empfangAn: Boolean = false
    private set

  /**
   * Eine Activity ist sichtbar geworden.
   *
   * Liefert `true`, wenn die Empfänger dabei **eingeschaltet** werden müssen.
   */
  fun aktivitaetSichtbar(): Boolean {
    sichtbar++
    fristMs = null
    return setzeEmpfang(an = true)
  }

  /**
   * Eine Activity ist nicht mehr sichtbar.
   *
   * Liefert die absolute Prüfzeit, wenn die letzte Activity weg ist – sonst `null`.
   */
  fun aktivitaetVersteckt(): Long? {
    if (sichtbar > 0) sichtbar--
    if (sichtbar > 0) return null
    return setzeFrist()
  }

  /**
   * Daten sind von der Gegenstelle angekommen (Manifest, Upload, Commit, Preset).
   *
   * Hält den Empfang an und schiebt die Frist um [nachlauf] hinter den letzten Eingang. Die
   * Rufende Seite wählt die Frist: kurz für normale Pakete, lang für einen laufenden Musik-Spiegel
   * (eine große Datei darf ruhig Minuten brauchen, ohne dass der Lauscher mitten im Transfer
   * zugeht). Liefert die neue Prüfzeit, sonst `null` (solange die App offen ist, braucht der
   * Handler gar nichts zu tun).
   */
  fun datenEingegangen(nachlauf: Long = nachlaufMs): Long? {
    setzeEmpfang(an = true)
    if (sichtbar > 0) {
      fristMs = null
      return null
    }
    return setzeFrist(jetztMs() + nachlauf)
  }

  /**
   * Der Handler ruft das zur gesetzten Frist auf.
   *
   * Liefert `true` nur, wenn die Frist wirklich abgelaufen ist und nichts mehr den Empfang hält –
   * ein überfälliger Rückruf von einer alten Frist tut dann nichts.
   */
  fun pruefeFrist(zeitMs: Long = jetztMs()): Boolean {
    val f = fristMs ?: return false
    if (zeitMs < f) return false
    fristMs = null
    if (!empfangAn || sichtbar > 0) return false
    return setzeEmpfang(an = false)
  }

  /**
   * Beim Prozessstart: Empfang ist aus, erst eine sichtbare Activity oder eingehende Daten schalten
   * ein. Liefert `true`, wenn die Komponenten tatsächlich ausgeschaltet werden mussten.
   */
  fun zwingeAus(): Boolean {
    fristMs = null
    sichtbar = 0
    return setzeEmpfang(an = false)
  }

  private fun setzeFrist(bisMs: Long = jetztMs() + nachlaufMs): Long? {
    if (sichtbar > 0) return null
    if (!empfangAn) return null
    fristMs = bisMs
    return bisMs
  }

  private fun setzeEmpfang(an: Boolean): Boolean {
    if (empfangAn == an) return false
    empfangAn = an
    return true
  }

  companion object {
    /** Ruhezeit ohne offene App und ohne Daten, bis der Empfang wieder zugeht. */
    const val NACHLAUF_MS = 90_000L

    /**
     * Rückfall-Frist, solange ein Musik-Spiegel läuft.
     *
     * Das Phone schickt alle 2 Minuten ein Lebenszeichen (`/music/keepalive`), das diese Frist
     * verlängert. Die Frist ist deutlich größer, damit ein einzelner großer Titel (bis 40 MB ≈
     * 5–6 Minuten über die Bluetooth-Brücke) nie mitten im Transfer zugeschlagen wird. Hört das
     * Phone auf zu senden – fertig, abgebrochen oder tot –, geht der Lauscher nach dieser Frist zu.
     */
    const val NACHLAUF_SYNC_MS = 30L * 60L * 1_000L

    /** Abstand, in dem das Phone während einer Spiegelung Lebenszeichen schickt. */
    const val HALTEZEIT_MS = 2L * 60L * 1_000L
  }
}

/**
 * Schaltstelle zwischen [Empfangsfenster] und den echten Komponenten.
 *
 * Die beiden `WearableListenerService` (`MusicTransferReceiver`, `PhoneFilterReceiver`) sind im
 * Manifest standardmäßig deaktiviert. Diese Klasse schaltet sie ein, solange die App offen ist
 * oder Daten eintreffen, und meldet in denselben Momenten die Fähigkeit der Uhr an bzw. ab. Das
 * Handy sieht die Uhr dadurch nur dann als erreichbar, wenn sie wirklich empfangen kann.
 *
 * Folge, die bewusst so gewählt ist: Ein Preset-Schub oder ein Musik-Sync vom Handy braucht die
 * geöffnete Uhr-App. Auf der Uhr selbst bleiben „Profile anfordern" und der Status unverändert
 * möglich, weil die Uhr die Gegenstelle anspricht.
 */
object SyncEmpfang {

  private val handler = Handler(Looper.getMainLooper())
  private val fenster = Empfangsfenster()

  private val empfaenger =
    listOf(
      MusicTransferReceiver::class.java,
      PhoneFilterReceiver::class.java,
      StatisticsTransferReceiver::class.java,
    )

  private val pruefungsLaeufer =
    Runnable {
      val kontext = anwendungsKontext ?: return@Runnable
      if (fenster.pruefeFrist()) schalteUm(kontext, an = false)
    }

  private var anwendungsKontext: Context? = null

  /** `true`, solange ein Musik-Spiegel ein ausstehendes Manifest hat. */
  private var sitzungLaeuft = false

  /** Zuerst aus [WatchApplication.onCreate] heraus: Ausgangszustand ist „Empfang aus". */
  fun install(anwendung: Application) {
    val kontext = anwendung.applicationContext
    anwendungsKontext = kontext
    anwendung.registerActivityLifecycleCallbacks(
      object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, gespeichert: Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) = empfangSichtbar(activity)

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = empfangVersteckt(activity)

        override fun onActivitySaveInstanceState(activity: Activity, gespeichert: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
      },
    )
    if (fenster.zwingeAus()) schalteUm(kontext, an = false)
    // Ein Prozess kann mitten in einem Spiegel neu gestartet sein – dann gilt weiter die lange Frist.
    sitzungLaeuft = MusicSyncStore.load(kontext) != null
  }

  /** Von den Empfängern aufgerufen, sobald Daten von der Gegenstelle ankommen. */
  fun datenEingegangen(empfaengerKontext: Context) {
    anwendungsKontext = empfaengerKontext.applicationContext
    val pruefung = fenster.datenEingegangen(if (sitzungLaeuft) Empfangsfenster.NACHLAUF_SYNC_MS else Empfangsfenster.NACHLAUF_MS)
    // Der Prozess kann vom System mit deaktivierten Komponenten geweckt worden sein – wieder an.
    schalteUmIfNeeded(empfaengerKontext, an = true)
    planePruefung(pruefung)
  }

  /** Ein Manifest ist angenommen: der Spiegel läuft, der Lauscher bleibt bis zum Commit offen. */
  fun sitzungStart(empfaengerKontext: Context) {
    anwendungsKontext = empfaengerKontext.applicationContext
    sitzungLaeuft = true
    planePruefung(fenster.datenEingegangen(Empfangsfenster.NACHLAUF_SYNC_MS))
  }

  /** Commit oder Abbruch: die lange Frist ist vorbei, die kurze genügt wieder. */
  fun sitzungEnde(empfaengerKontext: Context) {
    anwendungsKontext = empfaengerKontext.applicationContext
    sitzungLaeuft = false
    planePruefung(fenster.datenEingegangen(Empfangsfenster.NACHLAUF_MS))
  }

  /** Activity sichtbar: Empfänger an, offene Frist verwerfen. */
  private fun empfangSichtbar(kontext: Context) {
    anwendungsKontext = kontext.applicationContext
    handler.removeCallbacks(pruefungsLaeufer)
    if (fenster.aktivitaetSichtbar()) schalteUm(kontext, an = true)
    else schalteUmIfNeeded(kontext, an = true)
  }

  /** Activity weg: Frist setzen, nach der der Empfang zugeht, falls nichts dazwischenkommt. */
  private fun empfangVersteckt(kontext: Context) {
    anwendungsKontext = kontext.applicationContext
    planePruefung(fenster.aktivitaetVersteckt())
  }

  private fun planePruefung(pruefungMs: Long?) {
    handler.removeCallbacks(pruefungsLaeufer)
    val f = pruefungMs ?: return
    val verzogerung = (f - System.currentTimeMillis()).coerceAtLeast(0L)
    handler.postDelayed(pruefungsLaeufer, verzogerung)
  }

  /** Der zuletzt tatsächlich geschriebene Komponenten-Zustand – gegen unnötige Schreibvorgänge. */
  private var letzterZustand: Boolean? = null

  private fun schalteUmIfNeeded(kontext: Context, an: Boolean) {
    if (letzterZustand == an) return
    schalteUm(kontext, an)
  }

  private fun schalteUm(kontext: Context, an: Boolean) {
    val paket = kontext.packageManager
    for (klasse in empfaenger) {
      paket.setComponentEnabledSetting(
        ComponentName(kontext, klasse),
        if (an) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
      )
    }
    if (an) PhoneLink.meldeFaehigkeit(kontext) else PhoneLink.entferneFaehigkeit(kontext)
    letzterZustand = an
  }
}
