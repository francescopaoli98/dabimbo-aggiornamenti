package it.francesco.fotonegozio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelManualeTest {

    @Test
    fun quadretto_diventa_del_colore_medio_e_il_resto_non_cambia() {
        val w = 20; val h = 10; val lato = 10
        // metà sinistra: righe alternate bianche e nere; metà destra: rossa
        val px = IntArray(w * h) { i -> val x = i % w; val y = i / w
            if (x < 10) (if (y % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()) else 0xFFFF0000.toInt() }
        PixelManuale.applica(px, w, h, setOf(PixelManuale.cella(0, 0)), lato)
        val grigio = px[0] and 0xFFFFFF
        assertEquals("tutto il quadretto uguale", px[0], px[9 * w + 9])
        assertTrue("grigio medio", (grigio shr 16) in 120..135)
        assertEquals("il resto non cambia", 0xFFFF0000.toInt(), px[5 * w + 15])
    }

    @Test
    fun pennello_tocca_i_quadretti_vicini() {
        val celle = PixelManuale.celleAttorno(50f, 50f, raggio = 15f, lato = 10, larghezza = 200, altezza = 200)
        assertTrue(PixelManuale.cella(5, 5) in celle)
        assertTrue(PixelManuale.cella(4, 4) in celle)
        assertTrue(PixelManuale.cella(9, 9) !in celle)
    }

    @Test
    fun pennello_sul_bordo_non_esce_dalla_foto() {
        val celle = PixelManuale.celleAttorno(0f, 0f, raggio = 30f, lato = 10, larghezza = 100, altezza = 100)
        assertTrue(celle.all { PixelManuale.colonna(it) >= 0 && PixelManuale.riga(it) >= 0 })
    }
}
