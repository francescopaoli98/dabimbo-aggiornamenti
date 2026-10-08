package it.francesco.fotonegozio

import org.json.JSONArray
import org.json.JSONObject

/**
 * Un articolo pubblicato: giorno (aaaa-mm-gg), codice (o descrizione se manca) e prezzo in centesimi.
 * [miniatura] = nome del file con la fotina (in filesDir/storico); [prenotato]/[venduto] li spunta Elisa.
 * [foto] = copia in alta qualità (in filesDir/storico_hd, tenuta 30 giorni) per Instagram; [taglia] già per esteso ("8 anni").
 */
data class Pubblicato(
    val giorno: String,
    val chiave: String,
    val centesimi: Int,
    val nome: String = "",
    val miniatura: String = "",
    val prenotato: Boolean = false,
    val venduto: Boolean = false,
    val foto: String = "",
    val taglia: String = "",
    // Dove è stato caricato (il segno si mette da solo, Elisa lo può togliere/rimettere a mano)
    val whatsapp: Boolean = true,
    val storiaIG: Boolean = false,
    val postIG: Boolean = false,
) {
    fun su(c: Canale) = when (c) { Canale.WHATSAPP -> whatsapp; Canale.STORIA_IG -> storiaIG; Canale.POST_IG -> postIG }
    fun con(c: Canale, si: Boolean) = when (c) {
        Canale.WHATSAPP -> copy(whatsapp = si); Canale.STORIA_IG -> copy(storiaIG = si); Canale.POST_IG -> copy(postIG = si)
    }
}

/** Dove si carica un articolo. */
enum class Canale(val simbolo: String, val nome: String) {
    WHATSAPP("🟢", "WhatsApp"), STORIA_IG("📱", "Storia IG"), POST_IG("▦", "Post IG")
}

/** Totale di un giorno: quanti articoli e quanto valgono, e quanti di questi sono prenotati/venduti. */
data class Giornata(
    val giorno: String,
    val articoli: Int,
    val centesimi: Int,
    val prenotati: Int = 0,
    val centesimiPrenotati: Int = 0,
    val venduti: Int = 0,
    val centesimiVenduti: Int = 0,
    val suWhatsapp: Int = 0,
    val suStorieIG: Int = 0,
    val suPostIG: Int = 0,
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
    fun aggiungi(
        registro: List<Pubblicato>, giorno: String, articoli: List<DatiCartellino>, miniatura: String = "", foto: String = "",
        canale: Canale = Canale.WHATSAPP,   // dove è stato caricato: gli articoli già contati oggi prendono il segno
    ): List<Pubblicato> {
        val nuovi = articoli.mapNotNull { a ->
            val chiave = a.codice ?: a.descrizione ?: return@mapNotNull null
            Pubblicato(giorno, chiave, centesimi(a.prezzo) ?: 0, a.descrizione.orEmpty(), miniatura, foto = foto, taglia = a.taglia.orEmpty(), whatsapp = false)
                .con(canale, true)
        }
        // Già contato oggi ma senza fotina/copia buona/taglia (es. pubblicato con la versione vecchia): ora le prende
        val conFotina = registro.map { r ->
            val n = if (r.giorno == giorno) nuovi.firstOrNull { it.chiave == r.chiave } else null
            if (n == null) r
            else r.copy(miniatura = r.miniatura.ifEmpty { miniatura }, foto = r.foto.ifEmpty { foto }, taglia = r.taglia.ifEmpty { n.taglia }).con(canale, true)
        }
        return conFotina + nuovi.filter { n -> registro.none { it.giorno == n.giorno && it.chiave == n.chiave } }.distinctBy { it.chiave }
    }

    /** I totali per giorno, dal più recente. */
    fun giornate(registro: List<Pubblicato>): List<Giornata> =
        registro.groupBy { it.giorno }.map { (g, l) ->
            val pren = l.filter { it.prenotato }
            val vend = l.filter { it.venduto }
            Giornata(
                g, l.size, l.sumOf { it.centesimi }, pren.size, pren.sumOf { it.centesimi }, vend.size, vend.sumOf { it.centesimi },
                l.count { it.whatsapp }, l.count { it.storiaIG }, l.count { it.postIG },
            )
        }.sortedByDescending { it.giorno }

    /** Cambia un articolo (lo stesso giorno + codice) lasciando gli altri come sono. */
    fun cambia(registro: List<Pubblicato>, p: Pubblicato, nuovo: (Pubblicato) -> Pubblicato): List<Pubblicato> =
        registro.map { if (it.giorno == p.giorno && it.chiave == p.chiave) nuovo(it) else it }

    /**
     * Mette o toglie il segno di un canale. Non cancella mai l'articolo, anche senza segni:
     * per toglierlo dal conteggio c'è la ✕ (con "Sblocca cancellazione").
     */
    fun segnaCanale(registro: List<Pubblicato>, p: Pubblicato, c: Canale, si: Boolean): List<Pubblicato> =
        cambia(registro, p) { it.con(c, si) }

    /** 8450 → "€ 84,50" */
    fun euro(centesimi: Int) = "€ ${centesimi / 100},${(centesimi % 100).toString().padStart(2, '0')}"

    fun scrivi(registro: List<Pubblicato>): String = JSONArray().apply {
        registro.forEach {
            put(JSONObject().put("g", it.giorno).put("k", it.chiave).put("c", it.centesimi).put("n", it.nome).apply {
                if (it.miniatura.isNotEmpty()) put("m", it.miniatura)
                if (it.prenotato) put("p", true)
                if (it.venduto) put("v", true)
                if (it.foto.isNotEmpty()) put("f", it.foto)
                if (it.taglia.isNotEmpty()) put("t", it.taglia)
                if (!it.whatsapp) put("w", false)
                if (it.storiaIG) put("is", true)
                if (it.postIG) put("ip", true)
            })
        }
    }.toString()

    fun leggi(testo: String): List<Pubblicato> = runCatching {
        val a = JSONArray(testo)
        List(a.length()) {
            val o = a.getJSONObject(it)
            Pubblicato(o.getString("g"), o.getString("k"), o.optInt("c"), o.optString("n"), o.optString("m"), o.optBoolean("p"), o.optBoolean("v"), o.optString("f"), o.optString("t"),
                o.optBoolean("w", true), o.optBoolean("is"), o.optBoolean("ip"))
        }
    }.getOrDefault(emptyList())
}
