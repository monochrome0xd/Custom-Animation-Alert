package io.github.monochrome0xd.customanimationalert

/*
 * AdMob 배너 — V1에서 제외, V1.1 이후 광고 재도입 시 복원.
 *
 * 복원 절차:
 *  1. app/build.gradle.kts에 `implementation(libs.play.services.ads)` 추가
 *  2. AndroidManifest.xml에 AdMob APPLICATION_ID meta-data 추가 (본인 ID)
 *  3. MainActivity.onCreate에 MobileAds.initialize(applicationContext) {} 호출 추가
 *  4. proguard-rules.pro에 AdMob keep 룰 복원
 *  5. 아래 주석 풀고 TEST_AD_UNIT_ID를 본인 ad unit ID로 교체
 *
 * 원본 구현은 git history에 보존됨 (이 파일의 첫 작성 커밋 참고).
 */

/*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val adWidth = (configuration.screenWidthDp - 48).coerceAtLeast(50)
    var isLoaded by remember { mutableStateOf(false) }

    val adView = remember(adWidth) {
        AdView(context).apply {
            adUnitId = TEST_AD_UNIT_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth))
            adListener = object : AdListener() {
                override fun onAdLoaded() { isLoaded = true }
                override fun onAdFailedToLoad(error: LoadAdError) { isLoaded = false }
                override fun onAdClosed() { }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    if (isLoaded) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            AndroidView(
                factory = { adView },
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(8.dp)
            )
        }
    }
}
*/
