package io.github.monochromex.customanimationalert

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class AlertNotificationListener : NotificationListenerService() {

    // sbn.key별 마지막 발동 알림 (내용, 시각). 규칙의 쿨타임 내라면 같은 내용은 무시.
    private val lastTriggeredContent = mutableMapOf<String, Pair<String, Long>>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        val flags = sbn.notification.flags
        val isOngoing = flags and Notification.FLAG_ONGOING_EVENT != 0
        val isForegroundSvc = flags and Notification.FLAG_FOREGROUND_SERVICE != 0
        val isGroupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0
        Log.d("AlertListener", "수신 [$packageName] '$title' / ongoing=$isOngoing fgsvc=$isForegroundSvc summary=$isGroupSummary key='${sbn.key}'")

        if (isOngoing) {
            Log.d("AlertListener", "  ↳ ongoing 플래그로 스킵")
            return
        }
        if (isForegroundSvc) {
            Log.d("AlertListener", "  ↳ foregroundService 플래그로 스킵")
            return
        }
        // 그룹 요약 알림 — 같은 알림이 개별로도 오므로 요약은 무시 (중복 발동 방지)
        if (isGroupSummary) {
            Log.d("AlertListener", "  ↳ 그룹 요약으로 스킵")
            return
        }
        // 제목/본문 둘 다 비어있는 알림 — 매칭 무의미 + 중복 발동 원인
        if (title.isEmpty() && text.isEmpty()) {
            Log.d("AlertListener", "  ↳ 빈 알림으로 스킵")
            return
        }

        val rules = RuleStore.loadAll(applicationContext).filter { it.enabled }
        if (rules.isEmpty()) {
            Log.d("AlertListener", "  ↳ 활성 규칙 없음")
            return
        }

        // 매칭되는 모든 규칙 + 구체성 점수 계산.
        // 점수: 앱+키워드 둘다=3, 키워드만=2, 앱만=1
        data class Scored(val rule: Rule, val score: Int)
        val matchedAll = rules.mapNotNull { rule ->
            val activeKeywords = rule.keywords.filter { it.isNotBlank() }
            val keywordActive = rule.keywordEnabled && activeKeywords.isNotEmpty()
            val packageActive = !rule.packageName.isNullOrBlank()
            val keywordMatch = keywordActive && activeKeywords.any { kw ->
                title.contains(kw) || text.contains(kw)
            }
            val packageMatch = packageActive && packageName == rule.packageName
            val matches = when {
                !keywordActive && !packageActive -> false
                keywordActive && packageActive -> keywordMatch && packageMatch
                keywordActive -> keywordMatch
                else -> packageMatch
            }
            if (!matches) return@mapNotNull null
            // 네거티브 키워드: 제목/본문에 하나라도 포함되면 발동 안 함
            val negKeywords = rule.negativeKeywords.filter { it.isNotBlank() }
            if (negKeywords.any { neg -> title.contains(neg) || text.contains(neg) }) {
                Log.d("AlertListener", "  ↳ 규칙 '${rule.name}' 네거티브 키워드 매칭 → 차단")
                return@mapNotNull null
            }
            Scored(rule, when {
                keywordActive && packageActive -> 3
                keywordActive -> 2
                else -> 1
            })
        }

        if (matchedAll.isEmpty()) {
            Log.d("AlertListener", "  ↳ 매칭되는 규칙 없음")
            return
        }

        // 가장 구체적인 규칙 1개 + "같이 재생" 켠 다른 매칭 규칙 모두 발동
        val primary = matchedAll.maxByOrNull { it.score }!!.rule
        val toFire = mutableListOf(primary)
        matchedAll.forEach { scored ->
            if (scored.rule.id != primary.id && scored.rule.playAlongside) {
                toFire.add(scored.rule)
            }
        }

        toFire.forEach { rule ->
            // 규칙별 쿨타임 체크 (각 규칙 독립적으로)
            if (rule.blockSameContentRepeat) {
                val sigKey = "${rule.id}|${sbn.key}"
                val contentSig = "$title|$text"
                val now = System.currentTimeMillis()
                val cooldownMs = rule.sameContentCooldownSec * 1000L
                val last = lastTriggeredContent[sigKey]
                if (last != null && last.first == contentSig && (now - last.second) < cooldownMs) {
                    val remaining = (cooldownMs - (now - last.second)) / 1000
                    Log.d("AlertListener", "  ↳ 규칙 '${rule.name}' 쿨타임 ${remaining}초 남음: 무시")
                    return@forEach
                }
                lastTriggeredContent[sigKey] = contentSig to now
            }

            Log.d("AlertListener", "  ↳ ✓ 발동: '${rule.name}' (id=${rule.id})")
            val overlayIntent = Intent(this, OverlayService::class.java)
            overlayIntent.putExtra("sourcePackage", packageName)
            overlayIntent.putExtra("ruleId", rule.id)
            startService(overlayIntent)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // 알림 해제되면 그 sbn.key가 들어간 모든 규칙의 캐시 비움
        lastTriggeredContent.keys.removeAll { it.endsWith("|${sbn.key}") }
    }
}
