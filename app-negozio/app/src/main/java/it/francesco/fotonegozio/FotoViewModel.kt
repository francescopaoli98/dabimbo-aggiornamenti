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
    val pubblicata: Boolean = false,     // pezzo 6: già mandata a WhatsApp    // testo per lo stato scritto/corretto da Elisa (null = quello automatico)
)

/** Tutti gli articoli della foto (il principale + gli altri), nell'ordine in cui vengono mostrati. */
val Foto.articoli: List<DatiCartellino> get() = listOfNotNull(dati) + altri

/** Un articolo è "da completare" se manca la descrizione o il prezzo (es. cartellino sfocato). */
val DatiCartellino.daCompletare: Boolean get() = descrizione == null || prezzo == null

private fun fileDizionario(app: Application) = File(app.filesDir, "dizionario.txt")

/** Il dizionario salvato sul telefono; la prima volta, quello di partenza (assets/dizionario.txt). */
private fun caricaDizionario(app: Application): List<VoceDizionario> {
    val file = fileDizionario(app)
    if (!file.exists()) file.writeText(app.assets.open("dizionario.txt").bufferedReader().readText())
    return Dizionario.leggi(file.readText()).sortedBy { it.sigla }
}

/** Cose da controllare prima di pubblicare (avvisi arancioni). Vuota = tutto a posto. */
val Foto.avvisi: List<String> get() = buildList {
    if (daControllare) add("il verso della foto")
    if (articoli.isEmpty()) add("il cartellino non è stato letto")
    if (articoli.any { it.daCompletare }) add("un articolo ha descrizione o prezzo mancanti")
    if (etichetteViste > articoli.size) add("ci sono etichette non lette")
}

/** Tiene la lista delle foto ed esegue l'elaborazione una alla volta, in sottofondo. */
class FotoViewModel(app: Application) : AndroidViewModel(app) {

    val foto = mutableStateListOf<Foto>()
    var elaborate by mutableStateOf(0)
        private set

    private val raddrizzatore by lazy { Raddrizzatore(app) }   // creato solo quando serve (prima foto)
    private val versoPreferito = VersoPreferito(app)

    private var lavoro: Job? = null
    private val bloccoRotazioni = Mutex()   // un tocco su "Gira" alla volta

    /** Dizionario delle sigle (salvato in un file di testo sul telefono). */
    val dizionario = mutableStateListOf<VoceDizionario>().apply { addAll(caricaDizionario(app)) }

    /** Testo per lo stato di una foto: quello corretto da Elisa, o quello composto dall'app. */
    fun testo(f: Foto): String = f.testoManuale ?: TestoFinale.testo(f.articoli, dizionario)

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

    /** Pixelatura a mano: applica i quadretti [celle] alla foto attuale e la salva come nuova foto. */
    fun salvaPixelata(numero: Int, celle: Set<Long>, lato: Int) {
        if (celle.isEmpty()) return
        viewModelScope.launch {
            bloccoRotazioni.withLock {
                val i = foto.indexOfFirst { it.numero == numero }
                val f = foto.getOrNull(i) ?: return@withLock
                val attuale = f.file ?: return@withLock
                val (nuovo, mini) = withContext(Dispatchers.Default) {
                    val b = BitmapFactory.decodeFile(attuale.path)
                    val w = b.width; val h = b.height
                    val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
                    PixelManuale.applica(px, w, h, celle, lato)
                    val pixelata = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                    val file = Raddrizzatore.salva(getApplication(), pixelata, "foto_${numero}_px${System.currentTimeMillis() % 100_000}")
                    file to miniatura(pixelata).asImageBitmap()
                }
                // La foto pixelata diventa la nuova base (le rotazioni a mano ripartono da qui)
                foto[i] = f.copy(fileAuto = nuovo, file = nuovo, miniatura = mini, rotazioneManuale = 0, messaInVerticale = false, daControllare = false)
            }
        }
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
        elaborate = 0
        uris.forEachIndexed { i, uri -> foto.add(Foto(numero = i + 1, origine = uri)) }

        lavoro = viewModelScope.launch {
            for (i in foto.indices) {
                foto[i] = elabora(foto[i])
                elaborate++
            }
        }
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // lista sostituita: interrompi senza segnare errori
        } catch (e: Exception) {
            f.copy(inCorso = false, errore = e.message ?: "Errore sconosciuto")
        } catch (e: OutOfMemoryError) {
            f.copy(inCorso = false, errore = "Foto troppo grande per la memoria")
        }
    }

    /** Pulsanti "Gira" (90) e "Capovolgi" (180): Elisa corregge il verso a mano. */
    fun gira(numero: Int, gradi: Int) {
        viewModelScope.launch {
            bloccoRotazioni.withLock {
                val i = foto.indexOfFirst { it.numero == numero }
                val f = foto.getOrNull(i) ?: return@withLock
                val base = f.fileAuto ?: return@withLock
                val nuovaManuale = (f.rotazioneManuale + gradi) % 360

                val (file, mini) = withContext(Dispatchers.Default) {
                    val partenza = BitmapFactory.decodeFile(base.path)
                    val girata = Raddrizzatore.ruotaImmagine(partenza, nuovaManuale)
                    // Nome diverso per ogni verso, così l'anteprima si aggiorna
                    val file = if (nuovaManuale == 0) base
                    else Raddrizzatore.salva(getApplication(), girata, "foto_${f.numero}_r$nuovaManuale")
                    file to miniatura(girata).asImageBitmap()
                }

                // L'app impara: il voto passa al verso in cui la foto è finita davvero
                if (f.messaInVerticale) {
                    versoPreferito.cambiaVoto(
                        vecchio = (f.versoVerticale + f.rotazioneManuale) % 360,
                        nuovo = (f.versoVerticale + nuovaManuale) % 360,
                    )
                }
                // Elisa l'ha guardata e girata: non serve più l'avviso
                foto[i] = f.copy(file = file, miniatura = mini, rotazioneManuale = nuovaManuale, daControllare = false)
            }
        }
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
}
