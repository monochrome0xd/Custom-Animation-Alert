package io.github.monochrome0xd.customanimationalert

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 사용자 로컬 규칙을 클라우드로 백업/복원.
 *
 * 데이터 구조:
 * - Firestore: users/{uid}/rules/{ruleId} = { ruleJson, mediaCloudUrl, soundCloudUrl, updatedAt }
 * - Storage: users/{uid}/rules/{ruleId}/media.{ext}, sound.{ext}
 * - 백업 시각: users/{uid}.lastBackupAt
 *
 * 다른 기기에서 같은 Google 계정 로그인 → 복원 시 모든 규칙 + 미디어/사운드 파일이 함께 복구됨.
 */
object RuleSync {
    private const val TAG = "RuleSync"
    private val storage by lazy { FirebaseStorage.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private fun userDoc(uid: String) = firestore.collection("users").document(uid)
    private fun rulesCol(uid: String) = userDoc(uid).collection("rules")

    /**
     * 로컬 모든 규칙을 클라우드에 업로드 (같은 ID 있으면 덮어쓰기).
     * onProgress(done, total) — 진행률 콜백.
     */
    suspend fun backupAll(
        context: Context,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Int> {
        val uid = AuthManager.currentUser?.uid
            ?: return Result.failure(IllegalStateException("로그인이 필요합니다"))
        val rules = RuleStore.loadAll(context)
        onProgress(0, rules.size)
        return try {
            for ((i, rule) in rules.withIndex()) {
                backupSingle(context, uid, rule).getOrThrow()
                onProgress(i + 1, rules.size)
            }
            // 백업 완료 시각 기록
            try {
                userDoc(uid).set(
                    mapOf("lastBackupAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge()
                ).await()
            } catch (_: Exception) {}
            Result.success(rules.size)
        } catch (e: Exception) {
            Log.e(TAG, "backupAll failed", e)
            Result.failure(e)
        }
    }

    /**
     * 클라우드에서 모든 규칙을 가져와서 로컬 RuleStore를 덮어씀.
     * onProgress(done, total).
     */
    suspend fun restoreAll(
        context: Context,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Int> {
        val uid = AuthManager.currentUser?.uid
            ?: return Result.failure(IllegalStateException("로그인이 필요합니다"))
        return try {
            val snap = rulesCol(uid).get().await()
            val docs = snap.documents
            onProgress(0, docs.size)
            val newRules = mutableListOf<Rule>()
            for ((i, doc) in docs.withIndex()) {
                try {
                    val ruleJsonStr = doc.getString("ruleJson") ?: continue
                    val mediaCloudUrl = doc.getString("mediaCloudUrl")
                    val soundCloudUrl = doc.getString("soundCloudUrl")
                    val baseRule = Rule.fromJson(org.json.JSONObject(ruleJsonStr))

                    val dir = File(context.filesDir, "synced/${baseRule.id}").apply { mkdirs() }
                    val mediaLocal = mediaCloudUrl?.let { url ->
                        val ext = url.substringBefore('?').substringAfterLast('.', "png")
                        val out = File(dir, "media.$ext")
                        downloadUrlToFile(url, out)
                        Uri.fromFile(out).toString()
                    }
                    val soundLocal = soundCloudUrl?.let { url ->
                        val ext = url.substringBefore('?').substringAfterLast('.', "mp3")
                        val out = File(dir, "sound.$ext")
                        downloadUrlToFile(url, out)
                        Uri.fromFile(out).toString()
                    }
                    newRules.add(
                        baseRule.copy(
                            mediaUri = mediaLocal ?: baseRule.mediaUri,
                            soundUri = soundLocal ?: baseRule.soundUri
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "restore single failed (skipping): ${doc.id}", e)
                }
                onProgress(i + 1, docs.size)
            }
            // 로컬 덮어쓰기
            RuleStore.saveAll(context, newRules)
            Result.success(newRules.size)
        } catch (e: Exception) {
            Log.e(TAG, "restoreAll failed", e)
            Result.failure(e)
        }
    }

    /** 마지막 백업 시각 (epoch ms) — 백업한 적 없으면 null. */
    suspend fun lastBackupAtMs(): Long? {
        val uid = AuthManager.currentUser?.uid ?: return null
        return try {
            val ts = userDoc(uid).get().await().getTimestamp("lastBackupAt")
            ts?.toDate()?.time
        } catch (e: Exception) {
            Log.w(TAG, "lastBackupAtMs failed", e)
            null
        }
    }

    // ====== 내부 ======

    private suspend fun backupSingle(context: Context, uid: String, rule: Rule): Result<Unit> {
        return try {
            // 미디어 업로드 (있을 때만)
            val mediaCloudUrl: String? = rule.mediaUri?.let { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    val ext = guessExtension(context, uri, if (rule.mediaType == "video") "mp4" else "png")
                    val ref = storage.reference.child("users/$uid/rules/${rule.id}/media.$ext")
                    uploadUriToStorage(context, uri, ref)
                    ref.downloadUrl.await().toString()
                } catch (e: Exception) {
                    Log.w(TAG, "media upload failed for ${rule.id}", e)
                    null
                }
            }
            // 사운드 업로드
            val soundCloudUrl: String? = rule.soundUri?.let { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    val ext = guessExtension(context, uri, "mp3")
                    val ref = storage.reference.child("users/$uid/rules/${rule.id}/sound.$ext")
                    uploadUriToStorage(context, uri, ref)
                    ref.downloadUrl.await().toString()
                } catch (e: Exception) {
                    Log.w(TAG, "sound upload failed for ${rule.id}", e)
                    null
                }
            }
            // Firestore 메타데이터
            val data = mapOf(
                "ruleJson" to rule.toJson().toString(),
                "mediaCloudUrl" to mediaCloudUrl,
                "soundCloudUrl" to soundCloudUrl,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            rulesCol(uid).document(rule.id).set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "backupSingle failed for ${rule.id}", e)
            Result.failure(e)
        }
    }

    private suspend fun uploadUriToStorage(context: Context, uri: Uri, ref: StorageReference): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("열 수 없는 URI: $uri")
        input.use { ref.putStream(it).await() }
        return ref.downloadUrl.await().toString()
    }

    private suspend fun downloadUrlToFile(urlStr: String, outFile: File) {
        withContext(Dispatchers.IO) {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                conn.inputStream.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun guessExtension(context: Context, uri: Uri, defaultExt: String): String {
        val fromPath = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        if (!fromPath.isNullOrBlank() && fromPath.length <= 5) return fromPath
        val mime = context.contentResolver.getType(uri)
        return when {
            mime == null -> defaultExt
            mime.startsWith("image/png") -> "png"
            mime.startsWith("image/jpeg") -> "jpg"
            mime.startsWith("image/gif") -> "gif"
            mime.startsWith("image/webp") -> "webp"
            mime.startsWith("video/mp4") -> "mp4"
            mime.startsWith("video/quicktime") -> "mov"
            mime.startsWith("video/webm") -> "webm"
            mime.startsWith("audio/mpeg") -> "mp3"
            mime.startsWith("audio/wav") -> "wav"
            mime.startsWith("audio/ogg") -> "ogg"
            mime.startsWith("audio/mp4") -> "m4a"
            else -> defaultExt
        }
    }
}
