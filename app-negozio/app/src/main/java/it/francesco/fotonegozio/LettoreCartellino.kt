package it.francesco.fotonegozio

import kotlin.math.max
import kotlin.math.min

/** Una riga di testo letta da ML Kit, con la sua posizione (in pixel) sul cartellino già dritto. */
data class Riga(val testo: String, val sx: Int, val su: Int, val dx: Int, val giu: Int) {
    val altezza get() = giu - su
    val centroY get() = (su + giu) / 2
}

/** I dati letti dal cartellino. Campi null = non trovati. */
data class DatiCartellino(
    val codice: String?,
    val descrizione: String?,   // grezza, con le sigle (le espande il pezzo 3)
    val prezzo: String?,        // sempre nel formato "€ 4,00"
    val taglia: String?,        // es. "8A", "NR 34"; null per giochi e oggetti
)

/**
 * Capisce cosa c'è scritto sul cartellino Zebra guardando DOVE sta ogni riga:
 *
 *   ||||||||||||||||||  A2954/14444..   <- prefisso: ignorato
 *   FELPA ZIP CAPP OKAI 1444496         <- descrizione (riga 1, ALLA STESSA ALTEZZA del codice)
 *   DI 8A RS MARGH FELP 8A              <- descrizione (righe successive)
 *   € 4,00              8A              <- prezzo (grande, a sinistra) + taglia (a destra)
 *
 * È codice Kotlin puro (niente Android), così si può testare sul PC.
 */
object LettoreCartellino {

    private val CODICE = Regex("(?<!\\d)\\d{7}(?!\\d)")
    // Riga "A2954/1444496": lettera facoltativa + 2-5 cifre + "/" + cifre
    private val PREFISSO = Regex("^[A-Za-z]?\\s?\\d{2,5}\\s*/\\s*\\d+")
    // Prezzo: 1-3 cifre, virgola o punto, 2 cifre (es. 4,00 · 12.50)
    private val PREZZO = Regex("(?<![\\d/])(\\d{1,3})\\s?[,.]\\s?(\\d{2})(?!\\d)")
    // Simboli che l'OCR può leggere al posto di "€" o che non servono
    private val SIMBOLI_EURO = Regex("[€£]|\\bEUR\\b|^[Ee€Cc](?=\\s?\\d)")

    fun analizza(righe: List<Riga>): DatiCartellino {
        // 1. Codice: preferisco la riga fatta SOLO di 7 cifre (quella sotto il prefisso)
        val rigaCodice = righe.firstOrNull { it.testo.trim().replace(" ", "").matches(Regex("\\d{7}")) }
            ?: righe.firstOrNull { CODICE.containsMatchIn(it.testo) }
            ?: return DatiCartellino(null, null, null, null)
        val codice = CODICE.find(rigaCodice.testo)!!.value
        val h = max(rigaCodice.altezza, 1)
        val cw = rigaCodice.dx - rigaCodice.sx

        // 2. Righe utili: dall'altezza del codice in giù (la descrizione parte accanto al codice),
        //    dentro il cartellino, senza prefisso né numeri lunghi (cifre del codice a barre)
        val sotto = righe.filter { r ->
            r !== rigaCodice &&
                r.centroY > rigaCodice.su - h / 2 &&
                r.sx >= rigaCodice.sx - 5 * cw && r.dx <= rigaCodice.dx + 3 * cw / 2 &&
                r.su <= rigaCodice.giu + 10 * h &&
                !PREFISSO.containsMatchIn(r.testo.trim()) &&
                !r.testo.replace(" ", "").matches(Regex("\\d{5,}"))
        }

        // 3. Prezzo: tra le righe con "x,yy" la più alta (è scritto in grande)
        val rigaPrezzo = sotto.filter { PREZZO.containsMatchIn(it.testo) }.maxByOrNull { it.altezza }
        val prezzo = rigaPrezzo?.let { r -> PREZZO.find(r.testo)!!.let { "€ ${it.groupValues[1]},${it.groupValues[2]}" } }

        // 4. Taglia: a destra del prezzo, più o meno alla stessa altezza (o poco sotto)
        val taglia = rigaPrezzo?.let { trovaTaglia(it, sotto) }

        // 5. Descrizione: tutto quello che sta tra il codice e il prezzo, in ordine di lettura
        val limiteBasso = rigaPrezzo?.let { it.su + it.altezza / 3 } ?: Int.MAX_VALUE
        val righeDescrizione = sotto.filter { r ->
            r !== rigaPrezzo && r.centroY < limiteBasso && !(taglia != null && eTaglia(r, rigaPrezzo))
        }
        val descrizione = inOrdineDiLettura(righeDescrizione, h)
            .joinToString(" ") { it.testo.trim() }
            .replace(Regex("\\s+"), " ")
            .ifBlank { null }

        return DatiCartellino(codice, descrizione, prezzo, taglia)
    }

    /** La taglia è un testo corto a destra del prezzo; a volte ML Kit la attacca alla riga del prezzo. */
    private fun trovaTaglia(rigaPrezzo: Riga, righe: List<Riga>): String? {
        // Caso A: riga separata a destra del prezzo
        righe.filter { it !== rigaPrezzo && eTaglia(it, rigaPrezzo) }
            .sortedBy { it.sx }
            .joinToString(" ") { it.testo.trim() }
            .ifBlank { null }
            ?.let { return it }

        // Caso B: stessa riga, dopo il prezzo (es. "€4,00     8A")
        val m = PREZZO.find(rigaPrezzo.testo) ?: return null
        return rigaPrezzo.testo.substring(m.range.last + 1)
            .replace(SIMBOLI_EURO, "")
            .trim()
            .ifBlank { null }
    }

    /** Vero se [r] sta a destra del prezzo, tra la sua metà e poco sotto il suo fondo. */
    private fun eTaglia(r: Riga, prezzo: Riga?): Boolean {
        if (prezzo == null) return false
        val ph = max(prezzo.altezza, 1)
        return r.sx > prezzo.dx &&
            r.centroY > prezzo.su + ph / 4 &&
            r.centroY < prezzo.giu + ph
    }

    /** Ordina come si legge: per righe (dall'alto), e nella stessa riga da sinistra a destra. */
    private fun inOrdineDiLettura(righe: List<Riga>, altezzaTesto: Int): List<Riga> {
        val ordinate = righe.sortedBy { it.centroY }
        val gruppi = mutableListOf<MutableList<Riga>>()
        for (r in ordinate) {
            val ultimo = gruppi.lastOrNull()
            // Stessa riga se i centri distano meno di mezza altezza di testo
            if (ultimo != null && abs(r.centroY - ultimo.first().centroY) < altezzaTesto / 2 + 1) ultimo += r
            else gruppi += mutableListOf(r)
        }
        return gruppi.flatMap { g -> g.sortedBy { it.sx } }
    }

    private fun abs(x: Int) = if (x < 0) -x else x

    /**
     * Zona del cartellino nella foto dritta, stimata dalla riga del codice:
     * il codice sta in alto a destra, il prezzo in basso a sinistra.
     * Restituisce (sinistra, sopra, destra, sotto), già limitata alla foto.
     */
    fun zonaDaCodice(codice: Riga, larghezzaFoto: Int, altezzaFoto: Int): IntArray {
        val cw = codice.dx - codice.sx
        val ch = max(codice.altezza, 1)
        return intArrayOf(
            max(0, codice.sx - 5 * cw),
            max(0, codice.su - 6 * ch),
            min(larghezzaFoto, codice.dx + 3 * cw / 2),
            min(altezzaFoto, codice.giu + 10 * ch),
        )
    }
}
