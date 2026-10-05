package it.francesco.fotonegozio

import android.content.Context

/**
 * Ricorda in che verso Elisa vuole le foto orizzontali messe in verticale.
 *
 * Ogni foto messa in verticale "vota" il verso che ha alla fine:
 * - se Elisa non la tocca, vota il verso scelto dall'app (era giusto);
 * - se la capovolge, il voto passa all'altro verso.
 * L'app usa il verso con più voti: così, col tempo, sbaglia sempre meno.
 * I voti restano salvati anche chiudendo l'app.
 */
class VersoPreferito(context: Context) {

    private val prefs = context.getSharedPreferences("verso_preferito", Context.MODE_PRIVATE)

    /** 90 = gira a destra (orario), 270 = gira a sinistra. All'inizio a destra. */
    val verso: Int
        get() = if (voti(270) > voti(90)) 270 else 90

    /** Sposta un voto da [vecchio] a [nuovo]. Contano solo 90 e 270 (foto verticali). */
    fun cambiaVoto(vecchio: Int?, nuovo: Int?) {
        if (vecchio == nuovo) return
        val e = prefs.edit()
        if (vecchio == 90 || vecchio == 270) e.putInt(chiave(vecchio), maxOf(0, voti(vecchio) - 1))
        if (nuovo == 90 || nuovo == 270) e.putInt(chiave(nuovo), voti(nuovo) + 1)
        e.apply()
    }

    private fun voti(verso: Int) = prefs.getInt(chiave(verso), 0)
    private fun chiave(verso: Int) = "voti_$verso"
}
