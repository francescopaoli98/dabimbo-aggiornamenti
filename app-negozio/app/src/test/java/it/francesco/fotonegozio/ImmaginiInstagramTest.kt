package it.francesco.fotonegozio

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImmaginiInstagramTest {
    private fun foto(w: Int, h: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 30, 30)) }

    @Test
    fun storia_9_16_con_fascia_sotto_la_foto() {
        val s = ImmaginiInstagram.storia(foto(1500, 2000), listOf(RigaIG("Felpa zip con cappuccio rosa", "8 anni", "1444496", "€ 4,00")))
        assertEquals(1080, s.width); assertEquals(1920, s.height)
        // In mezzo c'è la foto (rossa), più in basso la fascia bianca
        assertTrue(Color.red(s.getPixel(540, 700)) > 180 && Color.green(s.getPixel(540, 700)) < 80)
        val fascia = s.getPixel(900, 1560)
        assertTrue(Color.red(fascia) > 230 && Color.green(fascia) > 230 && Color.blue(fascia) > 230)
    }

    @Test
    fun storia_con_tanti_articoli_resta_nello_schermo() {
        val righe = (1..4).map { RigaIG("Articolo numero $it con una descrizione lunga lunga lunga", "taglia $it", "14444$it", "€ $it,00") }
        val s = ImmaginiInstagram.storia(foto(2000, 1500), righe)
        assertEquals(1920, s.height)
    }

    @Test
    fun carosello_4_5_col_numero() {
        val c = ImmaginiInstagram.carosello(foto(1500, 2000), 3)
        assertEquals(1080, c.width); assertEquals(1350, c.height)
        // Angolo: il cerchio bianco del numero; il resto è la foto
        val bordo = c.getPixel(95 + 40, 95)
        assertTrue(Color.green(bordo) > 200)
        assertTrue(Color.green(c.getPixel(540, 700)) < 80)
        // Foto orizzontale: resta intera (sopra e sotto lo sfondo sfocato, non la foto tagliata)
        val o = ImmaginiInstagram.carosello(foto(2000, 1000), 1)
        assertEquals(1350, o.height)
    }

    @Test
    fun fascia_prenotato() {
        val s = ImmaginiInstagram.storia(foto(1500, 2000), listOf(RigaIG("Felpa", null, "1444496", "€ 4,00")))
        val p = ImmaginiInstagram.prenotato(s)
        assertEquals(1080, p.width); assertEquals(1920, p.height)
        // Al centro della fascia: rosa (prima era la foto rossa)
        val c = p.getPixel(540 + 200, (1920 * 0.30f).toInt() - 106)
        assertTrue(Color.blue(c) > 120)
    }
}
