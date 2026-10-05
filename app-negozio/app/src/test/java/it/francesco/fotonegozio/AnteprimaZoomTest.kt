package it.francesco.fotonegozio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Il pezzo di foto originale da caricare nitido corrisponde a quello inquadrato. */
@RunWith(RobolectricTestRunner::class)   // serve per android.graphics.Rect
class AnteprimaZoomTest {
    @Test
    fun zoom_doppio_al_centro() {
        // Foto 4000×3000 in un riquadro 400×300: ingrandita 2 volte si vede la metà centrale
        val z = zonaVisibile(IntSize(4000, 3000), IntSize(400, 300), 2f, Offset.Zero)!!
        assertEquals(1000, z.left); assertEquals(750, z.top)
        assertEquals(3001, z.right); assertEquals(2251, z.bottom)
    }

    @Test
    fun zoom_spostato_a_destra_mostra_la_parte_sinistra() {
        // Spostando l'immagine a destra di 200 px (zoom 2) si vede il bordo sinistro della foto
        val z = zonaVisibile(IntSize(4000, 3000), IntSize(400, 300), 2f, Offset(200f, 0f))!!
        assertEquals(0, z.left)
        assertEquals(2001, z.right)
    }
}
