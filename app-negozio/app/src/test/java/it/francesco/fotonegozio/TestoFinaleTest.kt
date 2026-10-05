package it.francesco.fotonegozio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Con il dizionario vero (assets/dizionario.txt) e i cartellini veri delle foto di prova. */
class TestoFinaleTest {

    private val voci = Dizionario.leggi(File("src/main/assets/dizionario.txt").readText())
    private fun riga(desc: String, taglia: String?, prezzo: String, codice: String) =
        TestoFinale.riga(DatiCartellino(codice, desc, prezzo, taglia), voci)

    @Test
    fun dizionario_letto() {
        assertTrue(voci.size > 40)
        assertEquals(listOf("grigio", "grigia", "grigi", "grigie"), voci.first { it.sigla == "gri" }.significati)
        assertTrue(voci.first { it.sigla == "primark" }.daTogliere)
    }

    @Test
    fun felpa_esempio_dello_schema() {
        assertEquals(
            "Felpa zip con cappuccio Okaidi rosa margherite felpata - 8 anni - cod. 1444496 - € 4,00",
            riga("FELPA ZIP CAPP OKAIDI 8A RS MARGH FELP 8A", "8A", "€ 4,00", "1444496"),
        )
    }

    @Test
    fun maglia_femminile() {
        assertEquals(
            "Maglia manica lunga nera brillantini fili argento Piazza Italia - 7/8 anni - cod. 1444490 - € 3,00",
            riga("MAGLIA ML N BRILL FILI ARG PIAZZA ITALIA 7/8A 7/8A", "7/8A", "€ 3,00", "1444490"),
        )
    }

    @Test
    fun giubbotto_maschile() {
        assertEquals(
            "Giubbotto bomber nero sopra argento Blukids - 8/9 anni - cod. 1444485 - € 8,00",
            riga("GIUBB BOMBER 8/9A N SOPRA ARG BLUKIDS 8/9A", "8/9A", "€ 8,00", "1444485"),
        )
    }

    @Test
    fun scarpe_plurale_e_numero() {
        assertEquals(
            "Scarpe ginnastica Diadora bianche logo fucsie brillantini lacci rosa velluto - numero 31 - cod. 1444488 - € 9,00",
            riga("SCARPE GINN DIADORA B LOGO FUX brill lacci rs vell 31 31", "31", "€ 9,00", "1444488"),
        )
        assertEquals(
            "Scarpe ginnastica rosa disegno nere grigie Artengo - numero 34 - cod. 1444487 - € 8,00",
            riga("SCARPE GINN RS DIS N GRI ARTENGO NR 34 NR 34", "NR 34", "€ 8,00", "1444487"),
        )
    }

    @Test
    fun coprispalla_e_maschile_anche_se_finisce_in_a() {
        assertEquals(
            "Coprispalla panna no bottoni Ido - 7 anni - cod. 1444493 - € 2,50",
            riga("COPRISPALLA 7A PANNA NO BOTT IDO 7A", "7A", "€ 2,50", "1444493"),
        )
    }

    @Test
    fun gioco_senza_taglia() {
        assertEquals(
            "Set animali dinosauri viola nero arancione - cod. 1443990 - € 2,50",
            riga("SET ANIMALI DINOS VI N AR", null, "€ 2,50", "1443990"),
        )
    }

    @Test
    fun primark_tolto_dal_testo() {
        assertEquals("Maglia rosa - 5/6 anni - cod. 1444000 - € 2,00",
            riga("MAGLIA RS PRIMARK 5/6A", "5/6A", "€ 2,00", "1444000"))
    }

    @Test
    fun piu_articoli_una_riga_ciascuno() {
        val testo = TestoFinale.testo(listOf(
            DatiCartellino("1444112", "LIBROTTINO FROZEN II", "€ 1,00", null),
            DatiCartellino("1444113", "LIBROTTINO BAMBI", "€ 1,50", null),
        ), voci)
        assertEquals("Librottino frozen II - cod. 1444112 - € 1,00\nLibrottino bambi - cod. 1444113 - € 1,50", testo)
    }

    @Test
    fun dizionario_si_riscrive_e_rilegge_uguale() {
        assertEquals(voci.sortedBy { it.sigla }, Dizionario.leggi(Dizionario.scrivi(voci)))
    }
}
