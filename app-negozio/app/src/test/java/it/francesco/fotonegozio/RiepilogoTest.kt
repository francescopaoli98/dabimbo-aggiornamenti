package it.francesco.fotonegozio

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)   // serve per org.json
class RiepilogoTest {
    private val felpa = DatiCartellino("1444496", "FELPA", "€ 4,00", "8A")
    private val libro = DatiCartellino("1444115", "LIBROTTINO", "€ 1,50", null)

    @Test
    fun prezzi_in_centesimi() {
        assertEquals(400, Riepilogo.centesimi("€ 4,00"))
        assertEquals(150, Riepilogo.centesimi("€ 1,5"))
        assertEquals(1200, Riepilogo.centesimi("€ 12"))
        assertEquals("€ 84,50", Riepilogo.euro(8450))
        assertEquals("€ 0,05", Riepilogo.euro(5))
    }

    @Test
    fun conta_ogni_articolo_una_volta_al_giorno() {
        var r = Riepilogo.aggiungi(emptyList(), "2026-10-06", listOf(felpa, libro))
        r = Riepilogo.aggiungi(r, "2026-10-06", listOf(felpa))          // ripubblicata: non conta
        r = Riepilogo.aggiungi(r, "2026-10-07", listOf(felpa))          // giorno dopo: conta
        val g = Riepilogo.giornate(r)
        assertEquals(Giornata("2026-10-07", 1, 400), g[0])
        assertEquals(Giornata("2026-10-06", 2, 550), g[1])
        // Si salva e si rilegge uguale
        assertEquals(r, Riepilogo.leggi(Riepilogo.scrivi(r)))
    }
}
