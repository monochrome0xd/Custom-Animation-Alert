package io.github.monochrome0xd.customanimationalert

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.UUID

/**
 * 마켓플레이스 데이터 레이어:
 * - 업로드: 미디어/사운드 파일을 Firebase Storage에, 메타데이터를 Firestore에 저장
 * - 조회: Firestore에서 공개 애니메이션 목록 조회
 * - 다운로드: Storage에서 파일 받고 local filesDir에 캐시, Rule로 임포트
 *
 * Firestore 컬렉션 구조: animations/{id}
 * Storage 경로: animations/{id}/media.{ext}, animations/{id}/sound.{ext}
 */
object AnimationStore {
    private const val TAG = "AnimationStore"
    private const val COLLECTION = "animations"
    private val storage by lazy { FirebaseStorage.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    /** Firestore 문서 ↔ Kotlin 데이터 클래스 변환용. id는 문서 ID에서 따로 채움. */
    data class RemoteAnimation(
        val id: String = "",
        val creatorUid: String = "",
        val creatorName: String = "",
        val title: String = "",
        val categoryName: String = "기본",
        val mediaUrl: String = "",
        val mediaType: String = "image",
        val mediaName: String? = null,
        val soundUrl: String? = null,
        val soundName: String? = null,
        val measuredLoudnessDb: Double? = null,
        val ruleJson: String = "",
        val downloadCount: Long = 0,
        val likes: Long = 0,
        val isPrivate: Boolean = false,  // #39 — 비공개 공유: 일반 목록에 안 뜨고 제목 정확 검색으로만 노출
        val passwordHash: String = ""    // #39b — 비공개 + 비밀번호: SHA-256 해시. 빈 문자열이면 비번 없음
    )

    /** 비밀번호 → SHA-256 hex. 평문은 저장하지 않고 해시만 Firestore에 둠. */
    fun hashPassword(pw: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(pw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 입력 비밀번호가 해당 콘텐츠 비번과 일치하는지. 비번 없는 콘텐츠는 항상 true. */
    fun verifyPassword(animation: RemoteAnimation, input: String): Boolean =
        animation.passwordHash.isEmpty() || hashPassword(input) == animation.passwordHash

    /**
     * 규칙을 클라우드에 업로드.
     * @param customTitle 사용 시 마켓 표시 제목 덮어쓰기 (null/빈문자열 → rule.name/appLabel)
     * @param includeApp false면 packageName/appLabel을 빈 값으로 업로드 (다른 사용자가 받았을 때 앱 선택 없는 상태)
     * onProgress: 0.0 ~ 1.0 (전체 진행률). 미디어 0~0.5, 사운드 0.5~1.0.
     * 반환: animationId (Firestore 문서 ID) 또는 실패
     */
    suspend fun upload(
        context: Context,
        rule: Rule,
        customTitle: String? = null,
        includeApp: Boolean = false,
        isPrivate: Boolean = false,
        password: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        val user = AuthManager.currentUser
            ?: return Result.failure(IllegalStateException("로그인이 필요합니다"))
        val mediaUriStr = rule.mediaUri
            ?: return Result.failure(IllegalStateException("미디어 없이는 공유할 수 없습니다"))

        // 옵션 적용: 앱 정보 제외 시 packageName/appLabel 제거된 rule로 업로드
        val effectiveRule = if (includeApp) rule else rule.copy(packageName = null, appLabel = null)
        val effectiveTitle = customTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: rule.name.ifBlank { rule.appLabel ?: "이름 없음" }

        val animationId = UUID.randomUUID().toString()
        return try {
            // 1단계: 미디어 업로드
            val mediaUri = Uri.parse(mediaUriStr)
            val mediaExt = guessExtension(context, mediaUri, defaultExt = when (rule.mediaType) {
                "video" -> "mp4"; "lottie" -> "json"; else -> "png"
            })
            val mediaRef = storage.reference.child("animations/$animationId/media.$mediaExt")
            val mediaUrl = uploadUri(context, mediaUri, mediaRef) { onProgress(it * 0.5f) }

            // 2단계: 사운드 업로드 (있을 때만)
            var soundUrl: String? = null
            val soundUriStr = rule.soundUri
            if (soundUriStr != null) {
                val soundUri = Uri.parse(soundUriStr)
                val soundExt = guessExtension(context, soundUri, defaultExt = "mp3")
                val soundRef = storage.reference.child("animations/$animationId/sound.$soundExt")
                soundUrl = uploadUri(context, soundUri, soundRef) { onProgress(0.5f + it * 0.5f) }
            } else {
                onProgress(1f)
            }

            // 3단계: Firestore 메타데이터 저장
            val data = mapOf(
                "id" to animationId,
                "creatorUid" to user.uid,
                "creatorName" to (ProfileStore.currentNickname ?: user.displayName ?: "익명"),
                "title" to effectiveTitle,
                "categoryName" to effectiveRule.groupName,
                "mediaUrl" to mediaUrl,
                "mediaType" to effectiveRule.mediaType,
                "mediaName" to effectiveRule.mediaName,
                "soundUrl" to soundUrl,
                "soundName" to effectiveRule.soundName,
                "measuredLoudnessDb" to effectiveRule.measuredLoudnessDb?.toDouble(),
                "ruleJson" to effectiveRule.toJson().toString(),
                "createdAt" to FieldValue.serverTimestamp(),
                "downloadCount" to 0L,
                "likes" to 0L,
                "isPrivate" to isPrivate,
                // 비번은 비공개일 때만, 입력이 있을 때만 해시로 저장
                "passwordHash" to (if (isPrivate && !password.isNullOrBlank()) hashPassword(password) else "")
            )
            firestore.collection(COLLECTION).document(animationId).set(data).await()

            Log.d(TAG, "uploaded: $animationId (media=$mediaUrl, sound=$soundUrl)")
            Result.success(animationId)
        } catch (e: Exception) {
            Log.e(TAG, "upload failed", e)
            Result.failure(e)
        }
    }

    /**
     * Firestore에서 최신 애니메이션 목록 조회 (createdAt 내림차순).
     * #39 — 비공개(isPrivate) 항목은 제외. private이 끼어 있을 수 있으니 넉넉히 받고 클라이언트에서 필터.
     */
    suspend fun listRecent(limit: Long = 30): Result<List<RemoteAnimation>> =
        runQuery(
            firestore.collection(COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit * 2)
        ).map { list -> list.filter { !it.isPrivate }.take(limit.toInt()) }

    /** 다운로드 수 많은 순 (전체 인기). 비공개 제외. */
    suspend fun listPopular(limit: Long = 30): Result<List<RemoteAnimation>> =
        runQuery(
            firestore.collection(COLLECTION)
                .orderBy("downloadCount", Query.Direction.DESCENDING)
                .limit(limit * 2)
        ).map { list -> list.filter { !it.isPrivate }.take(limit.toInt()) }

    /** 특정 카테고리 (groupName) 내 최신순. 비공개 제외. */
    suspend fun listByCategory(category: String, limit: Long = 30): Result<List<RemoteAnimation>> =
        runQuery(
            firestore.collection(COLLECTION)
                .whereEqualTo("categoryName", category)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit * 2)
        ).map { list -> list.filter { !it.isPrivate }.take(limit.toInt()) }

    /**
     * #38/#39 — 마켓 검색.
     * - 공개 항목: 제목에 query가 포함되면 노출 (대소문자 무시, 최근 200개 범위 내).
     * - 비공개 항목: 제목이 query와 정확히 일치할 때만 노출 (#39).
     * 복합 인덱스 불필요 (orderBy createdAt 단일 + whereEqualTo title 단일).
     */
    suspend fun search(query: String): Result<List<RemoteAnimation>> {
        val q = query.trim()
        if (q.isBlank()) return Result.success(emptyList())
        return try {
            // 공개 항목 부분 일치 (최근 200개 내)
            val recentSnap = firestore.collection(COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(200).get().await()
            val recent = recentSnap.documents.mapNotNull { parseDoc(it) }
            val publicMatches = recent.filter { !it.isPrivate && it.title.contains(q, ignoreCase = true) }
            // 제목 정확 일치 (비공개 포함)
            val exactSnap = firestore.collection(COLLECTION)
                .whereEqualTo("title", q).limit(20).get().await()
            val exactMatches = exactSnap.documents.mapNotNull { parseDoc(it) }
            Result.success((publicMatches + exactMatches).distinctBy { it.id })
        } catch (e: Exception) {
            Log.e(TAG, "search failed", e)
            Result.failure(e)
        }
    }

    /**
     * 내가 업로드한 애니메이션 (최신순).
     * where + orderBy 조합은 Firestore 복합 인덱스 필요 → 그냥 where만 하고 클라이언트에서 정렬.
     */
    suspend fun listMine(limit: Long = 100): Result<List<RemoteAnimation>> {
        val uid = AuthManager.currentUser?.uid ?: return Result.success(emptyList())
        return try {
            val snap = firestore.collection(COLLECTION)
                .whereEqualTo("creatorUid", uid)
                .limit(limit)
                .get()
                .await()
            val items = snap.documents.mapNotNull { doc ->
                try { doc.toObject(RemoteAnimation::class.java)?.copy(id = doc.id) }
                catch (e: Exception) { Log.w(TAG, "parse failed ${doc.id}", e); null }
            }
            // 클라이언트 측에서 createdAt 역순 정렬은 어렵고 (서버 timestamp), id 역순으로 충분
            Result.success(items.sortedByDescending { it.id })
        } catch (e: Exception) {
            Log.e(TAG, "listMine failed", e)
            Result.failure(e)
        }
    }

    /**
     * 현재 사용자가 좋아요 누른 애니메이션 ID 집합.
     * 미로그인 또는 실패 시 빈 셋. users/{uid}/likes/{animationId} 하위 컬렉션 사용.
     */
    suspend fun fetchMyLikes(): Set<String> {
        val uid = AuthManager.currentUser?.uid ?: return emptySet()
        return try {
            val snap = firestore.collection("users").document(uid)
                .collection("likes").get().await()
            snap.documents.map { it.id }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "fetchMyLikes failed", e); emptySet()
        }
    }

    /**
     * 좋아요 토글 — 현재 상태가 liked면 unlike, 아니면 like.
     * users/{uid}/likes/{id} 문서 추가/삭제 + animations/{id}.likes count ±1.
     * 반환: 토글 후의 새 상태 (true=liked).
     */
    suspend fun toggleLike(animationId: String): Result<Boolean> {
        val uid = AuthManager.currentUser?.uid
            ?: return Result.failure(IllegalStateException("로그인이 필요합니다"))
        val likeDoc = firestore.collection("users").document(uid)
            .collection("likes").document(animationId)
        val animDoc = firestore.collection(COLLECTION).document(animationId)
        return try {
            val exists = likeDoc.get().await().exists()
            if (exists) {
                likeDoc.delete().await()
                try { animDoc.update("likes", FieldValue.increment(-1)).await() } catch (_: Exception) {}
                Result.success(false)
            } else {
                likeDoc.set(mapOf("likedAt" to FieldValue.serverTimestamp())).await()
                try { animDoc.update("likes", FieldValue.increment(1)).await() } catch (_: Exception) {}
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "toggleLike failed", e); Result.failure(e)
        }
    }

    /**
     * 내가 올린 애니메이션 삭제 — Storage 파일 + Firestore 문서 모두 제거.
     * 소유권 확인: creatorUid == 현재 사용자 uid 일치해야 함.
     */
    suspend fun delete(animation: RemoteAnimation): Result<Unit> {
        val user = AuthManager.currentUser
            ?: return Result.failure(IllegalStateException("로그인 필요"))
        if (animation.creatorUid != user.uid) {
            return Result.failure(IllegalStateException("내가 올린 애니메이션이 아닙니다"))
        }
        return try {
            // Storage 파일 삭제 (실패해도 Firestore 문서는 계속 지움)
            try { storage.getReferenceFromUrl(animation.mediaUrl).delete().await() }
            catch (e: Exception) { Log.w(TAG, "media file delete failed (이미 없거나 권한)", e) }
            animation.soundUrl?.let { url ->
                try { storage.getReferenceFromUrl(url).delete().await() }
                catch (e: Exception) { Log.w(TAG, "sound file delete failed", e) }
            }
            // Firestore 문서 삭제
            firestore.collection(COLLECTION).document(animation.id).delete().await()
            Log.d(TAG, "deleted animation ${animation.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "delete failed", e)
            Result.failure(e)
        }
    }

    /**
     * #40 — 관리자(부적절 콘텐츠) 삭제. 소유권 검사 없이 Admin 권한으로 삭제.
     * 실제 Firestore 차단은 보안 규칙에서 admin만 타인 문서 삭제 가능하도록 설정돼 있어야 함.
     */
    suspend fun adminDelete(animation: RemoteAnimation): Result<Unit> {
        if (!Admin.isAdmin) return Result.failure(IllegalStateException("관리자만 가능"))
        return try {
            try { storage.getReferenceFromUrl(animation.mediaUrl).delete().await() }
            catch (e: Exception) { Log.w(TAG, "admin media delete failed", e) }
            animation.soundUrl?.let { url ->
                try { storage.getReferenceFromUrl(url).delete().await() }
                catch (e: Exception) { Log.w(TAG, "admin sound delete failed", e) }
            }
            firestore.collection(COLLECTION).document(animation.id).delete().await()
            Log.d(TAG, "admin deleted animation ${animation.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "admin delete failed", e)
            Result.failure(e)
        }
    }

    /**
     * #40 — 신고 검토 화면에서 animationId만으로 관리자 삭제.
     * 문서를 먼저 읽어 Storage 경로(mediaUrl/soundUrl)를 확보한 뒤 adminDelete로 위임.
     * 이미 삭제됐으면 성공으로 간주.
     */
    suspend fun adminDeleteById(animationId: String): Result<Unit> {
        if (!Admin.isAdmin) return Result.failure(IllegalStateException("관리자만 가능"))
        return try {
            val doc = firestore.collection(COLLECTION).document(animationId).get().await()
            val animation = parseDoc(doc)
                ?: return Result.success(Unit)  // 이미 없음
            adminDelete(animation)
        } catch (e: Exception) {
            Log.e(TAG, "adminDeleteById failed", e)
            Result.failure(e)
        }
    }

    private fun parseDoc(doc: com.google.firebase.firestore.DocumentSnapshot): RemoteAnimation? = try {
        doc.toObject(RemoteAnimation::class.java)?.copy(id = doc.id)
    } catch (e: Exception) {
        Log.w(TAG, "failed to parse doc ${doc.id}", e)
        null
    }

    private suspend fun runQuery(query: Query): Result<List<RemoteAnimation>> = try {
        val snapshot = query.get().await()
        Result.success(snapshot.documents.mapNotNull { parseDoc(it) })
    } catch (e: Exception) {
        Log.e(TAG, "query failed", e)
        Result.failure(e)
    }

    /**
     * 원격 애니메이션을 다운로드해서 로컬 Rule로 변환.
     * 미디어/사운드 파일을 filesDir/imported/{newRuleId}/에 저장 후 file:// URI로 Rule 채움.
     * @param countAsDownload true면 Firestore downloadCount +1 (사용자가 "내 규칙으로 추가" 누른 경우).
     *                        false면 카운트 증가 안 함 (마켓 카드의 일회성 재생).
     */
    suspend fun download(
        context: Context,
        animation: RemoteAnimation,
        countAsDownload: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): Result<Rule> = try {
        val ruleJsonObj = org.json.JSONObject(animation.ruleJson)
        val sourceRule = Rule.fromJson(ruleJsonObj)

        // 새 ID 부여 (원본과 충돌 방지) + 로컬 디렉토리
        val newRuleId = UUID.randomUUID().toString()
        val dir = java.io.File(context.filesDir, "imported/$newRuleId").apply { mkdirs() }

        // 미디어 다운로드
        val mediaLocalUri: String = run {
            val ext = animation.mediaUrl.substringBefore('?').substringAfterLast('.', "png")
            val outFile = java.io.File(dir, "media.$ext")
            downloadUrlToFile(animation.mediaUrl, outFile) { onProgress(it * 0.5f) }
            Uri.fromFile(outFile).toString()
        }

        // 사운드 다운로드 (있을 때만)
        val soundLocalUri: String? = animation.soundUrl?.let { url ->
            val ext = url.substringBefore('?').substringAfterLast('.', "mp3")
            val outFile = java.io.File(dir, "sound.$ext")
            downloadUrlToFile(url, outFile) { onProgress(0.5f + it * 0.5f) }
            Uri.fromFile(outFile).toString()
        } ?: run {
            onProgress(1f)
            null
        }

        val importedRule = sourceRule.copy(
            id = newRuleId,
            mediaUri = mediaLocalUri,
            soundUri = soundLocalUri,
            measuredLoudnessDb = animation.measuredLoudnessDb?.toFloat()
        )

        // 다운로드 카운트 +1 — countAsDownload=false면 스킵 (마켓 카드 일회성 재생 등)
        if (countAsDownload) {
            try {
                firestore.collection(COLLECTION).document(animation.id)
                    .update("downloadCount", FieldValue.increment(1)).await()
            } catch (_: Exception) {}
        }

        Log.d(TAG, "downloaded animation ${animation.id} → rule $newRuleId")
        Result.success(importedRule)
    } catch (e: Exception) {
        Log.e(TAG, "download failed", e)
        Result.failure(e)
    }

    // ====== 내부 헬퍼 ======

    private suspend fun uploadUri(
        context: Context,
        uri: Uri,
        ref: StorageReference,
        onProgress: (Float) -> Unit
    ): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("열 수 없는 URI: $uri")
        input.use { stream ->
            val task = ref.putStream(stream)
            task.addOnProgressListener { snap ->
                val total = snap.totalByteCount.coerceAtLeast(1L)
                onProgress(snap.bytesTransferred.toFloat() / total)
            }
            task.await()
        }
        return ref.downloadUrl.await().toString()
    }

    private suspend fun downloadUrlToFile(
        urlStr: String,
        outFile: java.io.File,
        onProgress: (Float) -> Unit
    ) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val conn = (java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                val total = conn.contentLengthLong.coerceAtLeast(1L)
                conn.inputStream.use { input ->
                    outFile.outputStream().use { output ->
                        val buf = ByteArray(8 * 1024)
                        var transferred = 0L
                        while (true) {
                            val read = input.read(buf)
                            if (read == -1) break
                            output.write(buf, 0, read)
                            transferred += read
                            onProgress(transferred.toFloat() / total)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    /** content:// 또는 file:// URI에서 확장자 추정. 실패 시 defaultExt. */
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
