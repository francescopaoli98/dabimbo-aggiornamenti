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

    @Test
    fun prenotati_e_venduti_nei_totali() {
        var r = Riepilogo.aggiungi(emptyList(), "2026-10-06", listOf(felpa, libro), "m_1.jpg")
        assertEquals("m_1.jpg", r[0].miniatura)
        r = Riepilogo.cambia(r, r[0]) { it.copy(prenotato = true) }
        r = Riepilogo.cambia(r, r[1]) { it.copy(venduto = true) }
        assertEquals(Giornata("2026-10-06", 2, 550, 1, 400, 1, 150), Riepilogo.giornate(r)[0])
        // Annulla prenotazione
        r = Riepilogo.cambia(r, r[0]) { it.copy(prenotato = false) }
        assertEquals(0, Riepilogo.giornate(r)[0].prenotati)
        assertEquals(r, Riepilogo.leggi(Riepilogo.scrivi(r)))
    }

    @Test
    fun legge_il_registro_vecchio() {
        // Il file della 4.7 non ha fotina né spunte
        val r = Riepilogo.leggi("""[{"g":"2026-10-06","k":"1444496","c":400,"n":"Felpa"}]""")
        assertEquals(listOf(Pubblicato("2026-10-06", "1444496", 400, "Felpa")), r)
    }

    @Test
    fun la_fotina_arriva_anche_dopo() {
        // Pubblicato con la versione vecchia (senza fotina), poi ripubblicato lo stesso giorno: prende la fotina, conta sempre 1
        var r = Riepilogo.aggiungi(emptyList(), "2026-10-06", listOf(felpa))
        r = Riepilogo.aggiungi(r, "2026-10-06", listOf(felpa), "m_2.jpg")
        assertEquals(1, r.size)
        assertEquals("m_2.jpg", r[0].miniatura)
        // Una fotina già presente non si cambia
        r = Riepilogo.aggiungi(r, "2026-10-06", listOf(felpa), "m_3.jpg")
        assertEquals("m_2.jpg", r[0].miniatura)
    }

    @Test
    fun copia_buona_e_taglia() {
        val r = Riepilogo.aggiungi(emptyList(), "2026-10-06", listOf(felpa.copy(taglia = "8 anni")), "m_1.jpg", "h_1.jpg")
        assertEquals("h_1.jpg", r[0].foto)
        assertEquals("8 anni", r[0].taglia)
        assertEquals(r, Riepilogo.leggi(Riepilogo.scrivi(r)))
        // Ripubblicato con la versione vecchia (senza copia buona): la prende ora
        var v = Riepilogo.aggiungi(emptyList(), "2026-10-06", listOf(felpa))
        v = Riepilogo.aggiungi(v, "2026-10-06", listOf(felpa), "m_2.jpg", "h_2.jpg")
        assertEquals("h_2.jpg", v[0].foto)
    }

    @Test
    fun caroselli_salvati() {
        val c = listOf(CaroselloIG("2026-10-08", listOf(listOf(RigaIG("Felpa", "8 anni", "1444496", "€ 4,00")), listOf(RigaIG("Libro")))))
        assertEquals(c, CaroselliSalvati.leggi(CaroselliSalvati.scrivi(c)))
        assertEquals(emptyList<CaroselloIG>(), CaroselliSalvati.leggi("rotto"))
    }
}
