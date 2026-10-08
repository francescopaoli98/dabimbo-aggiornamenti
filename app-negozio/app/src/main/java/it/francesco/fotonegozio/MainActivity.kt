package it.francesco.fotonegozio

import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType

class MainActivity : ComponentActivity() {

    private val viewModel: FotoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App sempre chiara: icone di sistema (ora, batteria) sempre scure, anche col telefono in modalità scura
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
        )
        // Foto arrivate dal tasto "Condividi" della Galleria (solo al primo avvio, non dopo una rotazione schermo)
        if (savedInstanceState == null) viewModel.carica(fotoDaIntent(intent))
        // Chiaro / scuro: dalle impostazioni, o come il telefono
        TemaApp.modo = viewModel.tema
        TemaApp.tavolozza = viewModel.tavolozza
        TemaApp.coloreMio = viewModel.coloreMio
        TemaApp.telefonoScuro = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        setContent {
            // Icone di sistema (ora, batteria) chiare sul tema scuro e scure su quello chiaro
            val scuro = TemaApp.scuro
            LaunchedEffect(scuro) {
                val barre = if (scuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = barre, navigationBarStyle = barre)
            }
            ConScritte(viewModel.scalaTesto) { TemaBimbo { Schermata(viewModel) } }
        }

        // Tasto Indietro sulla lista delle foto: la prima volta avvisa, la seconda (entro 2 secondi) chiude l'app.
        // Così un tocco per sbaglio non fa perdere le foto. Le finestre aperte (articoli, foto grande…) si chiudono da sole.
        onBackPressedDispatcher.addCallback(this) {
            val adesso = System.currentTimeMillis()
            if (adesso - ultimoIndietro < 2000) finish()
            else {
                ultimoIndietro = adesso
                Toast.makeText(this@MainActivity, "Premi ancora Indietro per uscire", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var ultimoIndietro = 0L

    override fun onStart() {
        super.onStart()
        AppVisibile.visibile = true
        Avvisi.togliPronte(this)
        // Ogni volta che si torna nell'app (al massimo ogni 10 minuti) guardo se c'è una versione nuova
        viewModel.controllaAggiornamentiOgniTanto()
    }
    override fun onStop() { AppVisibile.visibile = false; super.onStop() }

    // App già aperta e Elisa condivide altre foto dalla Galleria
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.carica(fotoDaIntent(intent))
    }

    /** Estrae le foto da un intent "Condividi" (una sola o più di una). */
    @Suppress("DEPRECATION")
    private fun fotoDaIntent(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        )
        Intent.ACTION_SEND_MULTIPLE ->
            (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
        else -> emptyList()
    }
}

/** Grandezza del testo scelta nelle impostazioni (letta anche dalle finestre, che hanno un loro schermo). */
private val LocalScalaTesto = compositionLocalOf { 1f }

/** Ingrandisce o rimpicciolisce tutte le scritte di [scala] (il resto della grafica resta uguale). */
@Composable
private fun ConScritte(scala: Float, contenuto: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalScalaTesto provides scala,
        LocalDensity provides Density(d.density, d.fontScale * scala),
        content = contenuto,
    )
}

