import 'dart:developer';
import 'dart:ui';

import 'package:caller/caller.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:fluttertoast/fluttertoast.dart';
import 'package:permission_handler/permission_handler.dart';
// import 'package:shared_preferences/shared_preferences.dart';


/// Defines a callback that will handle all background incoming events
Future<void> callerCallbackHandler(
  CallerEvent event,
  String number,
  int duration,
) async {
  DartPluginRegistrant.ensureInitialized();
  print("New event received from native $event");
  // Fluttertoast.showToast(
  //     msg: "This is Center Short Toast",
  //     toastLength: Toast.LENGTH_SHORT,
  //     gravity: ToastGravity.CENTER,
  //     timeInSecForIosWeb: 1,
  //     backgroundColor: Colors.red,
  //     textColor: Colors.white,
  //     fontSize: 16.0
  // );

  // const MethodChannel _backgroundChannel =
  //     MethodChannel('me.leoletto.caller_background');
  // _backgroundChannel.invokeMethod('endCall');
  print(number);
}

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  /// Run your app as you would normally do...
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Caller Plugin Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: const MyHomePage(title: 'Caller Plugin Demo'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({Key? key, required this.title}) : super(key: key);
  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  bool? hasPermission;

  @override
  void initState() {
    super.initState();

    _checkPermission();
  }

  Future<void> _checkPermission() async {
   //  SharedPreferences pre =await SharedPreferences.getInstance();
   // final val= pre.getString('token');
   // log(val.toString());
    final permission = await Caller.checkPermission();
    print('Caller permission $permission');
    setState(() => hasPermission = permission);
  }

  Future<void> _requestPermission() async {
    await Caller.requestPermissions();
    await _checkPermission();
  }

  Future<void> _stopCaller() async {
    await Caller.stopCaller();
  }

  Future<void> _startCaller() async {
    Permission.phone.request();
    if (hasPermission != true) return;
    final running =await Caller.isRunning();
    log(running.toString());
    // if(running)
    await Caller.initialize(callerCallbackHandler);



  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Text(hasPermission == true ? 'Has permission' : 'No permission'),
            ElevatedButton(
              onPressed: () {
                _requestPermission();
              },
              child: Text('Ask Permission'),
            ),
            ElevatedButton(
              onPressed: () {
                _startCaller();
              },
              child: Text('Start caller'),
            ),
            ElevatedButton(
              onPressed: () => _stopCaller(),
              child: Text('Stop caller'),
            ),
          ],
        ),
      ),
    );
  }
}
