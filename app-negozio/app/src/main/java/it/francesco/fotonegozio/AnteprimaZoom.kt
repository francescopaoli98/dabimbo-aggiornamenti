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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Un pezzo della foto originale, caricato a piena risoluzione: [zona] = dove sta nella foto vera. */
private class Pezzo(val immagine: ImageBitmap, val zona: Rect)

/**
 * Anteprima nella lista: con due dita si ingrandisce (finché le dita sono giù), poi torna com'era.
 * Mentre si ingrandisce, l'app carica dalla foto ORIGINALE solo il pezzo inquadrato,
 * così i dettagli e l'etichetta si leggono nitidi invece che sgranati.
 * Un dito solo resta alla lista (scorre normalmente); il tocco apre la foto grande.
 */
@Composable
fun AnteprimaZoomabile(miniatura: ImageBitmap, file: File?, modifier: Modifier = Modifier) {
    var attivo by remember { mutableStateOf(false) }
    var scala by remember { mutableFloatStateOf(1f) }
    var spostamento by remember { mutableStateOf(Offset.Zero) }
    var riquadro by remember { mutableStateOf(IntSize.Zero) }
    var pezzo by remember(file) { mutableStateOf<Pezzo?>(null) }
    // Grandezza vera della foto (si legge solo l'intestazione del file: velocissimo)
    val misure = remember(file) {
        file?.let { BitmapFactory.Options().apply { inJustDecodeBounds = true; BitmapFactory.decodeFile(it.path, this) } }
            ?.takeIf { it.outWidth > 0 }?.let { IntSize(it.outWidth, it.outHeight) }
    }

    // Mentre pizzica segue le dita; quando lascia torna dolcemente a posto
    val s by animateFloatAsState(if (attivo) scala else 1f, if (attivo) snap() else spring(), label = "zoom")
    val p by animateOffsetAsState(if (attivo) spostamento else Offset.Zero, if (attivo) snap() else spring(), label = "sposta")

    // Appena le dita si fermano un attimo (0,1 s), carico nitido il pezzo inquadrato
    LaunchedEffect(attivo, scala, spostamento, riquadro) {
        if (!attivo) { pezzo = null; return@LaunchedEffect }
        if (file == null || misure == null || scala < 1.3f || riquadro == IntSize.Zero) return@LaunchedEffect
        delay(100)
        val zona = zonaVisibile(misure, riquadro, scala, spostamento) ?: return@LaunchedEffect
        // Pixel della foto per ogni pixel dello schermo: carico solo quelli che servono
        val schermoPerFoto = adattamento(misure, riquadro) * scala
        var campione = 1
        while (campione * 2 * schermoPerFoto <= 1f) campione *= 2
        pezzo = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                val decoder = BitmapRegionDecoder.newInstance(file.path, false)!!
                try {
                    decoder.decodeRegion(zona, BitmapFactory.Options().apply { inSampleSize = campione })?.let { Pezzo(it.asImageBitmap(), zona) }
                } finally { decoder.recycle() }
            }.getOrNull()
        } ?: pezzo
    }

    Canvas(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                riquadro = size
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
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
        val box = IntSize(size.width.roundToInt(), size.height.roundToInt())
        val vera = misure ?: IntSize(miniatura.width, miniatura.height)
        val f = adattamento(vera, box)
        // Dove finisce sullo schermo un punto (x, y) della foto vera
        fun aSchermo(x: Float, y: Float): Offset {
            val ox = (box.width - vera.width * f) / 2; val oy = (box.height - vera.height * f) / 2
            val c = Offset(box.width / 2f, box.height / 2f)
            return c + (Offset(ox + x * f, oy + y * f) - c) * s + p
        }
        val inizio = aSchermo(0f, 0f); val fine = aSchermo(vera.width.toFloat(), vera.height.toFloat())
        drawImage(
            miniatura, dstOffset = IntOffset(inizio.x.roundToInt(), inizio.y.roundToInt()),
            dstSize = IntSize((fine.x - inizio.x).roundToInt(), (fine.y - inizio.y).roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
        pezzo?.let { pz ->
            val a = aSchermo(pz.zona.left.toFloat(), pz.zona.top.toFloat())
            val b = aSchermo(pz.zona.right.toFloat(), pz.zona.bottom.toFloat())
            drawImage(
                pz.immagine, dstOffset = IntOffset(a.x.roundToInt(), a.y.roundToInt()),
                dstSize = IntSize((b.x - a.x).roundToInt(), (b.y - a.y).roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
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
