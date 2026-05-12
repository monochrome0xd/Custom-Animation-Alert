# 커스텀 애니메이션 알림 (Custom Animation Alert)

안드로이드용 알림 커스터마이징 앱. 특정 키워드 또는 앱에서 알림이 왔을 때 사용자가 등록한 미디어(이미지·GIF·동영상)와 사운드를 화면 위에 띄우고, 물리학 기반 애니메이션으로 표현한다.

> **상태**: 자체 사용/테스트 단계. Play Store 출시·마켓플레이스 오픈은 완성도 높인 후.

---

## 프로젝트 컨텍스트

- **개발자**: 코딩 입문자, AI 어시스턴트와 함께 처음부터 학습하며 진행
- **타겟 플랫폼**: Android만 (iOS는 시스템적으로 타 앱 알림 접근 불가)
- **언어/프레임워크**: Kotlin + Jetpack Compose
- **주요 안드로이드 API**:
  - `NotificationListenerService` — 시스템 알림 가로채기
  - `SYSTEM_ALERT_WINDOW` — 화면 위 오버레이
  - `WindowManager.updateViewLayout` — 윈도우 위치 직접 이동
  - `WindowInsets` (API 30+) — 폰 기종별 안전 영역 자동 감지
  - `PowerManager.WakeLock` — 화면 꺼져있을 때 깨우기
- **데이터 저장**: SharedPreferences + JSONArray (다중 규칙 RuleStore)
- **외부 라이브러리**: Lottie Compose (체크마크/마켓 아이콘/로딩 애니)
- **테스트 폰**: Samsung Galaxy SM-S937N (S23+)
- **패키지명**: `io.github.monochromex.customanimationalert`

---

## 주요 기능

### 매칭 (어떤 알림에 발동할지)
- **다중 키워드 매칭**: 한 규칙에 여러 키워드 등록 가능 (OR 조건)
- **앱 매칭**: 특정 앱(패키지명) 알림 시 발동
- **키워드 사용 ON/OFF 토글**
- **조합 규칙**: 키워드/앱 동시 활성 시 AND 조건
- **다중 규칙 지원**: RuleStore (SharedPreferences + JSONArray) — 그룹별 관리
- **충돌 처리 (구체성 우선)**: 여러 규칙이 매칭되면 가장 구체적인 것만 발동
  - 점수: 앱+키워드 둘 다 매칭=3, 키워드만=2, 앱만=1
- **충돌 시 같이 재생 토글**: 같이 발동되도록 강제 (per-rule)
- **시스템 노이즈 필터링**:
  - `FLAG_ONGOING_EVENT` 차단 (백그라운드 서비스 알림)
  - `FLAG_FOREGROUND_SERVICE` 차단
  - `FLAG_GROUP_SUMMARY` 차단 (카톡 그룹 요약)
  - 빈 제목/본문 알림 차단
- **자기 패키지 알림 무시** (앱이 만든 알림 자체)
- **키워드 중복시 재생안함**: 규칙별 쿨타임 설정 (초 단위, 직접 입력)
  - 같은 sbn.key + 같은 (title|text) 조합이 쿨타임 안에 다시 오면 무시
  - 채팅창 진입 시 "읽음" 갱신으로 중복 발동되는 케이스 방어

### 미디어
- **이미지**: JPG/PNG/WebP/GIF (Android 9+에서 GIF·WebP 애니메이션 자동 재생)
- **동영상**: MP4/MOV 등 (`VideoView`, 기본 H.264/HEVC 지원)
- **반복 재생 토글**: GIF/WebP 선택 시 자동 노출 (`AnimatedImageDrawable.repeatCount` API 31+)
- **선택 방법 두 가지**:
  - 갤러리 (`PickVisualMedia`) — MediaStore 인덱스 미디어
  - 파일 (`OpenDocument`) — 모든 폴더 + Drive 등
- **미디어 미설정 + 앱 선택 시**: 알림 보낸 앱의 런처 아이콘이 폴백으로 표시 (원형 마스킹)
- **미디어 위치 조절**: 오버레이 모드로 앱 위에 미디어 직접 띄워 드래그
  - 가장자리 자동 snap (16dp 이내), 화면 밖 이동 차단
  - 좌하단 "초기화" 버튼 / 우하단 "저장" 버튼 (둘 다 드래그 가능)
