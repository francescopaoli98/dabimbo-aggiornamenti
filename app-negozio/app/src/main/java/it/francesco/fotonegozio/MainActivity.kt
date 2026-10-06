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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
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
private fun Finestra(onDismissRequest: () -> Unit, properties: DialogProperties, contenuto: @Composable () -> Unit) {
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
    var ingrandita by remember { mutableStateOf<Int?>(null) }   // numero della foto aperta a schermo intero
    var inModifica by remember { mutableStateOf<Pair<Int, Int?>?>(null) }   // (foto, articolo) - articolo null = nuovo
    var dizionarioAperto by remember { mutableStateOf(false) }
    var testoInModifica by remember { mutableStateOf<Int?>(null) }   // numero della foto di cui si corregge il testo
    var articoliAperti by remember { mutableStateOf<Int?>(null) }    // numero della foto di cui si guardano gli articoli
    var daConfermare by remember { mutableStateOf<Int?>(null) }      // foto con avvisi: chiedo prima di pubblicare
    var daTogliere by remember { mutableStateOf<Int?>(null) }        // foto da togliere dalla lista (chiedo conferma)
    var impostazioniAperte by remember { mutableStateOf(false) }
    var riepilogoAperto by remember { mutableStateOf(false) }
    var chiediMenu by remember { mutableStateOf(false) }               // ✕: tornare al menu principale (chiedo se mancano foto)
    val riaperte = remember { mutableStateListOf<Int>() }              // foto pubblicate riaperte (se le schede si comprimono)
    val lista = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
        val file = f.file ?: return
        vibra.performHapticFeedback(HapticFeedbackType.LongPress)
        if (Condivisione.pubblica(context, file, vm.testo(f))) vm.segnaPubblicata(f.numero)
        else vm.messaggio = "WhatsApp non trovato sul telefono"
    }
    fun chiediEPubblica(f: Foto) {
        if (f.avvisi.isEmpty()) pubblica(f) else daConfermare = f.numero
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // In alto: ✕ torna al menu principale (solo con delle foto), ⚙ impostazioni (sempre)
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (vm.foto.isNotEmpty()) PulsanteTondo("✕", {
                        if (vm.foto.all { it.pubblicata }) vm.svuota() else chiediMenu = true
                    })
                    Spacer(Modifier.weight(1f))
                    PulsanteTondo("⚙", { impostazioniAperte = true })
                }
            }
            // Versione nuova dell'app
            vm.novita?.let { n ->
                item { AvvisoAggiornamento(n, vm.scaricamento, vm::aggiorna) }
            }
            // Logo (tenuto premuto: modalità prove, solo per chi sistema l'app)
            item {
                Image(
                    painterResource(R.drawable.logo), "Da bimbo a bimbo",
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .combinedClickable(onClick = {}, onLongClick = { vm.prove = !vm.prove }),
                    contentScale = ContentScale.FillWidth,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PulsanteGrande("Scegli foto", "📷", ::scegliFoto, Modifier.weight(1f), altezza = 60.dp, grandezzaTesto = 18)
                    PulsanteChiaro("Sigle", "📖", { dizionarioAperto = true }, sfondo = Rosa, altezza = 60.dp)
                }
            }
            // Riepilogo di oggi (toccandolo: i giorni prima)
            vm.oggiPubblicati?.let { g ->
                item {
                    Surface(onClick = { riepilogoAperto = true }, color = Superficie, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, BordoScheda)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📊", fontSize = 20.sp)
                            Text(
                                "Oggi: ${g.articoli} ${if (g.articoli == 1) "articolo" else "articoli"} · ${Riepilogo.euro(g.centesimi)}",
                                fontWeight = FontWeight.Bold, color = BluNotte, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp).weight(1f),
                            )
                            Text("›", color = BluNotte, fontSize = 20.sp)
                        }
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
        val nomi = remember { java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.ITALY) }
        val leggi = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ITALY) }
        AlertDialog(
            onDismissRequest = { riepilogoAperto = false },
            title = { Text("📊 Pubblicati") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.giornate.take(14).forEach { g ->
                        val giorno = runCatching { nomi.format(leggi.parse(g.giorno)!!) }.getOrDefault(g.giorno).replaceFirstChar { it.uppercase() }
                        Row(Modifier.fillMaxWidth()) {
                            Text(giorno, Modifier.weight(1f))
                            Text("${g.articoli} · ${Riepilogo.euro(g.centesimi)}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { riepilogoAperto = false }) { Text("Chiudi") } },
        )
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
                text = { Text(f.avvisi.joinToString("\n") { "• $it" } + "\n\nVuoi sistemarla prima, o pubblicarla così?") },
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
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pubblicate $fatte di $tutte", fontSize = 14.sp, color = BluNotte, modifier = Modifier.weight(1f))
                if (fatte == tutte) Text("🎉", fontSize = 18.sp)
            }
            val avanzamento by animateFloatAsState(if (tutte == 0) 0f else fatte / tutte.toFloat(), label = "pubblicate")
            LinearProgressIndicator(
                progress = { avanzamento },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Verde, trackColor = Cielo,
            )
            PulsanteGrande(
                when {
                    prossima != null -> "Pubblica la prossima · Foto ${prossima.numero}"
                    fatte == tutte -> "Tutte pubblicate!"
                    else -> "Un attimo, preparo le foto…"
                },
                if (prossima != null) "📤" else if (fatte == tutte) "🎉" else "⏳",
                pubblica,
                Modifier.fillMaxWidth(),
                colore = if (fatte == tutte) Verde else Azzurro,
                attivo = prossima != null,
                altezza = 60.dp,
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
/** Impostazioni: grandezza del testo, schede compresse, pulsante "torna su", prezzo in grassetto. */
@Composable
private fun SchermataImpostazioni(vm: FotoViewModel, chiudi: () -> Unit) {
    Finestra(onDismissRequest = chiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(SfondoLista).systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Rosa, Cielo))).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚙ Impostazioni", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = BluNotte, modifier = Modifier.weight(1f))
                PulsanteTondo("✕", chiudi)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Riquadro("Aspetto") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "☀ Chiaro", 1 to "🌙 Scuro", 2 to "📱 Come il telefono").forEach { (v, nome) ->
                            FilterChip(selected = vm.tema == v, onClick = { vm.cambiaTema(v) }, label = { Text(nome, maxLines = 1) })
                        }
                    }
                }
                Riquadro("Grandezza del testo") {
                    val scelte = listOf(0.9f to "Piccolo", 1f to "Medio", 1.2f to "Grande", 1.4f to "Molto grande")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        scelte.forEach { (v, nome) ->
                            FilterChip(
                                selected = vm.scalaTesto == v, onClick = { vm.cambiaScalaTesto(v) },
                                label = { Text(nome, maxLines = 1) },
                            )
                        }
                    }
                    Text("Esempio: Felpa con cappuccio rosa - 8 anni - € 4,00", color = TestoTenue, modifier = Modifier.padding(top = 6.dp))
                }
                Riquadro("Lista delle foto") {
                    Interruttore("Chiudi le foto già pubblicate", "Diventano una riga piccola: meno da scorrere. Toccandole si riaprono.", vm.comprimiPubblicate, vm::cambiaComprimi)
                    Interruttore("2 foto alla volta", "Prepara le foto più in fretta. Se il telefono rallenta, spegnilo.", vm.dueAllaVolta, vm::cambiaDueAllaVolta)
                    Interruttore("Pulsante \"torna su\"", "Un pulsantino ↑ in basso a destra per tornare in cima alla lista.", vm.tornaSu, vm::cambiaTornaSu)
                }
                Riquadro("Aggiornamenti") {
                    val context = LocalContext.current
                    Text("Versione installata: ${remember { Aggiornamento.nomeVersione(context) }}", fontSize = 15.sp)
                    PulsanteChiaro("Controlla aggiornamenti", "🔄", { vm.controllaAggiornamenti(aMano = true) }, Modifier.fillMaxWidth().padding(top = 8.dp), altezza = 50.dp)
                    vm.esitoControllo?.let { Text(it, fontSize = 14.sp, color = BluNotte, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
                    vm.novita?.let { n -> AvvisoAggiornamento(n, vm.scaricamento, vm::aggiorna) }
                }
                Riquadro("Testo per WhatsApp") {
                    Interruttore("Prezzo in grassetto", "Il prezzo esce come *€ 4,00*: su WhatsApp si vede in grassetto.", vm.prezzoGrassetto, vm::cambiaGrassetto)
                }
            }
        }
    }
}

