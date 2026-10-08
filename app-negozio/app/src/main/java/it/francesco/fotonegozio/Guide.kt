package it.francesco.fotonegozio

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/** Una pagina di guida: la schermata vera dell'app col punto da toccare cerchiato (o un simbolo grande) e una frase. */
data class PaginaGuida(@DrawableRes val immagine: Int?, val testo: String, val simbolo: String = "")

/** Una guida: tante pagine da sfogliare. */
data class Guida(val id: String, val icona: String, val titolo: String, val pagine: List<PaginaGuida>)

/** Le guide dell'app (le schermate le fa GuideTest con foto vere: vedi res/drawable-nodpi/guida_*.webp). */
object Guide {
    val WHATSAPP = Guida(
        "whatsapp", "📤", "Pubblicare su WhatsApp", listOf(
            PaginaGuida(R.drawable.guida_wa_1, "Tocca «Scegli foto» e seleziona dalla Galleria le foto degli articoli. Puoi sceglierne tante insieme."),
            PaginaGuida(R.drawable.guida_wa_2, "L'app raddrizza ogni foto e legge il cartellino. Sotto la foto c'è il testo che andrà nello stato: se c'è un errore, toccalo per correggerlo."),
            PaginaGuida(R.drawable.guida_wa_3, "Se compare «⚠ Da controllare», leggi la scritta arancione: ti dice cosa sistemare (per esempio il verso della foto)."),
            PaginaGuida(R.drawable.guida_wa_4, "Quando è tutto a posto, tocca «Pubblica la prossima»: si apre WhatsApp con la foto e il testo già pronti."),
            PaginaGuida(null, "In WhatsApp scegli «Il mio stato» e premi Invia. Se il testo non c'è, tieni premuto e scegli «Incolla»: l'app l'ha già copiato.", "📱"),
            PaginaGuida(R.drawable.guida_wa_5, "Torna nell'app: la foto pubblicata si chiude e qui vedi quante ne hai fatte. Tocca di nuovo «Pubblica la prossima» per la foto dopo."),
            PaginaGuida(R.drawable.guida_wa_segni, "Questi segni dicono dove hai caricato la foto: 🟢 WhatsApp, 📱 Storia Instagram, ▦ Post Instagram. Si accendono da soli; se poi non l'hai caricata davvero, toccali per spegnerli (o per accenderli a mano)."),
        )
    )
    val FOTO = Guida(
        "foto", "🔍", "Sistemare una foto", listOf(
            PaginaGuida(R.drawable.guida_foto_1, "Tocca la foto (🔍) per aprirla grande. Con due dita la ingrandisci."),
            PaginaGuida(R.drawable.guida_foto_2, "Se è storta, girala con «Sinistra», «Destra» o «Capovolgi»."),
            PaginaGuida(R.drawable.guida_foto_3, "«Sfondo automatico» copre lo sfondo con dei quadrettoni; con «Pixel a mano» li metti tu col dito. «↺ Togli» la rimette com'era."),
            PaginaGuida(R.drawable.guida_foto_4, "Tocca «Ritaglia» per tagliare via i bordi."),
            PaginaGuida(R.drawable.guida_foto_5, "Trascina gli angoli, oppure scegli un formato: 4:5 è perfetto per Instagram, 9:16 per lo stato a tutto schermo. Poi tocca «Salva»."),
            PaginaGuida(R.drawable.guida_foto_6, "Tocca «Testo» per correggere descrizione, prezzo o taglia."),
        )
    )
    val STORICO = Guida(
        "storico", "📌", "Storico e prenotati", listOf(
            PaginaGuida(R.drawable.guida_storico_1, "Tocca «📊 Oggi» per vedere cosa hai pubblicato."),
            PaginaGuida(R.drawable.guida_storico_2, "Qui ci sono i giorni: tocca un giorno per vedere i suoi articoli."),
            PaginaGuida(R.drawable.guida_storico_segni, "Ogni articolo ha i suoi segni: 🟢 WhatsApp, 📱 Storia IG, ▦ Post IG. Toccali per metterli o toglierli. In alto vedi quanti ne hai caricati per ognuno."),
            PaginaGuida(R.drawable.guida_storico_3, "Quando qualcuno prenota un articolo, tocca «📌 Prenotato» accanto a quell'articolo."),
            PaginaGuida(R.drawable.guida_storico_4, "L'app non può togliere da sola la storia già pubblicata: tocca «WhatsApp» o «Instagram» e cancellala tu."),
            PaginaGuida(R.drawable.guida_storico_5, "Se vuoi, pubblica la storia «PRENOTATO»: la stessa foto con una fascia rosa. Chi la vede sa che l'articolo è andato."),
            PaginaGuida(R.drawable.guida_storico_6, "In alto vedi quanti articoli sono prenotati e quanto valgono. Se ti sei sbagliata, tocca «Annulla prenotazione»."),
            PaginaGuida(R.drawable.guida_storico_7, "Per togliere un articolo contato per sbaglio, accendi «Sblocca cancellazione»: accanto agli articoli compare la ✕."),
        )
    )
    val STORIE = Guida(
        "storie", "📱", "Storie Instagram", listOf(
            PaginaGuida(R.drawable.guida_storie_1, "Tocca «📸 Instagram: storie e carosello»."),
            PaginaGuida(R.drawable.guida_storie_2, "Scegli da dove prendere le foto: la lista di adesso, oppure un giorno passato (fino a $GIORNI_HD giorni)."),
            PaginaGuida(R.drawable.guida_storie_3, "Tocca le foto nell'ordine in cui le vuoi: compare il numero. Se ne tocchi una scelta, la togli."),
            PaginaGuida(R.drawable.guida_storie_4, "Tocca «Storie»: sotto ogni foto l'app scrive descrizione, taglia, codice e prezzo."),
            PaginaGuida(R.drawable.guida_storie_5, "Guarda l'anteprima e tocca «Apri Instagram». Instagram chiede dove mandarle: scegli «Storie»."),
            PaginaGuida(R.drawable.guida_storie_6, "Se Instagram ne prende una sola, torna qui e tocca «Una alla volta»: le mandi una per volta."),
        )
    )
    val CAROSELLO = Guida(
        "carosello", "▦", "Carosello Instagram", listOf(
            PaginaGuida(R.drawable.guida_carosello_1, "Nella pagina Instagram tocca le foto nell'ordine giusto: il numero sulla foto sarà lo stesso nel testo."),
            PaginaGuida(R.drawable.guida_carosello_2, "Tocca «Carosello» (al massimo 20 foto)."),
            PaginaGuida(R.drawable.guida_carosello_3, "Questo è il testo del post, con i numeri e gli hashtag: è già copiato."),
            PaginaGuida(R.drawable.guida_carosello_4, "Tocca «Apri Instagram» e scegli «Post» (o «Feed»)."),
            PaginaGuida(null, "In Instagram vai avanti fino alla descrizione, tieni premuto e scegli «Incolla». Poi tocca «Condividi».", "📋"),
            PaginaGuida(R.drawable.guida_carosello_6, "Se poi un articolo del carosello viene prenotato: nello storico tocca «📣 Storie e testo», poi «Copia il testo aggiornato». In Instagram apri il post, «Modifica» e incolla."),
        )
    )

