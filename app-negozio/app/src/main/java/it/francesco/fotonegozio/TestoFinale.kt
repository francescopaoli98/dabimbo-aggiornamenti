package it.francesco.fotonegozio

/**
 * Compone il testo per lo stato WhatsApp partendo dai dati del cartellino e dal dizionario:
 *   "FELPA ZIP CAPP OKAIDI 8A RS MARGH FELP 8A" + taglia 8A + € 4,00 + 1444496
 *   → "Felpa zip con cappuccio Okaidi rosa margherite felpata - 8 anni - cod. 1444496 - € 4,00"
 * Ordine scelto da Elisa: descrizione - taglia - codice - prezzo
 *
 * Kotlin puro: testabile sul PC.
 */
object TestoFinale {

    /** Genere e numero del capo, per accordare i colori: 0 m.sing, 1 f.sing, 2 m.plur, 3 f.plur. */
    private enum class Forma(val indice: Int, val plurale: Boolean) { MS(0, false), FS(1, false), MP(2, true), FP(3, true) }

    private val ROMANI = setOf("II", "III", "IV")

    // Parole che finiscono in -a ma sono maschili
    private val MASCHILI_IN_A = setOf("coprispalla", "pigiama", "poncho", "set", "gioco", "puzzle")

    // Scritte da taglia dentro la descrizione: 8A, 7/8A, 18M, 3 anni, NR 34
    private val TAGLIA_IN_DESCRIZIONE = Regex("(?i)^\\d{1,2}(/\\d{1,2})?(a|m|anni|mesi)$")
    private val CALZATURE = listOf("scarp", "stival", "sandal", "ciabatt", "pantofol", "sneaker")

    /** Una riga di testo per un articolo. */
    fun riga(d: DatiCartellino, voci: List<VoceDizionario>): String {
        val taglia = d.taglia
        val descrizione = d.descrizione?.let { espandi(it, taglia, voci) }
        val parti = listOfNotNull(
            descrizione?.ifBlank { null },
            tagliaPerEsteso(taglia, descrizione),
            d.codice?.let { "cod. $it" },
            d.prezzo,
        )
        return parti.joinToString(" - ")
    }

    /**
     * Tutte le righe di una foto (una per articolo).
     * Collage con lo stesso articolo più volte: una riga sola.
     * Stesso codice = stesso articolo: tengo la riga più completa (es. una etichetta letta male, l'altra bene).
     */
    fun testo(articoli: List<DatiCartellino>, voci: List<VoceDizionario>): String {
        val righe = mutableListOf<String>()
        val posizioneCodice = mutableMapOf<String, Int>()
        for (d in articoli) {
            val r = riga(d, voci)
            if (r.isBlank()) continue
            val gia = d.codice?.let { posizioneCodice[it] }
            when {
                gia != null -> if (r.length > righe[gia].length) righe[gia] = r
                r in righe -> {}
                else -> {
                    d.codice?.let { posizioneCodice[it] = righe.size }
                    righe += r
                }
            }
        }
        return righe.joinToString("\n")
    }

    /** Espande le sigle, accorda i colori, toglie taglie ripetute e marchi esclusi. */
    fun espandi(descrizione: String, taglia: String?, voci: List<VoceDizionario>): String {
        val perSigla = voci.associateBy { it.sigla.lowercase() }
        val tagliaPulita = taglia?.lowercase()?.replace(" ", "")
        val parole = descrizione.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()

        // 1. Via le scritte da taglia (la taglia va nel suo pezzo di testo) e i "NR 34" ripetuti
        val tenute = mutableListOf<String>()
        var i = 0
        while (i < parole.size) {
            val p = parole[i]
            val pl = p.lowercase()
            val prossima = parole.getOrNull(i + 1)
            when {
                TAGLIA_IN_DESCRIZIONE.matches(p) -> i++
                pl == "nr" && prossima?.all(Char::isDigit) == true -> i += 2
                tagliaPulita != null && pl == tagliaPulita -> i++                   // es. scarpe "31 31"
                else -> { tenute += p; i++ }
            }
        }

        // 2. Genere e numero del capo (prima parola, espansa se è una sigla)
        val primaParola = tenute.firstOrNull()?.let { perSigla[it.lowercase()]?.significati?.firstOrNull() ?: it }?.lowercase().orEmpty()
        val forma = formaDi(primaParola)

        // 3. Ogni parola: sigla → significato accordato; marchi esclusi tolti; il resto in minuscolo
        val espanse = tenute.mapNotNull { p ->
            val voce = perSigla[p.lowercase()]
            when {
                voce == null && p.uppercase() in ROMANI -> p.uppercase()   // Frozen II
                voce == null -> p.lowercase()
                voce.daTogliere -> null
                voce.significati.size >= 4 -> voce.significati[forma.indice]
                voce.significati.size == 2 -> voce.significati[if (forma.plurale) 1 else 0]
                else -> voce.significati[0]
            }
        }

        // 4. Niente parole uguali una dopo l'altra, prima lettera maiuscola
        val senzaDoppioni = espanse.filterIndexed { j, p -> j == 0 || !p.equals(espanse[j - 1], ignoreCase = true) }
        return senzaDoppioni.joinToString(" ").replaceFirstChar { it.uppercase() }
    }

    private fun formaDi(parola: String): Forma = when {
        parola in MASCHILI_IN_A -> Forma.MS
        parola.endsWith("a") -> Forma.FS
        parola.endsWith("e") && parola.length > 3 -> Forma.FP   // scarpe, magliette, ...
        parola.endsWith("i") -> Forma.MP
        else -> Forma.MS
    }

    /** "8A" → "8 anni", "7/8A" → "7/8 anni", "18M" → "18 mesi", "NR 34" → "numero 34", "31" (scarpe) → "numero 31". */
    fun tagliaPerEsteso(taglia: String?, descrizione: String?): String? {
        val t = taglia?.trim()?.uppercase() ?: return null
        Regex("^(\\d{1,2}(?:/\\d{1,2})?)\\s?A(NNI)?$").find(t)?.let { return "${it.groupValues[1]} anni" }
        Regex("^(\\d{1,2}(?:/\\d{1,2})?)\\s?M(ESI)?$").find(t)?.let { return "${it.groupValues[1]} mesi" }
        Regex("^(?:NR|N\\.?|TG)\\s?(\\d{1,2})$").find(t)?.let { return "numero ${it.groupValues[1]}" }
        if (t.all(Char::isDigit)) {
            val scarpe = CALZATURE.any { descrizione?.lowercase()?.contains(it) == true }
            return if (scarpe) "numero $t" else "taglia $t"
        }
        return "taglia ${taglia.trim()}"
    }
}
