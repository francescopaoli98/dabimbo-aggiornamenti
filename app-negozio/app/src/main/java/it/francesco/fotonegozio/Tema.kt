package it.francesco.fotonegozio

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Colori presi dal logo "Da bimbo a bimbo"
val BluNotte = Color(0xFF1D2F8F)    // contorno del logo
val Azzurro = Color(0xFF1E9AD6)     // metà bassa delle lettere
val Cielo = Color(0xFFA8D8F2)       // nuvole celesti
val Rosa = Color(0xFFF6C1CC)        // metà alta delle lettere
val Sfondo = Color(0xFFF4FAFE)      // bianco-celeste delle nuvole
val SfondoLista = Color(0xFFDCEBF7) // dietro le schede: abbastanza scuro da staccarle bene
val BordoScheda = Color(0xFFB9D3EC) // bordo delle schede
val Arancione = Color(0xFFE65100)   // avvisi
val Verde = Color(0xFF2E7D32)       // pubblicata

private val colori = lightColorScheme(
    primary = BluNotte,
    onPrimary = Color.White,
    primaryContainer = Cielo,
    onPrimaryContainer = BluNotte,
    secondary = Azzurro,
    onSecondary = Color.White,
    secondaryContainer = Rosa,
    onSecondaryContainer = BluNotte,
    background = Sfondo,
    onBackground = Color(0xFF1B1F2A),
    surface = Color.White,
    onSurface = Color(0xFF1B1F2A),
    surfaceVariant = Color(0xFFEAF5FC),
    onSurfaceVariant = Color(0xFF3A4660),
    outline = Cielo,
)

private val forme = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
)

/** Tema dell'app: sempre chiaro, colori del logo, angoli morbidi. */
@Composable
fun TemaBimbo(contenuto: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colori, shapes = forme, content = contenuto)
