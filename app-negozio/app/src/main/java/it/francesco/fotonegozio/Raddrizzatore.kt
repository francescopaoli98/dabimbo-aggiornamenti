package it.francesco.fotonegozio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** Risultato del raddrizzamento di una foto. */
class FotoRaddrizzata(
    val immagine: Bitmap,          // foto già girata nel verso giusto
    val rotazioneApplicata: Int,   // gradi aggiunti da noi (0, 90, 180, 270)
    val codiceLetto: String?,      // codice di 7 cifre trovato (null = cartellino non trovato)
)

/**
 * Raddrizza le foto in due passaggi:
 * 1. applica l'orientamento salvato dal telefono nella foto (dati EXIF);
 * 2. usa ML Kit per cercare il cartellino: prova le 4 rotazioni e tiene
 *    quella in cui il codice di 7 cifre si legge in orizzontale.
 */
class Raddrizzatore(private val context: Context) {

    private val lettoreTesto = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun raddrizza(uri: Uri): FotoRaddrizzata {
        val foto = caricaConExif(uri)

        // Prima la rotazione 0 (caso più comune), poi le altre
        for (gradi in listOf(0, 90, 270, 180)) {
            val testo = lettoreTesto.process(InputImage.fromBitmap(foto, gradi)).await()
            val codice = cercaCodiceDritto(testo)
            if (codice != null) {
                return FotoRaddrizzata(ruota(foto, gradi), gradi, codice)
            }
        }
        // Cartellino non trovato: teniamo solo la correzione EXIF
        return FotoRaddrizzata(foto, 0, null)
    }

    /**
     * Cerca una riga con il codice articolo (7 cifre, es. "1443984" o "A442/1443984")
     * scritta quasi in orizzontale (inclinazione sotto i 30°).
     */
    private fun cercaCodiceDritto(testo: Text): String? {
        for (blocco in testo.textBlocks) {
            for (riga in blocco.lines) {
                val trovato = REGEX_CODICE.find(riga.text) ?: continue
                if (abs(riga.angle) < 30f) return trovato.value
            }
        }
        return null
    }

    /** Legge la foto rimpicciolendola (max ~2500 px) e applica l'orientamento EXIF. */
    private fun caricaConExif(uri: Uri): Bitmap {
        val resolver = context.contentResolver

        // 1. Solo le dimensioni, senza caricare la foto in memoria
        val dimensioni = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, dimensioni) }

        // 2. Fattore di riduzione (potenza di 2), per non esaurire la memoria con 50 foto
        var riduzione = 1
        while (max(dimensioni.outWidth, dimensioni.outHeight) / (riduzione * 2) >= LATO_MASSIMO) {
            riduzione *= 2
        }
        val opzioni = BitmapFactory.Options().apply { inSampleSize = riduzione }
        val foto = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opzioni) }
            ?: error("Impossibile aprire la foto")

        // 3. Orientamento salvato dalla fotocamera
        val gradiExif = resolver.openInputStream(uri).use { stream ->
            stream?.let { ExifInterface(it).rotationDegrees } ?: 0
        }
        return ruota(foto, gradiExif)
    }

    private fun ruota(foto: Bitmap, gradi: Int): Bitmap {
        if (gradi % 360 == 0) return foto
        val matrice = Matrix().apply { postRotate(gradi.toFloat()) }
        return Bitmap.createBitmap(foto, 0, 0, foto.width, foto.height, matrice, true)
    }

    companion object {
        private const val LATO_MASSIMO = 2500

        // 7 cifre esatte, non attaccate ad altre cifre (esclude i codici EAN a 13 cifre)
        private val REGEX_CODICE = Regex("(?<!\\d)\\d{7}(?!\\d)")

        /** Salva la foto raddrizzata come JPEG nella cartella temporanea dell'app. */
        fun salva(context: Context, foto: Bitmap, nome: String): File {
            val cartella = File(context.cacheDir, "raddrizzate").apply { mkdirs() }
            val file = File(cartella, "$nome.jpg")
            file.outputStream().use { foto.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            return file
        }
    }
}
