package it.francesco.fotonegozio

/**
 * Una sigla del dizionario.
 * [significati]: 1 forma (sempre uguale), 2 forme (singolare/plurale) o 4 forme
 * (maschile / femminile / maschile plurale / femminile plurale). Vuoto = togli la parola dal testo.
 */
data class VoceDizionario(val sigla: String, val significati: List<String>) {
    val daTogliere get() = significati.isEmpty()
    /** Come si scrive nel file e si mostra a Elisa: "gri = grigio / grigia / grigi / grigie". */
    val significatoTesto get() = significati.joinToString(" / ")
}

/** Lettura e scrittura del file di testo del dizionario (Kotlin puro: testabile sul PC). */
object Dizionario {

    fun leggi(testo: String): List<VoceDizionario> =
        testo.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
            .map { riga ->
                val sigla = riga.substringBefore("=").trim().lowercase()
                val significati = riga.substringAfter("=").split("/").map { it.trim() }.filter { it.isNotEmpty() }
                VoceDizionario(sigla, significati)
            }
            .filter { it.sigla.isNotEmpty() }
            .distinctBy { it.sigla }

    fun scrivi(voci: List<VoceDizionario>): String = buildString {
        appendLine("# DIZIONARIO DELLE SIGLE - una riga per sigla:   sigla = significato")
        appendLine("# 4 forme: maschile / femminile / maschile plurale / femminile plurale - 2 forme: singolare / plurale")
        appendLine("# Niente dopo l'uguale = parola da togliere dal testo")
        voci.sortedBy { it.sigla }.forEach { appendLine("${it.sigla} = ${it.significatoTesto}") }
    }

    /**
     * Porta sul telefono le sigle nuove o corrette arrivate con un aggiornamento dell'app,
     * senza perdere quelle che Elisa ha scritto o corretto lei.
     * Cambia solo le sigle che nel dizionario di partenza sono diverse tra [vecchiaBase] e [nuovaBase].
     */
    fun aggiorna(telefono: List<VoceDizionario>, vecchiaBase: List<VoceDizionario>, nuovaBase: List<VoceDizionario>): List<VoceDizionario> {
        val prima = vecchiaBase.associateBy { it.sigla }
        val risultato = telefono.associateBy { it.sigla }.toMutableMap()
        for (v in nuovaBase) if (prima[v.sigla] != v) risultato[v.sigla] = v
        return risultato.values.sortedBy { it.sigla }
    }
}