/** Finestra a tutto schermo che rispetta anche lei la grandezza del testo. */
@Composable
internal fun Finestra(onDismissRequest: () -> Unit, properties: DialogProperties, contenuto: @Composable () -> Unit) {
    val scala = LocalScalaTesto.current
    Dialog(onDismissRequest, properties) { ConScritte(scala, contenuto) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Schermata(vm: FotoViewModel) {
    // Selettore foto di sistema, più foto insieme, nessun permesso richiesto
    val scegli = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        vm.carica(it)
    }
    // Permesso per gli avvisi (Android 13+): chiesto una volta sola, quando arrivano le prime foto
    val permessoAvvisi = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val ctx = LocalContext.current
    LaunchedEffect(vm.foto.isNotEmpty()) {
        if (vm.foto.isNotEmpty() && Build.VERSION.SDK_INT >= 33 && !vm.permessoChiesto && !Avvisi.puoAvvisare(ctx)) {
            vm.permessoChiesto = true
            permessoAvvisi.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var ingrandita by remember { mutableStateOf<Int?>(null) }   // numero della foto aperta a schermo intero
    var inModifica by remember { mutableStateOf<Pair<Int, Int?>?>(null) }   // (foto, articolo) - articolo null = nuovo
    var dizionarioAperto by remember { mutableStateOf(false) }
    var testoInModifica by remember { mutableStateOf<Int?>(null) }   // numero della foto di cui si corregge il testo
    var articoliAperti by remember { mutableStateOf<Int?>(null) }    // numero della foto di cui si guardano gli articoli
    var daConfermare by remember { mutableStateOf<Int?>(null) }      // foto con avvisi: chiedo prima di pubblicare
    var daTogliere by remember { mutableStateOf<Int?>(null) }        // foto da togliere dalla lista (chiedo conferma)
    var impostazioniAperte by remember { mutableStateOf(false) }
    var riepilogoAperto by remember { mutableStateOf(false) }
    var instagramAperto by remember { mutableStateOf(false) }
    var guideAperte by remember { mutableStateOf(false) }
    var chiediMenu by remember { mutableStateOf(false) }               // ✕: tornare al menu principale (chiedo se mancano foto)
    val riaperte = remember { mutableStateListOf<Int>() }              // foto pubblicate riaperte (se le schede si comprimono)
    val lista = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Foto + testo a WhatsApp Business. L'invio lo preme Elisa dentro WhatsApp.
    val vibra = LocalHapticFeedback.current
    // Coriandoli quando sono tutte pubblicate
    val tuttePubblicate = vm.foto.isNotEmpty() && vm.quantePubblicate == vm.foto.size
    var festa by remember { mutableStateOf(false) }
    var mancavano by remember { mutableStateOf(!tuttePubblicate) }
    LaunchedEffect(tuttePubblicate) {
        if (!tuttePubblicate) mancavano = true
        else if (mancavano) { mancavano = false; festa = true; kotlinx.coroutines.delay(3400); festa = false }
    }
    fun pubblica(f: Foto) {
        vibra.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            // Se la foto si sta ancora sistemando (appena girata o pixelata), aspetto la versione finale
            if (!vm.pronta(f.numero)) snapshotFlow { vm.pronta(f.numero) }.first { it }
            val attuale = vm.foto.firstOrNull { it.numero == f.numero } ?: return@launch
            val file = attuale.file ?: return@launch
            if (Condivisione.pubblica(context, file, vm.testo(attuale))) vm.segnaPubblicata(attuale.numero)
            else vm.messaggio = "WhatsApp non trovato sul telefono"
        }
    }
    fun chiediEPubblica(f: Foto) {
        if (f.avvisi.isEmpty() && vm.giaPubblicati(f).isEmpty() && vm.giaPrenotati(f).isEmpty()) pubblica(f) else daConfermare = f.numero
    }
    fun scegliFoto() = scegli.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Scaffold(
        containerColor = SfondoLista,
        bottomBar = { if (vm.foto.isNotEmpty()) BarraPubblica(vm) { vm.prossima?.let { chiediEPubblica(it) } } },
    ) { padding ->
      Box(Modifier.fillMaxSize()) {
        SfondoNuvole()   // ferme: belle e senza consumare
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            state = lista,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (vm.foto.isEmpty()) 20.dp else 12.dp),
        ) {
            // In alto: ✕ torna al menu principale (solo con delle foto), ⚙ impostazioni (sempre).
            // Con delle foto in lista il logo diventa piccolo e sta nella stessa riga: più spazio alle foto.
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (vm.foto.isNotEmpty()) PulsanteTondo("✕", {
                        if (vm.foto.all { it.pubblicata }) vm.svuota() else chiediMenu = true
                    })
                    if (vm.foto.isNotEmpty()) Image(
                        painterResource(R.drawable.logo), "Da bimbo a bimbo",
                        Modifier.weight(1f).height(56.dp).padding(horizontal = 10.dp)
                            .combinedClickable(onClick = {}, onLongClick = { vm.prove = !vm.prove }),
                        contentScale = ContentScale.Fit,
                    ) else Spacer(Modifier.weight(1f))
                    PulsanteTondo("❓", { guideAperte = true }, Modifier.testTag("apri_guide"))
                    Spacer(Modifier.width(8.dp))
                    PulsanteTondo("⚙", { impostazioniAperte = true })
                }
            }
            // Versione nuova dell'app
            vm.novita?.let { n ->
                item { AvvisoAggiornamento(n, vm.scaricamento, vm::aggiorna) }
            }
            // Logo grande solo all'inizio (tenuto premuto: modalità prove, solo per chi sistema l'app)
            if (vm.foto.isEmpty()) item {
                Image(
                    painterResource(R.drawable.logo), "Da bimbo a bimbo",
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .combinedClickable(onClick = {}, onLongClick = { vm.prove = !vm.prove }),
                    contentScale = ContentScale.FillWidth,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val alto = if (vm.foto.isEmpty()) 60.dp else 48.dp
                    PulsanteGrande("Scegli foto", "📷", ::scegliFoto, Modifier.weight(1f), altezza = alto, grandezzaTesto = if (vm.foto.isEmpty()) 18 else 16)
                    PulsanteChiaro("Sigle", "📖", { dizionarioAperto = true }, sfondo = Rosa, altezza = alto, grandezzaTesto = 15)
                }
            }
            // Riepilogo di oggi (toccandolo: i giorni prima). Se oggi niente, si apre lo storico lo stesso.
            val g = vm.oggiPubblicati
            if (g == null && vm.registro.isNotEmpty()) item {
                Surface(onClick = { riepilogoAperto = true }, color = Superficie, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, BordoScheda)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📊", fontSize = 16.sp)
                        Text("Storico pubblicati", fontWeight = FontWeight.Bold, color = BluNotte, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Text("›", color = BluNotte, fontSize = 20.sp)
                    }
                }
            }
            if (g != null) {
                item {
                    Surface(onClick = { riepilogoAperto = true }, color = Superficie, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, BordoScheda)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📊", fontSize = 16.sp)
                            Text(
                                "Oggi: ${g.articoli} ${if (g.articoli == 1) "articolo" else "articoli"} · ${Riepilogo.euro(g.centesimi)}",
                                fontWeight = FontWeight.Bold, color = BluNotte, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp).weight(1f),
                            )
                            if (g.prenotati > 0) Text("📌 ${g.prenotati}", color = Verde, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                            Text("›", color = BluNotte, fontSize = 20.sp)
                        }
                    }
                }
            }
            // Instagram: storie e carosello (dalla lista di adesso o dai giorni passati)
            if (vm.foto.any { !it.inCorso && it.file != null } || vm.registro.any { it.foto.isNotEmpty() }) item {
                Surface(onClick = { instagramAperto = true }, color = Superficie, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, BordoScheda)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📸", fontSize = 16.sp)
                        Text("Instagram: storie e carosello", fontWeight = FontWeight.Bold, color = BluNotte, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Text("›", color = BluNotte, fontSize = 20.sp)
                    }
                }
            }
            if (vm.foto.isNotEmpty()) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val quante = vm.foto.count { it.daGuardare }
                    if (vm.foto.isNotEmpty()) {
                        FilterChip(
                            selected = !vm.soloDaControllare, onClick = { vm.soloDaControllare = false },
                            label = { Text("Tutte (${vm.foto.size})") },
                        )
                        FilterChip(
                            selected = vm.soloDaControllare, onClick = { vm.soloDaControllare = true },
                            enabled = quante > 0 || vm.soloDaControllare,
                            label = { Text("⚠ Da controllare ($quante)") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Arancione, selectedLabelColor = Color.White),
                        )
                    }
                }
            }
            if (vm.soloDaControllare && vm.fotoVisibili.isEmpty()) item {
                Surface(onClick = { vm.soloDaControllare = false }, color = Superficie, shape = MaterialTheme.shapes.medium) {
                    Text("Nessuna foto da controllare 👍  Tocca per vederle tutte.", Modifier.fillMaxWidth().padding(16.dp), color = BluNotte, fontWeight = FontWeight.Bold)
                }
            }
            // Solo in modalità prove
            if (vm.prove) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Diagnosi (modalità prove)", Modifier.weight(1f), fontSize = 14.sp, color = TestoTenue)
                    Switch(checked = vm.diagnosi, onCheckedChange = { vm.diagnosi = it })
                }
            }
            vm.messaggio?.let { m ->
                item {
                    Surface(onClick = { vm.messaggio = null }, color = Cielo, shape = MaterialTheme.shapes.medium) {
                        Text(m, Modifier.fillMaxWidth().padding(14.dp), color = BluNotte, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (vm.foto.isEmpty()) {
                item { Benvenuto() }
            } else if (vm.elaborate < vm.foto.size) {
                item { Avanzamento(vm.elaborate, vm.foto.size) }
            }

            items(vm.fotoVisibili, key = { it.numero }) { f ->
                val comprimibile = f.pubblicata && vm.comprimiPubblicate
                if (comprimibile && f.numero !in riaperte) {
                    SchedaCompatta(f, Modifier.animateItem()) { riaperte += f.numero }
                    return@items
                }
                Scheda(
                    f,
                    modifier = Modifier.animateItem(),
                    prove = vm.prove,
                    testo = vm.testo(f),
                    ingrandisci = { ingrandita = f.numero },
                    articoli = { articoliAperti = f.numero },
                    modificaTesto = { testoInModifica = f.numero },
                    pubblica = { chiediEPubblica(f) },
                    salvaDiagnosi = { vm.salvaDiagnosi(f.numero) },
                    giaPubblicati = vm.giaPubblicati(f),
                    giaPrenotati = vm.giaPrenotati(f),
                    zoomAnteprima = vm.zoomAnteprima,
                    togli = { daTogliere = f.numero },
                    comprimi = if (comprimibile) ({ riaperte -= f.numero }) else null,
                )
            }
        }
        // Pulsantino "torna su" (se acceso nelle impostazioni), solo quando si è scesi un po'
        val sceso by remember { derivedStateOf { lista.firstVisibleItemIndex > 2 } }
        AnimatedVisibility(
            vm.tornaSu && sceso,
            Modifier.align(Alignment.BottomEnd).padding(padding).padding(16.dp),
            enter = fadeIn() + scaleIn(), exit = fadeOut(),
        ) {
            PulsanteTondo("↑", { scope.launch { lista.animateScrollToItem(0) } }, colore = Azzurro)
        }
        if (festa) Coriandoli()
      }
    }

    if (riepilogoAperto) {
        SchermataRiepilogo(vm) { riepilogoAperto = false }
    }

    if (instagramAperto) {
        SchermataInstagram(vm) { instagramAperto = false }
    }

    if (guideAperte) {
        SchermataGuide { guideAperte = false }
    }

    if (impostazioniAperte) {
        SchermataImpostazioni(vm) { impostazioniAperte = false }
    }

    // ✕ con foto non ancora pubblicate: chiedo prima
    if (chiediMenu) {
        val mancano = vm.foto.count { !it.pubblicata }
        AlertDialog(
            onDismissRequest = { chiediMenu = false },
            title = { Text("Stai per tornare al menu principale") },
            text = {
                Text(
                    (if (mancano == 1) "1 foto non è ancora stata pubblicata e non verrà caricata."
                    else "$mancano foto non sono ancora state pubblicate e non verranno caricate.") + "\n\nSei sicura?"
                )
            },
            confirmButton = { TextButton(onClick = { chiediMenu = false; vm.svuota() }) { Text("Sì, torna al menu", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { chiediMenu = false }) { Text("No") } },
        )
    }

    if (dizionarioAperto) {
        SchermataDizionario(vm.dizionario, salva = vm::salvaSigla, chiudi = { dizionarioAperto = false })
    }

    // Foto con avvisi: prima di pubblicare chiedo
    daConfermare?.let { numero ->
        vm.foto.firstOrNull { it.numero == numero }?.let { f ->
            AlertDialog(
                onDismissRequest = { daConfermare = null },
                title = { Text("Foto ${f.numero}: da controllare") },
                text = {
                    val gia = vm.giaPrenotati(f).map { "già PRENOTATO: cod. $it" } + vm.giaPubblicati(f).map { "già pubblicato: cod. $it" }
                    Text((gia + f.avvisi).joinToString("\n") { "• $it" } + "\n\nVuoi sistemarla prima, o pubblicarla così?")
                },
                confirmButton = { TextButton(onClick = { daConfermare = null; pubblica(f) }) { Text("Pubblica lo stesso") } },
                dismissButton = { TextButton(onClick = { daConfermare = null }) { Text("La sistemo") } },
            )
        }
    }

    // Togliere una foto dalla lista: chiedo prima
    daTogliere?.let { numero ->
        AlertDialog(
            onDismissRequest = { daTogliere = null },
            title = { Text("Stai per rimuovere la Foto $numero") },
            text = { Text("Sei sicura?\n\nSparisce solo da questa lista: nella Galleria del telefono resta.") },
            confirmButton = { TextButton(onClick = { vm.togli(numero); daTogliere = null }) { Text("Sì, rimuovi", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { daTogliere = null }) { Text("No") } },
        )
    }

    // Correzione del testo per lo stato (schermo diviso con la foto)
    testoInModifica?.let { numero ->
        vm.foto.firstOrNull { it.numero == numero }?.let { f ->
            ModificaTesto(
                foto = f.file,
                iniziale = vm.testo(f),
                manuale = f.testoManuale != null,
                salva = { t -> vm.cambiaTesto(numero, t); testoInModifica = null },
                annulla = { testoInModifica = null },
            )
        }
    }

    // Articoli della foto (schermata a parte: niente schede che si allungano)
    articoliAperti?.let { numero ->
        vm.foto.firstOrNull { it.numero == numero }?.let { f ->
            SchermataArticoli(
                f,
                modifica = { indice -> inModifica = numero to indice },
                chiudi = { articoliAperti = null },
            )
        }
    }

    // Modifica / aggiunta di un articolo a mano
    inModifica?.let { (numero, indice) ->
        val f = vm.foto.firstOrNull { it.numero == numero }
        if (f != null) {
            ModificaArticolo(
                foto = f.file,
                iniziale = indice?.let { f.articoli.getOrNull(it) },
                nuovo = indice == null,
                salva = { d -> vm.salvaArticolo(numero, indice, d); inModifica = null },
                annulla = { inModifica = null },
            )
        }
    }

    // Cerco sempre la versione aggiornata, così dopo "Gira" l'ingrandimento cambia subito
    vm.foto.firstOrNull { it.numero == ingrandita }?.let { f ->
        Visore(
            f,
            sfondoInCorso = vm.sfondoInCorso == f.numero,
            gira = { gradi -> vibra.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.gira(f.numero, gradi) },
            pixelaSfondo = { vm.pixelaSfondo(f.numero) },
            togliSfondo = { vm.togliPixelSfondo(f.numero) },
            salvaPixel = { celle, lato, rimesse -> vm.salvaPixelManuale(f.numero, celle, lato, rimesse) },
            togliPixelMano = { vm.togliPixelManuale(f.numero) },
            salvaRitaglio = { q -> vm.salvaRitaglio(f.numero, q) },
            testo = { ingrandita = null; testoInModifica = f.numero },
            chiudi = { ingrandita = null },
        )
    }
}

/** Barra in basso: quante pubblicate e il pulsante grande per la prossima. */
@Composable
private fun BarraPubblica(vm: FotoViewModel, pubblica: () -> Unit) {
    val prossima = vm.prossima
    val tutte = vm.foto.size
    val fatte = vm.quantePubblicate
    Surface(color = Superficie, shadowElevation = 12.dp, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
            // Una riga sottile: "Pubblicate 3 di 10" con la barretta accanto
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pubblicate $fatte di $tutte" + if (fatte == tutte) " 🎉" else "", fontSize = 13.sp, color = BluNotte)
                val avanzamento by animateFloatAsState(if (tutte == 0) 0f else fatte / tutte.toFloat(), label = "pubblicate")
                LinearProgressIndicator(
                    progress = { avanzamento },
                    modifier = Modifier.weight(1f).padding(start = 10.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = Verde, trackColor = Cielo,
                )
            }
            PulsanteGrande(
                when {
                    prossima != null -> "Pubblica la prossima · Foto ${prossima.numero}"
                    fatte == tutte -> "Tutte pubblicate!"
                    else -> "Un attimo, preparo le foto…"
                },
                if (prossima != null) "📤" else if (fatte == tutte) "🎉" else "⏳",
                pubblica,
                Modifier.fillMaxWidth().padding(top = 6.dp),
                colore = if (fatte == tutte) Verde else Azzurro,
                attivo = prossima != null,
                altezza = 52.dp,
            )
        }
    }
}

/** "È arrivata la versione 2.8": un tocco e si aggiorna (Android chiede poi "Installa"). */
@Composable
private fun AvvisoAggiornamento(n: Novita, scaricamento: Float?, aggiorna: () -> Unit) {
    Surface(color = Superficie, shape = MaterialTheme.shapes.large, border = BorderStroke(2.dp, Verde), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎁 È arrivata la versione ${n.versionName}!", fontWeight = FontWeight.ExtraBold, color = BluNotte, fontSize = 17.sp)
            if (n.note.isNotBlank()) Text(n.note, fontSize = 14.sp, color = TestoTenue)
            if (scaricamento != null) {
                LinearProgressIndicator(
                    progress = { scaricamento },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Verde, trackColor = Cielo,
                )
                Text("Scarico… ${(scaricamento * 100).toInt()}%", fontSize = 13.sp, color = BluNotte)
            } else {
                PulsanteGrande("Aggiorna", "⬇", aggiorna, Modifier.fillMaxWidth(), colore = Verde, altezza = 52.dp)
                Text("Poi Android chiede \"Installa\": toccalo e l'app si aggiorna. Le foto in lista restano.", fontSize = 12.sp, color = TestoTenue)
            }
        }
    }
}

