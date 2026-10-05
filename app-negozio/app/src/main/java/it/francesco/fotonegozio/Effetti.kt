package it.francesco.fotonegozio

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

/** Nuvolette bianche sullo sfondo, come nel logo. Ferme: si disegnano una volta sola (leggero). */
@Composable
fun SfondoNuvole(modifier: Modifier = Modifier) {
    val tempo = 0.2f
    // posizione verticale (0..1), grandezza, velocità
    val nuvole = remember { List(7) { i -> Triple(0.06f + i * 0.14f, 0.7f + (i % 3) * 0.25f, 0.6f + (i % 4) * 0.25f) } }
    Canvas(modifier.fillMaxSize()) {
        nuvole.forEachIndexed { i, (y, grandezza, velocita) ->
            val r = 34.dp.toPx() * grandezza
            val larghezza = size.width + 8 * r
            val x = ((tempo * velocita + i * 0.37f) % 1f) * larghezza - 4 * r
            val cy = size.height * y
            val bianco = Color.White.copy(alpha = 0.55f)
            // una nuvola = 4 cerchi sovrapposti
            drawCircle(bianco, r, Offset(x, cy))
            drawCircle(bianco, r * 1.3f, Offset(x + r * 1.2f, cy - r * 0.5f))
            drawCircle(bianco, r * 1.1f, Offset(x + r * 2.5f, cy - r * 0.1f))
            drawRoundRect(bianco, Offset(x - r * 0.3f, cy - r * 0.2f), Size(r * 3.4f, r * 1.2f), CornerRadius(r, r))
        }
    }
}

/** Pulsante "morbido": premuto si schiaccia un po' e torna su con un rimbalzo. */
@Composable
fun rimbalzo(): Pair<MutableInteractionSource, Modifier> {
    val sorgente = remember { MutableInteractionSource() }
    val premuto by sorgente.collectIsPressedAsState()
    val scala by animateFloatAsState(
        if (premuto) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "rimbalzo",
    )
    return sorgente to Modifier.graphicsLayer { scaleX = scala; scaleY = scala }
}

/** Entrata della scheda: dal basso, con un piccolo rimbalzo. */
@Composable
fun Modifier.entrata(): Modifier {
    val v = remember { Animatable(0f) }
    LaunchedEffect(Unit) { v.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
    return graphicsLayer {
        alpha = v.value.coerceIn(0f, 1f)
        translationY = (1f - v.value) * 60.dp.toPx()
        val s = 0.95f + 0.05f * v.value
        scaleX = s; scaleY = s
    }
}

/** Riflesso luccicante che scorre (al posto della rotellina mentre la foto è in lavorazione). */
@Composable
fun Luccichio(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "luccichio").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "t",
    )
    Canvas(modifier.fillMaxSize()) {
        val x = t * size.width
        drawRect(
            Brush.linearGradient(
                listOf(Cielo.copy(alpha = 0.25f), Color.White.copy(alpha = 0.9f), Cielo.copy(alpha = 0.25f)),
                start = Offset(x - size.width * 0.4f, 0f), end = Offset(x + size.width * 0.4f, size.height),
            )
        )
    }
}

/**
 * Barra di caricamento a nuvolette: una fila di nuvole bianche che si riempiono di colore
 * da sinistra (dal rosa all'azzurro, come le lettere del logo). Ogni nuvola piena fa "boing".
 */
@Composable
fun NuvoleAvanzamento(avanzamento: Float, modifier: Modifier = Modifier, quante: Int = 8) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(quante) { i ->
            val pieno = (avanzamento * quante - i).coerceIn(0f, 1f)
            val colore = lerp(Rosa, Azzurro, i / (quante - 1f).coerceAtLeast(1f))
            Nuvoletta(pieno, colore, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Nuvoletta(pieno: Float, colore: Color, modifier: Modifier) {
    val scala by animateFloatAsState(
        if (pieno >= 1f) 1f else 0.86f,
        spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow),
        label = "boing",
    )
    Canvas(modifier.aspectRatio(1.5f).graphicsLayer { scaleX = scala; scaleY = scala }) {
        val w = size.width; val h = size.height
        // una nuvola = 3 cerchi + base arrotondata
        fun nuvola(colore: Color, dy: Float = 0f) {
            drawCircle(colore, h * 0.30f, Offset(w * 0.30f, h * 0.58f + dy))
            drawCircle(colore, h * 0.40f, Offset(w * 0.52f, h * 0.44f + dy))
            drawCircle(colore, h * 0.28f, Offset(w * 0.74f, h * 0.60f + dy))
            drawRoundRect(colore, Offset(w * 0.12f, h * 0.55f + dy), Size(w * 0.76f, h * 0.33f), CornerRadius(h * 0.16f, h * 0.16f))
        }
        nuvola(BluNotte.copy(alpha = 0.18f), dy = h * 0.06f)   // ombra morbida
        nuvola(Color.White)
        if (pieno > 0f) clipRect(right = w * (0.1f + 0.8f * pieno)) { nuvola(colore) }
    }
}

/** Coriandoli rosa, azzurri e blu che cadono su tutto lo schermo (quando sono tutte pubblicate). */
@Composable
fun Coriandoli(modifier: Modifier = Modifier) {
    val avanzamento = remember { Animatable(0f) }
    LaunchedEffect(Unit) { avanzamento.animateTo(1f, tween(3200, easing = LinearEasing)) }
    val colori = listOf(Rosa, Azzurro, BluNotte, Cielo, Color(0xFFFFD54F))
    val pezzi = remember {
        val r = Random(7)
        List(140) {
            floatArrayOf(r.nextFloat(), -r.nextFloat() * 0.6f, 0.6f + r.nextFloat() * 0.9f, r.nextFloat() * 360f, (r.nextFloat() - 0.5f) * 720f, r.nextFloat())
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = avanzamento.value
        pezzi.forEachIndexed { i, p ->
            val x0 = p[0]; val y0 = p[1]; val vel = p[2]; val rot0 = p[3]; val giro = p[4]; val oscilla = p[5]
            val y = (y0 + t * vel * 1.6f) * size.height
            if (y < -20 || y > size.height + 20) return@forEachIndexed
            val x = x0 * size.width + sin((t * 8 + oscilla * 6).toDouble()).toFloat() * 18.dp.toPx()
            val alfa = if (t > 0.8f) (1f - t) / 0.2f else 1f
            rotate(rot0 + giro * t, Offset(x, y)) {
                drawRect(colori[i % colori.size].copy(alpha = alfa), Offset(x - 5.dp.toPx(), y - 3.dp.toPx()), Size(10.dp.toPx(), 6.dp.toPx()))
            }
        }
    }
}
