package mycroft.ai.adapters

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import mycroft.ai.services.BackgroundService

class Autostart : BroadcastReceiver() {
    override fun onReceive(context: Context, arg1: Intent?) {
        val intent = Intent(context, BackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}