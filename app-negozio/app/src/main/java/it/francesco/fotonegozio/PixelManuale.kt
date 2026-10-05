package it.francesco.fotonegozio

import kotlin.math.max
import kotlin.math.min

/**
 * Pixelatura A MANO: Elisa passa il dito sulle parti da nascondere e quelle diventano quadrettoni.
 * La foto è divisa in una griglia di quadretti; ogni quadretto toccato prende il colore medio.
 * Kotlin puro: testabile sul PC.
 */
object PixelManuale {

    /** Lato dei quadrettoni: grandi, circa 1/40 del lato lungo della foto. */
    fun lato(larghezza: Int, altezza: Int) = max(8, max(larghezza, altezza) / 40)

    /** Un quadretto della griglia, come numero unico (riga * 100000 + colonna). */
    fun cella(colonna: Int, riga: Int) = riga.toLong() * 100_000 + colonna
    fun colonna(c: Long) = (c % 100_000).toInt()
    fun riga(c: Long) = (c / 100_000).toInt()

    /** I quadretti toccati da un pennello rotondo di [raggio] pixel centrato in (x, y). */
    fun celleAttorno(x: Float, y: Float, raggio: Float, lato: Int, larghezza: Int, altezza: Int): Set<Long> {
        val celle = mutableSetOf<Long>()
        val c0 = max(0, ((x - raggio) / lato).toInt()); val c1 = min((larghezza - 1) / lato, ((x + raggio) / lato).toInt())
        val r0 = max(0, ((y - raggio) / lato).toInt()); val r1 = min((altezza - 1) / lato, ((y + raggio) / lato).toInt())
        for (r in r0..r1) for (c in c0..c1) {
            // centro del quadretto dentro il pennello (con mezzo quadretto di tolleranza)
            val cx = c * lato + lato / 2f; val cy = r * lato + lato / 2f
            val d = raggio + lato / 2f
            if ((cx - x) * (cx - x) + (cy - y) * (cy - y) <= d * d) celle += cella(c, r)
        }
        return celle
    }

    /** Applica la pixelatura ai pixel ARGB [px] (modificati sul posto): ogni quadretto diventa del suo colore medio. */
    fun applica(px: IntArray, larghezza: Int, altezza: Int, celle: Set<Long>, lato: Int) {
        for (c in celle) {
            val x0 = colonna(c) * lato; val y0 = riga(c) * lato
            val x1 = min(larghezza, x0 + lato); val y1 = min(altezza, y0 + lato)
            if (x0 >= x1 || y0 >= y1) continue
            var r = 0L; var g = 0L; var b = 0L; var n = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                val p = px[y * larghezza + x]
                r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; n++
            }
            val media = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
            for (y in y0 until y1) for (x in x0 until x1) px[y * larghezza + x] = media
        }
    }
}
