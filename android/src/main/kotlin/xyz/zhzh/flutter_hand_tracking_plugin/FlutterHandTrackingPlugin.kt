package xyz.zhzh.flutter_hand_tracking_plugin

import android.app.Activity
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** FlutterHandTrackingPlugin */
class FlutterHandTrackingPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  private lateinit var channel: MethodChannel
  private var activity: Activity? = null
  private var messenger: BinaryMessenger? = null

  // This method is needed for backwards compatibility
  fun initializePlugin(messenger: BinaryMessenger, activity: Activity?) {
    this.messenger = messenger
    this.activity = activity
    channel = MethodChannel(messenger, "plugins.zhzh.xyz/flutter_hand_tracking_plugin")
    channel.setMethodCallHandler(this)
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    messenger = flutterPluginBinding.binaryMessenger
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "plugins.zhzh.xyz/flutter_hand_tracking_plugin")
    channel.setMethodCallHandler(this)
    
    // Register platform view factory
    flutterPluginBinding.platformViewRegistry.registerViewFactory(
      "plugins.zhzh.xyz/flutter_hand_tracking_plugin/view",
      HandTrackingViewFactory(flutterPluginBinding.binaryMessenger, activity)
    )
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    if (call.method == "getPlatformVersion") {
      result.success("Android ${android.os.Build.VERSION.RELEASE}")
    } else {
      result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    messenger = null
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }
}