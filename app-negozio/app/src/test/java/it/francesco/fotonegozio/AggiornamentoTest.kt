package it.francesco.fotonegozio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)   // serve per org.json
class AggiornamentoTest {
    @Test
    fun legge_il_file_della_versione() {
        val n = Aggiornamento.leggi("""{"versionCode": 54, "versionName": "2.9", "apk": "https://esempio/app.apk", "note": "Novità"}""")!!
        assertEquals(54L, n.versionCode); assertEquals("2.9", n.versionName); assertEquals("https://esempio/app.apk", n.apk); assertEquals("Novità", n.note)
    }

    @Test
    fun file_rovinato_nessun_aggiornamento() {
        assertNull(Aggiornamento.leggi("<html>404</html>"))
        assertNull(Aggiornamento.leggi("""{"versionName": "2.9"}"""))
    }
}
