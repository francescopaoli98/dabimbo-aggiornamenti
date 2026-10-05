package it.francesco.fotonegozio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.min

/** Risultato della pixelatura: la foto e quanti oggetti sono rimasti nitidi (per le prove). */
class FotoPixelata(val immagine: Bitmap, val oggettiNitidi: Int, val oggettiTotali: Int)

/**
 * Pixela lo sfondo a quadrettoni grandi, lasciando nitidi gli articoli in vendita e i cartellini.
 * Gli oggetti li separa ML Kit "Subject Segmentation" (servizi Google del telefono, gratis;
 * la prima volta scarica un piccolo modello). Se non è disponibile restituisce null.
 */
class Pixelatore {

    private val segmentatore = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder()
            .enableMultipleSubjects(SubjectSegmenterOptions.SubjectResultOptions.Builder().enableConfidenceMask().build())
            .build()
    )

    suspend fun pixela(foto: Bitmap, zone: List<ZonaNitida>): FotoPixelata? {
        // 1. Oggetti trovati su una copia piccola (più veloce): maschera "cosa resta nitido"
        val k = min(1f, LATO_ANALISI.toFloat() / max(foto.width, foto.height))
        val sw = (foto.width * k).toInt().coerceAtLeast(1)
        val sh = (foto.height * k).toInt().coerceAtLeast(1)
        val piccola = Bitmap.createScaledBitmap(foto, sw, sh, true)
        val risultato = segmentatore.process(InputImage.fromBitmap(piccola, 0)).await()

        val oggetti = risultato.subjects.mapNotNull { s ->
            val maschera = s.confidenceMask ?: return@mapNotNull null
            val valori = FloatArray(s.width * s.height).also { maschera.rewind(); maschera.get(it) }
            val pixel = valori.count { it > 0.5f }
            OggettoFoto(s.startX, s.startY, s.startX + s.width, s.startY + s.height, pixel) { x, y ->
                val mx = x - s.startX; val my = y - s.startY
                mx in 0 until s.width && my in 0 until s.height && valori[my * s.width + mx] > 0.5f
            } to valori
        }
        val zonePiccole = zone.map { ZonaNitida(it.x * k, it.y * k, it.raggio * k) }
        val scelti = SceltaNitidi.scegli(oggetti.map { it.first }, zonePiccole, sw * sh)

        val nitido = BooleanArray(sw * sh)
        for (i in scelti) {
            val (o, valori) = oggetti[i]
            val w = o.dx - o.sx
            for (y in o.su until o.giu) for (x in o.sx until o.dx) {
                if (valori[(y - o.su) * w + (x - o.sx)] > 0.5f) nitido[y * sw + x] = true
            }
        }
        // I cartellini sempre nitidi
        for (z in zonePiccole) {
            val r = z.raggio * 0.6f
            for (y in max(0, (z.y - r).toInt())..min(sh - 1, (z.y + r).toInt()))
                for (x in max(0, (z.x - r).toInt())..min(sw - 1, (z.x + r).toInt()))
                    if ((x - z.x) * (x - z.x) + (y - z.y) * (y - z.y) <= r * r) nitido[y * sw + x] = true
        }
        val allargato = allarga(nitido, sw, sh, 2)   // un po' di margine attorno agli oggetti

        // 2. Foto pixelata intera: rimpicciolita e riallargata senza sfumare = quadrettoni
        val lato = SceltaNitidi.latoQuadretto(foto.width, foto.height)
        val mini = Bitmap.createScaledBitmap(foto, max(1, foto.width / lato), max(1, foto.height / lato), true)
        val uscita = Bitmap.createBitmap(foto.width, foto.height, Bitmap.Config.ARGB_8888)
        Canvas(uscita).drawBitmap(mini, null, Rect(0, 0, foto.width, foto.height), Paint().apply { isFilterBitmap = false })

        // 3. Riga per riga: dove la maschera dice "nitido" rimetto i pixel originali
        val riga = IntArray(foto.width)
        for (y in 0 until foto.height) {
            val ys = min(sh - 1, (y * k).toInt())
            foto.getPixels(riga, 0, foto.width, 0, y, foto.width, 1)
            var x = 0
            while (x < foto.width) {
                // tratti consecutivi nitidi copiati in un colpo solo
                if (allargato[ys * sw + min(sw - 1, (x * k).toInt())]) {
                    val inizio = x
                    while (x < foto.width && allargato[ys * sw + min(sw - 1, (x * k).toInt())]) x++
                    uscita.setPixels(riga, inizio, foto.width, inizio, y, x - inizio, 1)
                } else x++
            }
        }
        return FotoPixelata(uscita, scelti.size, oggetti.size)
    }

    /** Allarga le zone nitide di [passi] pixel (sulla maschera piccola). */
    private fun allarga(m: BooleanArray, w: Int, h: Int, passi: Int): BooleanArray {
        var cur = m
        repeat(passi) {
            val nuovo = cur.copyOf()
            for (y in 0 until h) for (x in 0 until w) {
                if (cur[y * w + x]) continue
                if ((x > 0 && cur[y * w + x - 1]) || (x < w - 1 && cur[y * w + x + 1]) ||
                    (y > 0 && cur[(y - 1) * w + x]) || (y < h - 1 && cur[(y + 1) * w + x])
                ) nuovo[y * w + x] = true
            }
            cur = nuovo
        }
        return cur
    }

    companion object {
        private const val LATO_ANALISI = 1024
    }
}
