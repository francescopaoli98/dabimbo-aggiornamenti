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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
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
)

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
    fun salvaPixelManuale(numero: Int, celle: Set<Long>, lato: Int) = modifica(numero) { it.copy(pixelManuale = celle, latoPixel = lato) }

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
    fun togliPixelSfondo(numero: Int) = modifica(numero) { it.copy(sfondoPixelato = false) }

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

    /** Cambia una foto (rotazione, pixel…) e ne ricompone il file finale, un cambiamento alla volta. */
    private fun modifica(numero: Int, fine: () -> Unit = {}, cambia: suspend (Foto) -> Foto?) {
        viewModelScope.launch {
            try {
                bloccoRotazioni.withLock {
                    val f = foto.firstOrNull { it.numero == numero } ?: return@withLock
                    val nuova = cambia(f) ?: return@withLock
                    val composta = componi(nuova)
                    // Il file finale vecchio non serve più (le basi sì)
                    val vecchio = f.file
                    if (vecchio != null && vecchio != composta.file && vecchio != f.fileAuto && vecchio != f.fileSfondo) vecchio.delete()
                    val i = foto.indexOfFirst { it.numero == numero }
                    if (i >= 0) foto[i] = composta
                }
            } finally {
                fine()
            }
        }
    }

    /** Foto finale = base (o base con lo sfondo pixelato) + quadretti a mano, girata come vuole Elisa. */
    private suspend fun componi(f: Foto): Foto = withContext(Dispatchers.Default) {
        val base = (if (f.sfondoPixelato) f.fileSfondo else null) ?: f.fileAuto ?: return@withContext f
        var b = BitmapFactory.decodeFile(base.path) ?: return@withContext f
        if (f.pixelManuale.isEmpty() && f.rotazioneManuale == 0) {
            return@withContext f.copy(file = base, miniatura = miniatura(b).asImageBitmap())
        }
        if (f.pixelManuale.isNotEmpty()) {
            val w = b.width; val h = b.height
            val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
            PixelManuale.applica(px, w, h, f.pixelManuale, f.latoPixel.takeIf { it > 0 } ?: PixelManuale.lato(w, h))
            b = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }
        b = Raddrizzatore.ruotaImmagine(b, f.rotazioneManuale)
        // Nome sempre nuovo, così le anteprime si aggiornano
        val file = Raddrizzatore.salva(getApplication(), b, "foto_${f.numero}_v${System.currentTimeMillis() % 100_000_000}")
        f.copy(file = file, miniatura = miniatura(b).asImageBitmap())
    }

    /** Torna alla schermata iniziale: lista vuota. */
    fun svuota() {
        lavoro?.cancel()
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
    }

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
    private fun elaboraInCoda() {
        lavoro = viewModelScope.launch {
            while (true) {
                val prossima = foto.firstOrNull { it.inCorso } ?: break
                val fatta = elabora(prossima)
                // Se nel frattempo Elisa l'ha tolta dalla lista, il risultato si butta
                val i = foto.indexOfFirst { it.numero == prossima.numero }
                if (i >= 0) foto[i] = fatta
                else fatta.fileAuto?.delete()
            }
        }
    }

    /** Toglie una foto dalla lista (resta nella Galleria del telefono). */
    fun togli(numero: Int) {
        val f = foto.firstOrNull { it.numero == numero } ?: return
        foto.remove(f)
        listOfNotNull(f.fileAuto, f.file).distinct().forEach { it.delete() }
    }

    private suspend fun elabora(f: Foto): Foto = withContext(Dispatchers.Default) {
        try {
            val inizio = System.currentTimeMillis()
            val diario = if (diagnosi) Diario() else null
            raddrizzatore.diario = diario
            val r = raddrizzatore.raddrizza(f.origine)

            // Capi d'abbigliamento sempre in verticale (nel verso che Elisa preferisce).
            // Giochi, libri, peluche e scarpe restano come sono stati fotografati.
            var immagine = r.immagine
            var verso = 0
            val unCapo = (listOfNotNull(r.dati) + r.altri).any { Abbigliamento.eUnCapo(it.descrizione) }
            if (unCapo && immagine.width > immagine.height) {
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
    fun gira(numero: Int, gradi: Int) = modifica(numero) { f ->
        val nuovaManuale = (f.rotazioneManuale + gradi) % 360
        // L'app impara: il voto passa al verso in cui la foto è finita davvero
        if (f.messaInVerticale) {
            versoPreferito.cambiaVoto(
                vecchio = (f.versoVerticale + f.rotazioneManuale) % 360,
                nuovo = (f.versoVerticale + nuovaManuale) % 360,
            )
        }
        // Elisa l'ha guardata e girata: non serve più l'avviso
        f.copy(rotazioneManuale = nuovaManuale, daControllare = false)
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
        controllaAggiornamenti()
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