    val TUTTE = listOf(WHATSAPP, FOTO, STORICO, STORIE, CAROSELLO)
}

/** L'elenco delle guide; [iniziale] = una guida da aprire subito. */
@Composable
fun SchermataGuide(iniziale: Guida? = null, chiudi: () -> Unit) {
    var aperta by remember { mutableStateOf(iniziale) }
    // Aperta direttamente una guida: "indietro" chiude tutto
    val soloQuella = iniziale != null
    val g = aperta
    if (g != null) {
        // key: guida nuova = si riparte dalla prima pagina
        key(g.id) { PagineGuida(g, chiudi = { if (soloQuella) chiudi() else aperta = null }, prossima = Guide.TUTTE.getOrNull(Guide.TUTTE.indexOf(g) + 1)) { aperta = it } }
        return
    }
    Finestra(onDismissRequest = chiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(SfondoLista).systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Rosa, Cielo))).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("❓ Guide", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = BluNotte, modifier = Modifier.weight(1f))
                PulsanteTondo("✕", chiudi)
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Scegli cosa vuoi imparare: ti faccio vedere i passaggi uno alla volta.", color = TestoTenue, fontSize = 15.sp) }
                items(Guide.TUTTE) { guida ->
                    Surface(
                        onClick = { aperta = guida }, color = Superficie, shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, BordoScheda), modifier = Modifier.testTag("guida_${guida.id}"),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Cielo), contentAlignment = Alignment.Center) { Text(guida.icona, fontSize = 22.sp) }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(guida.titolo, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = BluNotte)
                                Text("${guida.pagine.size} passaggi", fontSize = 13.sp, color = TestoTenue)
                            }
                            Text("›", fontSize = 22.sp, color = BluNotte)
                        }
                    }
                }
            }
        }
    }
}

