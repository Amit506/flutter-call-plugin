package me.leoletto.caller
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getSystemService
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor.DartCallback
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.leoletto.caller.CallerPlugin.Companion.PREF_KEY
import me.leoletto.caller.CallerPlugin.Companion.SHARED_PREFERENCES_NAME
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

enum class CallType {
    INCOMING, OUTGOING,INCOMING_ENDED,MISSED_CALL,OUTGOING_ENDED;
}

class CallerPhoneStateListener internal constructor(
    private val context: Context,
    private val intent: Intent,
    private val flutterLoader: FlutterLoader
) : PhoneStateListener() {

    private var sBackgroundFlutterEngine: FlutterEngine? = null
    private var channel: MethodChannel? = null
    private var callbackHandler: Long? = null
    private var callbackHandlerUser: Long? = null

    private var time: ZonedDateTime? = null
    private var callType: CallType? = null
    private var previousState: Int? = null

    @RequiresApi(Build.VERSION_CODES.O)
    @Synchronized
    override fun onCallStateChanged(state: Int, incomingNumber: String?) {
        if(incomingNumber==null){
            return
        }
        try {
            when (state) {
                TelephonyManager.CALL_STATE_IDLE -> {
                    val duration =
                        Duration.between(time ?: ZonedDateTime.now(), ZonedDateTime.now())

                    if (previousState == TelephonyManager.CALL_STATE_OFFHOOK && callType == CallType.INCOMING) {
                        // Incoming call ended
                        Log.d(
                            CallerPlugin.PLUGIN_NAME,
                            "Phone State event IDLE (INCOMING ENDED) with number - $incomingNumber"
                        )
                        notifyFlutterEngine(
                            CallType.INCOMING_ENDED,
                            duration.toMillis() / 1000,
                            incomingNumber
                        )
                    }
                    if (previousState == TelephonyManager.CALL_STATE_RINGING && callType == CallType.INCOMING) {
                        // Missed call
                        Log.d(
                            CallerPlugin.PLUGIN_NAME,
                            "Phone State event IDLE (MISSED CALL) with number - $incomingNumber"
                        )
                        notifyFlutterEngine(CallType.MISSED_CALL, 0, incomingNumber)
                    } else if (callType == CallType.OUTGOING) {
                        // Outgoing call ended
                        Log.d(
                            CallerPlugin.PLUGIN_NAME,
                            "Phone State event IDLE (OUTGOING ENDED) with number - $incomingNumber"
                        )
                        notifyFlutterEngine(
                            CallType.OUTGOING_ENDED,
                            duration.toMillis() / 1000,
                            incomingNumber
                        )
                    }

                    callType = null
                    previousState = TelephonyManager.CALL_STATE_IDLE
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    Log.d(CallerPlugin.PLUGIN_NAME, "Phone State event STATE_OFFHOOK")
                    // Phone didn't ring, so this is an outgoing call
                    if (callType == null)
                        callType = CallType.OUTGOING

                    // Get current time to use later to calculate the duration of the call
                    time = ZonedDateTime.now()
                    previousState = TelephonyManager.CALL_STATE_OFFHOOK
                    notifyFlutterEngine(CallType.OUTGOING, 0, incomingNumber)
                }
                TelephonyManager.CALL_STATE_RINGING -> {
                    // INCOMING_CALL
                    Log.d(
                        CallerPlugin.PLUGIN_NAME,
                        "Phone State event PHONE_RINGING number: $incomingNumber"
                    )
                    callType = CallType.INCOMING
                    previousState = TelephonyManager.CALL_STATE_RINGING
                    val telecomManager = Objects.requireNonNull(
                        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    )

                    if (telecomManagerEndCall(telecomManager, incomingNumber)) {
                        Log.i(CallerPlugin.PLUGIN_NAME, "endCall() ended call using TelecomManager")
                    } else {
                        Log.w(CallerPlugin.PLUGIN_NAME, "endCall() TelecomManager returned false")
                    }

                    notifyFlutterEngine(CallType.INCOMING, 0, incomingNumber)
                }
            }
        }catch (e:Exception){
            Log.i(CallerPlugin.PLUGIN_NAME, e.toString())
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun notifyFlutterEngine(type: CallType, duration: Long, number: String){
        val arguments = ArrayList<Any?>()


           val callbackHandler = context.getSharedPreferences(
                CallerPlugin.PLUGIN_NAME,
                Context.MODE_PRIVATE
            ).getLong(CallerPlugin.CALLBACK_SHAREDPREFERENCES_KEY, 0)
          val  callbackHandlerUser = context.getSharedPreferences(
                CallerPlugin.PLUGIN_NAME,
                Context.MODE_PRIVATE
            ).getLong(CallerPlugin.CALLBACK_USER_SHAREDPREFERENCES_KEY, 0)
        if (callbackHandler == 0L || callbackHandlerUser == 0L) {
                Log.e(CallerPlugin.PLUGIN_NAME, "Fatal: No callback registered")
                return
            }
            Log.d(CallerPlugin.PLUGIN_NAME, "Found callback handler $callbackHandler")
            Log.d(CallerPlugin.PLUGIN_NAME, "Found user callback handler $callbackHandlerUser")
try {
    // Retrieve the actual callback information needed to invoke it.
    val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandler)
    if (callbackInfo == null) {
        Log.e(CallerPlugin.PLUGIN_NAME, "Fatal: failed to find callback")
        return
    }
    val sBackgroundFlutterEngine = FlutterEngine(context)
    val args = DartCallback(
        context.assets,
        flutterLoader.findAppBundlePath(),
        callbackInfo
    )

    // Start running callback dispatcher code in our background FlutterEngine instance.
    sBackgroundFlutterEngine.dartExecutor.executeDartCallback(args)

    // Create the MethodChannel used to communicate between the callback
    // dispatcher and this instance.
    val channel = MethodChannel(
        sBackgroundFlutterEngine.dartExecutor.binaryMessenger,
        CallerPlugin.PLUGIN_NAME + "_background"
    )
     arguments.add(callbackHandler)
     arguments.add(callbackHandlerUser)
     arguments.add(type.toString())
     arguments.add(duration)
     arguments.add(number)
    Log.e(CallerPlugin.PLUGIN_NAME, arguments.toString())

    channel.invokeMethod("call", arguments)
     } catch(e: Exception){
      Log.e(CallerPlugin.PLUGIN_NAME, e.toString())
      }
    }
    private fun telecomManagerEndCall(telecomManager: TelecomManager,phoneNumber:String): Boolean {
        try {
//      if(isBlocked(phoneNumber)){
//          Log.d(CallerPlugin.PLUGIN_NAME, "present in blocked list")
//         callEnd(telecomManager)
//         return  true
//     }
            CoroutineScope(Dispatchers.Default).launch {
                // call isSpam() function to fetch data and return boolean value
                val isSpam = isSpam(phoneNumber)
                if (isSpam) {
                    buildNotification(phoneNumber)
                    callEnd(telecomManager)

                } else {

                    // phone number is not spam, do something else
                    Log.d(CallerPlugin.PLUGIN_NAME, "Phone number is not spam")
                }
            }
         return  true
        } catch (e: Exception) {
            Log.i(CallerPlugin.PLUGIN_NAME, "endCall() ended call failed")
            Log.i(CallerPlugin.PLUGIN_NAME, e.toString())
            return false
        }
    }
     private fun callEnd(telecomManager: TelecomManager){
         val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
             if(telephonyManager==null){
                 return
             }
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
             telecomManager.endCall()
         }else{
             val c = Class.forName(telephonyManager.javaClass.name)
             val m = c.getDeclaredMethod("getITelephony")
             m.isAccessible = true
             val telephonyService = m.invoke(telephonyManager)

             // End the call
             val telephonyServiceClass = Class.forName(telephonyService.javaClass.name)
             val endCallMethod = telephonyServiceClass.getDeclaredMethod("endCall")
             endCallMethod.invoke(telephonyService)

         }
     }
    @SuppressLint("MissingPermission")
    private fun buildNotification(phone: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "blocked_notication",
                "blocked_notication channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager: NotificationManager? =
                getSystemService(context, NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
        Log.i(CallerPlugin.PLUGIN_NAME, "building notification")
        // Next, create a notification
          val contactName= getContactName(phone,context)
        Log.i(CallerPlugin.PLUGIN_NAME, "contact name  $contactName")
        val appInfo =context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val iconResourceId = appInfo.metaData.getInt("me.leoletto.caller.icon")
        Log.d(CallerPlugin.PLUGIN_NAME, "icon resource id : $iconResourceId")
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            context,
            "blocked_notication"
        )
            .setSmallIcon(iconResourceId)
            .setSound(null)
            .setContentTitle("Blocked by benam")
            .setContentText(phone)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if(contactName!=null&&contactName.isNotEmpty()){
            builder.setContentText("$contactName $phone")
        }

        val notificationManager: NotificationManagerCompat =
            NotificationManagerCompat.from(context)
        notificationManager.notify(0, builder.build())
    }

    private fun isSpam(phoneNumber:String):Boolean{
        // TODO: add logic to decide number is spam or not
       
        return true;

        val sharedPreferences =context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("token","")
        Log.d(CallerPlugin.PLUGIN_NAME, "shared Pref value background : $token")
        val baseUrl = "url to detect spam or not"

        val countryCode = "IN" // example country code

        val encodedPhoneNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            URLEncoder.encode(phoneNumber, StandardCharsets.UTF_8.toString())
        } else {
           phoneNumber
        }
        Log.i(CallerPlugin.PLUGIN_NAME, "encodede number$encodedPhoneNumber")
        val urlString = "$baseUrl?phoneNumber=$encodedPhoneNumber&countryCode=$countryCode"

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout=200
        connection.requestMethod = "GET"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")

        Log.i(CallerPlugin.PLUGIN_NAME, "Request headers :")
        for ((key, value) in connection.requestProperties) {

            Log.i(CallerPlugin.PLUGIN_NAME, "$key: $value")
        }
        try {
            val inputStreamReader = InputStreamReader(connection.inputStream)
            val bufferedReader = BufferedReader(inputStreamReader)

            val response = StringBuilder()

            bufferedReader.forEachLine {
                response.append(it)
            }

            bufferedReader.close()
            inputStreamReader.close()
            connection.disconnect()

            val jsonResponse = response.toString()
            val jsonObject = JSONObject(jsonResponse)
            Log.i(CallerPlugin.PLUGIN_NAME, jsonObject.toString())
            Log.i(CallerPlugin.PLUGIN_NAME, jsonResponse)
            Log.i(
                CallerPlugin.PLUGIN_NAME,
                " response of spam request $encodedPhoneNumber " + connection.responseCode
            )
            if (connection.responseCode == 200){
                val value = jsonObject.getBoolean("spam")
                return value
            }
                return false
        }catch (e:Exception){
            Log.i(
                CallerPlugin.PLUGIN_NAME,
                e.toString()
            )
            return  false
        }
    }

    private fun getContactName(phoneNumber: String, context: Context): String? {
        val cr = context.contentResolver
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val cursor = cr.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)

        var contactName: String? = null
        cursor?.use {
            if (it.moveToFirst()) {
                contactName = it.getString(it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
            }
        }

        return contactName
    }
  private  fun isBlocked(phone:String): Boolean {
      return false
//      val sharedPreferences =context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
//
//
//      val stringValue = sharedPreferences.getStringSet(PREF_KEY,Collections.emptySet())
//      Log.d(CallerPlugin.PLUGIN_NAME, "returned value : $stringValue")
//      if(stringValue==null){
//          Log.d(CallerPlugin.PLUGIN_NAME, "does not exist pref : $stringValue")
//          return  false
//      }
//
//
//          val stringList = stringValue?.toList()
//          Log.d(CallerPlugin.PLUGIN_NAME, "present in blocked list : "+(stringList.contains(phone)))
//          if(stringList.contains(phone)){
//              return  true
//          }
//          return false

  }

}