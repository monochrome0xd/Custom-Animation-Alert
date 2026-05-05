package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class AlertNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (packageName == applicationContext.packageName) return

        val prefs = applicationContext.getSharedPreferences("rules", Context.MODE_PRIVATE)
        val keywordEnabled = prefs.getBoolean("keywordEnabled", true)
        val keyword = prefs.getString("keyword", "") ?: ""
        val targetPackage = prefs.getString("packageName", null)

        val keywordActive = keywordEnabled && keyword.isNotBlank()
        val packageActive = !targetPackage.isNullOrBlank()

        Log.d("AlertListener", "▶ 알림 수신 [$packageName] title='$title' text='$text'")
        Log.d("AlertListener", "  설정: keywordEnabled=$keywordEnabled keyword='$keyword' targetPackage='$targetPackage'")
        Log.d("AlertListener", "  활성: keywordActive=$keywordActive packageActive=$packageActive")

        val keywordMatch = keywordActive && (title.contains(keyword) || text.contains(keyword))
        val packageMatch = packageActive && packageName == targetPackage

        val shouldTrigger = when {
            !keywordActive && !packageActive -> {
                Log.d("AlertListener", "  → 둘 다 비활성, 스킵")
                false
            }
            keywordActive && packageActive -> {
                val ok = keywordMatch && packageMatch
                Log.d("AlertListener", "  → AND 모드: keyword=$keywordMatch package=$packageMatch → $ok")
                ok
            }
            keywordActive -> {
                Log.d("AlertListener", "  → 키워드만: $keywordMatch")
                keywordMatch
            }
            else -> {
                Log.d("AlertListener", "  → 앱만: 대상='$targetPackage' 실제='$packageName' → $packageMatch")
                packageMatch
            }
        }

        if (!shouldTrigger) {
            Log.d("AlertListener", "  ✗ 스킵")
            return
        }

        Log.d("AlertListener", "  ✓ 매칭! 효과 발동")

        val overlayIntent = Intent(this, OverlayService::class.java)
        overlayIntent.putExtra("sourcePackage", packageName)
        startService(overlayIntent)
    }
}