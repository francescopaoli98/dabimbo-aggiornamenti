package it.francesco.fotonegozio

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

/** Nuvolette bianche che scorrono lentissime sullo sfondo, come nel logo. */
@Composable
fun SfondoNuvole(modifier: Modifier = Modifier) {
    val tempo by rememberInfiniteTransition(label = "nuvole").animateFloat(
        0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "t",
    )
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

/** Fa "galleggiare" piano (su e giù). */
@Composable
fun Modifier.galleggia(ampiezza: Dp = 4.dp, durataMs: Int = 2600): Modifier {
    val v by rememberInfiniteTransition(label = "galleggia").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(durataMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "v",
    )
    val px = with(LocalDensity.current) { ampiezza.toPx() }
    return graphicsLayer { translationY = v * px }
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

/** Pulsa piano (per attirare l'attenzione sul pulsante giusto). */
@Composable
fun Modifier.pulsa(attivo: Boolean): Modifier {
    val v by rememberInfiniteTransition(label = "pulsa").animateFloat(
        1f, 1.035f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "v",
    )
    return if (attivo) graphicsLayer { scaleX = v; scaleY = v } else this
}

/** Entrata della scheda: dal basso, con un piccolo rimbalzo. */
@Composable
fun Modifier.entrata(): Modifier {
    val v = remember { Animatable(0f) }
    LaunchedEffect(Unit) { v.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
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

/** Il bimbo del logo che gattona: [verso] 1 = verso destra, -1 = verso sinistra. Dondola un po' mentre "cammina". */
@Composable
fun BimboCheGattona(altezza: Dp, verso: Int, modifier: Modifier = Modifier) {
    val passo by rememberInfiniteTransition(label = "passo").animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(700, easing = LinearEasing)), label = "p",
    )
    Image(
        painterResource(R.drawable.bimbo), null,
        modifier.height(altezza).graphicsLayer {
            scaleX = if (verso > 0) -1f else 1f    // nel logo guarda a sinistra
            translationY = sin(passo) * 2.dp.toPx()
            rotationZ = sin(passo) * 2.5f
        },
    )
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
