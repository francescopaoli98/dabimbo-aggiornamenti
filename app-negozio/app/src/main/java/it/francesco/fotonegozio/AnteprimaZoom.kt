package it.francesco.fotonegozio

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Un pezzo della foto originale, caricato a piena risoluzione: [zona] = dove sta nella foto vera. */
class Pezzo(val immagine: ImageBitmap, val zona: Rect)

/** Grandezza vera di una foto (si legge solo l'intestazione del file: velocissimo). */
@Composable
fun misureVere(file: File?): IntSize? = remember(file) {
    file?.let { BitmapFactory.Options().apply { inJustDecodeBounds = true; BitmapFactory.decodeFile(it.path, this) } }
        ?.takeIf { it.outWidth > 0 }?.let { IntSize(it.outWidth, it.outHeight) }
}

/**
 * Nitidezza a risoluzione originale mentre si ingrandisce: carica dalla foto VERA solo il pezzo inquadrato.
 * Veloce perché:
 * - il "lettore" della foto originale si apre una volta sola e resta pronto ([prepara] lo apre in anticipo);
 * - si ricarica appena le dita si fermano un attimo (2 centesimi di secondo), tenendo intanto il pezzo di prima.
 */
class Nitidezza(private val file: File?, private val misure: IntSize?) {
    private var lettore: BitmapRegionDecoder? = null
    private val blocco = Mutex()
    var pezzo by mutableStateOf<Pezzo?>(null)

    /** Apre la foto originale in sottofondo (da chiamare appena si appoggia un dito). */
    suspend fun prepara() = blocco.withLock { apri() }

    @Suppress("DEPRECATION")
    private suspend fun apri(): BitmapRegionDecoder? {
        lettore?.let { return it }
        val f = file ?: return null
        return withContext(Dispatchers.IO) { runCatching { BitmapRegionDecoder.newInstance(f.path, false) }.getOrNull() }
            .also { lettore = it }
    }

    /** Carica nitido il pezzo visibile per questo zoom (scala intorno al centro + spostamento). */
    suspend fun aggiorna(scala: Float, spostamento: Offset, riquadro: IntSize) {
        val m = misure ?: return
        if (scala < 1.05f || riquadro == IntSize.Zero) { pezzo = null; return }
        delay(20)   // le dita si sono fermate un attimo (se si muovono ancora, questo viene annullato)
        val zona = zonaVisibile(m, riquadro, scala, spostamento) ?: return
        // Pixel della foto per ogni pixel dello schermo: carico tutti quelli che lo schermo può mostrare
        val schermoPerFoto = adattamento(m, riquadro) * scala
        var campione = 1
        while (campione * 2 * schermoPerFoto <= 1f) campione *= 2
        val nuovo = blocco.withLock {
            val l = apri() ?: return
            withContext(Dispatchers.IO) {
                runCatching { l.decodeRegion(zona, BitmapFactory.Options().apply { inSampleSize = campione }) }.getOrNull()
            }
        } ?: return
        pezzo = Pezzo(nuovo.asImageBitmap(), zona)
    }

    fun chiudi() { lettore?.recycle(); lettore = null }
}

@Composable
fun ricordaNitidezza(file: File?): Nitidezza {
    val misure = misureVere(file)
    val n = remember(file) { Nitidezza(file, misure) }
    DisposableEffect(n) { onDispose { n.chiudi() } }
    return n
}

/** Disegna l'immagine [img] (foto intera, anche rimpicciolita) come la mostra lo zoom: scala [s] intorno al centro + spostamento [p]. */
fun DrawScope.disegnaZoom(img: ImageBitmap, vera: IntSize, s: Float, p: Offset, zona: Rect? = null) {
    val box = IntSize(size.width.roundToInt(), size.height.roundToInt())
    val f = adattamento(vera, box)
    val ox = (box.width - vera.width * f) / 2; val oy = (box.height - vera.height * f) / 2
    val c = Offset(box.width / 2f, box.height / 2f)
    fun aSchermo(x: Float, y: Float) = c + (Offset(ox + x * f, oy + y * f) - c) * s + p
    val r = zona ?: Rect(0, 0, vera.width, vera.height)
    val a = aSchermo(r.left.toFloat(), r.top.toFloat()); val b = aSchermo(r.right.toFloat(), r.bottom.toFloat())
    drawImage(
        img, dstOffset = IntOffset(a.x.roundToInt(), a.y.roundToInt()),
        dstSize = IntSize((b.x - a.x).roundToInt(), (b.y - a.y).roundToInt()),
        filterQuality = FilterQuality.Medium,
    )
}

