package com.example.myapplication

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class AlertNotificationListener : NotificationListenerService() {

    private var lastTriggerTime: Long = 0
    private val debounceMs = 800L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (packageName == applicationContext.packageName) return

        val rules = RuleStore.loadAll(applicationContext).filter { it.enabled }
        if (rules.isEmpty()) return

        // 등록된 모든 활성 규칙에 대해 매칭 시도. 첫 번째 매칭되는 규칙만 발동.
        val matched = rules.firstOrNull { rule ->
            val keywordActive = rule.keywordEnabled && rule.keyword.isNotBlank()
            val packageActive = !rule.packageName.isNullOrBlank()
            val keywordMatch = keywordActive && (title.contains(rule.keyword) || text.contains(rule.keyword))
            val packageMatch = packageActive && packageName == rule.packageName
            when {
                !keywordActive && !packageActive -> false
                keywordActive && packageActive -> keywordMatch && packageMatch
                keywordActive -> keywordMatch
                else -> packageMatch
            }
        } ?: return

        // 글로벌 디바운스
        val now = System.currentTimeMillis()
        if ((now - lastTriggerTime) < debounceMs) {
            Log.d("AlertListener", "디바운스: ${now - lastTriggerTime}ms 이내 재트리거, 무시")
            return
        }
        lastTriggerTime = now

        Log.d("AlertListener", "✓ 매칭 [$packageName] 규칙='${matched.name}' (id=${matched.id})")

        val overlayIntent = Intent(this, OverlayService::class.java)
        overlayIntent.putExtra("sourcePackage", packageName)
        overlayIntent.putExtra("ruleId", matched.id)
        startService(overlayIntent)
    }
}