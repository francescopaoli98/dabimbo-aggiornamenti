package it.francesco.fotonegozio

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
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
        enableEdgeToEdge()
        // Foto arrivate dal tasto "Condividi" della Galleria (solo al primo avvio, non dopo una rotazione schermo)
        if (savedInstanceState == null) viewModel.carica(fotoDaIntent(intent))
        setContent { MaterialTheme { Schermata(viewModel) } }
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

    Scaffold { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize()) {
            Button(
                onClick = { scegli.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
            ) { Text("Scegli foto", fontSize = 18.sp) }
            OutlinedButton(onClick = { dizionarioAperto = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("📖 Dizionario delle sigle")
            }

            // Modalità diagnosi (per le prove): vale per le foto scelte DOPO averla accesa
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Diagnosi (per le prove)", Modifier.weight(1f), fontSize = 14.sp, color = Color.Gray)
                Switch(checked = vm.diagnosi, onCheckedChange = { vm.diagnosi = it })
            }
            vm.messaggio?.let { m ->
                Text(m, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { vm.messaggio = null }.padding(vertical = 4.dp))
            }

            // Avanzamento: "Elaboro 3 di 50"
            if (vm.foto.isNotEmpty()) {
                val totale = vm.foto.size
                Text(
                    if (vm.elaborate < totale) "Elaboro ${vm.elaborate + 1} di $totale…" else "Fatto: $totale foto",
                    Modifier.padding(vertical = 8.dp), fontWeight = FontWeight.Bold,
                )
                LinearProgressIndicator(
                    progress = { vm.elaborate / totale.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(vm.foto, key = { it.numero }) { f ->
                    Scheda(
                        f,
                        onClick = { ingrandita = f.numero },
                        gira = { gradi -> vm.gira(f.numero, gradi) },
                        modifica = { indice -> inModifica = f.numero to indice },
                        salvaDiagnosi = { vm.salvaDiagnosi(f.numero) },
                        testo = vm.testo(f),
                        cambiaTesto = { t -> vm.cambiaTesto(f.numero, t) },
                        modificaTesto = { testoInModifica = f.numero },
                        pixelatura = { on -> vm.pixelatura(f.numero, on) },
                    )
                }
            }
        }
    }

    if (dizionarioAperto) {
        SchermataDizionario(vm.dizionario, salva = vm::salvaSigla, chiudi = { dizionarioAperto = false })
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

/** Una foto nella lista: foto grande, avviso se il verso è da controllare, articoli letti, pulsanti. */
@Composable
private fun Scheda(
    f: Foto,
    onClick: () -> Unit,
    gira: (Int) -> Unit,
    modifica: (Int?) -> Unit,
    salvaDiagnosi: () -> Unit,
    testo: String,
    cambiaTesto: (String?) -> Unit,
    modificaTesto: () -> Unit,
    pixelatura: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Foto ${f.numero}", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                if (f.daControllare && !f.inCorso) {
                    Text("⚠ Controlla il verso", color = ARANCIONE, fontWeight = FontWeight.Bold)
                }
            }

            // Foto grande, a tutta larghezza: si vede subito se è dritta. Toccala per lo schermo intero.
            Box(
                Modifier.fillMaxWidth().height(280.dp).padding(vertical = 8.dp)
                    .clickable(enabled = f.file != null, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    f.miniatura != null -> Image(f.miniatura, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    f.inCorso -> CircularProgressIndicator()
                }
            }

            when {
                f.inCorso -> Text("In attesa…")
                f.errore != null -> Text("Errore: ${f.errore}", color = MaterialTheme.colorScheme.error)
                else -> {
                    val articoli = f.articoli
                    if (articoli.isEmpty()) {
                        Avviso(
                            if (f.codice == null) "⚠ Cartellino non letto: aggiungi l'articolo a mano"
                            else "⚠ Cartellino ${f.codice} non leggibile: aggiungi l'articolo a mano"
                        )
                    }
                    articoli.forEachIndexed { i, d ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (articoli.size > 1) "Articolo ${i + 1} di ${articoli.size}" else "Articolo",
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { modifica(i) }) { Text("✏ Modifica") }
                        }
                        if (d.daCompletare) Avviso("⚠ Da completare: tocca la foto per leggere meglio il cartellino")
                        DatiLetti(d)
                    }
                    // Si vedono più cartellini di quelli letti: Elisa deve aggiungere i mancanti
                    val mancanti = f.etichetteViste - articoli.size
                    if (mancanti > 0) {
                        Avviso(
                            if (mancanti == 1) "⚠ C'è ancora 1 etichetta non letta: aggiungila a mano"
                            else "⚠ Ci sono ancora $mancanti etichette non lette: aggiungile a mano"
                        )
                    }
                    OutlinedButton(onClick = { modifica(null) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("+ Aggiungi articolo")
                    }

                    // Pezzo 3: il testo che andrà nello stato (si può correggere a mano)
                    if (testo.isNotBlank() || f.testoManuale != null) {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Testo per lo stato", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (f.testoManuale != null) TextButton(onClick = { cambiaTesto(null) }) { Text("↺ Automatico") }
                        }
                        // Toccando il testo si apre lo schermo diviso: foto ingrandibile sopra, testo sotto
                        Surface(
                            onClick = modificaTesto,
                            shape = MaterialTheme.shapes.small,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(testo, style = MaterialTheme.typography.bodyLarge)
                                Text("✏ Tocca per correggere", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        if (f.testoManuale != null) Text("Corretto a mano: le modifiche agli articoli non lo cambiano più", fontSize = 12.sp, color = Color.Gray)
                    }
                    // Per le prove: come ha lavorato l'app (sparirà nella versione finale)
                    Text(
                        (if (f.rotazione == 0) "Già dritta" else "Ruotata di ${f.rotazione}°") +
                            (if (f.messaInVerticale) " · messa in verticale" else "") +
                            (if (f.rotazioneManuale != 0) " · girata a mano" else "") +
                            " · ${f.metodo} · ${"%.1f".format(f.secondi)} s",
                        fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (f.file != null) PulsantiGira(gira, Modifier.padding(top = 8.dp))
            // Pezzo 4: sfondo pixelato sì/no
            if (f.filePixelata != null) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sfondo pixelato", Modifier.weight(1f))
                    Switch(checked = f.pixelata, onCheckedChange = pixelatura)
                }
            } else if (!f.inCorso && f.file != null) {
                Text("Sfondo non pixelato (separazione oggetti non riuscita)", fontSize = 12.sp, color = Color.Gray)
            }
            if (f.diario != null && !f.diario.vuoto) {
                OutlinedButton(onClick = salvaDiagnosi, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("📷 Salva diagnosi in Galleria") }
            }
        }
    }
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
private fun Avviso(testo: String) = Text(testo, color = ARANCIONE, fontWeight = FontWeight.Bold)

private val ARANCIONE = Color(0xFFE65100)

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
        OutlinedButton(onClick = { gira(90) }, modifier = Modifier.weight(1f).height(48.dp)) {
            Text("↻ Gira", fontSize = 16.sp)
        }
        OutlinedButton(onClick = { gira(180) }, modifier = Modifier.weight(1f).height(48.dp)) {
            Text("⇅ Capovolgi", fontSize = 16.sp)
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
