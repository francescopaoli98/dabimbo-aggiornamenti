package it.francesco.fotonegozio

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Cosa si sta mandando a Instagram (per l'anteprima). */
private enum class TipoIG { STORIE, CAROSELLO }

/** Instagram accetta al massimo 20 foto in un carosello. */
private const val MAX_CAROSELLO = 20

/**
 * Instagram: si toccano le foto nell'ordine voluto (compare ①②③…), dalla lista di adesso
 * o dai giorni passati dello storico; poi "Storie" (una storia per foto, con la fascia del prezzo)
 * o "Carosello" (4:5 numerate + didascalia negli appunti). Si vede l'anteprima e si apre Instagram:
 * la pubblicazione la preme sempre Elisa.
 */
@Composable
fun SchermataInstagram(vm: FotoViewModel, chiudi: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nomi = remember { java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.ITALY) }
    val leggi = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ITALY) }
    fun nomeGiorno(g: String) = runCatching { nomi.format(leggi.parse(g)!!) }.getOrDefault(g)

    val lista = remember { vm.elementiListaIG() }
    val giorni = remember { vm.giorniIG() }
    // Da dove si prendono le foto: null = lista di adesso, altrimenti un giorno dello storico
    var sorgente by remember { mutableStateOf<String?>(if (lista.isEmpty()) giorni.firstOrNull() else null) }
    val elementi = remember(sorgente) { sorgente?.let(vm::elementiGiornoIG) ?: lista }
    val scelte = remember { mutableStateListOf<ElementoIG>() }
    var preparo by remember { mutableStateOf(false) }
    var anteprima by remember { mutableStateOf<Pair<TipoIG, List<File>>?>(null) }
    var guida by remember { mutableStateOf(false) }

    fun prepara(tipo: TipoIG) {
        if (preparo) return
        preparo = true
        scope.launch {
            val file = runCatching {
                if (tipo == TipoIG.STORIE) vm.preparaStorie(context, scelte.toList()) else vm.preparaCarosello(context, scelte.toList())
            }.getOrDefault(emptyList())
            preparo = false
            if (file.isEmpty()) Toast.makeText(context, "Non sono riuscito a preparare le foto", Toast.LENGTH_LONG).show()
            else anteprima = tipo to file
        }
    }

    Finestra(onDismissRequest = chiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(SfondoLista).systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Rosa, Cielo))).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📸 Instagram", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = BluNotte, modifier = Modifier.weight(1f))
                PulsanteTondo("❓", { guida = true })
                Spacer(Modifier.width(8.dp))
                PulsanteTondo("✕", chiudi)
            }
            ProponiGuida(Guide.STORIE, vm.guidaVista(Guide.STORIE.id), { vm.segnaGuidaVista(Guide.STORIE.id) }) { guida = true }
            // Da dove: lista di adesso o un giorno passato
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (lista.isNotEmpty()) item {
                    FilterChip(selected = sorgente == null, onClick = { sorgente = null }, label = { Text("Lista di adesso (${lista.size})") })
                }
                items(giorni) { g ->
                    FilterChip(selected = sorgente == g, onClick = { sorgente = g }, label = { Text(nomeGiorno(g)) }, modifier = Modifier.testTag("giorno_$g"))
                }
            }
            Text(
                if (scelte.isEmpty()) "Tocca le foto nell'ordine in cui le vuoi: compare il numero."
                else "Scelte: ${scelte.size}. Ritocca una foto per toglierla.",
                fontSize = 14.sp, color = TestoTenue, modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (elementi.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Qui non ci sono foto. Le foto pubblicate restano disponibili per $GIORNI_HD giorni.",
                        color = TestoTenue, fontSize = 15.sp,
                    )
                }
            } else {
                LazyVerticalGrid(
                    GridCells.Fixed(3), Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(elementi, key = { it.id }) { e ->
                        val posto = scelte.indexOfFirst { it.id == e.id }
                        CellaIG(e, vm, if (posto >= 0) posto + 1 else null) {
                            if (posto >= 0) scelte.removeAt(posto) else scelte += e   // i numeri dopo scalano da soli
                        }
                    }
                }
            }
            Surface(color = Superficie, shadowElevation = 12.dp, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (preparo) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 3.dp, color = Azzurro)
                        Text("  Preparo le foto…", color = BluNotte, fontWeight = FontWeight.Bold)
                    }
                    if (scelte.size > MAX_CAROSELLO) Text("Nel carosello ne stanno al massimo $MAX_CAROSELLO: togline ${scelte.size - MAX_CAROSELLO}.", color = Arancione, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulsanteChiaro("↺ Svuota", "", { scelte.clear() }, Modifier.weight(0.9f), attivo = scelte.isNotEmpty() && !preparo, altezza = 52.dp, grandezzaTesto = 14)
                        PulsanteChiaro(
                            "Storie", "📱", { prepara(TipoIG.STORIE) }, Modifier.weight(1f),
                            attivo = scelte.isNotEmpty() && !preparo, altezza = 52.dp, grandezzaTesto = 14,
                        )
                        PulsanteGrande(
                            "Carosello", "▦", { prepara(TipoIG.CAROSELLO) }, Modifier.weight(1.1f),
                            attivo = scelte.isNotEmpty() && scelte.size <= MAX_CAROSELLO && !preparo, altezza = 52.dp, grandezzaTesto = 14,
                        )
                    }
                }
            }
        }
    }

    if (guida) SchermataGuide(Guide.STORIE) { guida = false }

    anteprima?.let { (tipo, file) ->
        AnteprimaIG(
            tipo, file, if (tipo == TipoIG.CAROSELLO) vm.didascaliaIG(scelte) else null,
            // Il carosello mandato si ricorda: se poi un articolo viene prenotato, il testo si rifà
            mandato = { indici ->
                // Il segno "caricata su Instagram" si mette da solo (si toglie a mano se poi non l'hai caricata)
                val canale = if (tipo == TipoIG.STORIE) Canale.STORIA_IG else Canale.POST_IG
                indici.mapNotNull { scelte.getOrNull(it) }.forEach { vm.segnaInstagram(it, canale) }
                if (tipo == TipoIG.CAROSELLO) vm.ricordaCarosello(scelte.toList())
            },
        ) { anteprima = null }
    }
}