/** Una guida aperta: le pagine si sfogliano col dito o con «Avanti». */
@Composable
private fun PagineGuida(g: Guida, chiudi: () -> Unit, prossima: Guida?, apri: (Guida) -> Unit) {
    val stato = rememberPagerState { g.pagine.size }
    val scope = rememberCoroutineScope()
    val ultima = stato.currentPage == g.pagine.size - 1
    Finestra(onDismissRequest = chiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(SfondoLista).systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Rosa, Cielo))).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${g.icona} ${g.titolo}", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = BluNotte, modifier = Modifier.weight(1f))
                PulsanteTondo("✕", chiudi)
            }
            HorizontalPager(stato, Modifier.weight(1f).fillMaxWidth().testTag("pagine_guida")) { i ->
                val p = g.pagine[i]
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (p.immagine != null) {
                            Image(
                                painterResource(p.immagine), p.testo,
                                Modifier.fillMaxHeight().aspectRatio(600f / 1300f).shadow(6.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Box(Modifier.size(150.dp).clip(CircleShape).background(Cielo), contentAlignment = Alignment.Center) { Text(p.simbolo, fontSize = 72.sp) }
                        }
                    }
                    Text(
                        "${i + 1}. ${p.testo}", fontSize = 17.sp, color = Testo, lineHeight = 23.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp).heightIn(min = 92.dp),
                    )
                }
            }
            // Pallini: a che pagina sei
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
                repeat(g.pagine.size) { i ->
                    Box(Modifier.padding(3.dp).size(if (i == stato.currentPage) 10.dp else 7.dp).clip(CircleShape).background(if (i == stato.currentPage) Azzurro else BordoScheda))
                }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PulsanteChiaro(
                    "‹ Indietro", "", { scope.launch { stato.animateScrollToPage(stato.currentPage - 1) } }, Modifier.weight(1f),
                    attivo = stato.currentPage > 0, altezza = 52.dp, grandezzaTesto = 15,
                )
                when {
                    !ultima -> PulsanteGrande("Avanti ›", "", { scope.launch { stato.animateScrollToPage(stato.currentPage + 1) } }, Modifier.weight(1f), altezza = 52.dp, grandezzaTesto = 15)
                    prossima != null -> PulsanteGrande("${prossima.titolo} ›", "", { apri(prossima) }, Modifier.weight(1.4f), colore = Verde, altezza = 52.dp, grandezzaTesto = 14)
                    else -> PulsanteGrande("Fine ✓", "", chiudi, Modifier.weight(1f), colore = Verde, altezza = 52.dp, grandezzaTesto = 15)
                }
            }
            if (ultima && prossima != null) TextButton(onClick = chiudi, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Ho finito") }
        }
    }
}

/** Striscia "Prima volta qui?" che propone la guida (sparisce per sempre con "Non ora" o aprendo la guida). */
@Composable
fun ProponiGuida(g: Guida, vista: Boolean, segnaVista: () -> Unit, apri: () -> Unit) {
    if (vista) return
    Surface(color = Superficie, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.5.dp, Azzurro), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("❓ Prima volta qui?", fontWeight = FontWeight.Bold, color = BluNotte, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { segnaVista(); apri() }) { Text("Guarda la guida") }
            TextButton(onClick = segnaVista) { Text("Non ora", color = TestoTenue) }
        }
    }
}
