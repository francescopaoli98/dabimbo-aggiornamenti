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
    /** Colori: 0 Da bimbo a bimbo, 1 Salvia, 2 Lavanda, 3 Sabbia e mare, 4 Nuvola. */
    var tavolozza by mutableIntStateOf(0)
    val scuro get() = modo == 1 || (modo == 2 && telefonoScuro)
}

/** I colori di un tema (in ordine: vedi i nomi qui sotto). */
private class Tavolozza(
    val titoli: Long, val accento: Long, val tenue: Long, val secondario: Long,
    val sfondo: Long, val sfondoLista: Long, val bordo: Long, val avviso: Long, val ok: Long,
    val superficie: Long, val fondoTenue: Long, val fondoAvviso: Long, val testo: Long, val testoTenue: Long,
)

/** Nome e simbolo dei temi, per le impostazioni. */
val NOMI_TAVOLOZZE = listOf("🧸 Da bimbo a bimbo", "🌿 Salvia", "💜 Lavanda", "🏖 Sabbia e mare", "☁️ Nuvola")

// Per ogni tema: versione chiara e versione scura
private val TAVOLOZZE: List<Pair<Tavolozza, Tavolozza>> = listOf(
    // Da bimbo a bimbo: i colori del logo
    Tavolozza(0xFF1D2F8F, 0xFF1E9AD6, 0xFFA8D8F2, 0xFFF6C1CC, 0xFFF4FAFE, 0xFFDCEBF7, 0xFFB9D3EC, 0xFFE65100, 0xFF2E7D32, 0xFFFFFFFF, 0xFFEAF5FC, 0xFFFFEFE3, 0xFF1B1F2A, 0xFF3A4660) to
        Tavolozza(0xFFA9C4FF, 0xFF1C86BE, 0xFF2B4766, 0xFF5B3443, 0xFF11161F, 0xFF0C1118, 0xFF2C3B52, 0xFFFF8A3D, 0xFF3FA046, 0xFF1A2231, 0xFF223049, 0xFF3A2618, 0xFFE6EBF5, 0xFFA3AEC4),
    // Salvia: verde salvia tenue e crema
    Tavolozza(0xFF3E5C4A, 0xFF6E9277, 0xFFCFE0D2, 0xFFEDE3D1, 0xFFF7F5EF, 0xFFE8EDE4, 0xFFC9D6C6, 0xFFC1662B, 0xFF4E7D4F, 0xFFFDFCF8, 0xFFEEF2EA, 0xFFF8EADB, 0xFF26302A, 0xFF56635A) to
        Tavolozza(0xFFA8C9AE, 0xFF5F8A69, 0xFF2D4033, 0xFF4A4333, 0xFF141915, 0xFF0F1310, 0xFF2B362E, 0xFFE89A5C, 0xFF6FAE72, 0xFF1B221D, 0xFF243028, 0xFF3A2A1C, 0xFFE3EAE4, 0xFFA2B0A6),
    // Lavanda: lilla chiaro e grigio perla
    Tavolozza(0xFF4B3F72, 0xFF8A7BC2, 0xFFDDD6F2, 0xFFF2E6EE, 0xFFF8F6FC, 0xFFECE8F5, 0xFFD3CCE8, 0xFFC25E3A, 0xFF4F8A5E, 0xFFFFFFFF, 0xFFF1EEF9, 0xFFFAEAE3, 0xFF2A2638, 0xFF5E5872) to
        Tavolozza(0xFFC9BDF2, 0xFF7A6BB5, 0xFF3A3357, 0xFF4E3A48, 0xFF15131C, 0xFF100E16, 0xFF302B42, 0xFFF0946A, 0xFF6FB27E, 0xFF1E1B28, 0xFF2A2538, 0xFF3A2722, 0xFFE8E5F2, 0xFFABA5BF),
    // Sabbia e mare: beige sabbia e azzurro acqua
    Tavolozza(0xFF2F5D6E, 0xFF3A9BB0, 0xFFBFE3EA, 0xFFF1E2C6, 0xFFFBF7EF, 0xFFF0E8D8, 0xFFDCCFB4, 0xFFD0662E, 0xFF3F8550, 0xFFFFFDF8, 0xFFF5EFE2, 0xFFFBE9DA, 0xFF2B2A26, 0xFF6A6252) to
        Tavolozza(0xFF9FD3DE, 0xFF2F8296, 0xFF24434B, 0xFF4D4330, 0xFF17150F, 0xFF110F0B, 0xFF3A3428, 0xFFF09A5E, 0xFF6BB27C, 0xFF221F18, 0xFF2E2A20, 0xFF3C2A1C, 0xFFECE6DA, 0xFFB3AA98),
    // Nuvola: grigi caldi e bianco latte
    Tavolozza(0xFF4A4A52, 0xFF7D8494, 0xFFDDE0E6, 0xFFEEE8E2, 0xFFFAF9F7, 0xFFEEECE9, 0xFFD9D6D1, 0xFFC0703A, 0xFF5A8A62, 0xFFFFFFFF, 0xFFF3F2EF, 0xFFF7ECE2, 0xFF2A2A2E, 0xFF6B6B72) to
        Tavolozza(0xFFD0D3DA, 0xFF6E7585, 0xFF33363D, 0xFF46413C, 0xFF151517, 0xFF0F0F11, 0xFF2F3034, 0xFFE39A66, 0xFF79AE82, 0xFF1D1D21, 0xFF27272C, 0xFF352A22, 0xFFE8E8EC, 0xFFA9A9B1),
)

private val t: Tavolozza get() = TAVOLOZZE[TemaApp.tavolozza.coerceIn(0, TAVOLOZZE.lastIndex)].let { if (TemaApp.scuro) it.second else it.first }

/** Il colore principale di un tema (per il pallino nelle impostazioni). */
fun coloreDelTema(i: Int): Color = Color(TAVOLOZZE[i].first.accento)

// I colori dell'app: cambiano col tema scelto e con chiaro/scuro
val BluNotte get() = Color(t.titoli)        // titoli, scritte importanti
val Azzurro get() = Color(t.accento)        // pulsanti principali
val Cielo get() = Color(t.tenue)            // sfondi chiari dei pulsanti
val Rosa get() = Color(t.secondario)        // pulsanti secondari, fascia delle schede
val Sfondo get() = Color(t.sfondo)
val SfondoLista get() = Color(t.sfondoLista)  // dietro le schede
val BordoScheda get() = Color(t.bordo)
val Arancione get() = Color(t.avviso)       // avvisi
val Verde get() = Color(t.ok)               // pubblicata, pronta
val Superficie get() = Color(t.superficie)  // schede, pannelli
val FondoTenue get() = Color(t.fondoTenue)  // riquadri leggeri (testo, foto)
val FondoAvviso get() = Color(t.fondoAvviso)  // dietro le scritte arancioni
val Testo get() = Color(t.testo)
val TestoTenue get() = Color(t.testoTenue)
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