- **회전 입력**: 도(°) 단위 직접 입력 필드
- **크기 슬라이더**: 미디어 50~600dp, 앱 아이콘 25~200dp + 랜덤 토글

### 사운드
- **사용자 파일 업로드**: SAF로 오디오 파일 선택 — 임포트 직후 라우드니스 자동 분석 (백그라운드 코루틴, 1~3초)
- **URL로 가져오기**: 직접 오디오 파일(mp3/wav/ogg/m4a/aac/flac/opus) URL 또는 사운드 페이지 URL을 붙여넣어 다운로드
  - 사이트 무관 — myinstants 등 어디든 직접 미디어 파일 URL이면 동작
  - **HTML 페이지 자동 처리** — 4단계 폴백으로 mp3 URL 추출:
    1. 절대 URL 정규식 매칭 (myinstants 케이스)
    2. HTML 엔티티 디코드(`&q;`, `&amp;` 등) 후 재시도
    3. 파일명만 추출 → 같은 도메인 흔한 경로 13개(`/audio/`, `/sounds/` ...) HEAD 프로빙 (soundbuttonsworld 케이스)
    4. `<audio>`/`<source>` 태그 안 모든 URL 추출 → CDN의 `response-content-type` 같은 응답 덮어쓰기 쿼리 제거 후 HEAD 프로빙 (mewpot 케이스)
  - 안전장치: 20MB 크기 제한, HTML 본문 2MB 제한, 30초 read / 15초 connect 타임아웃, 3초 프로빙 타임아웃
  - 저장 위치: `filesDir/sounds/{uuid}.{ext}` (앱 내부 저장소, 앱 삭제 시 자동 정리)
- **미설정 시**: 시스템 기본 알림음
- **자동 라우드니스 정규화** — 임포트 시 RMS dBFS 측정, 재생 시 목표 음량까지 자동 보정
  - 측정: `LoudnessAnalyzer` (MediaExtractor + MediaCodec으로 PCM 디코딩 후 RMS 계산, 최대 60초 분석)
  - 재생: `target - measured = gain`
    - gain ≤ 0 → MediaPlayer.setVolume으로 감쇠
    - gain > 0 → setVolume(1.0) + `LoudnessEnhancer` AudioEffect로 부스트 (최대 +24 dB)
  - 결과: 큰 파일이든 작은 파일이든 슬라이더 값 근처로 수렴
  - 기존 규칙(measuredLoudnessDb == null) → 폴백: targetLoudnessDb를 그대로 감쇠로 사용 (재임포트하면 분석됨)
- **음량 슬라이더**: 목표 음량(dBFS), 범위 -30 dB ~ -3 dB, 기본 -14 dB
- **AudioAttributes USAGE_MEDIA**: 폰의 미디어 볼륨키로 직접 조절 가능
- **모드 정책 분리** (체크박스): 진동 모드 재생 / 무음 모드 재생
- **동영상 사운드 사용 토글**: ON이면 동영상 음원 사용 (사운드 선택 UI 자동 숨김)
- **닫는 인터랙션 시 페이드 아웃**: 탭/던지기 시 사운드 갑자기 끊지 않고 부드럽게

### 등장 애니메이션
- **구슬 모드**: 등가속도 운동 공식 기반 매 프레임 시뮬레이션
  - 화면 아래 (사용자 X 위치)에서 솟아오름 (포물선)
  - 정점 부근에서 자연스럽게 느려짐 (체공감)
  - 떨어지면서 가속, 바닥/벽/천장 충돌 시 탄성 반사 (감쇠 튕김)
  - 굴러감 모드 전환 (마찰 감속)
  - 회전: `θ = 거리 / 반지름` (실제 굴러가는 공의 회전)
  - 사용자 조절: 튕김 높이(0.3~0.8), 중력(0.5~2.5x), 스핀(0~3x), 탄성(0~1), 바닥 위치(0~60dp)
- **천장/벽/바닥 자동 보정**: `WindowInsets.systemBars + displayCutout`로 폰 기종별 자동 (status bar / nav bar / 디스플레이 컷아웃)
- **위로 던지기 시 dismiss 안 함**: 마블 물리로 천장에 튕김

