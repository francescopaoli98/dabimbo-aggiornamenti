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
import kotlin.math.roundToInt

/** Risultato del raddrizzamento di una foto. */
class FotoRaddrizzata(
    val immagine: Bitmap,          // foto già girata nel verso giusto
    val rotazioneApplicata: Int,   // gradi aggiunti da noi (0, 90, 180, 270)
    val codiceLetto: String?,      // codice di 7 cifre (null = cartellino non trovato)
    val metodo: String,            // come è stato trovato il cartellino (per le prove)
    val dati: DatiCartellino? = null, // descrizione, prezzo, taglia letti dal cartellino principale
    val altri: List<DatiCartellino> = emptyList(), // altri cartellini nella stessa foto (es. 9 librottini)
    val versoSicuro: Boolean = false,  // true = il verso è stato deciso leggendo il cartellino dritto
    val etichetteViste: Int = 0,       // cartellini che si vedono nella foto (contati dai prezzi)
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
    private class Zona(
        val base: Bitmap, val rotazione: Int, val riquadro: Rect, val codice: String?, val metodo: String,
        val angoli: List<PointF>? = null,   // i 4 angoli della riga del codice in "base", se conosciuti
    )

    suspend fun raddrizza(uri: Uri): FotoRaddrizzata {
        val foto = caricaConExif(uri)
        val zona = trovaCartellino(foto)
            ?: return FotoRaddrizzata(foto, 0, null, "non trovato")

        // Ritaglio il cartellino e cerco il verso giusto (4 prove su un pezzo piccolo = veloce)
        val (ritaglio, area) = ritagliaConArea(zona.base, zona.riquadro)
        for (gradi in listOf(0, 90, 270, 180)) {
            val ritaglioGirato = ruota(ritaglio, gradi)
            val testoGirato = leggi(ritaglioGirato, "verso: ritaglio girato di $gradi°")
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
                angoli?.let { leggiCartellino(dritta, it, zona.codice ?: codiceDi(riga)) }
            } catch (e: Exception) {
                null   // la lettura dei dati non deve mai bloccare il raddrizzamento
            } ?: LettoreCartellino.analizza(righeDa(testoGirato), zona.codice ?: codiceDi(riga)).takeIf { it.codice != null }
            val codice = zona.codice ?: dati?.codice ?: codiceDi(riga)

            // Altri cartellini nella stessa foto (es. tanti librottini insieme)
            val altri = try {
                if (codice != null && angoli != null) altriCartellini(dritta, codice, angoli) else null
            } catch (e: Exception) {
                AltriCartellini(emptyList(), "altri: errore ${e.javaClass.simpleName}")
            }
            val metodo = zona.metodo + (altri?.diagnostica?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
            return FotoRaddrizzata(dritta, totale, codice, metodo, dati, altri?.dati.orEmpty(), versoSicuro = true, etichetteViste = altri?.etichetteViste ?: 0)
        }
        // Il ritaglio non ha funzionato, ma se conosco la riga del codice so in che direzione è scritta:
        // giro la foto in modo che la riga venga orizzontale e leggo il cartellino da lì
        zona.angoli?.let { angoliBase ->
            val (a, b) = angoliBase
            val direzione = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
            val gradi = ((-Math.round(direzione / 90f) * 90) % 360 + 360) % 360
            val totale = (zona.rotazione + gradi) % 360
            val dritta = ruota(foto, totale)
            val m = matriceRotazione(zona.base.width, zona.base.height, gradi)
            val angoliDritti = angoliBase.map { p -> floatArrayOf(p.x, p.y).also { m.mapPoints(it) }.let { PointF(it[0], it[1]) } }
            val dati = try { leggiCartellino(dritta, angoliDritti, zona.codice) } catch (e: Exception) { null }
            val codice = zona.codice ?: dati?.codice
            val altri = try {
                if (codice != null) altriCartellini(dritta, codice, angoliDritti) else null
            } catch (e: Exception) { null }
            val metodo = zona.metodo + ", verso dalla riga" + (altri?.diagnostica?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
            return FotoRaddrizzata(dritta, totale, codice, metodo, dati, altri?.dati.orEmpty(), versoSicuro = false, etichetteViste = altri?.etichetteViste ?: 0)
        }
        // Verso incerto e riga sconosciuta: applico almeno la rotazione con cui il cartellino è stato trovato
        return FotoRaddrizzata(ruota(foto, zona.rotazione), zona.rotazione, zona.codice, zona.metodo + ", verso incerto")
    }

    /** Cerca il cartellino, dal metodo più veloce al più lento. */
    private suspend fun trovaCartellino(foto: Bitmap): Zona? {
        // 1. Codice a barre su tutta la foto: contiene direttamente il codice articolo
        val barre = lettoreBarre.process(InputImage.fromBitmap(foto, 0)).await()
        diario?.nota("primo: codici a barre", barre.joinToString { "${it.rawValue}" }.ifEmpty { "nessuno" })
        barre.firstOrNull { it.rawValue?.matches(SETTE_CIFRE) == true && it.boundingBox != null }?.let {
            return Zona(foto, 0, it.boundingBox!!, it.rawValue, "codice a barre",
                it.cornerPoints?.takeIf { p -> p.size == 4 }?.let { p -> rigaCodiceDaCodiceABarre(p.map { q -> PointF(q.x.toFloat(), q.y.toFloat()) }) })
        }

        // 2. Testo su tutta la foto (funziona se il cartellino è grande, foto da vicino)
        val testoIntero = leggi(foto, "primo: foto intera")
        rigaCodice(testoIntero)?.let { return Zona(foto, 0, it.boundingBox!!, codiceDi(it), "testo", angoliDi(it, Rect(0, 0, 0, 0), 1f)) }

        // 3. Zone dove ML Kit ha visto del testo ma non è riuscito a leggerlo: ritaglio e ingrandisco
        val provate = mutableListOf<Rect>()
        ingrandisciZone(foto, 0, zoneTesto(testoIntero, Rect(0, 0, foto.width, foto.height)), provate)
            ?.let { return it }

        // Prezzi letti (scritta grande): segnaposto dei cartellini, usati al punto 6
        val prezzi = prezziNelTesto(testoIntero, Rect(0, 0, foto.width, foto.height), 1f).toMutableList()

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
                val testo = leggi(pezzo, "primo: tassello rot $gradi")
                rigaCodice(testo)?.let {
                    // coordinate dal tassello alla foto intera
                    return Zona(base, gradi, nellaFoto(it.boundingBox!!, t, scala), codiceDi(it), "tasselli", angoliDi(it, t, scala))
                }
                zoneTasselli += zoneTesto(testo, t, scala)
                if (gradi == 0) prezzi += prezziNelTesto(testo, t, scala)
            }
            // 5. Come il punto 3, ma con le zone viste nei tasselli
            ingrandisciZone(base, gradi, zoneTasselli, if (gradi == 0) provate else mutableListOf())
                ?.let { return it }

            if (gradi == 0) {
                // Dopo il giro a 0°: attorno ai prezzi letti (ritaglio, raddrizzo, ingrandisco, cerco il codice)
                val prezziProvati = mutableListOf<PointF>()
                for (p in prezzi) {
                    if (prezziProvati.size >= MAX_PREZZI_PRIMO) break
                    val centro = p.centro
                    if (prezziProvati.any { hypot(it.x - centro.x, it.y - centro.y) < 2 * p.altezza }) continue
                    prezziProvati += centro
                    val c = codiciAttornoAlPrezzo(foto, p).firstOrNull() ?: continue
                    return Zona(foto, 0, riquadroDi(c.angoli), c.codice, "prezzo", c.angoli)
                }
            }
        }

        // 6. Ultima spiaggia: rettangoli bianchi (etichette) trovati dalla forma, letti uno per uno
        //    ingranditi, in tutti i versi e, se serve, con l'immagine "pulita"
        for (r in etichetteIn(foto).take(MAX_ETICHETTE)) {
            val c = codiciNelRitaglio(foto, r, listOf(0, 90, 270, 180)).firstOrNull() ?: continue
            return Zona(foto, 0, riquadroDi(c.angoli), c.codice, "etichetta", c.angoli)
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
                rigaCodice(leggi(ruota(ritaglio, g), "primo: zona ingrandita $g°"))?.let {
                    val angoli = it.cornerPoints?.takeIf { p -> p.size == 4 }?.map { p ->
                        dalRitaglio(p.x.toFloat(), p.y.toFloat(), ritaglio, area, g)
                    }
                    return Zona(base, gradi, z, codiceDi(it), "ingrandimento", angoli)
                }
            }
        }
        return null
    }

    /** Modalità diagnosi: se non è null, ogni lettura viene annotata (vedi [Diario]). */
    var diario: Diario? = null

    private suspend fun leggi(b: Bitmap, fase: String = "lettura"): Text =
        lettoreTesto.process(InputImage.fromBitmap(b, 0)).await().also { diario?.registra(fase, b, it) }

    /** Riga con il codice di 7 cifre (qualsiasi inclinazione), es. "1443984" o "A442/1443984". */
    private fun rigaCodice(testo: Text): Text.Line? =
        testo.textBlocks.flatMap { it.lines }.firstOrNull { REGEX_CODICE.containsMatchIn(LettoreCartellino.cifre(it.text)) && it.boundingBox != null }

    private fun codiceDi(riga: Text.Line): String? = REGEX_CODICE.find(LettoreCartellino.cifre(riga.text))?.value

    /** Riga col codice scritta più in orizzontale che in verticale (cartellino anche un po' storto). */
    private fun rigaCodiceDritta(testo: Text): Text.Line? =
        testo.textBlocks.flatMap { it.lines }.firstOrNull {
            REGEX_CODICE.containsMatchIn(LettoreCartellino.cifre(it.text)) && abs(it.angle) < 45f && it.cornerPoints != null
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
    private suspend fun leggiCartellino(dritta: Bitmap, angoli: List<PointF>, codiceNoto: String? = null): DatiCartellino? {
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
        var pezzoMigliore: Bitmap? = null
        for (correzione in prove) {
            val matrice = Matrix().apply {
                postScale(scala, scala)
                postRotate(correzione)
            }
            val pezzo = Bitmap.createBitmap(dritta, area.left, area.top, area.width(), area.height(), matrice, true)
            if (pezzoMigliore == null) pezzoMigliore = pezzo
            val testo = leggi(pezzo, "lettura cartellino, correzione ${correzione.roundToInt()}°")
            val dati = LettoreCartellino.analizza(righeDa(testo), codiceNoto)
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
                pezzoMigliore = pezzo
            }
            if (stortoMigliore < 2f && completezza(migliore) == 3) break   // dritto e completo: basta così
        }
        // Lettura incompleta (cartellino sbiadito o sfocato): riprovo con l'immagine "pulita"
        if (completezza(migliore) < 3) {
            pezzoMigliore?.let { p ->
                val pulita = LettoreCartellino.analizza(righeDa(leggi(migliora(p), "lettura cartellino, immagine pulita")), codiceNoto)
                if (pulita.codice != null && completezza(pulita) > completezza(migliore)) migliore = pulita
            }
        }
        return migliore
    }

    /** Immagine "pulita": bianco e nero, contrasto al massimo, più nitida (vedi [Miglioramento]). */
    private fun migliora(b: Bitmap): Bitmap {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return Bitmap.createBitmap(Miglioramento.migliora(px, b.width, b.height), b.width, b.height, Bitmap.Config.ARGB_8888)
    }

    /** Etichette (rettangoli bianchi con del nero dentro) cercate a due risoluzioni, nelle coordinate della foto. */
    private fun etichetteIn(foto: Bitmap): List<Rect> {
        val trovate = mutableListOf<TrovaEtichette.Riquadro>()
        for (lato in listOf(400, 800)) {
            val k = (lato.toFloat() / max(foto.width, foto.height)).coerceAtMost(1f)
            val w = (foto.width * k).toInt().coerceAtLeast(1)
            val h = (foto.height * k).toInt().coerceAtLeast(1)
            val piccola = Bitmap.createScaledBitmap(foto, w, h, true)
            val px = IntArray(w * h).also { piccola.getPixels(it, 0, w, 0, 0, w, h) }
            for (r in TrovaEtichette.trova(px, w, h)) {
                val nellaFoto = TrovaEtichette.Riquadro((r.sx / k).toInt(), (r.su / k).toInt(), (r.dx / k).toInt(), (r.giu / k).toInt(), r.somiglianza)
                if (trovate.none { it.sovrappostoA(nellaFoto) }) trovate += nellaFoto
            }
        }
        return trovate.sortedBy { it.somiglianza }.map { Rect(it.sx, it.su, it.dx, it.giu) }
    }

    /**
     * Ritaglia [zona] (allargata un po'), la ingrandisce, e cerca i codici provando le [rotazioni];
     * se non trova niente riprova con l'immagine "pulita". Restituisce i codici nelle coordinate della foto.
     */
    private suspend fun codiciNelRitaglio(foto: Bitmap, zona: Rect, rotazioni: List<Int>): List<CodiceTrovato> {
        val area = Rect(zona).apply { inset(-width() * 15 / 100, -height() * 15 / 100) }
        if (!area.intersect(0, 0, foto.width, foto.height)) return emptyList()
        val scala = (LATO_ETICHETTA.toFloat() / max(area.width(), area.height())).coerceIn(0.5f, 6f)
        val ritaglio = Bitmap.createBitmap(foto, area.left, area.top, area.width(), area.height()).let {
            Bitmap.createScaledBitmap(it, (it.width * scala).toInt().coerceAtLeast(1), (it.height * scala).toInt().coerceAtLeast(1), true)
        }
        for (versione in 0..1) {
            val immagine = if (versione == 0) ritaglio else migliora(ritaglio)
            for (g in rotazioni) {
                val inversa = Matrix().also { matriceRotazione(immagine.width, immagine.height, g).invert(it) }
                val codici = leggi(ruota(immagine, g), "etichetta ${if (versione == 0) "" else "pulita "}$g°").textBlocks.flatMap { it.lines }.mapNotNull { l ->
                    val codice = REGEX_CODICE.find(LettoreCartellino.cifre(l.text))?.value ?: return@mapNotNull null
                    val angoli = l.cornerPoints?.takeIf { it.size == 4 } ?: return@mapNotNull null
                    CodiceTrovato(codice, angoli.map { q ->
                        val xy = floatArrayOf(q.x.toFloat(), q.y.toFloat())
                        inversa.mapPoints(xy)
                        PointF(xy[0] / scala + area.left, xy[1] / scala + area.top)
                    })
                }
                if (codici.isNotEmpty()) return codici
            }
        }
        return emptyList()
    }

    /** Angoli di una riga letta in un pezzo (spostato di [pezzo] e ingrandito di [scala]), nelle coordinate della foto. */
    private fun angoliDi(riga: Text.Line, pezzo: Rect, scala: Float): List<PointF>? =
        riga.cornerPoints?.takeIf { it.size == 4 }?.map { PointF(it.x / scala + pezzo.left, it.y / scala + pezzo.top) }

    /** Un punto del ritaglio (ingrandito e girato di [gradi]) riportato nelle coordinate della foto. */
    private fun dalRitaglio(x: Float, y: Float, ritaglio: Bitmap, area: Rect, gradi: Int): PointF {
        val xy = floatArrayOf(x, y)
        Matrix().also { matriceRotazione(ritaglio.width, ritaglio.height, gradi).invert(it) }.mapPoints(xy)
        val scala = ritaglio.width.toFloat() / area.width()
        return PointF(xy[0] / scala + area.left, xy[1] / scala + area.top)
    }

    /** Riquadro che contiene i 4 angoli. */
    private fun riquadroDi(angoli: List<PointF>) = Rect(
        angoli.minOf { it.x }.toInt(), angoli.minOf { it.y }.toInt(),
        angoli.maxOf { it.x }.toInt(), angoli.maxOf { it.y }.toInt(),
    )

    /** Un codice trovato nella foto dritta, con i 4 angoli della sua riga. */
    private class CodiceTrovato(val codice: String, val angoli: List<PointF>) {
        val altezza get() = hypot(angoli[3].x - angoli[0].x, angoli[3].y - angoli[0].y)
    }

    /** Altri cartellini trovati nella foto, più un riassunto per le prove ("cosa ha trovato e cosa ha scartato"). */
    private class AltriCartellini(val dati: List<DatiCartellino>, val diagnostica: String, val etichetteViste: Int = 0)

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
            if (c.altezza < altezzaPrincipale / 4) return segna("piccoli")   // sullo sfondo (es. scaffali)
            // Nello stesso punto di un cartellino già preso = stesso cartellino letto con una cifra sbagliata
            if (posizioni.any { p -> hypot(p.x - c.angoli[0].x, p.y - c.angoli[0].y) < 3 * altezzaPrincipale }) return segna("doppi")
            trovati[c.codice] = c
            posizioni += c.angoli[0]
            segna(metodo)
        }

        // 1. Giro veloce
        val tutta = Rect(0, 0, dritta.width, dritta.height)
        codiciABarre(dritta, tutta, 1f).forEach { aggiungi(it, "barre") }
        val testoIntero = leggi(dritta, "altri: foto intera")
        codiciNelTesto(testoIntero, tutta, 1f).forEach { aggiungi(it, "testo") }
        val prezzi = prezziNelTesto(testoIntero, tutta, 1f).toMutableList()   // segnaposto dei cartellini

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
                val testo = leggi(pezzo, "altri: tassello")
                codiciNelTesto(testo, t, scala).forEach { aggiungi(it, "tasselli") }
                zone += zoneTesto(testo, t, scala)
                prezzi += prezziNelTesto(testo, t, scala)
            }

            // 3. Ogni prezzo letto segna un cartellino: ritaglio attorno al prezzo, raddrizzo, ingrandisco, cerco il codice
            val prezziProvati = mutableListOf<PointF>()
            for (p in prezzi) {
                if (prezziProvati.size >= MAX_PREZZI) break
                val centro = p.centro
                // stesso prezzo letto due volte (foto intera + tassello): una prova basta
                if (prezziProvati.any { hypot(it.x - centro.x, it.y - centro.y) < 2 * p.altezza }) continue
                prezziProvati += centro
                codiciAttornoAlPrezzo(dritta, p).forEach { aggiungi(it, "prezzi") }
            }
            if (prezziProvati.isNotEmpty()) conta["prezzi provati"] = prezziProvati.size

            // 4. Etichette trovate dalla forma (rettangoli bianchi) che non contengono un cartellino già preso
            var etichetteProvate = 0
            for (r in etichetteIn(dritta)) {
                if (etichetteProvate >= MAX_ETICHETTE) break
                if (posizioni.any { p -> r.contains(p.x.toInt(), p.y.toInt()) }) continue
                etichetteProvate++
                codiciNelRitaglio(dritta, r, listOf(0, 90)).forEach { aggiungi(it, "etichette") }
            }
            if (etichetteProvate > 0) conta["etichette provate"] = etichetteProvate

            // 5. Zone con testo illeggibile lontane dai cartellini già presi: ritaglio, ingrandisco, rileggo
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
                    leggi(girato, "altri: zona $g°").textBlocks.flatMap { it.lines }.forEach { l ->
                        val codice = REGEX_CODICE.find(LettoreCartellino.cifre(l.text))?.value ?: return@forEach
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
            .map { c -> leggiCartellino(dritta, c.angoli, c.codice)?.copy(codice = c.codice) ?: DatiCartellino(c.codice, null, null, null) }
        val diagnostica = if (conta.isEmpty()) "" else "altri: " + conta.entries.joinToString(", ") { "${it.key} ${it.value}" }
        // Quanti cartellini si vedono nella foto: ogni prezzo letto (non minuscolo, non doppio) è un cartellino
        val prezziDistinti = mutableListOf<PrezzoTrovato>()
        for (p in prezzi) {
            if (p.altezza < altezzaPrincipale) continue   // prezzo minuscolo: cartellino sullo sfondo
            val c = p.centro
            if (prezziDistinti.none { q -> hypot(q.centro.x - c.x, q.centro.y - c.y) < 3 * max(p.altezza, q.altezza) }) prezziDistinti += p
        }
        return AltriCartellini(dati, diagnostica + (if (prezziDistinti.isNotEmpty()) " · prezzi visti ${prezziDistinti.size}" else ""), prezziDistinti.size)
    }

    /** Un prezzo ("1,50") letto nella foto: segna dove sta un cartellino. */
    private class PrezzoTrovato(val angoli: List<PointF>) {
        val altezza get() = hypot(angoli[3].x - angoli[0].x, angoli[3].y - angoli[0].y).coerceAtLeast(1f)
        val centro get() = PointF((angoli[0].x + angoli[2].x) / 2, (angoli[0].y + angoli[2].y) / 2)
        /** Inclinazione della scritta del prezzo, in gradi (qualsiasi, anche capovolta). */
        val inclinazione get() = Math.toDegrees(atan2((angoli[1].y - angoli[0].y).toDouble(), (angoli[1].x - angoli[0].x).toDouble())).toFloat()
    }

    /** Righe con un prezzo (qualsiasi inclinazione), riportate nelle coordinate della foto. */
    private fun prezziNelTesto(testo: Text, pezzo: Rect, scala: Float): List<PrezzoTrovato> =
        testo.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            if (!REGEX_PREZZO.containsMatchIn(l.text)) return@mapNotNull null
            val angoli = l.cornerPoints?.takeIf { it.size == 4 } ?: return@mapNotNull null
            PrezzoTrovato(angoli.map { PointF(it.x / scala + pezzo.left, it.y / scala + pezzo.top) })
        }

    /**
     * Ritaglia un quadrato attorno al prezzo (il codice sta in alto a destra, entro ~8 altezze del prezzo),
     * lo raddrizza con l'inclinazione del prezzo, lo ingrandisce e cerca i codici.
     */
    private suspend fun codiciAttornoAlPrezzo(dritta: Bitmap, p: PrezzoTrovato): List<CodiceTrovato> {
        val c = p.centro
        val meta = 10 * p.altezza   // misurato su Inside Out: il codice sta a ~7,5 altezze del prezzo
        val area = Rect((c.x - meta).toInt(), (c.y - meta).toInt(), (c.x + meta).toInt(), (c.y + meta).toInt())
        if (!area.intersect(0, 0, dritta.width, dritta.height)) return emptyList()

        // Ingrandisco perché il prezzo sia alto ~80 px (il codice viene ~25-30 px) e raddrizzo
        val scala = (80f / p.altezza).coerceIn(0.5f, 4f)
        val m = Matrix().apply { postScale(scala, scala); postRotate(-p.inclinazione) }
        val bordi = RectF(0f, 0f, area.width().toFloat(), area.height().toFloat())
        Matrix(m).mapRect(bordi)
        m.postTranslate(-bordi.left, -bordi.top)   // come fa createBitmap
        val pezzo = Bitmap.createBitmap(dritta, area.left, area.top, area.width(), area.height(), m, true)
        val inversa = Matrix().also { m.invert(it) }

        return leggi(pezzo, "altri: attorno al prezzo").textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val codice = REGEX_CODICE.find(LettoreCartellino.cifre(l.text))?.value ?: return@mapNotNull null
            val angoli = l.cornerPoints?.takeIf { it.size == 4 } ?: return@mapNotNull null
            CodiceTrovato(codice, angoli.map { q ->
                val xy = floatArrayOf(q.x.toFloat(), q.y.toFloat())
                inversa.mapPoints(xy)
                PointF(xy[0] + area.left, xy[1] + area.top)
            })
        }
    }

    /** Righe col codice (con qualsiasi inclinazione) lette in un pezzo di foto, riportate nelle coordinate della foto. */
    private fun codiciNelTesto(testo: Text, pezzo: Rect, scala: Float): List<CodiceTrovato> =
        testo.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val codice = REGEX_CODICE.find(LettoreCartellino.cifre(l.text))?.value ?: return@mapNotNull null
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
        private const val MAX_ETICHETTE = 10     // etichette (trovate dalla forma) lette al massimo per foto
        private const val LATO_ETICHETTA = 1400  // ogni etichetta viene ingrandita a questa misura prima di leggerla
        private const val MAX_PREZZI = 15
        private const val MAX_PREZZI_PRIMO = 6   // prezzi provati come segnaposto per il primo cartellino        // prezzi usati come segnaposto (foto con tanti articoli)
        private const val MAX_ZONE_ALTRI = 12    // zone ingrandite per cercare gli altri cartellini (foto con tanti articoli)           // zone ingrandite al massimo per ogni giro (tiene basso il tempo)

        private val SETTE_CIFRE = Regex("\\d{7}")
        // Prezzo tipo "1,50" o "12.00" (anche con spazi: "2, 00")
        private val REGEX_PREZZO = Regex("(?<![\\d/])\\d{1,3}\\s?[,.]\\s?\\d{2}(?!\\d)")
        // 7 cifre esatte, non attaccate ad altre cifre (esclude i codici EAN a 13 cifre)
        private val REGEX_CODICE = Regex("(?<!\\d)\\d{7}(?!\\d)")

        /** Gira la foto in senso orario di [gradi] (multipli di 90). */
        fun ruotaImmagine(foto: Bitmap, gradi: Int): Bitmap {
            if (gradi % 360 == 0) return foto
            val matrice = Matrix().apply { postRotate(gradi.toFloat()) }
            return Bitmap.createBitmap(foto, 0, 0, foto.width, foto.height, matrice, true)
        }

        /** Salva la foto raddrizzata come JPEG nella cartella temporanea dell'app. */
        /** Cartella delle foto sistemate: nei file dell'app (non nella cache), così restano anche chiudendo l'app. */
        fun cartella(context: Context) = File(context.filesDir, "raddrizzate").apply { mkdirs() }

        fun salva(context: Context, foto: Bitmap, nome: String): File {
            val file = File(cartella(context), "$nome.jpg")
            file.outputStream().use { foto.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            return file
        }
    }
}
