package it.francesco.fotonegozio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import java.io.File

/** Un articolo come si scrive su Instagram: nome, taglia ("8 anni"), codice e prezzo ("€ 4,00"). */
data class RigaIG(val nome: String, val taglia: String? = null, val codice: String? = null, val prezzo: String? = null)

/** Un carosello mandato a Instagram: il giorno e gli articoli di ogni foto, nell'ordine (per rifare il testo coi prenotati). */
data class CaroselloIG(val giorno: String, val foto: List<List<RigaIG>>)

/** Una foto da mandare a Instagram: il file (buona qualità) e gli articoli che ci sono dentro. */
data class ElementoIG(val id: String, val file: File, val righe: List<RigaIG>, val fotina: File? = null)

/** I testi per Instagram. Kotlin puro: testabile sul PC. */
object TestoInstagram {

    const val HASHTAG = "#dabimboabimbo #dabimboabimboprato #prato #usatobambini #negoziousatobambini"
    const val INIZIO = "Nuovi arrivi 🧸"
    const val FINE = "Scrivi nei commenti o in DM il numero che ti interessa 💬"

    /** Un articolo della lista (descrizione espansa col dizionario, taglia per esteso, come su WhatsApp). */
    fun daDati(d: DatiCartellino, voci: List<VoceDizionario>): RigaIG {
        val taglia = d.taglia ?: d.descrizione?.let(TestoFinale::tagliaNellaDescrizione)
        val nome = d.descrizione?.let { TestoFinale.espandi(it, taglia, voci) }.orEmpty()
        return RigaIG(nome.ifBlank { "Articolo" }, TestoFinale.tagliaPerEsteso(taglia, nome), d.codice, d.prezzo)
    }

    /** Un articolo dello storico. */
    fun daStorico(p: Pubblicato): RigaIG = RigaIG(
        p.nome.ifBlank { p.chiave.takeUnless { c -> c.all(Char::isDigit) } ?: "Articolo" },
        p.taglia.ifBlank { null },
        p.chiave.takeIf { c -> c.isNotEmpty() && c.all(Char::isDigit) },
        Riepilogo.euro(p.centesimi).takeIf { p.centesimi > 0 },
    )

    /** "Felpa rosa - 8 anni - cod. 1444496 - € 4,00" */
    fun riga(r: RigaIG) = listOfNotNull(r.nome, r.taglia, r.codice?.let { "cod. $it" }, r.prezzo).joinToString(" - ")

    /** "8 anni · cod. 1444496" (la riga piccola nella fascia delle storie). */
    fun dettagli(r: RigaIG) = listOfNotNull(r.taglia, r.codice?.let { "cod. $it" }).joinToString(" · ")

    /**
     * La didascalia del carosello: un numero per foto, nello stesso ordine delle foto
     * (più articoli nella stessa foto: sulla stessa riga, separati da " + ").
     */
    fun didascalia(
        foto: List<List<RigaIG>>, inizio: String = INIZIO, hashtag: String = HASHTAG,
        prenotati: Set<String> = emptySet(),   // codici prenotati: nella riga compare "PRENOTATO –"
        fine: String = FINE,                    // ultima riga ("Scrivi nei commenti…"), vuota = niente
    ): String = buildString {
        if (inizio.isNotBlank()) append(inizio.trim()).append("\n\n")
        foto.forEachIndexed { i, righe ->
            val testo = righe.joinToString(" + ") { r -> (if (r.codice != null && r.codice in prenotati) "PRENOTATO – " else "") + riga(r) }
            append(i + 1).append(". ").append(testo.ifEmpty { "—" }).append('\n')
        }
        if (fine.isNotBlank()) append('\n').append(fine.trim())
        if (hashtag.isNotBlank()) append("\n\n").append(hashtag.trim())
    }
}

