package com.example.myapplication

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.ImageDecoder
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class OverlayService : Service() {

    /** 한 알림에 대한 모든 상태를 캡슐화 */
    private inner class OverlayInstance {
        var view: View? = null
        var params: WindowManager.LayoutParams? = null
        var mediaPlayer: MediaPlayer? = null
        var videoView: VideoView? = null
        var dismissRunnable: Runnable? = null
        var velocityTracker: VelocityTracker? = null
        var isCleanedUp = false
        var centerX = 0
        var centerY = 0
        var sizePx = 0

        fun cleanup() {
            if (isCleanedUp) return
            isCleanedUp = true
            dismissRunnable?.let { dismissHandler.removeCallbacks(it) }
            try {
                view?.let { v ->
                    videoView?.stopPlayback()
                    windowManager?.removeView(v)
                }
            } catch (_: Exception) {}
            try { mediaPlayer?.release() } catch (_: Exception) {}
            try { velocityTracker?.recycle() } catch (_: Exception) {}
            view = null
            videoView = null
            mediaPlayer = null
            velocityTracker = null
            instances.remove(this)
            if (instances.isEmpty()) stopSelf()
        }
    }

    private val instances = mutableListOf<OverlayInstance>()
    private var windowManager: WindowManager? = null
    private val dismissHandler = Handler(Looper.getMainLooper())
    private var screenWidth = 0
    private var screenHeight = 0

    private val maxInstances = 10

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences("rules", Context.MODE_PRIVATE)
        val stackOverlays = prefs.getBoolean("stackOverlays", false)

        // 중첩 OFF면 기존 인스턴스 모두 정리
        if (!stackOverlays) {
            instances.toList().forEach { it.cleanup() }
        } else {
            // 너무 많이 쌓이면 가장 오래된 것 제거
            while (instances.size >= maxInstances) {
                instances.first().cleanup()
            }
        }

        val sourcePackage = intent?.getStringExtra("sourcePackage")
        val instance = OverlayInstance()
        instances.add(instance)

        val soundDurationMs = playSound(instance, prefs)
        showOverlay(instance, prefs, soundDurationMs, sourcePackage)
        return START_NOT_STICKY
    }

    private fun shouldSkipSound(prefs: SharedPreferences): Boolean {
        val playInVibrate = prefs.getBoolean("playInVibrate", false)
        val playInSilent = prefs.getBoolean("playInSilent", false)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> !playInVibrate
            AudioManager.RINGER_MODE_SILENT -> !playInSilent
            else -> false
        }
    }

    private fun effective(
        prefs: SharedPreferences, key: String, default: Float,
        randomKey: String, minVal: Float, maxVal: Float
    ): Float {
        return if (prefs.getBoolean(randomKey, false)) {
            (Math.random() * (maxVal - minVal) + minVal).toFloat()
        } else {
            prefs.getFloat(key, default)
        }
    }

    private fun showOverlay(
        instance: OverlayInstance,
        prefs: SharedPreferences,
        soundDurationMs: Int,
        sourcePackage: String?
    ) {
        val mediaUriStr = prefs.getString("mediaUri", null) ?: prefs.getString("imageUri", null)
        val mediaType = prefs.getString("mediaType", "image") ?: "image"
        val useVideoSound = prefs.getBoolean("useVideoSound", true)
        val skipSound = shouldSkipSound(prefs)
        val muteVideo = !useVideoSound || skipSound

        val entryAnimation = prefs.getBoolean("entryAnimation", true)
        val entryMode = prefs.getString("entryMode", "spring") ?: "spring"
        val dragEnabled = prefs.getBoolean("dragEnabled", true)

        val mediaSizeDp = effective(prefs, "mediaSize", 250f, "mediaSizeRandom", 50f, 600f)
        val appIconSizeDp = effective(prefs, "appIconSize", 100f, "appIconSizeRandom", 50f, 200f)

        var hasMedia = false
        var isVideo = false
        var isAppIcon = false
        val view: View = run {
            if (mediaUriStr != null) {
                val uri = Uri.parse(mediaUriStr)
                if (mediaType == "video") {
                    try {
                        val vv = VideoView(this)
                        vv.setVideoURI(uri)
                        isVideo = true
                        hasMedia = true
                        instance.videoView = vv
                        return@run vv
                    } catch (e: Exception) {
                        Log.e("OverlayService", "동영상 로드 실패", e)
                    }
                } else {
                    try {
                        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(contentResolver, uri)
                            val drawable = ImageDecoder.decodeDrawable(source)
                            iv.setImageDrawable(drawable)
                            if (drawable is AnimatedImageDrawable) drawable.start()
                        } else {
                            iv.setImageURI(uri)
                        }
                        if (iv.drawable != null) {
                            hasMedia = true
                            return@run iv
                        }
                    } catch (e: Exception) {
                        Log.e("OverlayService", "이미지 로드 실패", e)
                    }
                }
            }
            if (sourcePackage != null) {
                try {
                    val appIcon = packageManager.getApplicationIcon(sourcePackage)
                    val iv = ImageView(this).apply {
                        setImageDrawable(appIcon)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(v: View, outline: Outline) {
                                outline.setOval(0, 0, v.width, v.height)
                            }
                        }
                        clipToOutline = true
                    }
                    hasMedia = true
                    isAppIcon = true
                    return@run iv
                } catch (e: Exception) {
                    Log.e("OverlayService", "앱 아이콘 로드 실패", e)
                }
            }
            TextView(this).apply { text = "HEART"; textSize = 100f }
        }

        instance.view = view

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val sizePx = if (isAppIcon) (appIconSizeDp * resources.displayMetrics.density).toInt()
        else (mediaSizeDp * resources.displayMetrics.density).toInt()
        instance.sizePx = sizePx
        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels
        instance.centerX = (screenWidth - sizePx) / 2
        instance.centerY = (screenHeight - sizePx) / 2

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!dragEnabled) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        val useMarble = entryAnimation && entryMode == "marble"

        val params = WindowManager.LayoutParams(
            if (hasMedia) sizePx else WindowManager.LayoutParams.WRAP_CONTENT,
            if (hasMedia) sizePx else WindowManager.LayoutParams.WRAP_CONTENT,
            type, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (useMarble) (Math.random() * (screenWidth - sizePx)).toInt() else instance.centerX
            y = if (entryAnimation) screenHeight else instance.centerY
        }
        instance.params = params

        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            instance.cleanup()
            return
        }

        if (entryAnimation) {
            if (entryMode == "marble") {
                val bouncePeak = effective(prefs, "bouncePeak", 0.5f, "bouncePeakRandom", 0.3f, 0.8f)
                val gravityScale = effective(prefs, "gravityScale", 1.0f, "gravityScaleRandom", 0.5f, 2.5f)
                val spinScale = effective(prefs, "spinScale", 1.0f, "spinScaleRandom", 0f, 3f)
                val elasticity = effective(prefs, "elasticity", 0.5f, "elasticityRandom", 0f, 1f)
                playMarblePhysics(instance, bouncePeak, gravityScale, spinScale, elasticity)
            } else {
                animateInstanceTo(instance, params.y, instance.centerY, 700, OvershootInterpolator(1.5f))
            }
        }

        if (isVideo && view is VideoView) {
            view.setOnPreparedListener { mp ->
                if (muteVideo) mp.setVolume(0f, 0f)
                view.start()
                val totalMs = max(mp.duration, soundDurationMs).coerceAtLeast(3000)
                scheduleDismiss(instance, totalMs.toLong())
            }
            view.setOnErrorListener { _, _, _ -> instance.cleanup(); true }
        } else {
            scheduleDismiss(instance, soundDurationMs.coerceAtLeast(3000).toLong())
        }

        if (dragEnabled) attachTouchHandler(instance)
    }

    private fun animateInstanceTo(
        instance: OverlayInstance, startY: Int, endY: Int, durationMs: Long,
        interpolator: android.view.animation.Interpolator
    ) {
        val params = instance.params ?: return
        val view = instance.view ?: return
        val animator = ValueAnimator.ofInt(startY, endY)
        animator.duration = durationMs
        animator.interpolator = interpolator
        animator.addUpdateListener { anim ->
            params.y = anim.animatedValue as Int
            try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
        }
        animator.start()
    }

    private fun playMarblePhysics(
        instance: OverlayInstance,
        bouncePeak: Float, gravityScale: Float, spinScale: Float, elasticity: Float
    ) {
        val view = instance.view ?: return
        val params = instance.params ?: return
        val sizePx = instance.sizePx

        val density = resources.displayMetrics.density
        val pxPerMeter = 200f * density
        val g = 9.81f * pxPerMeter * gravityScale

        val startX = params.x.toFloat()
        val startY = screenHeight.toFloat()
        val groundY = (screenHeight - sizePx - 30f * density)

        val peakY = (1f - bouncePeak) * screenHeight
        val peakHeight = startY - peakY
        val v0y = -sqrt(2f * g * peakHeight)

        val firstFlightT = (-v0y + sqrt(v0y * v0y + 2f * g * (groundY - startY))) / g
        val endX = (Math.random() * (screenWidth - sizePx)).toFloat()
        val initialVx = (endX - startX) / firstFlightT

        val maxX = (screenWidth - sizePx).toFloat()
        val minX = 0f
        val radius = sizePx / 2f
        val friction = 800f * density
        val bounceCutoff = 150f * density

        var currentX = startX
        var currentY = startY
        var currentVx = initialVx
        var currentVy = v0y
        var rotation = 0f
        var lastT = 0f
        var rolling = false

        val animator = ValueAnimator.ofFloat(0f, 12f)
        animator.duration = 12000
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { anim ->
            if (instance.isCleanedUp) { anim.cancel(); return@addUpdateListener }
            val t = anim.animatedValue as Float
            val dt = (t - lastT).coerceAtMost(0.04f)
            lastT = t

            if (!rolling) {
                currentVy += g * dt
                currentY += currentVy * dt
                currentX += currentVx * dt
                if (currentX < minX) { currentX = minX; currentVx = -currentVx * elasticity }
                else if (currentX > maxX) { currentX = maxX; currentVx = -currentVx * elasticity }
                if (currentY >= groundY && currentVy > 0) {
                    currentY = groundY
                    currentVy = -currentVy * elasticity
                    if (abs(currentVy) < bounceCutoff) {
                        currentVy = 0f
                        rolling = true
                    }
                }
            } else {
                currentY = groundY
                val sign = if (currentVx >= 0) 1f else -1f
                val newSpeed = (abs(currentVx) - friction * dt).coerceAtLeast(0f)
                currentVx = newSpeed * sign
                currentX += currentVx * dt
                if (currentX < minX) { currentX = minX; currentVx = -currentVx * elasticity }
                else if (currentX > maxX) { currentX = maxX; currentVx = -currentVx * elasticity }
                if (abs(currentVx) < 0.5f) { anim.cancel(); return@addUpdateListener }
            }

            params.x = currentX.toInt()
            params.y = currentY.toInt()
            rotation += (currentVx * dt) / radius * (180f / Math.PI.toFloat()) * spinScale
            view.rotation = rotation

            instance.centerX = currentX.toInt()
            instance.centerY = currentY.toInt()
            try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
        }
        animator.start()
    }

    private fun attachTouchHandler(instance: OverlayInstance) {
        val view = instance.view ?: return
        val params = instance.params ?: return
        var initialTouchX = 0f
        var initialTouchY = 0f
        var initialParamsX = 0
        var initialParamsY = 0
        var downTime = 0L

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialParamsX = params.x
                    initialParamsY = params.y
                    downTime = System.currentTimeMillis()
                    instance.velocityTracker?.recycle()
                    instance.velocityTracker = VelocityTracker.obtain()
                    instance.velocityTracker?.addMovement(event)
                    instance.dismissRunnable?.let { dismissHandler.removeCallbacks(it) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    params.x = initialParamsX + dx
                    params.y = initialParamsY + dy
                    try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
                    instance.velocityTracker?.addMovement(event)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val prefs = getSharedPreferences("rules", Context.MODE_PRIVATE)
                    val flingToDismiss = prefs.getBoolean("flingToDismiss", true)
                    val tapToDismiss = prefs.getBoolean("tapToDismiss", false)
                    val dt = System.currentTimeMillis() - downTime
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val moveDist = sqrt((dx * dx + dy * dy).toDouble())
                    val isTap = dt < 200 && moveDist < 30

                    if (isTap && tapToDismiss) {
                        view.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f)
                            .setDuration(200).withEndAction { instance.cleanup() }.start()
                    } else {
                        instance.velocityTracker?.computeCurrentVelocity(1000)
                        val vx = instance.velocityTracker?.xVelocity ?: 0f
                        val vy = instance.velocityTracker?.yVelocity ?: 0f
                        val speed = sqrt((vx * vx + vy * vy).toDouble())
                        if (flingToDismiss && speed > 1500.0) {
                            flingTo(instance, params.x + (vx * 0.5f).toInt(), params.y + (vy * 0.5f).toInt())
                        } else {
                            springBackTo(instance)
                            scheduleDismiss(instance, 1500)
                        }
                    }
                    instance.velocityTracker?.recycle()
                    instance.velocityTracker = null
                    true
                }
                else -> false
            }
        }
    }

    private fun flingTo(instance: OverlayInstance, targetX: Int, targetY: Int) {
        val view = instance.view ?: return
        val params = instance.params ?: return
        val startX = params.x; val startY = params.y
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 450
        animator.addUpdateListener { anim ->
            if (instance.isCleanedUp) { anim.cancel(); return@addUpdateListener }
            val t = anim.animatedValue as Float
            params.x = (startX + (targetX - startX) * t).toInt()
            params.y = (startY + (targetY - startY) * t).toInt()
            view.alpha = 1f - t * 0.7f
            try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { instance.cleanup() }
        })
        animator.start()
    }

    private fun springBackTo(instance: OverlayInstance) {
        val view = instance.view ?: return
        val params = instance.params ?: return
        val startX = params.x; val startY = params.y
        val targetX = instance.centerX; val targetY = instance.centerY
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 350
        animator.interpolator = OvershootInterpolator(0.8f)
        animator.addUpdateListener { anim ->
            if (instance.isCleanedUp) { anim.cancel(); return@addUpdateListener }
            val t = anim.animatedValue as Float
            params.x = (startX + (targetX - startX) * t).toInt()
            params.y = (startY + (targetY - startY) * t).toInt()
            try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
        }
        animator.start()
    }

    private fun scheduleDismiss(instance: OverlayInstance, delayMs: Long) {
        instance.dismissRunnable?.let { dismissHandler.removeCallbacks(it) }
        val r = Runnable { instance.cleanup() }
        instance.dismissRunnable = r
        dismissHandler.postDelayed(r, delayMs)
    }

    private fun playSound(instance: OverlayInstance, prefs: SharedPreferences): Int {
        val volume = prefs.getFloat("volume", 1.0f)
        val mediaType = prefs.getString("mediaType", "image") ?: "image"
        val useVideoSound = prefs.getBoolean("useVideoSound", true)
        val mediaUriStr = prefs.getString("mediaUri", null)

        if (mediaType == "video" && mediaUriStr != null && useVideoSound) return 0
        if (shouldSkipSound(prefs)) return 0

        val soundUriStr = prefs.getString("soundUri", null)
        val uri = soundUriStr?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        return try {
            val mp = MediaPlayer()
            mp.setDataSource(applicationContext, uri)
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            mp.setOnCompletionListener { it.release() }
            mp.setOnErrorListener { player, _, _ -> player.release(); true }
            mp.prepare()
            mp.setVolume(volume, volume)
            mp.start()
            instance.mediaPlayer = mp
            mp.duration
        } catch (e: Exception) {
            Log.e("OverlayService", "사운드 재생 실패", e)
            0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instances.toList().forEach { it.cleanup() }
        instances.clear()
    }
}