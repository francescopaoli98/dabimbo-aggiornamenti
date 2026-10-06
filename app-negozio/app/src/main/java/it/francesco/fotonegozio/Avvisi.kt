package it.francesco.fotonegozio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** L'app è sullo schermo? (Se sì, l'avviso "foto pronte" non serve.) */
object AppVisibile {
    @Volatile var visibile = false
}

/**
 * Avvisi sul telefono:
 * - mentre l'app prepara le foto: "Sto preparando le foto… 12 di 30" (fisso, finché lavora);
 * - quando ha finito e Elisa è fuori dall'app: "✅ 30 foto pronte da pubblicare".
 */
object Avvisi {
    private const val CANALE_LAVORO = "lavoro"
    private const val CANALE_PRONTE = "pronte"
    const val ID_LAVORO = 1
    private const val ID_PRONTE = 2

    private fun canali(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANALE_LAVORO, "Preparazione foto", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Mentre l'app sistema le foto"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CANALE_PRONTE, "Foto pronte", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Quando le foto sono pronte da pubblicare"
        })
    }

    /** Toccando l'avviso si torna nell'app (con la lista com'era). */
    private fun apriApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun lavoro(context: Context, fatte: Int, tutte: Int): Notification {
        canali(context)
        return NotificationCompat.Builder(context, CANALE_LAVORO)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("Sto preparando le foto…")
            .setContentText("$fatte di $tutte")
            .setProgress(tutte, fatte, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(apriApp(context))
            .build()
    }

    fun puoAvvisare(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun aggiornaLavoro(context: Context, fatte: Int, tutte: Int) {
        if (!ServizioLavoro.attivo) return   // l'avviso fisso c'è solo mentre il servizio lavora (poi sparisce con lui)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching { NotificationManagerCompat.from(context).notify(ID_LAVORO, lavoro(context, fatte, tutte)) }
    }

    /** "✅ 30 foto pronte da pubblicare" (+ quante da controllare). */
    fun pronte(context: Context, tutte: Int, daControllare: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        canali(context)
        val testo = if (daControllare > 0) "$daControllare da controllare prima di pubblicare" else "Tocca per pubblicarle"
        val n = NotificationCompat.Builder(context, CANALE_PRONTE)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(if (tutte == 1) "✅ 1 foto pronta da pubblicare" else "✅ $tutte foto pronte da pubblicare")
            .setContentText(testo)
            .setAutoCancel(true)
            .setContentIntent(apriApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_PRONTE, n) }
    }

    fun togliPronte(context: Context) = NotificationManagerCompat.from(context).cancel(ID_PRONTE)
}

/**
 * Servizio "in primo piano" mentre l'app prepara le foto: così Android non ferma il lavoro
 * se Elisa esce dall'app. Non fa niente da solo: il lavoro lo fa l'app, lui la tiene sveglia.
 */
class ServizioLavoro : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fatte = intent?.getIntExtra("fatte", 0) ?: 0
        val tutte = intent?.getIntExtra("tutte", 0) ?: 0
        runCatching {
            ServiceCompat.startForeground(
                this, Avvisi.ID_LAVORO, Avvisi.lavoro(this, fatte, tutte),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
            attivo = true
        }.onFailure { stopSelf() }
        inAvvio = false
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        attivo = false
        inAvvio = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        /** Il servizio è partito davvero (ha mostrato il suo avviso). */
        @Volatile var attivo = false
            private set
        /** Avvio chiesto ma non ancora arrivato al servizio. */
        @Volatile private var inAvvio = false

        fun avvia(context: Context, fatte: Int, tutte: Int) = runCatching {
            inAvvio = true
            ContextCompat.startForegroundService(context, Intent(context, ServizioLavoro::class.java).putExtra("fatte", fatte).putExtra("tutte", tutte))
        }.onFailure { inAvvio = false }

        /**
         * Ferma il servizio. Se l'avvio non è ancora arrivato, aspetto (al massimo 3 secondi):
         * fermarlo prima che parta farebbe chiudere l'app ad Android.
         */
        suspend fun ferma(context: Context) {
            kotlinx.coroutines.withTimeoutOrNull(3_000) { while (inAvvio && !attivo) kotlinx.coroutines.delay(50) }
            runCatching { context.stopService(Intent(context, ServizioLavoro::class.java)) }
            NotificationManagerCompat.from(context).cancel(Avvisi.ID_LAVORO)
        }
    }
}
