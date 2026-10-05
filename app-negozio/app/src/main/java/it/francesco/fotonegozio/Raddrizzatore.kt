package it.francesco.fotonegozio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Risultato del raddrizzamento di una foto. */
class FotoRaddrizzata(
    val immagine: Bitmap,          // foto già girata nel verso giusto
    val rotazioneApplicata: Int,   // gradi aggiunti da noi (0, 90, 180, 270)
    val codiceLetto: String?,      // codice di 7 cifre (null = cartellino non trovato)
    val metodo: String,            // come è stato trovato il cartellino (per le prove)
    val dati: DatiCartellino? = null, // descrizione, prezzo, taglia letti dal cartellino principale
    val altri: List<DatiCartellino> = emptyList(), // altri cartellini nella stessa foto (es. 9 librottini)
    val versoSicuro: Boolean = false,  // true = il verso è stato deciso leggendo il cartellino dritto
)

/**
 * Raddrizza le foto:
 * 1. applica l'orientamento EXIF salvato dal telefono;
 * 2. TROVA il cartellino (codice a barre → testo su foto intera → foto divisa in 9 tasselli);
 * 3. RITAGLIA il cartellino e prova le 4 rotazioni sul ritaglio: tiene quella in cui
 *    il codice si legge dritto.
 *
 * Perché i tasselli: ML Kit rimpicciolisce le foto grandi prima di leggerle, e nelle foto
 * del capo intero il testo del cartellino diventa troppo piccolo. Un tassello è più piccolo
 * della foto, quindi il testo resta leggibile.
 */
class Raddrizzatore(private val context: Context) {

