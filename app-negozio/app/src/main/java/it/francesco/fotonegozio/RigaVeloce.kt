package it.francesco.fotonegozio

/**
 * Capisce un articolo scritto (o dettato) in una sola riga, in qualsiasi ordine:
 *   "1444115 librottino inside out 1,50"
 *   "felpa zip capp okaidi rosa 8A 4,00 1444496"
 *
 * - codice      = numero di 7 cifre
 * - prezzo      = numero con la virgola/punto o col simbolo € / la parola "euro"
 * - taglia      = anni o mesi (8A, 7/8A, 18M) o numero di scarpa (NR 34, TG 31)
 * - descrizione = tutto il resto
 *
 * Kotlin puro: si può testare sul PC.
 */
object RigaVeloce {

    private val CODICE = Regex("(?<!\\d)\\d{7}(?!\\d)")

    // Prezzo: "€ 3", "3 €", "3 euro", "2,50", "2.5", "€2,50"
    private val PREZZO = Regex(
        "(?i)€\\s?(\\d{1,3})(?:[,.](\\d{1,2}))?(?!\\d)" +                // € 3 · €2,50
            "|(?<![\\d/])(\\d{1,3})(?:[,.](\\d{1,2}))?\\s?(?:€|euro\\b)" +  // 3 € · 2,50 euro
            "|(?<![\\d/,.])(\\d{1,3})[,.](\\d{1,2})(?![\\d/])"                // 2,50
    )

    // Taglia: anni/mesi (8A, 7/8A, 18M, 8 anni, 18 mesi) o scarpe (NR 34, TG 31, N. 28)
    private val TAGLIA = Regex(
        "(?i)(?<![\\w/])(\\d{1,2}(?:/\\d{1,2})?\\s?(?:a|anni|m|mesi))(?![\\w])" +
            "|(?i)(?<!\\w)((?:nr|tg|n\\.)\\s?\\d{2})(?!\\d)"
    )

    fun analizza(riga: String): DatiCartellino {
        var resto = " ${riga.trim()} "

        // 1. Codice
        val codice = CODICE.find(resto)?.value
        if (codice != null) resto = resto.replaceFirst(codice, " ")

        // 2. Prezzo (prima della taglia, così "2,50" non viene preso per altro)
        var prezzo: String? = null
        PREZZO.find(resto)?.let { m ->
            val g = m.groupValues
            val (euro, cent) = when {
                g[1].isNotEmpty() -> g[1] to g[2]
                g[3].isNotEmpty() -> g[3] to g[4]
                else -> g[5] to g[6]
            }
            prezzo = "€ $euro,${cent.padEnd(2, '0')}"
            resto = resto.removeRange(m.range).let { " $it " }
        }

        // 3. Taglia: l'ultima scritta da taglia (sui cartellini la taglia viene di solito alla fine)
        var taglia: String? = null
        TAGLIA.findAll(resto).lastOrNull()?.let { m ->
            taglia = m.value.trim().uppercase()
                .replace(Regex("\\s?ANNI$"), "A").replace(Regex("\\s?MESI$"), "M")
                .replace(Regex("^(NR|TG|N\\.)\\s?"), "NR ")
                .replace(Regex("(\\d)\\s(A|M)$"), "$1$2")
            resto = resto.removeRange(m.range).let { " $it " }
        }

        // 4. Descrizione: quello che resta, spazi sistemati
        val descrizione = resto.replace(Regex("\\s+"), " ").trim().trim(',', '-', ';').trim().ifBlank { null }
        return DatiCartellino(codice, descrizione, prezzo, taglia)
    }
}