### 인터랙션
- **드래그**: 손가락으로 미디어를 화면 어디든 이동
- **던져서 닫기**: 속도 1500px/s 이상이면 그 방향으로 날아가 사라짐 (위쪽 제외 — 천장 튕김)
- **탭해서 닫기**: 짧고 작은 터치 → 페이드아웃 + 스케일 다운
- **드래그 OFF**: 미디어가 다른 앱 터치를 막지 않음 (`FLAG_NOT_TOUCHABLE`)
- **드래그 후 안 던지면 6초 후 자동 사라짐** (이전 1.5초보다 충분히 연장)

### 알림 처리
- **애니메이션 중첩 (stackOverlays)**: ON이면 여러 알람의 미디어가 동시에 화면 (최대 10개)
- **화면 꺼져있을 때 깨우기 (wakeScreen)**:
  - `PowerManager.WakeLock(SCREEN_BRIGHT | ACQUIRE_CAUSES_WAKEUP)`로 화면 ON
  - 윈도우 플래그 `FLAG_SHOW_WHEN_LOCKED` + `FLAG_TURN_SCREEN_ON` + `FLAG_KEEP_SCREEN_ON`로 잠금 화면 위 표시

### UI
- **3탭 하단 네비**: 홈 / 마켓 / 설정
- **필 모양 떠있는 메뉴 바**: 컨텐츠 위에 overlay, 위로 스크롤 시 사라짐
- **마켓 탭 아이콘**: Lottie 애니메이션 (cloud upload, 클릭/마운트 시 1회 재생)
- **권한 안내 배너**: 알림 접근 / 다른 앱 위에 표시 권한 빠지면 자동 표시 + 시스템 설정 링크
- **홈 탭 (규칙 목록)**:
  - 그룹별 가로 스크롤 (3.n 카드 peek 효과)
  - 그룹 간 세로 스크롤 (Netflix 스타일)
  - 9:16 카드 (휴대폰 화면 미리보기) — 배경은 현재 테마의 `surfaceVariant` (테마 따라 변경됨)
  - 카드 안에서 마블 물리 시뮬레이션 (2.5초마다 반복)
  - 카드 좌상단 ▶ 재생 버튼 (클릭 즉시 OverlayService 발동)
  - 카드 우상단 ⋯ 메뉴 (삭제)
  - 토글: 점등형 동그라미 (Lottie 체크마크, 세이지 그린 — 테마 무관 공통)
  - 키워드/이름 길면 마키 스크롤
- **설정 탭**:
  - 권한 배너 (알림 접근 / 오버레이)
  - **테마 선택** (라디오 + 4색 미리보기 스와치, 5개 프리셋)
  - 앱 정보
- **그룹 헤더**: 양 끝 spread, 편집(✏)/추가(+) 원형 버튼
- **앱 선택 다이얼로그**: 
  - 모든 설치된 앱 (시스템 앱 토글로 필터)
  - 아이콘 + 라벨 + 패키지명
  - 라벨/아이콘 모두 비동기 로드 + 캐싱 (멈춤 방지)
  - 로딩 중 Lottie liquid loader 표시
- **뒤로가기**: BackHandler로 편집 화면 → 목록

### 마터리얼 디자인 / 테마
- **5개 프리셋 테마** — 설정 탭에서 사용자가 선택, SharedPreferences에 저장. 기본값 **Notion Mono**.
  - **Notion Mono** (기본) — 차콜 + 오프화이트 + 블루 액센트 (Notion/Linear 스타일)
  - **Café Cream** — 아이보리/베이지 + 머스타드 골드 (웜 톤, 빈티지)
  - **Forest** — 세이지/포레스트 그린 (자연, 차분)
  - **Lavender** — 라벤더 + 딥 퍼플 (소프트, 드리미)
  - **Sunset** — 피치/코랄 + 오렌지 (따뜻함, 에너지)
- **각 테마는 라이트/다크 모두 정의** — 시스템 다크 모드 토글 따라감
- **세이지 그린 (`#7C8F6D`)** 토글 점등은 모든 테마 공통 (액센트와 시각 구분)
- **dynamicColor OFF** — 폰 배경화면 색에 영향 안 받음, 일관된 디자인
- 구현: [`ui/theme/Themes.kt`](app/src/main/java/io/github/monochromex/customanimationalert/ui/theme/Themes.kt) — `AppTheme` enum + 라이트/다크 ColorScheme 매핑, `ThemeStore` (SharedPreferences + `mutableStateOf`)

