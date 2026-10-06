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
        TemaApp.modo = 0
        TemaApp.tavolozza = 0
        TemaApp.coloreMio = 0xFF2E86AB
        java.io.File(app.filesDir, "pubblicazioni.json").delete()
        // Il FileProvider si ricorda le cartelle del primo test (cache statica): la svuoto
        runCatching {
            val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
            (cache.get(null) as MutableMap<*, *>).clear()
        }
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
        regola.onNodeWithText("Destra").performClick()
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
        regola.onNodeWithText("Destra").performClick()
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

    @Test
    fun modalitaScuraDalleImpostazioni() {
        metti(fotoDiProva(1, listOf(felpa)))
        assertFalse(TemaApp.scuro)
        assertEquals(androidx.compose.ui.graphics.Color.White, Superficie)
        regola.onNodeWithText("⚙").performClick()
        regola.onNodeWithText("🌙 Scuro").performClick()
        regola.waitForIdle()
        assertTrue(TemaApp.scuro)
        assertEquals(1, vm.tema)
        assertNotEquals(androidx.compose.ui.graphics.Color.White, Superficie)
        // "Come il telefono": segue il telefono
        regola.onNodeWithText("📱 Come il telefono").performClick()
        regola.runOnUiThread { TemaApp.telefonoScuro = false }
        assertFalse(TemaApp.scuro)
        regola.runOnUiThread { TemaApp.telefonoScuro = true }
        assertTrue(TemaApp.scuro)
        // Resta salvato
        assertEquals(2, FotoViewModel(vm.getApplication()).also { it.foto.clear() }.tema)
        regola.onNodeWithText("☀ Chiaro").performClick()
        regola.waitForIdle()
        assertFalse(TemaApp.scuro)
    }

    @Test
    fun sinistraEDestraGiranoSubito() {
        metti(fotoDiProva(1, listOf(felpa)))
        apriVisore()
        regola.onNodeWithText("Sinistra").performClick()
        // L'anteprima gira subito (la foto vera si ricompone dopo)
        assertEquals(270, vm.foto[0].rotazioneManuale)
        assertEquals(400, vm.foto[0].miniatura!!.width)   // era 300×400: girata diventa 400×300
        regola.waitUntil(5_000) { vm.foto[0].rotazioneFile == 270 }
        val b = android.graphics.BitmapFactory.decodeFile(vm.foto[0].file!!.path)
        assertEquals(400, b.width)
        // Girata a sinistra: il rosso (che era a sinistra) ora sta in basso
        assertTrue(android.graphics.Color.red(b.getPixel(200, 280)) > 200)
        // Tre tocchi di fila: una sola foto finale, girata giusta
        regola.onNodeWithText("Destra").performClick()
        regola.onNodeWithText("Destra").performClick()
        regola.onNodeWithText("Destra").performClick()
        regola.waitUntil(5_000) { vm.foto[0].rotazioneManuale == 180 && vm.foto[0].rotazioneFile == 180 }
    }

    @Test
    fun pennelloOriginaleSulloSfondoPixelato() {
        val f = fotoDiProva(1, listOf(felpa))
        // Sfondo "pixelato" finto: tutto grigio
        val grigio = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF808080.toInt()) }
        val sfondo = Raddrizzatore.salva(regola.activity, grigio, "prova_sfondo")
        metti(f.copy(fileSfondo = sfondo, sfondoPixelato = true, file = sfondo))
        apriVisore()
        regola.onNodeWithText("Rimetti originale").performClick()
        regola.onNodeWithText("✨ Originale").assertIsSelected()
        // Passo il pennello sulla parte sinistra (rossa nell'originale)
        regola.onNodeWithTag("tela").performTouchInput { swipe(Offset(left + width * 0.2f, centerY - 100f), Offset(left + width * 0.2f, centerY + 100f)) }
        // Annulla il passo, poi lo rifaccio
        regola.onNodeWithText("Annulla").performClick()
        regola.onNodeWithText("Salva").assertIsNotEnabled()
        regola.onNodeWithTag("tela").performTouchInput { swipe(Offset(left + width * 0.2f, centerY - 100f), Offset(left + width * 0.2f, centerY + 100f)) }
        regola.onNodeWithText("Salva").performClick()
        regola.waitUntil(5_000) { vm.foto[0].ripristinate.isNotEmpty() && vm.foto[0].statoFile == vm.foto[0].statoVoluto }
        val b = android.graphics.BitmapFactory.decodeFile(vm.foto[0].file!!.path)
        assertTrue(android.graphics.Color.red(b.getPixel(60, 200)) > 200)      // lì è tornata la foto vera (rossa)
        assertTrue(android.graphics.Color.red(b.getPixel(250, 50)) in 100..160) // il resto resta "pixelato" (grigio)
        // "Togli" dello sfondo: via sfondo e ritocchi
        regola.onAllNodesWithText("Togli").onFirst().performClick()
        regola.waitUntil(5_000) { !vm.foto[0].sfondoPixelato && vm.foto[0].ripristinate.isEmpty() }
    }

    @Test
    fun riepilogoDellaGiornata() {
        metti(fotoDiProva(1, listOf(felpa)), fotoDiProva(2, listOf(felpa.copy(codice = "7654321", prezzo = "€ 1,50"))))
        regola.onNodeWithText("Oggi:", substring = true).assertDoesNotExist()
        regola.runOnUiThread { vm.segnaPubblicata(1); vm.segnaPubblicata(2); vm.segnaPubblicata(1) }   // la 1 due volte: conta una
        regola.onNodeWithText("Oggi: 2 articoli · € 5,50").assertExists().performClick()
        regola.onNodeWithText("📊 Pubblicati").assertExists()
        regola.onNodeWithText("2 · € 5,50").assertExists().performClick()
        // Tolgo l'articolo contato per sbaglio (con conferma)
        regola.onNodeWithText("cod. 7654321 · € 1,50").assertExists()
        regola.onNodeWithTag("togli_7654321").performClick()
        regola.onNodeWithText("Togliere dal conteggio?").assertExists()
        regola.onNodeWithText("Sì, togli").performClick()
        regola.waitForIdle()
        assertEquals(1, vm.oggiPubblicati?.articoli)
        assertEquals(400, vm.oggiPubblicati?.centesimi)
        // Resta anche riaprendo l'app
        assertEquals(1, FotoViewModel(vm.getApplication()).also { it.foto.clear() }.oggiPubblicati?.articoli)
    }

    @Test
    fun avvisoGiaPubblicato() {
        metti(fotoDiProva(1, listOf(felpa)))
        regola.runOnUiThread { vm.segnaPubblicata(1) }
        // Il giorno dopo (o in un'altra lista) rifotografa lo stesso articolo
        metti(fotoDiProva(1, listOf(felpa)), fotoDiProva(2, listOf(felpa.copy(codice = "7654321"))))
        regola.onNodeWithText("⚠ Già pubblicato: cod. 1234567").assertExists()
        regola.onAllNodesWithText("Già pubblicato", substring = true).assertCountEquals(1)   // la foto 2 no
        // Pubblicando chiede prima
        regola.onAllNodesWithText("Pubblica").onFirst().performClick()
        regola.onNodeWithText("già pubblicato: cod. 1234567", substring = true).assertExists()
        regola.onNodeWithText("Pubblica lo stesso").performClick()
        regola.waitForIdle()
        assertTrue(vm.foto[0].pubblicata)
        regola.onNodeWithText("⚠ Già pubblicato: cod. 1234567").assertDoesNotExist()
    }

    @Test
    fun avvisoFotoPronteQuandoSeiFuoriDallApp() {
        val app = vm.getApplication<android.app.Application>()
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val file = Raddrizzatore.salva(regola.activity, Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888), "prova_avviso")
        regola.runOnUiThread {
            AppVisibile.visibile = false   // Elisa è uscita dall'app
            vm.carica(listOf(Uri.fromFile(file)))
        }
        // Mentre lavora parte il servizio con l'avviso fisso
        val servizio = shadowOf(app).nextStartedService
        assertEquals(ServizioLavoro::class.java.name, servizio?.component?.className)
        // Sul PC la lettura non gira: la foto finisce subito (con errore), ma il percorso è lo stesso
        regola.waitUntil(10_000) { vm.foto.isNotEmpty() && vm.foto.none { it.inCorso } }
        // (nel simulatore il servizio non parte davvero: l'app aspetta fino a 3 s prima di fermarlo)
        regola.waitUntil(15_000) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
            shadowOf(app.getSystemService(android.app.NotificationManager::class.java)).allNotifications
                .any { it.extras.getString(android.app.Notification.EXTRA_TITLE)?.contains("pronta da pubblicare") == true }
        }
        // Servizio fermato a fine lavoro
        assertNotNull(shadowOf(app).nextStoppedService)
    }

    @Test
    fun nessunAvvisoSeSeiNellApp() {
        val app = vm.getApplication<android.app.Application>()
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val file = Raddrizzatore.salva(regola.activity, Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888), "prova_avviso2")
        regola.runOnUiThread { AppVisibile.visibile = true; vm.carica(listOf(Uri.fromFile(file))) }
        regola.waitUntil(10_000) { vm.foto.isNotEmpty() && vm.foto.none { it.inCorso } }
        Thread.sleep(300); regola.waitForIdle()
        val avvisi = shadowOf(app.getSystemService(android.app.NotificationManager::class.java)).allNotifications
        assertTrue(avvisi.none { it.extras.getString(android.app.Notification.EXTRA_TITLE)?.contains("pronta") == true })
    }

    @Test
    fun pubblicaSubitoDopoGiraAspettaLaFotoGirata() {
        metti(fotoDiProva(1, listOf(felpa)))
        apriVisore()
        regola.onNodeWithText("Destra").performClick()
        regola.onAllNodesWithText("✕").onLast().performClick()   // chiudo il visore
        // Subito "Pubblica la prossima": l'app aspetta che la foto girata sia pronta, poi la passa a WhatsApp
        regola.onNodeWithText("Pubblica la prossima · Foto 1").performClick()
        // (nel simulatore la condivisione del file nuovo non riesce per via delle cartelle dei test:
        //  conta che il tentativo avvenga solo DOPO che la foto girata è pronta)
        regola.waitUntil(8_000) { vm.foto[0].pubblicata || vm.messaggio != null }
        assertEquals(90, vm.foto[0].rotazioneFile)
        assertTrue(vm.pronta(1))
    }

    @Test
    fun coloriDalleImpostazioni() {
        val prima = Azzurro
        regola.onNodeWithText("⚙").performClick()
        regola.onNodeWithText("🌿 Salvia").performClick()
        regola.waitForIdle()
        assertEquals(1, vm.tavolozza)
        assertEquals(1, TemaApp.tavolozza)
        assertNotEquals(prima, Azzurro)
        // Resta salvato
        assertEquals(1, FotoViewModel(vm.getApplication()).also { it.foto.clear() }.tavolozza)
        regola.onNodeWithText("🧸 Da bimbo a bimbo").performClick()
        regola.waitForIdle()
        assertEquals(prima, Azzurro)
    }

    @Test
    fun ilMioColore() {
        regola.onNodeWithText("⚙").performClick()
        regola.onNodeWithText("🎨 Il mio colore").performScrollTo().performClick()
        regola.onNodeWithTag("colore_${0xFFD1495B}").performScrollTo().performClick()
        regola.waitForIdle()
        assertEquals(IL_MIO_COLORE, vm.tavolozza)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFD1495B), Azzurro)   // il pulsante principale prende il colore scelto
        TemaApp.modo = 1
        assertNotEquals(androidx.compose.ui.graphics.Color(0xFFD1495B), Azzurro)  // in scuro una versione più adatta
        TemaApp.modo = 0
        assertEquals(0xFFD1495B, FotoViewModel(vm.getApplication()).also { it.foto.clear() }.coloreMio)
    }
}
