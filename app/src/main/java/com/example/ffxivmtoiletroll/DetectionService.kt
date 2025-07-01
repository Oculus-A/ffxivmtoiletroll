package com.example.ffxivmtoiletroll
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

class DetectionService : Service() {

    private lateinit var repository: RuleRepository
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0
    private var detectionInterval = 1000L

    // (新增) 初始化所有需要的管理器
    private lateinit var resourceManager: ResourceManager
    private lateinit var floatingWindowManager: FloatingWindowManager
    private lateinit var soundPlayer: SoundPlayer

    private val captureRuleStates = mutableMapOf<String, Boolean>()
    private val actionRuleStates = mutableMapOf<String, Boolean>()

    private val handler = Handler(Looper.getMainLooper())
    private val detectionRunnable = object : Runnable {
        override fun run() {
            performDetectionCycle()
            handler.postDelayed(this, detectionInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV library not found!"); stopSelf(); return
        } else {
            Log.d(TAG, "OpenCV library found and loaded!")
        }
        repository = RuleRepository(this)
        createNotificationChannel()

        // (新增) 初始化 ResourceManager 和其他依赖它的管理器
        resourceManager = ResourceManager(this)
        floatingWindowManager = FloatingWindowManager(this)
        soundPlayer = SoundPlayer(this)

        // (已修改) 服务创建时，预加载所有已知音效，包括用户导入的
        val allSounds = resourceManager.getAvailableSounds()
        allSounds.forEach { soundPlayer.loadSound(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        detectionInterval = intent?.getLongExtra("detectionInterval", 1000L) ?: 1000L
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode == Activity.RESULT_OK && data != null) {
            startForeground(NOTIFICATION_ID, createNotification())
            startScreenCapture(resultCode, data)
            handler.post(detectionRunnable)
        } else {
            Log.e(TAG, "未获得有效的 MediaProjection 授权"); stopSelf()
        }
        return START_STICKY
    }

    private fun performDetectionCycle() {
        var image: Image? = null
        var screenBitmap: Bitmap? = null
        try {
            image = imageReader?.acquireLatestImage() ?: return
            screenBitmap = convertImageToBitmap(image)

            val captureRules = repository.loadCaptureRules()
            val currentCycleCaptureStates = mutableMapOf<String, Boolean>()
            for (rule in captureRules) {
                if (!isCaptureAreaValid(rule.captureArea)) continue
                // (已修改) matchImage 现在内部使用 ResourceManager
                val isMatched = matchImage(screenBitmap, rule)
                currentCycleCaptureStates[rule.id] = isMatched
            }

            val actionRules = repository.loadActionRules()
            for (rule in actionRules) {
                val isRuleActive = evaluateActionRule(rule, currentCycleCaptureStates)
                val previousState = actionRuleStates.getOrDefault(rule.id, false)

                if (isRuleActive && !previousState) {
                    Log.i(TAG, ">>> 行动规则 '${rule.name}' 已触发！")
                    // (已修改) executeActions 现在传递 ResourceIdentifier
                    executeActions(rule.actions)
                } else if (!isRuleActive && previousState) {
                    Log.i(TAG, "<<< 行动规则 '${rule.name}' 已结束！")
                    undoActions(rule.actions)
                }
                actionRuleStates[rule.id] = isRuleActive
            }

            captureRuleStates.clear()
            captureRuleStates.putAll(currentCycleCaptureStates)

        } catch (e: Exception) {
            Log.e(TAG, "检测周期中发生错误", e)
        } finally {
            screenBitmap?.recycle()
            image?.close()
        }
    }

    private fun evaluateActionRule(rule: ActionRule, captureStates: Map<String, Boolean>): Boolean {
        if (rule.conditions.isEmpty()) return false

        return when (rule.logicOperator) {
            LogicOperator.AND -> {
                rule.conditions.all { cond ->
                    captureStates.getOrDefault(cond.captureRuleId, false) == cond.isMet
                }
            }
            LogicOperator.OR -> {
                rule.conditions.any { cond ->
                    captureStates.getOrDefault(cond.captureRuleId, false) == cond.isMet
                }
            }
        }
    }

    // (已修改) 使用新的 Action 定义来调用对应的方法
    private fun executeActions(actions: List<Action>) {
        for (action in actions) {
            when (action) {
                is Action.ShowImage -> {
                    val details = action.details
                    floatingWindowManager.showImage(details.id, details.image, details.positionX, details.positionY)
                }
                is Action.PlaySound -> {
                    soundPlayer.playSound(action.details.sound)
                }
            }
        }
    }

    private fun undoActions(actions: List<Action>) {
        for (action in actions) {
            if (action is Action.ShowImage) {
                floatingWindowManager.removeView(action.details.id)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(detectionRunnable)
        virtualDisplay?.release()
        mediaProjection?.stop()
        floatingWindowManager.removeAllViews()
        soundPlayer.release()
        Log.d(TAG, "服务已销毁，资源已释放")
    }

    @SuppressLint("WrongConstant")
    private fun startScreenCapture(resultCode: Int, data: Intent) {
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection 初始化失败"); return
        }

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay("ScreenCapture", screenWidth, screenHeight, screenDensity, 0, imageReader?.surface, null, null)
    }

    private fun convertImageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride: Int = plane.pixelStride
        val rowStride: Int = plane.rowStride
        val rowPadding: Int = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun isCaptureAreaValid(area: android.graphics.Rect): Boolean {
        return area.left >= 0 && area.top >= 0 &&
                area.right <= screenWidth && area.bottom <= screenHeight &&
                area.width() > 0 && area.height() > 0
    }

    // (已修改) 使用 ResourceManager 加载模板图片进行匹配
    private fun matchImage(screenBitmap: Bitmap, rule: CaptureRule): Boolean {
        var croppedBitmap: Bitmap? = null
        var templateBitmap: Bitmap? = null
        val screenMat = Mat()
        val templateMat = Mat()
        val result = Mat()
        var isMatched = false

        try {
            croppedBitmap = Bitmap.createBitmap(screenBitmap, rule.captureArea.left, rule.captureArea.top, rule.captureArea.width(), rule.captureArea.height())

            // (已修改) 从 ResourceManager 加载模板图片
            templateBitmap = resourceManager.loadBitmap(rule.matchImage)

            if (templateBitmap == null) {
                Log.e(TAG, "无法加载模板图片: ${rule.matchImage.path}")
                return false
            }

            Utils.bitmapToMat(croppedBitmap, screenMat)
            Utils.bitmapToMat(templateBitmap, templateMat)
            Imgproc.cvtColor(screenMat, screenMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(templateMat, templateMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.matchTemplate(screenMat, templateMat, result, Imgproc.TM_CCOEFF_NORMED)

            val mmr = Core.minMaxLoc(result)
            if (mmr.maxVal >= rule.matchThreshold) {
                isMatched = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "图像匹配时发生错误: ${e.message}")
        } finally {
            croppedBitmap?.recycle()
            templateBitmap?.recycle()
            screenMat.release()
            templateMat.release()
            result.release()
        }
        return isMatched
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "后台检测服务", NotificationManager.IMPORTANCE_DEFAULT)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("屏幕检测服务运行中")
            .setContentText("正在后台执行检测任务...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DetectionService"
        private const val NOTIFICATION_CHANNEL_ID = "DetectionServiceChannel"
        private const val NOTIFICATION_ID = 1
    }
}