/**
 * Storico delle pubblicazioni: i giorni con i totali; toccando un giorno si vedono i suoi articoli,
 * con la fotina (si ingrandisce), la spunta "Prenotato" (e "Venduto" se accesa nelle impostazioni).
 * Il ✕ per togliere un articolo contato per sbaglio c'è solo con "Sblocca cancellazione".
 */
@Composable
private fun SchermataRiepilogo(vm: FotoViewModel, chiudi: () -> Unit) {
    val nomi = remember { java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.ITALY) }
    val leggi = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ITALY) }
    fun nomeGiorno(g: String) = runCatching { nomi.format(leggi.parse(g)!!) }.getOrDefault(g).replaceFirstChar { it.uppercase() }
    var giorno by remember { mutableStateOf<String?>(null) }
    var daTogliere by remember { mutableStateOf<Pubblicato?>(null) }
    var sbloccata by remember { mutableStateOf(false) }
    var grande by remember { mutableStateOf<File?>(null) }   // fotina ingrandita
    var avvisoPrenotato by remember { mutableStateOf<Pubblicato?>(null) }   // appena prenotato: cosa fare con le storie
    var guida by remember { mutableStateOf(false) }
    // Articoli pubblicati senza fotina (es. prima della 4.8): la rifaccio dalle foto ancora in lista
    LaunchedEffect(Unit) { vm.recuperaMiniature() }

    Finestra(onDismissRequest = { if (giorno != null) giorno = null else chiudi() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(SfondoLista).systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Rosa, Cielo))).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (giorno != null) PulsanteTondo("‹", { giorno = null })
                Text(
                    giorno?.let(::nomeGiorno) ?: "📊 Pubblicati",
                    fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = BluNotte,
                    modifier = Modifier.weight(1f).padding(start = if (giorno != null) 12.dp else 0.dp),
                )
                PulsanteTondo("❓", { guida = true })
                Spacer(Modifier.width(8.dp))
                PulsanteTondo("✕", chiudi)
            }
            ProponiGuida(Guide.STORICO, vm.guidaVista(Guide.STORICO.id), { vm.segnaGuidaVista(Guide.STORICO.id) }) { guida = true }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val g = giorno
                if (g == null) {
                    item { Text("Tocca un giorno per vedere gli articoli e segnare i prenotati.", color = TestoTenue, fontSize = 14.sp) }
                    items(vm.giornate.take(60), key = { it.giorno }) { gg ->
                        Surface(onClick = { giorno = gg.giorno }, color = Superficie, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, BordoScheda)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(nomeGiorno(gg.giorno), fontSize = 16.sp)
                                    if (gg.prenotati > 0) Text("📌 ${gg.prenotati} prenotati · ${Riepilogo.euro(gg.centesimiPrenotati)}", fontSize = 13.sp, color = Verde, fontWeight = FontWeight.Bold)
                                    if (vm.mostraVenduto && gg.venduti > 0) Text("💶 ${gg.venduti} venduti · ${Riepilogo.euro(gg.centesimiVenduti)}", fontSize = 13.sp, color = Azzurro, fontWeight = FontWeight.Bold)
                                }
                                Text("${gg.articoli} · ${Riepilogo.euro(gg.centesimi)}", fontWeight = FontWeight.Bold, color = BluNotte)
                                Text("  ›", color = BluNotte, fontSize = 18.sp)
                            }
                        }
                    }
                } else {
                    val tot = vm.giornate.firstOrNull { it.giorno == g }
                    item {
                        // Totali del giorno e, in alto, il lucchetto della cancellazione
                        Surface(color = Superficie, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, BordoScheda)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                tot?.let {
                                    Text("${it.articoli} ${if (it.articoli == 1) "pubblicato" else "pubblicati"} · ${Riepilogo.euro(it.centesimi)}", fontWeight = FontWeight.Bold, color = BluNotte, fontSize = 15.sp)
                                    Text("📌 Prenotati: ${it.prenotati} · ${Riepilogo.euro(it.centesimiPrenotati)}", color = Verde, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (vm.mostraVenduto) Text("💶 Venduti: ${it.venduti} · ${Riepilogo.euro(it.centesimiVenduti)}", color = Azzurro, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).clickable { sbloccata = !sbloccata },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        if (sbloccata) "🔓 Cancellazione sbloccata" else "🔒 Sblocca cancellazione",
                                        fontSize = 14.sp, color = if (sbloccata) Arancione else TestoTenue, modifier = Modifier.weight(1f),
                                    )
                                    Switch(checked = sbloccata, onCheckedChange = { sbloccata = it }, modifier = Modifier.testTag("sblocca"))
                                }
                            }
                        }
                    }
                    items(vm.articoliDel(g), key = { it.chiave }) { p ->
                        RigaStorico(
                            p, File(vm.cartellaStorico, p.miniatura).takeIf { p.miniatura.isNotEmpty() },
                            mostraVenduto = vm.mostraVenduto, cancellabile = sbloccata,
                            ingrandisci = { grande = it },
                            prenota = { vm.segnaPrenotato(p, it); if (it) avvisoPrenotato = p },
                            avvisa = { avvisoPrenotato = p },
                            vendi = { vm.segnaVenduto(p, it) },
                            togli = { daTogliere = p },
                        )
                    }
                }
            }
        }
    }

    grande?.let { file ->
        val img by fotoPerSchermo(file)
        Finestra(onDismissRequest = { grande = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().testTag("fotina_grande")) {
                img?.let { FotoZoomabile(it, Modifier.fillMaxSize(), file) }
                PulsanteTondo("✕", { grande = null }, Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
        }
    }

    avvisoPrenotato?.let { p -> DialogoPrenotato(vm, p) { avvisoPrenotato = null } }
    if (guida) SchermataGuide(Guide.STORICO) { guida = false }

    daTogliere?.let { p ->
        AlertDialog(
            onDismissRequest = { daTogliere = null },
            title = { Text("Togliere dal conteggio?") },
            text = { Text("cod. ${p.chiave} · ${Riepilogo.euro(p.centesimi)}\n\nSolo se per sbaglio non l'hai caricato su WhatsApp.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.togliDalRiepilogo(p); daTogliere = null
                    if (vm.articoliDel(p.giorno).isEmpty()) giorno = null
                }) { Text("Sì, togli", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { daTogliere = null }) { Text("No") } },
        )
    }
}

/**
 * Appena prenotato: l'app non può togliere da sola le storie già pubblicate (WhatsApp e Instagram non lo permettono),
 * quindi aiuta Elisa: apre WhatsApp/Instagram per toglierle, prepara la storia "PRENOTATO",
 * e rifà il testo dei caroselli dove c'era l'articolo.
 */
@Composable
private fun DialogoPrenotato(vm: FotoViewModel, p: Pubblicato, chiudi: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val caroselli = remember(p) { p.chiave.takeIf { c -> c.all(Char::isDigit) }?.let(vm::caroselliCon).orEmpty() }
    var preparo by remember { mutableStateOf(false) }
    fun nonApre() = Toast.makeText(context, "App non trovata sul telefono", Toast.LENGTH_SHORT).show()
    fun pubblicaPrenotato(suWhatsApp: Boolean) {
        if (preparo) return
        preparo = true
        scope.launch {
            val file = vm.preparaPrenotato(context, p)
            preparo = false
            when {
                file == null -> Toast.makeText(context, "La foto di questo articolo non c'è più", Toast.LENGTH_LONG).show()
                suWhatsApp -> if (!Condivisione.pubblica(context, file, "PRENOTATO - " + TestoInstagram.riga(TestoInstagram.daStorico(p)))) nonApre()
                else -> if (!CondividiInstagram.condividi(context, listOf(file), null)) nonApre()
            }
        }
    }
    AlertDialog(
        onDismissRequest = chiudi,
        title = { Text("📌 Prenotato: ${p.nome.ifBlank { "cod. ${p.chiave}" }}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Togli la sua storia (l'app non può farlo da sola):", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PulsanteChiaro("WhatsApp", "", { if (!ApriApp.whatsapp(context)) nonApre() }, Modifier.weight(1f), altezza = 44.dp, grandezzaTesto = 13)
                    PulsanteChiaro("Instagram", "", { if (!ApriApp.instagram(context)) nonApre() }, Modifier.weight(1f), altezza = 44.dp, grandezzaTesto = 13)
                }
                Text("2. Se vuoi, pubblica la storia \"PRENOTATO\":", fontWeight = FontWeight.Bold)
                if (preparo) Text("Preparo la foto…", color = TestoTenue)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PulsanteChiaro("WhatsApp", "", { pubblicaPrenotato(true) }, Modifier.weight(1f).testTag("prenotato_wa"), sfondo = Rosa, altezza = 44.dp, grandezzaTesto = 13, attivo = !preparo)
                    PulsanteChiaro("Instagram", "", { pubblicaPrenotato(false) }, Modifier.weight(1f).testTag("prenotato_ig"), sfondo = Rosa, altezza = 44.dp, grandezzaTesto = 13, attivo = !preparo)
                }
                if (caroselli.isNotEmpty()) {
                    val c = caroselli.first()
                    Text("3. Era nel carosello di Instagram del ${c.giorno.split('-').reversed().take(2).joinToString("/")}:", fontWeight = FontWeight.Bold)
                    PulsanteChiaro(
                        "Copia il testo aggiornato", "📋",
                        {
                            context.getSystemService(android.content.ClipboardManager::class.java)
                                ?.setPrimaryClip(android.content.ClipData.newPlainText("Testo per Instagram", vm.didascaliaAggiornata(c)))
                            Toast.makeText(context, "Copiato! Apri il post, tocca ⋯ → Modifica e incolla", Toast.LENGTH_LONG).show()
                        },
                        Modifier.fillMaxWidth().testTag("copia_carosello"), altezza = 44.dp, grandezzaTesto = 13,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = chiudi) { Text("Fatto", fontWeight = FontWeight.Bold) } },
    )
}

/** Un articolo dello storico: fotina, nome, codice e prezzo, spunte Prenotato (e Venduto). */
@Composable
private fun RigaStorico(
    p: Pubblicato, fotina: File?, mostraVenduto: Boolean, cancellabile: Boolean,
    ingrandisci: (File) -> Unit, prenota: (Boolean) -> Unit, vendi: (Boolean) -> Unit, togli: () -> Unit,
    avvisa: () -> Unit = {},
) {
    // La fotina si legge in sottofondo (appena pubblicata potrebbe essere ancora in scrittura: riprovo un attimo)
    val img by produceState<ImageBitmap?>(null, fotina) {
        val f = fotina ?: return@produceState
        repeat(10) {
            if (f.exists()) { value = withContext(Dispatchers.IO) { caricaRidotta(f, 256) }; if (value != null) return@produceState }
            kotlinx.coroutines.delay(300)
        }
    }
    Surface(
        color = Superficie, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (p.prenotato) 2.dp else 1.dp, if (p.prenotato) Verde else BordoScheda),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(FondoTenue)
                    .then(if (fotina != null && img != null) Modifier.clickable { ingrandisci(fotina) } else Modifier)
                    .testTag("fotina_${p.chiave}"),
                contentAlignment = Alignment.Center,
            ) {
                val i = img
                if (i != null) Image(i, "Foto", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text("🖼", fontSize = 22.sp)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(p.nome.ifBlank { "Articolo" }, fontSize = 16.sp, maxLines = 2)
                Text("cod. ${p.chiave} · ${Riepilogo.euro(p.centesimi)}", fontSize = 13.sp, color = TestoTenue)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.prenotato) {
                        FilterChip(
                            selected = true, onClick = {}, label = { Text("✓ Prenotato", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Verde.copy(alpha = 0.22f)),
                        )
                        TextButton(onClick = { prenota(false) }) { Text("Annulla prenotazione", color = Arancione, fontSize = 13.sp) }
                        TextButton(onClick = avvisa, modifier = Modifier.testTag("avvisa_${p.chiave}")) { Text("📣 Storie e testo", color = Azzurro, fontSize = 13.sp) }
                    } else {
                        FilterChip(selected = false, onClick = { prenota(true) }, label = { Text("📌 Prenotato") }, modifier = Modifier.testTag("prenota_${p.chiave}"))
                    }
                    if (mostraVenduto) FilterChip(
                        selected = p.venduto, onClick = { vendi(!p.venduto) },
                        label = { Text(if (p.venduto) "✓ Venduto" else "💶 Venduto") },
                        modifier = Modifier.testTag("vendi_${p.chiave}"),
                    )
                }
            }
            if (cancellabile) PulsanteTondo("✕", togli, Modifier.testTag("togli_${p.chiave}"), colore = Arancione)
        }
    }
}

