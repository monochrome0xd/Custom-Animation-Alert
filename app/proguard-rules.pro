# ============================================================
# Custom Animation Alert — ProGuard / R8 rules
# ============================================================
# 일반 원칙:
#  - Firebase, Coil, Lottie, Compose는 라이브러리 측에서 자체 consumer-rules 제공 → 대부분 자동 처리
#  - 우리 코드는 JSON 직렬화 데이터(Rule), Firestore POJO(RemoteAnimation) 만 명시 keep 필요
#  - 스택 트레이스 디버깅을 위해 라인 정보는 유지

# 스택 트레이스용 — Play Console 크래시 리포트에서 라인 보임 (mapping.txt 업로드 시 자동 디미니피케이션)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== Rule 데이터 클래스 — JSON 직렬화에 필드명 사용 =====
# fromJson/toJson에서 obj.optString("필드명") 패턴이라 필드명이 난독화되면 깨짐
# 단, JSON에 들어가는 키는 코드 안에서 리터럴 문자열로 하드코딩돼있어 안전한데,
# 필드 자체는 reflection 안 쓰므로 클래스 보존만 하면 됨
-keep class io.github.monochrome0xd.customanimationalert.Rule { *; }

# ===== Firestore POJO =====
# AnimationStore.RemoteAnimation은 doc.toObject(RemoteAnimation::class.java) 호출.
# Firestore가 리플렉션으로 필드 매핑 → 필드명 보존 필요
-keep class io.github.monochrome0xd.customanimationalert.AnimationStore$RemoteAnimation { *; }
-keepclassmembers class io.github.monochrome0xd.customanimationalert.AnimationStore$RemoteAnimation {
    <init>(...);
}

# ===== Firebase 일반 =====
# Firestore의 코드 생성 + 리플렉션 — 라이브러리 consumer rules가 대부분 처리하지만 안전망
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ===== Lottie =====
# Lottie 자체에 consumer-rules 있음, 추가 보호:
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ===== Coil =====
# Coil은 consumer-rules 충분하지만 OkHttp 의존성 경고 무시
-dontwarn org.conscrypt.**
-dontwarn okio.**
-dontwarn okhttp3.**

# ===== Media3 ExoPlayer =====
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ===== WorkManager =====
# 커스텀 Worker 클래스는 newInstance 호출되니까 보존
-keep class io.github.monochrome0xd.customanimationalert.CoinPriceWorker { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# ===== Compose 런타임 =====
# Compose 자체는 consumer rules에서 처리. -dontwarn 만 추가
-dontwarn androidx.compose.**

# ===== Kotlin Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ===== Credential Manager / GoogleId =====
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# ===== 우리 앱 컴포넌트 =====
# AndroidManifest에 등록된 컴포넌트는 R8이 자동 보호하지만 명시
-keep class io.github.monochrome0xd.customanimationalert.App { *; }
-keep class io.github.monochrome0xd.customanimationalert.MainActivity { *; }
-keep class io.github.monochrome0xd.customanimationalert.AlertNotificationListener { *; }
-keep class io.github.monochrome0xd.customanimationalert.OverlayService { *; }

# ===== Serializable (있을 경우 대비) =====
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
