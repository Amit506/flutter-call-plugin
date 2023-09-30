package me.leoletto.caller

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import io.flutter.embedding.engine.loader.FlutterLoader

class CallerPhoneServiceReceiver : BroadcastReceiver() {
    private var telephony: TelephonyManager? = null

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(CallerPlugin.PLUGIN_NAME, "New broadcast event received")
        if (callerPhoneStateListener == null) {


            val flutterLoader = FlutterLoader()
            flutterLoader.startInitialization(context)
            flutterLoader.ensureInitializationComplete(context, null)
            callerPhoneStateListener = CallerPhoneStateListener(context, intent, flutterLoader)
          val  telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
            if(telephony==null){
                return
            }
            telephony.listen(callerPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var callerPhoneStateListener: CallerPhoneStateListener? = null
    }
}