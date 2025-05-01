package xyz.zhzh.flutter_hand_tracking_plugin

import android.app.Activity
import android.content.Context
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class HandTrackingViewFactory(
    private val messenger: BinaryMessenger,
    private val activity: Activity?
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        return FlutterHandTrackingView(
            context = context,
            messenger = messenger,
            viewId = viewId,
            activity = activity
        )
    }
}