---

## 파일 구조

```
app/src/main/
├── java/io/github/monochromex/customanimationalert/
│   ├── MainActivity.kt              # Compose 메인 화면 + 모든 UI Composable
│   ├── AlertNotificationListener.kt # NotificationListenerService — 매칭/필터링/쿨타임
│   ├── OverlayService.kt            # SYSTEM_ALERT_WINDOW 오버레이 + 마블 물리
│   ├── Rule.kt                      # 데이터 모델 (Rule, RuleStore, EntryModes)
│   ├── SoundDownloader.kt           # URL → filesDir/sounds 다운로드 (HttpURLConnection + 4단계 HTML 파싱 폴백)
│   ├── LoudnessAnalyzer.kt          # 오디오 파일 RMS dBFS 측정 (MediaExtractor + MediaCodec)
│   └── ui/theme/
│       ├── Theme.kt                 # MaterialTheme 래퍼 (ThemeStore 읽음)
│       ├── Themes.kt                # AppTheme enum (5종) + ColorScheme 매핑 + ThemeStore
│       └── Type.kt                  # Typography
├── res/
│   ├── raw/                         # Lottie JSON 애니메이션
│   │   ├── checkmark.json           # 토글 점등 애니
│   │   ├── cloud.json               # 마켓 탭 아이콘
│   │   └── loader.json              # 앱 목록 로딩 인디케이터
│   ├── drawable/, mipmap-*/         # 앱 아이콘
│   └── values/strings.xml           # app_name = "커스텀 애니메이션 알림"
└── AndroidManifest.xml              # 권한 + 서비스 등록
```

### `AndroidManifest.xml` 핵심
- `SYSTEM_ALERT_WINDOW` (오버레이)
- `WAKE_LOCK` + `DISABLE_KEYGUARD` (화면 깨우기)
- `INTERNET` (URL로 사운드 다운로드)
- `QUERY_ALL_PACKAGES` (앱 목록 조회 — Play Store 출시 시 정당화 필요)
- `BIND_NOTIFICATION_LISTENER_SERVICE` (`AlertNotificationListener` 서비스)

---

## 동작 정책 (중요)

### 키워드 중복 차단 (per-rule)
매칭된 규칙의 `blockSameContentRepeat == true`이면 같은 sbn.key + 같은 `(title|text)` 조합이 `sameContentCooldownSec` 이내 다시 오면 무시. 카톡 채팅창 진입 시 "읽음" 갱신으로 onNotificationPosted가 다시 호출되는 버그 방어.

### 우선순위 (가장 구체적인 규칙 우선)
- 앱 + 키워드 매칭 (점수 3) > 키워드만 (점수 2) > 앱만 (점수 1)
- `playAlongside == true`인 다른 매칭 규칙은 같이 발동
- 동급일 경우 RuleStore 순서대로

### 알림 중첩 OFF시 화면 정리 순서
새 알람 들어와 알림 중첩 OFF인 경우:
1. `instances.toList().forEach { it.cleanup(checkStopSelf = false) }` — 기존 인스턴스 정리, `stopSelf` 호출 안 함
2. 새 `OverlayInstance` 생성 + 추가
3. `showOverlay`로 새 미디어 표시

`cleanup(checkStopSelf = true)` 기본값 (자연스러운 dismiss): 인스턴스 정리 후 `instances`가 비어있으면 `stopSelf()` → 서비스 종료

이 분리 없으면 새 알람 와도 화면에 미디어 안 나타나는 버그 발생.

### 위치/회전 적용
- `instance.centerX/Y` = 사용자 저장 `targetXFraction/Y * screenSize - sizePx/2`
- 마블 entry 시작 X도 `instance.centerX` (이전 랜덤 X에서 변경)
- `view.rotation = rule.targetRotation` 모든 모드에서 적용

### 위치 편집 동기화
- OverlayService → RuleStore 직접 저장
- `RuleUpdateBus` (singleton MutableState)로 UI에 알림
- RuleEditScreen이 `LaunchedEffect(RuleUpdateBus.lastUpdate)`로 RuleStore에서 fresh 값 reload

