package xyz.zhzh.flutter_hand_tracking_plugin

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.components.*
import com.google.mediapipe.formats.proto.LandmarkProto
import com.google.mediapipe.framework.AndroidAssetUtil
import com.google.mediapipe.framework.Packet
import com.google.mediapipe.framework.PacketGetter
import com.google.mediapipe.glutil.EglManager
import com.google.protobuf.InvalidProtocolBufferException
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView

class FlutterHandTrackingView(
    private val context: Context,
    messenger: BinaryMessenger,
    viewId: Int,
    private val activity: Activity? = null
) : PlatformView, MethodChannel.MethodCallHandler {
    
    companion object {
        private const val TAG = "HandTrackingView"
        private const val NAMESPACE = "plugins.zhzh.xyz/flutter_hand_tracking_plugin"
        private const val BINARY_GRAPH_NAME = "handtrackinggpu.binarypb"
        private const val INPUT_VIDEO_STREAM_NAME = "input_video"
        private const val OUTPUT_VIDEO_STREAM_NAME = "output_video"
        private const val OUTPUT_HAND_PRESENCE_STREAM_NAME = "hand_presence"
        private const val OUTPUT_LANDMARKS_STREAM_NAME = "hand_landmarks"
        private val CAMERA_FACING = CameraHelper.CameraFacing.FRONT
        private const val FLIP_FRAMES_VERTICALLY = true
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        
        init {
            try {
                System.loadLibrary("mediapipe_jni")
                System.loadLibrary("opencv_java3")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Error loading libraries: $e")
            }
        }
    }

    private val methodChannel = MethodChannel(messenger, "$NAMESPACE/$viewId")
    private val eventChannel = EventChannel(messenger, "$NAMESPACE/$viewId/landmarks")
    private var eventSink: EventChannel.EventSink? = null
    private val uiThreadHandler = Handler(Looper.getMainLooper())
    
    // View
    private var previewDisplayView = SurfaceView(context)
    
    // MediaPipe components
    private var previewFrameTexture: SurfaceTexture? = null
    private var eglManager = EglManager(null)
    private var processor: FrameProcessor
    private var converter: ExternalTextureConverter? = null
    private var cameraHelper: CameraXPreviewHelper? = null

    init {
        methodChannel.setMethodCallHandler(this)
        eventChannel.setStreamHandler(createLandmarksStreamHandler())
        
        // Initialize processor
        processor = FrameProcessor(
            context,
            eglManager.nativeContext,
            BINARY_GRAPH_NAME,
            INPUT_VIDEO_STREAM_NAME,
            OUTPUT_VIDEO_STREAM_NAME
        )
        
        setupPreviewDisplayView()
        AndroidAssetUtil.initializeNativeAssetManager(context)
        setupProcessorCallbacks()
        
        // Check camera permission and start camera if permitted
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onResume()
        } else if (activity != null) {
            // Request permission if we have an activity
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        } else {
            Log.e(TAG, "Camera permission not granted and cannot request without activity")
        }
    }

    override fun getView(): View = previewDisplayView

    override fun dispose() {
        eventChannel.setStreamHandler(null)
        methodChannel.setMethodCallHandler(null)
        converter?.close()
    }

    override fun onMethodCall(call: MethodChannel.MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            else -> result.notImplemented()
        }
    }

    private fun onResume() {
        converter = ExternalTextureConverter(eglManager.context)
        converter!!.setFlipY(FLIP_FRAMES_VERTICALLY)
        converter!!.setConsumer(processor)
        startCamera()
    }

    private fun setupPreviewDisplayView() {
        previewDisplayView.visibility = View.GONE
        previewDisplayView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                processor.videoSurfaceOutput.setSurface(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                val viewSize = Size(width, height)
                val displaySize = cameraHelper?.computeDisplaySizeFromViewSize(viewSize)
                val isCameraRotated = cameraHelper?.isCameraRotated
                
                if (displaySize != null && isCameraRotated != null && previewFrameTexture != null) {
                    converter!!.setSurfaceTextureAndAttachToGLContext(
                        previewFrameTexture,
                        if (isCameraRotated) displaySize.height else displaySize.width,
                        if (isCameraRotated) displaySize.width else displaySize.height
                    )
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                processor.videoSurfaceOutput.setSurface(null)
            }
        })
    }

    private fun setupProcessorCallbacks() {
        processor.videoSurfaceOutput.setFlipY(FLIP_FRAMES_VERTICALLY)
        
        // Hand presence callback
        processor.addPacketCallback(OUTPUT_HAND_PRESENCE_STREAM_NAME) { packet: Packet ->
            val handPresence = PacketGetter.getBool(packet)
            if (!handPresence) {
                Log.d(TAG, "[TS: ${packet.timestamp}] No hands detected.")
            }
        }
        
        // Hand landmarks callback
        processor.addPacketCallback(OUTPUT_LANDMARKS_STREAM_NAME) { packet: Packet ->
            val landmarksRaw = PacketGetter.getProtoBytes(packet)
            
            if (eventSink == null) {
                try {
                    val landmarks = LandmarkProto.NormalizedLandmarkList.parseFrom(landmarksRaw)
                    if (landmarks == null) {
                        Log.d(TAG, "[TS: ${packet.timestamp}] No hand landmarks.")
                        return@addPacketCallback
                    }
                    // Log landmarks for debugging
                    Log.d(TAG, "[TS: ${packet.timestamp}] Hand landmarks: ${landmarks.landmarkCount}")
                } catch (e: InvalidProtocolBufferException) {
                    Log.e(TAG, "Error parsing landmarks: $e")
                    return@addPacketCallback
                }
            } else {
                // Send landmarks to Flutter
                uiThreadHandler.post { eventSink?.success(landmarksRaw) }
            }
        }
    }

    private fun createLandmarksStreamHandler(): EventChannel.StreamHandler {
        return object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                eventSink = events
            }

            override fun onCancel(arguments: Any?) {
                eventSink = null
            }
        }
    }

    private fun startCamera() {
        cameraHelper = CameraXPreviewHelper()
        cameraHelper!!.setOnCameraStartedListener { surfaceTexture: SurfaceTexture? ->
            previewFrameTexture = surfaceTexture
            previewDisplayView.visibility = View.VISIBLE
        }
        cameraHelper!!.startCamera(context, CAMERA_FACING, null)
    }
}