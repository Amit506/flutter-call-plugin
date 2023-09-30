# Caller

A flutter plugin to handle Phone Call state and execute a Dart callback in background.
<br />

## Warning 

> This package is under development and since I'm not keen on native Android development, there may be a lot of work to do, so any PR are welcome.

<br />

## IOS Implementation

> Unfortunately I'm not familiar with IOS development neither with Switf/ObjC languages, so if you wish to help any PR will be welcome.

<br />

## Android

Add the following permissions to your `AndroidManifest.xml` file:


```xml
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
```

Also, apparently it is necessary to register the broadcast receiver manually,
otherwise an error will be throw saying that our receiver does not exist inside the app:


```xml
<receiver android:name="me.leoletto.caller.CallerPhoneServiceReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.PHONE_STATE" />
    </intent-filter>
</receiver>
```


<br />

## Getting started


All you need to start using our package after installing it, is defining a callback which must be a top level function or static function, that will be called by our plugin when any incoming call events are detected.

`
void callerCallbackHandler(CallerEvent event, String number, int? duration)
`

This callback handler must accept 3 arguments:

- <b>CallerEvent</b>: The event type detect by our plugin in background.

- <b>Number</b>: The incoming number that is triggering the phone state call.

- <b>duration</b>: An integer that will only have a value if the current `CallerEvent` is equal to `CallerEvent.callEnded`, and will contain the duration of the previous ended call in seconds.

The `CallerEvent` is an `enum` with four possible values: 

Event Value  | Description
------------ | ------------
onIncomingCallReceived | Triggered when the phone is ringing
onIncomingCallAnswered | Triggered if the phone was ringing and the call was accepted by the user
callEnded | Called after the onIncomingCallAnsewered to indicate that the call is ended
onMissedCall | Triggered if the phone was ringing and the user did not answer the call

Since all this process happens in background in a Dart Isolate, there's no guarantee that the current
OS will call the registered callback as soon as an event is triggered or that the callback will ever be called at all,
each OS handle background services with different policies. Make sure to ask user permission before calling the `Caller.initialize` 
method of our plugin. Check the example to see a simple implementation of it.