/**
 * Anteprima nella lista: con due dita si ingrandisce (finché le dita sono giù), poi torna com'era.
 * Mentre si ingrandisce, il pezzo inquadrato arriva nitido dalla foto originale.
 * Un dito solo resta alla lista (scorre normalmente); il tocco apre la foto grande.
 */
@Composable
fun AnteprimaZoomabile(miniatura: ImageBitmap, file: File?, modifier: Modifier = Modifier) {
    var attivo by remember { mutableStateOf(false) }
    var scala by remember { mutableFloatStateOf(1f) }
    var spostamento by remember { mutableStateOf(Offset.Zero) }
    var riquadro by remember { mutableStateOf(IntSize.Zero) }
    val nitidezza = ricordaNitidezza(file)
    val misure = misureVere(file)
    val scope = rememberCoroutineScope()

    // Mentre pizzica segue le dita; quando lascia torna dolcemente a posto
    val s by animateFloatAsState(if (attivo) scala else 1f, if (attivo) snap() else spring(), label = "zoom")
    val p by animateOffsetAsState(if (attivo) spostamento else Offset.Zero, if (attivo) snap() else spring(), label = "sposta")

    LaunchedEffect(attivo, scala, spostamento, riquadro) {
        if (!attivo) nitidezza.pezzo = null else nitidezza.aggiorna(scala, spostamento, riquadro)
    }

    Canvas(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                riquadro = size
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    scope.launch { nitidezza.prepara() }   // apro la foto originale già al primo dito
                    do {
                        val evento = awaitPointerEvent()
                        if (evento.changes.count { it.pressed } >= 2) {
                            attivo = true
                            scala = (scala * evento.calculateZoom()).coerceIn(1f, 8f)
                            spostamento += evento.calculatePan()
                            evento.changes.forEach { it.consume() }   // la lista non scorre e il tocco non apre la foto
                        }
                    } while (evento.changes.any { it.pressed })
                    attivo = false; scala = 1f; spostamento = Offset.Zero
                }
            }
    ) {
        val vera = misure ?: IntSize(miniatura.width, miniatura.height)
        disegnaZoom(miniatura, vera, s, p)
        if (attivo) nitidezza.pezzo?.let { disegnaZoom(it.immagine, vera, s, p, it.zona) }
    }
}

/** Scala con cui la foto intera entra nel riquadro (come ContentScale.Fit). */
private fun adattamento(foto: IntSize, box: IntSize) = min(box.width / foto.width.toFloat(), box.height / foto.height.toFloat())

/** La parte della foto vera che si vede nel riquadro con questo zoom (null se fuori dalla foto). */
internal fun zonaVisibile(foto: IntSize, box: IntSize, scala: Float, spostamento: Offset): Rect? {
    val f = adattamento(foto, box)
    val ox = (box.width - foto.width * f) / 2; val oy = (box.height - foto.height * f) / 2
    val cx = box.width / 2f; val cy = box.height / 2f
    // Dallo schermo alla foto: si toglie lo spostamento, si "sgrandisce" attorno al centro, si toglie il margine
    fun fotoX(q: Float) = ((cx + (q - spostamento.x - cx) / scala) - ox) / f
    fun fotoY(q: Float) = ((cy + (q - spostamento.y - cy) / scala) - oy) / f
    val l = max(0, fotoX(0f).toInt()); val t = max(0, fotoY(0f).toInt())
    val r = min(foto.width, fotoX(box.width.toFloat()).toInt() + 1); val b = min(foto.height, fotoY(box.height.toFloat()).toInt() + 1)
    return if (r > l && b > t) Rect(l, t, r, b) else null
}
