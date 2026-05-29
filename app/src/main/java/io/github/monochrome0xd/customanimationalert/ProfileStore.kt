package io.github.monochrome0xd.customanimationalert

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * 사용자 프로필 (닉네임) 관리.
 * Firestore: profiles/{uid} = { nickname, createdAt, updatedAt }
 *
 * - nickname은 마켓플레이스에 업로드 시 creatorName으로 사용됨
 * - 중복 방지: setNickname 시 다른 uid가 같은 닉네임 가졌는지 확인
 */
object ProfileStore {
    private const val TAG = "ProfileStore"
    private const val COLLECTION = "profiles"
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    /** 현재 사용자의 닉네임. 로그인 + load 후 채워짐. 미설정이면 null. */
    var currentNickname by mutableStateOf<String?>(null)
        private set

    /** Firestore에서 내 프로필 불러옴. AuthManager 로그인 후 호출. */
    suspend fun load() {
        val uid = AuthManager.currentUser?.uid
        if (uid == null) {
            currentNickname = null
            return
        }
        try {
            val doc = firestore.collection(COLLECTION).document(uid).get().await()
            currentNickname = doc.getString("nickname")?.takeIf { it.isNotBlank() }
            Log.d(TAG, "loaded nickname=$currentNickname for uid=$uid")
        } catch (e: Exception) {
            Log.e(TAG, "load failed", e)
        }
    }

    /** 닉네임 검증 + 중복 체크 결과 */
    sealed class CheckResult {
        object Available : CheckResult()
        object TooShort : CheckResult()
        object TooLong : CheckResult()
        object InvalidChars : CheckResult()
        object Taken : CheckResult()  // 다른 uid가 사용 중
        object Same : CheckResult()   // 내 현재 닉네임과 동일
        data class Error(val message: String) : CheckResult()
    }

    private const val MIN_LEN = 2
    private const val MAX_LEN = 16
    private val ALLOWED_REGEX = Regex("^[a-zA-Z0-9가-힣_-]+$")

    suspend fun checkAvailability(nickname: String): CheckResult {
        val trimmed = nickname.trim()
        if (trimmed.length < MIN_LEN) return CheckResult.TooShort
        if (trimmed.length > MAX_LEN) return CheckResult.TooLong
        if (!ALLOWED_REGEX.matches(trimmed)) return CheckResult.InvalidChars
        if (trimmed == currentNickname) return CheckResult.Same

        val uid = AuthManager.currentUser?.uid
            ?: return CheckResult.Error("로그인 필요")
        return try {
            val snap = firestore.collection(COLLECTION)
                .whereEqualTo("nickname", trimmed)
                .limit(2)  // 내 거 + 1개 더 있는지만 체크하면 충분
                .get()
                .await()
            // 다른 uid의 문서가 있으면 taken
            val takenByOther = snap.documents.any { it.id != uid }
            if (takenByOther) CheckResult.Taken else CheckResult.Available
        } catch (e: Exception) {
            Log.e(TAG, "checkAvailability failed", e)
            CheckResult.Error(e.message ?: "확인 실패")
        }
    }

    /** 닉네임 저장. 호출 전 checkAvailability로 Available인지 확인 권장. */
    suspend fun setNickname(nickname: String): Result<Unit> {
        val uid = AuthManager.currentUser?.uid
            ?: return Result.failure(IllegalStateException("로그인 필요"))
        val trimmed = nickname.trim()
        return try {
            // 한 번 더 중복 체크 (race condition 최소화)
            val check = checkAvailability(trimmed)
            if (check is CheckResult.Taken) return Result.failure(IllegalStateException("이미 사용 중인 닉네임"))
            if (check is CheckResult.TooShort) return Result.failure(IllegalStateException("닉네임이 너무 짧음 (최소 $MIN_LEN 자)"))
            if (check is CheckResult.TooLong) return Result.failure(IllegalStateException("닉네임이 너무 김 (최대 $MAX_LEN 자)"))
            if (check is CheckResult.InvalidChars) return Result.failure(IllegalStateException("한/영/숫자/_- 만 허용"))

            val data = mapOf(
                "nickname" to trimmed,
                "updatedAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp()  // 첫 저장 시에만 실제로 기록됨 (merge)
            )
            firestore.collection(COLLECTION).document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.mergeFields(listOf("nickname", "updatedAt")))
                .await()
            // createdAt은 별도로 처음에만
            firestore.collection(COLLECTION).document(uid)
                .set(mapOf("createdAt" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
                .await()

            currentNickname = trimmed
            Log.d(TAG, "setNickname: $trimmed for $uid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "setNickname failed", e)
            Result.failure(e)
        }
    }
}
