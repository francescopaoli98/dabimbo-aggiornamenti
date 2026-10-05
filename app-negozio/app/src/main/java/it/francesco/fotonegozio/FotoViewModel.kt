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
    val codice: String? = null,
    val metodo: String = "",             // per le prove: come è stato trovato il cartellino
    val secondi: Float = 0f,             // per le prove: tempo di elaborazione
    val errore: String? = null,
)

/** Tiene la lista delle foto ed esegue l'elaborazione una alla volta, in sottofondo. */
class FotoViewModel(app: Application) : AndroidViewModel(app) {

    val foto = mutableStateListOf<Foto>()
    var elaborate by mutableStateOf(0)
        private set

    private val raddrizzatore = Raddrizzatore(app)
    private val versoPreferito = VersoPreferito(app)
    private var lavoro: Job? = null
    private val bloccoRotazioni = Mutex()   // un tocco su "Gira" alla volta

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
            val r = raddrizzatore.raddrizza(f.origine)

            // Foto sempre verticale: se è orizzontale la giro nel verso che Elisa preferisce
            var immagine = r.immagine
            var verso = 0
            if (immagine.width > immagine.height) {
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
                versoVerticale = verso,
                codice = r.codiceLetto,
                metodo = "${r.metodo} · ${r.immagine.width}×${r.immagine.height}",
                secondi = (System.currentTimeMillis() - inizio) / 1000f,
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
                foto[i] = f.copy(file = file, miniatura = mini, rotazioneManuale = nuovaManuale)
            }
        }
    }

    /** Versione piccola (max 600 px) per la lista, così la memoria non si riempie. */
    private fun miniatura(b: Bitmap): Bitmap {
        val scala = 600f / maxOf(b.width, b.height)
        if (scala >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * scala).toInt(), (b.height * scala).toInt(), true)
    }
}
