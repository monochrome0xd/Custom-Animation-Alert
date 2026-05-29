package io.github.monochrome0xd.customanimationalert

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * CoinGecko 무료 API 래퍼.
 *
 * 1) symbol→id 매핑 (예: "BTC" → "bitcoin")
 *    - /coins/markets로 시총 상위 N개 받아옴 (심볼 충돌 시 시총 큰 게 우선)
 *    - 로컬에 7일 캐시
 *
 * 2) 가격 일괄 조회 (/simple/price)
 *
 * 3) 아이콘 URL은 /coins/markets 응답에 포함됨 (image 필드)
 *
 * 무료 한도: 대략 분당 10-30회. simple/price는 ids 콤마 구분으로 한 번에 여러 코인 조회 가능.
 */
object CoinRegistry {
    private const val TAG = "CoinRegistry"
    private const val BASE = "https://api.coingecko.com/api/v3"
    private const val CACHE_FILE = "coingecko_symbols.json"
    private const val CACHE_TTL_MS = 7L * 24 * 3600 * 1000  // 7일
    private const val TOP_N = 500  // 시총 상위 500 — 대부분의 사용자 케이스 커버

    data class CoinInfo(val id: String, val symbol: String, val name: String, val image: String)

    private var cachedMap: Map<String, CoinInfo>? = null  // symbol(대문자) → info

    /** 심볼로 코인 정보 조회. 캐시 없거나 만료면 자동 refresh. */
    suspend fun lookup(context: Context, symbol: String): CoinInfo? {
        val map = ensureMap(context) ?: return null
        return map[symbol.uppercase()]
    }

    private suspend fun ensureMap(context: Context): Map<String, CoinInfo>? {
        cachedMap?.let { return it }
        val cacheFile = File(context.filesDir, CACHE_FILE)
        if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < CACHE_TTL_MS) {
            try {
                val map = parseList(JSONArray(cacheFile.readText()))
                cachedMap = map
                return map
            } catch (e: Exception) {
                Log.w(TAG, "캐시 파싱 실패 — 재다운로드", e)
            }
        }
        return refresh(context, cacheFile)
    }

    private suspend fun refresh(context: Context, cacheFile: File): Map<String, CoinInfo>? = withContext(Dispatchers.IO) {
        try {
            // /coins/markets — 시총 순 정렬, 250개씩 (페이지당). 2 페이지 = 500개.
            val combined = JSONArray()
            for (page in 1..2) {
                val url = "$BASE/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=250&page=$page"
                val arr = JSONArray(httpGet(url))
                for (i in 0 until arr.length()) combined.put(arr.getJSONObject(i))
            }
            cacheFile.writeText(combined.toString())
            val map = parseList(combined)
            cachedMap = map
            Log.d(TAG, "코인 목록 새로고침 완료 (${map.size}개)")
            map
        } catch (e: Exception) {
            Log.e(TAG, "코인 목록 다운로드 실패", e)
            null
        }
    }

    private fun parseList(arr: JSONArray): Map<String, CoinInfo> {
        // 같은 심볼이 여러 개면 시총 큰(=먼저 나온) 게 우선
        val map = mutableMapOf<String, CoinInfo>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val sym = o.optString("symbol").uppercase()
            if (sym.isBlank() || map.containsKey(sym)) continue
            map[sym] = CoinInfo(
                id = o.optString("id"),
                symbol = sym,
                name = o.optString("name"),
                image = o.optString("image")
            )
        }
        return map
    }

    /** 여러 코인 ID의 현재 USD 가격을 한 번에 조회. id → price. */
    suspend fun fetchPrices(coinIds: Collection<String>): Map<String, Float> = withContext(Dispatchers.IO) {
        if (coinIds.isEmpty()) return@withContext emptyMap()
        try {
            val ids = coinIds.joinToString(",") { URLEncoder.encode(it, "UTF-8") }
            val url = "$BASE/simple/price?ids=$ids&vs_currencies=usd"
            val obj = JSONObject(httpGet(url))
            val out = mutableMapOf<String, Float>()
            for (key in obj.keys()) {
                val price = obj.getJSONObject(key).optDouble("usd", Double.NaN)
                if (!price.isNaN()) out[key] = price.toFloat()
            }
            out
        } catch (e: Exception) {
            Log.e(TAG, "가격 조회 실패", e); emptyMap()
        }
    }

    /** 아이콘 URL → 로컬 파일 (filesDir/coin_icons/{symbol}.png). 이미 있으면 그대로 반환. */
    suspend fun downloadIcon(context: Context, symbol: String, imageUrl: String): File? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "coin_icons").apply { mkdirs() }
        val ext = imageUrl.substringBefore('?').substringAfterLast('.', "png").take(5)
        val out = File(dir, "${symbol.uppercase()}.$ext")
        if (out.exists() && out.length() > 0) return@withContext out
        try {
            val conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 20_000
                instanceFollowRedirects = true
            }
            try {
                conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            } finally { conn.disconnect() }
            out
        } catch (e: Exception) {
            Log.e(TAG, "아이콘 다운로드 실패", e); null
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode == 429) throw IOException("Rate limited (429)")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }
}
