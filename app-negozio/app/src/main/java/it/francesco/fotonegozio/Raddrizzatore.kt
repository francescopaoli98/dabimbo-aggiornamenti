package it.francesco.fotonegozio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Risultato del raddrizzamento di una foto. */
class FotoRaddrizzata(
    val immagine: Bitmap,          // foto già girata nel verso giusto
    val rotazioneApplicata: Int,   // gradi aggiunti da noi (0, 90, 180, 270)
    val codiceLetto: String?,      // codice di 7 cifre (null = cartellino non trovato)
    val metodo: String,            // come è stato trovato il cartellino (per le prove)
)

/**
 * Raddrizza le foto:
 * 1. applica l'orientamento EXIF salvato dal telefono;
 * 2. TROVA il cartellino (codice a barre → testo su foto intera → foto divisa in 9 tasselli);
 * 3. RITAGLIA il cartellino e prova le 4 rotazioni sul ritaglio: tiene quella in cui
 *    il codice si legge dritto.
 *
 * Perché i tasselli: ML Kit rimpicciolisce le foto grandi prima di leggerle, e nelle foto
 * del capo intero il testo del cartellino diventa troppo piccolo. Un tassello è più piccolo
 * della foto, quindi il testo resta leggibile.
 */
class Raddrizzatore(private val context: Context) {

