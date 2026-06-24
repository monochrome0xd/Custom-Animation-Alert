package io.github.monochrome0xd.customanimationalert

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID


data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val groupName: String = "기본",
    val enabled: Boolean = true,

    val keywordEnabled: Boolean = true,
    val keywords: List<String> = emptyList(),
    val negativeKeywords: List<String> = emptyList(),  // 매칭되면 발동 차단
    val packageName: String? = null,
    val appLabel: String? = null,

    val mediaUri: String? = null,
    val mediaType: String = "image",
    val mediaName: String? = null,
    val mediaLoop: Boolean = true,
    val mediaCircleCrop: Boolean = false,  // 미디어를 원형으로 마스킹 (이미지/GIF/동영상 모두 적용)
    val targetXFraction: Float = 0.5f,  // 미디어가 등장할 화면 X 위치 (0=좌, 1=우)
    val targetYFraction: Float = 0.5f,  // 미디어가 등장할 화면 Y 위치 (0=상, 1=하)
    val targetRotation: Float = 0f,      // 회전 각도 (도)
    val useVideoSound: Boolean = true,
    val mediaSize: Float = 80f,
    val mediaSizeRandom: Boolean = false,
    val previewBoost: Boolean = true,  // 카드 미리보기에서 80dp 미만 미디어를 80dp로 확대해서 표시 (실제 재생엔 영향 없음)
    val appIconSize: Float = 50f,
    val appIconSizeRandom: Boolean = false,

    val soundUri: String? = null,
    val soundName: String? = null,
    val targetLoudnessDb: Float = -14f,  // 자동 정규화 기준점 (내부 전용, UI 노출 X) — 모든 사운드를 이 레벨에 정규화
    val measuredLoudnessDb: Float? = null,  // 임포트 시 측정된 파일의 RMS dBFS (null이면 정규화 안 됨, 폴백)
    val userVolume: Float = 1.0f,  // 사용자 음량 (0.0=무음, 1.0=정규화 기준 그대로). UI는 0-100%로 표시.
    val soundStartMs: Int = 0,           // 컷 시작 (밀리초). 0 = 처음부터
    val soundEndMs: Int = -1,            // 컷 종료 (밀리초). -1 = 끝까지
    val soundDurationMs: Int = 0,        // 임포트 시 측정된 전체 길이. UI 표시용
    val playInVibrate: Boolean = true,
    val playInSilent: Boolean = false,

    val entryAnimation: Boolean = true,
    val entryMode: String = "marble",  // "marble" | "drift"
    // 천천히 이동(drift) 모드 전용 — entryMode == "drift"일 때만 사용
    val driftSpeed: Float = 400f,  // dp/s (50~1000, OverlayService coerceIn 한도와 동일)
    val driftBounceWalls: Boolean = true,
    val driftBounceFloor: Boolean = true,
    val driftBounceCeiling: Boolean = false,
    val driftAccelerate: Boolean = true,    // 처음엔 느리다가 점점 빨라짐 (0%→100% 페이드)
    val driftRotate: Boolean = false,       // 회전 (켜두면 30°/s, 매 사이클 시계/반시계 랜덤)
    val directionalAngleDeg: Float = 0f,    // 방향 이동 모드 — 0°=오른쪽, 90°=아래, 180°=왼쪽, 270°=위
    val animationDurationEnabled: Boolean = false,  // 토글 — OFF면 사운드 길이/충돌로 자연 종료
    val animationDurationSec: Float = 3f,           // 토글 ON일 때만 적용. 강제 종료 시간
    val previewSpeedCap: Boolean = true,            // 카드 미리보기에서 driftSpeed가 150 초과면 150으로 표시
    val bouncePeak: Float = 0.5f,
    val bouncePeakRandom: Boolean = false,
    val gravityScale: Float = 2.5f,
    val gravityScaleRandom: Boolean = false,
    val spinScale: Float = 3.0f,
    val spinScaleRandom: Boolean = false,
    val elasticity: Float = 0.4f,
    val elasticityRandom: Boolean = false,
    val floorOffset: Float = 16f,

    val dragEnabled: Boolean = true,
    val flingToDismiss: Boolean = true,
    val tapToDismiss: Boolean = true,

    // 코인 가격 알람 — 이름이 "$BTC" 등으로 시작하면 자동 세팅됨.
    // keyword가 숫자면 그 가격을 임계값으로 사용. 가격이 통과(crossing)할 때만 트리거.
    val coinSymbol: String? = null,        // "BTC", "ETH" (대문자)
    val coinId: String? = null,            // CoinGecko id ("bitcoin", "ethereum")
    val coinIconUrl: String? = null,       // CoinGecko 아이콘 URL (로컬 다운로드 전 임시 저장)
    val lastPolledPrice: Float? = null,    // 마지막 폴링 가격 (USD), crossing 감지용

    // 사운드만 재생 — true면 오버레이 안 띄우고 사운드만 출력
    val soundOnly: Boolean = false,

    // 시간대 제한 — true면 [scheduleStartMin..scheduleEndMin] 사이에만 발동.
    // 분 단위 (자정부터). 종료가 시작보다 작으면 야간 모드(예: 22→6시 = 밤 10시 ~ 새벽 6시).
    val scheduleEnabled: Boolean = false,
    val scheduleStartMin: Int = 9 * 60,   // 오전 9시
    val scheduleEndMin: Int = 22 * 60,    // 오후 10시

    // 천천히 상승 모드 — 시작 X 위치를 매번 랜덤으로 (지정값 무시)
    val driftRandomStartX: Boolean = false,

    // Peek(모서리에서 살짝 나오는) 모드 — entryMode == "peek"일 때 사용. "random" | "left" | "right" | "top" | "bottom"
    val peekSide: String = "random",
    val peekHoldSec: Float = 1.5f,         // 모서리에서 머무는 시간

    val stackOverlays: Boolean = true,
    val stackMaxCount: Int = 10,  // stackOverlays=true 일 때 동시 표시할 최대 개수 (1~20)
    val wakeScreen: Boolean = false,
    val disableInLandscape: Boolean = true,  // 가로모드에서 발동 비활성화 (기본 ON)
    val landscapeAnimationOnly: Boolean = false,  // disableInLandscape=false 일 때 애니메이션만 재생
    val landscapeSoundOnly: Boolean = false,      // disableInLandscape=false 일 때 사운드만 재생
    val blockSameContentRepeat: Boolean = true,
    val sameContentCooldownSec: Int = 5,  // 같은 내용을 다시 받기까지의 쿨타임 (초)
    val playAlongside: Boolean = false  // 다른 규칙이 가장 구체적이어도 이 규칙이 같이 발동
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("groupName", groupName)
        put("enabled", enabled)
        put("keywordEnabled", keywordEnabled)
        put("keywords", JSONArray().apply { keywords.forEach { put(it) } })
        put("negativeKeywords", JSONArray().apply { negativeKeywords.forEach { put(it) } })
        put("packageName", packageName ?: JSONObject.NULL)
        put("appLabel", appLabel ?: JSONObject.NULL)
        put("mediaUri", mediaUri ?: JSONObject.NULL)
        put("mediaType", mediaType)
        put("mediaName", mediaName ?: JSONObject.NULL)
        put("mediaLoop", mediaLoop)
        put("mediaCircleCrop", mediaCircleCrop)
        put("targetXFraction", targetXFraction.toDouble())
        put("targetYFraction", targetYFraction.toDouble())
        put("targetRotation", targetRotation.toDouble())
        put("useVideoSound", useVideoSound)
        put("mediaSize", mediaSize.toDouble())
        put("mediaSizeRandom", mediaSizeRandom)
        put("previewBoost", previewBoost)
        put("appIconSize", appIconSize.toDouble())
        put("appIconSizeRandom", appIconSizeRandom)
        put("soundUri", soundUri ?: JSONObject.NULL)
        put("soundName", soundName ?: JSONObject.NULL)
        put("targetLoudnessDb", targetLoudnessDb.toDouble())
        put("measuredLoudnessDb", measuredLoudnessDb?.toDouble() ?: JSONObject.NULL)
        put("userVolume", userVolume.toDouble())
        put("soundStartMs", soundStartMs)
        put("soundEndMs", soundEndMs)
        put("soundDurationMs", soundDurationMs)
        put("coinSymbol", coinSymbol ?: JSONObject.NULL)
        put("coinId", coinId ?: JSONObject.NULL)
        put("coinIconUrl", coinIconUrl ?: JSONObject.NULL)
        put("lastPolledPrice", lastPolledPrice?.toDouble() ?: JSONObject.NULL)
        put("soundOnly", soundOnly)
        put("scheduleEnabled", scheduleEnabled)
        put("scheduleStartMin", scheduleStartMin)
        put("scheduleEndMin", scheduleEndMin)
        put("driftRandomStartX", driftRandomStartX)
        put("peekSide", peekSide)
        put("peekHoldSec", peekHoldSec.toDouble())
        put("playInVibrate", playInVibrate)
        put("playInSilent", playInSilent)
        put("entryAnimation", entryAnimation)
        put("entryMode", entryMode)
        put("driftSpeed", driftSpeed.toDouble())
        put("driftBounceWalls", driftBounceWalls)
        put("driftBounceFloor", driftBounceFloor)
        put("driftBounceCeiling", driftBounceCeiling)
        put("driftAccelerate", driftAccelerate)
        put("driftRotate", driftRotate)
        put("directionalAngleDeg", directionalAngleDeg.toDouble())
        put("animationDurationEnabled", animationDurationEnabled)
        put("animationDurationSec", animationDurationSec.toDouble())
        put("previewSpeedCap", previewSpeedCap)
        put("bouncePeak", bouncePeak.toDouble())
        put("bouncePeakRandom", bouncePeakRandom)
        put("gravityScale", gravityScale.toDouble())
        put("gravityScaleRandom", gravityScaleRandom)
        put("spinScale", spinScale.toDouble())
        put("spinScaleRandom", spinScaleRandom)
        put("elasticity", elasticity.toDouble())
        put("elasticityRandom", elasticityRandom)
        put("floorOffset", floorOffset.toDouble())
        put("dragEnabled", dragEnabled)
        put("flingToDismiss", flingToDismiss)
        put("tapToDismiss", tapToDismiss)
        put("stackOverlays", stackOverlays)
        put("stackMaxCount", stackMaxCount)
        put("wakeScreen", wakeScreen)
        put("disableInLandscape", disableInLandscape)
        put("landscapeAnimationOnly", landscapeAnimationOnly)
        put("landscapeSoundOnly", landscapeSoundOnly)
        put("blockSameContentRepeat", blockSameContentRepeat)
        put("sameContentCooldownSec", sameContentCooldownSec)
        put("playAlongside", playAlongside)
    }

    companion object {
        fun fromJson(obj: JSONObject): Rule = Rule(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", ""),
            groupName = obj.optString("groupName", "기본").ifBlank { "기본" },
            enabled = obj.optBoolean("enabled", true),
            keywordEnabled = obj.optBoolean("keywordEnabled", true),
            keywords = run {
                // 새 포맷 (배열) 우선, 없으면 기존 단일 keyword 문자열에서 마이그레이션
                val arr = obj.optJSONArray("keywords")
                if (arr != null) {
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                } else {
                    val single = obj.optString("keyword", "")
                    if (single.isBlank()) emptyList() else listOf(single)
                }
            },
            negativeKeywords = run {
                val arr = obj.optJSONArray("negativeKeywords")
                if (arr != null) (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                else emptyList()
            },
            packageName = if (obj.isNull("packageName")) null else obj.optString("packageName", null),
            appLabel = if (obj.isNull("appLabel")) null else obj.optString("appLabel", null),
            mediaUri = if (obj.isNull("mediaUri")) null else obj.optString("mediaUri", null),
            mediaType = obj.optString("mediaType", "image"),
            mediaName = if (obj.isNull("mediaName")) null else obj.optString("mediaName", null),
            mediaLoop = obj.optBoolean("mediaLoop", true),
            mediaCircleCrop = obj.optBoolean("mediaCircleCrop", false),
            targetXFraction = obj.optDouble("targetXFraction", 0.5).toFloat(),
            targetYFraction = obj.optDouble("targetYFraction", 0.5).toFloat(),
            targetRotation = obj.optDouble("targetRotation", 0.0).toFloat(),
            useVideoSound = obj.optBoolean("useVideoSound", true),
            mediaSize = obj.optDouble("mediaSize", 80.0).toFloat(),
            mediaSizeRandom = obj.optBoolean("mediaSizeRandom", false),
            previewBoost = obj.optBoolean("previewBoost", true),
            appIconSize = obj.optDouble("appIconSize", 100.0).toFloat(),
            appIconSizeRandom = obj.optBoolean("appIconSizeRandom", false),
            soundUri = if (obj.isNull("soundUri")) null else obj.optString("soundUri", null),
            soundName = if (obj.isNull("soundName")) null else obj.optString("soundName", null),
            targetLoudnessDb = if (obj.has("targetLoudnessDb")) {
                obj.optDouble("targetLoudnessDb", -14.0).toFloat()
            } else {
                // 레거시 마이그레이션: 기존 volume(0..1) → dB
                val legacyVolume = obj.optDouble("volume", 1.0)
                if (legacyVolume <= 0.0001) -30f
                else (20.0 * kotlin.math.log10(legacyVolume)).toFloat().coerceIn(-30f, 0f)
            },
            measuredLoudnessDb = if (obj.has("measuredLoudnessDb") && !obj.isNull("measuredLoudnessDb")) {
                obj.optDouble("measuredLoudnessDb").toFloat()
            } else null,
            userVolume = obj.optDouble("userVolume", 1.0).toFloat().coerceIn(0f, 1f),
            soundStartMs = obj.optInt("soundStartMs", 0),
            soundEndMs = obj.optInt("soundEndMs", -1),
            soundDurationMs = obj.optInt("soundDurationMs", 0),
            coinSymbol = if (obj.isNull("coinSymbol")) null else obj.optString("coinSymbol", null),
            coinId = if (obj.isNull("coinId")) null else obj.optString("coinId", null),
            coinIconUrl = if (obj.isNull("coinIconUrl")) null else obj.optString("coinIconUrl", null),
            lastPolledPrice = if (obj.has("lastPolledPrice") && !obj.isNull("lastPolledPrice"))
                obj.optDouble("lastPolledPrice").toFloat() else null,
            soundOnly = obj.optBoolean("soundOnly", false),
            scheduleEnabled = obj.optBoolean("scheduleEnabled", false),
            scheduleStartMin = obj.optInt("scheduleStartMin", 9 * 60),
            scheduleEndMin = obj.optInt("scheduleEndMin", 22 * 60),
            driftRandomStartX = obj.optBoolean("driftRandomStartX", false),
            peekSide = obj.optString("peekSide", "random"),
            peekHoldSec = obj.optDouble("peekHoldSec", 1.5).toFloat(),
            playInVibrate = obj.optBoolean("playInVibrate", false),
            playInSilent = obj.optBoolean("playInSilent", false),
            entryAnimation = obj.optBoolean("entryAnimation", true),
            entryMode = obj.optString("entryMode", "marble"),
            driftSpeed = obj.optDouble("driftSpeed", 400.0).toFloat().coerceIn(50f, 1000f),
            driftBounceWalls = obj.optBoolean("driftBounceWalls", true),
            driftBounceFloor = obj.optBoolean("driftBounceFloor", true),
            driftBounceCeiling = obj.optBoolean("driftBounceCeiling", false),
            driftAccelerate = obj.optBoolean("driftAccelerate", true),
            driftRotate = obj.optBoolean("driftRotate", false),
            directionalAngleDeg = obj.optDouble("directionalAngleDeg", 0.0).toFloat(),
            animationDurationEnabled = obj.optBoolean("animationDurationEnabled", false),
            animationDurationSec = obj.optDouble("animationDurationSec", 3.0).toFloat(),
            previewSpeedCap = obj.optBoolean("previewSpeedCap", true),
            bouncePeak = obj.optDouble("bouncePeak", 0.5).toFloat(),
            bouncePeakRandom = obj.optBoolean("bouncePeakRandom", false),
            gravityScale = obj.optDouble("gravityScale", 1.0).toFloat(),
            gravityScaleRandom = obj.optBoolean("gravityScaleRandom", false),
            spinScale = obj.optDouble("spinScale", 1.0).toFloat(),
            spinScaleRandom = obj.optBoolean("spinScaleRandom", false),
            elasticity = obj.optDouble("elasticity", 0.5).toFloat(),
            elasticityRandom = obj.optBoolean("elasticityRandom", false),
            floorOffset = obj.optDouble("floorOffset", 16.0).toFloat(),
            dragEnabled = obj.optBoolean("dragEnabled", true),
            flingToDismiss = obj.optBoolean("flingToDismiss", true),
            tapToDismiss = obj.optBoolean("tapToDismiss", false),
            stackOverlays = obj.optBoolean("stackOverlays", false),
            stackMaxCount = obj.optInt("stackMaxCount", 10).coerceIn(1, 20),
            wakeScreen = obj.optBoolean("wakeScreen", false),
            disableInLandscape = obj.optBoolean("disableInLandscape", true),
            landscapeAnimationOnly = obj.optBoolean("landscapeAnimationOnly", false),
            landscapeSoundOnly = obj.optBoolean("landscapeSoundOnly", false),
            blockSameContentRepeat = obj.optBoolean("blockSameContentRepeat", true),
            sameContentCooldownSec = obj.optInt("sameContentCooldownSec", 5),
            playAlongside = obj.optBoolean("playAlongside", false)
        )
    }
}

