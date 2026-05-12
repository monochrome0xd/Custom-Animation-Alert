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
    private const val MAX_HTML_BYTES = 2L * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private val KNOWN_AUDIO_EXTS = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus")
    private val AUDIO_URL_REGEX = Regex(
        """https?://[^"\s'<>\\]+?\.(mp3|wav|ogg|m4a|aac|flac|opus)(?:\?[^"\s'<>\\]*)?""",
        RegexOption.IGNORE_CASE
    )

    data class Result(
        val fileUri: String? = null,
        val displayName: String? = null,
        val error: String? = null
    )

    suspend fun downloadFromUrl(
        context: Context,
        urlStr: String,
        allowHtmlParse: Boolean = true
    ): Result = withContext(Dispatchers.IO) {
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
            val isHtml = contentType.startsWith("text/html") || contentType.startsWith("application/xhtml")

            // HTML 페이지면 본문에서 mp3 URL 추출 후 재귀 다운로드
            if (isHtml && allowHtmlParse) {
                val html = readBodyAsString(conn, MAX_HTML_BYTES)
                conn.disconnect()
                conn = null
                val match = AUDIO_URL_REGEX.find(html)
                    ?: return@withContext Result(error = "페이지에서 오디오 URL을 찾지 못함")
                Log.d(TAG, "HTML page → extracted: ${match.value}")
                return@withContext downloadFromUrl(context, match.value, allowHtmlParse = false)
            }

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

    private fun readBodyAsString(conn: HttpURLConnection, maxBytes: Long): String {
        val charset = conn.contentEncoding?.takeIf { it.isNotBlank() } ?: "UTF-8"
        return conn.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buf)
                if (read == -1) break
                total += read
                if (total > maxBytes) break
                out.write(buf, 0, read)
            }
            try { String(out.toByteArray(), charset(charset)) }
            catch (_: Exception) { String(out.toByteArray(), Charsets.UTF_8) }
        }
    }
}
