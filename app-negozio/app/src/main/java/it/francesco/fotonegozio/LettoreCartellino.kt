package it.francesco.fotonegozio

import kotlin.math.max

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
 *   DI 8A RS MARGH FELP 8A      8A      <- descrizione + taglia (a destra, staccata: a volte qui...)
 *   € 4,00                      8A      <- prezzo (grande, a sinistra)   (...a volte qui)
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

        // 4. Ricostruisco le righe e le spezzo dove c'è un grande spazio vuoto.
        //    Taglia = pezzo di testo a DESTRA del prezzo, staccato, vicino al prezzo (anche un po' più in alto).
        //    Descrizione = tutto il resto sopra il prezzo (parte sempre da sinistra, parole attaccate).
        val ph = rigaPrezzo?.altezza ?: 0
        val candidati = sotto.filter { r ->
            r !== rigaPrezzo && (rigaPrezzo == null || r.centroY < rigaPrezzo.giu + ph)
        }
        val partiTaglia = mutableListOf<Riga>()
        val partiDescrizione = mutableListOf<Riga>()
        for (riga in raggruppaInRighe(candidati, h)) {
            for (pezzo in spezzaSuiVuoti(riga, 5 * h / 2)) {
                val cy = pezzo.sumOf { it.centroY } / pezzo.size
                val vicinoAlPrezzo = rigaPrezzo != null &&
                    pezzo.first().sx > rigaPrezzo.dx &&
                    cy > rigaPrezzo.su - ph && cy < rigaPrezzo.giu + ph
                when {
                    vicinoAlPrezzo -> partiTaglia += pezzo
                    rigaPrezzo == null || cy < rigaPrezzo.su + ph / 3 -> partiDescrizione += pezzo
                }
            }
        }
        val taglia = partiTaglia.unisci() ?: rigaPrezzo?.let { tagliaDopoIlPrezzo(it) }
        val descrizione = partiDescrizione.unisci()

        return DatiCartellino(codice, descrizione, prezzo, taglia)
    }

    private fun List<Riga>.unisci(): String? =
        joinToString(" ") { it.testo.trim() }.replace(Regex("\\s+"), " ").trim().ifBlank { null }

    /** Caso raro: ML Kit attacca la taglia al prezzo (es. "€4,00     8A"). */
    private fun tagliaDopoIlPrezzo(rigaPrezzo: Riga): String? {
        val m = PREZZO.find(rigaPrezzo.testo) ?: return null
        return rigaPrezzo.testo.substring(m.range.last + 1)
            .replace(SIMBOLI_EURO, "")
            .trim()
            .ifBlank { null }
    }

    /** Raggruppa le parole in righe (dall'alto in basso), ogni riga ordinata da sinistra a destra. */
    private fun raggruppaInRighe(parole: List<Riga>, altezzaTesto: Int): List<List<Riga>> {
        val gruppi = mutableListOf<MutableList<Riga>>()
        for (r in parole.sortedBy { it.centroY }) {
            val ultimo = gruppi.lastOrNull()
            // Stessa riga se i centri distano meno di mezza altezza di testo
            if (ultimo != null && abs(r.centroY - ultimo.first().centroY) < altezzaTesto / 2 + 1) ultimo += r
            else gruppi += mutableListOf(r)
        }
        return gruppi.map { g -> g.sortedBy { it.sx } }
    }

    /** Spezza una riga dove tra due parole c'è uno spazio più largo di [vuoto] pixel. */
    private fun spezzaSuiVuoti(riga: List<Riga>, vuoto: Int): List<List<Riga>> {
        val pezzi = mutableListOf(mutableListOf(riga.first()))
        for (r in riga.drop(1)) {
            if (r.sx - pezzi.last().last().dx > vuoto) pezzi += mutableListOf(r) else pezzi.last() += r
        }
        return pezzi
    }

    private fun abs(x: Int) = if (x < 0) -x else x
}
