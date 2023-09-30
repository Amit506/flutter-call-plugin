package me.leoletto.caller

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Context.ROLE_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.BlockedNumberContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.util.*


/** CallerPlugin */
class CallerPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener {
  companion object {
    const val PLUGIN_NAME = "me.leoletto.caller"
    const val CALLBACK_SHAREDPREFERENCES_KEY = "callerPluginCallbackHandler"
    const val CALLBACK_USER_SHAREDPREFERENCES_KEY = "callerPluginCallbackHandlerUser"
    const val    SHARED_PREFERENCES_NAME = "CallBlockerSharedPref"
    const val PREF_KEY = "blockedCalls"
  }

  private var channel: MethodChannel? = null
  private var currentActivity: Activity? = null
  private lateinit var applicationContext: Context
  override fun onAttachedToEngine(flutterPluginBinding: FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, PLUGIN_NAME)
    channel!!.setMethodCallHandler(this)
    applicationContext=flutterPluginBinding.applicationContext;
  }


  @RequiresApi(Build.VERSION_CODES.N)
  override fun onMethodCall(call: MethodCall, result: Result) {
    val arguments = call.arguments as ArrayList<*>?
    if (call.method == "initialize" && arguments?.size == 2) {
      if(!isMyServiceRunning(ForegroundService::class.java)) {
        val intent = Intent(currentActivity!!.applicationContext, ForegroundService::class.java)
        ContextCompat.startForegroundService(currentActivity!!.applicationContext, intent)
      }
      val sharedPref = currentActivity!!.getSharedPreferences(PLUGIN_NAME, Context.MODE_PRIVATE)
      val editor = sharedPref.edit()
      editor.putLong(CALLBACK_SHAREDPREFERENCES_KEY, (arguments[0] as Long))
      editor.putLong(CALLBACK_USER_SHAREDPREFERENCES_KEY, (arguments[1] as Long))
      editor.commit()
      Log.d(PLUGIN_NAME, "Service initialized")
      result.success(true)

    } else if (call.method == "stopCaller") {
      val sharedPref = currentActivity!!.getSharedPreferences(PLUGIN_NAME, Context.MODE_PRIVATE)
      val editor = sharedPref.edit()
      editor.remove(CALLBACK_SHAREDPREFERENCES_KEY)
      editor.remove(CALLBACK_USER_SHAREDPREFERENCES_KEY)
      val context: Context = currentActivity!!.applicationContext
      val intent = Intent(currentActivity!!.applicationContext, ForegroundService::class.java)
      currentActivity!!.applicationContext.stopService(intent)

      editor.commit()
      Log.d(PLUGIN_NAME, "Service destroyed")
      result.success(true)

    } else if (call.method == "requestPermissions") {
      Log.d(PLUGIN_NAME, "Requesting permission")
      requestPermissions()

    } else if (call.method == "checkPermissions") {

      val check = doCheckPermission()
      Log.d(PLUGIN_NAME, "Permission checked: $check")
      result.success(check)

    }
    else if(call.method=="addToken"){
      val sharedPreferences =currentActivity!!.applicationContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
val sEdit=sharedPreferences.edit();
      sEdit.putString("token",arguments?.get(0) as String)
      sEdit.commit()

    }
    else if(call.method=="isRunning"){
      if(isMyServiceRunning(ForegroundService::class.java)){
        result.success(true)
      }else{
        result.success(false)
      }
    }
    else if(call.method=="blockNumber"){
     val phoneNumber= arguments?.get(0) as String;
      Log.d(CallerPlugin.PLUGIN_NAME, phoneNumber)
      val context: Context = currentActivity!!.applicationContext
      val contentResolver = context.contentResolver
        val values = ContentValues().apply {
          put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, phoneNumber)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
          Log.d(CallerPlugin.PLUGIN_NAME, "done")
        }

      }