@Composable
private fun Riquadro(titolo: String, contenuto: @Composable ColumnScope.() -> Unit) {
    Surface(color = Superficie, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, BordoScheda)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(titolo, fontWeight = FontWeight.ExtraBold, color = BluNotte, fontSize = 17.sp, modifier = Modifier.padding(bottom = 8.dp))
            contenuto()
        }
    }
}

@Composable
private fun Interruttore(titolo: String, spiegazione: String, acceso: Boolean, cambia: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { cambia(!acceso) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titolo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(spiegazione, fontSize = 13.sp, color = TestoTenue)
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

/** "Sto sistemando le foto… 3 di 31" */
@Composable
private fun Avanzamento(fatte: Int, tutte: Int) {
    Surface(color = Cielo, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Sto sistemando le foto… ${fatte + 1} di $tutte", fontWeight = FontWeight.Bold, color = BluNotte, fontSize = 16.sp)
            // Nuvolette che si riempiono di colore man mano che le foto sono pronte
            val avanzamento by animateFloatAsState(fatte / tutte.toFloat(), tween(800), label = "avanzamento")
            NuvoleAvanzamento(avanzamento, Modifier.padding(top = 10.dp))
            Text("Puoi già pubblicare quelle pronte.", fontSize = 13.sp, color = BluNotte, modifier = Modifier.padding(top = 6.dp))
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
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Rosa, Cielo))).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Foto ${f.numero}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = BluNotte)
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
                Modifier.padding(start = 8.dp).size(34.dp).clip(CircleShape).background(Superficie.copy(alpha = 0.8f)).clickable(onClick = comprimi),
                contentAlignment = Alignment.Center,
            ) { Text("▲", fontSize = 14.sp, color = BluNotte) }
            // ✕ per togliere la foto dalla lista (chiede conferma)
            Box(
                Modifier.padding(start = 8.dp).size(34.dp).clip(CircleShape).background(Superficie.copy(alpha = 0.8f)).clickable(onClick = togli),
                contentAlignment = Alignment.Center,
            ) { Text("✕", fontSize = 16.sp, color = BluNotte, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.padding(14.dp)) {
            // Foto: toccala per ingrandire, girare, pixelare
            Box(
                Modifier.fillMaxWidth().height(300.dp).clip(MaterialTheme.shapes.medium)
                    .background(FondoTenue).clickable(enabled = f.file != null, onClick = ingrandisci),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = f.miniatura, label = "foto") { mini ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            mini != null -> AnteprimaZoomabile(mini, f.file, Modifier.fillMaxSize())
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
                    avviso?.let { Avviso(it) }

                    // Il testo per lo stato (toccalo per correggerlo)
                    if (testo.isNotBlank()) {
                        Surface(
                            onClick = modificaTesto,
                            color = FondoTenue,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(testo, fontSize = 17.sp, color = Testo)
                                Text("✏ Tocca per correggere", fontSize = 13.sp, color = BluNotte, modifier = Modifier.padding(top = 8.dp))
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
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PulsanteChiaro("Articoli (${lista.size})", "📋", articoli, Modifier.weight(1f))
                        PulsanteGrande(if (f.pubblicata) "Di nuovo" else "Pubblica", if (f.pubblicata) "↺" else "📤", pubblica, Modifier.weight(1f))
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
        testo, color = colore, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.background(sfondo, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp),
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
                            color = if (v.daTogliere) ARANCIONE else Color.Unspecified,
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
    testo, color = Arancione, fontWeight = FontWeight.Bold,
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(FondoAvviso, RoundedCornerShape(14.dp)).padding(12.dp),
)

private val ARANCIONE = Arancione

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
    Finestra(onDismissRequest = { if (pennello != null) pennello = null else chiudi() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (pennello != null) "Passa il dito sulla foto" else "Foto ${f.numero} · due dita per ingrandire",
                    color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f),
                )
                if (pennello == null) PulsanteTondo("✕", chiudi)
            }
            val img = immagine
            val modo = pennello
            if (modo != null && aggiornata != null && misureBase != null) {
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
                        // Sezione pixel: due strade, ognuna col suo "torna indietro"
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PulsanteVisore(
                if (applicato && !applicaAncora) "✓" else simbolo,
                if (applicato && !applicaAncora) "Fatto" else scritta,
                Modifier.fillMaxWidth(),
                attivo = attivo && (applicaAncora || !applicato),
                colore = if (applicato) Verde.copy(alpha = 0.18f) else Cielo,
                azione = applica,
            )
            AnimatedVisibility(extra != null) {
                PulsanteChiaro("Rimetti originale", "🖌", { extra?.invoke() }, Modifier.fillMaxWidth(), colore = Azzurro, altezza = 44.dp, grandezzaTesto = 13, attivo = attivo)
            }
            AnimatedVisibility(applicato) {
                PulsanteChiaro("Togli", "↺", togli, Modifier.fillMaxWidth(), colore = Arancione, altezza = 44.dp, grandezzaTesto = 14)
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

private fun caricaRidotta(file: File, lato: Int): ImageBitmap? {
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
