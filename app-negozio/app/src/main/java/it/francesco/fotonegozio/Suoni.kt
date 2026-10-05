package it.francesco.fotonegozio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Suoni brevi dell'app (creati apposta, nessun diritto): pronte, inviata, festa. Si possono spegnere. */
class Suoni(context: Context) {

    private val prefs = context.getSharedPreferences("suoni", Context.MODE_PRIVATE)
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val pronte = pool.load(context, R.raw.pronte, 1)
    private val inviata = pool.load(context, R.raw.inviata, 1)
    private val festa = pool.load(context, R.raw.festa, 1)

    var attivi: Boolean
        get() = prefs.getBoolean("attivi", true)
        set(v) = prefs.edit().putBoolean("attivi", v).apply()

    fun fotoPronte() = suona(pronte)
    fun fotoInviata() = suona(inviata)
    fun tuttePubblicate() = suona(festa)

    private fun suona(id: Int) {
        if (attivi) pool.play(id, 0.45f, 0.45f, 1, 0, 1f)   // volume basso: suoni leggeri
    }
}