/** Foto già pubblicata, chiusa in una riga piccola: toccandola si riapre. */
@Composable
private fun SchedaCompatta(f: Foto, modifier: Modifier = Modifier, apri: () -> Unit) {
    Surface(
        onClick = apri,
        color = Superficie.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, BordoScheda),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(FondoTenue)) {
                f.miniatura?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Text("Foto ${f.numero}", fontWeight = FontWeight.Bold, color = BluNotte, fontSize = 17.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
            Etichetta("✓ Pubblicata", Color.White, Verde)
            Text("▼", color = BluNotte, modifier = Modifier.padding(horizontal = 10.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/**
 * Impostazioni snelle: una riga per gruppo con il riassunto della scelta;
 * toccandola si apre solo quel gruppo (gli altri restano chiusi).
 */
@Composable
private fun SchermataImpostazioni(vm: FotoViewModel, chiudi: () -> Unit) {
    var guide by remember { mutableStateOf(false) }
    if (guide) SchermataGuide { guide = false }
    var aperta by remember { mutableStateOf<String?>(null) }
    fun apri(nome: String) { aperta = if (aperta == nome) null else nome }
    val nomiModo = listOf("Chiaro", "Scuro", "Come il telefono")
    val nomiTesto = mapOf(0.9f to "Piccolo", 1f to "Medio", 1.2f to "Grande", 1.4f to "Molto grande")
    Finestra(onDismissRequest = chiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(SfondoLista).systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Rosa, Cielo))).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚙ Impostazioni", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = BluNotte, modifier = Modifier.weight(1f))
                PulsanteTondo("✕", chiudi)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Sezione("🎨", "Aspetto", "${nomiModo[vm.tema]} · ${NOMI_TAVOLOZZE[vm.tavolozza].substringAfter(' ')}", aperta == "aspetto", { apri("aspetto") }) {
                    Scelte(nomiModo, vm.tema, vm::cambiaTema)
                    // Colori: una fila di pallini con il nome sotto
                    FlowRow(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        NOMI_TAVOLOZZE.indices.forEach { i ->
                            val scelto = vm.tavolozza == i
                            Column(
                                Modifier.width(64.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.cambiaTavolozza(i) }
                                    .testTag("tema_$i").padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape).background(coloreDelTema(i))
                                        .then(if (scelto) Modifier.border(3.dp, Testo, CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (scelto) "✓" else NOMI_TAVOLOZZE[i].substringBefore(' '), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                                Text(
                                    NOMI_TAVOLOZZE[i].substringAfter(' '), fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2,
                                    lineHeight = 13.sp, color = if (scelto) BluNotte else TestoTenue, fontWeight = if (scelto) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    // "Il mio colore": i 12 colori
                    AnimatedVisibility(vm.tavolozza == IL_MIO_COLORE) {
                        FlowRow(Modifier.padding(top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            COLORI_MIEI.forEach { c ->
                                val scelto = vm.coloreMio == c
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                        .then(if (scelto) Modifier.border(3.dp, Testo, CircleShape) else Modifier)
                                        .clickable { vm.cambiaColoreMio(c) }.testTag("colore_$c"),
                                    contentAlignment = Alignment.Center,
                                ) { if (scelto) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                Sezione("🔠", "Grandezza del testo", nomiTesto[vm.scalaTesto] ?: "Medio", aperta == "testo", { apri("testo") }) {
                    val valori = listOf(0.9f, 1f, 1.2f, 1.4f)
                    Scelte(valori.map { nomiTesto.getValue(it) }, valori.indexOf(vm.scalaTesto).coerceAtLeast(0)) { vm.cambiaScalaTesto(valori[it]) }
                    Text("Felpa con cappuccio rosa - 8 anni - € 4,00", color = TestoTenue, modifier = Modifier.padding(top = 8.dp))
                }
                Sezione(
                    "📋", "Lista delle foto",
                    listOfNotNull(
                        when (vm.zoomAnteprima) { ZoomAnteprima.RESTA -> "zoom resta"; ZoomAnteprima.TORNA -> "zoom torna"; ZoomAnteprima.SPENTO -> "zoom spento" },
                        "chiudi pubblicate".takeIf { vm.comprimiPubblicate }, "2 alla volta".takeIf { vm.dueAllaVolta }, "torna su".takeIf { vm.tornaSu })
                        .joinToString(" · ").ifEmpty { "tutto spento" }.replaceFirstChar { it.uppercase() },
                    aperta == "lista", { apri("lista") },
                ) {
                    Text("Zoom con due dita nell'anteprima", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Scelte(listOf("Resta", "Torna normale", "Spento"), vm.zoomAnteprima.ordinal) { vm.cambiaZoomAnteprima(ZoomAnteprima.entries[it]) }
                    Spacer(Modifier.height(8.dp))
                    Interruttore("Chiudi le foto già pubblicate", "Diventano una riga piccola; toccandole si riaprono.", vm.comprimiPubblicate, vm::cambiaComprimi)
                    Interruttore("2 foto alla volta", "Più veloce. Se il telefono rallenta, spegnilo.", vm.dueAllaVolta, vm::cambiaDueAllaVolta)
                    Interruttore("Pulsante \"torna su\"", "Un pulsantino ↑ per tornare in cima.", vm.tornaSu, vm::cambiaTornaSu)
                }
                // Le scelte singole stanno direttamente nella riga: niente da aprire
                RigaInterruttore("🔔", "Avvisami quando le foto sono pronte", vm.avvisoPronte, vm::cambiaAvvisoPronte)
                RigaInterruttore("💬", "Prezzo in grassetto su WhatsApp", vm.prezzoGrassetto, vm::cambiaGrassetto)
                RigaInterruttore("💶", "Spunta \"Venduto\" nello storico", vm.mostraVenduto, vm::cambiaMostraVenduto)
                val context = LocalContext.current
                val versione = remember { Aggiornamento.nomeVersione(context) }
                Surface(onClick = { guide = true }, color = Superficie, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, BordoScheda)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("❓", fontSize = 20.sp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Guide", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BluNotte)
                            Text("Come si fa, passo per passo", fontSize = 13.sp, color = TestoTenue)
                        }
                        Text("›", fontSize = 22.sp, color = BluNotte)
                    }
                }
                Sezione("📸", "Instagram", vm.hashtagIG.ifBlank { "Nessun hashtag" }, aperta == "instagram", { apri("instagram") }) {
                    Text("Prima riga del carosello", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    OutlinedTextField(vm.inizioIG, vm::cambiaInizioIG, Modifier.fillMaxWidth(), singleLine = true)
                    Text("Ultima riga (dove scrivervi)", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(vm.fineIG, vm::cambiaFineIG, Modifier.fillMaxWidth().testTag("fine_ig"))
                    Text("Hashtag (in fondo al testo)", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(vm.hashtagIG, vm::cambiaHashtagIG, Modifier.fillMaxWidth().testTag("hashtag_ig"))
                    TextButton(onClick = { vm.cambiaHashtagIG(TestoInstagram.HASHTAG); vm.cambiaInizioIG(TestoInstagram.INIZIO); vm.cambiaFineIG(TestoInstagram.FINE) }) { Text("Rimetti quelli di partenza") }
                    Text("Le foto pubblicate restano in buona qualità per $GIORNI_HD giorni, per poterle mettere su Instagram anche dopo.", fontSize = 12.sp, color = TestoTenue)
                }
                Sezione("🔄", "Aggiornamenti", vm.esitoControllo ?: "Versione $versione", aperta == "aggiornamenti", { apri("aggiornamenti") }) {
                    PulsanteChiaro("Controlla aggiornamenti", "🔄", { vm.controllaAggiornamenti(aMano = true) }, Modifier.fillMaxWidth(), altezza = 48.dp)
                    vm.novita?.let { n -> Box(Modifier.padding(top = 8.dp)) { AvvisoAggiornamento(n, vm.scaricamento, vm::aggiorna) } }
                }
            }
        }
    }
}

/** Una riga delle impostazioni: icona, titolo, riassunto; toccandola si apre il contenuto. */
@Composable
private fun Sezione(
    icona: String, titolo: String, riassunto: String, aperta: Boolean, tocca: () -> Unit,
    contenuto: @Composable ColumnScope.() -> Unit,
) {
    val giro by animateFloatAsState(if (aperta) 90f else 0f, label = "freccia")
    Surface(color = Superficie, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, BordoScheda)) {
        Column(Modifier.animateContentSize()) {
            Row(Modifier.fillMaxWidth().clickable(onClick = tocca).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(icona, fontSize = 20.sp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(titolo, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BluNotte)
                    Text(riassunto, fontSize = 13.sp, color = TestoTenue, maxLines = 1)
                }
                Text("›", fontSize = 22.sp, color = BluNotte, modifier = Modifier.graphicsLayer { rotationZ = giro })
            }
            if (aperta) Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) { contenuto() }
        }
    }
}

/** Una riga con l'interruttore direttamente dentro (per le scelte sì/no). */
@Composable
private fun RigaInterruttore(icona: String, titolo: String, acceso: Boolean, cambia: (Boolean) -> Unit) {
    Surface(color = Superficie, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, BordoScheda)) {
        Row(Modifier.fillMaxWidth().clickable { cambia(!acceso) }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icona, fontSize = 20.sp)
            Text(titolo, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BluNotte, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Switch(checked = acceso, onCheckedChange = cambia)
        }
    }
}

/** Scelta tra poche voci, tutte in fila (come un interruttore a più posizioni). */
@Composable
private fun Scelte(voci: List<String>, scelta: Int, cambia: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FondoTenue).padding(3.dp)) {
        voci.forEachIndexed { i, v ->
            val sel = i == scelta
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (sel) Azzurro else Color.Transparent)
                    .clickable { cambia(i) }.padding(vertical = 9.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) { Text(v, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) Color.White else Testo, textAlign = TextAlign.Center, maxLines = 2) }
        }
    }
}

@Composable
private fun Interruttore(titolo: String, spiegazione: String, acceso: Boolean, cambia: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { cambia(!acceso) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titolo, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(spiegazione, fontSize = 12.sp, color = TestoTenue)
        }
        Switch(checked = acceso, onCheckedChange = cambia, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Schermata vuota: cosa fare per cominciare. */
@Composable
private fun Benvenuto() {
    Surface(color = Superficie, shape = MaterialTheme.shapes.large, shadowElevation = 4.dp, modifier = Modifier.entrata()) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ciao! 👋", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BluNotte)
            Text(
                "Tocca «Scegli foto» e seleziona le foto da pubblicare.\n\n" +
                    "Oppure dalla Galleria: seleziona le foto, tocca Condividi e scegli «Da bimbo a bimbo».",
                fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp), color = TestoTenue,
            )
        }
    }
}

/** "Preparo le foto… 3 di 31", con le nuvolette che si riempiono. Compatto: una riga e le nuvolette. */
@Composable
private fun Avanzamento(fatte: Int, tutte: Int) {
    Surface(color = Cielo, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Preparo le foto… ${fatte + 1} di $tutte", fontWeight = FontWeight.Bold, color = BluNotte, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("puoi già pubblicare le pronte", fontSize = 11.sp, color = BluNotte)
            }
            val avanzamento by animateFloatAsState(fatte / tutte.toFloat(), tween(800), label = "avanzamento")
            NuvoleAvanzamento(avanzamento, Modifier.padding(top = 6.dp).padding(horizontal = 24.dp))
        }
    }
}

/** Una foto, semplice: fascia col numero e lo stato, foto, testo per lo stato, "Articoli" e "Pubblica". */
@Composable
private fun Scheda(
    f: Foto,
    modifier: Modifier = Modifier,
    prove: Boolean,
    testo: String,
    ingrandisci: () -> Unit,
    articoli: () -> Unit,
    modificaTesto: () -> Unit,
    pubblica: () -> Unit,
    salvaDiagnosi: () -> Unit,
    giaPubblicati: List<String> = emptyList(),
    giaPrenotati: List<String> = emptyList(),
    zoomAnteprima: ZoomAnteprima = ZoomAnteprima.RESTA,
    togli: () -> Unit,
    comprimi: (() -> Unit)? = null,
) {
    val lista = f.articoli
    // Le schede pubblicate si "spengono" un po': si vede subito cosa resta da fare
    val trasparenza by animateFloatAsState(if (f.pubblicata) 0.72f else 1f, label = "pubblicata")
    Surface(
        color = Superficie,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 2.dp,
        border = BorderStroke(1.5.dp, BordoScheda),
        modifier = modifier.fillMaxWidth().alpha(trasparenza),
    ) {
      Column {
        // Fascia colorata in cima: separa bene una foto dall'altra
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Rosa, Cielo))).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Foto ${f.numero}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = BluNotte)
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = when {
                    f.pubblicata -> 0
                    f.inCorso -> 1
                    f.avvisi.isNotEmpty() -> 2
                    else -> 3
                },
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() },
                label = "stato",
            ) { stato ->
                when (stato) {
                    0 -> Etichetta("✓ Pubblicata", Color.White, Verde)
                    1 -> Etichetta("In lavorazione…", Color.White, Azzurro)
                    2 -> Etichetta("⚠ Da controllare", Color.White, Arancione)
                    else -> Etichetta("Pronta", Color.White, Verde)
                }
            }
            // ▲ per richiudere una foto già pubblicata
            if (comprimi != null) Box(
                Modifier.padding(start = 6.dp).size(30.dp).clip(CircleShape).background(Superficie.copy(alpha = 0.8f)).clickable(onClick = comprimi),
                contentAlignment = Alignment.Center,
            ) { Text("▲", fontSize = 14.sp, color = BluNotte) }
            // ✕ per togliere la foto dalla lista (chiede conferma)
            Box(
                Modifier.padding(start = 6.dp).size(30.dp).clip(CircleShape).background(Superficie.copy(alpha = 0.8f)).clickable(onClick = togli),
                contentAlignment = Alignment.Center,
            ) { Text("✕", fontSize = 16.sp, color = BluNotte, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.padding(10.dp)) {
            // Foto: toccala per ingrandire, girare, pixelare
            Box(
                Modifier.fillMaxWidth().height(270.dp).clip(MaterialTheme.shapes.medium)
                    .background(FondoTenue).clickable(enabled = f.file != null, onClick = ingrandisci),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = f.miniatura, label = "foto") { mini ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            mini != null -> AnteprimaZoomabile(mini, f.file, Modifier.fillMaxSize(), zoomAnteprima)
                            f.inCorso -> Luccichio()
                        }
                    }
                }
                if (f.file != null) Text(
                    "🔍 Tocca", fontSize = 13.sp, color = BluNotte, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                        .background(Superficie.copy(alpha = 0.9f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            when {
                f.inCorso -> {}
                f.errore != null -> Avviso("Errore: ${f.errore}")
                else -> {
                    // Un solo avviso, il più importante
                    val avviso = when {
                        lista.isEmpty() -> "⚠ Cartellino non letto: aggiungi l'etichetta in «Articoli»"
                        f.etichetteViste > lista.size -> "⚠ Ci sono etichette non lette: aggiungile in «Articoli»"
                        lista.any { it.daCompletare } -> "⚠ Un articolo è da completare: apri «Articoli»"
                        f.daControllare && !f.pubblicata -> "⚠ Controlla che la foto sia dritta (tocca la foto)"
                        else -> null
                    }
                    // Prima di tutto: è già stato pubblicato in passato?
                    if (giaPrenotati.isNotEmpty()) Avviso("⛔ Già prenotato: cod. " + giaPrenotati.joinToString(", "))
                    if (giaPubblicati.isNotEmpty()) Avviso("⚠ Già pubblicato: cod. " + giaPubblicati.joinToString(", "))
                    avviso?.let { Avviso(it) }

                    // Il testo per lo stato (toccalo per correggerlo)
                    if (testo.isNotBlank()) {
                        Surface(
                            onClick = modificaTesto,
                            color = FondoTenue,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("testo_${f.numero}"),
                        ) {
                            // Il testo, con una piccola ✏ (toccandolo si corregge)
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(testo, fontSize = 16.sp, color = Testo, modifier = Modifier.weight(1f))
                                Text("✏", fontSize = 14.sp, color = BluNotte, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }

                    // Solo in modalità prove: come ha lavorato l'app
                    if (prove) Text(
                        (if (f.rotazione == 0) "Già dritta" else "Ruotata di ${f.rotazione}°") +
                            (if (f.messaInVerticale) " · messa in verticale" else "") +
                            (if (f.rotazioneManuale != 0) " · girata a mano" else "") +
                            " · ${f.metodo} · ${"%.1f".format(f.secondi)} s",
                        fontSize = 12.sp, color = TestoTenue, modifier = Modifier.padding(top = 6.dp),
                    )

                    // Due pulsanti, niente di più
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulsanteChiaro("Articoli (${lista.size})", "📋", articoli, Modifier.weight(1f), altezza = 46.dp, grandezzaTesto = 15)
                        PulsanteGrande(if (f.pubblicata) "Di nuovo" else "Pubblica", if (f.pubblicata) "↺" else "📤", pubblica, Modifier.weight(1f), altezza = 46.dp, grandezzaTesto = 15)
                    }
                }
            }
            if (prove && f.diario != null && !f.diario.vuoto) {
                OutlinedButton(onClick = salvaDiagnosi, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("📷 Salva diagnosi in Galleria") }
            }
        }
      }
    }
}

