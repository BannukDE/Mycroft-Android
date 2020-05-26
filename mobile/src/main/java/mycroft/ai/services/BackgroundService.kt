package mycroft.ai.services

import mycroft.ai.R
import mycroft.ai.MainActivity
import mycroft.ai.FloatingWidgetView
import android.app.Service
import android.app.Notification
import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.view.WindowManager

import androidx.annotation.Nullable
import androidx.preference.PreferenceManager


class BackgroundService : Service() {

    private var activityBackground = false

    private lateinit var windowManager: WindowManager
    private lateinit var floatingWidgetView: FloatingWidgetView
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val notification = createNotification()
        startForeground(1, notification)

        if (intent != null) activityBackground = intent.getBooleanExtra("activity_background", true)
        if (activityBackground && sharedPref.getBoolean("overlaySwitch", false)) {

            // creates the floatingWidgetView
            if (!::floatingWidgetView.isInitialized) {
                floatingWidgetView = FloatingWidgetView(this)
            }
        } else stopSelf()

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        if(::windowManager.isInitialized && ::floatingWidgetView.isInitialized) windowManager.removeView(floatingWidgetView)
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
