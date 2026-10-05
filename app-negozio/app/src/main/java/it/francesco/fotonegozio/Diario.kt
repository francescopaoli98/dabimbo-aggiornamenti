package it.francesco.fotonegozio

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.mlkit.vision.text.Text
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Modalità diagnosi: si annota ogni pezzo di foto che l'app prova a leggere e cosa ML Kit ci legge.
 * Alla fine disegna delle "pagine" (immagini) da salvare in Galleria e mandare a chi sistema l'app.
 */
class Diario {

    private class Voce(val fase: String, val jpeg: ByteArray?, val piccola: Boolean, val righe: List<String>)

    private val voci = mutableListOf<Voce>()

    /** Annota un pezzo letto con ML Kit. */
    fun registra(fase: String, immagine: Bitmap, testo: Text) {
        if (voci.size >= MAX_VOCI) return
        val piccola = fase.contains("tassello") || fase.contains("foto intera")
        val scala = min(1f, (if (piccola) 500f else 900f) / max(immagine.width, immagine.height))
        val mini = if (scala < 1f) Bitmap.createScaledBitmap(immagine, (immagine.width * scala).toInt(), (immagine.height * scala).toInt(), true) else immagine
        val jpeg = ByteArrayOutputStream().also { mini.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
        val righe = testo.textBlocks.flatMap { it.lines }.map { "${it.text}   (${it.angle.roundToInt()}°)" }
        voci += Voce("$fase · ${immagine.width}×${immagine.height}", jpeg, piccola, righe.ifEmpty { listOf("(niente letto)") })
    }

    /** Annota solo un testo (es. codici a barre trovati). */
    fun nota(fase: String, testo: String) {
        if (voci.size < MAX_VOCI) voci += Voce(fase, null, true, listOf(testo))
    }

    val vuoto get() = voci.isEmpty()

    /** Disegna le pagine (larghe 1080 px) con ritagli e testi letti. */
    fun pagine(titolo: String): List<Bitmap> {
        val pagine = mutableListOf<Bitmap>()
        val titoloP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val faseP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(200, 80, 0) }
        val testoP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; color = Color.DKGRAY }

        var pagina: Bitmap? = null
        var canvas: Canvas? = null
        var y = 0f
        fun nuovaPagina() {
            pagina = Bitmap.createBitmap(LARGHEZZA, ALTEZZA, Bitmap.Config.ARGB_8888).also { pagine += it }
            canvas = Canvas(pagina!!).apply { drawColor(Color.WHITE) }
            y = 50f
            canvas!!.drawText("$titolo · pagina ${pagine.size}", 20f, y, titoloP)
            y += 30f
        }
        nuovaPagina()

        for ((n, v) in voci.withIndex()) {
            if (pagine.size > MAX_PAGINE) break
            val img = v.jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            val altezzaMax = if (v.piccola) 260f else 480f
            val scala = img?.let { min((LARGHEZZA - 40f) / it.width, altezzaMax / it.height) } ?: 0f
            val altezzaVoce = 40f + (img?.let { it.height * scala + 10f } ?: 0f) + 30f * min(v.righe.size, MAX_RIGHE)
            if (y + altezzaVoce > ALTEZZA - 20) {
                if (pagine.size == MAX_PAGINE) break
                nuovaPagina()
            }
            val c = canvas!!
            y += 34f
            c.drawText("${n + 1}. ${v.fase}", 20f, y, faseP)
            y += 8f
            if (img != null) {
                val dest = Rect(20, y.toInt(), (20 + img.width * scala).toInt(), (y + img.height * scala).toInt())
                c.drawBitmap(img, null, dest, null)
                y += img.height * scala + 10f
            }
            for (r in v.righe.take(MAX_RIGHE)) {
                y += 28f
                c.drawText(r.take(70), 30f, y, testoP)
            }
            y += 10f
        }
        return pagine
    }

    companion object {
        private const val MAX_VOCI = 140
        private const val MAX_PAGINE = 16
        private const val MAX_RIGHE = 8
        private const val LARGHEZZA = 1080
        private const val ALTEZZA = 3600

        /** Salva le pagine in Galleria, album "FotoNegozio". Restituisce quante ne ha salvate. */
        fun salvaInGalleria(context: Context, pagine: List<Bitmap>, nome: String): Int {
            var salvate = 0
            pagine.forEachIndexed { i, p ->
                val file = "diagnosi_${nome}_${i + 1}.jpg"
                if (Build.VERSION.SDK_INT >= 29) {
                    val valori = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, file)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FotoNegozio")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valori) ?: return@forEachIndexed
                    context.contentResolver.openOutputStream(uri)?.use { p.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                } else {
                    val cartella = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FotoNegozio").apply { mkdirs() }
                    File(cartella, file).outputStream().use { p.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                }
                salvate++
            }
            return salvate
        }
    }
}
