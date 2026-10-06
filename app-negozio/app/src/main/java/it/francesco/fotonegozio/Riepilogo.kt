package it.francesco.fotonegozio

import org.json.JSONArray
import org.json.JSONObject

/** Un articolo pubblicato: giorno (aaaa-mm-gg), codice (o descrizione se manca) e prezzo in centesimi. */
data class Pubblicato(val giorno: String, val chiave: String, val centesimi: Int, val nome: String = "")

/** Totale di un giorno: quanti articoli e quanto valgono. */
data class Giornata(val giorno: String, val articoli: Int, val centesimi: Int)

/**
 * Riepilogo delle pubblicazioni ("Oggi: 31 articoli · € 84,50").
 * Lo stesso articolo pubblicato due volte nello stesso giorno conta una volta sola.
 * Kotlin puro (+ org.json): testabile sul PC.
 */
object Riepilogo {

    /** "€ 4,00" → 400; "€ 12,5" → 1250; null se non è un prezzo. */
    fun centesimi(prezzo: String?): Int? {
        val m = Regex("(\\d+)(?:[,.](\\d{1,2}))?").find(prezzo ?: return null) ?: return null
        val euro = m.groupValues[1].toIntOrNull() ?: return null
        val cent = m.groupValues[2].padEnd(2, '0').toIntOrNull() ?: 0
        return euro * 100 + cent
    }

    /** Aggiunge al registro gli articoli di una foto pubblicata oggi (senza doppioni nello stesso giorno). */
    fun aggiungi(registro: List<Pubblicato>, giorno: String, articoli: List<DatiCartellino>): List<Pubblicato> {
        val nuovi = articoli.mapNotNull { a ->
            val chiave = a.codice ?: a.descrizione ?: return@mapNotNull null
            Pubblicato(giorno, chiave, centesimi(a.prezzo) ?: 0, a.descrizione.orEmpty())
        }.filter { n -> registro.none { it.giorno == n.giorno && it.chiave == n.chiave } }
            .distinctBy { it.chiave }
        return registro + nuovi
    }

    /** I totali per giorno, dal più recente. */
    fun giornate(registro: List<Pubblicato>): List<Giornata> =
        registro.groupBy { it.giorno }.map { (g, l) -> Giornata(g, l.size, l.sumOf { it.centesimi }) }.sortedByDescending { it.giorno }

    /** 8450 → "€ 84,50" */
    fun euro(centesimi: Int) = "€ ${centesimi / 100},${(centesimi % 100).toString().padStart(2, '0')}"

    fun scrivi(registro: List<Pubblicato>): String = JSONArray().apply {
        registro.forEach { put(JSONObject().put("g", it.giorno).put("k", it.chiave).put("c", it.centesimi).put("n", it.nome)) }
    }.toString()

    fun leggi(testo: String): List<Pubblicato> = runCatching {
        val a = JSONArray(testo)
        List(a.length()) { a.getJSONObject(it).let { o -> Pubblicato(o.getString("g"), o.getString("k"), o.optInt("c"), o.optString("n")) } }
    }.getOrDefault(emptyList())
}
