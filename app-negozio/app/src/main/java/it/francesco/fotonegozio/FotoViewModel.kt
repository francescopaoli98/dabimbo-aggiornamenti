package it.francesco.fotonegozio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Una foto nella lista, con il suo stato di elaborazione. */
data class Foto(
    val numero: Int,
    val origine: Uri,
    val inCorso: Boolean = true,
    val miniatura: ImageBitmap? = null,
    val fileAuto: File? = null,          // foto come l'ha sistemata l'app (raddrizzata + verticale)
    val file: File? = null,              // foto finale, con le eventuali rotazioni a mano di Elisa
    val rotazione: Int = 0,              // rotazione automatica totale
    val messaInVerticale: Boolean = false,
    val versoVerticale: Int = 0,         // 90 o 270, solo se messaInVerticale
    val rotazioneManuale: Int = 0,       // 0, 90, 180, 270 aggiunti da Elisa
    val daControllare: Boolean = false,  // l'app non è sicura del verso: Elisa deve guardarla
    val codice: String? = null,
    val dati: DatiCartellino? = null,    // descrizione, prezzo, taglia (pezzo 2)
    val altri: List<DatiCartellino> = emptyList(),   // altri cartellini nella stessa foto
    val metodo: String = "",             // per le prove: come è stato trovato il cartellino
    val secondi: Float = 0f,             // per le prove: tempo di elaborazione
    val errore: String? = null,
    val diario: Diario? = null,          // modalità diagnosi: cosa ha provato a leggere
    val testoManuale: String? = null,
    val etichetteViste: Int = 0,         // cartellini che si vedono nella foto (contati dai prezzi)
    val pubblicata: Boolean = false,     // pezzo 6: già mandata a WhatsApp
    // Pixel a strati, così ognuno si può togliere da solo:
    val pixelManuale: Set<Long> = emptySet(),   // quadretti fatti a mano (sulla foto di base, prima delle rotazioni a mano)
    val latoPixel: Int = 0,                     // lato dei quadretti a mano
    val fileSfondo: File? = null,               // la foto di base con lo sfondo pixelato in automatico (fatta una volta sola)
    val sfondoPixelato: Boolean = false,        // pixel automatico dello sfondo acceso
    val ripristinate: Set<Long> = emptySet(),   // quadretti dove lo sfondo pixelato torna come l'originale (pennello "Originale")
    val rotazioneFile: Int = 0,                 // rotazione a mano già "dentro" il file finale (se diversa: si sta ricomponendo)
    val statoFile: String = "",                 // cosa c'è nel file finale (per non ricomporlo due volte uguale)
)

/** Cosa deve contenere il file finale: se cambia, la foto va ricomposta. */
val Foto.statoVoluto: String get() =
    "${fileAuto?.name}|$rotazioneManuale|$sfondoPixelato|${fileSfondo?.name}|${impronta(pixelManuale)}|${impronta(ripristinate)}|$latoPixel"

/** Impronta di un insieme di quadretti: quanti sono + hash dell'elenco ordinato (due zone diverse non si confondono). */
private fun impronta(celle: Set<Long>) = "${celle.size}:${celle.sorted().hashCode()}"

/** Tutti gli articoli della foto (il principale + gli altri), nell'ordine in cui vengono mostrati. */
val Foto.articoli: List<DatiCartellino> get() = listOfNotNull(dati) + altri

/** Un articolo è "da completare" se manca la descrizione o il prezzo (es. cartellino sfocato). */
val DatiCartellino.daCompletare: Boolean get() = descrizione == null || prezzo == null

private fun fileDizionario(app: Application) = File(app.filesDir, "dizionario.txt")

/**
 * Il dizionario salvato sul telefono; la prima volta, quello di partenza (assets/dizionario.txt).
 * Se l'app aggiornata porta sigle nuove o corrette, le aggiunge senza toccare le modifiche di Elisa.
 */
private fun caricaDizionario(app: Application): List<VoceDizionario> {
    val file = fileDizionario(app)
    val partenza = app.assets.open("dizionario.txt").bufferedReader().readText()
    val baseApplicata = File(app.filesDir, "dizionario_base.txt")   // il dizionario di partenza già portato sul telefono
    if (!file.exists()) {
        file.writeText(partenza)
    } else if (!baseApplicata.exists() || baseApplicata.readText() != partenza) {
        // Telefoni con l'app fino alla 1.9: la base era quella di allora
        val vecchia = if (baseApplicata.exists()) baseApplicata.readText()
        else app.assets.open("dizionario_fino_1_9.txt").bufferedReader().readText()
        val aggiornato = Dizionario.aggiorna(Dizionario.leggi(file.readText()), Dizionario.leggi(vecchia), Dizionario.leggi(partenza))
        file.writeText(Dizionario.scrivi(aggiornato))
    }
    baseApplicata.writeText(partenza)
    return Dizionario.leggi(file.readText()).sortedBy { it.sigla }
}

