package it.francesco.fotonegozio

import kotlin.math.max
import kotlin.math.min

/**
 * Trova le etichette nella foto dalla FORMA, senza leggere niente:
 * rettangolini bianchi con dentro qualcosa di nero (codice a barre, scritte).
 * Serve quando il testo è troppo piccolo o sfocato perché ML Kit lo trovi da solo:
 * l'app poi ritaglia ogni etichetta, la ingrandisce e la legge.
 *
 * Lavora su un array di pixel (Kotlin puro), così si può testare sul PC.
 */
object TrovaEtichette {

    /** Un'etichetta candidata: riquadro in pixel dell'immagine analizzata. */
    data class Riquadro(val sx: Int, val su: Int, val dx: Int, val giu: Int) {
        val area get() = (dx - sx) * (giu - su)
        fun sovrappostoA(a: Riquadro): Boolean {
            val ix = max(0, min(dx, a.dx) - max(sx, a.sx))
            val iy = max(0, min(giu, a.giu) - max(su, a.su))
            return ix * iy > 0.5 * min(area, a.area)
        }
    }

    // Due soglie di "bianco": normale, e "molto bianco" per gli sfondi chiari (es. felpa rosa)
    private val SOGLIE = listOf(170 to 45, 215 to 30)   // (luminosità minima, differenza massima tra i colori)

    /**
     * Cerca le etichette nei pixel ARGB [px] di un'immagine [w]×[h].
     * Restituisce i riquadri nelle coordinate dell'immagine data.
     */
    fun trova(px: IntArray, w: Int, h: Int): List<Riquadro> {
        val trovati = mutableListOf<Riquadro>()
        for ((soglia, colore) in SOGLIE) {
            for (r in componentiBianche(px, w, h, soglia, colore)) {
                if (trovati.none { it.sovrappostoA(r) }) trovati += r
            }
        }
        return trovati
    }

    private fun componentiBianche(px: IntArray, w: Int, h: Int, soglia: Int, colore: Int): List<Riquadro> {
        val n = w * h
        val bianco = BooleanArray(n) { i ->
            val c = px[i]
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            mx > soglia && mx - mn < colore
        }
        val visto = BooleanArray(n)
        val coda = IntArray(n)
        val risultato = mutableListOf<Riquadro>()

        for (inizio in 0 until n) {
            if (!bianco[inizio] || visto[inizio]) continue
            // Riempimento: tutti i pixel bianchi collegati a questo
            var testa = 0; var fine = 0
            coda[fine++] = inizio; visto[inizio] = true
            var x0 = w; var x1 = 0; var y0 = h; var y1 = 0; var conta = 0
            while (testa < fine) {
                val i = coda[testa++]
                val x = i % w; val y = i / w
                conta++
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
                if (x + 1 < w && bianco[i + 1] && !visto[i + 1]) { visto[i + 1] = true; coda[fine++] = i + 1 }
                if (x > 0 && bianco[i - 1] && !visto[i - 1]) { visto[i - 1] = true; coda[fine++] = i - 1 }
                if (y + 1 < h && bianco[i + w] && !visto[i + w]) { visto[i + w] = true; coda[fine++] = i + w }
                if (y > 0 && bianco[i - w] && !visto[i - w]) { visto[i - w] = true; coda[fine++] = i - w }
            }
            val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
            // Né briciole né mezza foto; abbastanza "pieno" (un rettangolo, non una striscia sottile)
            if (conta < n * 0.002 || conta > n * 0.15) continue
            if (conta.toFloat() / (bw * bh) < 0.35f) continue
            if (max(bw, bh).toFloat() / min(bw, bh) > 5f) continue
            // Dentro un'etichetta c'è del nero (codice a barre, scritte), ma non troppo
            var scuri = 0
            for (y in y0..y1) for (x in x0..x1) {
                val c = px[y * w + x]
                if (max((c shr 16) and 255, max((c shr 8) and 255, c and 255)) < 120) scuri++
            }
            val quotaScuri = scuri.toFloat() / (bw * bh)
            if (quotaScuri < 0.02f || quotaScuri > 0.5f) continue
            risultato += Riquadro(x0, y0, x1 + 1, y1 + 1)
        }
        return risultato
    }
}
