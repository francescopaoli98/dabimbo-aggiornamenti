package it.francesco.fotonegozio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cartellini veri (da foto-test/), con posizioni simili a quelle che restituisce ML Kit.
 * Layout: codice in alto a destra (altezza testo 20 px), descrizione sotto,
 * prezzo grande in basso a sinistra (altezza 40 px), taglia in basso a destra.
 */
class LettoreCartellinoTest {

    private fun r(testo: String, sx: Int, su: Int, dx: Int, giu: Int) = Riga(testo, sx, su, dx, giu)

    @Test
    fun felpa_due_righe_di_descrizione_e_taglia() {
        val dati = LettoreCartellino.analizza(listOf(
            r("A2954/14444", 300, 0, 420, 20),
            r("1444496", 300, 24, 380, 44),
            r("FELPA ZIP CAPP OKAIDI 8A RS", 20, 26, 290, 46),
            r("MARGH FELP 8A", 20, 52, 200, 72),
            r("€4,00", 20, 110, 130, 150),
            r("8A", 300, 125, 330, 145),
        ))
        assertEquals("1444496", dati.codice)
        assertEquals("FELPA ZIP CAPP OKAIDI 8A RS MARGH FELP 8A", dati.descrizione)
        assertEquals("€ 4,00", dati.prezzo)
        assertEquals("8A", dati.taglia)
    }

    @Test
    fun scarpe_taglia_numero() {
        val dati = LettoreCartellino.analizza(listOf(
            r("A2954/144448", 300, 0, 430, 20),
            r("1444488", 300, 24, 380, 44),
            r("SCARPE GINN DIADORA B LOGO", 20, 22, 290, 42),
            r("FUX brill lacci rs vell 31 31", 20, 50, 400, 70),
            r("€ 9,00", 20, 110, 140, 150),
            r("31", 310, 130, 330, 150),
        ))
        assertEquals("1444488", dati.codice)
        assertEquals("SCARPE GINN DIADORA B LOGO FUX brill lacci rs vell 31 31", dati.descrizione)
        assertEquals("€ 9,00", dati.prezzo)
        assertEquals("31", dati.taglia)
    }

    @Test
    fun scarpe_NR_34_e_cifre_del_codice_a_barre_ignorate() {
        val dati = LettoreCartellino.analizza(listOf(
            r("4295414444870", 20, -40, 280, -20),   // cifre sotto il codice a barre: da ignorare
            r("A2954/14444", 300, 0, 420, 20),
            r("1444487", 300, 24, 380, 44),
            r("SCARPE GINN RS DIS N GRI", 20, 26, 290, 46),
            r("ARTENGO NR 34 NR 34", 20, 52, 260, 72),
            r("€8,00", 20, 110, 130, 150),
            r("NR 34", 290, 130, 340, 150),
        ))
        assertEquals("SCARPE GINN RS DIS N GRI ARTENGO NR 34 NR 34", dati.descrizione)
        assertEquals("NR 34", dati.taglia)
    }

    @Test
    fun gioco_senza_taglia() {
        val dati = LettoreCartellino.analizza(listOf(
            r("A2073/14439", 300, 0, 420, 20),
            r("1443990", 300, 24, 380, 44),
            r("SET ANIMALI DINOS VI N AR", 20, 26, 290, 46),
            r("€2,50", 20, 110, 130, 150),
        ))
        assertEquals("1443990", dati.codice)
        assertEquals("SET ANIMALI DINOS VI N AR", dati.descrizione)
        assertEquals("€ 2,50", dati.prezzo)
        assertNull(dati.taglia)
    }

    @Test
    fun taglia_attaccata_alla_riga_del_prezzo() {
        val dati = LettoreCartellino.analizza(listOf(
            r("1444493", 300, 24, 380, 44),
            r("COPRISPALLA 7A PANNA NO", 20, 26, 290, 46),
            r("BOTT IDO 7A", 20, 52, 160, 72),
            r("€ 2,50          7A", 20, 110, 330, 150),
        ))
        assertEquals("COPRISPALLA 7A PANNA NO BOTT IDO 7A", dati.descrizione)
        assertEquals("€ 2,50", dati.prezzo)
        assertEquals("7A", dati.taglia)
    }

    @Test
    fun descrizione_spezzata_da_MLKit_sulla_stessa_riga() {
        // Il laccio rosso di Pluto copre parte del testo: ML Kit spezza la riga in due pezzi
        val dati = LettoreCartellino.analizza(listOf(
            r("1443983", 300, 24, 380, 44),
            r("pluto filo da tirare rss", 120, 53, 380, 73),
            r("ca", 20, 52, 45, 72),
            r("2,50", 40, 110, 130, 150),   // il "€" è coperto
        ))
        assertEquals("ca pluto filo da tirare rss", dati.descrizione)
        assertEquals("€ 2,50", dati.prezzo)
    }