/** Cose da controllare prima di pubblicare (avvisi arancioni). Vuota = tutto a posto. */
val Foto.avvisi: List<String> get() = buildList {
    if (daControllare) add("il verso della foto")
    if (articoli.isEmpty()) add("il cartellino non è stato letto")
    if (articoli.any { it.daCompletare }) add("un articolo ha descrizione o prezzo mancanti")
    if (etichetteViste > articoli.size) add("ci sono etichette non lette")
}

/** Foto da guardare: avviso arancione o errore (quelle ancora in lavorazione no). */
val Foto.daGuardare: Boolean get() = !inCorso && (errore != null || (!pubblicata && avvisi.isNotEmpty()))

/** Tiene la lista delle foto ed esegue l'elaborazione una alla volta, in sottofondo. */
class FotoViewModel(app: Application) : AndroidViewModel(app) {

    val foto = mutableStateListOf<Foto>()
    /** Quante foto hanno finito l'elaborazione. */
    val elaborate: Int get() = foto.count { !it.inCorso }

    /** Mostra solo le foto con l'avviso arancione. */
    var soloDaControllare by mutableStateOf(false)
    val fotoVisibili: List<Foto> get() = if (soloDaControllare) foto.filter { it.daGuardare } else foto

    private val preferenze = app.getSharedPreferences("preferenze", android.content.Context.MODE_PRIVATE)

    // ---- Impostazioni (restano salvate) ----
    /** Grandezza del testo: 0,9 piccolo · 1 medio · 1,2 grande · 1,4 molto grande. */
    var scalaTesto by mutableStateOf(
        preferenze.getFloat("scala_testo", if (preferenze.getBoolean("scritte_grandi", false)) 1.2f else 1f)
    )
        private set
    /** Le foto già pubblicate si chiudono in una riga piccola (si riaprono toccandole). */
    var comprimiPubblicate by mutableStateOf(preferenze.getBoolean("comprimi_pubblicate", true))
        private set
    /** Pulsantino per tornare in cima alla lista. */
    var tornaSu by mutableStateOf(preferenze.getBoolean("torna_su", false))
        private set
    /** Prezzo in grassetto su WhatsApp (*€ 4,00*). */
    var prezzoGrassetto by mutableStateOf(preferenze.getBoolean("prezzo_grassetto", false))
        private set

    /** Tema: 0 chiaro, 1 scuro (modalità notte), 2 come il telefono. */
    var tema by mutableStateOf(preferenze.getInt("tema", 0))
        private set
    fun cambiaTema(v: Int) { tema = v; TemaApp.modo = v; preferenze.edit().putInt("tema", v).apply() }

    /** Colori: 0 Da bimbo a bimbo, 1 Salvia, 2 Lavanda, 3 Sabbia e mare, 4 Nuvola. */
    var tavolozza by mutableStateOf(preferenze.getInt("tavolozza", 0))
        private set
    fun cambiaTavolozza(v: Int) { tavolozza = v; TemaApp.tavolozza = v; preferenze.edit().putInt("tavolozza", v).apply() }

    /** Il colore scelto per "Il mio colore". */
    var coloreMio by mutableStateOf(preferenze.getLong("colore_mio", 0xFF2E86AB))
        private set
    fun cambiaColoreMio(v: Long) {
        coloreMio = v; TemaApp.coloreMio = v
        cambiaTavolozza(IL_MIO_COLORE)
        preferenze.edit().putLong("colore_mio", v).apply()
    }

    /** Elabora 2 foto alla volta (più veloce; spegnere se il telefono rallenta). */
    var dueAllaVolta by mutableStateOf(preferenze.getBoolean("due_alla_volta", true))
        private set
    /** Avviso sul telefono quando le foto sono pronte (se Elisa è uscita dall'app). */
    var avvisoPronte by mutableStateOf(preferenze.getBoolean("avviso_pronte", true))
        private set
    fun cambiaAvvisoPronte(v: Boolean) { avvisoPronte = v; preferenze.edit().putBoolean("avviso_pronte", v).apply() }
    /** Il permesso per gli avvisi è già stato chiesto una volta (non si richiede di continuo). */
    var permessoChiesto: Boolean
        get() = preferenze.getBoolean("permesso_avvisi_chiesto", false)
        set(v) { preferenze.edit().putBoolean("permesso_avvisi_chiesto", v).apply() }

