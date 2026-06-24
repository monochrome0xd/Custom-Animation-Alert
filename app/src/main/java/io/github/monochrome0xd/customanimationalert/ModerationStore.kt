package io.github.monochrome0xd.customanimationalert

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * 관리자 식별. (#40/#45/#46)
 *
 * 현재는 이메일 기반으로 판별한다. 본인 Google 계정 이메일이 ADMIN_EMAILS에 있으면 관리자.
 * UID 기반으로 바꾸고 싶으면 ADMIN_UIDS에 본인 Firebase UID를 추가하면 됨
 * (계정 화면의 "내 UID" 항목에서 확인 가능). UID가 들어 있으면 UID도 함께 검사한다.
 *
 * 주의: 클라이언트의 관리자 판별은 UI 노출용일 뿐이고, 실제 타인 콘텐츠 삭제/신고 열람 권한은
 * Firestore 보안 규칙에서 동일한 조건(이메일/UID)으로 막아야 진짜로 보호된다.
 */
object Admin {
    private val ADMIN_EMAILS = setOf("monochrome0xd@gmail.com")
    private val ADMIN_UIDS = emptySet<String>()  // 본인 UID 확인되면 여기에 추가

    val isAdmin: Boolean
        get() {
            val u = AuthManager.currentUser ?: return false
            if (u.uid in ADMIN_UIDS) return true
            val email = u.email?.lowercase() ?: return false
            return email in ADMIN_EMAILS
        }
}

/**
 * 신고(#44) / 사용자 차단(#46) 데이터 레이어.
 *
 * Firestore 구조:
 * - reports/{animationId}_{reporterUid}  : 한 사용자가 한 콘텐츠에 1건 (중복 신고 방지)
 * - users/{uid}/blocked/{blockedUid}      : 내가 차단한 사용자
 */
object ModerationStore {
    private const val TAG = "ModerationStore"
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    /** 신고 카테고리 (#44). */
    val REPORT_CATEGORIES = listOf(
        "선정적/음란물",
        "폭력/고어",
        "혐오/차별 발언",
        "스팸/광고",
        "저작권 침해",
        "개인정보 노출",
        "기타"
    )

    data class Report(
        val id: String = "",
        val animationId: String = "",
        val animationTitle: String = "",
        val creatorUid: String = "",
        val creatorName: String = "",
        val reporterUid: String = "",
        val category: String = "",
        val note: String = ""
    )

    data class BlockedUser(
        val uid: String = "",
        val name: String = ""
    )

    // ====== 신고 ======

    /** 콘텐츠 신고. 같은 사용자가 같은 콘텐츠를 다시 신고하면 덮어씀(1건 유지). */
    suspend fun report(
        animation: AnimationStore.RemoteAnimation,
        category: String,
        note: String = ""
    ): Result<Unit> {
        val uid = AuthManager.currentUser?.uid
            ?: return Result.failure(IllegalStateException("로그인이 필요합니다"))
        return try {
            val docId = "${animation.id}_$uid"
            val data = mapOf(
                "animationId" to animation.id,
                "animationTitle" to animation.title,
                "creatorUid" to animation.creatorUid,
                "creatorName" to animation.creatorName,
                "reporterUid" to uid,
                "category" to category,
                "note" to note,
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("reports").document(docId).set(data).await()
            Log.d(TAG, "reported ${animation.id} as $category")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "report failed", e)
            Result.failure(e)
        }
    }

    /** 관리자 — 모든 신고 목록 (최신순). */
    suspend fun listReports(limit: Long = 100): Result<List<Report>> {
        if (!Admin.isAdmin) return Result.failure(IllegalStateException("관리자만 가능"))
        return try {
            val snap = firestore.collection("reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit).get().await()
            val items = snap.documents.mapNotNull { doc ->
                try { doc.toObject(Report::class.java)?.copy(id = doc.id) }
                catch (e: Exception) { Log.w(TAG, "parse report failed ${doc.id}", e); null }
            }
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "listReports failed", e)
            Result.failure(e)
        }
    }

    /** 관리자 — 신고 처리 완료(문서 삭제). */
    suspend fun dismissReport(reportId: String): Result<Unit> {
        if (!Admin.isAdmin) return Result.failure(IllegalStateException("관리자만 가능"))
        return try {
            firestore.collection("reports").document(reportId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "dismissReport failed", e)
            Result.failure(e)
        }
    }

    /** 관리자 — 특정 애니메이션에 대한 신고 문서들 일괄 삭제 (콘텐츠 삭제 시 같이 정리). */
    suspend fun dismissReportsForAnimation(animationId: String) {
        if (!Admin.isAdmin) return
        try {
            val snap = firestore.collection("reports")
                .whereEqualTo("animationId", animationId).get().await()
            for (doc in snap.documents) {
                try { doc.reference.delete().await() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "dismissReportsForAnimation failed", e)
        }
    }

    // ====== 사용자 차단 ======

    /** 내가 차단한 사용자 UID 집합. 미로그인/실패 시 빈 셋. */
    suspend fun fetchBlockedUids(): Set<String> {
        val uid = AuthManager.currentUser?.uid ?: return emptySet()
        return try {
            val snap = firestore.collection("users").document(uid)
                .collection("blocked").get().await()
            snap.documents.map { it.id }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "fetchBlockedUids failed", e); emptySet()
        }
    }

    /** 차단한 사용자 목록 (관리 화면용). */
    suspend fun listBlockedUsers(): Result<List<BlockedUser>> {
        val uid = AuthManager.currentUser?.uid
            ?: return Result.success(emptyList())
        return try {
            val snap = firestore.collection("users").document(uid)
                .collection("blocked").get().await()
            val items = snap.documents.map { doc ->
                BlockedUser(uid = doc.id, name = doc.getString("name") ?: "(이름 없음)")
            }
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "listBlockedUsers failed", e)
            Result.failure(e)
        }
    }

    /** 사용자 차단. 자기 자신은 차단 불가. */
    suspend fun blockUser(targetUid: String, targetName: String): Result<Unit> {
        val uid = AuthManager.currentUser?.uid
            ?: return Result.failure(IllegalStateException("로그인이 필요합니다"))
        if (targetUid.isBlank() || targetUid == uid) {
            return Result.failure(IllegalStateException("자기 자신은 차단할 수 없습니다"))
        }
        return try {
            firestore.collection("users").document(uid)
                .collection("blocked").document(targetUid)
                .set(mapOf("name" to targetName, "blockedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "blockUser failed", e)
            Result.failure(e)
        }
    }

    /** 차단 해제. */
    suspend fun unblockUser(targetUid: String): Result<Unit> {
        val uid = AuthManager.currentUser?.uid
            ?: return Result.failure(IllegalStateException("로그인이 필요합니다"))
        return try {
            firestore.collection("users").document(uid)
                .collection("blocked").document(targetUid).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "unblockUser failed", e)
            Result.failure(e)
        }
    }
}
