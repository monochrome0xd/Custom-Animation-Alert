package io.github.monochromex.customanimationalert

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 오디오 파일의 평균 음량(dBFS)을 측정.
 * MediaExtractor + MediaCodec으로 디코딩 후 RMS 계산.
 *
 * - dBFS = 20 * log10(rms), rms는 [0,1]로 정규화된 샘플 진폭의 평균 제곱근
 * - 결과 범위: [-60 dB, 0 dB], 실패 시 null
 * - 길이가 매우 긴 파일은 60초까지만 분석 (메모리/시간 절약)
 */
object LoudnessAnalyzer {
    private const val TAG = "LoudnessAnalyzer"
    private const val MAX_ANALYZE_DURATION_US = 60_000_000L
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    suspend fun measureDbfs(context: Context, uri: Uri): Float? = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val tf = extractor.getTrackFormat(i)
                val mime = tf.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = tf
                    break
                }
            }
            if (trackIndex < 0 || format == null) {
                Log.w(TAG, "no audio track: $uri")
                return@withContext null
            }
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var sumSquared = 0.0
            var sampleCount = 0L
            var sawInputEOS = false
            var sawOutputEOS = false
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf == null) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val read = extractor.readSampleData(inBuf, 0)
                            if (read < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                val time = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, read, time, 0)
                                extractor.advance()
                                if (time > MAX_ANALYZE_DURATION_US) {
                                    // 다음 input은 EOS로 처리
                                    sawInputEOS = true
                                }
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.order(ByteOrder.nativeOrder())

                            when (pcmEncoding) {
                                AudioFormat.ENCODING_PCM_16BIT -> {
                                    val shortBuf = outBuf.asShortBuffer()
                                    while (shortBuf.hasRemaining()) {
                                        val s = shortBuf.get().toDouble() / Short.MAX_VALUE
                                        sumSquared += s * s
                                        sampleCount++
                                    }
                                }
                                AudioFormat.ENCODING_PCM_FLOAT -> {
                                    val floatBuf = outBuf.asFloatBuffer()
                                    while (floatBuf.hasRemaining()) {
                                        val s = floatBuf.get().toDouble()
                                        sumSquared += s * s
                                        sampleCount++
                                    }
                                }
                                else -> {
                                    // 미지원 인코딩 → 분석 포기
                                    return@withContext null
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    // INFO_TRY_AGAIN_LATER 등은 그냥 다음 루프
                }
            }

            if (sampleCount == 0L) {
                Log.w(TAG, "no samples decoded: $uri")
                return@withContext null
            }

            val rms = sqrt(sumSquared / sampleCount)
            val dbfs = if (rms <= 0.0) -60f else (20.0 * log10(rms)).toFloat()
            val clamped = dbfs.coerceIn(-60f, 0f)
            Log.d(TAG, "measured $uri: $clamped dBFS (samples=$sampleCount)")
            clamped
        } catch (e: Exception) {
            Log.e(TAG, "analyze failed: $uri", e)
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }
}
