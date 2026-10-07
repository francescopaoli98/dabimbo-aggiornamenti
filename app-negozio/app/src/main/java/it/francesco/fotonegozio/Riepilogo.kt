package it.francesco.fotonegozio

import org.json.JSONArray
import org.json.JSONObject

/**
 * Un articolo pubblicato: giorno (aaaa-mm-gg), codice (o descrizione se manca) e prezzo in centesimi.
 * [miniatura] = nome del file con la fotina (in filesDir/storico); [prenotato]/[venduto] li spunta Elisa.
 */
data class Pubblicato(
    val giorno: String,
    val chiave: String,
    val centesimi: Int,
    val nome: String = "",
    val miniatura: String = "",
    val prenotato: Boolean = false,
    val venduto: Boolean = false,
)

/** Totale di un giorno: quanti articoli e quanto valgono, e quanti di questi sono prenotati/venduti. */
data class Giornata(
    val giorno: String,
    val articoli: Int,
    val centesimi: Int,
    val prenotati: Int = 0,
    val centesimiPrenotati: Int = 0,
    val venduti: Int = 0,
    val centesimiVenduti: Int = 0,
)

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
    fun aggiungi(registro: List<Pubblicato>, giorno: String, articoli: List<DatiCartellino>, miniatura: String = ""): List<Pubblicato> {
        val nuovi = articoli.mapNotNull { a ->
            val chiave = a.codice ?: a.descrizione ?: return@mapNotNull null
            Pubblicato(giorno, chiave, centesimi(a.prezzo) ?: 0, a.descrizione.orEmpty(), miniatura)
        }.filter { n -> registro.none { it.giorno == n.giorno && it.chiave == n.chiave } }
            .distinctBy { it.chiave }
        return registro + nuovi
    }

    /** I totali per giorno, dal più recente. */
    fun giornate(registro: List<Pubblicato>): List<Giornata> =
        registro.groupBy { it.giorno }.map { (g, l) ->
            val pren = l.filter { it.prenotato }
            val vend = l.filter { it.venduto }
            Giornata(g, l.size, l.sumOf { it.centesimi }, pren.size, pren.sumOf { it.centesimi }, vend.size, vend.sumOf { it.centesimi })
        }.sortedByDescending { it.giorno }

    /** Cambia un articolo (lo stesso giorno + codice) lasciando gli altri come sono. */
    fun cambia(registro: List<Pubblicato>, p: Pubblicato, nuovo: (Pubblicato) -> Pubblicato): List<Pubblicato> =
        registro.map { if (it.giorno == p.giorno && it.chiave == p.chiave) nuovo(it) else it }

    /** 8450 → "€ 84,50" */
    fun euro(centesimi: Int) = "€ ${centesimi / 100},${(centesimi % 100).toString().padStart(2, '0')}"

    fun scrivi(registro: List<Pubblicato>): String = JSONArray().apply {
        registro.forEach {
            put(JSONObject().put("g", it.giorno).put("k", it.chiave).put("c", it.centesimi).put("n", it.nome).apply {
                if (it.miniatura.isNotEmpty()) put("m", it.miniatura)
                if (it.prenotato) put("p", true)
                if (it.venduto) put("v", true)
            })
        }
    }.toString()

    fun leggi(testo: String): List<Pubblicato> = runCatching {
        val a = JSONArray(testo)
        List(a.length()) {
            val o = a.getJSONObject(it)
            Pubblicato(o.getString("g"), o.getString("k"), o.optInt("c"), o.optString("n"), o.optString("m"), o.optBoolean("p"), o.optBoolean("v"))
        }
    }.getOrDefault(emptyList())
}
