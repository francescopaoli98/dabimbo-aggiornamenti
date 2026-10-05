package it.francesco.fotonegozio

import kotlin.math.hypot
import kotlin.math.max

/** Una zona da tenere nitida: dove c'è un cartellino (centro e raggio, in pixel della foto). */
data class ZonaNitida(val x: Float, val y: Float, val raggio: Float)

/**
 * Un oggetto trovato nella foto (dalla segmentazione), visto in modo semplice:
 * riquadro, quanti pixel occupa, e se contiene un certo punto.
 */
class OggettoFoto(
    val sx: Int, val su: Int, val dx: Int, val giu: Int,
    val pixel: Int,
    val contiene: (Int, Int) -> Boolean,
) {
    fun distanzaDa(x: Float, y: Float): Float {
        val ddx = max(0f, max(sx - x, x - dx))
        val ddy = max(0f, max(su - y, y - giu))
        return hypot(ddx, ddy)
    }
}

/**
 * Decide quali oggetti restano nitidi (Kotlin puro, testabile sul PC):
 * - quelli col cartellino (il cartellino ci sta sopra, o è appoggiato lì vicino);
 * - quelli grandi in primo piano ("preponderanti");
 * tutto il resto verrà pixelato.
 */
object SceltaNitidi {

    /** Un oggetto che occupa almeno questa parte della foto è "in primo piano": resta nitido. */
    const val QUOTA_PREPONDERANTE = 0.08f

    fun scegli(oggetti: List<OggettoFoto>, zone: List<ZonaNitida>, pixelFoto: Int): Set<Int> {
        val tenuti = mutableSetOf<Int>()
        oggetti.forEachIndexed { i, o -> if (o.pixel >= pixelFoto * QUOTA_PREPONDERANTE) tenuti += i }

        for (z in zone) {
            // Punti del cartellino: il centro e 8 punti a metà raggio
            val punti = listOf(z.x to z.y) + (0 until 8).map { k ->
                val a = k * Math.PI / 4
                (z.x + z.raggio / 2 * Math.cos(a).toFloat()) to (z.y + z.raggio / 2 * Math.sin(a).toFloat())
            }
            val conCartellino = oggetti.indices.filter { i -> punti.any { (px, py) -> oggetti[i].contiene(px.toInt(), py.toInt()) } }
            if (conCartellino.isNotEmpty()) {
                tenuti += conCartellino
            } else {
                // Cartellino appoggiato accanto all'oggetto (es. sul tavolo vicino alle scarpe): l'oggetto più vicino
                oggetti.indices.minByOrNull { oggetti[it].distanzaDa(z.x, z.y) }
                    ?.takeIf { oggetti[it].distanzaDa(z.x, z.y) < 3 * z.raggio }
                    ?.let { tenuti += it }
            }
        }
        return tenuti
    }

    /** Lato dei "quadrettoni" della pixelatura: grandi, circa 1/40 del lato lungo della foto. */
    fun latoQuadretto(larghezza: Int, altezza: Int) = max(8, max(larghezza, altezza) / 40)

    /** Raggio della zona nitida attorno a un cartellino, dalla larghezza della riga del codice. */
    fun raggioDaCodice(larghezzaCodice: Float) = 4.5f * larghezzaCodice

    /** Raggio della zona nitida attorno a un cartellino, dall'altezza del prezzo. */
    fun raggioDaPrezzo(altezzaPrezzo: Float) = 8f * altezzaPrezzo
}