/** 카드 메뉴의 복사/붙여넣기 — 메모리 클립보드. 앱 재시작 시 비워짐. */
object RuleClipboard {
    private val state = androidx.compose.runtime.mutableStateOf<Rule?>(null)
    var copied: Rule?
        get() = state.value
        set(value) { state.value = value }
}

object RuleStore {
    private const val PREFS_NAME = "rules_v2"
    private const val KEY_RULES = "rules_list"
    private const val KEY_MIGRATED = "migrated_from_v1"

    fun loadAll(context: Context): List<Rule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_MIGRATED, false)) {
            migrateFromV1(context)
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        }
        val jsonStr = prefs.getString(KEY_RULES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { Rule.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e("RuleStore", "loadAll 실패", e)
            emptyList()
        }
    }

    fun saveAll(context: Context, rules: List<Rule>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_RULES, arr.toString()).apply()
        // 코인 룰 변화 → 폴링 스케줄 동기화 (활성 코인 룰 없으면 자동 취소)
        try { CoinPriceWorker.syncSchedule(context) } catch (_: Throwable) {}
    }

    fun find(context: Context, id: String): Rule? = loadAll(context).find { it.id == id }

    fun upsert(context: Context, rule: Rule) {
        val list = loadAll(context).toMutableList()
        val idx = list.indexOfFirst { it.id == rule.id }
        if (idx >= 0) list[idx] = rule else list.add(rule)
        saveAll(context, list)
    }

    fun remove(context: Context, id: String) {
        val list = loadAll(context).filter { it.id != id }
        saveAll(context, list)
    }

    private fun migrateFromV1(context: Context) {
        val v1 = context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        if (!v1.contains("keyword") && !v1.contains("packageName")) return

        val migrated = Rule(
            id = UUID.randomUUID().toString(),
            name = v1.getString("appLabel", null) ?: v1.getString("keyword", "기본 규칙") ?: "기본 규칙",
            groupName = "기본",
            enabled = true,
            keywordEnabled = v1.getBoolean("keywordEnabled", true),
            keywords = (v1.getString("keyword", "") ?: "").let { if (it.isBlank()) emptyList() else listOf(it) },
            packageName = v1.getString("packageName", null),
            appLabel = v1.getString("appLabel", null),
            mediaUri = v1.getString("mediaUri", null) ?: v1.getString("imageUri", null),
            mediaType = v1.getString("mediaType", "image") ?: "image",
            mediaName = v1.getString("mediaName", null),
            mediaLoop = v1.getBoolean("mediaLoop", true),
            mediaCircleCrop = v1.getBoolean("mediaCircleCrop", false),
            targetXFraction = v1.getFloat("targetXFraction", 0.5f),
            targetYFraction = v1.getFloat("targetYFraction", 0.5f),
            targetRotation = v1.getFloat("targetRotation", 0f),
            useVideoSound = v1.getBoolean("useVideoSound", true),
            mediaSize = v1.getFloat("mediaSize", 80f),
            mediaSizeRandom = v1.getBoolean("mediaSizeRandom", false),
            previewBoost = v1.getBoolean("previewBoost", true),
            appIconSize = v1.getFloat("appIconSize", 100f),
            appIconSizeRandom = v1.getBoolean("appIconSizeRandom", false),
            soundUri = v1.getString("soundUri", null),
            soundName = v1.getString("soundName", null),
            targetLoudnessDb = run {
                val v = v1.getFloat("volume", 1f).toDouble()
                if (v <= 0.0001) -30f
                else (20.0 * kotlin.math.log10(v)).toFloat().coerceIn(-30f, 0f)
            },
            measuredLoudnessDb = null,
            userVolume = v1.getFloat("userVolume", 1f).coerceIn(0f, 1f),
            playInVibrate = v1.getBoolean("playInVibrate", false),
            playInSilent = v1.getBoolean("playInSilent", false),
            entryAnimation = v1.getBoolean("entryAnimation", true),
            entryMode = v1.getString("entryMode", "marble") ?: "marble",
            driftSpeed = v1.getFloat("driftSpeed", 400f).coerceIn(50f, 1000f),
            driftBounceWalls = v1.getBoolean("driftBounceWalls", true),
            driftBounceFloor = v1.getBoolean("driftBounceFloor", true),
            driftBounceCeiling = v1.getBoolean("driftBounceCeiling", false),
            driftAccelerate = v1.getBoolean("driftAccelerate", true),
            driftRotate = v1.getBoolean("driftRotate", false),
            directionalAngleDeg = v1.getFloat("directionalAngleDeg", 0f),
            animationDurationEnabled = v1.getBoolean("animationDurationEnabled", false),
            animationDurationSec = v1.getFloat("animationDurationSec", 3f),
            previewSpeedCap = v1.getBoolean("previewSpeedCap", true),
            bouncePeak = v1.getFloat("bouncePeak", 0.5f),
            bouncePeakRandom = v1.getBoolean("bouncePeakRandom", false),
            gravityScale = v1.getFloat("gravityScale", 1f),
            gravityScaleRandom = v1.getBoolean("gravityScaleRandom", false),
            spinScale = v1.getFloat("spinScale", 1f),
            spinScaleRandom = v1.getBoolean("spinScaleRandom", false),
            elasticity = v1.getFloat("elasticity", 0.5f),
            elasticityRandom = v1.getBoolean("elasticityRandom", false),
            floorOffset = v1.getFloat("floorOffset", 16f),
            dragEnabled = v1.getBoolean("dragEnabled", true),
            flingToDismiss = v1.getBoolean("flingToDismiss", true),
            tapToDismiss = v1.getBoolean("tapToDismiss", false),
            stackOverlays = v1.getBoolean("stackOverlays", false),
            stackMaxCount = v1.getInt("stackMaxCount", 10).coerceIn(1, 20),
            wakeScreen = v1.getBoolean("wakeScreen", false),
            disableInLandscape = v1.getBoolean("disableInLandscape", true),
            landscapeAnimationOnly = v1.getBoolean("landscapeAnimationOnly", false),
            landscapeSoundOnly = v1.getBoolean("landscapeSoundOnly", false),
            blockSameContentRepeat = v1.getBoolean("blockSameContentRepeat", true),
            sameContentCooldownSec = v1.getInt("sameContentCooldownSec", 5),
            playAlongside = v1.getBoolean("playAlongside", false)
        )
        saveAll(context, listOf(migrated))
        Log.d("RuleStore", "v1 → v2 마이그레이션 완료")
    }
}