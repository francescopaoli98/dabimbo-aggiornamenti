package it.francesco.fotonegozio

/**
 * Il verso della foto deciso dall'OGGETTO, non dall'etichetta: si guardano le scritte sull'oggetto
 * (scatole dei giochi, copertine, magliette con scritte) e si tiene il verso in cui si leggono dritte.
 * Le scritte del cartellino si escludono: servono solo per codice, descrizione e prezzo.
 * Kotlin puro: testabile sul PC.
 */
object VersoOggetto {

    private val PREZZO_O_CODICI = Regex("€|\\d+[,.]\\d\\d|\\d{4,}|A\\d{3,}/", RegexOption.IGNORE_CASE)
    private val PAROLA = Regex("[\\p{L}]{3,}")

    /**
     * Quanto "pesa" una riga letta: le lettere delle parole vere (almeno 3 lettere),
     * per quanto ML Kit è sicuro della lettura. Le righe del cartellino non contano.
     * [paroleCartellino]: parole della descrizione sul cartellino (in minuscolo).
     */
    fun peso(testo: String, sicurezza: Float, paroleCartellino: Set<String>): Float {
        if (PREZZO_O_CODICI.containsMatchIn(testo)) return 0f
        if (sicurezza < 0.5f) return 0f   // lettura incerta: di solito scritte capovolte lette "a caso"
        val parole = PAROLA.findAll(testo).map { it.value.lowercase() }.toList()
        if (parole.isEmpty()) return 0f
        // Riga che ripete la descrizione del cartellino: è il cartellino
        if (paroleCartellino.isNotEmpty() && parole.count { it in paroleCartellino } * 2 >= parole.size) return 0f
        return parole.sumOf { it.length } * sicurezza
    }

    /**
     * Sceglie il verso: [voti] = per ogni rotazione (0/90/180/270, in senso orario) quante lettere
     * si leggono dritte. Serve un verso chiaramente migliore degli altri, altrimenti null (decide l'etichetta).
     */
    fun scegli(voti: Map<Int, Float>): Int? {
        val ordinati = voti.entries.sortedByDescending { it.value }
        val primo = ordinati.firstOrNull() ?: return null
        val secondo = ordinati.getOrNull(1)?.value ?: 0f
        return primo.key.takeIf { primo.value >= MINIMO && primo.value >= 2f * secondo }
    }

    fun paroleDi(descrizioni: List<String?>): Set<String> =
        descrizioni.filterNotNull().flatMap { d -> PAROLA.findAll(d).map { it.value.lowercase() } }.toSet()

    /** Lettere "sicure" necessarie per fidarsi delle scritte dell'oggetto (es. "SCARABEO" + "SOLE"). */
    const val MINIMO = 10f
}
