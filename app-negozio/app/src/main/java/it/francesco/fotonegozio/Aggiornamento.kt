package it.francesco.fotonegozio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Una versione nuova dell'app trovata su GitHub. */
data class Novita(val versionCode: Long, val versionName: String, val apk: String, val note: String)

/**
 * Aggiornamenti senza Play Store: l'app legge da GitHub (progetto pubblico "dabimbo-aggiornamenti")
 * un piccolo file con l'ultima versione; se è più nuova la scarica e apre l'installazione di Android.
 * Il tocco su "Installa" lo fa sempre Elisa: Android non permette di aggiornare da soli fuori dal Play Store.
 */
object Aggiornamento {

    /** Il file con l'ultima versione (progetto pubblico: solo l'app pronta, niente codice). */
    const val INDIRIZZO = "https://raw.githubusercontent.com/francescopaoli85-ops/dabimbo-aggiornamenti/main/versione.json"

    /** Legge il file della versione (Kotlin puro, testabile). */
    fun leggi(testo: String): Novita? = runCatching {
        val o = JSONObject(testo)
        Novita(o.getLong("versionCode"), o.getString("versionName"), o.getString("apk"), o.optString("note"))
    }.getOrNull()

    fun versioneInstallata(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    fun nomeVersione(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** La versione nuova, se c'è (null se non c'è o se manca internet). */
    suspend fun controlla(context: Context): Novita? = withContext(Dispatchers.IO) {
        runCatching {
            // "?t=" evita di ricevere una copia vecchia del file
            val c = URL("$INDIRIZZO?t=${System.currentTimeMillis() / 60_000}").openConnection() as HttpURLConnection
            c.connectTimeout = 8_000; c.readTimeout = 8_000
            try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
        }.getOrNull()?.let(::leggi)?.takeIf { it.versionCode > versioneInstallata(context) }
    }

    /** Scarica l'app nuova; [avanzamento] va da 0 a 1. Restituisce il file, o null se non riesce. */
    suspend fun scarica(context: Context, n: Novita, avanzamento: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val cartella = File(context.cacheDir, "aggiornamento").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val file = File(cartella, "dabimbo-${n.versionName}.apk")
            val c = URL(n.apk).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            try {
                val totale = c.contentLengthLong.takeIf { it > 0 } ?: -1L
                c.inputStream.use { ingresso ->
                    file.outputStream().use { uscita ->
                        val buffer = ByteArray(64 * 1024)
                        var letti = 0L
                        while (true) {
                            val n2 = ingresso.read(buffer)
                            if (n2 < 0) break
                            uscita.write(buffer, 0, n2)
                            letti += n2
                            if (totale > 0) avanzamento(letti / totale.toFloat())
                        }
                    }
                }
            } finally { c.disconnect() }
            file
        }.getOrNull()
    }

    /** Android chiede il permesso di installare app che non vengono dal Play Store (una volta sola). */
    fun puoInstallare(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Apre la pagina delle impostazioni dove Elisa permette l'installazione (la prima volta). */
    fun chiediPermesso(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Apre la schermata "Installa" di Android con l'app scaricata. */
    fun installa(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
