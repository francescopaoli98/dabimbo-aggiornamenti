package it.francesco.fotonegozio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestoInstagramTest {
    private val felpa = RigaIG("Felpa rosa", "8 anni", "1444496", "€ 4,00")
    private val scarpe = RigaIG("Scarpe Diadora", "numero 33", "1444583", "€ 9,00")
    private val libro = RigaIG("Librottino", null, null, "€ 1,50")

    @Test
    fun righe_numerate_nell_ordine_delle_foto() {
        val t = TestoInstagram.didascalia(listOf(listOf(scarpe), listOf(felpa, libro)), "Nuovi arrivi", "#dabimboabimbo #prato")
        assertEquals(
            "Nuovi arrivi\n\n" +
                "1. Scarpe Diadora - numero 33 - cod. 1444583 - € 9,00\n" +
                "2. Felpa rosa - 8 anni - cod. 1444496 - € 4,00 + Librottino - € 1,50\n\n" +
                TestoInstagram.FINE + "\n\n#dabimboabimbo #prato",
            t,
        )
    }

    @Test
    fun senza_inizio_ne_hashtag() {
        val t = TestoInstagram.didascalia(listOf(listOf(libro)), "", "")
        assertEquals("1. Librottino - € 1,50\n\n" + TestoInstagram.FINE, t)
    }

    @Test
    fun hashtag_di_partenza() {
        for (h in listOf("#dabimboabimbo", "#dabimboabimboprato", "#prato", "#usatobambini", "#negoziousatobambini"))
            assertTrue(h, h in TestoInstagram.HASHTAG.split(" "))
    }

    @Test
    fun dallo_storico() {
        assertEquals(RigaIG("Felpa rosa", "8 anni", "1444496", "€ 4,00"), TestoInstagram.daStorico(Pubblicato("2026-10-07", "1444496", 400, "Felpa rosa", taglia = "8 anni")))
        // Senza codice la chiave è la descrizione: niente "cod."
        assertEquals(RigaIG("Librottino", null, null, null), TestoInstagram.daStorico(Pubblicato("2026-10-07", "Librottino", 0, "")))
        assertEquals("8 anni · cod. 1444496", TestoInstagram.dettagli(felpa))
    }

    @Test
    fun dalla_lista_come_whatsapp() {
        val r = TestoInstagram.daDati(DatiCartellino("1444496", "felpa 8A", "€ 4,00", null), emptyList())
        assertEquals("8 anni", r.taglia)
        assertEquals("1444496", r.codice)
        assertTrue(!r.nome.contains("8A"))
    }

    @Test
    fun carosello_coi_prenotati() {
        val t = TestoInstagram.didascalia(listOf(listOf(scarpe), listOf(felpa, libro)), "", "", prenotati = setOf("1444496"))
        assertTrue(t.startsWith("1. Scarpe Diadora - numero 33"))
        assertTrue(t.contains("2. PRENOTATO – Felpa rosa - 8 anni - cod. 1444496 - € 4,00 + Librottino - € 1,50"))
    }
}
