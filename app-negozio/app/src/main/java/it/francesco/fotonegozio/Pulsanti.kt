package it.francesco.fotonegozio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Pulsanti dell'app, tutti dello stesso stile: angoli morbidi, colori del logo,
 * icona in un cerchietto, e un piccolo rimbalzo quando si premono.
 */

/** Pulsante pieno, per l'azione principale (Scegli foto, Pubblica…). */
@Composable
fun PulsanteGrande(
    testo: String,
    icona: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colore: Color = Azzurro,
    attivo: Boolean = true,
    altezza: Dp = 56.dp,
    grandezzaTesto: Int = 17,
) {
    val (sorgente, morbido) = rimbalzo()
    val forma = RoundedCornerShape(22.dp)
    Surface(
        onClick = onClick,
        enabled = attivo,
        shape = forma,
        color = Color.Transparent,
        shadowElevation = if (attivo) 3.dp else 0.dp,
        interactionSource = sorgente,
        modifier = modifier.heightIn(min = altezza).then(morbido).alpha(if (attivo) 1f else 0.5f),
    ) {
        Row(
            Modifier
                // Sfumatura leggera dall'alto: un po' di "rilievo"
                .background(Brush.verticalGradient(listOf(lerp(colore, Color.White, 0.18f), colore)), forma)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Cerchietto(icona, Color.White.copy(alpha = 0.25f))
            Spacer(Modifier.width(10.dp))
            Text(testo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = grandezzaTesto.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Pulsante chiaro, per le azioni secondarie (Articoli, Sigle…). */
@Composable
fun PulsanteChiaro(
    testo: String,
    icona: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colore: Color = BluNotte,
    sfondo: Color = Color.White,
    attivo: Boolean = true,
    altezza: Dp = 56.dp,
    grandezzaTesto: Int = 16,
) {
    val (sorgente, morbido) = rimbalzo()
    Surface(
        onClick = onClick,
        enabled = attivo,
        shape = RoundedCornerShape(22.dp),
        color = sfondo,
        border = BorderStroke(1.5.dp, colore.copy(alpha = 0.28f)),
        interactionSource = sorgente,
        modifier = modifier.heightIn(min = altezza).then(morbido).alpha(if (attivo) 1f else 0.5f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Cerchietto(icona, colore.copy(alpha = 0.10f))
            Spacer(Modifier.width(8.dp))
            Text(testo, color = colore, fontWeight = FontWeight.Bold, fontSize = grandezzaTesto.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Pulsante rotondo con solo il simbolo (✕, ⚙…). */
@Composable
fun PulsanteTondo(simbolo: String, onClick: () -> Unit, modifier: Modifier = Modifier, colore: Color = BluNotte) {
    val (sorgente, morbido) = rimbalzo()
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 3.dp,
        interactionSource = sorgente,
        modifier = modifier.size(46.dp).then(morbido),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(simbolo, fontSize = 20.sp, color = colore, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Cerchietto(icona: String, sfondo: Color) {
    Box(Modifier.size(30.dp).background(sfondo, CircleShape), contentAlignment = Alignment.Center) {
        Text(icona, fontSize = 15.sp)
    }
}