/** Una foto nella griglia: quadrata, col numero se scelta. */
@Composable
private fun CellaIG(e: ElementoIG, vm: FotoViewModel, numero: Int?, tocca: () -> Unit) {
    // La miniatura: quella della lista se c'è, altrimenti la fotina dello storico (o la copia buona, ridotta)
    val daLista = remember(e.id) { vm.foto.firstOrNull { "L${it.numero}" == e.id }?.miniatura }
    val img by produceState(daLista, e.id) {
        if (value == null) value = withContext(Dispatchers.IO) { caricaRidotta(e.fotina?.takeIf { it.exists() } ?: e.file, 300) }
    }
    val scelta = numero != null
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(FondoTenue)
            .then(if (scelta) Modifier.border(4.dp, Rosa, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = tocca).testTag("ig_${e.id}"),
    ) {
        img?.let { Image(it, e.righe.firstOrNull()?.nome, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (numero != null) Box(
            Modifier.padding(6.dp).size(32.dp).clip(CircleShape).background(Color.White).border(2.dp, Rosa, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$numero", color = BluNotte, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
        // Prezzo in basso, per riconoscerla
        // Dove è già stata caricata (così non la rimandi due volte)
        val canali = vm.canaliDi(e)
        if (canali.isNotEmpty()) Text(
            Canale.entries.filter { it in canali }.joinToString(" ") { it.simbolo },
            fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                .background(Color(0xDDFFFFFF), RoundedCornerShape(8.dp)).padding(horizontal = 5.dp, vertical = 1.dp).testTag("canali_${e.id}"),
        )
        e.righe.firstOrNull()?.prezzo?.let { p ->
            Text(
                p, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * Anteprima di quello che arriva a Instagram, e il pulsante per aprirlo.
 * Storie: se Instagram ne prende una sola, "Una alla volta" le passa una per volta.
 */
@Composable
private fun AnteprimaIG(tipo: TipoIG, file: List<File>, didascalia: String?, mandato: (List<Int>) -> Unit, chiudi: () -> Unit) {
    val context = LocalContext.current
    var prossima by remember { mutableIntStateOf(-1) }   // -1 = tutte insieme; altrimenti la prossima da mandare una alla volta
    fun manda(quali: List<File>) {
        if (!CondividiInstagram.condividi(context, quali, didascalia))
            Toast.makeText(context, "Non riesco ad aprire Instagram", Toast.LENGTH_LONG).show()
    }
    Finestra(onDismissRequest = chiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(SfondoLista).systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Rosa, Cielo))).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsanteTondo("‹", chiudi)
                Text(
                    if (tipo == TipoIG.STORIE) "Anteprima storie (${file.size})" else "Anteprima carosello (${file.size})",
                    fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = BluNotte, modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                LazyRow(
                    Modifier.fillMaxWidth().height(if (tipo == TipoIG.STORIE) 420.dp else 330.dp).testTag("anteprima_ig"),
                    contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(file) { i, f ->
                        val img by produceState<ImageBitmap?>(null, f) { value = withContext(Dispatchers.IO) { caricaRidotta(f, 800) } }
                        Box(
                            Modifier.fillMaxHeight().aspectRatio(if (tipo == TipoIG.STORIE) 9f / 16f else 4f / 5f).clip(RoundedCornerShape(12.dp))
                                .background(FondoTenue)
                                .then(if (i == prossima) Modifier.border(3.dp, Verde, RoundedCornerShape(12.dp)) else Modifier),
                        ) { img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } }
                    }
                }
                if (didascalia != null) {
                    Text("Testo (già copiato: in Instagram tieni premuto e \"Incolla\")", fontWeight = FontWeight.Bold, color = BluNotte, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
                    Surface(color = Superficie, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, BordoScheda), modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        Text(didascalia, fontSize = 14.sp, modifier = Modifier.padding(12.dp).testTag("didascalia_ig"))
                    }
                } else {
                    Text(
                        "Instagram chiede dove: scegli \"Storie\". Se ne prende una sola, usa \"Una alla volta\".",
                        color = TestoTenue, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            Surface(color = Superficie, shadowElevation = 12.dp, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tipo == TipoIG.STORIE && file.size > 1) {
                        if (prossima < 0) {
                            PulsanteChiaro("Una alla volta", "1️⃣", { prossima = 0 }, Modifier.fillMaxWidth(), altezza = 48.dp, grandezzaTesto = 14)
                        } else if (prossima < file.size) {
                            PulsanteGrande(
                                "Storia ${prossima + 1} di ${file.size}", "📱",
                                { manda(listOf(file[prossima])); mandato(listOf(prossima)); prossima++ }, Modifier.fillMaxWidth(), colore = Verde, altezza = 52.dp, grandezzaTesto = 15,
                            )
                        } else {
                            Text("✓ Mandate tutte", color = Verde, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                        }
                    }
                    if (prossima < 0) PulsanteGrande(
                        "Apri Instagram", "📸", { manda(file); mandato(file.indices.toList()) }, Modifier.fillMaxWidth(), altezza = 56.dp, grandezzaTesto = 16,
                    )
                }
            }
        }
    }
}
