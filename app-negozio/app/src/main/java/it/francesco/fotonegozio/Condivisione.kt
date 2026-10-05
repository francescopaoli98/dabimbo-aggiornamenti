package it.francesco.fotonegozio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Pezzo 6: passa foto + testo a WhatsApp Business (o WhatsApp, o il menu Condividi).
 * L'INVIO lo preme sempre Elisa dentro WhatsApp: niente automatismi (rischio ban).
 */
object Condivisione {

    private val APP = listOf("com.whatsapp.w4b", "com.whatsapp")   // prima Business (provato: tiene la didascalia)

    /** Restituisce false se non è riuscita ad aprire nessuna app. */
    fun pubblica(context: Context, foto: File, testo: String): Boolean {
        // Il testo anche negli appunti: se WhatsApp lo perdesse, basta "Incolla"
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Testo per lo stato", testo))

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", foto)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, testo)
            clipData = ClipData.newRawUri("foto", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        for (pacchetto in APP) {
            val diretto = Intent(intent).setPackage(pacchetto)
            if (diretto.resolveActivity(context.packageManager) != null) {
                context.startActivity(diretto)
                return true
            }
        }
        // Nessun WhatsApp: menu Condividi di Android
        return try {
            context.startActivity(Intent.createChooser(intent, "Condividi con…"))
            true
        } catch (e: Exception) {
            false
        }
    }
}
