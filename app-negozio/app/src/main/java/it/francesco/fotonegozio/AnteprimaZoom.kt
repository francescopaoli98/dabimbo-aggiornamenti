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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
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

/** Come si comporta lo zoom con due dita nell'anteprima della lista (scelta nelle impostazioni). */
enum class ZoomAnteprima { RESTA, TORNA, SPENTO }

/**
 * Anteprima nella lista: con due dita si ingrandisce e si sposta.
 * - RESTA: togliendo le dita resta ingrandita; il pulsantino ↺ la rimette normale;
 * - TORNA: togliendo le dita torna com'era;
 * - SPENTO: niente zoom (la lista scorre e basta).
 * Il pezzo inquadrato arriva nitido dalla foto originale. Un dito solo resta sempre alla lista
 * (scorre normalmente); il tocco apre la foto grande.
 */
@Composable
fun AnteprimaZoomabile(miniatura: ImageBitmap, file: File?, modifier: Modifier = Modifier, modo: ZoomAnteprima = ZoomAnteprima.RESTA) {
    var dita by remember { mutableStateOf(false) }          // le due dita sono appoggiate adesso
    var scala by remember { mutableFloatStateOf(1f) }
    var spostamento by remember { mutableStateOf(Offset.Zero) }
    var riquadro by remember { mutableStateOf(IntSize.Zero) }
    val nitidezza = ricordaNitidezza(file)
    val misure = misureVere(file)
    val scope = rememberCoroutineScope()
    val ingrandita = scala > 1.01f

    // Segue le dita subito; quando si torna normali, ci torna dolcemente
    val s by animateFloatAsState(scala, if (dita) snap() else spring(), label = "zoom")
    val p by animateOffsetAsState(spostamento, if (dita) snap() else spring(), label = "sposta")

    LaunchedEffect(scala, spostamento, riquadro) {
        if (!ingrandita) nitidezza.pezzo = null else nitidezza.aggiorna(scala, spostamento, riquadro)
    }

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize()
                .clipToBounds()
                .then(if (modo == ZoomAnteprima.SPENTO) Modifier else Modifier.pointerInput(modo) {
                    riquadro = size
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val evento = awaitPointerEvent()
                            if (evento.changes.count { it.pressed } >= 2) {
                                // Due dita: apro la foto originale (una volta sola; scorrendo la lista con un dito no)
                                if (!dita && !ingrandita) scope.launch { nitidezza.prepara() }
                                dita = true
                                scala = (scala * evento.calculateZoom()).coerceIn(1f, 8f)
                                spostamento = if (scala <= 1.01f) Offset.Zero else spostamento + evento.calculatePan()
                                evento.changes.forEach { it.consume() }   // la lista non scorre e il tocco non apre la foto
                            }
                        } while (evento.changes.any { it.pressed })
                        dita = false
                        if (modo == ZoomAnteprima.TORNA) { scala = 1f; spostamento = Offset.Zero }
                    }
                })
        ) {
            val vera = misure ?: IntSize(miniatura.width, miniatura.height)
            disegnaZoom(miniatura, vera, s, p)
            if (scala > 1.01f) nitidezza.pezzo?.let { disegnaZoom(it.immagine, vera, s, p, it.zona) }
        }
        // ↺: torna normale (solo se resta ingrandita)
        if (ingrandita && !dita) Surface(
            onClick = { scala = 1f; spostamento = Offset.Zero },
            shape = CircleShape, color = Superficie.copy(alpha = 0.92f), shadowElevation = 2.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(38.dp).testTag("zoom_normale"),
        ) { Box(contentAlignment = Alignment.Center) { Text("↺", fontSize = 18.sp, color = BluNotte, fontWeight = FontWeight.Bold) } }
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
