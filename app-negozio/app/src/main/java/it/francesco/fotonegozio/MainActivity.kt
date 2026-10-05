package it.francesco.fotonegozio

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.io.File
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
        setContent { TemaBimbo { Schermata(viewModel) } }
    }

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
    var daConfermare by remember { mutableStateOf<Int?>(null) }      // foto con avvisi: chiedo prima di pubblicare
    val context = LocalContext.current

    // Foto + testo a WhatsApp Business. L'invio lo preme Elisa dentro WhatsApp.
    val vibra = LocalHapticFeedback.current
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
                    Button(
                        onClick = ::scegliFoto,
                        modifier = Modifier.weight(1f).height(60.dp),
                        shape = MaterialTheme.shapes.large,
                    ) { Text("📷  Scegli foto", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    FilledTonalButton(
                        onClick = { dizionarioAperto = true },
                        modifier = Modifier.height(60.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Rosa, contentColor = BluNotte),
                    ) { Text("📖 Sigle", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    FilledTonalButton(
                        onClick = vm::cambiaSuoni,
                        modifier = Modifier.height(60.dp).width(60.dp),
                        shape = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = BluNotte),
                    ) { Text(if (vm.suoniAttivi) "🔊" else "🔇", fontSize = 22.sp) }
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

            items(vm.foto, key = { it.numero }) { f ->
                Scheda(
                    f,
                    modifier = Modifier.animateItem(),
                    prove = vm.prove,
                    testo = vm.testo(f),
                    ingrandisci = { ingrandita = f.numero },
                    gira = { gradi -> vibra.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.gira(f.numero, gradi) },
                    modifica = { indice -> inModifica = f.numero to indice },
                    modificaTesto = { testoInModifica = f.numero },
                    automatico = { vm.cambiaTesto(f.numero, null) },
                    pubblica = { chiediEPubblica(f) },
                    salvaDiagnosi = { vm.salvaDiagnosi(f.numero) },
                )
            }
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
        Ingrandimento(f, gira = { gradi -> vm.gira(f.numero, gradi) }) { ingrandita = null }
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
            Button(
                onClick = pubblica,
                enabled = prossima != null,
                modifier = Modifier.fillMaxWidth().height(62.dp),
                shape = MaterialTheme.shapes.large,
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
    Surface(color = Color.White, shape = MaterialTheme.shapes.large, shadowElevation = 2.dp) {
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
            LinearProgressIndicator(
                progress = { fatte / tutte.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = BluNotte, trackColor = Color.White,
            )
            Text("Puoi già pubblicare quelle pronte.", fontSize = 13.sp, color = BluNotte, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Una foto: foto grande, testo per lo stato in evidenza, dettagli degli articoli che si aprono, pulsanti. */
@Composable
private fun Scheda(
    f: Foto,
    modifier: Modifier = Modifier,
    prove: Boolean,
    testo: String,
    ingrandisci: () -> Unit,
    gira: (Int) -> Unit,
    modifica: (Int?) -> Unit,
    modificaTesto: () -> Unit,
    automatico: () -> Unit,
    pubblica: () -> Unit,
    salvaDiagnosi: () -> Unit,
) {
    val articoli = f.articoli
    val mancanti = f.etichetteViste - articoli.size
    // I dettagli si aprono da soli se c'è qualcosa da sistemare
    var dettagli by remember(f.numero) { mutableStateOf(false) }
    val daSistemare = !f.inCorso && f.errore == null && (articoli.isEmpty() || articoli.any { it.daCompletare } || mancanti > 0)

    // Le schede pubblicate si "spengono" un po': si vede subito cosa resta da fare
    val trasparenza by animateFloatAsState(if (f.pubblicata) 0.72f else 1f, label = "pubblicata")
    Surface(
        color = Color.White,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 6.dp,
        border = BorderStroke(1.5.dp, BordoScheda),
        modifier = modifier.fillMaxWidth().alpha(trasparenza),
    ) {
      Column(Modifier.animateContentSize()) {
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
        }
        Column(Modifier.padding(14.dp)) {

            // Foto grande (toccala per ingrandire); quando la giri sfuma nella nuova
            Box(
                Modifier.fillMaxWidth().height(300.dp).clip(MaterialTheme.shapes.medium)
                    .background(Color(0xFFEAF5FC)).clickable(enabled = f.file != null, onClick = ingrandisci),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = f.miniatura, label = "foto") { mini ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            mini != null -> Image(mini, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            f.inCorso -> CircularProgressIndicator(color = BluNotte)
                        }
                    }
                }
                if (f.file != null) Text(
                    "🔍", fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color.White.copy(alpha = 0.85f), CircleShape).padding(8.dp),
                )
            }
            if (f.file != null) PulsantiGira(gira, Modifier.padding(top = 8.dp))
            if (f.daControllare && !f.inCorso && !f.pubblicata) Avviso("⚠ Controlla che la foto sia dritta")

            when {
                f.inCorso -> {}
                f.errore != null -> Avviso("Errore: ${f.errore}")
                else -> {
                    // Il testo per lo stato, in evidenza
                    if (testo.isNotBlank()) {
                        Surface(
                            onClick = modificaTesto,
                            color = Color(0xFFEAF5FC),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Testo per lo stato", fontSize = 13.sp, color = Azzurro, fontWeight = FontWeight.Bold)
                                Text(testo, fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp), color = Color(0xFF1B1F2A))
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("✏ Tocca per correggere", fontSize = 13.sp, color = BluNotte, modifier = Modifier.weight(1f))
                                    if (f.testoManuale != null) TextButton(onClick = automatico) { Text("↺ Automatico") }
                                }
                            }
                        }
                    }

                    // Avvisi sugli articoli
                    if (articoli.isEmpty()) Avviso(
                        if (f.codice == null) "⚠ Cartellino non letto: aggiungi l'articolo a mano"
                        else "⚠ Cartellino ${f.codice} non leggibile: aggiungi l'articolo a mano"
                    )
                    if (mancanti > 0) Avviso(
                        if (mancanti == 1) "⚠ C'è ancora 1 etichetta non letta: aggiungila a mano"
                        else "⚠ Ci sono ancora $mancanti etichette non lette: aggiungile a mano"
                    )

                    // Dettagli degli articoli (si aprono a richiesta, o da soli se c'è da sistemare)
                    val aperti = dettagli || daSistemare
                    TextButton(onClick = { dettagli = !dettagli }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            (if (aperti) "▾ " else "▸ ") + when (articoli.size) {
                                0 -> "Articoli"
                                1 -> "Dettagli articolo"
                                else -> "Dettagli ${articoli.size} articoli"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    AnimatedVisibility(visible = aperti, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                      Column {
                        articoli.forEachIndexed { i, d ->
                            Surface(color = Sfondo, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (articoli.size > 1) "Articolo ${i + 1}" else "Articolo",
                                            fontWeight = FontWeight.Bold, color = BluNotte, modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = { modifica(i) }) { Text("✏ Modifica") }
                                    }
                                    if (d.daCompletare) Avviso("⚠ Da completare: tocca Modifica e guarda la foto")
                                    DatiLetti(d)
                                }
                            }
                        }
                        OutlinedButton(onClick = { modifica(null) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium) {
                            Text("+ Aggiungi articolo")
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
                }
            }

            if (f.file != null && !f.inCorso) {
                FilledTonalButton(
                    onClick = pubblica,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Cielo, contentColor = BluNotte),
                ) { Text(if (f.pubblicata) "↺  Pubblica di nuovo" else "📤  Pubblica questa", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
            if (prove && f.diario != null && !f.diario.vuoto) {
                OutlinedButton(onClick = salvaDiagnosi, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("📷 Salva diagnosi in Galleria") }
            }
        }
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

    Dialog(onDismissRequest = chiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().imePadding().padding(16.dp)) {
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

    if (nuova || inModifica != null) {
        ModificaSigla(
            iniziale = inModifica,
            salva = { v -> salva(inModifica?.sigla, v); nuova = false; inModifica = null },
            annulla = { nuova = false; inModifica = null },
        )
    }
}

/** Finestra per una sigla: sigla, significato (con le forme separate da "/"), oppure "togli dal testo". */
@Composable
private fun ModificaSigla(iniziale: VoceDizionario?, salva: (VoceDizionario?) -> Unit, annulla: () -> Unit) {
    var sigla by remember { mutableStateOf(iniziale?.sigla.orEmpty()) }
    var significato by remember { mutableStateOf(iniziale?.significatoTesto.orEmpty()) }
    var togli by remember { mutableStateOf(iniziale?.daTogliere ?: false) }
    AlertDialog(
        onDismissRequest = annulla,
        title = { Text(if (iniziale == null) "Nuova sigla" else "Modifica sigla") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sigla, { sigla = it.trim() }, label = { Text("Sigla (es. gri)") }, singleLine = true)
                if (!togli) {
                    OutlinedTextField(significato, { significato = it }, label = { Text("Significato") })
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = sigla.isNotBlank() && (togli || significato.isNotBlank()),
                onClick = {
                    val forme = if (togli) emptyList() else significato.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                    salva(VoceDizionario(sigla.lowercase(), forme))
                },
            ) { Text("Salva") }
        },
        dismissButton = {
            Row {
                if (iniziale != null) TextButton(onClick = { salva(null) }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = annulla) { Text("Annulla") }
            }
        },
    )
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

/** "Gira" = 90° a destra; "Capovolgi" = 180°, un tocco solo se l'app ha scelto il verso sbagliato. */
@Composable
private fun PulsantiGira(gira: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { gira(90) }, modifier = Modifier.weight(1f).height(46.dp), shape = MaterialTheme.shapes.medium) {
            Text("↻ Gira", fontSize = 15.sp)
        }
        OutlinedButton(onClick = { gira(180) }, modifier = Modifier.weight(1f).height(46.dp), shape = MaterialTheme.shapes.medium) {
            Text("⇅ Capovolgi", fontSize = 15.sp)
        }
    }
}

/** Foto a schermo intero: si ingrandisce con due dita o con doppio tocco; pulsanti per girarla. */
@Composable
private fun Ingrandimento(f: Foto, gira: (Int) -> Unit, chiudi: () -> Unit) {
    val immagine = remember(f.file) { f.file?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    Dialog(onDismissRequest = chiudi, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Due dita o doppio tocco per ingrandire", color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = chiudi) { Text("✕ Chiudi", color = Color.White, fontSize = 18.sp) }
            }
            // key: se la foto viene girata, lo zoom riparte da capo
            key(f.file) {
                immagine?.let { FotoZoomabile(it, Modifier.weight(1f).fillMaxWidth()) }
            }
            Surface { PulsantiGira(gira, Modifier.padding(12.dp)) }
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
    val immagine = remember(foto) { foto?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }

    Dialog(onDismissRequest = annulla, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
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
    val immagine = remember(foto) { foto?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    Dialog(onDismissRequest = annulla, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
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

/** "2,5" → "€ 2,50"; "3" → "€ 3,00"; vuoto → null. */
private fun prezzoInFormato(testo: String): String? {
    val pulito = testo.replace("€", "").replace(" ", "").replace('.', ',').ifBlank { return null }
    val parti = pulito.split(",")
    val euro = parti[0].ifBlank { "0" }
    val centesimi = parti.getOrNull(1).orEmpty().padEnd(2, '0').take(2)
    return "€ $euro,$centesimi"
}
