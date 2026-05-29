package io.github.monochrome0xd.customanimationalert

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 오디오 파일을 디코드해서 시간축 따라 RMS 진폭을 추출.
 * 결과는 [0..1] 범위의 Float 배열, 길이 = bins (기본 256).
 *
 * 처리 흐름:
 *  1. MediaExtractor로 첫 오디오 트랙 선택
 *  2. MediaCodec로 PCM(16-bit signed)으로 디코드
 *  3. 전체 시간을 bins개 구간으로 나눠 각 구간의 RMS 계산
 *  4. 채널이 여러 개면 평균. 16-bit → -1..1 정규화 후 RMS
 *
 * 반환 데이터 클래스에 durationMs도 포함.
 */
object WaveformExtractor {
    private const val TAG = "WaveformExtractor"
    const val DEFAULT_BINS = 256

    data class Result(val rms: FloatArray, val durationMs: Int)

    suspend fun extract(context: Context, uri: Uri, bins: Int = DEFAULT_BINS): Result? = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            // 첫 오디오 트랙 선택
            var trackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (trackIndex < 0 || inputFormat == null) {
                Log.w(TAG, "오디오 트랙 없음")
                return@withContext null
            }
            extractor.selectTrack(trackIndex)
            val durationUs = inputFormat.getLong(MediaFormat.KEY_DURATION)
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            // bins 개의 누적 버킷
            val sumSq = DoubleArray(bins)
            val cnt = LongArray(bins)
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputBufIdx = codec.dequeueInputBuffer(10_000)
                    if (inputBufIdx >= 0) {
                        val inputBuf = codec.getInputBuffer(inputBufIdx) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val presentationUs = extractor.sampleTime
                            codec.queueInputBuffer(inputBufIdx, 0, sampleSize, presentationUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputBufIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outputBufIdx >= 0 -> {
                        if (bufferInfo.size > 0) {
                            val outBuf = codec.getOutputBuffer(outputBufIdx) ?: continue
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.order(ByteOrder.LITTLE_ENDIAN)
                            val shortBuf = outBuf.asShortBuffer()
                            val frameCount = shortBuf.remaining() / channels
                            val timeStartUs = bufferInfo.presentationTimeUs

                            for (f in 0 until frameCount) {
                                var sum = 0
                                for (c in 0 until channels) sum += shortBuf.get(f * channels + c).toInt()
                                val avg = sum.toDouble() / channels / 32768.0  // -1..1 정규화
                                val tUs = timeStartUs + (f.toLong() * 1_000_000L / sampleRate)
                                val bin = (tUs * bins / durationUs).toInt().coerceIn(0, bins - 1)
                                sumSq[bin] += avg * avg
                                cnt[bin]++
                            }
                        }
                        codec.releaseOutputBuffer(outputBufIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
                    }
                    outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                    outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                }
            }

            // 빈 버킷은 이웃 평균으로 채움 (작은 파일에서 발생 가능)
            val rms = FloatArray(bins)
            for (i in 0 until bins) {
                rms[i] = if (cnt[i] > 0) kotlin.math.sqrt(sumSq[i] / cnt[i]).toFloat() else 0f
            }
            // 빈 칸을 양쪽 이웃 평균으로 보간
            for (i in 0 until bins) {
                if (cnt[i] > 0) continue
                val prev = (i - 1 downTo 0).firstOrNull { cnt[it] > 0 }?.let { rms[it] } ?: 0f
                val next = (i + 1 until bins).firstOrNull { cnt[it] > 0 }?.let { rms[it] } ?: prev
                rms[i] = (prev + next) / 2f
            }
            // 0..1 정규화 (최대값 기준 — 작게 녹음된 사운드도 잘 보이게)
            val max = rms.max().coerceAtLeast(0.001f)
            for (i in rms.indices) rms[i] = (rms[i] / max).coerceIn(0f, 1f)

            Result(rms, (durationUs / 1000).toInt())
        } catch (e: Exception) {
            Log.e(TAG, "extract failed", e)
            null
        } finally {
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }
}
