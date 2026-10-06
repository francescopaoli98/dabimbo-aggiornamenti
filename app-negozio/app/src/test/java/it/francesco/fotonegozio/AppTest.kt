package it.francesco.fotonegozio

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/**
 * Test generale dell'app, sul PC: Robolectric simula il telefono e i test "toccano" i pulsanti.
 * La lettura dei cartellini (ML Kit) qui non gira: le foto arrivano già "lette".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")   // schermo alto: tutta la scheda visibile senza scorrere
class AppTest {

    @get:Rule
    val regola = createAndroidComposeRule<MainActivity>()

    private val vm get() = ViewModelProvider(regola.activity)[FotoViewModel::class.java]

    private val felpa = DatiCartellino("1234567", "felpa", "€ 4,00", "8A")

    /** Una foto finta già elaborata: metà rossa e metà blu, 300×400. */
    private fun fotoDiProva(numero: Int, articoli: List<DatiCartellino>): Foto {
        val b = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        for (y in 0 until 400) for (x in 0 until 300) b.setPixel(x, y, if (x < 150) 0xFFFF0000.toInt() else 0xFF0000FF.toInt())
        val file = Raddrizzatore.salva(regola.activity, b, "prova_$numero")
        return Foto(
            numero, Uri.fromFile(file), inCorso = false, miniatura = b.asImageBitmap(),
            fileAuto = file, file = file, dati = articoli.firstOrNull(), altri = articoli.drop(1),
        )
    }

    /** Ogni test riparte pulito: niente lista salvata né impostazioni dal test prima. */
    @org.junit.After
    fun pulisci() {
        // Svuoto la lista e lascio finire il salvataggio, così il test dopo non la ritrova
        regola.runOnUiThread { vm.foto.clear() }
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        Thread.sleep(200)
        // Il simulatore dà al ViewModel sempre la prima Application dei test: uso quella
        val app = vm.getApplication<android.app.Application>()
        java.io.File(app.filesDir, "lista.json").delete()
        app.getSharedPreferences("preferenze", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun metti(vararg f: Foto) {
        regola.runOnUiThread { vm.foto.clear(); vm.foto.addAll(f) }
        regola.waitForIdle()
    }

    /** Apre la foto grande e aspetta che sia caricata (si carica in sottofondo). */
    private fun apriVisore() {
        regola.onNodeWithText("🔍 Tocca").performClick()
        regola.waitUntil(5_000) { runCatching { regola.onNodeWithText("Pixel a mano").assertIsEnabled() }.isSuccess }
    }

    private fun premiIndietro() {
        regola.runOnUiThread { regola.activity.onBackPressedDispatcher.onBackPressed() }
        regola.waitForIdle()
    }

    @Test
    fun sempreInVerticale() {
        val info = regola.activity.packageManager.getActivityInfo(ComponentName(regola.activity, MainActivity::class.java), 0)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, info.screenOrientation)
    }

    @Test
    fun avvioMostraIPulsanti() {
        regola.onNodeWithText("Scegli foto").assertIsDisplayed()
        regola.onNodeWithText("Sigle").assertIsDisplayed()
        regola.onNodeWithText("Ciao! 👋").assertExists()
    }

    @Test
    fun indietroUnaVoltaNonEsceDueVolteSi() {
        premiIndietro()
        assertFalse(regola.activity.isFinishing)
        assertEquals("Premi ancora Indietro per uscire", ShadowToast.getTextOfLatestToast())
        premiIndietro()
        assertTrue(regola.activity.isFinishing)
    }

    @Test
    fun indietroChiudePrimaLeFinestre() {
        regola.onNodeWithText("Sigle").performClick()
        regola.onNodeWithText("+ Nuova sigla").assertExists()
        regola.onNodeWithText("✕ Chiudi").performClick()
        regola.onNodeWithText("+ Nuova sigla").assertDoesNotExist()
        assertFalse(regola.activity.isFinishing)
    }

    @Test
    fun schedaMostraTestoEPulsanti() {
        metti(fotoDiProva(1, listOf(felpa)))
        regola.onNodeWithText("Foto 1").assertExists()
        regola.onNodeWithText("Pronta").assertExists()
        regola.onNodeWithText("Articoli (1)").assertExists()
        regola.onNodeWithText("Pubblica").assertExists()
        regola.onNodeWithText("cod. 1234567", substring = true).assertExists()
    }

    @Test
    fun modificaArticoloCambiaIlTesto() {
        metti(fotoDiProva(1, listOf(felpa)))
        regola.onNodeWithText("Articoli (1)").performClick()
        regola.onNodeWithText("Articoli · Foto 1").assertExists()
        regola.onNodeWithText("✏ Modifica").performClick()
        regola.onNode(hasSetTextAction() and hasText("4,00")).performTextReplacement("6,5")
        regola.onNodeWithText("Salva").performClick()
        regola.waitForIdle()
        assertEquals("€ 6,50", vm.foto[0].dati?.prezzo)
        regola.onNodeWithText("✕ Chiudi").performClick()
        regola.onNodeWithText("€ 6,50", substring = true).assertExists()
    }

    @Test
    fun aggiungiArticoloConRigaVeloce() {
        metti(fotoDiProva(1, emptyList()))
        regola.onNodeWithText("Cartellino non letto", substring = true).assertExists()
        regola.onNodeWithText("Articoli (0)").performClick()
        regola.onNodeWithText("Aggiungi etichetta").performClick()
        regola.onAllNodes(hasSetTextAction())[0].performTextInput("1444115 librottino inside out 1,50")
        regola.onNodeWithText("Salva").performClick()
        regola.waitForIdle()
        val a = vm.foto[0].articoli.single()
        assertEquals("1444115", a.codice)
        assertEquals("€ 1,50", a.prezzo)
        regola.onNodeWithText("Articoli (1)").assertExists()
    }

    @Test
    fun dueDitaIngrandisconoLAnteprimaSenzaAprireLaFoto() {
        metti(fotoDiProva(1, listOf(felpa)))
        regola.onNodeWithText("🔍 Tocca").performTouchInput { pinch(center - Offset(20f, 0f), center - Offset(200f, 0f), center + Offset(20f, 0f), center + Offset(200f, 0f)) }
        regola.waitForIdle()
        regola.onNodeWithText("Capovolgi").assertDoesNotExist()   // il visore non si è aperto
        // Un tocco normale invece apre la foto grande
        regola.onNodeWithText("🔍 Tocca").performClick()
        regola.onNodeWithText("Capovolgi").assertExists()
    }

    @Test
    fun conAvvisiChiedePrimaDiPubblicare() {
        metti(fotoDiProva(1, emptyList()))
        regola.onNodeWithText("Pubblica").performClick()
        regola.onNodeWithText("Pubblica lo stesso").assertExists()
        regola.onNodeWithText("La sistemo").performClick()
        regola.onNodeWithText("Pubblica lo stesso").assertDoesNotExist()
        assertFalse(vm.foto[0].pubblicata)
    }

    @Test
    fun pubblicaPassaFotoETestoESegnaPubblicata() {
        metti(fotoDiProva(1, listOf(felpa)), fotoDiProva(2, listOf(felpa.copy(codice = "7654321"))))
        regola.onNodeWithText("Pubblica la prossima · Foto 1").performClick()
        regola.waitForIdle()
        val scelta = shadowOf(regola.activity).nextStartedActivity
        assertNotNull(scelta)
        @Suppress("DEPRECATION")
        val dentro = scelta.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: scelta
        assertEquals(Intent.ACTION_SEND, dentro.action)
        assertTrue(dentro.getStringExtra(Intent.EXTRA_TEXT)!!.contains("1234567"))
        assertTrue(vm.foto[0].pubblicata)
        // La barra passa alla foto successiva
        regola.onNodeWithText("Pubblica la prossima · Foto 2").assertExists()
    }

    @Test
    fun correggiTestoDallaScheda() {
        metti(fotoDiProva(1, listOf(felpa)))
        regola.onNodeWithText("✏ Tocca per correggere").performClick()
        regola.onNodeWithText("Testo per lo stato").assertExists()
        regola.onNode(hasSetTextAction()).performTextReplacement("Felpa bellissima")
        regola.onNodeWithText("Salva").performClick()
        regola.waitForIdle()
        assertEquals("Felpa bellissima", vm.testo(vm.foto[0]))
        regola.onNodeWithText("Felpa bellissima").assertExists()
    }

    @Test
    fun visoreApreTestoEChiude() {
        metti(fotoDiProva(1, listOf(felpa)))
        apriVisore()
        regola.onNodeWithText("Capovolgi").assertExists()
        regola.onNodeWithText("Testo").performClick()
        regola.onNodeWithText("Capovolgi").assertDoesNotExist()
        regola.onNodeWithText("Testo per lo stato").assertExists()
        regola.onNodeWithText("Annulla").performClick()
        regola.onNodeWithText("Testo per lo stato").assertDoesNotExist()
    }

    @Test
    fun giraLaFoto() {
        metti(fotoDiProva(1, listOf(felpa)))
        apriVisore()
        regola.onNodeWithText("Gira").performClick()
        regola.waitUntil(5_000) { vm.foto[0].rotazioneManuale == 90 }
        regola.onNodeWithText("Capovolgi").performClick()
        regola.waitUntil(5_000) { vm.foto[0].rotazioneManuale == 270 }
        regola.onAllNodesWithText("✕").onLast().performClick()   // la ✕ del visore (sta sopra)
        regola.onNodeWithText("Capovolgi").assertDoesNotExist()
    }

    @Test
    fun pixelaAMano() {
        metti(fotoDiProva(1, listOf(felpa)))
        val prima = vm.foto[0].file
        apriVisore()
        regola.onNodeWithText("Pixel a mano").performClick()
        regola.onNodeWithText("Salva").assertIsNotEnabled()
        // Passo il dito sul confine rosso/blu
        regola.onNodeWithTag("tela").performTouchInput { swipe(Offset(centerX, top + 50f), Offset(centerX, bottom - 50f)) }
        regola.onNodeWithText("Salva").assertIsEnabled()
        // Annulla toglie la passata
        regola.onNodeWithText("Annulla").performClick()
        regola.onNodeWithText("Salva").assertIsNotEnabled()
        regola.onNodeWithTag("tela").performTouchInput { swipe(Offset(centerX, top + 50f), Offset(centerX, bottom - 50f)) }
        regola.onNodeWithText("Salva").performClick()
        regola.waitUntil(5_000) { vm.foto[0].file != prima }
        // Sul confine ora c'è un colore misto (né rosso puro né blu puro)
        val b = android.graphics.BitmapFactory.decodeFile(vm.foto[0].file!!.path)
        val c = b.getPixel(150, 200)
        assertTrue(android.graphics.Color.red(c) in 40..215 && android.graphics.Color.blue(c) in 40..215)
        // Torna al visore normale
        regola.onNodeWithText("Capovolgi").assertExists()
        assertTrue(vm.foto[0].pixelManuale.isNotEmpty())
        // "↺ Togli" del pixel a mano: torna la foto pulita
        regola.onNodeWithText("Togli").performClick()
        regola.waitUntil(5_000) { vm.foto[0].pixelManuale.isEmpty() && vm.foto[0].file == vm.foto[0].fileAuto }
        regola.onNodeWithText("Togli").assertDoesNotExist()
    }

    @Test
    fun pixelAManoSuFotoGirata() {
        metti(fotoDiProva(1, listOf(felpa)))
        apriVisore()
        regola.onNodeWithText("Gira").performClick()
        regola.waitUntil(5_000) { vm.foto[0].rotazioneManuale == 90 }
        // Aspetto che la foto girata sia caricata (prima il pixel a mano non si apre)
        regola.waitUntil(5_000) { runCatching { regola.onNodeWithText("Pixel a mano").assertIsEnabled() }.isSuccess }
        // Girata di 90°: il rosso ora sta sopra e il blu sotto. Passo il dito in orizzontale sul confine.
        regola.onNodeWithText("Pixel a mano").performClick()
        regola.onNodeWithTag("tela").performTouchInput { swipe(Offset(left + 20f, centerY), Offset(right - 20f, centerY)) }
        val prima = vm.foto[0].file
        regola.onNodeWithText("Salva").performClick()
        regola.waitUntil(5_000) { vm.foto[0].file != prima && vm.foto[0].pixelManuale.isNotEmpty() }
        val b = android.graphics.BitmapFactory.decodeFile(vm.foto[0].file!!.path)
        assertEquals(400, b.width); assertEquals(300, b.height)   // resta girata
        val c = b.getPixel(200, 150)
        assertTrue(android.graphics.Color.red(c) in 40..215 && android.graphics.Color.blue(c) in 40..215)
        // Lontano dal confine resta nitida
        assertTrue(android.graphics.Color.red(b.getPixel(200, 20)) > 200)
    }

    @Test
    fun pixelAutomaticoHaIlSuoPulsante() {
        metti(fotoDiProva(1, listOf(felpa)))
        apriVisore()
        regola.onNodeWithText("Sfondo automatico").assertIsEnabled()
        // Senza pixel fatti, nessun "Togli"
        regola.onNodeWithText("Togli").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")   // schermo come il OnePlus
    fun aggiungiSiglaAlDizionario() {
        regola.onNodeWithText("Sigle").performClick()
        regola.onNodeWithText("+ Nuova sigla").performClick()
        regola.onNode(hasSetTextAction() and hasText("Sigla (es. gri)")).performTextReplacement("zzz")
        regola.onNode(hasSetTextAction() and hasText("Significato")).performTextReplacement("zebrato")
        regola.onNodeWithText("Salva").performClick()
        regola.waitForIdle()
        assertTrue(vm.dizionario.any { it.sigla == "zzz" })
        // Correggo la sigla appena aggiunta, poi la elimino
        regola.onNode(hasSetTextAction() and hasText("Cerca")).performTextReplacement("zzz")
        regola.onNodeWithText("ZZZ").performClick()
        regola.onNode(hasSetTextAction() and hasText("zebrato")).performTextReplacement("zebrata")
        regola.onNodeWithText("Salva").performClick()
        regola.waitForIdle()
        assertEquals(listOf("zebrata"), vm.dizionario.single { it.sigla == "zzz" }.significati)
        regola.onNodeWithText("ZZZ").performClick()
        regola.onNodeWithText("Elimina").performClick()
        regola.waitForIdle()
        assertFalse(vm.dizionario.any { it.sigla == "zzz" })
        regola.onNodeWithText("✕ Chiudi").performClick()
        regola.onNodeWithText("+ Nuova sigla").assertDoesNotExist()
    }

    @Test
    fun togliFotoDallaLista() {
        metti(fotoDiProva(1, listOf(felpa)), fotoDiProva(2, listOf(felpa.copy(codice = "7654321"))))
        regola.onAllNodesWithText("✕")[1].performClick()   // [0] è la ✕ del menu in alto
        regola.onNodeWithText("Stai per rimuovere la Foto 1").assertExists()
        regola.onNodeWithText("Sei sicura?", substring = true).assertExists()
        regola.onNodeWithText("No").performClick()
        assertEquals(2, vm.foto.size)
        regola.onAllNodesWithText("✕")[1].performClick()
        regola.onNodeWithText("Sì, rimuovi").performClick()
        regola.waitForIdle()
        assertEquals(listOf(2), vm.foto.map { it.numero })
        regola.onNodeWithText("Foto 1").assertDoesNotExist()
    }

    @Test
    fun filtroSoloDaControllare() {
        metti(fotoDiProva(1, listOf(felpa)), fotoDiProva(2, emptyList()))
        regola.onNodeWithText("⚠ Da controllare (1)").performClick()
        regola.onNodeWithText("Foto 2").assertExists()
        regola.onNodeWithText("Foto 1").assertDoesNotExist()
        // Sistemata la foto 2, il filtro resta vuoto e lo dice
        regola.runOnUiThread { vm.salvaArticolo(2, null, felpa.copy(codice = "1111111")) }
        regola.onNodeWithText("Nessuna foto da controllare", substring = true).assertExists()
        regola.onNodeWithText("Tutte (2)").performClick()
        regola.onNodeWithText("Foto 1").assertExists()
    }

    @Test
    fun impostazioniTestoGrandeRestaSalvato() {
        regola.onNodeWithText("⚙").performClick()
        regola.onNodeWithText("Grande").performClick()
        regola.waitForIdle()
        assertEquals(1.2f, vm.scalaTesto)
        // Riaprendo l'app l'impostazione c'è ancora
        assertEquals(1.2f, FotoViewModel(vm.getApplication()).also { it.foto.clear() }.scalaTesto)
        regola.onAllNodesWithText("✕").onLast().performClick()
        regola.onNodeWithText("⚙ Impostazioni").assertDoesNotExist()
    }

    @Test
    fun prezzoInGrassettoDalleImpostazioni() {
        metti(fotoDiProva(1, listOf(felpa)))
        assertTrue(vm.testo(vm.foto[0]).endsWith("- € 4,00"))
        regola.onNodeWithText("⚙").performClick()
        regola.onNodeWithText("Prezzo in grassetto").performClick()
        regola.waitForIdle()
        assertTrue(vm.testo(vm.foto[0]).endsWith("- *€ 4,00*"))
    }

    @Test
    fun fotoPubblicataSiChiudeESiRiapre() {
        metti(fotoDiProva(1, listOf(felpa)), fotoDiProva(2, listOf(felpa.copy(codice = "7654321"))))
        regola.runOnUiThread { vm.segnaPubblicata(1) }
        regola.waitForIdle()
        // Compressa: niente pulsanti, solo la riga
        regola.onAllNodesWithText("Articoli (1)").assertCountEquals(1)
        regola.onNodeWithText("▼").performClick()
        regola.onAllNodesWithText("Articoli (1)").assertCountEquals(2)
        regola.onNodeWithText("▲").performClick()
        regola.onAllNodesWithText("Articoli (1)").assertCountEquals(1)
        // Spento nelle impostazioni: resta aperta
        regola.runOnUiThread { vm.cambiaComprimi(false) }
        regola.onAllNodesWithText("Articoli (1)").assertCountEquals(2)
    }

    @Test
    fun crocettaInAltoChiedeSeMancanoFoto() {
        metti(fotoDiProva(1, listOf(felpa)), fotoDiProva(2, listOf(felpa.copy(codice = "7654321"))))
        regola.runOnUiThread { vm.segnaPubblicata(1) }
        regola.onAllNodesWithText("✕")[0].performClick()
        regola.onNodeWithText("Stai per tornare al menu principale").assertExists()
        regola.onNodeWithText("1 foto non è ancora stata pubblicata", substring = true).assertExists()
        regola.onNodeWithText("No").performClick()
        assertEquals(2, vm.foto.size)
        regola.onAllNodesWithText("✕")[0].performClick()
        regola.onNodeWithText("Sì, torna al menu").performClick()
        regola.waitForIdle()
        assertTrue(vm.foto.isEmpty())
        regola.onNodeWithText("Ciao! 👋").assertExists()
    }

    @Test
    fun crocettaConTuttePubblicateNonChiedeNiente() {
        metti(fotoDiProva(1, listOf(felpa)))
        regola.runOnUiThread { vm.segnaPubblicata(1) }
        regola.onAllNodesWithText("✕")[0].performClick()
        regola.waitForIdle()
        regola.onNodeWithText("Stai per tornare al menu principale").assertDoesNotExist()
        assertTrue(vm.foto.isEmpty())
    }

    @Test
    fun tornaSuSoloSeAcceso() {
        metti(*Array(8) { fotoDiProva(it + 1, listOf(felpa.copy(codice = "${1000000 + it}"))) })
        regola.onNode(hasScrollToKeyAction()).performScrollToKey(8)   // scendo fino alla foto 8
        regola.onNodeWithText("↑").assertDoesNotExist()
        regola.runOnUiThread { vm.cambiaTornaSu(true) }
        regola.onNodeWithText("↑").assertExists().performClick()
        regola.onNodeWithText("Scegli foto").assertIsDisplayed()
    }

    @Test
    fun laListaSiRitrovaRiaprendoLApp() {
        metti(fotoDiProva(1, listOf(felpa)), fotoDiProva(2, emptyList()))
        regola.runOnUiThread {
            vm.segnaPubblicata(1)
            vm.cambiaTesto(2, "Testo scritto a mano")
        }
        // Il salvataggio parte 0,3 s dopo l'ultimo cambiamento: faccio passare il tempo
        regola.waitUntil(5_000) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
            java.io.File(vm.getApplication<android.app.Application>().filesDir, "lista.json").let { it.exists() && "Testo scritto a mano" in it.readText() }
        }
        // Come se il telefono avesse chiuso l'app: un ViewModel nuovo legge la lista salvata
        val nuovo = FotoViewModel(vm.getApplication())
        assertEquals(listOf(1, 2), nuovo.foto.map { it.numero })
        assertTrue(nuovo.foto[0].pubblicata)
        assertEquals(felpa, nuovo.foto[0].dati)
        assertEquals("Testo scritto a mano", nuovo.foto[1].testoManuale)
        assertTrue(nuovo.foto[0].file!!.exists())
        nuovo.foto.clear()
    }
}
