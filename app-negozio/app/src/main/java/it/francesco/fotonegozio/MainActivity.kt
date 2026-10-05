package it.francesco.fotonegozio

import android.content.Intent
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
        setContent { ConScritte(viewModel.scritteGrandi) { TemaBimbo { Schermata(viewModel) } } }

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

/** Scritte grandi accese o spente (letto anche dalle finestre, che hanno un loro schermo). */
private val LocalScritteGrandi = compositionLocalOf { false }

/** Ingrandisce tutte le scritte del 25% se [grandi] (il resto della grafica resta uguale). */
@Composable
private fun ConScritte(grandi: Boolean, contenuto: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalScritteGrandi provides grandi,
        LocalDensity provides Density(d.density, d.fontScale * if (grandi) 1.25f else 1f),
        content = contenuto,
    )
}

/** Finestra a tutto schermo che rispetta anche lei le scritte grandi. */
@Composable
private fun Finestra(onDismissRequest: () -> Unit, properties: DialogProperties, contenuto: @Composable () -> Unit) {
    val grandi = LocalScritteGrandi.current
    Dialog(onDismissRequest, properties) { ConScritte(grandi, contenuto) }
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
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
                    val (s1, m1) = rimbalzo()
                    val (s2, m2) = rimbalzo()
                    Button(
                        onClick = ::scegliFoto,
                        modifier = Modifier.weight(1f).heightIn(min = 60.dp).then(m1),
                        shape = MaterialTheme.shapes.large,
                        interactionSource = s1,
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    ) { Text("📷  Scegli foto", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    FilledTonalButton(
                        onClick = { dizionarioAperto = true },
                        modifier = Modifier.heightIn(min = 60.dp).then(m2),
                        interactionSource = s2,
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Rosa, contentColor = BluNotte),
                    ) { Text("📖 Sigle", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
            item {
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
                    Spacer(Modifier.weight(1f))
                    FilterChip(
                        selected = vm.scritteGrandi, onClick = { vm.cambiaScritte(!vm.scritteGrandi) },
                        label = { Text("A+") },
                    )
                }
            }
            if (vm.soloDaControllare && vm.fotoVisibili.isEmpty()) item {
                Surface(onClick = { vm.soloDaControllare = false }, color = Color.White, shape = MaterialTheme.shapes.medium) {
                    Text("Nessuna foto da controllare 👍  Tocca per vederle tutte.", Modifier.fillMaxWidth().padding(16.dp), color = BluNotte, fontWeight = FontWeight.Bold)
                }
            }
            // Solo in modalità prove
            if (vm.prove) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Diagnosi (modalità prove)", Modifier.weight(1f), fontSize = 14.sp, color = Color.Gray)
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
                )
            }
        }
        if (festa) Coriandoli()
      }
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
            title = { Text("Togliere la Foto $numero?") },
            text = { Text("Sparisce solo da questa lista: nella Galleria del telefono resta.") },
            confirmButton = { TextButton(onClick = { vm.togli(numero); daTogliere = null }) { Text("Togli", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { daTogliere = null }) { Text("Annulla") } },
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
            gira = { gradi -> vibra.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.gira(f.numero, gradi) },
            salvaPixel = { celle, lato -> vm.salvaPixelata(f.numero, celle, lato) },
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
    Surface(color = Color.White, shadowElevation = 12.dp, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
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
            val (sorgente, morbido) = rimbalzo()
            Button(
                onClick = pubblica,
                enabled = prossima != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).then(morbido),
                shape = MaterialTheme.shapes.large,
                interactionSource = sorgente,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            ) {
                AnimatedContent(
                    targetState = when {
                        prossima != null -> "📤  Pubblica la prossima · Foto ${prossima.numero}"
                        fatte == tutte -> "✓  Tutte pubblicate!"
                        else -> "Un attimo, preparo le foto…"
                    },
                    transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                    label = "pulsante",
                ) { t -> Text(t, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Schermata vuota: cosa fare per cominciare. */
@Composable
private fun Benvenuto() {
    Surface(color = Color.White, shape = MaterialTheme.shapes.large, shadowElevation = 4.dp, modifier = Modifier.entrata()) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ciao! 👋", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BluNotte)
            Text(
                "Tocca «Scegli foto» e seleziona le foto da pubblicare.\n\n" +
                    "Oppure dalla Galleria: seleziona le foto, tocca Condividi e scegli «Da bimbo a bimbo».",
                fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp), color = Color(0xFF3A4660),
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
) {
    val lista = f.articoli
    // Le schede pubblicate si "spengono" un po': si vede subito cosa resta da fare
    val trasparenza by animateFloatAsState(if (f.pubblicata) 0.72f else 1f, label = "pubblicata")
    Surface(
        color = Color.White,
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
                    1 -> Etichetta("In lavorazione…", Color.White, BluNotte)
                    2 -> Etichetta("⚠ Da controllare", Color.White, Arancione)
                    else -> Etichetta("Pronta", Color.White, Verde)
                }
            }
            // ✕ per togliere la foto dalla lista (chiede conferma)
            Box(
                Modifier.padding(start = 8.dp).size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)).clickable(onClick = togli),
                contentAlignment = Alignment.Center,
            ) { Text("✕", fontSize = 16.sp, color = BluNotte, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.padding(14.dp)) {
            // Foto: toccala per ingrandire, girare, pixelare
            Box(
                Modifier.fillMaxWidth().height(300.dp).clip(MaterialTheme.shapes.medium)
                    .background(Color(0xFFEAF5FC)).clickable(enabled = f.file != null, onClick = ingrandisci),
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
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
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
                            color = Color(0xFFEAF5FC),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(testo, fontSize = 17.sp, color = Color(0xFF1B1F2A))
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
                        fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp),
                    )

                    // Due pulsanti, niente di più
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val (s1, m1) = rimbalzo()
                        val (s2, m2) = rimbalzo()
                        OutlinedButton(
                            onClick = articoli,
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp).then(m1),
                            shape = MaterialTheme.shapes.medium,
                            interactionSource = s1,
                        ) { Text("📋 Articoli (${lista.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = pubblica,
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp).then(m2),
                            shape = MaterialTheme.shapes.medium,
                            interactionSource = s2,
                            colors = ButtonDefaults.buttonColors(containerColor = Azzurro),
                        ) { Text(if (f.pubblicata) "↺ Di nuovo" else "📤 Pubblica", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
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
                    Surface(color = Color.White, shape = MaterialTheme.shapes.medium, shadowElevation = 3.dp, border = BorderStroke(1.dp, BordoScheda)) {
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
            Button(
                onClick = { modifica(null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 58.dp),
                shape = MaterialTheme.shapes.large,
            ) { Text("+ Aggiungi etichetta", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
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
                fontSize = 12.sp, color = Color.Gray,
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
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(Color(0xFFFFEFE3), RoundedCornerShape(14.dp)).padding(12.dp),
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
 * Foto a schermo intero: si ingrandisce con le dita. In basso: Gira, Capovolgi, Pixela, Testo.
 * "Pixela" apre l'editor: col dito si coprono a quadrettoni le parti da nascondere.
 */
@Composable
private fun Visore(f: Foto, gira: (Int) -> Unit, salvaPixel: (Set<Long>, Int) -> Unit, testo: () -> Unit, chiudi: () -> Unit) {
    val immagine by fotoPerSchermo(f.file)
    val misure = remember(f.file) { f.file?.let(::misureFoto) }   // grandezza vera, per i quadretti della pixelatura
    var pixela by remember { mutableStateOf(false) }
    Finestra(onDismissRequest = { if (pixela) pixela = false else chiudi() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (pixela) "Passa il dito su cosa nascondere" else "Foto ${f.numero} · due dita per ingrandire",
                    color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f),
                )
                if (!pixela) TextButton(onClick = chiudi) { Text("✕ Chiudi", color = Color.White, fontSize = 18.sp) }
            }
            val img = immagine
            if (pixela && img != null && misure != null) {
                EditorPixel(
                    img, misure.first, misure.second, Modifier.weight(1f),
                    salva = { celle, lato -> salvaPixel(celle, lato); pixela = false },
                    esci = { pixela = false },
                )
            } else {
                // key: se la foto cambia (girata, pixelata) lo zoom riparte da capo
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (img != null) key(f.file) { FotoZoomabile(img, Modifier.fillMaxSize()) }
                }
                Surface(color = Color.White, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulsanteVisore("↻", "Gira", Modifier.weight(1f)) { gira(90) }
                        PulsanteVisore("⇅", "Capovolgi", Modifier.weight(1f)) { gira(180) }
                        PulsanteVisore("▦", "Pixela", Modifier.weight(1f)) { pixela = true }
                        PulsanteVisore("✏", "Testo", Modifier.weight(1f), testo)
                    }
                }
            }
        }
    }
}

/** Pulsante quadrato del visore: simbolo grande e scritta sotto. */
@Composable
private fun PulsanteVisore(simbolo: String, scritta: String, modifier: Modifier = Modifier, azione: () -> Unit) {
    val (sorgente, morbido) = rimbalzo()
    FilledTonalButton(
        onClick = azione,
        modifier = modifier.heightIn(min = 72.dp).then(morbido),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(4.dp),
        interactionSource = sorgente,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Cielo, contentColor = BluNotte),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(simbolo, fontSize = 24.sp)
            Text(scritta, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Editor della pixelatura a mano: col dito si "dipingono" quadrettoni sulle parti da nascondere. */
@Composable
private fun EditorPixel(immagine: ImageBitmap, w: Int, h: Int, modifier: Modifier, salva: (Set<Long>, Int) -> Unit, esci: () -> Unit) {
    // w, h = grandezza vera della foto (quella mostrata può essere rimpicciolita)
    val lato = remember(immagine) { PixelManuale.lato(w, h) }
    // Colori di anteprima: la foto rimpicciolita, un pixel per quadretto
    val piccola = remember(immagine) {
        android.graphics.Bitmap.createScaledBitmap(immagine.asAndroidBitmap(), (w + lato - 1) / lato, (h + lato - 1) / lato, true)
    }
    val celle = remember { mutableStateMapOf<Long, Int>() }   // quadretto → numero del tratto (per "Annulla")
    var tratto by remember { mutableIntStateOf(0) }
    var grande by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pxW = constraints.maxWidth.toFloat(); val pxH = constraints.maxHeight.toFloat()
            val scala = minOf(pxW / w, pxH / h)
            val ox = (pxW - w * scala) / 2; val oy = (pxH - h * scala) / 2
            fun dipingi(p: Offset) {
                val raggio = lato * (if (grande) 2.4f else 1.2f)
                PixelManuale.celleAttorno((p.x - ox) / scala, (p.y - oy) / scala, raggio, lato, w, h)
                    .forEach { if (it !in celle) celle[it] = tratto }
            }
            Image(immagine, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Canvas(
                Modifier.fillMaxSize().testTag("tela")
                    .pointerInput(grande) {
                        detectTapGestures(onPress = { tratto++; dipingi(it) })
                    }
                    .pointerInput(grande) {
                        // il tratto è già contato da onPress
                        detectDragGestures(onDragStart = { dipingi(it) }) { cambio, _ -> dipingi(cambio.position) }
                    }
            ) {
                val l = lato * scala
                for (c in celle.keys) {
                    val col = PixelManuale.colonna(c); val rig = PixelManuale.riga(c)
                    drawRect(
                        Color(piccola.getPixel(col.coerceAtMost(piccola.width - 1), rig.coerceAtMost(piccola.height - 1))),
                        Offset(ox + col * l, oy + rig * l), androidx.compose.ui.geometry.Size(l + 1, l + 1),
                    )
                }
            }
        }
        Surface(color = Color.White, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !grande, onClick = { grande = false }, label = { Text("● Pennello piccolo") })
                    FilterChip(selected = grande, onClick = { grande = true }, label = { Text("⬤ Pennello grande") })
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = esci, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("✕ Esci") }
                    OutlinedButton(
                        onClick = { celle.values.maxOrNull()?.let { ultimo -> celle.keys.filter { celle[it] == ultimo }.forEach(celle::remove) } },
                        enabled = celle.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("↶ Annulla") }
                    Button(
                        onClick = { salva(celle.keys.toSet(), lato) },
                        enabled = celle.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("✓ Salva", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Immagine che si ingrandisce con due dita (fino a 8 volte) e si sposta col dito. Doppio tocco: zoom avanti/indietro. */
@Composable
private fun FotoZoomabile(immagine: ImageBitmap, modifier: Modifier) {
    var scala by remember { mutableFloatStateOf(1f) }
    var spostamento by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { punto ->
                    if (scala > 1f) {
                        scala = 1f; spostamento = Offset.Zero
                    } else {
                        // Porto il punto toccato al centro, ingrandito 3 volte
                        val centro = Offset(size.width / 2f, size.height / 2f)
                        scala = 3f; spostamento = (centro - punto) * 3f
                    }
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, sposta, zoom, _ ->
                    scala = (scala * zoom).coerceIn(1f, 8f)
                    spostamento = if (scala == 1f) Offset.Zero else spostamento + sposta
                }
            }
    ) {
        Image(
            immagine, null,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scala; scaleY = scala
                translationX = spostamento.x; translationY = spostamento.y
            },
            contentScale = ContentScale.Fit,
        )
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
                immagine?.let { FotoZoomabile(it, Modifier.fillMaxSize()) }
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
                Text("…oppure riempi o correggi i campi:", fontSize = 13.sp, color = Color.Gray)
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
                immagine?.let { FotoZoomabile(it, Modifier.fillMaxSize()) }
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