### 폰 기종별 안전 영역
`getSafeInsets()` (API 30+: `WindowInsets.systemBars + displayCutout`, 이전: 시스템 리소스 폴백). 마블 물리의 바닥/천장/좌우 벽 모두 자동 보정.

---

## 빌드 / 실행

1. Android Studio (Panda 4 권장)에서 프로젝트 열기
2. Gradle sync 완료 대기
3. 안드로이드 폰 USB 디버깅 ON, USB 연결
4. ▶️ Run → 자동 빌드 + 설치
5. 첫 실행 시 사용자가 직접 허용해야 할 권한:
   - **알림 접근**: 폰 설정 → 알림 → 알림 접근 → "커스텀 애니메이션 알림" ON
   - **다른 앱 위에 표시**: 폰 설정 → 검색 "다른 앱 위에 표시" → 본 앱 ON
   - 앱 안의 권한 배너에서 직접 시스템 설정으로 이동 가능

---

## 디버깅

### Logcat 태그
- `AlertListener` — 알림 매칭 흐름 (수신/플래그/매칭/쿨타임/발동)
- `OverlayService` — 오버레이 동작 (사운드 재생, 시각 효과 등)

### 자주 만나는 문제
- **알림 접근 권한 켰는데 안 받아짐** → 폰 재부팅 한 번
- **OEM 배터리 매니저(샤오미/화웨이/오포 등)가 백그라운드 서비스 종료** → 배터리 최적화 제외 권장
- **알림 중첩 OFF인데 알림 와도 미디어 안 뜸** → `cleanup(checkStopSelf = false)` 분리가 안 된 버전. 위 "동작 정책" 참조
- **카톡 등 채팅창 진입 시 미디어 발동** → `blockSameContentRepeat` 토글 ON + 적절한 쿨타임
- **앱 선택 다이얼로그 멈춤** → 라벨/아이콘 비동기 로드되도록 처리됨 (캐시도 있음). 첫 로드 시 잠깐 Lottie loader 표시
- **위치 편집 후 적용 안 됨** → `RuleUpdateBus` 통해 동기화. `LaunchedEffect`로 reload하므로 즉시 반영

---

## 향후 작업 (TODO)

### UI/UX 다듬기
- [ ] **상단 헤더 여백** — "Custom Animation Alert" 타이틀과 + 버튼이 화면 최상단에 너무 붙어있음
- [ ] **시각 요소 강화** — 글자 위주의 UI에 아이콘/일러스트/색상 포인트 추가

### 기능 추가
- [ ] **가로모드 알림 비활성화 토글** — 설정 메뉴에서
- [ ] **연락처 기반 매칭** — `READ_CONTACTS` 권한, 연락처 선택 UI
- [ ] **앱 다중 선택** — 한 규칙에 여러 앱 등록 (현재 키워드만 다중)
- [ ] **앱별 정교한 매칭 디테일** — "인스타 좋아요만" 같은 본문 패턴 매칭

### 마켓플레이스 (큰 작업, 완성도 높인 후)
- [ ] **백엔드 인프라** — Firebase Authentication + Firestore + Cloud Storage
- [ ] **마켓 V1 (무료 공유)** — 업로드/다운로드/검색/카테고리
- [ ] **수익화** — Google Play Billing
- [ ] **운영/컴플라이언스** — 신고 시스템, 약관, 콘텐츠 검토

### 출시 준비 (마찬가지로 완성도 높인 후)
- [ ] **Play Store 등록** — Core Function 신고서 (알림 접근 권한), `QUERY_ALL_PACKAGES` 사유 기재
- [ ] **개인정보처리방침** — GitHub Pages에 호스팅
- [ ] **광고 삽입 검토** — AdMob, 위치/시점 결정
- [ ] **MVP 자체 사용 검증** — 충분한 기간 일상에서 사용

### 우선순위 낮음 / 나중에
- [ ] **iOS 라이트 버전** — 자기 앱 푸시 한정 (시스템적 한계로 동등 구현 불가)

---

## 의존성 (build.gradle.kts)

- AndroidX Core, Activity Compose, Material3, Compose BOM
- Material Icons (Core)
- **Lottie Compose** (`com.airbnb.android:lottie-compose:6.6.0`) — 토글 체크마크, 마켓 아이콘, 로딩 인디케이터

---

## 라이선스

미정 (개인 프로젝트)
