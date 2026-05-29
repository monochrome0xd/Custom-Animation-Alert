package io.github.monochrome0xd.customanimationalert

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder

/**
 * Coil의 기본 ImageLoader는 GIF/WebP 디코더가 등록돼있지 않아 마켓 카드에서 GIF가 정지 프레임으로만 보임.
 * API 28+ 는 ImageDecoderDecoder (애니메이티드 GIF/WebP/HEIF 자동), 그 이전은 GifDecoder 폴백.
 * VideoFrameDecoder는 mp4 첫 프레임 추출용 (실제 재생은 ExoPlayer가 따로 함).
 */
class App : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
            add(VideoFrameDecoder.Factory())
        }
        .crossfade(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        // 코인 룰 있으면 가격 폴링 스케줄 (없으면 취소)
        CoinPriceWorker.syncSchedule(this)
    }
}