    @Test
    fun prezzo_con_punto_e_euro_letto_come_E() {
        val dati = LettoreCartellino.analizza(listOf(
            r("1443982", 300, 24, 380, 44),
            r("porta banane plast", 20, 52, 230, 72),
            r("E1.00", 20, 110, 130, 150),
        ))
        assertEquals("€ 1,00", dati.prezzo)
        assertEquals("porta banane plast", dati.descrizione)
        assertNull(dati.taglia)
    }

    @Test
    fun taglia_con_barra() {
        val dati = LettoreCartellino.analizza(listOf(
            r("A2954/14444", 300, 0, 420, 20),
            r("1444490", 300, 24, 380, 44),
            r("MAGLIA ML N BRILL FILI ARG", 20, 26, 290, 46),
            r("PIAZZA ITALIA 7/8A 7/8A", 20, 52, 300, 72),
            r("€3,00", 20, 110, 130, 150),
            r("7/8A", 300, 128, 345, 148),
        ))
        assertEquals("MAGLIA ML N BRILL FILI ARG PIAZZA ITALIA 7/8A 7/8A", dati.descrizione)
        assertEquals("7/8A", dati.taglia)
    }

    @Test
    fun testo_fuori_dal_cartellino_ignorato() {
        // Etichetta del capo (es. "Okaidi 8 ANS") vicino al cartellino: fuori dalla zona, non deve finire nella descrizione
        val dati = LettoreCartellino.analizza(listOf(
            r("1444496", 300, 24, 380, 44),
            r("FELPA ZIP CAPP", 20, 26, 290, 46),
            r("€4,00", 20, 110, 130, 150),
            r("Okaidi", 700, 60, 800, 90),       // troppo a destra
            r("8 ANS 128cm", 20, 400, 150, 420), // troppo in basso
        ))
        assertEquals("FELPA ZIP CAPP", dati.descrizione)
        assertNull(dati.taglia)
    }

    /** Parola per parola, come arrivano ora da ML Kit (posizioni misurate sul cartellino delle scarpe Diadora). */
    private fun parole(testo: String, sx: Int, su: Int, giu: Int, larghezzaLettera: Int = 22): List<Riga> {
        var x = sx
        return testo.split(" ").map { p ->
            val dx = x + p.length * larghezzaLettera
            Riga(p, x, su, dx, giu).also { x = dx + larghezzaLettera }
        }
    }

    @Test
    fun scarpe_parola_per_parola_taglia_separata() {
        val righe = parole("A2954/14444", 650, 385, 425) +
            parole("1444488", 650, 455, 495, 26) +
            parole("SCARPE GINN DIADORA B LOGO", 85, 445, 482) +
            parole("FUX brill lacci rs vell 31 31", 70, 520, 560) +
            parole("€", 45, 625, 680) + parole("9,00", 110, 600, 690, 50) +
            parole("31", 660, 668, 705)
        val dati = LettoreCartellino.analizza(righe)
        assertEquals("1444488", dati.codice)
        assertEquals("SCARPE GINN DIADORA B LOGO FUX brill lacci rs vell 31 31", dati.descrizione)
        assertEquals("€ 9,00", dati.prezzo)
        assertEquals("31", dati.taglia)
    }

    @Test
    fun parole_della_stessa_riga_un_po_sfalsate() {
        // Cartellino non perfettamente dritto: le parole della stessa riga non hanno la stessa altezza
        val righe = listOf(
            r("1444490", 650, 455, 830, 495),
            r("MAGLIA", 80, 440, 200, 478), r("ML", 220, 446, 270, 484), r("N", 290, 450, 310, 488),
            r("PIAZZA", 80, 515, 200, 553), r("ITALIA", 220, 520, 330, 558), r("7/8A", 350, 525, 420, 563),
            r("€3,00", 45, 600, 320, 690),
            r("7/8A", 650, 665, 730, 705),
        )
        val dati = LettoreCartellino.analizza(righe)
        assertEquals("MAGLIA ML N PIAZZA ITALIA 7/8A", dati.descrizione)
        assertEquals("7/8A", dati.taglia)
    }

    @Test
    fun felpa_taglia_piu_in_alto_del_prezzo_sulla_riga_della_descrizione() {
        // Sul cartellino vero della felpa la taglia "8A" sta quasi all'altezza di "MARGH FELP 8A"
        val righe = parole("1444496", 650, 455, 495, 26) +
            parole("FELPA ZIP CAPP OKAIDI 8A RS", 85, 445, 482) +
            parole("MARGH FELP 8A", 85, 520, 558) +
            parole("8A", 700, 512, 550) +
            parole("€4,00", 45, 575, 660, 50)
        val dati = LettoreCartellino.analizza(righe)
        assertEquals("FELPA ZIP CAPP OKAIDI 8A RS MARGH FELP 8A", dati.descrizione)
        assertEquals("€ 4,00", dati.prezzo)
        assertEquals("8A", dati.taglia)
    }

