package it.francesco.fotonegozio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrovaEtichetteTest {

    private val GRIGIO = 0xFF8090A0.toInt()   // tavolo azzurrino
    private val BIANCO = 0xFFF0F0EE.toInt()
    private val NERO = 0xFF101010.toInt()

    private fun immagine(w: Int, h: Int) = IntArray(w * h) { GRIGIO }
    private fun IntArray.rettangolo(w: Int, sx: Int, su: Int, dx: Int, giu: Int, colore: Int) {
        for (y in su until giu) for (x in sx until dx) this[y * w + x] = colore
    }

    @Test
    fun etichetta_con_codice_a_barre_viene_trovata() {
        val w = 400; val h = 300
        val px = immagine(w, h)
        px.rettangolo(w, 100, 100, 180, 140, BIANCO)
        for (x in 110 until 150 step 4) px.rettangolo(w, x, 105, x + 2, 120, NERO)   // codice a barre
        val trovate = TrovaEtichette.trova(px, w, h)
        assertEquals(1, trovate.size)
        val r = trovate[0]
        assertTrue(r.sx in 98..102 && r.su in 98..102 && r.dx in 178..182 && r.giu in 138..142)
    }

    @Test
    fun zona_bianca_senza_niente_dentro_ignorata() {
        val w = 400; val h = 300
        val px = immagine(w, h)
        px.rettangolo(w, 100, 100, 180, 140, BIANCO)   // muro / tavolo bianco: nessun nero dentro
        assertEquals(0, TrovaEtichette.trova(px, w, h).size)
    }

    @Test
    fun due_etichette_due_risultati() {
        val w = 400; val h = 300
        val px = immagine(w, h)
        for (sx in listOf(40, 240)) {
            px.rettangolo(w, sx, 100, sx + 80, 140, BIANCO)
            for (x in sx + 10 until sx + 50 step 4) px.rettangolo(w, x, 105, x + 2, 120, NERO)
        }
        assertEquals(2, TrovaEtichette.trova(px, w, h).size)
    }

    @Test
    fun miglioramento_aumenta_il_contrasto() {
        // Cartellino sbiadito: grigio chiaro su grigio un po' più chiaro
        val w = 20; val h = 20
        val px = IntArray(w * h) { if (it % w < 10) 0xFFB0B0B0.toInt() else 0xFFC8C8C8.toInt() }
        val out = Miglioramento.migliora(px, w, h)
        val sinistra = out[5 * w + 3] and 255
        val destra = out[5 * w + 15] and 255
        assertTrue("prima 24 di differenza, dopo $sinistra vs $destra", destra - sinistra > 200)
    }
}
