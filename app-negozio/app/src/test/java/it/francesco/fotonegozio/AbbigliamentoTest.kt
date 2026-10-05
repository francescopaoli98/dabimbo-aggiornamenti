package it.francesco.fotonegozio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Descrizioni vere lette dai cartellini di prova. */
class AbbigliamentoTest {

    @Test
    fun capi_vengono_riconosciuti() {
        listOf(
            "FELPA ZIP CAPP OKAIDI 8A RS MARGH FELP 8A",
            "MAGLIA ML N BRILL FILI ARG PIAZZA ITALIA 7/8A 7/8A",
            "GIUBB BOMBER 8/9A N SOPRA ARG BLUKIDS 8/9A",
            "COPRISPALLA 7A PANNA NO BOTT IDO 7A",
            "MAGLIA ML BASICA 5/6A GRI MEL PRENATAL 5/6A",
            "tuta ginn b",
        ).forEach { assertTrue(it, Abbigliamento.eUnCapo(it)) }
    }

    @Test
    fun giochi_libri_peluche_scarpe_no() {
        listOf(
            "SET ANIMALI DINOS VI N AR",
            "LIBROTTINO FROZEN II",
            "peluche sid era glaciale",
            "SCARPE GINN DIADORA B LOGO FUX brill lacci rs vell 31 31",
            "porta merenda n fux lol + bambolina capelli biondi",
            "gioco scatola scarabeo",
            "carrello topolino",   // "top" non deve far scattare "topolino"
        ).forEach { assertFalse(it, Abbigliamento.eUnCapo(it)) }
    }

    @Test
    fun descrizione_mancante_no() {
        assertFalse(Abbigliamento.eUnCapo(null))
    }
}
