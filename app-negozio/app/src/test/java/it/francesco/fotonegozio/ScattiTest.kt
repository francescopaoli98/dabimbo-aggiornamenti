package it.francesco.fotonegozio

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Solo per guardare l'aspetto: salva delle immagini dello schermo (cartella indicata da -Dscatti=...). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScattiTest {
    @get:Rule val regola = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(regola.activity)[FotoViewModel::class.java]

    @Test fun scatti() {
        val cartella = System.getProperty("scatti") ?: return
        val b = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        for (y in 0 until 400) for (x in 0 until 300) b.setPixel(x, y, if ((x / 50 + y / 50) % 2 == 0) 0xFFE8B4C8.toInt() else 0xFFB4D8E8.toInt())
        val file = Raddrizzatore.salva(regola.activity, b, "scatto")
        val felpa = DatiCartellino("1444496", "FELPA ZIP CAPP RS", "€ 4,00", "8A")
        regola.runOnUiThread {
            vm.foto.clear()
            vm.foto.add(Foto(1, Uri.fromFile(file), inCorso = false, miniatura = b.asImageBitmap(), fileAuto = file, file = file, dati = felpa))
            vm.foto.add(Foto(2, Uri.fromFile(file), inCorso = false, miniatura = b.asImageBitmap(), fileAuto = file, file = file, pubblicata = true, dati = felpa.copy(codice = "1444497")))
        }
        for ((modo, nome) in listOf(0 to "chiaro", 1 to "scuro")) {
            regola.runOnUiThread { vm.cambiaTema(modo) }
            regola.waitForIdle()
            salva(regola.activity.window.decorView, "$cartella/lista_$nome.png")
            regola.onNodeWithText("⚙").performClick()
            regola.waitForIdle()
            salva(org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView, "$cartella/impostazioni_$nome.png")
            regola.onAllNodesWithText("✕").onLast().performClick()
            regola.waitForIdle()
        }
        regola.runOnUiThread { vm.cambiaTema(0) }
        // Riepilogo: un giorno aperto
        regola.runOnUiThread { vm.segnaPubblicata(1) }
        regola.onNodeWithText("Oggi:", substring = true).performClick()
        regola.onNodeWithText("1 · € 4,00").performClick()
        regola.waitForIdle()
        salva(org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView, "$cartella/riepilogo.png")
        regola.onAllNodesWithText("✕").onLast().performClick()
        regola.waitForIdle()
        regola.runOnUiThread { vm.segnaPubblicata(1, false) }
        // Il visore, con lo sfondo "pixelato" acceso (così si vede anche "Rimetti originale")
        regola.runOnUiThread { vm.foto[0] = vm.foto[0].copy(fileSfondo = file, sfondoPixelato = true) }
        regola.onAllNodesWithText("🔍 Tocca").onFirst().performClick()
        regola.waitUntil(5_000) { runCatching { regola.onNodeWithText("Pixel a mano").assertIsEnabled() }.isSuccess }
        salva(org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView, "$cartella/visore.png")
        regola.onNodeWithText("Pixel a mano").performClick()
        regola.waitForIdle()
        salva(org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView, "$cartella/editor.png")
    }

    /** Disegna la finestra in un'immagine (Robolectric disegna anche senza schermo vero). */
    private fun salva(v: android.view.View, percorso: String) {
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        regola.runOnUiThread { v.draw(android.graphics.Canvas(b)) }
        java.io.FileOutputStream(percorso).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
