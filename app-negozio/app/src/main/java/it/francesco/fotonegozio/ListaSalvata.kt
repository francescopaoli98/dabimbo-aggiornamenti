package it.francesco.fotonegozio

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * La lista delle foto salvata sul telefono (file di testo JSON), così se l'app si chiude
 * Elisa la ritrova com'era: articoli, testi corretti, foto girate o pixelate, già pubblicate.
 * Le miniature non si salvano: si rifanno dalle foto.
 */
object ListaSalvata {

    fun scrivi(foto: List<Foto>): String = JSONArray().apply { foto.forEach { put(json(it)) } }.toString()

    fun leggi(testo: String): List<Foto> = runCatching {
        val a = JSONArray(testo)
        List(a.length()) { foto(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun json(f: Foto) = JSONObject().apply {
        put("numero", f.numero)
        put("origine", f.origine.toString())
        put("inCorso", f.inCorso)
        put("fileAuto", f.fileAuto?.path)
        put("file", f.file?.path)
        put("rotazione", f.rotazione)
        put("messaInVerticale", f.messaInVerticale)
        put("versoVerticale", f.versoVerticale)
        put("rotazioneManuale", f.rotazioneManuale)
        put("daControllare", f.daControllare)
        put("codice", f.codice)
        put("dati", f.dati?.let(::json))
        put("altri", JSONArray().apply { f.altri.forEach { put(json(it)) } })
        put("metodo", f.metodo)
        put("secondi", f.secondi.toDouble())
        put("errore", f.errore)
        put("testoManuale", f.testoManuale)
        put("etichetteViste", f.etichetteViste)
        put("pubblicata", f.pubblicata)
    }

    private fun json(d: DatiCartellino) = JSONObject().apply {
        put("codice", d.codice); put("descrizione", d.descrizione); put("prezzo", d.prezzo); put("taglia", d.taglia)
    }

    private fun foto(o: JSONObject) = Foto(
        numero = o.getInt("numero"),
        origine = Uri.parse(o.getString("origine")),
        inCorso = o.optBoolean("inCorso"),
        fileAuto = o.testo("fileAuto")?.let(::File),
        file = o.testo("file")?.let(::File),
        rotazione = o.optInt("rotazione"),
        messaInVerticale = o.optBoolean("messaInVerticale"),
        versoVerticale = o.optInt("versoVerticale"),
        rotazioneManuale = o.optInt("rotazioneManuale"),
        daControllare = o.optBoolean("daControllare"),
        codice = o.testo("codice"),
        dati = o.optJSONObject("dati")?.let(::dati),
        altri = o.optJSONArray("altri")?.let { a -> List(a.length()) { dati(a.getJSONObject(it)) } }.orEmpty(),
        metodo = o.optString("metodo"),
        secondi = o.optDouble("secondi", 0.0).toFloat(),
        errore = o.testo("errore"),
        testoManuale = o.testo("testoManuale"),
        etichetteViste = o.optInt("etichetteViste"),
        pubblicata = o.optBoolean("pubblicata"),
    )

    private fun dati(o: JSONObject) = DatiCartellino(o.testo("codice"), o.testo("descrizione"), o.testo("prezzo"), o.testo("taglia"))

    private fun JSONObject.testo(chiave: String): String? = if (has(chiave) && !isNull(chiave)) getString(chiave) else null
}
