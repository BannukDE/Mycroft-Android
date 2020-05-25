package mycroft.ai

import android.app.*
//import android.app.NotificationManager
//import android.app.NotificationChannel
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.Nullable
import android.util.Log
import android.view.WindowManager


class FloatingWidgetService : Service() {

    private val logTag = "MycroftFloatWidget"

    private var isServiceStarted = false
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var windowManager: WindowManager
    private lateinit var floatingWidgetView: FloatingWidgetView

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // creates the floatingWidgetView
        if (!::floatingWidgetView.isInitialized) {
            Log.i(logTag, "Service starting")
            floatingWidgetView = FloatingWidgetView(this)
        }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        if(::windowManager.isInitialized && ::floatingWidgetView.isInitialized) windowManager.removeView(floatingWidgetView)
        Log.i(logTag, "Service stopped")
    }

    @Nullable
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotification(): Notification {
        val notificationChannelId =  "ENDLESS SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
            val channel = NotificationChannel(
                    notificationChannelId,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_MIN
            ).let {
                it.description = "Service is running in background"
                //it.enableLights(true)
                //it.lightColor = Color.RED
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) Notification.Builder(
                this,
                notificationChannelId
        ) else Notification.Builder(this)

        return builder
                .setOngoing(true)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Service is running in background")
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_mycroft)
                .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
                .build()
        //.setTicker("Ticker text")
    }
}
