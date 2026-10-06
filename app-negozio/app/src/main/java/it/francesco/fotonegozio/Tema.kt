package it.francesco.fotonegozio

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Chiaro o scuro (modalità notte), scelto nelle impostazioni.
 * I colori qui sotto leggono questo stato: quando cambia, tutta l'app si ridisegna da sola.
 */
object TemaApp {
    /** 0 = chiaro, 1 = scuro, 2 = come il telefono. */
    var modo by mutableIntStateOf(0)
    /** Il telefono è in modalità scura (si aggiorna quando cambia). */
    var telefonoScuro by mutableStateOf(false)
    val scuro get() = modo == 1 || (modo == 2 && telefonoScuro)
}

private fun c(chiaro: Long, scuro: Long) = Color(if (TemaApp.scuro) scuro else chiaro)

// Colori presi dal logo "Da bimbo a bimbo" (a destra la versione per la modalità scura)
val BluNotte get() = c(0xFF1D2F8F, 0xFFA9C4FF)     // contorno del logo / titoli
val Azzurro get() = c(0xFF1E9AD6, 0xFF1C86BE)      // metà bassa delle lettere / pulsanti
val Cielo get() = c(0xFFA8D8F2, 0xFF2B4766)        // nuvole celesti
val Rosa get() = c(0xFFF6C1CC, 0xFF5B3443)         // metà alta delle lettere
val Sfondo get() = c(0xFFF4FAFE, 0xFF11161F)       // bianco-celeste delle nuvole
val SfondoLista get() = c(0xFFDCEBF7, 0xFF0C1118)  // dietro le schede
val BordoScheda get() = c(0xFFB9D3EC, 0xFF2C3B52)  // bordo delle schede
val Arancione get() = c(0xFFE65100, 0xFFFF8A3D)    // avvisi
val Verde get() = c(0xFF2E7D32, 0xFF3FA046)        // pubblicata

// Colori "di servizio"
val Superficie get() = c(0xFFFFFFFF, 0xFF1A2231)   // schede, pannelli, pulsanti chiari
val FondoTenue get() = c(0xFFEAF5FC, 0xFF223049)   // riquadri leggeri (testo, foto)
val FondoAvviso get() = c(0xFFFFEFE3, 0xFF3A2618)  // dietro le scritte arancioni
val Testo get() = c(0xFF1B1F2A, 0xFFE6EBF5)        // testo normale
val TestoTenue get() = c(0xFF3A4660, 0xFFA3AEC4)   // spiegazioni, note
val NuvoleSfondo get() = if (TemaApp.scuro) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.55f)

private val forme = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
)

/** Tema dell'app: colori del logo, angoli morbidi; chiaro o scuro secondo le impostazioni. */
@Composable
fun TemaBimbo(contenuto: @Composable () -> Unit) {
    val schema = if (TemaApp.scuro) darkColorScheme(
        primary = BluNotte, onPrimary = Color(0xFF0C1118),
        primaryContainer = Cielo, onPrimaryContainer = BluNotte,
        secondary = Azzurro, onSecondary = Color.White,
        secondaryContainer = Rosa, onSecondaryContainer = BluNotte,
        background = Sfondo, onBackground = Testo,
        surface = Superficie, onSurface = Testo,
        surfaceVariant = FondoTenue, onSurfaceVariant = TestoTenue,
        surfaceContainerHigh = Superficie, surfaceContainerHighest = FondoTenue,
        outline = Cielo,
    ) else lightColorScheme(
        primary = BluNotte, onPrimary = Color.White,
        primaryContainer = Cielo, onPrimaryContainer = BluNotte,
        secondary = Azzurro, onSecondary = Color.White,
        secondaryContainer = Rosa, onSecondaryContainer = BluNotte,
        background = Sfondo, onBackground = Testo,
        surface = Superficie, onSurface = Testo,
        surfaceVariant = FondoTenue, onSurfaceVariant = TestoTenue,
        outline = Cielo,
    )
    MaterialTheme(colorScheme = schema, shapes = forme) {
        // Testo leggibile anche sugli sfondi "nostri" (fuori dallo schema)
        CompositionLocalProvider(LocalContentColor provides Testo, content = contenuto)
    }
}