    private val lettoreTesto = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val lettoreBarre = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODE_128).build()
    )

    /** Zona della foto dove c'è il cartellino, trovata in una foto ruotata di [rotazione] gradi. */
    private class Zona(val base: Bitmap, val rotazione: Int, val riquadro: Rect, val codice: String?, val metodo: String)

    suspend fun raddrizza(uri: Uri): FotoRaddrizzata {
        val foto = caricaConExif(uri)
        val zona = trovaCartellino(foto)
            ?: return FotoRaddrizzata(foto, 0, null, "non trovato")

        // Ritaglio il cartellino e cerco il verso giusto (4 prove su un pezzo piccolo = veloce)
        val ritaglio = ritaglia(zona.base, zona.riquadro)
        for (gradi in listOf(0, 90, 270, 180)) {
            val testo = leggi(ruota(ritaglio, gradi))
            val codiceDritto = cercaCodice(testo, soloDritto = true)
            if (codiceDritto != null) {
                val totale = (zona.rotazione + gradi) % 360
                return FotoRaddrizzata(ruota(foto, totale), totale, zona.codice ?: codiceDritto, zona.metodo)
            }
        }
        // Cartellino trovato ma verso incerto: se viene dalla rotazione di 90° almeno quella la applico
        return FotoRaddrizzata(ruota(foto, zona.rotazione), zona.rotazione, zona.codice, zona.metodo + ", verso incerto")
    }

    /** Cerca il cartellino, dal metodo più veloce al più lento. */
    private suspend fun trovaCartellino(foto: Bitmap): Zona? {
        // 1. Codice a barre su tutta la foto: contiene direttamente il codice articolo
        val barre = lettoreBarre.process(InputImage.fromBitmap(foto, 0)).await()
        barre.firstOrNull { it.rawValue?.matches(SETTE_CIFRE) == true && it.boundingBox != null }?.let {
            return Zona(foto, 0, it.boundingBox!!, it.rawValue, "codice a barre")
        }

        // 2. Testo su tutta la foto (funziona se il cartellino è grande, foto da vicino)
        val testoIntero = leggi(foto)
        rigaCodice(testoIntero)?.let { return Zona(foto, 0, it.boundingBox!!, codiceDi(it), "testo") }

        // 3. Zone dove ML Kit ha visto del testo ma non è riuscito a leggerlo: ritaglio e ingrandisco
        val provate = mutableListOf<Rect>()
        ingrandisciZone(foto, 0, zoneTesto(testoIntero, Rect(0, 0, foto.width, foto.height)), provate)
            ?.let { return it }

        // 4. Foto divisa in tasselli, prima così com'è poi girata di 90° (per il testo in verticale)
        for (gradi in listOf(0, 90)) {
            val base = ruota(foto, gradi)
            val zoneTasselli = mutableListOf<Rect>()
            for (t in tasselli(base)) {
                val pezzo = Bitmap.createBitmap(base, t.left, t.top, t.width(), t.height())
                val testo = leggi(pezzo)
                rigaCodice(testo)?.let {
                    val r = it.boundingBox!!
                    r.offset(t.left, t.top)   // coordinate dal tassello alla foto intera
                    return Zona(base, gradi, r, codiceDi(it), "tasselli")
                }
                zoneTasselli += zoneTesto(testo, t)
            }
            // 5. Come il punto 3, ma con le zone viste nei tasselli
            ingrandisciZone(base, gradi, zoneTasselli, if (gradi == 0) provate else mutableListOf())
                ?.let { return it }
        }
        return null
    }

    /**
     * Riquadri dei blocchi di testo (anche illeggibili), già spostati nelle coordinate della foto.
     * Prima quelli con più cifre: il cartellino ha codice e prezzo.
     */
    private fun zoneTesto(testo: Text, tassello: Rect): List<Rect> =
        testo.textBlocks
            .filter { it.boundingBox != null }
            .sortedByDescending { b -> b.text.count { it.isDigit() } }
            .map { b -> Rect(b.boundingBox!!).apply { offset(tassello.left, tassello.top) } }

    /** Ritaglia e ingrandisce fino a [MAX_ZONE] zone, cercando il codice in orizzontale e in verticale. */
    private suspend fun ingrandisciZone(base: Bitmap, gradi: Int, zone: List<Rect>, provate: MutableList<Rect>): Zona? {
        var tentativi = 0
        for (z in zone) {
            if (tentativi >= MAX_ZONE) break
            // Zona già coperta da un ritaglio precedente: inutile rileggerla
            if (provate.any { it.contains(z.centerX(), z.centerY()) }) continue
            tentativi++
            val (ritaglio, area) = ritagliaConArea(base, z)
            provate += area
            for (g in listOf(0, 90)) {
                rigaCodice(leggi(ruota(ritaglio, g)))?.let {
                    return Zona(base, gradi, z, codiceDi(it), "ingrandimento")
                }
            }
        }
        return null
    }

    private suspend fun leggi(b: Bitmap): Text = lettoreTesto.process(InputImage.fromBitmap(b, 0)).await()

    /** Riga con il codice di 7 cifre (qualsiasi inclinazione), es. "1443984" o "A442/1443984". */
    private fun rigaCodice(testo: Text): Text.Line? =
        testo.textBlocks.flatMap { it.lines }.firstOrNull { REGEX_CODICE.containsMatchIn(it.text) && it.boundingBox != null }

    private fun codiceDi(riga: Text.Line): String? = REGEX_CODICE.find(riga.text)?.value

    /** Codice di 7 cifre; con [soloDritto] solo se la riga è più orizzontale che verticale (cartellino anche un po' storto). */
    private fun cercaCodice(testo: Text, soloDritto: Boolean): String? {
        for (riga in testo.textBlocks.flatMap { it.lines }) {
            val trovato = REGEX_CODICE.find(riga.text) ?: continue
            if (!soloDritto || abs(riga.angle) < 45f) return trovato.value
        }
        return null
    }

    /** 9 tasselli che si sovrappongono a metà (griglia 3x3, ognuno grande metà foto). */
    private fun tasselli(b: Bitmap): List<Rect> {
        val w = b.width / 2
        val h = b.height / 2
        return buildList {
            for (gy in 0..2) for (gx in 0..2) {
                val x = gx * b.width / 4
                val y = gy * b.height / 4
                add(Rect(x, y, min(x + w, b.width), min(y + h, b.height)))
            }
        }
    }

    /**
     * Ritaglio quadrato attorno alla zona trovata, abbastanza grande da contenere
     * tutto il cartellino, ingrandito se piccolo (ML Kit legge meglio).
     */
    private fun ritaglia(b: Bitmap, zona: Rect): Bitmap = ritagliaConArea(b, zona).first

    /** Come [ritaglia], ma restituisce anche l'area della foto ritagliata. */
    private fun ritagliaConArea(b: Bitmap, zona: Rect): Pair<Bitmap, Rect> {
        // Almeno 1/5 della foto: se la zona è una sola parola, il ritaglio prende comunque tutto il cartellino
        val lato = max(max(zona.width(), zona.height()) * 3, max(b.width, b.height) / 5)
        val cx = zona.centerX()
        val cy = zona.centerY()
        val r = Rect(cx - lato / 2, cy - lato / 2, cx + lato / 2, cy + lato / 2)
        r.intersect(0, 0, b.width, b.height)
        val pezzo = Bitmap.createBitmap(b, r.left, r.top, r.width(), r.height())
        val scala = LATO_RITAGLIO.toFloat() / max(pezzo.width, pezzo.height)
        val finale = if (scala > 1f) Bitmap.createScaledBitmap(pezzo, (pezzo.width * scala).toInt(), (pezzo.height * scala).toInt(), true)
        else pezzo
        return finale to r
    }

    /** Legge la foto (max ~4000 px per lato) e applica l'orientamento EXIF. */
    private fun caricaConExif(uri: Uri): Bitmap {
        val resolver = context.contentResolver

        // 1. Solo le dimensioni, senza caricare la foto in memoria
        val dimensioni = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, dimensioni) }

        // 2. Riduzione (potenza di 2) solo per le foto enormi, es. 50 megapixel
        var riduzione = 1
        while (max(dimensioni.outWidth, dimensioni.outHeight) / riduzione > LATO_MASSIMO) riduzione *= 2
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
        private const val LATO_MASSIMO = 4100   // 12 MP restano intere, 50 MP dimezzate
        private const val LATO_RITAGLIO = 1200
        private const val MAX_ZONE = 6           // zone ingrandite al massimo per ogni giro (tiene basso il tempo)

        private val SETTE_CIFRE = Regex("\\d{7}")
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