else if(call.method=="unBlockNumber") {
      val phoneNumber = arguments?.get(0) as String;

      val context: Context = currentActivity!!.applicationContext
      val values = ContentValues()
      val contentResolver = context.contentResolver
      values.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1234567890")
      val uri: Uri? =
       contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)

      if(uri!=null) {
        contentResolver.delete(uri, null, null)
        Log.d(CallerPlugin.PLUGIN_NAME, "done")
        result.success(true)
      }

      result.error("something went wrong","error","error")
    }
    else if(call.method=="endCall") {
      try {
        val context: Context = applicationContext
        val telecomManager = Objects.requireNonNull(
          context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        )
        if (telecomManagerEndCall(telecomManager)) {
          Log.i(CallerPlugin.PLUGIN_NAME, "endCall() ended call using TelecomManager")
        } else {
          Log.w(CallerPlugin.PLUGIN_NAME,"endCall() TelecomManager returned false")
        }
      } catch (e: Exception) {
        Log.w(CallerPlugin.PLUGIN_NAME, "endCall() error while ending call with TelecomManager", e)
      }
    }
    else if(call.method=="addBlockNumber") {
      try {
        val phoneNumber = arguments?.get(0) as String;

      val sharedPreferences =
        applicationContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
      val list = sharedPreferences.getStringSet(PREF_KEY, Collections.emptySet())
        val updatedSet = list?.toMutableSet()
        updatedSet?.add(phoneNumber)
      sharedPreferences.edit().putStringSet(PREF_KEY, updatedSet)
        Log.d(PLUGIN_NAME, "added number")


        return result.success("stored $phoneNumber")

    }catch(e:Exception){
      return result.error("someting went wring while storing ", "", "")
    }
    }
    else if(call.method=="removeBlockNumber") {
      try {
        val phoneNumber = arguments?.get(0) as String;

        val sharedPreferences =
          applicationContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val list = sharedPreferences.getStringSet(PREF_KEY, Collections.emptySet())
        val updatedSet = list?.toMutableSet()
        updatedSet?.remove(phoneNumber)
        sharedPreferences.edit().putStringSet(PREF_KEY, updatedSet)

        return result.success("removed $phoneNumber")

      }catch(e:Exception){
        return result.error("someting went wring while removing ", "", "")
      }
    }
    else {
      result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
    channel!!.setMethodCallHandler(null)
  }

  private fun doCheckPermission(): Boolean {
    if (currentActivity != null && currentActivity!!.applicationContext != null) {
      val permPhoneState = ContextCompat.checkSelfPermission(currentActivity!!, Manifest.permission.READ_PHONE_STATE)
      val permReadCallLog = ContextCompat.checkSelfPermission(currentActivity!!, Manifest.permission.READ_CALL_LOG)
      val permReadPhoneNumber = ContextCompat.checkSelfPermission(currentActivity!!, Manifest.permission.READ_PHONE_NUMBERS)
      val callPhone =  ContextCompat.checkSelfPermission(currentActivity!!, Manifest.permission.CALL_PHONE)
      val grantedCode = PackageManager.PERMISSION_GRANTED
      return permPhoneState == grantedCode && permReadCallLog == grantedCode&&permReadPhoneNumber==grantedCode
    }
    return false
  }

  private fun requestPermissions() {
    if (currentActivity?.applicationContext == null)
      return;
    ActivityCompat.requestPermissions(currentActivity!!,
      arrayOf(Manifest.permission.READ_PHONE_STATE),
      1)
    val grantedCode = PackageManager.PERMISSION_GRANTED

    val permissions = arrayOf(
      Manifest.permission.READ_PHONE_STATE,
      Manifest.permission.READ_CALL_LOG,
      Manifest.permission.READ_PHONE_NUMBERS,
      Manifest.permission.CALL_PHONE
    )
    val permissionsToAsk = arrayListOf<String>()

    for(permission in permissions) {

      val permState = ContextCompat.checkSelfPermission(currentActivity!!, permission)

      if (permState != PackageManager.PERMISSION_DENIED) continue
      permissionsToAsk.add(permission)
      continue


    }

    if(permissionsToAsk.size > 0)
      ActivityCompat.requestPermissions(currentActivity!!,
        permissionsToAsk.toTypedArray(),
        99)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    currentActivity = binding.activity
    binding.addRequestPermissionsResultListener(this)
//    requestPermissions()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    currentActivity = binding.activity
    binding.addRequestPermissionsResultListener(this)
//    requestPermissions()
  }

  override fun onDetachedFromActivityForConfigChanges() {
    currentActivity = null
  }

  override fun onDetachedFromActivity() {
    currentActivity = null
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
    return when (requestCode) {
      999 -> grantResults != null && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
      else -> false
    }
  }

  private fun telecomManagerEndCall(telecomManager: TelecomManager): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

     return telecomManager.endCall()
    } else {
      return  false
    }
  }
  fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
    val activityManager = getSystemService(applicationContext,ActivityManager::class.java) as ActivityManager
    for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
      if (serviceClass.name == service.service.className) {
        return true
      }
    }
    return false
  }
}