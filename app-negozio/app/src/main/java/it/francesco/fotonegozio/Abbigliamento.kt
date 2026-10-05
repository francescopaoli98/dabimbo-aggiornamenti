package it.francesco.fotonegozio

/**
 * Capisce dalla descrizione del cartellino se l'articolo è un capo d'abbigliamento.
 * Serve per la regola "foto in verticale": solo i capi (stesi sul tavolo) vengono girati;
 * giochi, libri, peluche e scarpe restano come sono stati fotografati.
 */
object Abbigliamento {

    // Parole che INIZIANO così (copre anche abbreviazioni e plurali: "giubb", "magliette", "pantal")
    private val INIZI = listOf(
        "felp", "magli", "giubb", "giacc", "cappott", "piumin", "pantal", "jeans", "legging",
        "gonn", "vestit", "abit", "coprispall", "camic", "canott", "tshirt", "t-shirt", "short",
        "salopett", "pigiam", "cardigan", "gilet", "bermud", "impermeab", "smanicat", "dolcevit",
        "tutin", "sciarp",
    )

    // Parole corte che devono essere ESATTAMENTE così (altrimenti "top" troverebbe anche "topolino")
    private val ESATTE = setOf("tuta", "tute", "polo", "top", "golf", "body", "pant", "kway")

    fun eUnCapo(descrizione: String?): Boolean {
        if (descrizione.isNullOrBlank()) return false
        val parole = descrizione.lowercase().split(Regex("[^a-zàèéìòù-]+")).filter { it.isNotBlank() }
        return parole.any { p -> p in ESATTE || INIZI.any { p.startsWith(it) } }
    }
}