/** I caroselli salvati (per poter rifare il testo quando un articolo viene prenotato). */
object CaroselliSalvati {
    fun scrivi(lista: List<CaroselloIG>): String = org.json.JSONArray().apply {
        lista.forEach { c ->
            put(org.json.JSONObject().put("g", c.giorno).put("f", org.json.JSONArray().apply {
                c.foto.forEach { righe ->
                    put(org.json.JSONArray().apply {
                        righe.forEach { r -> put(org.json.JSONObject().put("n", r.nome).put("t", r.taglia).put("c", r.codice).put("p", r.prezzo)) }
                    })
                }
            }))
        }
    }.toString()

    fun leggi(testo: String): List<CaroselloIG> = runCatching {
        val a = org.json.JSONArray(testo)
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val f = o.getJSONArray("f")
            CaroselloIG(o.getString("g"), List(f.length()) { j ->
                val r = f.getJSONArray(j)
                List(r.length()) { k ->
                    val x = r.getJSONObject(k)
                    fun t(c: String) = if (x.has(c) && !x.isNull(c)) x.getString(c) else null
                    RigaIG(x.getString("n"), t("t"), t("c"), t("p"))
                }
            })
        }
    }.getOrDefault(emptyList())
}

/** Le immagini per Instagram: storia 9:16 con la fascia sotto la foto, carosello 4:5 col numero nell'angolo. */
object ImmaginiInstagram {
    private val BLU = Color.rgb(30, 42, 120)
    private val ROSA = Color.rgb(232, 120, 160)
    private val GRIGIO = Color.rgb(90, 95, 120)