    private val lettoreTesto = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val lettoreBarre = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODE_128).build()
    )

    /** Zona della foto dove c'è il cartellino, trovata in una foto ruotata di [rotazione] gradi. */
    private class Zona(val base: Bitmap, val rotazione: Int, val riquadro: Rect, val codice: String?, val metodo: String)

    suspend fun raddrizza(uri: Uri): FotoRaddrizzata {
        val foto = caricaConExif(uri)
        val zona = trovaCartellino(foto)
            ?: return FotoRaddrizzata(foto, 0, null, "non trovato")

        // Ritaglio il cartellino e cerco il verso giusto (4 prove su un pezzo piccolo = veloce)
        val (ritaglio, area) = ritagliaConArea(zona.base, zona.riquadro)
        for (gradi in listOf(0, 90, 270, 180)) {
            val ritaglioGirato = ruota(ritaglio, gradi)
            val testoGirato = leggi(ritaglioGirato)
            val riga = rigaCodiceDritta(testoGirato) ?: continue
            val totale = (zona.rotazione + gradi) % 360
            val dritta = ruota(foto, totale)

            // Pezzo 2: dove sta la riga del codice nella foto dritta → leggo tutto il cartellino
            val angoli = try {
                riga.cornerPoints?.map { posizioneInFotoDritta(it.x.toFloat(), it.y.toFloat(), ritaglio, area, gradi, zona.base) }
                    ?.takeIf { it.size == 4 }
            } catch (e: Exception) {
                null
            }
            val dati = try {
                angoli?.let { leggiCartellino(dritta, it) }
            } catch (e: Exception) {
                null   // la lettura dei dati non deve mai bloccare il raddrizzamento
            } ?: LettoreCartellino.analizza(righeDa(testoGirato)).takeIf { it.codice != null }
            val codice = zona.codice ?: dati?.codice ?: codiceDi(riga)

            // Altri cartellini nella stessa foto (es. tanti librottini insieme)
            val altri = try {
                if (codice != null && angoli != null) altriCartellini(dritta, codice, angoli) else null
            } catch (e: Exception) {
                AltriCartellini(emptyList(), "altri: errore ${e.javaClass.simpleName}")
            }
            val metodo = zona.metodo + (altri?.diagnostica?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
            return FotoRaddrizzata(dritta, totale, codice, metodo, dati, altri?.dati.orEmpty(), versoSicuro = true)
        }
        // Cartellino trovato ma verso incerto: se viene dalla rotazione di 90° almeno quella la applico
        return FotoRaddrizzata(ruota(foto, zona.rotazione), zona.rotazione, zona.codice, zona.metodo + ", verso incerto")
    }

    /** Cerca il cartellino, dal metodo più veloce al più lento. */
    private suspend fun trovaCartellino(foto: Bitmap): Zona? {
        // 1. Codice a barre su tutta la foto: contiene direttamente il codice articolo
        val barre = lettoreBarre.process(InputImage.fromBitmap(foto, 0)).await()
        barre.firstOrNull { it.rawValue?.matches(SETTE_CIFRE) == true && it.boundingBox != null }?.let {
            return Zona(foto, 0, it.boundingBox!!, it.rawValue, "codice a barre")
        }

        // 2. Testo su tutta la foto (funziona se il cartellino è grande, foto da vicino)
        val testoIntero = leggi(foto)
        rigaCodice(testoIntero)?.let { return Zona(foto, 0, it.boundingBox!!, codiceDi(it), "testo") }

        // 3. Zone dove ML Kit ha visto del testo ma non è riuscito a leggerlo: ritaglio e ingrandisco
        val provate = mutableListOf<Rect>()
        ingrandisciZone(foto, 0, zoneTesto(testoIntero, Rect(0, 0, foto.width, foto.height)), provate)
            ?.let { return it }

        // 4. Foto divisa in tasselli, prima così com'è poi girata di 90° (per il testo in verticale)
        for (gradi in listOf(0, 90)) {
            val base = ruota(foto, gradi)
            val zoneTasselli = mutableListOf<Rect>()
            for (t in tasselli(base)) {
                // Foto piccole (es. già ritagliate): ingrandisco il tassello, così il testo minuscolo si legge
                val scala = (LATO_TASSELLO.toFloat() / max(t.width(), t.height())).coerceAtLeast(1f)
                val pezzo = Bitmap.createBitmap(base, t.left, t.top, t.width(), t.height()).let {
                    if (scala > 1f) Bitmap.createScaledBitmap(it, (it.width * scala).toInt(), (it.height * scala).toInt(), true)
                    else it
                }
                val testo = leggi(pezzo)
                rigaCodice(testo)?.let {
                    // coordinate dal tassello alla foto intera
                    return Zona(base, gradi, nellaFoto(it.boundingBox!!, t, scala), codiceDi(it), "tasselli")
                }
                zoneTasselli += zoneTesto(testo, t, scala)
            }
            // 5. Come il punto 3, ma con le zone viste nei tasselli
            ingrandisciZone(base, gradi, zoneTasselli, if (gradi == 0) provate else mutableListOf())
                ?.let { return it }
        }
        return null
    }

    /**
     * Riquadri dei blocchi di testo (anche illeggibili), già spostati nelle coordinate della foto.
     * Prima quelli con più cifre: il cartellino ha codice e prezzo.
     */
    private fun zoneTesto(testo: Text, tassello: Rect, scala: Float = 1f): List<Rect> =
        testo.textBlocks
            .filter { it.boundingBox != null }
            .sortedByDescending { b -> b.text.count { it.isDigit() } }
            .map { b -> nellaFoto(b.boundingBox!!, tassello, scala) }

    /** Riquadro dal tassello (eventualmente ingrandito di [scala]) alla foto intera. */
    private fun nellaFoto(r: Rect, tassello: Rect, scala: Float) = Rect(
        (r.left / scala).toInt() + tassello.left, (r.top / scala).toInt() + tassello.top,
        (r.right / scala).toInt() + tassello.left, (r.bottom / scala).toInt() + tassello.top,
    )

    /** Ritaglia e ingrandisce fino a [MAX_ZONE] zone, cercando il codice in orizzontale e in verticale. */
    private suspend fun ingrandisciZone(base: Bitmap, gradi: Int, zone: List<Rect>, provate: MutableList<Rect>): Zona? {
        var tentativi = 0
        for (z in zone) {
            if (tentativi >= MAX_ZONE) break
            // Zona già coperta da un ritaglio precedente: inutile rileggerla
            if (provate.any { it.contains(z.centerX(), z.centerY()) }) continue
            tentativi++
            val (ritaglio, area) = ritagliaConArea(base, z)
            provate += area
            for (g in listOf(0, 90)) {
                rigaCodice(leggi(ruota(ritaglio, g)))?.let {
                    return Zona(base, gradi, z, codiceDi(it), "ingrandimento")
                }
            }
        }
        return null
    }

    private suspend fun leggi(b: Bitmap): Text = lettoreTesto.process(InputImage.fromBitmap(b, 0)).await()

    /** Riga con il codice di 7 cifre (qualsiasi inclinazione), es. "1443984" o "A442/1443984". */
    private fun rigaCodice(testo: Text): Text.Line? =
        testo.textBlocks.flatMap { it.lines }.firstOrNull { REGEX_CODICE.containsMatchIn(it.text) && it.boundingBox != null }

    private fun codiceDi(riga: Text.Line): String? = REGEX_CODICE.find(riga.text)?.value

    /** Riga col codice scritta più in orizzontale che in verticale (cartellino anche un po' storto). */
    private fun rigaCodiceDritta(testo: Text): Text.Line? =
        testo.textBlocks.flatMap { it.lines }.firstOrNull {
            REGEX_CODICE.containsMatchIn(it.text) && abs(it.angle) < 45f && it.cornerPoints != null
        }

    /**
     * Porta un punto dal ritaglio girato alla foto dritta finale.
     * Passaggi inversi: ritaglio girato → ritaglio → foto "base" → foto dritta (= base girata degli stessi gradi).
     */
    private fun posizioneInFotoDritta(x: Float, y: Float, ritaglio: Bitmap, area: Rect, gradi: Int, base: Bitmap): PointF {
        val p = floatArrayOf(x, y)
        // 1. Annullo la rotazione del ritaglio
        val inversa = Matrix()
        matriceRotazione(ritaglio.width, ritaglio.height, gradi).invert(inversa)
        inversa.mapPoints(p)
        // 2. Annullo l'ingrandimento e lo spostamento del ritaglio
        val scala = ritaglio.width.toFloat() / area.width()
        p[0] = p[0] / scala + area.left
        p[1] = p[1] / scala + area.top
        // 3. Applico la stessa rotazione alla foto intera
        matriceRotazione(base.width, base.height, gradi).mapPoints(p)
        return PointF(p[0], p[1])
    }

    /**
     * Pezzo 2: legge codice, descrizione, prezzo e taglia.
     * [angoli] = i 4 angoli della riga del codice nella foto dritta (in alto a sx, in alto a dx, in basso a dx, in basso a sx).
     */
    private suspend fun leggiCartellino(dritta: Bitmap, angoli: List<PointF>): DatiCartellino? {
        val (a, b, _, d) = angoli
        val larghezzaCodice = hypot(b.x - a.x, b.y - a.y)
        val altezzaCodice = hypot(d.x - a.x, d.y - a.y).coerceAtLeast(1f)
        // Inclinazione del cartellino (es. porta banane messo storto)
        val inclinazione = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()

        // Quadrato attorno al codice, abbastanza grande da contenere il cartellino con qualsiasi inclinazione
        val cx = (a.x + b.x) / 2
        val cy = (a.y + d.y) / 2
        val meta = max(6 * larghezzaCodice, 11 * altezzaCodice)
        val area = Rect((cx - meta).toInt(), (cy - meta).toInt(), (cx + meta).toInt(), (cy + meta).toInt())
        if (!area.intersect(0, 0, dritta.width, dritta.height)) return null

        // Ingrandisco (o rimpicciolisco) perché il testo del codice sia alto ~40 px, poi raddrizzo l'inclinazione.
        // Provo la correzione nei due versi (e senza) e tengo quella con le righe più dritte.
        val scala = (ALTEZZA_TESTO / altezzaCodice).coerceIn(0.5f, 4f)
        val prove = if (abs(inclinazione) < 3f) listOf(0f) else listOf(-inclinazione, inclinazione, 0f)
        var migliore: DatiCartellino? = null
        var stortoMigliore = Float.MAX_VALUE
        for (correzione in prove) {
            val matrice = Matrix().apply {
                postScale(scala, scala)
                postRotate(correzione)
            }
            val pezzo = Bitmap.createBitmap(dritta, area.left, area.top, area.width(), area.height(), matrice, true)
            val testo = leggi(pezzo)
            val dati = LettoreCartellino.analizza(righeDa(testo))
            if (dati.codice == null) continue
            // Quanto sono storte in media le righe lette (0 = perfettamente dritte)
            val linee = testo.textBlocks.flatMap { it.lines }
            val storto = if (linee.isEmpty()) 90f else linee.map { abs(it.angle) }.average().toFloat()
            // Meglio = più dritto; a parità (meno di 2°), quello con più dati
            val meglio = migliore == null || storto < stortoMigliore - 2f ||
                (storto < stortoMigliore + 2f && completezza(dati) > completezza(migliore))
            if (meglio) {
                migliore = dati
                stortoMigliore = storto
            }
            if (stortoMigliore < 2f && completezza(migliore) == 3) break   // dritto e completo: basta così
        }
        return migliore
    }

    /** Un codice trovato nella foto dritta, con i 4 angoli della sua riga. */
    private class CodiceTrovato(val codice: String, val angoli: List<PointF>) {
        val altezza get() = hypot(angoli[3].x - angoli[0].x, angoli[3].y - angoli[0].y)
    }

    /** Altri cartellini trovati nella foto, più un riassunto per le prove ("cosa ha trovato e cosa ha scartato"). */
    private class AltriCartellini(val dati: List<DatiCartellino>, val diagnostica: String)

    /**
     * Cerca gli ALTRI cartellini nella foto già dritta e li legge tutti.
     * 1. giro veloce: codici a barre + testo su tutta la foto;
     * 2. se c'è almeno un altro cartellino: tasselli (codici a barre + testo, anche storti);
     * 3. zone con testo illeggibile: ritagliate, ingrandite e rilette.
     * Scarta i cartellini molto più piccoli del principale (sullo sfondo) e le doppie letture nello stesso punto.
     */
    private suspend fun altriCartellini(dritta: Bitmap, principale: String, angoliPrincipale: List<PointF>): AltriCartellini {
        val altezzaPrincipale = CodiceTrovato(principale, angoliPrincipale).altezza
        val trovati = mutableMapOf<String, CodiceTrovato>()
        val posizioni = mutableListOf(angoliPrincipale[0])   // dove stanno i cartellini già presi
        val conta = mutableMapOf<String, Int>()               // per la diagnostica
        fun segna(cosa: String) { conta[cosa] = (conta[cosa] ?: 0) + 1 }
        fun aggiungi(c: CodiceTrovato, metodo: String) {
            if (c.codice == principale || c.codice in trovati) return
            if (c.altezza < altezzaPrincipale / 3) return segna("piccoli")   // sullo sfondo (es. scaffali)
            // Nello stesso punto di un cartellino già preso = stesso cartellino letto con una cifra sbagliata
            if (posizioni.any { p -> hypot(p.x - c.angoli[0].x, p.y - c.angoli[0].y) < 3 * altezzaPrincipale }) return segna("doppi")
            trovati[c.codice] = c
            posizioni += c.angoli[0]
            segna(metodo)
        }

        // 1. Giro veloce
        val tutta = Rect(0, 0, dritta.width, dritta.height)
        codiciABarre(dritta, tutta, 1f).forEach { aggiungi(it, "barre") }
        val testoIntero = leggi(dritta)
        codiciNelTesto(testoIntero, tutta, 1f).forEach { aggiungi(it, "testo") }

        if (trovati.isNotEmpty()) {
            // 2. Tasselli
            val zone = zoneTesto(testoIntero, tutta).toMutableList()
            for (t in tasselli(dritta)) {
                val scala = (LATO_TASSELLO.toFloat() / max(t.width(), t.height())).coerceAtLeast(1f)
                val pezzo = Bitmap.createBitmap(dritta, t.left, t.top, t.width(), t.height()).let {
                    if (scala > 1f) Bitmap.createScaledBitmap(it, (it.width * scala).toInt(), (it.height * scala).toInt(), true)
                    else it
                }
                // Il codice a barre si legge anche se il cartellino è storto o girato
                codiciABarre(pezzo, t, scala).forEach { aggiungi(it, "tasselli") }
                val testo = leggi(pezzo)
                codiciNelTesto(testo, t, scala).forEach { aggiungi(it, "tasselli") }
                zone += zoneTesto(testo, t, scala)
            }

            // 3. Zone con testo illeggibile lontane dai cartellini già presi: ritaglio, ingrandisco, rileggo
            val provate = mutableListOf<Rect>()
            var tentativi = 0
            for (z in zone) {
                if (tentativi >= MAX_ZONE_ALTRI) break
                val cx = z.centerX().toFloat()
                val cy = z.centerY().toFloat()
                if (provate.any { it.contains(z.centerX(), z.centerY()) }) continue
                if (posizioni.any { p -> hypot(p.x - cx, p.y - cy) < 8 * altezzaPrincipale }) continue
                tentativi++
                val (ritaglio, area) = ritagliaConArea(dritta, z)
                provate += area
                for (g in listOf(0, 90)) {
                    val girato = ruota(ritaglio, g)
                    val inverso = Matrix().also { matriceRotazione(ritaglio.width, ritaglio.height, g).invert(it) }
                    val scalaRitaglio = ritaglio.width.toFloat() / area.width()
                    leggi(girato).textBlocks.flatMap { it.lines }.forEach { l ->
                        val codice = REGEX_CODICE.find(l.text)?.value ?: return@forEach
                        val angoli = l.cornerPoints?.takeIf { it.size == 4 } ?: return@forEach
                        // dal ritaglio girato alla foto dritta
                        val nellaFoto = angoli.map { p ->
                            val xy = floatArrayOf(p.x.toFloat(), p.y.toFloat())
                            inverso.mapPoints(xy)
                            PointF(xy[0] / scalaRitaglio + area.left, xy[1] / scalaRitaglio + area.top)
                        }
                        aggiungi(CodiceTrovato(codice, nellaFoto), "ingrandimento")
                    }
                }
            }
            if (tentativi > 0) conta["zone provate"] = tentativi
        }

        // Leggo ogni cartellino e li metto in ordine di lettura (dall'alto, da sinistra)
        val dati = trovati.values
            .sortedWith(compareBy({ (it.angoli[0].y / (altezzaPrincipale * 6)).toInt() }, { it.angoli[0].x }))
            .map { c -> leggiCartellino(dritta, c.angoli)?.copy(codice = c.codice) ?: DatiCartellino(c.codice, null, null, null) }
        val diagnostica = if (conta.isEmpty()) "" else "altri: " + conta.entries.joinToString(", ") { "${it.key} ${it.value}" }
        return AltriCartellini(dati, diagnostica)
    }

    /** Righe col codice (con qualsiasi inclinazione) lette in un pezzo di foto, riportate nelle coordinate della foto. */
    private fun codiciNelTesto(testo: Text, pezzo: Rect, scala: Float): List<CodiceTrovato> =
        testo.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val codice = REGEX_CODICE.find(l.text)?.value ?: return@mapNotNull null
            val angoli = l.cornerPoints?.takeIf { it.size == 4 } ?: return@mapNotNull null
            CodiceTrovato(codice, angoli.map { PointF(it.x / scala + pezzo.left, it.y / scala + pezzo.top) })
        }

    /** Codici a barre (7 cifre) in un pezzo di foto, con la posizione stimata della riga del codice. */
    private suspend fun codiciABarre(pezzo: Bitmap, area: Rect, scala: Float): List<CodiceTrovato> =
        lettoreBarre.process(InputImage.fromBitmap(pezzo, 0)).await().mapNotNull { b ->
            val valore = b.rawValue?.takeIf { it.matches(SETTE_CIFRE) } ?: return@mapNotNull null
            val angoli = b.cornerPoints?.takeIf { it.size == 4 }
                ?.map { PointF(it.x / scala + area.left, it.y / scala + area.top) }
                ?: return@mapNotNull null
            CodiceTrovato(valore, rigaCodiceDaCodiceABarre(angoli))
        }

    /**
     * Posizione stimata della riga del codice partendo dai 4 angoli del codice a barre
     * (sul cartellino il codice sta subito a destra del codice a barre, in basso).
     * Funziona con qualsiasi inclinazione: ci si sposta "lungo" e "giù" rispetto al codice a barre.
     */
    private fun rigaCodiceDaCodiceABarre(barre: List<PointF>): List<PointF> {
        val (a, b, c) = barre                                   // in alto a sx, in alto a dx, in basso a dx
        val lungoX = b.x - a.x; val lungoY = b.y - a.y          // direzione della larghezza
        val giuX = c.x - b.x;   val giuY = c.y - b.y            // direzione dell'altezza
        fun punto(lungo: Float, giu: Float) = PointF(a.x + lungoX * lungo + giuX * giu, a.y + lungoY * lungo + giuY * giu)
        return listOf(punto(1.02f, 0.83f), punto(1.35f, 0.83f), punto(1.35f, 1.02f), punto(1.02f, 1.02f))
    }


    /** Quanti dati importanti ha la lettura (codice, descrizione, prezzo). */
    private fun completezza(d: DatiCartellino?): Int =
        if (d == null) 0 else listOf(d.codice, d.descrizione, d.prezzo).count { it != null }

    /**
     * Le singole PAROLE lette da ML Kit, con la loro posizione.
     * Non uso le righe di ML Kit perché a volte attaccano la taglia all'ultima riga della descrizione:
     * le righe le ricostruisce l'analizzatore guardando le posizioni.
     */
    private fun righeDa(testo: Text): List<Riga> =
        testo.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { e ->
            e.boundingBox?.let { Riga(e.text, it.left, it.top, it.right, it.bottom) }
        }

    /** 9 tasselli che si sovrappongono a metà (griglia 3x3, ognuno grande metà foto). */
    private fun tasselli(b: Bitmap): List<Rect> {
        val w = b.width / 2
        val h = b.height / 2
        return buildList {
            for (gy in 0..2) for (gx in 0..2) {
                val x = gx * b.width / 4
                val y = gy * b.height / 4
                add(Rect(x, y, min(x + w, b.width), min(y + h, b.height)))
            }
        }
    }

    /**
     * Ritaglio quadrato attorno alla zona trovata, abbastanza grande da contenere
     * tutto il cartellino, ingrandito se piccolo (ML Kit legge meglio).
     * Restituisce anche l'area della foto ritagliata.
     */
    private fun ritagliaConArea(b: Bitmap, zona: Rect): Pair<Bitmap, Rect> {
        // Almeno 1/5 della foto: se la zona è una sola parola, il ritaglio prende comunque tutto il cartellino
        val lato = max(max(zona.width(), zona.height()) * 3, max(b.width, b.height) / 5)
        val cx = zona.centerX()
        val cy = zona.centerY()
        val r = Rect(cx - lato / 2, cy - lato / 2, cx + lato / 2, cy + lato / 2)
        r.intersect(0, 0, b.width, b.height)
        val pezzo = Bitmap.createBitmap(b, r.left, r.top, r.width(), r.height())
        val scala = LATO_RITAGLIO.toFloat() / max(pezzo.width, pezzo.height)
        val finale = if (scala > 1f) Bitmap.createScaledBitmap(pezzo, (pezzo.width * scala).toInt(), (pezzo.height * scala).toInt(), true)
        else pezzo
        return finale to r
    }

    /** Legge la foto (max ~4000 px per lato) e applica l'orientamento EXIF. */
    private fun caricaConExif(uri: Uri): Bitmap {
        val resolver = context.contentResolver

        // 1. Solo le dimensioni, senza caricare la foto in memoria
        val dimensioni = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, dimensioni) }

        // 2. Riduzione (potenza di 2) solo per le foto enormi, es. 50 megapixel
        var riduzione = 1
        while (max(dimensioni.outWidth, dimensioni.outHeight) / riduzione > LATO_MASSIMO) riduzione *= 2
        val opzioni = BitmapFactory.Options().apply { inSampleSize = riduzione }
        val foto = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opzioni) }
            ?: error("Impossibile aprire la foto")

        // 3. Orientamento salvato dalla fotocamera
        val gradiExif = resolver.openInputStream(uri).use { stream ->
            stream?.let { ExifInterface(it).rotationDegrees } ?: 0
        }
        return ruota(foto, gradiExif)
    }

    private fun ruota(foto: Bitmap, gradi: Int): Bitmap = ruotaImmagine(foto, gradi)

    /** La stessa trasformazione che usa [ruotaImmagine]: rotazione + spostamento per restare in coordinate positive. */
    private fun matriceRotazione(w: Int, h: Int, gradi: Int): Matrix {
        val m = Matrix().apply { postRotate(gradi.toFloat()) }
        val bordi = RectF(0f, 0f, w.toFloat(), h.toFloat())
        m.mapRect(bordi)
        m.postTranslate(-bordi.left, -bordi.top)
        return m
    }

    companion object {
        private const val LATO_MASSIMO = 4100   // 12 MP restano intere, 50 MP dimezzate
        private const val LATO_RITAGLIO = 1200
        private const val LATO_TASSELLO = 1600   // tasselli più piccoli di così vengono ingranditi
        private const val ALTEZZA_TESTO = 40f    // altezza (px) a cui porto il testo piccolo del cartellino prima di leggerlo
        private const val MAX_ZONE = 6
        private const val MAX_ZONE_ALTRI = 12    // zone ingrandite per cercare gli altri cartellini (foto con tanti articoli)           // zone ingrandite al massimo per ogni giro (tiene basso il tempo)

        private val SETTE_CIFRE = Regex("\\d{7}")
        // 7 cifre esatte, non attaccate ad altre cifre (esclude i codici EAN a 13 cifre)
        private val REGEX_CODICE = Regex("(?<!\\d)\\d{7}(?!\\d)")

        /** Gira la foto in senso orario di [gradi] (multipli di 90). */
        fun ruotaImmagine(foto: Bitmap, gradi: Int): Bitmap {
            if (gradi % 360 == 0) return foto
            val matrice = Matrix().apply { postRotate(gradi.toFloat()) }
            return Bitmap.createBitmap(foto, 0, 0, foto.width, foto.height, matrice, true)
        }

        /** Salva la foto raddrizzata come JPEG nella cartella temporanea dell'app. */
        fun salva(context: Context, foto: Bitmap, nome: String): File {
            val cartella = File(context.cacheDir, "raddrizzate").apply { mkdirs() }
            val file = File(cartella, "$nome.jpg")
            file.outputStream().use { foto.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            return file
        }
    }
}
