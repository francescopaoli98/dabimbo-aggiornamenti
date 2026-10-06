package it.francesco.fotonegozio

import org.junit.Assert.assertEquals
import org.junit.Test

class SceltaNitidiTest {

    /** Oggetto rettangolare pieno. */
    private fun rett(sx: Int, su: Int, dx: Int, giu: Int) =
        OggettoFoto(sx, su, dx, giu, (dx - sx) * (giu - su)) { x, y -> x in sx until dx && y in su until giu }

    private val PIXEL = 1000 * 1000

    @Test
    fun oggetto_col_cartellino_sopra_resta_nitido_gli_altri_no() {
        val felpa = rett(300, 300, 500, 500)       // 4% della foto
        val busta = rett(700, 100, 800, 200)       // 1%, senza cartellino
        val tenuti = SceltaNitidi.scegli(listOf(felpa, busta), listOf(ZonaNitida(400f, 400f, 50f)), PIXEL)
        assertEquals(setOf(0), tenuti)
    }

    @Test
    fun cartellino_appoggiato_accanto_tiene_l_oggetto_piu_vicino() {
        val scarpe = rett(300, 300, 500, 500)
        val tazza = rett(800, 800, 900, 900)
        // Cartellino sul tavolo, un po' sotto le scarpe
        val tenuti = SceltaNitidi.scegli(listOf(scarpe, tazza), listOf(ZonaNitida(400f, 560f, 40f)), PIXEL)
        assertEquals(setOf(0), tenuti)
    }

    @Test
    fun oggetto_grande_in_primo_piano_resta_nitido_anche_senza_cartellino() {
        val grande = rett(100, 100, 500, 500)      // 16% della foto
        val piccolo = rett(700, 700, 750, 750)
        assertEquals(setOf(0), SceltaNitidi.scegli(listOf(grande, piccolo), emptyList(), PIXEL))
    }

    @Test
    fun piu_articoli_con_cartellino_tutti_nitidi() {
        val libri = (0 until 5).map { rett(it * 180, 400, it * 180 + 150, 550) }
        val zone = libri.map { ZonaNitida((it.sx + 75).toFloat(), 480f, 30f) }.take(4)   // 4 cartellini letti su 5
        assertEquals(setOf(0, 1, 2, 3), SceltaNitidi.scegli(libri, zone, PIXEL))
    }
}
