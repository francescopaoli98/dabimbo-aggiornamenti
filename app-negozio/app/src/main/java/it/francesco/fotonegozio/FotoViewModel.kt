package it.francesco.fotonegozio

import android.app.Application
import android.graphics.Bitmap
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
import kotlinx.coroutines.withContext
import java.io.File

/** Una foto nella lista, con il suo stato di elaborazione. */
data class Foto(
    val numero: Int,
    val origine: Uri,
    val inCorso: Boolean = true,
    val miniatura: ImageBitmap? = null,
    val file: File? = null,              // foto raddrizzata salvata
    val rotazione: Int = 0,
    val codice: String? = null,
    val errore: String? = null,
)

/** Tiene la lista delle foto ed esegue l'elaborazione una alla volta, in sottofondo. */
class FotoViewModel(app: Application) : AndroidViewModel(app) {

    val foto = mutableStateListOf<Foto>()
    var elaborate by mutableStateOf(0)
        private set

    private val raddrizzatore = Raddrizzatore(app)
    private var lavoro: Job? = null

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
            val r = raddrizzatore.raddrizza(f.origine)
            val file = Raddrizzatore.salva(getApplication(), r.immagine, "foto_${f.numero}")
            f.copy(
                inCorso = false,
                miniatura = miniatura(r.immagine).asImageBitmap(),
                file = file,
                rotazione = r.rotazioneApplicata,
                codice = r.codiceLetto,
            )
        } catch (e: Exception) {
            f.copy(inCorso = false, errore = e.message ?: "Errore sconosciuto")
        }
    }

    /** Versione piccola (max 600 px) per la lista, così la memoria non si riempie. */
    private fun miniatura(b: Bitmap): Bitmap {
        val scala = 600f / maxOf(b.width, b.height)
        if (scala >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * scala).toInt(), (b.height * scala).toInt(), true)
    }
}
