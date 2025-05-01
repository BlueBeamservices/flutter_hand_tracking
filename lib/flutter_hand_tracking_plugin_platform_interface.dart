import 'package:plugin_platform_interface/plugin_platform_interface.dart';

abstract class FlutterHandTrackingPluginPlatform extends PlatformInterface {
  FlutterHandTrackingPluginPlatform() : super(token: _token);

  static final Object _token = Object();
  static FlutterHandTrackingPluginPlatform _instance =
      MethodChannelFlutterHandTrackingPlugin();

  static FlutterHandTrackingPluginPlatform get instance => _instance;

  static set instance(FlutterHandTrackingPluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}

class MethodChannelFlutterHandTrackingPlugin
    extends FlutterHandTrackingPluginPlatform {
  @override
  Future<String?> getPlatformVersion() async {
    // Implementation goes here
    return "Not implemented yet";
  }
}