    /** La foto da [file], grande abbastanza per [lato] (senza leggere più pixel del necessario). */
    fun carica(file: File, lato: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0) return null
        var passo = 1
        while (minOf(o.outWidth, o.outHeight) / (passo * 2) >= lato) passo *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = passo })
    }

    /** Sfondo: la foto che copre tutto, molto sfocata e schiarita (si fa rimpicciolendo e ingrandendo). */
    private fun sfondo(c: Canvas, foto: Bitmap, w: Int, h: Int) {
        val k = maxOf(w / foto.width.toFloat(), h / foto.height.toFloat())
        val sw = (w / k).toInt().coerceIn(1, foto.width); val sh = (h / k).toInt().coerceIn(1, foto.height)
        val pezzo = Rect((foto.width - sw) / 2, (foto.height - sh) / 2, (foto.width + sw) / 2, (foto.height + sh) / 2)
        val piccola = Bitmap.createBitmap(27, 48, Bitmap.Config.ARGB_8888)
        Canvas(piccola).drawBitmap(foto, pezzo, Rect(0, 0, 27, 48), Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawBitmap(piccola, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawColor(Color.argb(70, 255, 255, 255))
    }

    private fun testo(dim: Float, colore: Int, grassetto: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dim; color = colore; typeface = if (grassetto) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun blocco(t: String, p: TextPaint, larghezza: Int, righe: Int): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, p, larghezza.coerceAtLeast(50))
            .setMaxLines(righe).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

    /** Storia 1080×1920: la foto intera in alto, sotto la fascia bianca con descrizione, taglia, codice e prezzo. */
    fun storia(foto: Bitmap, righe: List<RigaIG>): Bitmap {
        val w = 1080; val h = 1920
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        sfondo(c, foto, w, h)

        // La fascia: prima la misuro (dipende da quanti articoli e da quanto è lunga la descrizione)
        val tante = righe.size > 1
        val pNome = testo(if (tante) 44f else 50f, BLU, true)
        val pInfo = testo(if (tante) 34f else 38f, GRIGIO, false)
        val pPrezzo = testo(if (tante) 64f else 84f, ROSA, true)
        val x0 = 40f; val x1 = w - 40f
        val larghPrezzo = righe.maxOfOrNull { r -> r.prezzo?.let { pPrezzo.measureText(it) } ?: 0f } ?: 0f
        val larghTesto = (x1 - x0 - 50 - 40 - larghPrezzo - 30).toInt()
        val pezzi = righe.take(4).map { r -> blocco(r.nome, pNome, larghTesto, if (tante) 2 else 3) to r }
        val altezze = pezzi.map { (l, r) -> l.height + (if (TestoInstagram.dettagli(r).isNotEmpty()) (pInfo.textSize * 1.4f).toInt() else 0) }
        val hFascia = 36 * 2 + altezze.sum() + 24 * (pezzi.size - 1).coerceAtLeast(0)
        val y0 = minOf(1500f, h - 190f - hFascia).coerceAtLeast(700f)

        // La foto intera, con gli angoli arrotondati e il bordino bianco, sopra la fascia
        val alto = 110f; val basso = y0 - 30f
        val k = minOf(1040f / foto.width, (basso - alto) / foto.height)
        val fw = foto.width * k; val fh = foto.height * k
        val dst = RectF((w - fw) / 2, alto + (basso - alto - fh) / 2, (w + fw) / 2, alto + (basso - alto - fh) / 2 + fh)
        val bianco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawRoundRect(RectF(dst.left - 6, dst.top - 6, dst.right + 6, dst.bottom + 6), 32f, 32f, bianco)
        c.save()
        c.clipPath(Path().apply { addRoundRect(dst, 26f, 26f, Path.Direction.CW) })
        c.drawBitmap(foto, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        c.restore()

        // La fascia
        if (pezzi.isEmpty()) return out
        val fascia = RectF(x0, y0, x1, y0 + hFascia)
        c.drawRoundRect(fascia, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(245, 255, 255, 255) })
        c.drawRoundRect(RectF(x0, y0, x0 + 18, y0 + hFascia), 9f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROSA })
        var y = y0 + 36
        pezzi.forEachIndexed { i, (layout, r) ->
            c.save(); c.translate(x0 + 50, y); layout.draw(c); c.restore()
            val info = TestoInstagram.dettagli(r)
            if (info.isNotEmpty()) c.drawText(info, x0 + 50, y + layout.height + pInfo.textSize * 1.15f, pInfo)
            r.prezzo?.let { pr ->
                val centro = y + altezze[i] / 2f
                c.drawText(pr, x1 - 40 - pPrezzo.measureText(pr), centro - (pPrezzo.descent() + pPrezzo.ascent()) / 2, pPrezzo)
            }
            y += altezze[i] + 24
        }
        return out
    }

    /**
     * Foto del carosello 1080×1350 (4:5) col numero nell'angolo.
     * Se la foto è già quasi 4:5 la riempie (taglia poco); se è molto diversa (es. orizzontale)
     * resta intera su sfondo sfocato, così non si perde l'articolo.
     */
    fun carosello(foto: Bitmap, numero: Int): Bitmap {
        val w = 1080; val h = 1350
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val pennello = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val kCopri = maxOf(w / foto.width.toFloat(), h / foto.height.toFloat())
        val kIntera = minOf(w / foto.width.toFloat(), h / foto.height.toFloat())
        val tagliata = 1f - (kIntera / kCopri) * (kIntera / kCopri)   // quanta foto si perde riempiendo
        if (tagliata <= 0.2f) {
            val sw = w / kCopri; val sh = h / kCopri
            val src = Rect(((foto.width - sw) / 2).toInt(), ((foto.height - sh) / 2).toInt(), ((foto.width + sw) / 2).toInt(), ((foto.height + sh) / 2).toInt())
            c.drawBitmap(foto, src, Rect(0, 0, w, h), pennello)
        } else {
            sfondo(c, foto, w, h)
            val fw = foto.width * kIntera; val fh = foto.height * kIntera
            c.drawBitmap(foto, null, RectF((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2), pennello)
        }
        // Il numero nell'angolino: cerchio bianco col bordo rosa
        val cx = 95f; val cy = 95f; val r = 62f
        c.drawCircle(cx + 4, cy + 6, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) })
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240, 255, 255, 255) })
        c.drawCircle(cx, cy, r - 3, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROSA; style = Paint.Style.STROKE; strokeWidth = 6f })
        val pn = testo(if (numero >= 10) 54f else 66f, BLU, true)
        val t = numero.toString()
        c.drawText(t, cx - pn.measureText(t) / 2, cy - (pn.descent() + pn.ascent()) / 2, pn)
        return out
    }

    /** La stessa immagine con una fascia rosa "PRENOTATO" di traverso, sopra la foto. */
    fun prenotato(immagine: Bitmap): Bitmap {
        val out = immagine.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val w = out.width.toFloat(); val h = out.height.toFloat()
        val alta = w * 0.17f
        c.save()
        c.translate(w / 2, h * 0.30f)
        c.rotate(-28f)
        c.drawRect(-w, -alta / 2, w, alta / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(238, 232, 120, 160) })
        val p = testo(w * 0.11f, Color.WHITE, true).apply { letterSpacing = 0.05f }
        val t = "PRENOTATO"
        c.drawText(t, -p.measureText(t) / 2, -(p.descent() + p.ascent()) / 2, p)
        c.restore()
        return out
    }

    fun cartella(context: Context) = File(context.cacheDir, "instagram").apply { mkdirs() }

    /** Salva le immagini pronte (svuotando quelle della volta prima). */
    fun salva(context: Context, immagini: List<Bitmap>, nome: String): List<File> {
        val dir = cartella(context)
        dir.listFiles()?.filter { it.name.startsWith(nome) }?.forEach { it.delete() }
        val ora = System.currentTimeMillis() % 100_000_000
        return immagini.mapIndexed { i, b ->
            File(dir, "${nome}_${ora}_${i + 1}.jpg").also { f ->
                // Prima in un file a parte, poi rinomino: nessuno legge mai un'immagine a metà
                val tmp = File(dir, "tmp_${f.name}")
                tmp.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                tmp.renameTo(f)
            }
        }
    }
}

