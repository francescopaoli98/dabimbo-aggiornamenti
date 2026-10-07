package it.francesco.fotonegozio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RitaglioTest {
    private fun vicino(a: Riquadro, b: Riquadro) =
        assertTrue("$a ≠ $b", listOf(a.l - b.l, a.t - b.t, a.r - b.r, a.b - b.b).all { kotlin.math.abs(it) < 0.001f })

    @Test
    fun formati_al_centro() {
        vicino(Riquadro(0f, 50f, 300f, 350f), Ritaglio.formato(1f, 300f, 400f))
        vicino(Riquadro(0f, 0f, 300f, 400f), Ritaglio.formato(3f / 4f, 300f, 400f))
        val nove = Ritaglio.formato(9f / 16f, 300f, 400f)
        assertEquals(9f / 16f, nove.larghezza / nove.altezza, 0.001f)
        assertEquals(400f, nove.altezza, 0.001f)
        vicino(Riquadro(0f, 0f, 300f, 400f), Ritaglio.formato(null, 300f, 400f))
    }

    @Test
    fun maniglie() {
        val q = Riquadro(50f, 50f, 250f, 350f)
        assertEquals(Maniglia.ALTO_SX, Ritaglio.maniglia(q, 55f, 45f, 20f))
        assertEquals(Maniglia.BASSO_DX, Ritaglio.maniglia(q, 245f, 355f, 20f))
        assertEquals(Maniglia.DENTRO, Ritaglio.maniglia(q, 150f, 200f, 20f))
        assertEquals(null, Ritaglio.maniglia(q, 10f, 200f, 20f))
    }

    @Test
    fun trascina_libero_resta_dentro() {
        val q = Riquadro(50f, 50f, 250f, 350f)
        // Spostando tutto troppo a sinistra si ferma al bordo
        vicino(Riquadro(0f, 50f, 200f, 350f), Ritaglio.trascina(q, Maniglia.DENTRO, -100f, 0f, 300f, 400f, null, 20f))
        // Angolo in basso a destra fuori dalla foto: si ferma al bordo
        vicino(Riquadro(50f, 50f, 300f, 400f), Ritaglio.trascina(q, Maniglia.BASSO_DX, 500f, 500f, 300f, 400f, null, 20f))
        // Angolo in alto a sinistra oltre l'altro: resta il minimo
        vicino(Riquadro(230f, 330f, 250f, 350f), Ritaglio.trascina(q, Maniglia.ALTO_SX, 500f, 500f, 300f, 400f, null, 20f))
    }

    @Test
    fun trascina_con_formato_tiene_la_forma() {
        val q = Ritaglio.formato(1f, 300f, 400f)    // 300×300
        val piu = Ritaglio.trascina(Riquadro(100f, 100f, 200f, 200f), Maniglia.BASSO_DX, 50f, 10f, 300f, 400f, 1f, 20f)
        assertEquals(1f, piu.larghezza / piu.altezza, 0.001f)
        assertEquals(150f, piu.larghezza, 0.001f)
        // Non esce dalla foto
        val troppo = Ritaglio.trascina(q, Maniglia.ALTO_SX, -100f, -100f, 300f, 400f, 1f, 20f)
        assertTrue(troppo.l >= 0f && troppo.t >= 0f && troppo.r <= 300f && troppo.b <= 400f)
        assertEquals(1f, troppo.larghezza / troppo.altezza, 0.001f)
    }

    @Test
    fun girata_e_ritorno() {
        val q = Riquadro(0.1f, 0.2f, 0.5f, 0.9f)
        for (g in listOf(0, 90, 180, 270)) vicino(q, Ritaglio.daBase(Ritaglio.versoBase(q, g), g))
        // Girata a destra: la parte in alto della foto vista è la sinistra della base
        vicino(Riquadro(0f, 0f, 0.5f, 1f), Ritaglio.versoBase(Riquadro(0f, 0f, 1f, 0.5f), 90))
    }

    @Test
    fun pixel_e_testo() {
        assertEquals(listOf(30, 80, 120, 200), Ritaglio.pixel(Riquadro(0.1f, 0.2f, 0.5f, 0.7f), 300, 400).toList())
        val q = Riquadro(0.1f, 0.2f, 0.5f, 0.7f)
        assertEquals(q, Ritaglio.leggi(Ritaglio.scrivi(q)))
        assertEquals(null, Ritaglio.leggi("rotto"))
        assertTrue(Ritaglio.tutta(Riquadro(0f, 0f, 1f, 1f)))
    }
}
