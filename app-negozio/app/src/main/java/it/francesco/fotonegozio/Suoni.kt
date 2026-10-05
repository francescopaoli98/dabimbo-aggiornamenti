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
                // Volume "contenuti multimediali": si sente anche col cursore OnePlus su vibrazione
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()
    // Un suono si può suonare solo quando è caricato
    private val caricati = mutableSetOf<Int>()
    init { pool.setOnLoadCompleteListener { _, id, stato -> if (stato == 0) caricati += id } }
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
        if (attivi && id in caricati) pool.play(id, 0.35f, 0.35f, 1, 0, 1f)   // piano: suoni leggeri
    }
}