/** Gli articoli di una foto, in una schermata a parte: modifica, aggiungi. */
@Composable
private fun SchermataArticoli(f: Foto, modifica: (Int?) -> Unit, chiudi: () -> Unit) {
    val lista = f.articoli
    Finestra(onDismissRequest = chiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(SfondoLista).systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Rosa, Cielo))).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Articoli · Foto ${f.numero}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = BluNotte, modifier = Modifier.weight(1f))
                TextButton(onClick = chiudi) { Text("✕ Chiudi", fontSize = 16.sp, color = BluNotte) }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (lista.isEmpty()) item {
                    Avviso("Nessun articolo letto: aggiungilo tu con il pulsante qui sotto.")
                }
                val mancanti = f.etichetteViste - lista.size
                if (mancanti > 0) item {
                    Avviso(if (mancanti == 1) "⚠ C'è ancora 1 etichetta non letta" else "⚠ Ci sono ancora $mancanti etichette non lette")
                }
                items(lista.size) { i ->
                    val d = lista[i]
                    Surface(color = Superficie, shape = MaterialTheme.shapes.medium, shadowElevation = 3.dp, border = BorderStroke(1.dp, BordoScheda)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (lista.size > 1) "Articolo ${i + 1}" else "Articolo",
                                    fontWeight = FontWeight.Bold, color = BluNotte, fontSize = 17.sp, modifier = Modifier.weight(1f),
                                )
                                FilledTonalButton(
                                    onClick = { modifica(i) },
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Cielo, contentColor = BluNotte),
                                ) { Text("✏ Modifica") }
                            }
                            if (d.daCompletare) Avviso("⚠ Da completare: tocca Modifica e guarda la foto")
                            DatiLetti(d)
                        }
                    }
                }
            }
            PulsanteGrande("Aggiungi etichetta", "＋", { modifica(null) }, Modifier.fillMaxWidth().padding(16.dp), altezza = 58.dp, grandezzaTesto = 18)
        }
    }
}

