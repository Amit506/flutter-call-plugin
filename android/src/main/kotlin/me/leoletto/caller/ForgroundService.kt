package me.leoletto.caller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat

class ForegroundService : Service() {
    private var receiverRegistered = false
    var startId: Int? = null
    private val phoneReceiver = CallerPhoneServiceReceiver()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start the service in the foreground
       receiverRegistered=true;
        this.startId=startId
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "my_channel_id",
                "My Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.setShowBadge(false)

            channel.setSound(null,null)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val iconResourceId = appInfo.metaData.getInt("me.leoletto.caller.icon")
        Log.d(CallerPlugin.PLUGIN_NAME, "icon resource id : "+iconResourceId.toString())
        val notification = NotificationCompat.Builder(this, "my_channel_id")
            .setContentTitle("Benam.me")
            .setContentText("Spam blocker running")
            .setSound(null)

            .setSmallIcon(iconResourceId)
            .build()
        startForeground(1, notification)

        // Register the PHONE_STATE broadcast receiver
        val filter = IntentFilter()
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(phoneReceiver, filter)

        // Return START_STICKY to keep the service running in the background
        return START_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, ForegroundService::class.java)
        restartServiceIntent.`package` = packageName
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the PHONE_STATE broadcast receiver
        try {
            unregisterReceiver(phoneReceiver)

            stopForeground(true)
            stopSelf(startId!!)

            receiverRegistered = false

        }catch (e:Exception){
            Log.d(CallerPlugin.PLUGIN_NAME, e.toString())
        }


    }


}