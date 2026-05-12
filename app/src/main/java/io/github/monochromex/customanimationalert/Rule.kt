package io.github.monochromex.customanimationalert

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
    val targetXFraction: Float = 0.5f,  // 미디어가 등장할 화면 X 위치 (0=좌, 1=우)
    val targetYFraction: Float = 0.5f,  // 미디어가 등장할 화면 Y 위치 (0=상, 1=하)
    val targetRotation: Float = 0f,      // 회전 각도 (도)
    val useVideoSound: Boolean = true,
    val mediaSize: Float = 250f,
    val mediaSizeRandom: Boolean = false,
    val appIconSize: Float = 50f,
    val appIconSizeRandom: Boolean = false,

    val soundUri: String? = null,
    val soundName: String? = null,
    val targetLoudnessDb: Float = -14f,  // 재생 시 목표 음량 (dBFS), 모든 사운드를 이 레벨에 맞춤
    val measuredLoudnessDb: Float? = null,  // 임포트 시 측정된 파일의 RMS dBFS (null이면 정규화 안 됨, 폴백 모드)
    val playInVibrate: Boolean = true,
    val playInSilent: Boolean = false,

    val entryAnimation: Boolean = true,
    val entryMode: String = "marble",
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

    val stackOverlays: Boolean = true,
    val wakeScreen: Boolean = false,
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
        put("targetXFraction", targetXFraction.toDouble())
        put("targetYFraction", targetYFraction.toDouble())
        put("targetRotation", targetRotation.toDouble())
        put("useVideoSound", useVideoSound)
        put("mediaSize", mediaSize.toDouble())
        put("mediaSizeRandom", mediaSizeRandom)
        put("appIconSize", appIconSize.toDouble())
        put("appIconSizeRandom", appIconSizeRandom)
        put("soundUri", soundUri ?: JSONObject.NULL)
        put("soundName", soundName ?: JSONObject.NULL)
        put("targetLoudnessDb", targetLoudnessDb.toDouble())
        put("measuredLoudnessDb", measuredLoudnessDb?.toDouble() ?: JSONObject.NULL)
        put("playInVibrate", playInVibrate)
        put("playInSilent", playInSilent)
        put("entryAnimation", entryAnimation)
        put("entryMode", entryMode)
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
        put("wakeScreen", wakeScreen)
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
            targetXFraction = obj.optDouble("targetXFraction", 0.5).toFloat(),
            targetYFraction = obj.optDouble("targetYFraction", 0.5).toFloat(),
            targetRotation = obj.optDouble("targetRotation", 0.0).toFloat(),
            useVideoSound = obj.optBoolean("useVideoSound", true),
            mediaSize = obj.optDouble("mediaSize", 250.0).toFloat(),
            mediaSizeRandom = obj.optBoolean("mediaSizeRandom", false),
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
            playInVibrate = obj.optBoolean("playInVibrate", false),
            playInSilent = obj.optBoolean("playInSilent", false),
            entryAnimation = obj.optBoolean("entryAnimation", true),
            entryMode = obj.optString("entryMode", "spring"),
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
            wakeScreen = obj.optBoolean("wakeScreen", false),
            blockSameContentRepeat = obj.optBoolean("blockSameContentRepeat", true),
            sameContentCooldownSec = obj.optInt("sameContentCooldownSec", 5),
            playAlongside = obj.optBoolean("playAlongside", false)
        )
    }
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
            targetXFraction = v1.getFloat("targetXFraction", 0.5f),
            targetYFraction = v1.getFloat("targetYFraction", 0.5f),
            targetRotation = v1.getFloat("targetRotation", 0f),
            useVideoSound = v1.getBoolean("useVideoSound", true),
            mediaSize = v1.getFloat("mediaSize", 250f),
            mediaSizeRandom = v1.getBoolean("mediaSizeRandom", false),
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
            playInVibrate = v1.getBoolean("playInVibrate", false),
            playInSilent = v1.getBoolean("playInSilent", false),
            entryAnimation = v1.getBoolean("entryAnimation", true),
            entryMode = v1.getString("entryMode", "spring") ?: "spring",
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
            wakeScreen = v1.getBoolean("wakeScreen", false),
            blockSameContentRepeat = v1.getBoolean("blockSameContentRepeat", true),
            sameContentCooldownSec = v1.getInt("sameContentCooldownSec", 5),
            playAlongside = v1.getBoolean("playAlongside", false)
        )
        saveAll(context, listOf(migrated))
        Log.d("RuleStore", "v1 → v2 마이그레이션 완료")
    }
}