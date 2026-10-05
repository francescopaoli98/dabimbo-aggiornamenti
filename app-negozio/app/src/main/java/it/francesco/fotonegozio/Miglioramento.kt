package it.francesco.fotonegozio

import kotlin.math.max

/**
 * "Pulisce" l'immagine di un cartellino sbiadito o sfocato prima di rileggerla:
 * bianco e nero, contrasto al massimo, un po' più di nitidezza.
 * Lavora su un array di pixel (Kotlin puro), così si può testare sul PC.
 */
object Miglioramento {

    fun migliora(px: IntArray, w: Int, h: Int): IntArray {
        val n = w * h
        // 1. Grigio (luminosità)
        val g = IntArray(n) { i ->
            val c = px[i]
            (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
        }
        // 2. Contrasto: il 2% più scuro diventa nero, il 2% più chiaro bianco
        val istogramma = IntArray(256).also { ist -> g.forEach { ist[it]++ } }
        val taglio = (n * 0.02).toInt()
        var basso = 0; var somma = 0
        while (basso < 255 && somma + istogramma[basso] <= taglio) { somma += istogramma[basso]; basso++ }
        var alto = 255; somma = 0
        while (alto > 0 && somma + istogramma[alto] <= taglio) { somma += istogramma[alto]; alto-- }
        val ampiezza = max(1, alto - basso)
        val s = IntArray(n) { (((g[it] - basso) * 255) / ampiezza).coerceIn(0, 255) }
        // 3. Nitidezza: ogni pixel si "stacca" dai vicini
        val out = IntArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val v = if (x == 0 || y == 0 || x == w - 1 || y == h - 1) s[i]
            else (5 * s[i] - s[i - 1] - s[i + 1] - s[i - w] - s[i + w]).coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return out
    }
}
