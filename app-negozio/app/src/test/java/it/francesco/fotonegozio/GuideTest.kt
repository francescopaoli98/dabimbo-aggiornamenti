package it.francesco.fotonegozio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
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
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * Fa le schermate delle guide (solo con -Pguida=cartella): l'app vera, con foto vere
 * (foto_1.jpg … foto_6.jpg nella cartella) e un cerchio rosa sul punto da toccare.
 * Le immagini poi vanno rimpicciolite in res/drawable-nodpi (guida_*.webp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class GuideTest {
    @get:Rule val regola = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(regola.activity)[FotoViewModel::class.java]
    private lateinit var cartella: String

    private val articoli = listOf(
        DatiCartellino("1444583", "SCARPE GINN DIADORA BIANCHE FUCSIA", "€ 9,00", "33"),
        DatiCartellino("1443990", "GIUBB PIUMINO CAPP NERO ARGENTO", "€ 8,00", "10A"),
        DatiCartellino("1444120", "PELUCHE MINION", "€ 3,00", null),
        DatiCartellino("1443986", "GIOCO PRIMI MESI CHICCO KOALA", "€ 3,00", null),
        DatiCartellino("1444610", "SCARPE ROSA STRAPPO", "€ 8,00", "30"),
        DatiCartellino("1444702", "FELPA ZIP FIORI RS", "€ 4,00", "6A"),
    )

    @Test fun guide() {
        cartella = System.getProperty("guida") ?: return
        // Le foto vere, già "sistemate" (l'app nel simulatore non legge i cartellini: i dati li metto io)
        val foto = articoli.mapIndexed { i, d ->
            val o = BitmapFactory.decodeFile("$cartella/foto_${i + 1}.jpg")
            val k = 1200f / maxOf(o.width, o.height)
            val b = Bitmap.createScaledBitmap(o, (o.width * k).toInt(), (o.height * k).toInt(), true)
            val file = Raddrizzatore.salva(regola.activity, b, "guida_${i + 1}")
            val mini = Bitmap.createScaledBitmap(b, b.width / 3, b.height / 3, true)
            Foto(i + 1, Uri.fromFile(file), inCorso = false, miniatura = mini.asImageBitmap(), fileAuto = file, file = file, dati = d, daControllare = i == 0)
        }

        // ---------- 1. WhatsApp ----------
        regola.waitForIdle()
        scatto("wa_1", regola.onNodeWithText("Scegli foto"))
        regola.runOnUiThread { vm.foto.clear(); vm.foto.addAll(foto) }
        aspetta()
        scatto("wa_2", regola.onAllNodesWithText("cod. 1444583", substring = true).onFirst())
        scatto("wa_3", regola.onAllNodesWithText("⚠ Da controllare").onFirst())
        scatto("wa_4", regola.onNodeWithText("Pubblica la prossima", substring = true))
        regola.runOnUiThread { vm.foto[0] = vm.foto[0].copy(daControllare = false); vm.segnaPubblicata(1) }
        aspetta()
        scatto("wa_5", regola.onNodeWithText("Pubblicate 1 di 6"))

        // ---------- 2. Sistemare una foto ----------
        scatto("foto_1", regola.onAllNodesWithText("🔍 Tocca").onFirst())
        regola.onAllNodesWithText("🔍 Tocca").onFirst().performClick()
        regola.waitUntil(10_000) { runCatching { regola.onNodeWithText("Pixel a mano").assertIsEnabled() }.isSuccess }
        aspetta()
        scatto("foto_2", regola.onNodeWithText("Sinistra"), regola.onNodeWithText("Destra"), regola.onNodeWithText("Capovolgi"), dialogo = true)
        scatto("foto_3", regola.onNodeWithText("Sfondo automatico"), regola.onNodeWithText("Pixel a mano"), dialogo = true)
        scatto("foto_4", regola.onNodeWithText("Ritaglia"), dialogo = true)
        regola.onNodeWithText("Ritaglia").performClick()
        regola.waitUntil(5_000) { runCatching { regola.onNodeWithTag("ritaglio_tela").assertExists() }.isSuccess }
        regola.onNodeWithText("4:5").performClick()
        aspetta()
        scatto("foto_5", *Ritaglio.FORMATI.map { regola.onNodeWithText(it.nome) }.toTypedArray(), dialogo = true)
        regola.onNodeWithText("Esci").performClick()
        aspetta()
        scatto("foto_6", regola.onNodeWithText("Testo"), dialogo = true)
        regola.onAllNodesWithText("✕").onLast().performClick()
        aspetta()

        // ---------- 3. Storico e prenotati ----------
        regola.runOnUiThread { (2..4).forEach { vm.segnaPubblicata(it) } }
        regola.waitUntil(10_000) { vm.registro.size == 4 && vm.registro.all { File(vm.cartellaStoricoHd, it.foto).exists() && File(vm.cartellaStorico, it.miniatura).exists() } }
        aspetta()
        scatto("storico_1", regola.onNodeWithText("Oggi:", substring = true))
        regola.onNodeWithText("Oggi:", substring = true).performClick()
        aspetta()
        val totale = vm.oggiPubblicati!!
        scatto("storico_2", regola.onNodeWithText("${totale.articoli} · ${Riepilogo.euro(totale.centesimi)}"), dialogo = true)
        regola.onNodeWithText("${totale.articoli} · ${Riepilogo.euro(totale.centesimi)}").performClick()
        regola.waitUntil(5_000) { regola.onAllNodesWithContentDescription("Foto", useUnmergedTree = true).fetchSemanticsNodes().size >= 3 }
        aspetta()
        scatto("storico_3", regola.onNodeWithTag("prenota_1443990"), dialogo = true)
        regola.onNodeWithTag("prenota_1443990").performClick()
        aspetta()
        scatto("storico_4", regola.onAllNodesWithText("WhatsApp").onFirst(), regola.onAllNodesWithText("Instagram").onFirst(), dialogo = true)
        scatto("storico_5", regola.onNodeWithTag("prenotato_wa"), regola.onNodeWithTag("prenotato_ig"), dialogo = true)
        regola.onNodeWithText("Fatto").performClick()
        aspetta()
        scatto("storico_6", regola.onNodeWithText("Annulla prenotazione"), regola.onNodeWithText("📌 Prenotati:", substring = true), dialogo = true)
        scatto("storico_7", regola.onNodeWithText("🔒 Sblocca cancellazione"), dialogo = true)
        regola.onAllNodesWithText("✕").onLast().performClick()
        aspetta()

        // ---------- 4. Storie Instagram ----------
        scatto("storie_1", regola.onNodeWithText("Instagram: storie e carosello"))
        regola.onNodeWithText("Instagram: storie e carosello").performClick()
        aspetta()
        scatto("storie_2", regola.onNodeWithText("Lista di adesso", substring = true), regola.onAllNodes(hasTestTagPrefix("giorno_")).onFirst(), dialogo = true)
        for (n in listOf(5, 2, 6)) regola.onNodeWithTag("ig_L$n").performClick()
        aspettaFotine()
        scatto("storie_3", regola.onNodeWithTag("ig_L5"), regola.onNodeWithTag("ig_L2"), regola.onNodeWithTag("ig_L6"), dialogo = true)
        scatto("storie_4", regola.onNodeWithText("Storie"), dialogo = true)
        regola.onNodeWithText("Storie").performClick()
        regola.waitUntil(15_000) { runCatching { regola.onNodeWithText("Apri Instagram").assertExists() }.isSuccess }
        aspetta(1500)
        scatto("storie_5", regola.onNodeWithText("Apri Instagram"), dialogo = true)
        scatto("storie_6", regola.onNodeWithText("Una alla volta"), dialogo = true)
        regola.onNodeWithText("‹").performClick()
        aspetta()

        // ---------- 5. Carosello ----------
        scatto("carosello_1", regola.onNodeWithTag("ig_L5"), regola.onNodeWithTag("ig_L2"), regola.onNodeWithTag("ig_L6"), dialogo = true)
        scatto("carosello_2", regola.onNodeWithText("Carosello"), dialogo = true)
        regola.onNodeWithText("Carosello").performClick()
        regola.waitUntil(15_000) { runCatching { regola.onNodeWithTag("didascalia_ig").assertExists() }.isSuccess }
        aspetta(1500)
        scatto("carosello_3", regola.onNodeWithTag("didascalia_ig"), dialogo = true)
        scatto("carosello_4", regola.onNodeWithText("Apri Instagram"), dialogo = true)
        regola.onNodeWithText("Apri Instagram").performClick()   // il carosello si ricorda (per il testo coi prenotati)
        aspetta()
        regola.onNodeWithText("‹").performClick()
        regola.onAllNodesWithText("✕").onLast().performClick()
        aspetta()
        // Un articolo del carosello viene prenotato: testo aggiornato
        regola.onNodeWithText("Oggi:", substring = true).performClick()
        regola.onNodeWithText("${totale.articoli} · ${Riepilogo.euro(totale.centesimi)}").performClick()
        aspetta()
        regola.onNodeWithTag("avvisa_1443990").performClick()   // il piumino (prenotato prima) era nel carosello
        aspetta()
        scatto("carosello_6", regola.onNodeWithTag("copia_carosello"), dialogo = true)
    }

    private fun hasTestTagPrefix(p: String) = SemanticsMatcher("tag $p…") { n ->
        n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith(p) == true
    }

    private fun aspetta(ms: Long = 400) {
        regola.waitForIdle(); Thread.sleep(ms); regola.waitForIdle()
    }

    /** Le miniature della griglia Instagram si caricano in sottofondo. */
    private fun aspettaFotine() = aspetta(1200)

    /**
     * Fotografa lo schermo (o la finestra in primo piano) e cerchia in rosa i [bersagli];
     * il resto si scurisce un po', così l'occhio va subito lì.
     */
    private fun scatto(nome: String, vararg bersagli: SemanticsNodeInteraction, dialogo: Boolean = false) {
        regola.waitForIdle()
        val v = if (dialogo) ShadowDialog.getLatestDialog().window!!.decorView else regola.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        regola.runOnUiThread { v.draw(Canvas(b)) }
        val c = Canvas(b)
        val margine = 14f
        val zone = bersagli.map { s ->
            val r = s.fetchSemanticsNode().boundsInWindow
            RectF(r.left - margine, r.top - margine, r.right + margine, r.bottom + margine)
        }
        // Scuro tutto tranne le zone
        val buchi = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, b.width.toFloat(), b.height.toFloat(), Path.Direction.CW)
            zone.forEach { addRoundRect(it, 28f, 28f, Path.Direction.CW) }
        }
        c.drawPath(buchi, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(95, 0, 0, 0) })
        val anello = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 10f; color = Color.rgb(232, 70, 130) }
        zone.forEach { c.drawRoundRect(it, 28f, 28f, anello) }
        java.io.FileOutputStream("$cartella/guida_$nome.png").use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
