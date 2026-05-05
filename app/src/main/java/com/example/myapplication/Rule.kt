package com.example.myapplication

package com.example.myapplication

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "새 규칙",
    val enabled: Boolean = true,

    // 매칭
    val keywordEnabled: Boolean = true,
    val keyword: String = "",
    val packageName: String? = null,
    val appLabel: String? = null,

    // 미디어
    val mediaUri: String? = null,
    val mediaType: String = "image",
    val mediaName: String? = null,
    val useVideoSound: Boolean = true,
    val mediaSize: Float = 250f,
    val mediaSizeRandom: Boolean = false,
    val appIconSize: Float = 100f,
    val appIconSizeRandom: Boolean = false,

    // 사운드
    val soundUri: String? = null,
    val soundName: String? = null,
    val volume: Float = 1.0f,
    val playInVibrate: Boolean = false,
    val playInSilent: Boolean = false,

    // 애니메이션
    val entryAnimation: Boolean = true,
    val entryMode: String = "spring",
    val bouncePeak: Float = 0.5f,
    val bouncePeakRandom: Boolean = false,
    val gravityScale: Float = 1.0f,
    val gravityScaleRandom: Boolean = false,
    val spinScale: Float = 1.0f,
    val spinScaleRandom: Boolean = false,
    val elasticity: Float = 0.5f,
    val elasticityRandom: Boolean = false,

    // 인터랙션
    val dragEnabled: Boolean = true,
    val flingToDismiss: Boolean = true,
    val tapToDismiss: Boolean = false,

    // 알림 처리
    val stackOverlays: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("keywordEnabled", keywordEnabled)
        put("keyword", keyword)
        put("packageName", packageName ?: JSONObject.NULL)
        put("appLabel", appLabel ?: JSONObject.NULL)
        put("mediaUri", mediaUri ?: JSONObject.NULL)
        put("mediaType", mediaType)
        put("mediaName", mediaName ?: JSONObject.NULL)
        put("useVideoSound", useVideoSound)
        put("mediaSize", mediaSize.toDouble())
        put("mediaSizeRandom", mediaSizeRandom)
        put("appIconSize", appIconSize.toDouble())
        put("appIconSizeRandom", appIconSizeRandom)
        put("soundUri", soundUri ?: JSONObject.NULL)
        put("soundName", soundName ?: JSONObject.NULL)
        put("volume", volume.toDouble())
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
        put("dragEnabled", dragEnabled)
        put("flingToDismiss", flingToDismiss)
        put("tapToDismiss", tapToDismiss)
        put("stackOverlays", stackOverlays)
    }

    companion object {
        fun fromJson(obj: JSONObject): Rule = Rule(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "새 규칙"),
            enabled = obj.optBoolean("enabled", true),
            keywordEnabled = obj.optBoolean("keywordEnabled", true),
            keyword = obj.optString("keyword", ""),
            packageName = if (obj.isNull("packageName")) null else obj.optString("packageName", null),
            appLabel = if (obj.isNull("appLabel")) null else obj.optString("appLabel", null),
            mediaUri = if (obj.isNull("mediaUri")) null else obj.optString("mediaUri", null),
            mediaType = obj.optString("mediaType", "image"),
            mediaName = if (obj.isNull("mediaName")) null else obj.optString("mediaName", null),
            useVideoSound = obj.optBoolean("useVideoSound", true),
            mediaSize = obj.optDouble("mediaSize", 250.0).toFloat(),
            mediaSizeRandom = obj.optBoolean("mediaSizeRandom", false),
            appIconSize = obj.optDouble("appIconSize", 100.0).toFloat(),
            appIconSizeRandom = obj.optBoolean("appIconSizeRandom", false),
            soundUri = if (obj.isNull("soundUri")) null else obj.optString("soundUri", null),
            soundName = if (obj.isNull("soundName")) null else obj.optString("soundName", null),
            volume = obj.optDouble("volume", 1.0).toFloat(),
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
            dragEnabled = obj.optBoolean("dragEnabled", true),
            flingToDismiss = obj.optBoolean("flingToDismiss", true),
            tapToDismiss = obj.optBoolean("tapToDismiss", false),
            stackOverlays = obj.optBoolean("stackOverlays", false)
        )
    }
}

object RuleStore {
    private const val PREFS_NAME = "rules_v2"
    private const val KEY_RULES = "rules_list"
    private const val KEY_MIGRATED = "migrated_from_v1"

    fun loadAll(context: Context): List<Rule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 첫 실행 시 v1 단일 규칙 마이그레이션
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

    fun find(context: Context, id: String): Rule? {
        return loadAll(context).find { it.id == id }
    }

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
        // v1에 데이터가 있는지 (단일 규칙 시절 데이터)
        if (!v1.contains("keyword") && !v1.contains("packageName")) return

        val migrated = Rule(
            id = UUID.randomUUID().toString(),
            name = v1.getString("appLabel", null) ?: v1.getString("keyword", "기본 규칙") ?: "기본 규칙",
            enabled = true,
            keywordEnabled = v1.getBoolean("keywordEnabled", true),
            keyword = v1.getString("keyword", "") ?: "",
            packageName = v1.getString("packageName", null),
            appLabel = v1.getString("appLabel", null),
            mediaUri = v1.getString("mediaUri", null) ?: v1.getString("imageUri", null),
            mediaType = v1.getString("mediaType", "image") ?: "image",
            mediaName = v1.getString("mediaName", null),
            useVideoSound = v1.getBoolean("useVideoSound", true),
            mediaSize = v1.getFloat("mediaSize", 250f),
            mediaSizeRandom = v1.getBoolean("mediaSizeRandom", false),
            appIconSize = v1.getFloat("appIconSize", 100f),
            appIconSizeRandom = v1.getBoolean("appIconSizeRandom", false),
            soundUri = v1.getString("soundUri", null),
            soundName = v1.getString("soundName", null),
            volume = v1.getFloat("volume", 1f),
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
            dragEnabled = v1.getBoolean("dragEnabled", true),
            flingToDismiss = v1.getBoolean("flingToDismiss", true),
            tapToDismiss = v1.getBoolean("tapToDismiss", false),
            stackOverlays = v1.getBoolean("stackOverlays", false)
        )
        saveAll(context, listOf(migrated))
        Log.d("RuleStore", "v1 → v2 마이그레이션 완료: ${migrated.name}")
    }
}