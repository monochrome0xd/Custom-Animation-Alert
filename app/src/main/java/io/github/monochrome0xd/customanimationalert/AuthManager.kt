package io.github.monochrome0xd.customanimationalert

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Firebase Auth + Credential Manager 기반 Google 로그인 래퍼.
 *
 * - currentUser: Compose state, 로그인 상태 변경 시 자동 recomposition
 * - signInWithGoogle(activity, webClientId): 비동기 로그인 (실패 시 Result.failure)
 * - signOut(): 동기 로그아웃
 *
 * 주의: Credential Manager는 Activity Context 필요 (ApplicationContext 안 됨)
 */
object AuthManager {
    private const val TAG = "AuthManager"
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    var currentUser by mutableStateOf<FirebaseUser?>(null)
        private set

    /** MainActivity.onCreate에서 한 번만 호출. Auth 상태 리스너 등록 + 초기 사용자 설정. */
    fun init() {
        currentUser = auth.currentUser
        auth.addAuthStateListener { fa ->
            currentUser = fa.currentUser
            Log.d(TAG, "auth state changed → user=${fa.currentUser?.uid}")
        }
    }

    /**
     * Google 계정 선택 다이얼로그 → ID 토큰 획득 → Firebase Auth credential로 로그인.
     *
     * webClientId는 google-services.json에 박힌 OAuth 웹 클라이언트 ID.
     * google-services Gradle 플러그인이 자동으로 R.string.default_web_client_id 리소스 생성.
     */
    suspend fun signInWithGoogle(activity: Activity, webClientId: String): Result<FirebaseUser> {
        val credentialManager = CredentialManager.create(activity)

        // 1차 시도: GetGoogleIdOption (바텀시트, 자주 쓰는 계정 우선)
        // 2차 폴백: GetSignInWithGoogleOption (풀스크린 계정 선택 — 명시적 로그인 버튼용)
        val attempts = listOf<GetCredentialRequest>(
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setServerClientId(webClientId)
                        .setFilterByAuthorizedAccounts(false)
                        .setAutoSelectEnabled(false)
                        .build()
                )
                .build(),
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(webClientId).build()
                )
                .build()
        )

        var lastError: Exception? = null
        for ((idx, request) in attempts.withIndex()) {
            try {
                Log.d(TAG, "sign-in attempt ${idx + 1}/${attempts.size}")
                val response = credentialManager.getCredential(activity, request)
                return handleCredential(response.credential)
            } catch (e: GetCredentialCancellationException) {
                Log.d(TAG, "user cancelled sign-in")
                return Result.failure(e)
            } catch (e: NoCredentialException) {
                Log.w(TAG, "attempt ${idx + 1}: NoCredentialException, trying next option", e)
                lastError = e
                // 다음 시도로 진행
            } catch (e: Exception) {
                Log.e(TAG, "attempt ${idx + 1} failed", e)
                lastError = e
                // 다음 시도로 진행 (다른 에러여도 폴백 시도)
            }
        }
        return Result.failure(lastError ?: IllegalStateException("All sign-in attempts failed"))
    }

    private suspend fun handleCredential(credential: androidx.credentials.Credential): Result<FirebaseUser> {
        return try {
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdCredential.idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user
                if (user != null) {
                    Log.d(TAG, "signed in: ${user.displayName} (${user.uid})")
                    Result.success(user)
                } else {
                    Result.failure(IllegalStateException("Firebase user null after sign-in"))
                }
            } else {
                Result.failure(IllegalStateException("Unexpected credential type: ${credential.type}"))
            }
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "failed to parse Google ID token", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase credential exchange failed", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