    /** Zoom con due dita nell'anteprima della lista: resta ingrandita (di partenza), torna normale, o spento. */
    var zoomAnteprima by mutableStateOf(ZoomAnteprima.entries.getOrElse(preferenze.getInt("zoom_anteprima", 0)) { ZoomAnteprima.RESTA })
        private set
    fun cambiaZoomAnteprima(v: ZoomAnteprima) { zoomAnteprima = v; preferenze.edit().putInt("zoom_anteprima", v.ordinal).apply() }

    fun cambiaDueAllaVolta(v: Boolean) { dueAllaVolta = v; preferenze.edit().putBoolean("due_alla_volta", v).apply() }

    fun cambiaScalaTesto(v: Float) { scalaTesto = v; preferenze.edit().putFloat("scala_testo", v).apply() }
    fun cambiaComprimi(v: Boolean) { comprimiPubblicate = v; preferenze.edit().putBoolean("comprimi_pubblicate", v).apply() }
    fun cambiaTornaSu(v: Boolean) { tornaSu = v; preferenze.edit().putBoolean("torna_su", v).apply() }
    fun cambiaGrassetto(v: Boolean) { prezzoGrassetto = v; preferenze.edit().putBoolean("prezzo_grassetto", v).apply() }

    // ---- Aggiornamenti ----
    /** Versione nuova trovata su GitHub (null = nessuna). */
    var novita by mutableStateOf<Novita?>(null)
        private set
    /** Scaricamento in corso: da 0 a 1 (null = fermo). */
    var scaricamento by mutableStateOf<Float?>(null)
        private set
    /** Esito dell'ultimo controllo fatto a mano, per le impostazioni. */
    var esitoControllo by mutableStateOf<String?>(null)
        private set

    /** Controlla se c'è una versione nuova ([aMano] = dal pulsante nelle impostazioni: dice anche "nessuna novità"). */
    fun controllaAggiornamenti(aMano: Boolean = false) {
        viewModelScope.launch {
            if (aMano) esitoControllo = "Controllo…"
            novita = Aggiornamento.controlla(getApplication())
            if (aMano) esitoControllo = novita?.let { "C'è la versione ${it.versionName}!" } ?: "Hai già l'ultima versione 👍"
        }
    }

    private var ultimoControllo = 0L
    /** Controllo "di passaggio" quando si torna nell'app: non più di una volta ogni 10 minuti. */
    fun controllaAggiornamentiOgniTanto() {
        val adesso = System.currentTimeMillis()
        if (adesso - ultimoControllo < 10 * 60_000) return
        ultimoControllo = adesso
        controllaAggiornamenti()
    }

