package it.francesco.fotonegozio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersoOggettoTest {
    private val cartellino = VersoOggetto.paroleDi(listOf("GIOCO SCAT NUMERI DA 1 A 10"))

    @Test
    fun le_scritte_del_cartellino_non_contano() {
        assertEquals(0f, VersoOggetto.peso("€ 3,50", 0.9f, cartellino))
        assertEquals(0f, VersoOggetto.peso("A1291/144410", 0.9f, cartellino))
        assertEquals(0f, VersoOggetto.peso("1444108", 0.9f, cartellino))
        assertEquals(0f, VersoOggetto.peso("GIOCO SCAT NUMERI DA 1 A 10", 0.9f, cartellino))
    }

    @Test
    fun le_scritte_della_scatola_contano() {
        // "Numeri da 1 a 10" c'è anche sulla scatola: ma "Grandi tessere e gettoni" no
        assertEquals(20f * 0.9f, VersoOggetto.peso("Grandi tessere e gettoni", 0.9f, cartellino), 0.01f)
        assertEquals(8f * 0.8f, VersoOggetto.peso("SCARABEO", 0.8f, emptySet()), 0.01f)
    }

    @Test
    fun letture_incerte_non_contano() {
        assertEquals(0f, VersoOggetto.peso("OT 6 8 qwz", 0.3f, emptySet()))
    }

    @Test
    fun sceglie_solo_un_verso_chiaramente_migliore() {
        assertEquals(180, VersoOggetto.scegli(mapOf(0 to 3f, 90 to 0f, 180 to 40f, 270 to 2f)))
        assertNull(VersoOggetto.scegli(mapOf(0 to 20f, 90 to 0f, 180 to 15f, 270 to 0f)))   // incerto
        assertNull(VersoOggetto.scegli(mapOf(0 to 4f, 90 to 0f, 180 to 0f, 270 to 0f)))      // troppo poco testo
        assertEquals(0, VersoOggetto.scegli(mapOf(0 to 30f, 90 to 2f, 180 to 5f, 270 to 0f)))
    }
}
