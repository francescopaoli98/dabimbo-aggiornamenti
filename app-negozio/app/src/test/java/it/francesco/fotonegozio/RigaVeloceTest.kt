package it.francesco.fotonegozio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RigaVeloceTest {

    private fun leggi(riga: String) = RigaVeloce.analizza(riga)

    @Test
    fun librottino() {
        val d = leggi("1444115 librottino inside out 1,50")
        assertEquals("1444115", d.codice)
        assertEquals("librottino inside out", d.descrizione)
        assertEquals("€ 1,50", d.prezzo)
        assertNull(d.taglia)
    }

    @Test
    fun felpa_con_taglia_in_mezzo_e_codice_in_fondo() {
        val d = leggi("felpa zip capp okaidi rosa 8A 4,00 1444496")
        assertEquals("1444496", d.codice)
        assertEquals("felpa zip capp okaidi rosa", d.descrizione)
        assertEquals("€ 4,00", d.prezzo)
        assertEquals("8A", d.taglia)
    }

    @Test
    fun taglia_con_barra_e_prezzo_col_simbolo() {
        val d = leggi("1444490 maglia ml nera 7/8A €3")
        assertEquals("maglia ml nera", d.descrizione)
        assertEquals("€ 3,00", d.prezzo)
        assertEquals("7/8A", d.taglia)
    }

    @Test
    fun scarpe_numero() {
        val d = leggi("1444487 scarpe ginn rosa nr 34 8,00")
        assertEquals("scarpe ginn rosa", d.descrizione)
        assertEquals("NR 34", d.taglia)
        assertEquals("€ 8,00", d.prezzo)
    }

    @Test
    fun dettato_a_voce() {
        // Il microfono della tastiera scrive le parole per esteso
        val d = leggi("1444496 felpa rosa con cappuccio 8 anni 4 euro")
        assertEquals("felpa rosa con cappuccio", d.descrizione)
        assertEquals("8A", d.taglia)
        assertEquals("€ 4,00", d.prezzo)
    }

    @Test
    fun mesi_e_prezzo_col_punto() {
        val d = leggi("body neonato 18 mesi 2.5")
        assertNull(d.codice)
        assertEquals("body neonato", d.descrizione)
        assertEquals("18M", d.taglia)
        assertEquals("€ 2,50", d.prezzo)
    }

    @Test
    fun numeri_normali_restano_nella_descrizione() {
        val d = leggi("puzzle 100 pezzi 3,00")
        assertEquals("puzzle 100 pezzi", d.descrizione)
        assertNull(d.taglia)
        assertEquals("€ 3,00", d.prezzo)
    }
}