/** Pastiglia colorata con una scritta corta (es. "Foto 3", "✓ Pubblicata"). */
@Composable
private fun Etichetta(testo: String, sfondo: Color, colore: Color) {
    Text(
        testo, color = colore, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        modifier = Modifier.background(sfondo, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Elenco delle sigle: cerca, aggiungi, correggi, elimina. Si salva da solo nel file di testo. */
@Composable
private fun SchermataDizionario(voci: List<VoceDizionario>, salva: (String?, VoceDizionario?) -> Unit, chiudi: () -> Unit) {
    var cerca by remember { mutableStateOf("") }
    var inModifica by remember { mutableStateOf<VoceDizionario?>(null) }
    var nuova by remember { mutableStateOf(false) }
    val filtrate = voci.filter { cerca.isBlank() || it.sigla.contains(cerca.lowercase()) || it.significatoTesto.contains(cerca, ignoreCase = true) }

    val inScheda = nuova || inModifica != null
    fun chiudiScheda() { nuova = false; inModifica = null }

    // Una finestra sola: la scheda della sigla prende il posto dell'elenco (niente finestre una sopra l'altra)
    Finestra(
        onDismissRequest = { if (inScheda) chiudiScheda() else chiudi() },   // Indietro: prima chiude la scheda
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().imePadding().padding(16.dp)) {
            if (inScheda) {
                // key: ogni sigla aperta riparte con i suoi valori
                key(inModifica?.sigla) {
                    ModificaSigla(
                        iniziale = inModifica,
                        salva = { v -> salva(inModifica?.sigla, v); chiudiScheda() },
                        annulla = ::chiudiScheda,
                    )
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📖 Dizionario (${voci.size} sigle)", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = chiudi) { Text("✕ Chiudi", fontSize = 16.sp) }
            }
            OutlinedTextField(cerca, { cerca = it }, label = { Text("Cerca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { nuova = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("+ Nuova sigla") }
            LazyColumn(Modifier.weight(1f)) {
                items(filtrate, key = { it.sigla }) { v ->
                    Row(
                        Modifier.fillMaxWidth().clickable { inModifica = v }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(v.sigla.uppercase(), fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                        Text(
                            if (v.daTogliere) "(tolto dal testo)" else v.significatoTesto,
                            color = if (v.daTogliere) Arancione else Color.Unspecified,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Scheda di una sigla: sigla, significato (con le forme separate da "/"), oppure "togli dal testo". */
@Composable
private fun ModificaSigla(iniziale: VoceDizionario?, salva: (VoceDizionario?) -> Unit, annulla: () -> Unit) {
    var sigla by remember { mutableStateOf(iniziale?.sigla.orEmpty()) }
    var significato by remember { mutableStateOf(iniziale?.significatoTesto.orEmpty()) }
    var togli by remember { mutableStateOf(iniziale?.daTogliere ?: false) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (iniziale == null) "Nuova sigla" else "Modifica sigla", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        OutlinedTextField(sigla, { sigla = it.trim() }, label = { Text("Sigla (es. gri)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (!togli) {
            OutlinedTextField(significato, { significato = it }, label = { Text("Significato") }, modifier = Modifier.fillMaxWidth())
            Text(
                "Se cambia con maschile/femminile scrivi le 4 forme:\ngrigio / grigia / grigi / grigie\n" +
                    "Se cambia solo col plurale: verde / verdi",
                fontSize = 12.sp, color = TestoTenue,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = togli, onCheckedChange = { togli = it })
            Text("Togli dal testo (es. marchio da non scrivere)")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (iniziale != null) TextButton(onClick = { salva(null) }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = annulla) { Text("Annulla") }
            Button(
                enabled = sigla.isNotBlank() && (togli || significato.isNotBlank()),
                onClick = {
                    val forme = if (togli) emptyList() else significato.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                    salva(VoceDizionario(sigla.lowercase(), forme))
                },
            ) { Text("Salva") }
        }
    }
}

/** Scritta arancione: qui Elisa deve guardare. */
@Composable
private fun Avviso(testo: String) = Text(
    testo, color = Arancione, fontWeight = FontWeight.Bold, fontSize = 14.sp,
    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).background(FondoAvviso, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
)


/** I dati di UN articolo, così come sono letti dal cartellino (le sigle le espande il pezzo 3). */
@Composable
private fun DatiLetti(dati: DatiCartellino) {
    val mancante = MaterialTheme.colorScheme.error
    @Composable
    fun Campo(nome: String, valore: String?, obbligatorio: Boolean = true) = Text(
        "$nome: ${valore ?: "—"}",
        color = if (valore == null && obbligatorio) mancante else Color.Unspecified,
        fontSize = 15.sp,
    )
    Campo("Codice", dati.codice)
    Campo("Descrizione", dati.descrizione)
    Campo("Prezzo", dati.prezzo)
    Campo("Taglia", dati.taglia, obbligatorio = false)   // i giochi non ce l'hanno
}

/**
 * Foto a schermo intero: si ingrandisce con le dita.
 * In basso: Sinistra, Destra, Capovolgi, Testo e la sezione Pixel (automatico dello sfondo e a mano),
 * ognuno col suo "↺ Togli" per tornare indietro.
 */
@Composable
private fun Visore(
    f: Foto,
    sfondoInCorso: Boolean,
    gira: (Int) -> Unit,
    pixelaSfondo: () -> Unit,
    togliSfondo: () -> Unit,
    salvaPixel: (Set<Long>, Int, Set<Long>) -> Unit,
    togliPixelMano: () -> Unit,
    salvaRitaglio: (Riquadro?) -> Unit,
    testo: () -> Unit,
    chiudi: () -> Unit,
) {
    // La foto caricata, col suo file e la rotazione che ha dentro (dopo "Gira" per un attimo c'è ancora quella vecchia)
    val caricata by produceState<Triple<File, ImageBitmap, Int>?>(null, f.file) {
        val file = f.file
        val rot = f.rotazioneFile
        if (file != null) withContext(Dispatchers.IO) { caricaRidotta(file, 2048) }?.let { value = Triple(file, it, rot) }
    }
    val immagine = caricata?.second
    // Quella giusta per il pixel a mano: file e rotazione aggiornati
    val aggiornata = caricata?.takeIf { it.first == f.file && it.third == f.rotazioneManuale }?.second
    // Grandezza della foto di base (dove si salvano i quadretti a mano)
    val misureBase = remember(f.fileAuto) { f.fileAuto?.let(::misureFoto) }
    var pennello by remember { mutableStateOf<Pennello?>(null) }   // editor aperto, con questo pennello
    var ritaglia by remember { mutableStateOf(false) }              // editor del ritaglio aperto
    Finestra(
        onDismissRequest = { if (pennello != null) pennello = null else if (ritaglia) ritaglia = false else chiudi() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        pennello != null -> "Passa il dito sulla foto"
                        ritaglia -> "Trascina gli angoli o sposta il riquadro"
                        else -> "Foto ${f.numero} · due dita per ingrandire"
                    },
                    color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f),
                )
                if (pennello == null && !ritaglia) PulsanteTondo("✕", chiudi)
            }
            val img = immagine
            val modo = pennello
            if (ritaglia && misureBase != null) {
                EditorRitaglio(
                    f, Modifier.weight(1f),
                    salva = { q -> salvaRitaglio(q); ritaglia = false },
                    esci = { ritaglia = false },
                )
            } else if (modo != null && aggiornata != null && misureBase != null) {
                EditorPixel(
                    f, aggiornata, misureBase.first, misureBase.second, modo, Modifier.weight(1f),
                    salva = { celle, lato, ripristinate -> salvaPixel(celle, lato, ripristinate); pennello = null },
                    esci = { pennello = null },
                )
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (img != null) {
                        // Rotazione subito a schermo (la foto vera si ricompone in sottofondo)
                        val delta = ((f.rotazioneManuale - (caricata?.third ?: 0)) % 360 + 360) % 360
                        key(caricata?.first) { FotoGirata(img, delta) { FotoZoomabile(img, Modifier.fillMaxSize(), caricata?.first) } }
                    }
                    if (sfondoInCorso) Surface(color = Superficie, shape = RoundedCornerShape(50), shadowElevation = 4.dp) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp, color = Azzurro)
                            Spacer(Modifier.width(10.dp))
                            Text("Pixelo lo sfondo…", color = BluNotte, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Surface(color = Superficie, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PulsanteVisore("↺", "Sinistra", Modifier.weight(1f)) { gira(270) }
                            PulsanteVisore("↻", "Destra", Modifier.weight(1f)) { gira(90) }
                            PulsanteVisore("⇅", "Capovolgi", Modifier.weight(1f)) { gira(180) }
                            PulsanteVisore("✏", "Testo", Modifier.weight(1f), azione = testo)
                        }
                        // Pixel (due strade) e ritaglio, ognuno col suo "torna indietro"
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SezionePixel(
                                "✨", "Sfondo automatico", Modifier.weight(1f),
                                applicato = f.sfondoPixelato, attivo = !sfondoInCorso && aggiornata != null,
                                applica = pixelaSfondo, togli = togliSfondo,
                                extra = if (f.sfondoPixelato) ({ pennello = Pennello.ORIGINALE }) else null,
                            )
                            SezionePixel(
                                "▦", "Pixel a mano", Modifier.weight(1f),
                                applicato = f.pixelManuale.isNotEmpty(), attivo = !sfondoInCorso && aggiornata != null,
                                applica = { pennello = Pennello.PIXEL }, togli = togliPixelMano,
                                applicaAncora = true,   // a mano si può sempre aggiungere
                            )
                            SezionePixel(
                                "✂", "Ritaglia", Modifier.weight(1f),
                                applicato = f.ritaglio != null, attivo = !sfondoInCorso && aggiornata != null,
                                applica = { ritaglia = true }, togli = { salvaRitaglio(null) },
                                applicaAncora = true,   // il ritaglio si può sempre cambiare
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Mostra subito la foto girata di [gradi] (con un giro morbido), ridimensionata per stare nel riquadro. */
@Composable
private fun FotoGirata(img: ImageBitmap, gradi: Int, contenuto: @Composable () -> Unit) {
    // Angolo "cumulato", così il giro va sempre dalla parte più corta
    var cumulato by remember { mutableFloatStateOf(gradi.toFloat()) }
    var ultimo by remember { mutableIntStateOf(gradi) }
    if (gradi != ultimo) {
        cumulato += (((gradi - ultimo) % 360 + 540) % 360 - 180).toFloat()
        ultimo = gradi
    }
    val angolo by animateFloatAsState(cumulato, tween(220), label = "gira")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bw = constraints.maxWidth.toFloat(); val bh = constraints.maxHeight.toFloat()
        val fit = minOf(bw / img.width, bh / img.height)
        val dw = img.width * fit; val dh = img.height * fit
        // Girata di un quarto: larghezza e altezza si scambiano
        val k = if (gradi % 180 != 0) minOf(bw / dh, bh / dw) else 1f
        val scala by animateFloatAsState(k, tween(220), label = "scala")
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angolo; scaleX = scala; scaleY = scala }) { contenuto() }
    }
}

/** I due pennelli dell'editor. */
private enum class Pennello { PIXEL, ORIGINALE }

/**
 * Un tipo di pixel nel visore: il pulsante per farlo e, se è già fatto, "↺ Togli" per tornare indietro.
 * [applicaAncora]: il pulsante resta attivo anche dopo (pixel a mano: si aggiungono altri quadretti).
 * [extra]: pulsante in più (sfondo pixelato: "Rimetti originale" col pennello).
 */
@Composable
private fun SezionePixel(
    simbolo: String, scritta: String, modifier: Modifier,
    applicato: Boolean, attivo: Boolean,
    applica: () -> Unit, togli: () -> Unit,
    applicaAncora: Boolean = false,
    extra: (() -> Unit)? = null,
) {
    Surface(color = FondoTenue, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, BordoScheda), modifier = modifier) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PulsanteVisore(
                if (applicato && !applicaAncora) "✓" else simbolo,
                if (applicato && !applicaAncora) "Fatto" else scritta,
                Modifier.fillMaxWidth(),
                attivo = attivo && (applicaAncora || !applicato),
                colore = if (applicato) Verde.copy(alpha = 0.18f) else Cielo,
                azione = applica,
            )
            AnimatedVisibility(extra != null) {
                PulsanteChiaro("🖌 Originale", "", { extra?.invoke() }, Modifier.fillMaxWidth(), colore = Azzurro, altezza = 44.dp, grandezzaTesto = 13, attivo = attivo)
            }
            AnimatedVisibility(applicato) {
                PulsanteChiaro("↺ Togli", "", togli, Modifier.fillMaxWidth(), colore = Arancione, altezza = 44.dp, grandezzaTesto = 14)
            }
        }
    }
}

/** Pulsante quadrato del visore: simbolo grande e scritta sotto. */
@Composable
private fun PulsanteVisore(
    simbolo: String, scritta: String, modifier: Modifier = Modifier,
    attivo: Boolean = true, colore: Color = Cielo, azione: () -> Unit,
) {
    val (sorgente, morbido) = rimbalzo()
    Surface(
        onClick = azione,
        enabled = attivo,
        shape = RoundedCornerShape(20.dp),
        color = colore,
        interactionSource = sorgente,
        modifier = modifier.heightIn(min = 68.dp).then(morbido).alpha(if (attivo) 1f else 0.45f),
    ) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(simbolo, fontSize = 22.sp, color = BluNotte)
            Text(scritta, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BluNotte, textAlign = TextAlign.Center)
        }
    }
}

/** Un passo del pennello: quadretti aggiunti, e quelli "a mano" tolti dal pennello Originale (per poterlo annullare). */
private class Passo(val pennello: Pennello, val aggiunte: MutableSet<Long> = mutableSetOf(), val tolteMano: MutableSet<Long> = mutableSetOf())

/**
 * Editor coi due pennelli:
 * - ▦ Pixela: quadrettoni sulle parti da nascondere;
 * - ✨ Originale: dove lo sfondo automatico ha pixelato troppo, rimette la foto vera.
 * "Annulla" toglie l'ultimo passo, uno alla volta. I quadretti si salvano sulla foto di base ([wBase]×[hBase]).
 */
@Composable
private fun EditorPixel(
    f: Foto, immagine: ImageBitmap, wBase: Int, hBase: Int, iniziale: Pennello,
    modifier: Modifier, salva: (Set<Long>, Int, Set<Long>) -> Unit, esci: () -> Unit,
) {
    val rotazione = f.rotazioneManuale
    val lato = remember(wBase, hBase) { f.latoPixel.takeIf { it > 0 } ?: PixelManuale.lato(wBase, hBase) }
    val wVista = if (rotazione % 180 == 0) wBase else hBase
    val hVista = if (rotazione % 180 == 0) hBase else wBase
    // Sotto: lo sfondo pixelato (se c'è) o la foto vera; il pennello Originale mostra la foto vera
    val fondo by produceState<ImageBitmap?>(null, f.fileSfondo, f.sfondoPixelato, rotazione) {
        val file = if (f.sfondoPixelato) f.fileSfondo else f.fileAuto
        value = file?.let { withContext(Dispatchers.IO) { caricaRuotata(it, rotazione) } }
    }
    val originale by produceState<ImageBitmap?>(null, f.fileAuto, rotazione) {
        value = f.fileAuto?.let { withContext(Dispatchers.IO) { caricaRuotata(it, rotazione) } }
    }
    // Colori dei quadrettoni: la foto rimpicciolita, circa un pixel per quadretto
    val piccola = remember(fondo) {
        val img = fondo ?: immagine
        android.graphics.Bitmap.createScaledBitmap(img.asAndroidBitmap(), maxOf(1, (wVista + lato - 1) / lato), maxOf(1, (hVista + lato - 1) / lato), true)
    }
    val mano = remember { mutableStateListOf<Long>().apply { addAll(f.pixelManuale) } }
    val rimesse = remember { mutableStateListOf<Long>().apply { addAll(f.ripristinate) } }
    val passi = remember { mutableStateListOf<Passo>() }
    var modo by remember { mutableStateOf(iniziale) }
    var grande by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pxW = constraints.maxWidth.toFloat(); val pxH = constraints.maxHeight.toFloat()
            val scala = minOf(pxW / wVista, pxH / hVista)
            val ox = (pxW - wVista * scala) / 2; val oy = (pxH - hVista * scala) / 2
            fun dipingi(p: Offset) {
                val passo = passi.lastOrNull() ?: return
                val raggio = lato * (if (grande) 2.4f else 1.2f)
                val (bx, by) = PixelManuale.versoBase((p.x - ox) / scala, (p.y - oy) / scala, rotazione, wBase, hBase)
                for (c in PixelManuale.celleAttorno(bx, by, raggio, lato, wBase, hBase)) {
                    when (passo.pennello) {
                        Pennello.PIXEL -> if (c !in mano) { mano += c; passo.aggiunte += c }
                        Pennello.ORIGINALE -> {
                            if (f.sfondoPixelato && c !in rimesse) { rimesse += c; passo.aggiunte += c }
                            if (mano.remove(c)) passo.tolteMano += c   // via anche il pixel a mano lì
                        }
                    }
                }
            }
            Image(fondo ?: immagine, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Canvas(
                Modifier.fillMaxSize().testTag("tela")
                    .pointerInput(grande, modo) {
                        detectTapGestures(onPress = { passi += Passo(modo); dipingi(it) })
                    }
                    .pointerInput(grande, modo) {
                        // il passo è già aperto da onPress
                        detectDragGestures(onDragStart = { dipingi(it) }) { cambio, _ -> dipingi(cambio.position) }
                    }
            ) {
                fun rettangolo(c: Long): Pair<Offset, Offset> {
                    val x0 = PixelManuale.colonna(c) * lato.toFloat(); val y0 = PixelManuale.riga(c) * lato.toFloat()
                    val x1 = minOf(wBase.toFloat(), x0 + lato); val y1 = minOf(hBase.toFloat(), y0 + lato)
                    val (ax, ay) = PixelManuale.daBase(x0, y0, rotazione, wBase, hBase)
                    val (bx, by) = PixelManuale.daBase(x1, y1, rotazione, wBase, hBase)
                    return Offset(minOf(ax, bx), minOf(ay, by)) to Offset(maxOf(ax, bx), maxOf(ay, by))
                }
                // Pennello Originale: la foto vera in quei quadretti
                originale?.let { o ->
                    val kx = o.width / wVista.toFloat(); val ky = o.height / hVista.toFloat()
                    for (c in rimesse) {
                        val (a, b) = rettangolo(c)
                        drawImage(
                            o,
                            srcOffset = androidx.compose.ui.unit.IntOffset((a.x * kx).toInt(), (a.y * ky).toInt()),
                            srcSize = androidx.compose.ui.unit.IntSize(maxOf(1, ((b.x - a.x) * kx).toInt()), maxOf(1, ((b.y - a.y) * ky).toInt())),
                            dstOffset = androidx.compose.ui.unit.IntOffset((ox + a.x * scala).toInt(), (oy + a.y * scala).toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(((b.x - a.x) * scala).toInt() + 1, ((b.y - a.y) * scala).toInt() + 1),
                        )
                    }
                }
                // Pixel a mano: quadrettoni del loro colore medio
                for (c in mano) {
                    val (a, b) = rettangolo(c)
                    val px = ((a.x + b.x) / 2 / lato).toInt().coerceIn(0, piccola.width - 1)
                    val py = ((a.y + b.y) / 2 / lato).toInt().coerceIn(0, piccola.height - 1)
                    drawRect(
                        Color(piccola.getPixel(px, py)),
                        Offset(ox + a.x * scala, oy + a.y * scala),
                        androidx.compose.ui.geometry.Size((b.x - a.x) * scala + 1, (b.y - a.y) * scala + 1),
                    )
                }
            }
        }
        Surface(color = Superficie, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = modo == Pennello.PIXEL, onClick = { modo = Pennello.PIXEL }, label = { Text("▦ Pixela") })
                    FilterChip(selected = modo == Pennello.ORIGINALE, onClick = { modo = Pennello.ORIGINALE }, label = { Text("✨ Originale") })
                    Spacer(Modifier.weight(1f))
                    FilterChip(selected = grande, onClick = { grande = !grande }, label = { Text(if (grande) "⬤ Grande" else "● Piccolo") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PulsanteChiaro("Esci", "✕", esci, Modifier.weight(1f), altezza = 50.dp, grandezzaTesto = 14)
                    PulsanteChiaro(
                        "Annulla", "↶",
                        {
                            // Annulla l'ultimo passo, qualunque pennello fosse
                            passi.removeLastOrNull()?.let { p ->
                                when (p.pennello) {
                                    Pennello.PIXEL -> mano.removeAll(p.aggiunte)
                                    Pennello.ORIGINALE -> { rimesse.removeAll(p.aggiunte); mano.addAll(p.tolteMano) }
                                }
                            }
                        },
                        Modifier.weight(1f), attivo = passi.isNotEmpty(), altezza = 50.dp, grandezzaTesto = 14,
                    )
                    PulsanteGrande(
                        "Salva", "✓", { salva(mano.toSet(), lato, rimesse.toSet()) }, Modifier.weight(1f),
                        // Si salva se qualcosa è davvero cambiato rispetto a prima
                        attivo = mano.toSet() != f.pixelManuale || rimesse.toSet() != f.ripristinate, colore = Verde, altezza = 50.dp, grandezzaTesto = 14,
                    )
                }
            }
        }
    }
}

/**
 * Editor del ritaglio: un riquadro sopra la foto (fuori è scurito).
 * Si trascinano gli angoli per allargarlo/stringerlo, o il centro per spostarlo.
 * Formati pronti (1:1, 4:5, 3:4, 9:16) o libero. Si salva sulla foto di base, in proporzione.
 */
@Composable
private fun EditorRitaglio(f: Foto, modifier: Modifier, salva: (Riquadro?) -> Unit, esci: () -> Unit) {
    val rotazione = f.rotazioneManuale
    // Sotto: la foto intera (senza ritaglio), girata come la vede Elisa
    val img by produceState<ImageBitmap?>(null, f.fileSfondo, f.sfondoPixelato, rotazione) {
        val file = if (f.sfondoPixelato) f.fileSfondo else f.fileAuto
        value = file?.let { withContext(Dispatchers.IO) { caricaRuotata(it, rotazione) } }
    }
    val immagine = img
    Column(modifier.fillMaxWidth()) {
        if (immagine == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Azzurro) }
            return@Column
        }
        val iw = immagine.width.toFloat(); val ih = immagine.height.toFloat()
        // Il riquadro, in pixel della foto mostrata
        val iniziale = remember(immagine) {
            f.ritaglio?.let { Ritaglio.daBase(it, rotazione) }?.let { Riquadro(it.l * iw, it.t * ih, it.r * iw, it.b * ih) } ?: Riquadro(0f, 0f, iw, ih)
        }
        var q by remember(immagine) { mutableStateOf(iniziale) }
        var formato by remember { mutableIntStateOf(0) }
        val rapporto = Ritaglio.FORMATI[formato].rapporto
        val bordo = Color.White
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pxW = constraints.maxWidth.toFloat(); val pxH = constraints.maxHeight.toFloat()
            val scala = minOf(pxW / iw, pxH / ih)
            val ox = (pxW - iw * scala) / 2; val oy = (pxH - ih * scala) / 2
            val tolleranza = with(LocalDensity.current) { 36.dp.toPx() }
            Image(immagine, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Canvas(
                Modifier.fillMaxSize().testTag("ritaglio_tela")
                    .pointerInput(scala, rapporto) {
                        var presa: Maniglia? = null
                        detectDragGestures(
                            onDragStart = { p -> presa = Ritaglio.maniglia(q, (p.x - ox) / scala, (p.y - oy) / scala, tolleranza / scala) },
                            onDragEnd = { presa = null },
                        ) { cambio, d ->
                            val m = presa ?: return@detectDragGestures
                            cambio.consume()
                            q = Ritaglio.trascina(q, m, d.x / scala, d.y / scala, iw, ih, rapporto, minOf(iw, ih) * 0.08f)
                        }
                    }
            ) {
                val a = Offset(ox + q.l * scala, oy + q.t * scala)
                val b = Offset(ox + q.r * scala, oy + q.b * scala)
                val scuro = Color(0x99000000)
                // Fuori dal riquadro: scurito
                drawRect(scuro, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, a.y))
                drawRect(scuro, Offset(0f, b.y), androidx.compose.ui.geometry.Size(size.width, size.height - b.y))
                drawRect(scuro, Offset(0f, a.y), androidx.compose.ui.geometry.Size(a.x, b.y - a.y))
                drawRect(scuro, Offset(b.x, a.y), androidx.compose.ui.geometry.Size(size.width - b.x, b.y - a.y))
                // Bordo e linee dei terzi
                drawRect(bordo, a, androidx.compose.ui.geometry.Size(b.x - a.x, b.y - a.y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                for (k in 1..2) {
                    val x = a.x + (b.x - a.x) * k / 3; val y = a.y + (b.y - a.y) * k / 3
                    drawLine(bordo.copy(alpha = 0.5f), Offset(x, a.y), Offset(x, b.y), 1.dp.toPx())
                    drawLine(bordo.copy(alpha = 0.5f), Offset(a.x, y), Offset(b.x, y), 1.dp.toPx())
                }
                // Le 4 maniglie agli angoli
                for (p in listOf(a, Offset(b.x, a.y), Offset(a.x, b.y), b)) drawCircle(bordo, 9.dp.toPx(), p)
            }
        }
        Surface(color = Superficie, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ritaglio.FORMATI.forEachIndexed { i, fm ->
                        FilterChip(
                            selected = formato == i,
                            onClick = { formato = i; if (fm.rapporto != null) q = Ritaglio.formato(fm.rapporto, iw, ih) },
                            label = { Text(fm.nome) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PulsanteChiaro("Esci", "✕", esci, Modifier.weight(1f), altezza = 50.dp, grandezzaTesto = 14)
                    PulsanteChiaro(
                        "Tutta", "⛶", { formato = 0; q = Riquadro(0f, 0f, iw, ih) },
                        Modifier.weight(1f), attivo = q != Riquadro(0f, 0f, iw, ih), altezza = 50.dp, grandezzaTesto = 14,
                    )
                    PulsanteGrande(
                        "Salva", "✓",
                        { salva(Ritaglio.versoBase(Riquadro(q.l / iw, q.t / ih, q.r / iw, q.b / ih), rotazione)) },
                        Modifier.weight(1f), attivo = q != iniziale, colore = Verde, altezza = 50.dp, grandezzaTesto = 14,
                    )
                }
            }
        }
    }
}

/** Immagine che si ingrandisce con due dita (fino a 8 volte) e si sposta col dito. Doppio tocco: zoom avanti/indietro. */
@Composable
private fun FotoZoomabile(immagine: ImageBitmap, modifier: Modifier, file: File? = null) {
    var scala by remember { mutableFloatStateOf(1f) }
    var spostamento by remember { mutableStateOf(Offset.Zero) }
    var riquadro by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    // Ingrandendo, il pezzo inquadrato arriva nitido dalla foto originale
    val nitidezza = ricordaNitidezza(file)
    val vera = misureVere(file) ?: androidx.compose.ui.unit.IntSize(immagine.width, immagine.height)
    val scope = rememberCoroutineScope()
    LaunchedEffect(scala, spostamento, riquadro) { nitidezza.aggiorna(scala, spostamento, riquadro) }
    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { riquadro = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { scope.launch { nitidezza.prepara() } },   // apro la foto originale già al primo tocco
                    onDoubleTap = { punto ->
                        if (scala > 1f) {
                            scala = 1f; spostamento = Offset.Zero
                        } else {
                            // Porto il punto toccato al centro, ingrandito 3 volte
                            val centro = Offset(size.width / 2f, size.height / 2f)
                            scala = 3f; spostamento = (centro - punto) * 3f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, sposta, zoom, _ ->
                    scala = (scala * zoom).coerceIn(1f, 8f)
                    spostamento = if (scala == 1f) Offset.Zero else spostamento + sposta
                }
            }
    ) {
        disegnaZoom(immagine, vera, scala, spostamento)
        if (scala > 1f) nitidezza.pezzo?.let { disegnaZoom(it.immagine, vera, scala, spostamento, it.zona) }
    }
}

/**
 * Schermo per scrivere o correggere a mano un articolo:
 * sopra la foto (si ingrandisce con le dita mentre si scrive), sotto i campi.
 */
@Composable
private fun ModificaArticolo(
    foto: File?,
    iniziale: DatiCartellino?,
    nuovo: Boolean,
    salva: (DatiCartellino?) -> Unit,
    annulla: () -> Unit,
) {
    var codice by remember { mutableStateOf(iniziale?.codice.orEmpty()) }
    var descrizione by remember { mutableStateOf(iniziale?.descrizione.orEmpty()) }
    var prezzo by remember { mutableStateOf(iniziale?.prezzo?.removePrefix("€")?.trim().orEmpty()) }
    var taglia by remember { mutableStateOf(iniziale?.taglia.orEmpty()) }
    var rigaVeloce by remember { mutableStateOf("") }
    val immagine by fotoPerSchermo(foto)

    Finestra(onDismissRequest = annulla, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().imePadding()) {
            // Sopra: la foto, ingrandibile (due dita / doppio tocco)
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                immagine?.let { FotoZoomabile(it, Modifier.fillMaxSize(), foto) }
                Text(
                    "Due dita per ingrandire",
                    color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopCenter).background(Color(0x88000000)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            // Sotto: i campi
            Column(
                Modifier.weight(1.3f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (nuovo) "Nuovo articolo" else "Modifica articolo", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                // Tutto in una riga (anche dettato col microfono della tastiera): l'app riempie i campi sotto
                OutlinedTextField(
                    rigaVeloce,
                    { testo ->
                        rigaVeloce = testo
                        val d = RigaVeloce.analizza(testo)
                        codice = d.codice.orEmpty()
                        descrizione = d.descrizione.orEmpty()
                        prezzo = d.prezzo?.removePrefix("€")?.trim().orEmpty()
                        taglia = d.taglia.orEmpty()
                    },
                    label = { Text("Scrivi tutto in una riga") },
                    placeholder = { Text("es. 1444115 librottino inside out 1,50") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("…oppure riempi o correggi i campi:", fontSize = 13.sp, color = TestoTenue)
                OutlinedTextField(codice, { codice = it.filter(Char::isDigit).take(7) }, label = { Text("Codice") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(descrizione, { descrizione = it }, label = { Text("Descrizione") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(prezzo, { prezzo = it }, label = { Text("Prezzo") }, prefix = { Text("€ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(taglia, { taglia = it }, label = { Text("Taglia") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!nuovo) TextButton(onClick = { salva(null) }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = annulla) { Text("Annulla") }
                    Button(onClick = {
                        salva(
                            DatiCartellino(
                                codice = codice.ifBlank { null },
                                descrizione = descrizione.trim().ifBlank { null },
                                prezzo = prezzoInFormato(prezzo),
                                taglia = taglia.trim().ifBlank { null },
                            )
                        )
                    }) { Text("Salva") }
                }
            }
        }
    }
}

/** Schermo per correggere il testo per lo stato: sopra la foto ingrandibile, sotto il testo. */
@Composable
private fun ModificaTesto(foto: File?, iniziale: String, manuale: Boolean, salva: (String?) -> Unit, annulla: () -> Unit) {
    var testo by remember { mutableStateOf(iniziale) }
    val immagine by fotoPerSchermo(foto)
    Finestra(onDismissRequest = annulla, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                immagine?.let { FotoZoomabile(it, Modifier.fillMaxSize(), foto) }
                Text(
                    "Due dita per ingrandire",
                    color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopCenter).background(Color(0x88000000)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Testo per lo stato", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                OutlinedTextField(
                    testo, { testo = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (manuale) TextButton(onClick = { salva(null) }) { Text("↺ Automatico") }
                    TextButton(onClick = annulla) { Text("Annulla") }
                    Button(onClick = { salva(testo.takeIf { it != iniziale || manuale }) }) { Text("Salva") }
                }
            }
        }
    }
}

/**
 * Foto per lo schermo: caricata in sottofondo (lo schermo non si blocca) e rimpicciolita
 * a circa 2000 px, che bastano anche ingrandendo e pesano molto meno in memoria.
 */
@Composable
private fun fotoPerSchermo(file: File?): State<ImageBitmap?> = produceState<ImageBitmap?>(null, file) {
    value = file?.let { withContext(Dispatchers.IO) { caricaRidotta(it, 2048) } }
}

private fun misureFoto(file: File): Pair<Int, Int> {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, o)
    return o.outWidth to o.outHeight
}

/** La foto [file] rimpicciolita (~2000 px) e girata di [gradi]. */
private fun caricaRuotata(file: File, gradi: Int): ImageBitmap? {
    val (w, h) = misureFoto(file)
    var campione = 1
    while (maxOf(w, h) / (campione * 2) >= 2048) campione *= 2
    val b = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = campione }) ?: return null
    return Raddrizzatore.ruotaImmagine(b, gradi).asImageBitmap()
}

internal fun caricaRidotta(file: File, lato: Int): ImageBitmap? {
    val (w, h) = misureFoto(file)
    var campione = 1
    while (maxOf(w, h) / (campione * 2) >= lato) campione *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = campione })?.asImageBitmap()
}

/** "2,5" → "€ 2,50"; "3" → "€ 3,00"; vuoto → null. */
private fun prezzoInFormato(testo: String): String? {
    val pulito = testo.replace("€", "").replace(" ", "").replace('.', ',').ifBlank { return null }
    val parti = pulito.split(",")
    val euro = parti[0].ifBlank { "0" }
    val centesimi = parti.getOrNull(1).orEmpty().padEnd(2, '0').take(2)
    return "€ $euro,$centesimi"
}