/** Apre WhatsApp (Business se c'è) o Instagram, per esempio per togliere a mano una storia. */
object ApriApp {
    private fun apri(context: Context, pacchetti: List<String>): Boolean {
        for (p in pacchetti) {
            val i = context.packageManager.getLaunchIntentForPackage(p) ?: continue
            return runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
        }
        return false
    }
    fun whatsapp(context: Context) = apri(context, listOf("com.whatsapp.w4b", "com.whatsapp"))
    fun instagram(context: Context) = apri(context, listOf(CondividiInstagram.PACCHETTO))
}

/**
 * Passa le foto a Instagram con il "Condividi" di Android, nell'ordine giusto.
 * La pubblicazione la preme sempre Elisa dentro Instagram: niente automatismi (rischio blocco).
 */
object CondividiInstagram {
    const val PACCHETTO = "com.instagram.android"

    /** [testo] va anche negli appunti (Instagram non lo prende da solo: basta "Incolla"). Restituisce false se non si è aperto niente. */
    fun condividi(context: Context, file: List<File>, testo: String?): Boolean {
        if (file.isEmpty()) return false
        if (testo != null) context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Testo per Instagram", testo))
        val uri = file.mapNotNull { runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }.getOrNull() }
        if (uri.isEmpty()) return false
        val intent = if (uri.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uri))
        intent.type = "image/jpeg"
        // Tutte le foto nel clipData: così Instagram ha il permesso di leggerle tutte, nell'ordine
        intent.clipData = ClipData.newRawUri("foto", uri[0]).apply { uri.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (testo != null) intent.putExtra(Intent.EXTRA_TEXT, testo)
        val diretto = Intent(intent).setPackage(PACCHETTO)
        return try {
            // Con Instagram installato: Android chiede solo dove (Feed, Storie, Messaggi)
            if (diretto.resolveActivity(context.packageManager) != null) context.startActivity(diretto)
            else context.startActivity(Intent.createChooser(intent, "Condividi con…"))
            true
        } catch (e: Exception) {
            false
        }
    }
}