    @Test
    fun scarpe_taglia_NR_34_su_riga_propria_tra_descrizione_e_prezzo() {
        val righe = parole("1444487", 650, 455, 495, 26) +
            parole("SCARPE GINN RS DIS N GRI", 85, 445, 482) +
            parole("ARTENGO NR 34 NR 34", 85, 520, 558) +
            parole("NR 34", 640, 580, 618) +
            parole("€8,00", 45, 590, 680, 50)
        val dati = LettoreCartellino.analizza(righe)
        assertEquals("SCARPE GINN RS DIS N GRI ARTENGO NR 34 NR 34", dati.descrizione)
        assertEquals("NR 34", dati.taglia)
    }

    @Test
    fun maglione_spazi_normali_tra_parole_non_creano_una_taglia() {
        // Errore visto sul telefono: "ARG" e "7/8A 7/8A" finivano nella taglia
        val righe = parole("1444490", 650, 455, 495, 26) +
            parole("MAGLIA ML N BRILL FILI", 85, 445, 482) + parole("ARG", 560, 446, 483) +   // spazio un po' largo
            parole("PIAZZA ITALIA", 85, 520, 558) + parole("7/8A 7/8A", 470, 521, 559) +      // spazio un po' largo
            parole("7/8A", 690, 575, 613) +
            parole("€3,00", 45, 585, 650, 40)
        val dati = LettoreCartellino.analizza(righe)
        assertEquals("MAGLIA ML N BRILL FILI ARG PIAZZA ITALIA 7/8A 7/8A", dati.descrizione)
        assertEquals("7/8A", dati.taglia)
    }

    @Test
    fun coprispalla_fine_della_prima_riga_resta_nella_descrizione() {
        val righe = parole("1444493", 650, 455, 495, 26) +
            parole("COPRISPALLA 7A", 85, 445, 482) + parole("PANNA NO", 470, 447, 484) +
            parole("BOTT IDO 7A", 85, 520, 558) +
            parole("7A", 700, 560, 598) +
            parole("€2,50", 45, 570, 650, 40)
        val dati = LettoreCartellino.analizza(righe)
        assertEquals("COPRISPALLA 7A PANNA NO BOTT IDO 7A", dati.descrizione)
        assertEquals("7A", dati.taglia)
    }

    @Test
    fun felpa_vera_taglia_sulla_riga_di_MARGH_poco_a_sinistra_del_codice() {
        // Misurato sulla foto della felpa: "8A" sulla riga di "MARGH FELP 8A", spazio ~5 volte il testo,
        // inizia un po' a sinistra del codice
        val righe = parole("1444496", 650, 455, 495, 26) +
            parole("FELPA ZIP CAPP OKAIDI 8A RS", 85, 445, 482) +
            parole("MARGH FELP 8A", 85, 520, 558) +
            parole("8A", 600, 518, 556) +
            parole("€4,00", 45, 575, 660, 50)
        val dati = LettoreCartellino.analizza(righe)
        assertEquals("FELPA ZIP CAPP OKAIDI 8A RS MARGH FELP 8A", dati.descrizione)
        assertEquals("8A", dati.taglia)
    }

    @Test
    fun librottino_frozen_II_poco_sotto_il_codice_resta_descrizione() {
        // Errore visto sul telefono: "II" finiva nella taglia
        val righe = parole("1444112", 650, 455, 495, 26) +
            parole("LIBROTTINO FROZEN", 85, 468, 506) + parole("II", 560, 470, 508) +
            parole("€1,00", 45, 560, 640, 45)
        val dati = LettoreCartellino.analizza(righe)
        assertEquals("LIBROTTINO FROZEN II", dati.descrizione)
        assertNull(dati.taglia)
    }

    @Test
    fun prezzo_spezzato_in_due_parole() {
        // Errore visto sul telefono: "e 2, 00" finiva nella descrizione
        val righe = parole("1444092", 650, 455, 495, 26) +
            parole("ciuchino shreck", 85, 445, 482) +
            parole("peluche", 85, 520, 558) +
            parole("e", 45, 610, 650, 30) + Riga("2,", 90, 590, 160, 670) + Riga("00", 175, 590, 260, 670)
        val dati = LettoreCartellino.analizza(righe)
        assertEquals("ciuchino shreck peluche", dati.descrizione)
        assertEquals("€ 2,00", dati.prezzo)
    }

    @Test
    fun nessun_codice_nessun_dato() {
        val dati = LettoreCartellino.analizza(listOf(r("Okaidi", 0, 0, 50, 20)))
        assertNull(dati.codice)
        assertNull(dati.descrizione)
    }
}
