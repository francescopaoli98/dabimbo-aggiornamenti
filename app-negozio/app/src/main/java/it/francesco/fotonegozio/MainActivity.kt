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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
    var ingrandita by remember { mutableStateOf<Foto?>(null) }

    Scaffold { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize()) {
            Button(
                onClick = { scegli.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
            ) { Text("Scegli foto", fontSize = 18.sp) }

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
                items(vm.foto, key = { it.numero }) { f -> Scheda(f) { ingrandita = f } }
            }
        }
    }

    ingrandita?.let { f -> Ingrandimento(f) { ingrandita = null } }
}

/** Una riga della lista: miniatura + cosa ha fatto l'app. */
@Composable
private fun Scheda(f: Foto, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = f.file != null, onClick = onClick)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                when {
                    f.miniatura != null -> Image(f.miniatura, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    f.inCorso -> CircularProgressIndicator()
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("Foto ${f.numero}", fontWeight = FontWeight.Bold)
                when {
                    f.inCorso -> Text("In attesa…")
                    f.errore != null -> Text("Errore: ${f.errore}", color = MaterialTheme.colorScheme.error)
                    else -> {
                        Text(if (f.rotazione == 0) "Già dritta" else "Ruotata di ${f.rotazione}°")
                        Text(
                            f.codice?.let { "Codice letto: $it" } ?: "Cartellino non trovato",
                            color = if (f.codice == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Text("Metodo: ${f.metodo} · ${"%.1f".format(f.secondi)} s", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Foto raddrizzata a schermo intero, per controllare il risultato. Tocca per chiudere. */
@Composable
private fun Ingrandimento(f: Foto, chiudi: () -> Unit) {
    val immagine = remember(f.file) { f.file?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    Dialog(onDismissRequest = chiudi, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = chiudi), contentAlignment = Alignment.Center) {
            immagine?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
    }
}
