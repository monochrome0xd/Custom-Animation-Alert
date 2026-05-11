package io.github.monochromex.customanimationalert

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object SoundDownloader {
    private const val TAG = "SoundDownloader"
    private const val MAX_SIZE_BYTES = 20L * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private val KNOWN_AUDIO_EXTS = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus")

    data class Result(
        val fileUri: String? = null,
        val displayName: String? = null,
        val error: String? = null
    )

    suspend fun downloadFromUrl(context: Context, urlStr: String): Result = withContext(Dispatchers.IO) {
        val trimmed = urlStr.trim()
        if (trimmed.isBlank()) return@withContext Result(error = "URL이 비어있음")
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
            return@withContext Result(error = "https:// 또는 http://로 시작해야 함")
        }

        val url = try {
            URL(trimmed)
        } catch (e: Exception) {
            return@withContext Result(error = "잘못된 URL 형식")
        }

        var conn: HttpURLConnection? = null
        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CustomAnimationAlert/1.0")
                connect()
            }

            val code = conn.responseCode
            if (code !in 200..299) return@withContext Result(error = "HTTP $code 응답")

            val contentType = conn.contentType?.lowercase().orEmpty()
            val urlPathLower = trimmed.substringBefore('?').lowercase()
            val urlExt = urlPathLower.substringAfterLast('.', "")
            val isAudioByContentType = contentType.startsWith("audio/") || contentType.startsWith("video/")
            val isAudioByExt = urlExt in KNOWN_AUDIO_EXTS
            val isOctetStream = contentType.startsWith("application/octet-stream")
            if (!isAudioByContentType && !isAudioByExt && !isOctetStream) {
                return@withContext Result(error = "오디오 파일이 아닌 듯 (Content-Type=$contentType)")
            }

            val declaredLen = conn.contentLengthLong
            if (declaredLen > MAX_SIZE_BYTES) {
                return@withContext Result(error = "파일이 너무 큼 (${declaredLen / 1024 / 1024}MB > 20MB)")
            }

            val saveExt = if (urlExt in KNOWN_AUDIO_EXTS) urlExt else "mp3"
            val soundsDir = File(context.filesDir, "sounds").apply { mkdirs() }
            val outFile = File(soundsDir, "${UUID.randomUUID()}.$saveExt")

            conn.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    val buf = ByteArray(8 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        total += read
                        if (total > MAX_SIZE_BYTES) {
                            outFile.delete()
                            return@withContext Result(error = "파일이 너무 큼 (>20MB)")
                        }
                        output.write(buf, 0, read)
                    }
                }
            }

            val fallbackName = "downloaded.$saveExt"
            val rawName = trimmed.substringBefore('?').substringAfterLast('/')
            val displayName = rawName.takeIf { it.isNotBlank() && it.contains('.') }
                ?.let { Uri.decode(it) }
                ?: fallbackName

            Result(
                fileUri = Uri.fromFile(outFile).toString(),
                displayName = displayName
            )
        } catch (e: Exception) {
            Log.e(TAG, "download failed: $trimmed", e)
            Result(error = "다운로드 실패: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn?.disconnect()
        }
    }
}
