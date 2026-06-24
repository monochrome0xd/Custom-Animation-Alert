package io.github.monochrome0xd.customanimationalert

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
        var hiddenByKeyboard = false  // #42 — 키보드 영역과 겹쳐 숨김 처리된 상태

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
    private val maxInstances = 20  // 절대 상한 (Rule.stackMaxCount는 1~20)
    // 위치 편집 모드용 오버레이 버튼들
    private var editSaveButtonView: View? = null
    private var editResetButtonView: View? = null

    // #42 — 키보드(IME) 감지용 1px 헬퍼 윈도우 + 키보드 상단 Y (MAX = 키보드 없음)
    private var imeHelperView: View? = null
    private var imeLayoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null
    private var keyboardTopY: Int = Int.MAX_VALUE

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
        // ruleJson extra가 있으면 그대로 사용 (마켓 카드에서 즉시 재생 — RuleStore 거치지 않음)
        val ruleJsonExtra = intent?.getStringExtra("ruleJson")

        val rawRule = when {
            ruleJsonExtra != null -> try {
                Rule.fromJson(org.json.JSONObject(ruleJsonExtra))
            } catch (e: Exception) {
                Log.e("OverlayService", "ruleJson 파싱 실패", e)
                Rule()
            }
            ruleId != null -> RuleStore.find(this, ruleId) ?: Rule()
            else -> RuleStore.loadAll(this).firstOrNull { it.enabled } ?: Rule()
        }

        // 가로모드 처리
        val isLandscape = resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val soundOnlyLandscape = isLandscape && !rawRule.disableInLandscape && rawRule.landscapeSoundOnly
        val soundOnlyAlways = rawRule.soundOnly
        val rule = if (isLandscape) {
            if (rawRule.disableInLandscape) {
                Log.d("OverlayService", "가로모드 — disableInLandscape=ON 이라 발동 안 함 (rule=${rawRule.name})")
                return START_NOT_STICKY
            }
            when {
                rawRule.landscapeAnimationOnly -> rawRule.copy(soundUri = null, useVideoSound = false)
                rawRule.landscapeSoundOnly -> rawRule  // showOverlay 호출은 아래에서 스킵
                else -> rawRule
            }
        } else rawRule

        if (!rule.stackOverlays) {
            instances.toList().forEach { it.cleanup(checkStopSelf = false) }
        } else {
            // 규칙별 최대 중첩 개수 (1~20)에 도달하면 가장 오래된 것부터 제거
            val cap = rule.stackMaxCount.coerceIn(1, maxInstances)
            while (instances.size >= cap) {
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
        if (soundOnlyLandscape || soundOnlyAlways) {
            // 사운드만 재생 — 오버레이 뷰 없이 사운드 종료 시 정리
            scheduleDismiss(instance, soundDurationMs.coerceAtLeast(3000).toLong())
        } else {
            showOverlay(instance, rule, soundDurationMs, sourcePackage)
        }
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
                        if (rule.mediaCircleCrop) applyCircleClip(vv)
                        isVideo = true
                        hasMedia = true
                        instance.videoView = vv
                        return@run vv
                    } catch (e: Exception) { Log.e("OverlayService", "동영상 로드 실패", e) }
                } else if (mediaType == "lottie") {
                    try {
                        val lv = com.airbnb.lottie.LottieAnimationView(this).apply {
                            // file:// 또는 content:// URI에서 JSON 읽기
                            val jsonStr = contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                            if (jsonStr != null) {
                                setAnimationFromJson(jsonStr, uri.toString())
                                if (rule.mediaLoop) repeatCount = com.airbnb.lottie.LottieDrawable.INFINITE
                                playAnimation()
                            }
                        }
                        if (rule.mediaCircleCrop) applyCircleClip(lv)
                        hasMedia = true
                        return@run lv
                    } catch (e: Exception) { Log.e("OverlayService", "Lottie 로드 실패", e) }
                } else {
                    try {
                        val iv = ImageView(this).apply {
                            scaleType = if (rule.mediaCircleCrop) ImageView.ScaleType.CENTER_CROP
                                        else ImageView.ScaleType.FIT_CENTER
                        }
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
                        if (rule.mediaCircleCrop) applyCircleClip(iv)
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
            // marble만 화면 아래에서 튀어오름. drift/directional/peek은 사용자가 지정한 위치에서 시작.
            // (#41 — drift가 무조건 화면 맨 아래에서 시작하던 버그 수정: 이제 설정 위치에서 상승)
            y = if (rule.entryAnimation && rule.entryMode == "marble") screenHeight
                else instance.centerY
        }
        instance.params = params

        try { windowManager?.addView(view, params) } catch (e: Exception) {
            instance.cleanup(); return
        }

        // #42 — 키보드 감지 워처 시작 + 초기 가시성 평가 (키보드가 이미 떠 있으면 즉시 숨김)
        ensureKeyboardWatcher()
        applyKeyboardVisibility(instance)

        if (rule.entryAnimation) {
            when (rule.entryMode) {
                "marble" -> startMarbleEntry(instance, rule)
                "drift" -> startDriftEntry(instance, rule)
                "directional" -> startDirectionalEntry(instance, rule)
                "peek" -> startPeekEntry(instance, rule)
                else -> animateInstanceTo(instance, params.y, instance.centerY, 700, OvershootInterpolator(1.5f))
            }
        }

        // animationDurationEnabled가 ON이면 사용자 설정 시간을 강제 종료 시점으로 사용
        val userDurationMs = if (rule.animationDurationEnabled && rule.animationDurationSec > 0f)
            (rule.animationDurationSec * 1000f).toLong() else 0L

        if (isVideo && view is VideoView) {
            view.setOnPreparedListener { mp ->
                instance.videoMediaPlayer = mp
                if (muteVideo) mp.setVolume(0f, 0f)
                view.start()
                val baseMs = max(mp.duration, soundDurationMs).coerceAtLeast(3000).toLong()
                val totalMs = if (userDurationMs > 0L) userDurationMs else baseMs
                scheduleDismiss(instance, totalMs)
            }
            view.setOnErrorListener { _, _, _ -> instance.cleanup(); true }
        } else {
            val baseMs = soundDurationMs.coerceAtLeast(3000).toLong()
            val totalMs = if (userDurationMs > 0L) userDurationMs else baseMs
            scheduleDismiss(instance, totalMs)
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

    /**
     * 천천히 상승 (drift) 모드 — 직선 운동 + 선택적 벽/바닥/천장 충돌.
     * 화면 가장자리(인셋 제외) 끝까지 닿도록 좌표 범위는 0..screen 전체 사용.
     * ValueAnimator를 "ticker"로 사용. 사용자 터치 시 attachTouchHandler가 currentAnimator.cancel().
     */
    private fun startDriftEntry(instance: OverlayInstance, rule: Rule) {
        val params = instance.params ?: return
        val view = instance.view ?: return
        val sizePx = instance.sizePx
        val density = resources.displayMetrics.density
        val targetSpeedPxs = rule.driftSpeed.coerceIn(50f, 1000f) * density  // 슬라이더 상한과 동일

        // 화면 가장자리까지 — 인셋 무시
        val floor = (screenHeight - sizePx).toFloat().coerceAtLeast(0f)
        val ceiling = 0f
        val leftWall = 0f
        val rightWall = (screenWidth - sizePx).toFloat().coerceAtLeast(0f)

        // 시작 위치 랜덤 옵션 — 화면 가로 범위 내 무작위 X
        var x = if (rule.driftRandomStartX) {
            (Math.random() * (screenWidth - sizePx).coerceAtLeast(1)).toFloat()
        } else params.x.toFloat()
        var y = params.y.toFloat()  // 신규 진입은 screenHeight (화면 아래), 드래그 후 재개는 현재 위치
        val angle = (-Math.PI / 2 + (Math.random() - 0.5) * (Math.PI / 3)).toFloat()
        var ux = kotlin.math.cos(angle).toFloat()
        var uy = kotlin.math.sin(angle).toFloat()
        val rotateDir = if (Math.random() < 0.5) 1f else -1f
        val startNanos = System.nanoTime()
        var lastNanos = startNanos
        val accelRampSec = 1.5f

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Long.MAX_VALUE / 2  // 사실상 무한
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
        }
        instance.currentAnimator = animator

        animator.addUpdateListener {
            if (instance.isCleanedUp) {
                animator.cancel()
                return@addUpdateListener
            }
            val now = System.nanoTime()
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            lastNanos = now
            val elapsed = (now - startNanos) / 1_000_000_000f

            val speedScale = if (rule.driftAccelerate) {
                (elapsed / accelRampSec).coerceAtMost(1f)
            } else 1f
            val curSpeed = targetSpeedPxs * speedScale
            val vx = ux * curSpeed
            val vy = uy * curSpeed

            x += vx * dt
            y += vy * dt

            if (rule.driftBounceWalls) {
                if (x < leftWall) { x = leftWall; ux = -ux }
                else if (x > rightWall) { x = rightWall; ux = -ux }
            }
            if (rule.driftBounceCeiling && y < ceiling) { y = ceiling; uy = -uy }
            if (rule.driftBounceFloor && y > floor) { y = floor; uy = -uy }

            // 화면 완전 이탈 (모든 충돌 off + 진행 중) → 자동 정리
            if (x < -sizePx * 2 || x > screenWidth + sizePx ||
                y < -sizePx * 2 || y > screenHeight + sizePx
            ) {
                animator.cancel()
                instance.cleanup()
                return@addUpdateListener
            }

            params.x = x.toInt()
            params.y = y.toInt()
            try { windowManager?.updateViewLayout(view, params) }
            catch (_: Exception) { animator.cancel(); return@addUpdateListener }
            applyKeyboardVisibility(instance)  // #42 — 이동 중 키보드 영역 진입/이탈 시 숨김 토글
            if (rule.driftRotate) {
                view.rotation = (view.rotation + 30f * speedScale * dt) % 360f
            }
        }
        animator.start()
    }

    /**
     * 방향 이동(directional) — 사용자 위치에서 지정 각도 방향으로 직선 이동.
     * 0°=오른쪽, 90°=아래, 180°=왼쪽, 270°=위 (화면 좌표계).
     */
    private fun startDirectionalEntry(instance: OverlayInstance, rule: Rule) {
        val params = instance.params ?: return
        val view = instance.view ?: return
        val sizePx = instance.sizePx
        val density = resources.displayMetrics.density
        val targetSpeedPxs = rule.driftSpeed.coerceIn(50f, 1000f) * density  // 슬라이더 상한과 동일

        val floor = (screenHeight - sizePx).toFloat().coerceAtLeast(0f)
        val ceiling = 0f
        val leftWall = 0f
        val rightWall = (screenWidth - sizePx).toFloat().coerceAtLeast(0f)

        var x = params.x.toFloat()
        var y = params.y.toFloat()  // 사용자 위치 그대로
        val rad = rule.directionalAngleDeg * Math.PI.toFloat() / 180f
        var ux = kotlin.math.cos(rad).toFloat()
        var uy = kotlin.math.sin(rad).toFloat()
        val rotateDir = if (Math.random() < 0.5) 1f else -1f
        val startNanos = System.nanoTime()
        var lastNanos = startNanos
        val accelRampSec = 1.5f

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Long.MAX_VALUE / 2
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
        }
        instance.currentAnimator = animator

        animator.addUpdateListener {
            if (instance.isCleanedUp) { animator.cancel(); return@addUpdateListener }
            val now = System.nanoTime()
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            lastNanos = now
            val elapsed = (now - startNanos) / 1_000_000_000f

            val speedScale = if (rule.driftAccelerate) (elapsed / accelRampSec).coerceAtMost(1f) else 1f
            val curSpeed = targetSpeedPxs * speedScale
            x += ux * curSpeed * dt
            y += uy * curSpeed * dt

            if (rule.driftBounceWalls) {
                if (x < leftWall) { x = leftWall; ux = -ux }
                else if (x > rightWall) { x = rightWall; ux = -ux }
            }
            if (rule.driftBounceCeiling && y < ceiling) { y = ceiling; uy = -uy }
            if (rule.driftBounceFloor && y > floor) { y = floor; uy = -uy }

            if (x < -sizePx * 2 || x > screenWidth + sizePx ||
                y < -sizePx * 2 || y > screenHeight + sizePx
            ) {
                animator.cancel()
                instance.cleanup()
                return@addUpdateListener
            }

            params.x = x.toInt()
            params.y = y.toInt()
            try { windowManager?.updateViewLayout(view, params) }
            catch (_: Exception) { animator.cancel(); return@addUpdateListener }
            applyKeyboardVisibility(instance)  // #42 — 이동 중 키보드 영역 진입/이탈 시 숨김 토글
            if (rule.driftRotate) {
                view.rotation = (view.rotation + 30f * speedScale * rotateDir * dt) % 360f
            }
        }
        animator.start()
    }

    /**
     * Peek 모드 — 4 모서리 중 한 곳에서 절반쯤 나왔다 사라짐.
     * 진행: off-screen → 슬라이드 인 → hold → 슬라이드 아웃 → cleanup.
     */
    private fun startPeekEntry(instance: OverlayInstance, rule: Rule) {
        val params = instance.params ?: return
        val view = instance.view ?: return
        val sizePx = instance.sizePx

        val side = if (rule.peekSide == "random") {
            listOf("top", "bottom", "left", "right").random()
        } else rule.peekSide

        val centerX = (screenWidth - sizePx) / 2
        val centerY = (screenHeight - sizePx) / 2
        val (startX, startY, peekX, peekY) = when (side) {
            "top" -> intArrayOf(centerX, -sizePx, centerX, -sizePx / 2)
            "bottom" -> intArrayOf(centerX, screenHeight, centerX, screenHeight - sizePx / 2)
            "left" -> intArrayOf(-sizePx, centerY, -sizePx / 2, centerY)
            else -> intArrayOf(screenWidth, centerY, screenWidth - sizePx / 2, centerY)
        }.let { listOf(it[0], it[1], it[2], it[3]) }

        // 즉시 시작 위치로 점프 (사용자 눈에 화면 가운데 안 보이게)
        params.x = startX
        params.y = startY
        try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) { return }

        val slideInMs = 400L
        val holdMs = (rule.peekHoldSec * 1000).toLong().coerceAtLeast(100L)
        val slideOutMs = 400L

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = slideInMs + holdMs + slideOutMs
        animator.interpolator = LinearInterpolator()
        instance.currentAnimator = animator
        animator.addUpdateListener { anim ->
            if (instance.isCleanedUp) { anim.cancel(); return@addUpdateListener }
            val elapsedMs = (anim.animatedValue as Float * animator.duration).toLong()
            val (nx, ny) = when {
                elapsedMs < slideInMs -> {
                    val t = elapsedMs.toFloat() / slideInMs
                    val ease = 1f - (1f - t) * (1f - t)
                    (startX + (peekX - startX) * ease).toInt() to
                    (startY + (peekY - startY) * ease).toInt()
                }
                elapsedMs < slideInMs + holdMs -> peekX to peekY
                else -> {
                    val t = (elapsedMs - slideInMs - holdMs).toFloat() / slideOutMs
                    val ease = t * t
                    (peekX + (startX - peekX) * ease).toInt() to
                    (peekY + (startY - peekY) * ease).toInt()
                }
            }
            params.x = nx; params.y = ny
            try { windowManager?.updateViewLayout(view, params) }
            catch (_: Exception) { anim.cancel() }
            applyKeyboardVisibility(instance)  // #42 — peek 이동 중 키보드 영역 숨김 토글
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 사운드가 계속 재생 중이면 그대로 두고, 뷰만 정리
                instance.cleanup()
            }
        })
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

        // 인셋 제외 — 화면 끝까지 사용. (사용자가 floorOffset으로 바닥 여백을 조정 가능)
        val startX = params.x.toFloat()
        val startY = screenHeight.toFloat()
        val groundY = (screenHeight - sizePx - rule.floorOffset * density)
        val peakY = (1f - bouncePeak) * screenHeight
        val peakHeight = startY - peakY
        val v0y = -sqrt(2f * g * peakHeight)

        val firstFlightT = (-v0y + sqrt(v0y * v0y + 2f * g * (groundY - startY))) / g
        val xMaxStart = (screenWidth - sizePx).toFloat()
        val xMinStart = 0f
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

        // 인셋 제외 — 화면 끝까지 사용
        val g = 9.81f * pxPerMeter * gravityScale
        val groundY = (screenHeight - sizePx - rule.floorOffset * density)
        val ceilingY = 0f
        val maxX = (screenWidth - sizePx).toFloat()
        val minX = 0f
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
            applyKeyboardVisibility(instance)  // #42 — 마블 낙하/구르는 중 키보드 영역 숨김 토글
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
                            when (rule.entryMode) {
                                "marble" -> runMarbleSimulation(
                                    instance,
                                    params.x.toFloat(), params.y.toFloat(),
                                    vx, vy, rule
                                )
                                // drift/directional은 사용자가 놓은 위치에서 그대로 관성 유지
                                "drift" -> startDriftEntry(instance, rule)
                                "directional" -> startDirectionalEntry(instance, rule)
                                else -> springBackTo(instance)
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
        // 저장 버튼만 focusable로 — BACK 키를 받아 취소 처리
        val focusableFlags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

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

        // 저장 버튼 (우측 끝) — focusable로 BACK 키 수신
        val saveW = (90 * density).toInt()
        val saveButton = makeFloatingButton(
            text = "저장",
            bgColor = grayBg,
            textSizeSp = 14f
        )
        val saveParams = WindowManager.LayoutParams(
            saveW, btnH, type, focusableFlags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - saveW - sideMargin
            y = btnY
        }
        saveButton.isFocusable = true
        saveButton.isFocusableInTouchMode = true
        saveButton.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                cancelEdit(instance)
                true
            } else false
        }

        try { windowManager?.addView(resetButton, resetParams) } catch (_: Exception) { return }
        editResetButtonView = resetButton
        try { windowManager?.addView(saveButton, saveParams) } catch (_: Exception) { return }
        editSaveButtonView = saveButton
        saveButton.requestFocus()

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

    /** 위치 편집 취소 — 변경사항 버리고 그냥 종료. */
    private fun cancelEdit(instance: OverlayInstance) {
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
            } else if (rule.mediaType == "lottie") {
                try {
                    return com.airbnb.lottie.LottieAnimationView(this).apply {
                        val jsonStr = contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                        if (jsonStr != null) {
                            setAnimationFromJson(jsonStr, uri.toString())
                            repeatCount = com.airbnb.lottie.LottieDrawable.INFINITE
                            playAnimation()
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
        // playSound와 동일한 수식 — finalLinear가 1보다 크면 setVolume은 이미 1.0, 부스트는 LoudnessEnhancer.
        val normalizedGainDb = if (rule.measuredLoudnessDb != null)
            rule.targetLoudnessDb - rule.measuredLoudnessDb!! else 0f
        val normalizedLinear = Math.pow(10.0, normalizedGainDb.toDouble() / 20.0).toFloat()
        val finalLinear = normalizedLinear * rule.userVolume.coerceIn(0f, 1f)
        val mpBase = finalLinear.coerceAtMost(1f)
        val vmpBase = if (muteVideo) 0f else rule.userVolume.coerceIn(0f, 1f)

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

    /** View를 원형으로 마스킹 (이미지/동영상 공통). */
    private fun applyCircleClip(view: View) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setOval(0, 0, v.width, v.height)
            }
        }
        view.clipToOutline = true
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

            // 1) 자동 정규화 — 모든 사운드를 targetLoudnessDb로 보정 (UI엔 노출 X)
            // 2) 그 위에 사용자 음량 (userVolume 0.0~1.0) 곱함
            val normalizedGainDb = if (rule.measuredLoudnessDb != null)
                rule.targetLoudnessDb - rule.measuredLoudnessDb!!
            else 0f  // 측정값 없으면 정규화 스킵 (원본 그대로)
            val normalizedLinear = Math.pow(10.0, normalizedGainDb.toDouble() / 20.0).toFloat()
            val userScale = rule.userVolume.coerceIn(0f, 1f)
            val finalLinear = normalizedLinear * userScale

            if (finalLinear <= 1f) {
                // 감쇠 (또는 정확히 1.0) — MediaPlayer.setVolume만으로 처리
                mp.setVolume(finalLinear, finalLinear)
            } else {
                // 부스트 필요 — setVolume(1.0) + LoudnessEnhancer
                mp.setVolume(1f, 1f)
                val boostDb = (20.0 * kotlin.math.log10(finalLinear.toDouble())).toFloat()
                try {
                    val enhancer = android.media.audiofx.LoudnessEnhancer(mp.audioSessionId)
                    val mb = (boostDb * 100f).toInt().coerceIn(0, 2400)  // 최대 +24dB
                    enhancer.setTargetGain(mb)
                    enhancer.enabled = true
                    instance.loudnessEnhancer = enhancer
                } catch (e: Exception) {
                    Log.w("OverlayService", "LoudnessEnhancer 적용 실패 — 부스트 없이 진행", e)
                }
            }

            // 컷 편집 적용
            val totalMs = mp.duration
            val startMs = rule.soundStartMs.coerceIn(0, totalMs.coerceAtLeast(0))
            val endMs = if (rule.soundEndMs in 1..totalMs) rule.soundEndMs else totalMs
            if (startMs > 0) {
                try { mp.seekTo(startMs) } catch (_: Exception) {}
            }
            mp.start()
            instance.mediaPlayer = mp

            // 종료 시점에서 자동 정지 — endMs가 totalMs보다 작을 때만
            val effectiveDuration = endMs - startMs
            if (endMs < totalMs && effectiveDuration > 0) {
                dismissHandler.postDelayed({
                    try {
                        if (mp.isPlaying) {
                            mp.stop()
                            try { instance.loudnessEnhancer?.release() } catch (_: Exception) {}
                            instance.loudnessEnhancer = null
                            mp.release()
                        }
                    } catch (_: Exception) {}
                }, effectiveDuration.toLong())
            }
            effectiveDuration.coerceAtLeast(0)
        } catch (e: Exception) {
            Log.e("OverlayService", "사운드 재생 실패", e)
            0
        }
    }

    /**
     * #42 — 키보드(IME)가 떠 있을 때 오버레이가 키보드 영역과 겹치면 완전히 숨김.
     * SYSTEM_ALERT_WINDOW 오버레이는 다른 앱의 IME inset을 직접 받지 못하므로,
     * 1px 헬퍼 윈도우의 가시 영역(getWindowVisibleDisplayFrame) 변화로 키보드 높이를 추정한다.
     * 감지가 실패하는 기기에선 keyboardTopY가 MAX_VALUE로 남아 아무것도 숨기지 않음(무해한 폴백).
     */
    private fun ensureKeyboardWatcher() {
        if (imeHelperView != null) return
        val wm = windowManager ?: return
        val v = View(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            1,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        try { wm.addView(v, lp) } catch (_: Exception) { return }
        imeHelperView = v
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val hv = imeHelperView ?: return@OnGlobalLayoutListener
            val r = android.graphics.Rect()
            try { hv.getWindowVisibleDisplayFrame(r) } catch (_: Exception) { return@OnGlobalLayoutListener }
            val screenH = resources.displayMetrics.heightPixels
            val covered = screenH - r.bottom
            // 화면 높이의 15% 이상 하단이 가려지면 키보드로 간주 (내비게이션 바보다 큼)
            keyboardTopY = if (covered > screenH * 0.15f) r.bottom else Int.MAX_VALUE
            applyKeyboardVisibilityToAll()
        }
        v.viewTreeObserver.addOnGlobalLayoutListener(listener)
        imeLayoutListener = listener
    }

    private fun removeKeyboardWatcher() {
        val v = imeHelperView ?: return
        try { imeLayoutListener?.let { v.viewTreeObserver.removeOnGlobalLayoutListener(it) } } catch (_: Exception) {}
        try { windowManager?.removeView(v) } catch (_: Exception) {}
        imeHelperView = null
        imeLayoutListener = null
        keyboardTopY = Int.MAX_VALUE
    }

    private fun applyKeyboardVisibilityToAll() {
        instances.toList().forEach { applyKeyboardVisibility(it) }
    }

    /** 인스턴스가 키보드 영역과 세로로 겹치면 INVISIBLE, 벗어나면 VISIBLE. */
    private fun applyKeyboardVisibility(instance: OverlayInstance) {
        if (instance.isCleanedUp) return
        val view = instance.view ?: return
        val params = instance.params ?: return
        val kbTop = keyboardTopY
        val overlaps = kbTop != Int.MAX_VALUE && (params.y + instance.sizePx) > kbTop
        if (overlaps && !instance.hiddenByKeyboard) {
            instance.hiddenByKeyboard = true
            view.visibility = View.INVISIBLE
        } else if (!overlaps && instance.hiddenByKeyboard) {
            instance.hiddenByKeyboard = false
            view.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeKeyboardWatcher()
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