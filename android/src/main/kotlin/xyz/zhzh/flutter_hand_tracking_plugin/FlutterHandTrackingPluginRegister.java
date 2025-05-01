package xyz.zhzh.flutter_hand_tracking_plugin;

import androidx.annotation.NonNull;
import io.flutter.embedding.engine.plugins.FlutterPlugin;

/** Plugin registration class for backwards compatibility */
public class FlutterHandTrackingPluginRegister {
  // This static method is only needed for compatibility with Flutter < 1.17
  // where registerWith was a requirement.
  public static void registerWith(@NonNull io.flutter.plugin.common.PluginRegistry.Registrar registrar) {
    FlutterHandTrackingPlugin plugin = new FlutterHandTrackingPlugin();
    plugin.initializePlugin(registrar.messenger(), registrar.activity());
    registrar.platformViewRegistry().registerViewFactory(
            "plugins.zhzh.xyz/flutter_hand_tracking_plugin/view",
            new HandTrackingViewFactory(registrar.messenger(), registrar.activity())
    );
  }
}