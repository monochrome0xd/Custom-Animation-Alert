package io.github.monochromex.customanimationalert

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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

    private inner class OverlayInstance {
        var view: View? = null
        var params: WindowManager.LayoutParams? = null
        var mediaPlayer: MediaPlayer? = null
        var videoMediaPlayer: MediaPlayer? = null  // VideoView 내부 mp 참조 (페이드아웃용)
        var videoView: VideoView? = null
        var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null  // 부스트용 AudioEffect
        var dismissRunnable: Runnable? = null
        var velocityTracker: VelocityTracker? = null
        var currentAnimator: Animator? = null
        var spinAnimator: ValueAnimator? = null
        var lastAngularVelocity: Float = 0f
        var isCleanedUp = false
        var centerX = 0
        var centerY = 0
        var sizePx = 0
        var wakeLock: PowerManager.WakeLock? = null

        fun cleanup(checkStopSelf: Boolean = true) {
            if (isCleanedUp) return
            isCleanedUp = true
            try { currentAnimator?.cancel() } catch (_: Exception) {}
            try { spinAnimator?.cancel() } catch (_: Exception) {}
            dismissRunnable?.let { dismissHandler.removeCallbacks(it) }
            try {
                view?.let { v ->
                    videoView?.stopPlayback()
                    windowManager?.removeView(v)
                }
            } catch (_: Exception) {}
            try { loudnessEnhancer?.release() } catch (_: Exception) {}
            try { mediaPlayer?.release() } catch (_: Exception) {}
            try { velocityTracker?.recycle() } catch (_: Exception) {}
            try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
            view = null
            videoView = null
            mediaPlayer = null
            videoMediaPlayer = null
            loudnessEnhancer = null
            velocityTracker = null
            currentAnimator = null
            spinAnimator = null
            wakeLock = null
            instances.remove(this)
            if (checkStopSelf && instances.isEmpty()) stopSelf()
        }
    }

    private val instances = mutableListOf<OverlayInstance>()
    private var windowManager: WindowManager? = null
    private val dismissHandler = Handler(Looper.getMainLooper())
    private var screenWidth = 0
    private var screenHeight = 0
    private val maxInstances = 10
    // 위치 편집 모드용 오버레이 버튼들
    private var editSaveButtonView: View? = null
    private var editResetButtonView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 위치 편집 모드 — 사용자가 화면 위에서 미디어를 드래그해 위치 조절
        if (intent?.action == "EDIT_POSITION") {
            val ruleId = intent.getStringExtra("ruleId") ?: return START_NOT_STICKY
            startEditPositionMode(ruleId)
            return START_NOT_STICKY
        }

        val ruleId = intent?.getStringExtra("ruleId")
        val sourcePackage = intent?.getStringExtra("sourcePackage")

        val rule = if (ruleId != null) RuleStore.find(this, ruleId) ?: Rule()
        else RuleStore.loadAll(this).firstOrNull { it.enabled } ?: Rule()

        if (!rule.stackOverlays) {
            instances.toList().forEach { it.cleanup(checkStopSelf = false) }
        } else {
            while (instances.size >= maxInstances) {
                instances.first().cleanup(checkStopSelf = false)
            }
        }

        val instance = OverlayInstance()
        instances.add(instance)

        if (rule.wakeScreen) {
            try {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                @Suppress("DEPRECATION")
                val wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                    "CustomAnimationAlert::OverlayWake"
                )
                // 30초 안전 타임아웃. 실제로는 cleanup에서 더 일찍 release됨.
                wl.acquire(30_000L)
                instance.wakeLock = wl
            } catch (e: Exception) {
                Log.e("OverlayService", "WakeLock 획득 실패", e)
            }
        }

        val soundDurationMs = playSound(instance, rule)
        showOverlay(instance, rule, soundDurationMs, sourcePackage)
        return START_NOT_STICKY
    }

    private data class SafeInsets(val top: Int, val bottom: Int, val left: Int, val right: Int)

    // 폰 기종별 안전 영역 자동 감지 — 시스템 바 + 디스플레이 컷아웃(노치/홀펀치/곡면 가장자리) 통합.
    // API 30+: WindowInsets로 정확히 가져옴. 그 이전: 시스템 리소스 폴백.
    private fun getSafeInsets(): SafeInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                val metrics = wm.currentWindowMetrics
                val insets = metrics.windowInsets.getInsets(
                    android.view.WindowInsets.Type.systemBars() or
                            android.view.WindowInsets.Type.displayCutout()
                )
                SafeInsets(insets.top, insets.bottom, insets.left, insets.right)
            } catch (_: Exception) {
                SafeInsets(0, 0, 0, 0)
            }
        }
        @Suppress("InternalInsetResource", "DiscouragedApi")
        val statusResId = resources.getIdentifier("status_bar_height", "dimen", "android")
        @Suppress("InternalInsetResource", "DiscouragedApi")
        val navResId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val top = if (statusResId > 0) resources.getDimensionPixelSize(statusResId) else 0
        val bottom = if (navResId > 0) resources.getDimensionPixelSize(navResId) else 0
        return SafeInsets(top, bottom, 0, 0)
    }

    private fun shouldSkipSound(rule: Rule): Boolean {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> !rule.playInVibrate
            AudioManager.RINGER_MODE_SILENT -> !rule.playInSilent
            else -> false
        }
    }

    private fun effective(value: Float, isRandom: Boolean, minVal: Float, maxVal: Float): Float {
        return if (isRandom) (Math.random() * (maxVal - minVal) + minVal).toFloat() else value
    }

    private fun showOverlay(
        instance: OverlayInstance, rule: Rule,
        soundDurationMs: Int, sourcePackage: String?
    ) {
        val mediaUriStr = rule.mediaUri
        val mediaType = rule.mediaType
        val muteVideo = !rule.useVideoSound || shouldSkipSound(rule)

        val mediaSizeDp = effective(rule.mediaSize, rule.mediaSizeRandom, 50f, 600f)
        val appIconSizeDp = effective(rule.appIconSize, rule.appIconSizeRandom, 50f, 200f)

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
                    } catch (e: Exception) { Log.e("OverlayService", "동영상 로드 실패", e) }
                } else {
                    try {
                        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(contentResolver, uri)
                            val drawable = ImageDecoder.decodeDrawable(source)
                            iv.setImageDrawable(drawable)
                            if (drawable is AnimatedImageDrawable) {
                                // setRepeatCount은 API 31+. 이전 버전은 파일 기본 반복 횟수(보통 무한) 사용.
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    drawable.repeatCount = if (rule.mediaLoop)
                                        AnimatedImageDrawable.REPEAT_INFINITE else 0
                                }
                                drawable.start()
                            }
                        } else iv.setImageURI(uri)
                        if (iv.drawable != null) { hasMedia = true; return@run iv }
                    } catch (e: Exception) { Log.e("OverlayService", "이미지 로드 실패", e) }
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
                } catch (e: Exception) { Log.e("OverlayService", "앱 아이콘 로드 실패", e) }
            }
            // 미디어/앱 모두 없음 → 랜덤 이모지 폴백 (rule.id 기반 결정적)
            hasMedia = true
            TextView(this).apply {
                text = fallbackEmojiFor(rule.id)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, mediaSizeDp * 0.7f)
                gravity = Gravity.CENTER
            }
        }

        instance.view = view

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val sizePx = if (isAppIcon) (appIconSizeDp * resources.displayMetrics.density).toInt()
        else (mediaSizeDp * resources.displayMetrics.density).toInt()
        instance.sizePx = sizePx
        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels
        // 사용자가 위치 조절로 저장한 좌표를 기본 안착 위치로 사용 (정적 표시·캐치 후 스프링백에 자동 적용)
        instance.centerX = (rule.targetXFraction * screenWidth - sizePx / 2f)
            .toInt().coerceIn(0, (screenWidth - sizePx).coerceAtLeast(0))
        instance.centerY = (rule.targetYFraction * screenHeight - sizePx / 2f)
            .toInt().coerceIn(0, (screenHeight - sizePx).coerceAtLeast(0))
        // 회전 적용
        view.rotation = rule.targetRotation

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!rule.dragEnabled) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (rule.wakeScreen) {
            // 잠금화면 위에 표시 + 화면 켜기 + 켜진 동안 자동 꺼짐 방지
            @Suppress("DEPRECATION")
            flags = flags or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        }

        val useMarble = rule.entryAnimation && rule.entryMode == "marble"

        val params = WindowManager.LayoutParams(
            if (hasMedia) sizePx else WindowManager.LayoutParams.WRAP_CONTENT,
            if (hasMedia) sizePx else WindowManager.LayoutParams.WRAP_CONTENT,
            type, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 마블 시작 X도 사용자 위치 기준 (랜덤 X 대신 centerX = 사용자 targetX)
            x = instance.centerX
            y = if (rule.entryAnimation) screenHeight else instance.centerY
        }
        instance.params = params

        try { windowManager?.addView(view, params) } catch (e: Exception) {
            instance.cleanup(); return
        }

        if (rule.entryAnimation) {
            if (rule.entryMode == "marble") {
                startMarbleEntry(instance, rule)
            } else {
                animateInstanceTo(instance, params.y, instance.centerY, 700, OvershootInterpolator(1.5f))
            }
        }

        if (isVideo && view is VideoView) {
            view.setOnPreparedListener { mp ->
                instance.videoMediaPlayer = mp
                if (muteVideo) mp.setVolume(0f, 0f)
                view.start()
                val totalMs = max(mp.duration, soundDurationMs).coerceAtLeast(3000)
                scheduleDismiss(instance, totalMs.toLong())
            }
            view.setOnErrorListener { _, _, _ -> instance.cleanup(); true }
        } else {
            scheduleDismiss(instance, soundDurationMs.coerceAtLeast(3000).toLong())
        }

        if (rule.dragEnabled) attachTouchHandler(instance, rule)
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
        instance.currentAnimator = animator
        animator.start()
    }

    private fun startMarbleEntry(instance: OverlayInstance, rule: Rule) {
        val params = instance.params ?: return
        val sizePx = instance.sizePx
        val density = resources.displayMetrics.density
        val pxPerMeter = 200f * density
        val gravityScale = effective(rule.gravityScale, rule.gravityScaleRandom, 0.5f, 2.5f)
        val g = 9.81f * pxPerMeter * gravityScale
        val bouncePeak = effective(rule.bouncePeak, rule.bouncePeakRandom, 0.3f, 0.8f)

        val insets = getSafeInsets()
        val startX = params.x.toFloat()
        val startY = screenHeight.toFloat()
        val groundY = (screenHeight - sizePx - insets.bottom - rule.floorOffset * density)
        val peakY = (1f - bouncePeak) * screenHeight
        val peakHeight = startY - peakY
        val v0y = -sqrt(2f * g * peakHeight)

        val firstFlightT = (-v0y + sqrt(v0y * v0y + 2f * g * (groundY - startY))) / g
        val xMaxStart = (screenWidth - sizePx - insets.right).toFloat()
        val xMinStart = insets.left.toFloat()
        val endX = (Math.random() * (xMaxStart - xMinStart) + xMinStart).toFloat()
        val initialVx = (endX - startX) / firstFlightT

        runMarbleSimulation(instance, startX, startY, initialVx, v0y, rule)
    }

    private fun runMarbleSimulation(
        instance: OverlayInstance,
        startX: Float, startY: Float,
        initialVx: Float, initialVy: Float,
        rule: Rule
    ) {
        val view = instance.view ?: return
        val params = instance.params ?: return
        val sizePx = instance.sizePx

        val density = resources.displayMetrics.density
        val pxPerMeter = 200f * density
        val gravityScale = effective(rule.gravityScale, rule.gravityScaleRandom, 0.5f, 2.5f)
        val spinScale = effective(rule.spinScale, rule.spinScaleRandom, 0f, 3f)
        val elasticity = effective(rule.elasticity, rule.elasticityRandom, 0f, 1f)

        val insets = getSafeInsets()
        val g = 9.81f * pxPerMeter * gravityScale
        val groundY = (screenHeight - sizePx - insets.bottom - rule.floorOffset * density)
        val ceilingY = insets.top.toFloat()
        val maxX = (screenWidth - sizePx - insets.right).toFloat()
        val minX = insets.left.toFloat()
        val radius = sizePx / 2f
        val friction = 800f * density
        val bounceCutoff = 150f * density

        var currentX = startX
        var currentY = startY
        var currentVx = initialVx
        var currentVy = initialVy
        var rotation = view.rotation
        var lastT = 0f
        var rolling = currentY >= groundY - 5f && abs(currentVy) < bounceCutoff

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
                // 천장 충돌 — status bar 아래 위치에서 튕김 (status bar 영역 침범 안 함)
                if (currentY < ceilingY && currentVy < 0) {
                    currentY = ceilingY
                    currentVy = -currentVy * elasticity
                }
                if (currentY >= groundY && currentVy > 0) {
                    currentY = groundY
                    currentVy = -currentVy * elasticity
                    if (abs(currentVy) < bounceCutoff) { currentVy = 0f; rolling = true }
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
            val angularVelDeg = (currentVx / radius) * (180f / Math.PI.toFloat()) * spinScale
            instance.lastAngularVelocity = angularVelDeg
            rotation += angularVelDeg * dt
            view.rotation = rotation
            instance.centerX = currentX.toInt()
            instance.centerY = currentY.toInt()
            try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
        }
        instance.currentAnimator = animator
        animator.start()
    }

    private fun attachTouchHandler(instance: OverlayInstance, rule: Rule) {
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
                    val omega = instance.lastAngularVelocity
                    instance.currentAnimator?.cancel()
                    instance.spinAnimator?.cancel()
                    if (abs(omega) > 1f) {
                        spinDownAfterCatch(instance, omega)
                    }
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
                    instance.spinAnimator?.cancel()
                    val dt = System.currentTimeMillis() - downTime
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val moveDist = sqrt((dx * dx + dy * dy).toDouble())
                    val isTap = dt < 200 && moveDist < 30

                    if (isTap && rule.tapToDismiss) {
                        fadeAudioOut(instance, rule, 250)
                        view.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f)
                            .setDuration(250).withEndAction { instance.cleanup() }.start()
                    } else {
                        instance.velocityTracker?.computeCurrentVelocity(1000)
                        val vx = instance.velocityTracker?.xVelocity ?: 0f
                        val vy = instance.velocityTracker?.yVelocity ?: 0f
                        val speed = sqrt((vx * vx + vy * vy).toDouble())
                        // 위로 던지는 건 dismiss 안 함 — 마블 물리로 천장 튕김
                        val isUpwardFling = vy < -500f
                        if (rule.flingToDismiss && speed > 1500.0 && !isUpwardFling) {
                            fadeAudioOut(instance, rule, 450)
                            flingTo(instance, params.x + (vx * 0.5f).toInt(), params.y + (vy * 0.5f).toInt())
                        } else {
                            if (rule.entryMode == "marble") {
                                runMarbleSimulation(
                                    instance,
                                    params.x.toFloat(), params.y.toFloat(),
                                    vx, vy, rule
                                )
                            } else {
                                springBackTo(instance)
                            }
                            // 사용자가 만지작거리고 닫지 않았으면 시간 연장 — 충분히 보고 결정할 시간 줌
                            scheduleDismiss(instance, 6000)
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

    private fun spinDownAfterCatch(instance: OverlayInstance, initialOmega: Float) {
        val view = instance.view ?: return
        var omega = initialOmega
        var rotation = view.rotation
        var lastT = 0f
        val decayCoef = 8f

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 700
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { anim ->
            if (instance.isCleanedUp) { anim.cancel(); return@addUpdateListener }
            val t = anim.animatedValue as Float
            val dt = (t - lastT)
            lastT = t

            omega *= (1f - decayCoef * dt).coerceAtLeast(0f)
            rotation += omega * dt
            view.rotation = rotation
            instance.lastAngularVelocity = omega

            if (abs(omega) < 1f) anim.cancel()
        }
        instance.spinAnimator = animator
        animator.start()
    }

    // 위치 편집 모드 — 미디어를 화면에 띄우고 사용자가 드래그해서 위치 조절. 길게 누르면 저장.
    private fun startEditPositionMode(ruleId: String) {
        val rule = RuleStore.find(this, ruleId) ?: return
        instances.toList().forEach { it.cleanup(checkStopSelf = false) }

        val instance = OverlayInstance()
        instances.add(instance)

        val density = resources.displayMetrics.density
        val sizeDp = effective(rule.mediaSize, rule.mediaSizeRandom, 50f, 600f)
        val isAppIcon = rule.mediaUri == null && rule.packageName != null
        val effSizeDp = if (isAppIcon) effective(rule.appIconSize, rule.appIconSizeRandom, 50f, 200f) else sizeDp
        val sizePx = (effSizeDp * density).toInt()
        instance.sizePx = sizePx

        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels

        // 저장된 위치에서 시작
        val initialX = (rule.targetXFraction * screenWidth - sizePx / 2f).toInt()
        val initialY = (rule.targetYFraction * screenHeight - sizePx / 2f).toInt()

        // 미디어 뷰 생성 (간소화 버전 — 이미지/비디오/앱 아이콘 분기)
        val view: View = createMediaViewForEdit(rule)
        view.rotation = rule.targetRotation
        instance.view = view

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            sizePx, sizePx, type, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }
        instance.params = params

        try { windowManager?.addView(view, params) } catch (e: Exception) {
            instance.cleanup(); return
        }

        attachEditTouchHandler(instance)
        showEditSaveButton(instance, ruleId)
    }

    private fun showEditSaveButton(instance: OverlayInstance, ruleId: String) {
        val density = resources.displayMetrics.density
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val btnH = (52 * density).toInt()
        val btnY = screenHeight - getSafeInsets().bottom - btnH - (40 * density).toInt()
        val sideMargin = (24 * density).toInt()
        val grayBg = AndroidColor.argb(200, 100, 100, 105)

        // 초기화 버튼 (좌측 끝)
        val resetW = (90 * density).toInt()
        val resetButton = makeFloatingButton(
            text = "초기화",
            bgColor = grayBg,
            textSizeSp = 14f
        )
        val resetParams = WindowManager.LayoutParams(
            resetW, btnH, type, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = sideMargin
            y = btnY
        }

        // 저장 버튼 (우측 끝)
        val saveW = (90 * density).toInt()
        val saveButton = makeFloatingButton(
            text = "저장",
            bgColor = grayBg,
            textSizeSp = 14f
        )
        val saveParams = WindowManager.LayoutParams(
            saveW, btnH, type, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - saveW - sideMargin
            y = btnY
        }

        try { windowManager?.addView(resetButton, resetParams) } catch (_: Exception) { return }
        editResetButtonView = resetButton
        try { windowManager?.addView(saveButton, saveParams) } catch (_: Exception) { return }
        editSaveButtonView = saveButton

        attachDraggableButton(resetButton, resetParams) {
            // 미디어를 화면 가운데로 리셋 (저장은 안 함 — 사용자가 저장 버튼 누를 때까지)
            val mediaParams = instance.params ?: return@attachDraggableButton
            mediaParams.x = (screenWidth - instance.sizePx) / 2
            mediaParams.y = (screenHeight - instance.sizePx) / 2
            try { windowManager?.updateViewLayout(instance.view, mediaParams) } catch (_: Exception) {}
        }
        attachDraggableButton(saveButton, saveParams) {
            saveAndDismissEdit(instance, ruleId)
        }
    }

    private fun makeFloatingButton(
        text: String,
        bgColor: Int,
        textSizeSp: Float,
        bold: Boolean = false
    ): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            setTextColor(AndroidColor.WHITE)
            textSize = textSizeSp
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 28f * density
                setColor(bgColor)
            }
        }
    }

    private fun attachDraggableButton(
        view: View,
        btnParams: WindowManager.LayoutParams,
        onTap: () -> Unit
    ) {
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
                    initialParamsX = btnParams.x
                    initialParamsY = btnParams.y
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    btnParams.x = initialParamsX + dx
                    btnParams.y = initialParamsY + dy
                    try { windowManager?.updateViewLayout(view, btnParams) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dt = System.currentTimeMillis() - downTime
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val moveDist = sqrt((dx * dx + dy * dy).toDouble())
                    if (dt < 300 && moveDist < 30) onTap()
                    true
                }
                else -> false
            }
        }
    }

    private fun saveAndDismissEdit(instance: OverlayInstance, ruleId: String) {
        val rule = RuleStore.find(this, ruleId)
        val params = instance.params
        if (rule != null && params != null) {
            val centerX = (params.x + instance.sizePx / 2f) / screenWidth.toFloat()
            val centerY = (params.y + instance.sizePx / 2f) / screenHeight.toFloat()
            RuleStore.upsert(this, rule.copy(
                targetXFraction = centerX.coerceIn(0f, 1f),
                targetYFraction = centerY.coerceIn(0f, 1f)
            ))
            // UI에 알림 — RuleEditScreen이 로컬 state 새로고침
            RuleUpdateBus.notifyUpdated(ruleId)
        }
        editSaveButtonView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            editSaveButtonView = null
        }
        editResetButtonView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            editResetButtonView = null
        }
        instance.cleanup()
    }

    private fun createMediaViewForEdit(rule: Rule): View {
        val mediaUri = rule.mediaUri
        if (mediaUri != null) {
            val uri = Uri.parse(mediaUri)
            if (rule.mediaType == "video") {
                try {
                    return VideoView(this).apply {
                        setVideoURI(uri)
                        setOnPreparedListener { mp ->
                            mp.setVolume(0f, 0f); mp.isLooping = true; start()
                        }
                    }
                } catch (_: Exception) {}
            } else {
                try {
                    return ImageView(this).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val src = ImageDecoder.createSource(contentResolver, uri)
                            val drawable = ImageDecoder.decodeDrawable(src)
                            setImageDrawable(drawable)
                            if (drawable is AnimatedImageDrawable) drawable.start()
                        } else setImageURI(uri)
                    }
                } catch (_: Exception) {}
            }
        }
        rule.packageName?.let { pkg ->
            try {
                return ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageDrawable(packageManager.getApplicationIcon(pkg))
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(v: View, outline: Outline) {
                            outline.setOval(0, 0, v.width, v.height)
                        }
                    }
                    clipToOutline = true
                }
            } catch (_: Exception) {}
        }
        return TextView(this).apply { text = "?"; textSize = 80f }
    }

    private fun attachEditTouchHandler(instance: OverlayInstance) {
        val view = instance.view ?: return
        val params = instance.params ?: return
        var initialTouchX = 0f
        var initialTouchY = 0f
        var initialParamsX = 0
        var initialParamsY = 0
        val snapThreshold = (16 * resources.displayMetrics.density).toInt()

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialParamsX = params.x
                    initialParamsY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    val maxX = (screenWidth - instance.sizePx).coerceAtLeast(0)
                    val maxY = (screenHeight - instance.sizePx).coerceAtLeast(0)
                    var newX = (initialParamsX + dx).coerceIn(0, maxX)
                    var newY = (initialParamsY + dy).coerceIn(0, maxY)
                    // 가장자리 근처면 자동으로 달라붙음 — 정확히 끝에 닿게
                    if (newX < snapThreshold) newX = 0
                    else if (newX > maxX - snapThreshold) newX = maxX
                    if (newY < snapThreshold) newY = 0
                    else if (newY > maxY - snapThreshold) newY = maxY
                    params.x = newX
                    params.y = newY
                    try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    // 닫는 인터랙션 시 사운드를 점진적으로 줄여 갑작스런 뚝 끊김 방지.
    private fun fadeAudioOut(instance: OverlayInstance, rule: Rule, fadeDurationMs: Long) {
        val mp = instance.mediaPlayer
        val vmp = instance.videoMediaPlayer
        if (mp == null && vmp == null) return

        val muteVideo = !rule.useVideoSound || shouldSkipSound(rule)
        // 재생 시점의 setVolume 값을 그대로 페이드 시작점으로 사용.
        // gain <= 0이면 감쇠 스케일, gain > 0이면 1.0 (부스트는 LoudnessEnhancer 쪽).
        val gainDb = if (rule.measuredLoudnessDb != null) {
            rule.targetLoudnessDb - rule.measuredLoudnessDb!!
        } else {
            rule.targetLoudnessDb
        }
        val mpBase = if (gainDb <= 0f) {
            Math.pow(10.0, gainDb.toDouble() / 20.0).toFloat().coerceIn(0f, 1f)
        } else 1f
        val vmpBase = if (muteVideo) 0f else 1f

        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = fadeDurationMs
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                try { mp?.setVolume(t * mpBase, t * mpBase) } catch (_: Exception) {}
                try { vmp?.setVolume(t * vmpBase, t * vmpBase) } catch (_: Exception) {}
            }
        }
        animator.start()
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
        instance.currentAnimator = animator
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
        instance.currentAnimator = animator
        animator.start()
    }

    private fun scheduleDismiss(instance: OverlayInstance, delayMs: Long) {
        instance.dismissRunnable?.let { dismissHandler.removeCallbacks(it) }
        val r = Runnable { instance.cleanup() }
        instance.dismissRunnable = r
        dismissHandler.postDelayed(r, delayMs)
    }

    private fun playSound(instance: OverlayInstance, rule: Rule): Int {
        if (rule.mediaType == "video" && rule.mediaUri != null && rule.useVideoSound) return 0
        if (shouldSkipSound(rule)) return 0

        val uri = rule.soundUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        return try {
            val mp = MediaPlayer()
            mp.setDataSource(applicationContext, uri)
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            mp.setOnCompletionListener {
                try { instance.loudnessEnhancer?.release() } catch (_: Exception) {}
                instance.loudnessEnhancer = null
                it.release()
            }
            mp.setOnErrorListener { player, _, _ ->
                try { instance.loudnessEnhancer?.release() } catch (_: Exception) {}
                instance.loudnessEnhancer = null
                player.release()
                true
            }
            mp.prepare()

            // 정규화 게인 계산:
            //   measured(원본 음량) → target(목표 음량) 차이만큼 보정
            //   measured == null이면 폴백: targetLoudnessDb를 그대로 감쇠로 사용
            val gainDb = if (rule.measuredLoudnessDb != null) {
                rule.targetLoudnessDb - rule.measuredLoudnessDb!!
            } else {
                rule.targetLoudnessDb
            }

            if (gainDb <= 0f) {
                // 감쇠만: MediaPlayer.setVolume (0..1)
                val scale = Math.pow(10.0, gainDb.toDouble() / 20.0).toFloat().coerceIn(0f, 1f)
                mp.setVolume(scale, scale)
            } else {
                // 부스트: setVolume(1.0) + LoudnessEnhancer
                mp.setVolume(1f, 1f)
                try {
                    val enhancer = android.media.audiofx.LoudnessEnhancer(mp.audioSessionId)
                    // 1 dB = 100 mB. 안전한 부스트 한계 ~24 dB (그 이상은 클리핑 위험)
                    val mb = (gainDb * 100f).toInt().coerceIn(0, 2400)
                    enhancer.setTargetGain(mb)
                    enhancer.enabled = true
                    instance.loudnessEnhancer = enhancer
                } catch (e: Exception) {
                    Log.w("OverlayService", "LoudnessEnhancer 적용 실패 — 부스트 없이 진행", e)
                }
            }

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
        instances.toList().forEach { it.cleanup(checkStopSelf = false) }
        instances.clear()
        editSaveButtonView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            editSaveButtonView = null
        }
        editResetButtonView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            editResetButtonView = null
        }
    }
}