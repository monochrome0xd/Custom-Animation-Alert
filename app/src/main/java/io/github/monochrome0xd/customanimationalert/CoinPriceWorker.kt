package io.github.monochrome0xd.customanimationalert

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 활성 코인 룰(rule.coinId != null && rule.enabled)의 가격을 폴링.
 * 각 룰의 keywords 중 숫자로 파싱 가능한 것 = 임계값.
 * 마지막 폴링 가격(lastPolledPrice)과 현재 가격을 비교해 임계값을 통과(crossing)했을 때만 트리거.
 *
 * 통과 정의: (last < threshold && now >= threshold) OR (last > threshold && now <= threshold)
 * → 가격이 임계값을 가로지를 때 1회 트리거. 그 후 lastPolledPrice 갱신.
 *
 * Android WorkManager 주기 최소값 = 15분. 더 자주 = ForegroundService 필요 (배터리/UX 비용).
 * v1은 15분 + 사용자가 원하면 "지금 확인" 수동 트리거 가능 (TODO).
 */
class CoinPriceWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val rules = RuleStore.loadAll(applicationContext)
                .filter { it.enabled && it.coinId != null }
            if (rules.isEmpty()) {
                Log.d(TAG, "코인 룰 없음 — 폴링 스킵")
                return Result.success()
            }
            val coinIds = rules.mapNotNull { it.coinId }.toSet()
            val prices = CoinRegistry.fetchPrices(coinIds)
            if (prices.isEmpty()) {
                Log.w(TAG, "가격 응답 비어있음 (rate limit 가능성)")
                return Result.retry()
            }

            var updatedAny = false
            val freshRules = RuleStore.loadAll(applicationContext).toMutableList()

            for (rule in rules) {
                val coinId = rule.coinId ?: continue
                val nowPrice = prices[coinId] ?: continue

                // keywords 중 숫자로 파싱 가능한 것을 임계값으로
                val thresholds = rule.keywords.mapNotNull { it.replace(",", "").trim().toFloatOrNull() }
                if (thresholds.isEmpty()) {
                    // 임계값 없으면 가격만 기록하고 트리거 안 함
                    updateInList(freshRules, rule.id) { it.copy(lastPolledPrice = nowPrice) }
                    updatedAny = true
                    continue
                }

                val last = rule.lastPolledPrice
                val triggered = if (last == null) {
                    // 첫 폴링 — 트리거 안 함 (기준선만 잡음)
                    false
                } else {
                    thresholds.any { th ->
                        (last < th && nowPrice >= th) || (last > th && nowPrice <= th)
                    }
                }

                if (triggered) {
                    // 시간대 제한 체크 — 범위 밖이면 트리거 스킵 (가격은 갱신해서 crossing은 유지)
                    val inSchedule = if (!rule.scheduleEnabled) true else {
                        val cal = java.util.Calendar.getInstance()
                        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
                        val s = rule.scheduleStartMin; val e = rule.scheduleEndMin
                        when { s == e -> false; s < e -> nowMin in s until e; else -> nowMin >= s || nowMin < e }
                    }
                    if (inSchedule) {
                        Log.d(TAG, "트리거: ${rule.coinSymbol} last=$last now=$nowPrice thresholds=$thresholds")
                        val intent = Intent(applicationContext, OverlayService::class.java)
                        intent.putExtra("ruleId", rule.id)
                        try { applicationContext.startService(intent) } catch (e: Exception) {
                            Log.e(TAG, "OverlayService 시작 실패", e)
                        }
                    } else {
                        Log.d(TAG, "${rule.coinSymbol} 통과 감지했으나 시간대 밖 → 트리거 스킵")
                    }
                }

                updateInList(freshRules, rule.id) { it.copy(lastPolledPrice = nowPrice) }
                updatedAny = true
            }

            if (updatedAny) RuleStore.saveAll(applicationContext, freshRules)
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "polling failed", e)
            return Result.retry()
        }
    }

    private inline fun updateInList(list: MutableList<Rule>, id: String, transform: (Rule) -> Rule) {
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) list[idx] = transform(list[idx])
    }

    companion object {
        private const val TAG = "CoinPriceWorker"
        private const val WORK_NAME = "coin_price_polling"

        /** 활성 코인 룰이 하나라도 있으면 주기 작업 시작, 없으면 취소. App.onCreate 등에서 호출. */
        fun syncSchedule(context: Context) {
            val hasCoinRules = RuleStore.loadAll(context).any { it.enabled && it.coinId != null }
            val wm = WorkManager.getInstance(context)
            if (!hasCoinRules) {
                wm.cancelUniqueWork(WORK_NAME)
                Log.d(TAG, "코인 룰 없음 — 스케줄 취소")
                return
            }
            val request = PeriodicWorkRequestBuilder<CoinPriceWorker>(15, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            Log.d(TAG, "코인 가격 폴링 스케줄 (15분 주기)")
        }
    }
}