    /** Scarica la versione nuova e apre "Installa" (la prima volta Android chiede il permesso). */
    fun aggiorna() {
        val n = novita ?: return
        val app = getApplication<Application>()
        if (!Aggiornamento.puoInstallare(app)) { Aggiornamento.chiediPermesso(app); return }
        if (scaricamento != null) return
        viewModelScope.launch {
            scaricamento = 0f
            val file = Aggiornamento.scarica(app, n) { p -> viewModelScope.launch { scaricamento = p } }
            scaricamento = null
            if (file != null) Aggiornamento.installa(app, file)
            else android.widget.Toast.makeText(app, "Scaricamento non riuscito: controlla internet e riprova", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** Numero della foto a cui si sta pixelando lo sfondo (per la rotellina). */
    var sfondoInCorso by mutableStateOf<Int?>(null)
        private set

    private val fileLista = File(app.filesDir, "lista.json")

    private val raddrizzatore by lazy { Raddrizzatore(app) }   // creato solo quando serve (prima foto)
    private val secondoRaddrizzatore by lazy { Raddrizzatore(app) }   // per lavorare su 2 foto alla volta
    private val versoPreferito = VersoPreferito(app)
    private val pixelatore by lazy { Pixelatore() }

    private var lavoro: Job? = null
    private val bloccoRotazioni = Mutex()   // un tocco su "Gira" alla volta

    /** Dizionario delle sigle (salvato in un file di testo sul telefono). */
    val dizionario = mutableStateListOf<VoceDizionario>().apply { addAll(caricaDizionario(app)) }

    /** Testo per lo stato di una foto: quello corretto da Elisa, o quello composto dall'app. */
    fun testo(f: Foto): String = f.testoManuale ?: TestoFinale.testo(f.articoli, dizionario, prezzoGrassetto)

    fun cambiaTesto(numero: Int, testo: String?) {
        val i = foto.indexOfFirst { it.numero == numero }
        if (i >= 0) foto[i] = foto[i].copy(testoManuale = testo)
    }

    /** Aggiunge o corregge una sigla ([vecchia] = sigla da sostituire, null se nuova); [nuova] null = elimina. */
    fun salvaSigla(vecchia: String?, nuova: VoceDizionario?) {
        if (vecchia != null) dizionario.removeAll { it.sigla == vecchia }
        if (nuova != null) {
            dizionario.removeAll { it.sigla == nuova.sigla }
            dizionario += nuova
        }
        dizionario.sortBy { it.sigla }
        fileDizionario(getApplication()).writeText(Dizionario.scrivi(dizionario))
    }

    // ---- Pixel e rotazioni: la foto finale si ricompone ogni volta dagli strati ----

    /** Pixel a mano: salva i quadretti (sulla foto di base) e ricompone la foto. */
    fun salvaPixelManuale(numero: Int, celle: Set<Long>, lato: Int, ripristinate: Set<Long>? = null) =
        modifica(numero) { it.copy(pixelManuale = celle, latoPixel = lato, ripristinate = ripristinate ?: it.ripristinate) }

    /** Toglie tutti i pixel fatti a mano (il pixel automatico resta). */
    fun togliPixelManuale(numero: Int) = modifica(numero) { it.copy(pixelManuale = emptySet()) }

    /** Pixel automatico: sfondo a quadrettoni, nitidi l'articolo e il cartellino. */
    fun pixelaSfondo(numero: Int) {
        if (sfondoInCorso != null) return
        sfondoInCorso = numero
        modifica(numero, fine = { sfondoInCorso = null }) { f ->
            val sfondo = f.fileSfondo ?: calcolaSfondo(f)
            if (sfondo == null) {
                android.widget.Toast.makeText(getApplication(), "Pixel automatico non riuscito. Se è la prima volta, il telefono sta scaricando il necessario: riprova tra un minuto. Intanto puoi usare il pixel a mano.", android.widget.Toast.LENGTH_LONG).show()
                null
            } else f.copy(fileSfondo = sfondo, sfondoPixelato = true)
        }
    }

    /** Toglie il pixel automatico (quelli a mano restano). */
    fun togliPixelSfondo(numero: Int) = modifica(numero) { it.copy(sfondoPixelato = false, ripristinate = emptySet()) }

    /** La foto di base con lo sfondo pixelato, salvata (null se non riesce). */
    private suspend fun calcolaSfondo(f: Foto): File? = withContext(Dispatchers.Default) {
        val base = f.fileAuto ?: return@withContext null
        try {
            val foto = BitmapFactory.decodeFile(base.path) ?: return@withContext null
            // I cartellini si cercano su una copia più piccola (più veloce), poi si riportano alla grandezza vera
            val k = minOf(1f, 2048f / maxOf(foto.width, foto.height))
            val piccola = if (k < 1f) Bitmap.createScaledBitmap(foto, (foto.width * k).toInt(), (foto.height * k).toInt(), true) else foto
            val zone = raddrizzatore.zoneCartellini(piccola).map { ZonaNitida(it.x / k, it.y / k, it.raggio / k) }
            val pix = pixelatore.pixela(foto, zone) ?: return@withContext null
            Raddrizzatore.salva(getApplication(), pix.immagine, "foto_${f.numero}_sfondo")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /**
     * Cambia una foto (rotazione, pixel…) e ne ricompone il file finale, un cambiamento alla volta.
     * Se nel frattempo arrivano altri cambiamenti (es. tre tocchi su "Gira"), la foto si ricompone
     * una volta sola, con l'ultimo stato.
     */
    private fun modifica(numero: Int, fine: () -> Unit = {}, cambia: suspend (Foto) -> Foto?) {
        inRicomposizione[numero] = (inRicomposizione[numero] ?: 0) + 1
        viewModelScope.launch {
            try {
                bloccoRotazioni.withLock {
                    val f = foto.firstOrNull { it.numero == numero } ?: return@withLock
                    val nuova = cambia(f) ?: return@withLock
                    aggiorna(numero) { nuova }
                    if (nuova.statoVoluto == nuova.statoFile) return@withLock   // già fatta
                    // Se la foto non si riesce a ricomporre (file rovinato), mi fermo: niente giri a vuoto
                    val composta = componi(nuova) ?: return@withLock
                    // Il file finale vecchio NON si cancella qui: WhatsApp potrebbe starlo ancora leggendo
                    // (la cartella si svuota quando si scelgono foto nuove)
                    aggiorna(numero) { attuale ->
                        // Se intanto è cambiata ancora, tengo l'anteprima "veloce" e ricompongo al giro dopo
                        val uguale = attuale.statoVoluto == composta.statoFile
                        attuale.copy(
                            file = composta.file, statoFile = composta.statoFile, rotazioneFile = composta.rotazioneFile,
                            miniatura = if (uguale) composta.miniatura else attuale.miniatura,
                        )
                    }
                    val ancora = foto.firstOrNull { it.numero == numero }
                    if (ancora != null && ancora.statoVoluto != ancora.statoFile) modifica(numero) { it }
                }
            } finally {
                val n = (inRicomposizione[numero] ?: 1) - 1
                if (n <= 0) inRicomposizione.remove(numero) else inRicomposizione[numero] = n
                fine()
            }
        }
    }

    /** Foto che si stanno ricomponendo (girate, pixelate…): finché non hanno finito non si pubblicano. */
    private val inRicomposizione = mutableStateMapOf<Int, Int>()
    fun pronta(numero: Int) = (inRicomposizione[numero] ?: 0) == 0

    private fun aggiorna(numero: Int, cambia: (Foto) -> Foto) {
        val i = foto.indexOfFirst { it.numero == numero }
        if (i >= 0) foto[i] = cambia(foto[i])
    }

    /**
     * Foto finale = base (o base con lo sfondo pixelato, con i quadretti "Originale" rimessi)
     * + quadretti a mano, girata come vuole Elisa.
     */
    private suspend fun componi(f: Foto): Foto? = withContext(Dispatchers.Default) { try { componiFoto(f) } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null } }

    private fun componiFoto(f: Foto): Foto? {
        val stato = f.statoVoluto
        val sfondo = if (f.sfondoPixelato) f.fileSfondo else null
        val base = sfondo ?: f.fileAuto ?: return null
        if (f.pixelManuale.isEmpty() && f.rotazioneManuale == 0 && (sfondo == null || f.ripristinate.isEmpty())) {
            val b = BitmapFactory.decodeFile(base.path) ?: return null
            return f.copy(file = base, miniatura = miniatura(b).asImageBitmap(), statoFile = stato, rotazioneFile = 0)
        }
        var b = BitmapFactory.decodeFile(base.path, BitmapFactory.Options().apply { inMutable = true }) ?: return null
        val w = b.width; val h = b.height
        val lato = f.latoPixel.takeIf { it > 0 } ?: PixelManuale.lato(w, h)
        if (f.pixelManuale.isNotEmpty() || (sfondo != null && f.ripristinate.isNotEmpty())) {
            val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
            if (sfondo != null && f.ripristinate.isNotEmpty()) {
                // Pennello "Originale": in quei quadretti rimetto la foto vera
                val orig = f.fileAuto?.let { BitmapFactory.decodeFile(it.path) }
                if (orig != null && orig.width == w && orig.height == h) {
                    val po = IntArray(w * h).also { orig.getPixels(it, 0, w, 0, 0, w, h) }
                    PixelManuale.rimetti(px, po, w, h, f.ripristinate, lato)
                }
            }
            if (f.pixelManuale.isNotEmpty()) PixelManuale.applica(px, w, h, f.pixelManuale, lato)
            b = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }
        b = Raddrizzatore.ruotaImmagine(b, f.rotazioneManuale)
        // Nome sempre nuovo, così le anteprime si aggiornano
        val file = Raddrizzatore.salva(getApplication(), b, "foto_${f.numero}_v${System.currentTimeMillis() % 100_000_000}")
        return f.copy(file = file, miniatura = miniatura(b).asImageBitmap(), statoFile = stato, rotazioneFile = f.rotazioneManuale)
    }

    /** Torna alla schermata iniziale: lista vuota. */
    fun svuota() {
        lavoro?.cancel()
        lavoro = null
        foto.clear()
        soloDaControllare = false
        Raddrizzatore.cartella(getApplication()).listFiles()?.forEach { it.delete() }
    }

    /** Pezzo 6: la prossima foto da pubblicare, in ordine (null = tutte pubblicate o ancora in lavorazione). */
    val prossima: Foto? get() = foto.firstOrNull { !it.pubblicata && !it.inCorso && it.file != null && it.errore == null }

    val quantePubblicate: Int get() = foto.count { it.pubblicata }

    fun segnaPubblicata(numero: Int, pubblicata: Boolean = true) {
        val i = foto.indexOfFirst { it.numero == numero }
        if (i < 0) return
        foto[i] = foto[i].copy(pubblicata = pubblicata)
        if (pubblicata) {
            // Nel riepilogo il nome già "tradotto" con il dizionario (Felpa zip con cappuccio rosa…)
            val articoli = foto[i].articoli.map { a -> a.copy(descrizione = a.descrizione?.let { TestoFinale.espandi(it, a.taglia, dizionario) }) }
            // Tengo un anno di storia (basta per i doppioni, e il file resta piccolo)
            val unAnnoFa = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ITALY).format(java.util.Date(System.currentTimeMillis() - 365L * 24 * 3600 * 1000))
            registro = Riepilogo.aggiungi(registro.filter { it.giorno >= unAnnoFa }, oggi(), articoli)
            fileRegistro.writeText(Riepilogo.scrivi(registro))
        }
    }

    // ---- Riepilogo delle pubblicazioni ----
    private val fileRegistro = File(app.filesDir, "pubblicazioni.json")
    /** Tutti gli articoli pubblicati, giorno per giorno (resta salvato). */
    var registro by mutableStateOf(Riepilogo.leggi(fileRegistro.takeIf { it.exists() }?.readText().orEmpty()))
        private set
    private val giornateCalcolate by derivedStateOf { Riepilogo.giornate(registro) }
    private val codiciNoti by derivedStateOf { registro.mapTo(HashSet()) { it.chiave } }
    val giornate: List<Giornata> get() = giornateCalcolate

    /** Gli articoli contati in un giorno. */
    fun articoliDel(giorno: String): List<Pubblicato> = registro.filter { it.giorno == giorno }

    /** Toglie dal conteggio un articolo contato per sbaglio (es. aperto in WhatsApp ma poi non pubblicato). */
    fun togliDalRiepilogo(p: Pubblicato) {
        registro = registro - p
        fileRegistro.writeText(Riepilogo.scrivi(registro))
    }

    /** Codici di questa foto già pubblicati in passato (per non pubblicarli due volte). Vuoto se la foto è già pubblicata ora. */
    fun giaPubblicati(f: Foto): List<String> {
        if (f.pubblicata) return emptyList()
        return f.articoli.mapNotNull { it.codice }.filter { it in codiciNoti }.distinct()
    }
    val oggiPubblicati: Giornata? get() = giornate.firstOrNull { it.giorno == oggi() }
    private fun oggi() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ITALY).format(java.util.Date())

    /** Modalità prove (logo tenuto premuto): mostra le informazioni tecniche e la diagnosi. */
    var prove by mutableStateOf(false)

    /** Modalità diagnosi: l'app si annota ogni lettura, per mandarla a chi sistema l'app. */
    var diagnosi by mutableStateOf(false)
    /** Messaggio breve da mostrare (es. "Diagnosi salvata in Galleria"). */
    var messaggio by mutableStateOf<String?>(null)

    /** Sostituisce la lista con le nuove foto e le elabora in ordine. */
    fun carica(uris: List<Uri>) {
        if (uris.isEmpty()) return
        lavoro?.cancel()
        foto.clear()
        soloDaControllare = false
        // Le foto sistemate della lista vecchia non servono più
        Raddrizzatore.cartella(getApplication()).listFiles()?.forEach { it.delete() }
        uris.forEachIndexed { i, uri -> foto.add(Foto(numero = i + 1, origine = uri)) }
        elaboraInCoda()
    }

    /** Elabora, una alla volta e in ordine, le foto ancora "in lavorazione". */
    /**
     * Elabora le foto ancora "in lavorazione", in ordine: 2 alla volta (più veloce),
     * oppure 1 alla volta se nelle impostazioni è spento "2 foto alla volta".
     */
    private fun elaboraInCoda() {
        val app = getApplication<Application>()
        lavoro = viewModelScope.launch {
            // Avviso fisso "Sto preparando le foto…": tiene sveglia l'app anche se Elisa esce
            val questo = coroutineContext[Job]
            ServizioLavoro.avvia(app, elaborate, foto.size)
            Avvisi.togliPronte(app)
            try {
                lavoraTutte()
            } finally {
                // Se intanto è partita un'altra lista, il servizio serve a lei: non lo fermo
                if (lavoro === questo || lavoro == null) withContext(NonCancellable) { ServizioLavoro.ferma(app) }
            }
            // Finito: se Elisa è fuori dall'app, l'avviso "foto pronte"
            if (avvisoPronte && !AppVisibile.visibile && foto.isNotEmpty())
                Avvisi.pronte(app, foto.count { !it.pubblicata }, foto.count { it.daGuardare })
        }
    }

    private suspend fun lavoraTutte() {
        run {
            val prese = mutableSetOf<Int>()   // foto già in mano a un "lavoratore"
            // Ogni "lavoratore" si crea il suo lettore solo quando serve, dentro il controllo errori:
            // se il lettore non parte, la foto finisce con un errore invece di chiudere l'app
            val lavoratori: List<() -> Raddrizzatore> =
                if (dueAllaVolta) listOf({ raddrizzatore }, { secondoRaddrizzatore }) else listOf({ raddrizzatore })
            kotlinx.coroutines.coroutineScope {
                for (r in lavoratori) launch {
                    while (true) {
                        val prossima = foto.firstOrNull { it.inCorso && it.numero !in prese } ?: break
                        prese += prossima.numero
                        val fatta = elabora(prossima, r)
                        // Se nel frattempo Elisa l'ha tolta dalla lista, il risultato si butta
                        val i = foto.indexOfFirst { it.numero == prossima.numero }
                        if (i >= 0) foto[i] = fatta
                        else fatta.fileAuto?.delete()
                        Avvisi.aggiornaLavoro(getApplication(), elaborate, foto.size)
                    }
                }
            }
        }
    }

    /** Toglie una foto dalla lista (resta nella Galleria del telefono). */
    fun togli(numero: Int) {
        val f = foto.firstOrNull { it.numero == numero } ?: return
        foto.remove(f)
        listOfNotNull(f.fileAuto, f.file).distinct().forEach { it.delete() }
    }

    private suspend fun elabora(f: Foto, lettore: () -> Raddrizzatore): Foto = withContext(Dispatchers.Default) {
        try {
            val raddrizzatore = lettore()
            val inizio = System.currentTimeMillis()
            val diario = if (diagnosi) Diario() else null
            raddrizzatore.diario = diario
            val r = raddrizzatore.raddrizza(f.origine)

            // Capi d'abbigliamento sempre in verticale (nel verso che Elisa preferisce).
            // Giochi, libri, peluche e scarpe restano come sono stati fotografati.
            var immagine = r.immagine
            var verso = 0
            // Vestiti in verticale (nel verso che Elisa preferisce), ma solo se le scritte dell'oggetto
            // non hanno già deciso il verso: un oggetto dritto "come lo vede una persona" non si gira più.
            val unCapo = (listOfNotNull(r.dati) + r.altri).any { Abbigliamento.eUnCapo(it.descrizione) }
            if (unCapo && !r.versoDaOggetto && immagine.width > immagine.height) {
                verso = versoPreferito.verso
                immagine = Raddrizzatore.ruotaImmagine(immagine, verso)
                versoPreferito.cambiaVoto(null, verso)   // finché Elisa non la corregge, il verso era giusto
            }

            val file = Raddrizzatore.salva(getApplication(), immagine, "foto_${f.numero}")
            f.copy(
                inCorso = false,
                miniatura = miniatura(immagine).asImageBitmap(),
                fileAuto = file,
                file = file,
                rotazione = (r.rotazioneApplicata + verso) % 360,
                messaInVerticale = verso != 0,
                // Verso da far controllare a Elisa: cartellino non letto dritto, o verso verticale scelto "a intuito"
                daControllare = !r.versoSicuro || verso != 0,
                versoVerticale = verso,
                codice = r.codiceLetto,
                dati = r.dati,
                altri = r.altri,
                etichetteViste = r.etichetteViste,
                metodo = "${r.metodo} · ${r.immagine.width}×${r.immagine.height}",
                secondi = (System.currentTimeMillis() - inizio) / 1000f,
                diario = diario,
            )
        } catch (e: SecurityException) {
            f.copy(inCorso = false, errore = "foto non più raggiungibile: sceglila di nuovo")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // lista sostituita: interrompi senza segnare errori
        } catch (e: Exception) {
            f.copy(inCorso = false, errore = e.message ?: "Errore sconosciuto")
        } catch (e: OutOfMemoryError) {
            f.copy(inCorso = false, errore = "Foto troppo grande per la memoria")
        }
    }

    /** Pulsanti "Gira" (90) e "Capovolgi" (180): Elisa corregge il verso a mano. */
    /** "↻ Destra" (90), "↺ Sinistra" (270), "Capovolgi" (180): l'anteprima gira subito, la foto vera subito dopo. */
    fun gira(numero: Int, gradi: Int) {
        val f = foto.firstOrNull { it.numero == numero } ?: return
        if (f.fileAuto == null) return
        val nuovaManuale = (f.rotazioneManuale + gradi) % 360
        // L'app impara: il voto passa al verso in cui la foto è finita davvero
        if (f.messaInVerticale) {
            versoPreferito.cambiaVoto(
                vecchio = (f.versoVerticale + f.rotazioneManuale) % 360,
                nuovo = (f.versoVerticale + nuovaManuale) % 360,
            )
        }
        // Subito: la miniatura girata (piccola, istantanea). Elisa l'ha guardata: niente più avviso
        val mini = f.miniatura?.let { Raddrizzatore.ruotaImmagine(it.asAndroidBitmap(), gradi).asImageBitmap() }
        aggiorna(numero) { it.copy(rotazioneManuale = nuovaManuale, daControllare = false, miniatura = mini ?: it.miniatura) }
        // Poi, in sottofondo, la foto vera (più tocchi di fila = una sola ricomposizione)
        modifica(numero) { it }
    }

    /**
     * Elisa corregge o aggiunge un articolo a mano.
     * [indice] = posizione dell'articolo nella foto, null = articolo nuovo; [nuovo] = null per eliminarlo.
     */
    fun salvaArticolo(numero: Int, indice: Int?, nuovo: DatiCartellino?) {
        val i = foto.indexOfFirst { it.numero == numero }
        val f = foto.getOrNull(i) ?: return
        val lista = f.articoli.toMutableList()
        when {
            indice == null && nuovo != null -> lista += nuovo
            indice != null && nuovo != null -> lista[indice] = nuovo
            indice != null -> lista.removeAt(indice)
        }
        foto[i] = f.copy(dati = lista.firstOrNull(), altri = lista.drop(1), codice = lista.firstOrNull()?.codice ?: f.codice)
    }

    /** Salva in Galleria (album FotoNegozio) le pagine della diagnosi di una foto. */
    fun salvaDiagnosi(numero: Int) {
        val f = foto.firstOrNull { it.numero == numero } ?: return
        val diario = f.diario ?: return
        viewModelScope.launch {
            val quante = withContext(Dispatchers.Default) {
                val testa = "Foto ${f.numero} · ${f.metodo}"
                Diario.salvaInGalleria(getApplication(), diario.pagine(testa), "foto${f.numero}_${System.currentTimeMillis() / 1000}")
            }
            messaggio = "Diagnosi foto ${f.numero}: $quante immagini in Galleria › FotoNegozio"
        }
    }

    /** Versione piccola (max 600 px) per la lista, così la memoria non si riempie. */
    private fun miniatura(b: Bitmap): Bitmap {
        val scala = 600f / maxOf(b.width, b.height)
        if (scala >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * scala).toInt(), (b.height * scala).toInt(), true)
    }

    init {
        // Ritrovo la lista com'era quando l'app si è chiusa
        foto.addAll(ListaSalvata.leggi(fileLista.takeIf { it.exists() }?.readText().orEmpty()))
        viewModelScope.launch {
            // Miniature rifatte dalle foto salvate (in sottofondo)
            for (f in foto.toList()) {
                val file = f.file ?: continue
                val mini = withContext(Dispatchers.IO) {
                    runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.let(::miniatura)?.asImageBitmap() }.getOrNull()
                } ?: continue
                val i = foto.indexOfFirst { it.numero == f.numero && it.file == file }
                if (i >= 0) foto[i] = foto[i].copy(miniatura = mini)
            }
        }
        if (foto.any { it.inCorso }) elaboraInCoda()
        // Ogni cambiamento della lista si salva (poco dopo, per non scrivere a ogni tocco)
        viewModelScope.launch {
            snapshotFlow { foto.toList() }.collectLatest { lista ->
                delay(300)
                withContext(Dispatchers.IO) {
                    val temporaneo = File(fileLista.path + ".tmp")
                    temporaneo.writeText(ListaSalvata.scrivi(lista))
                    temporaneo.renameTo(fileLista)
                }
            }
        }
    }
}
