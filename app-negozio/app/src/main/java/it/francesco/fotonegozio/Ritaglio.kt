package it.francesco.fotonegozio

/** Un rettangolo: sinistra, alto, destra, basso. */
data class Riquadro(val l: Float, val t: Float, val r: Float, val b: Float) {
    val larghezza get() = r - l
    val altezza get() = b - t
}

/** Cosa si sta trascinando nel ritaglio: tutto il riquadro o uno dei 4 angoli. */
enum class Maniglia { DENTRO, ALTO_SX, ALTO_DX, BASSO_SX, BASSO_DX }

/** Un formato pronto: nome e rapporto larghezza/altezza (null = libero). */
data class Formato(val nome: String, val rapporto: Float?)

/**
 * Ritaglio della foto. Kotlin puro: testabile sul PC.
 * Il ritaglio si salva sulla foto di base in proporzione (0..1), così resta giusto anche se poi Elisa la gira.
 */
object Ritaglio {

    val FORMATI = listOf(
        Formato("Libero", null),
        Formato("1:1", 1f),
        Formato("4:5", 4f / 5f),
        Formato("3:4", 3f / 4f),
        Formato("9:16", 9f / 16f),   // lo stato di WhatsApp a tutto schermo
    )

    /** Il riquadro più grande con quel rapporto, al centro di una foto [w]×[h] (rapporto null = tutta la foto). */
    fun formato(rapporto: Float?, w: Float, h: Float): Riquadro {
        if (rapporto == null) return Riquadro(0f, 0f, w, h)
        val lw = minOf(w, h * rapporto)
        val lh = lw / rapporto
        return Riquadro((w - lw) / 2, (h - lh) / 2, (w + lw) / 2, (h + lh) / 2)
    }

    /** Quale maniglia c'è vicino al dito ([tolleranza] in pixel), null se il dito è fuori. */
    fun maniglia(q: Riquadro, x: Float, y: Float, tolleranza: Float): Maniglia? {
        val angoli = listOf(
            Maniglia.ALTO_SX to (q.l to q.t), Maniglia.ALTO_DX to (q.r to q.t),
            Maniglia.BASSO_SX to (q.l to q.b), Maniglia.BASSO_DX to (q.r to q.b),
        )
        val vicino = angoli.minBy { (_, p) -> (p.first - x) * (p.first - x) + (p.second - y) * (p.second - y) }
        val (px, py) = vicino.second
        if ((px - x) * (px - x) + (py - y) * (py - y) <= tolleranza * tolleranza) return vicino.first
        return if (x in q.l..q.r && y in q.t..q.b) Maniglia.DENTRO else null
    }

    /**
     * Sposta il riquadro (DENTRO) o un angolo di ([dx], [dy]), restando dentro la foto [w]×[h].
     * Con un [rapporto] fisso l'angolo allarga/stringe mantenendo la forma. [minimo] = lato più piccolo.
     */
    fun trascina(q: Riquadro, m: Maniglia, dx: Float, dy: Float, w: Float, h: Float, rapporto: Float?, minimo: Float): Riquadro {
        if (m == Maniglia.DENTRO) {
            val mx = dx.coerceIn(-q.l, w - q.r)
            val my = dy.coerceIn(-q.t, h - q.b)
            return Riquadro(q.l + mx, q.t + my, q.r + mx, q.b + my)
        }
        // L'angolo opposto resta fermo
        val destra = m == Maniglia.ALTO_DX || m == Maniglia.BASSO_DX
        val basso = m == Maniglia.BASSO_SX || m == Maniglia.BASSO_DX
        val ax = if (destra) q.l else q.r
        val ay = if (basso) q.t else q.b
        val px = (if (destra) q.r else q.l) + dx
        val py = (if (basso) q.b else q.t) + dy
        val maxW = if (destra) w - ax else ax
        val maxH = if (basso) h - ay else ay
        var lw = (if (destra) px - ax else ax - px)
        var lh = (if (basso) py - ay else ay - py)
        if (rapporto == null) {
            lw = lw.coerceIn(minOf(minimo, maxW), maxW)
            lh = lh.coerceIn(minOf(minimo, maxH), maxH)
        } else {
            // Vince il lato tirato di più; poi la forma resta quella del formato
            lw = maxOf(lw, lh * rapporto)
            val tetto = minOf(maxW, maxH * rapporto)
            lw = lw.coerceIn(minOf(minimo * maxOf(1f, rapporto), tetto), tetto)
            lh = lw / rapporto
        }
        val l = if (destra) ax else ax - lw
        val t = if (basso) ay else ay - lh
        return Riquadro(l, t, l + lw, t + lh)
    }

    /** Da riquadro sulla foto vista (in proporzione 0..1, girata di [gradi] in senso orario) alla foto di base. */
    fun versoBase(q: Riquadro, gradi: Int): Riquadro = ordina(
        PixelManuale.versoBase(q.l, q.t, gradi, 1, 1), PixelManuale.versoBase(q.r, q.b, gradi, 1, 1),
    )

    /** Dalla foto di base (0..1) alla foto vista, girata di [gradi]. */
    fun daBase(q: Riquadro, gradi: Int): Riquadro = ordina(
        PixelManuale.daBase(q.l, q.t, gradi, 1, 1), PixelManuale.daBase(q.r, q.b, gradi, 1, 1),
    )

    private fun ordina(a: Pair<Float, Float>, b: Pair<Float, Float>) =
        Riquadro(minOf(a.first, b.first), minOf(a.second, b.second), maxOf(a.first, b.first), maxOf(a.second, b.second))

    /** Il riquadro in proporzione (0..1) diventa pixel veri su una foto [w]×[h]: sinistra, alto, larghezza, altezza. */
    fun pixel(q: Riquadro, w: Int, h: Int): IntArray {
        val l = (q.l * w).toInt().coerceIn(0, w - 1)
        val t = (q.t * h).toInt().coerceIn(0, h - 1)
        val r = (q.r * w + 0.5f).toInt().coerceIn(l + 1, w)
        val b = (q.b * h + 0.5f).toInt().coerceIn(t + 1, h)
        return intArrayOf(l, t, r - l, b - t)
    }

    /** "tutta la foto" (o quasi): non serve ritagliare. */
    fun tutta(q: Riquadro) = q.l <= 0.002f && q.t <= 0.002f && q.r >= 0.998f && q.b >= 0.998f

    fun scrivi(q: Riquadro) = "${q.l},${q.t},${q.r},${q.b}"
    fun leggi(s: String?): Riquadro? = s?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }?.let { Riquadro(it[0], it[1], it[2], it[3]) }
}
