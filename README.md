# Custom Animation Alert

안드로이드용 알림 커스터마이징 앱. 특정 키워드 또는 앱에서 알림이 왔을 때 사용자가 등록한 미디어(이미지·GIF·동영상)와 사운드를 화면 위에 띄워주고, 물리학 기반 애니메이션으로 표현한다.

> **상태**: MVP 개발 중. 단일 규칙만 지원. 다중 규칙(JSON 직렬화 + 규칙 목록 UI)은 작업 중.

---

## 프로젝트 컨텍스트 (새 세션에서 빠르게 따라잡으려면 이 섹션을 읽으세요)

- **개발자**: 코딩 입문자, AI 어시스턴트와 함께 처음부터 학습하며 진행
- **타겟 플랫폼**: Android만 (iOS는 시스템적으로 타 앱 알림 접근 불가)
- **언어/프레임워크**: Kotlin + Jetpack Compose
- **주요 안드로이드 API**:
  - `NotificationListenerService` — 시스템 알림 가로채기
  - `SYSTEM_ALERT_WINDOW` — 화면 위 오버레이
  - `WindowManager.updateViewLayout` — 윈도우 위치를 직접 이동시켜 화면 어디든 갈 수 있음
- **데이터 저장**: SharedPreferences (단일 규칙). 다중 규칙으로 전환 시 JSONArray + JSONObject 직접 사용 예정 (외부 의존성 추가 X)
- **테스트 폰**: Samsung Galaxy SM-S937N
- **Android Studio**: Panda 4 (2025.3.4)

---

## 주요 기능

### 매칭 (어떤 알림에 발동할지)
- **키워드 매칭**: 알림 제목/본문에 특정 단어 포함 시 발동
- **앱 매칭**: 특정 앱(패키지명) 알림 시 발동
- **키워드 사용 ON/OFF 토글**: 텍스트는 그대로 두고 일시 비활성 가능
- **조합 규칙**:
  - 키워드만 활성 → 키워드 매칭
  - 앱만 선택 → 그 앱의 모든 알림
  - 둘 다 활성 → AND (그 앱의 그 키워드만)
  - 둘 다 비활성 → 발동 안 함
- **글로벌 디바운스**: 매칭된 트리거 후 800ms 이내 새 트리거는 무시
  - 카톡 등에서 알림이 갱신되면서 같은 메시지에 대해 onNotificationPosted가 여러 번 호출되는 현상 방지
  - 의도된 다른 알림도 800ms 이내면 무시되니, 이 시간이 너무 길면 `AlertNotificationListener.kt`의 `debounceMs` 상수 조정
  - 권장 값: 500~1500ms

### 미디어 (어떤 시각 효과를 띄울지)
- **이미지**: JPG/PNG/WebP/GIF (Android 9+에서 GIF·WebP 애니메이션 자동 재생)
- **동영상**: MP4 등 (VideoView)
- **미디어 미설정 + 앱 선택 시**: 알림 보낸 앱의 런처 아이콘이 폴백으로 표시 (원형 마스킹)
- **크기 슬라이더**: 미디어 50~600dp, 앱 아이콘 50~200dp
- **랜덤 토글**: 각 슬라이더마다 매 알람 시 랜덤 값 사용 옵션

### 사운드
- **사용자 파일 업로드**: SAF로 오디오 파일 선택 (권한 우회)
- **미설정 시**: 시스템 기본 알림음
- **음량 슬라이더**: 0~100% (시스템 미디어 볼륨에 곱해지는 비율)
- **AudioAttributes USAGE_MEDIA**: 폰의 미디어 볼륨키로 직접 조절 가능
- **모드 정책 분리 토글**:
  - 진동 모드에서 재생 ON/OFF
  - 무음 모드에서 재생 ON/OFF
  - (미디어는 모드 무관 항상 재생, 사운드만 정책 적용)
- **동영상 자체 사운드 사용 토글**: ON이면 동영상 음원 사용 / OFF면 별도 사운드 파일

### 등장 애니메이션
- **스프링 모드**: 화면 가운데로 OvershootInterpolator (튕기는 듯)
- **구슬 모드**: 등가속도 운동 공식 기반 매 프레임 시뮬레이션
  - 화면 아래 랜덤 위치에서 솟아오름 (포물선)
  - 정점 부근에서 자연스럽게 느려짐 (체공감, `v=0`)
  - 떨어지면서 가속
  - 바닥 충돌 시 탄성 반사 (감쇠 튕김 → 점점 작아짐)
  - 좌우 벽 충돌 시 탄성 반사
  - 굴러감 모드 전환 (마찰 감속)
  - 회전: `θ = 거리 / 반지름` (실제 굴러가는 공의 회전)
  - 사용자 조절 슬라이더: 튕김 높이(0.3~0.8), 중력(0.5~2.5x), 스핀(0~3x), 탄성(0~1)

### 인터랙션
- **드래그**: 손가락으로 미디어를 화면 어디든 이동
- **던져서 닫기**: 속도 1500px/s 이상이면 그 방향으로 날아가 사라짐
- **탭해서 닫기**: 짧고 작은 터치 → 페이드아웃 + 스케일 다운 사라짐
- **드래그 OFF**: 미디어가 다른 앱 터치를 막지 않음 (`FLAG_NOT_TOUCHABLE`)

### 알람 중첩
- **OFF (기본)**: 새 알람 오면 이전 미디어 사라짐
- **ON**: 여러 알람의 미디어가 동시에 화면에 떠있음 (최대 10개)
- 구현: `OverlayInstance` 내부 클래스로 각 알림 상태 캡슐화 (view, params, mediaPlayer, videoView, dismissRunnable, velocityTracker)
- `cleanup(checkStopSelf: Boolean = true)` 매개변수로 새 알람 대비 정리 시엔 `stopSelf` 호출 안 함 (그래야 새 인스턴스가 onDestroy로 휘말려 같이 정리되지 않음)

### UI
- **메인 설정 화면**: 핵심 옵션만 (키워드, 앱, 미디어, 사운드, 등장 애니메이션 ON/OFF, 등장 모드, 테스트 재생)
- **상세 설정 (접기/펼치기)**: 슬라이더와 세부 토글 모두
- **앱 선택 다이얼로그**: 설치된 앱 목록 + 검색

---

## 파일 구조

```
app/src/main/
├── java/com/example/myapplication/
│   ├── MainActivity.kt            # Compose 설정 화면 + AppPickerDialog
│   ├── AlertNotificationListener.kt  # NotificationListenerService — 매칭 + 디바운스
│   ├── OverlayService.kt          # SYSTEM_ALERT_WINDOW 오버레이 + 물리학 애니메이션
│   └── ui/theme/                  # Compose 테마
├── res/
│   ├── drawable/
│   │   ├── ic_launcher_background.xml
│   │   └── ic_launcher_foreground.xml
│   ├── mipmap-anydpi-v26/
│   │   ├── ic_launcher.xml         # Adaptive Icon (직접 작성)
│   │   └── ic_launcher_round.xml
│   ├── mipmap-*/                   # 해상도별 ic_launcher.webp
│   └── values/
│       ├── strings.xml             # app_name = "Custom Animation Alert"
│       ├── colors.xml
│       └── themes.xml
└── AndroidManifest.xml             # 권한 + 서비스 등록
```

### `AndroidManifest.xml` 핵심
- `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`
- `<service android:name=".AlertNotificationListener" ...>` — `BIND_NOTIFICATION_LISTENER_SERVICE` 권한 + intent filter `android.service.notification.NotificationListenerService`
- `<service android:name=".OverlayService" android:exported="false" />`

---

## 데이터 모델 (SharedPreferences)

파일 이름: `"rules"` (단일 규칙 시절). 다중 규칙 전환 후 별도 마이그레이션 예정.

### 매칭
| 키 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `keywordEnabled` | Boolean | true | 키워드 사용 토글 |
| `keyword` | String | "테스트" | 매칭할 키워드 |
| `packageName` | String? | null | 선택된 앱 패키지명 (예: `com.kakao.talk`) |
| `appLabel` | String? | null | 선택된 앱 표시명 (예: "카카오톡") |

### 미디어
| 키 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `mediaUri` | String? | null | 미디어 URI |
| `mediaType` | String | "image" | "image" 또는 "video" |
| `mediaName` | String? | null | 파일명 표시용 |
| `useVideoSound` | Boolean | true | 동영상 자체 사운드 사용 |
| `mediaSize` | Float | 250 | dp, 50~600 |
| `mediaSizeRandom` | Boolean | false | 랜덤 모드 |
| `appIconSize` | Float | 100 | dp, 50~200 |
| `appIconSizeRandom` | Boolean | false | 랜덤 모드 |

### 사운드
| 키 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `soundUri` | String? | null | 사운드 URI |
| `soundName` | String? | null | 파일명 표시용 |
| `volume` | Float | 1.0 | 0.0~1.0 |
| `playInVibrate` | Boolean | false | 진동 모드 재생 |
| `playInSilent` | Boolean | false | 무음 모드 재생 |

### 애니메이션
| 키 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `entryAnimation` | Boolean | true | 등장 애니메이션 ON/OFF |
| `entryMode` | String | "spring" | "spring" 또는 "marble" |
| `bouncePeak` | Float | 0.5 | 화면 높이 비율, 0.3~0.8 |
| `bouncePeakRandom` | Boolean | false | |
| `gravityScale` | Float | 1.0 | 중력 배수, 0.5~2.5 |
| `gravityScaleRandom` | Boolean | false | |
| `spinScale` | Float | 1.0 | 스핀 배수, 0~3 |
| `spinScaleRandom` | Boolean | false | |
| `elasticity` | Float | 0.5 | 탄성 계수, 0~1 |
| `elasticityRandom` | Boolean | false | |

### 인터랙션
| 키 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `dragEnabled` | Boolean | true | 드래그 가능 |
| `flingToDismiss` | Boolean | true | 던져서 닫기 |
| `tapToDismiss` | Boolean | false | 탭해서 닫기 |

### 알림 처리 / UI
| 키 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `stackOverlays` | Boolean | false | 알람 중첩 |
| `showAdvanced` | Boolean | false | 상세 설정 펼침 상태 |

---

## 동작 정책 (중요)

### 디바운스 (중복 알림 방지)
`AlertNotificationListener`에서 매칭이 성공한 트리거 후 `debounceMs` (기본 800ms) 이내 발생하는 새 매칭은 모두 무시한다. 이는 다음 현상을 막기 위함:
- 카톡, 디스코드 같은 앱은 한 메시지에 대해 알림을 여러 번 갱신하면서 `onNotificationPosted`를 호출
- 같은 `sbn.key`로 오는 경우도 있고 다른 `sbn.key`로 오는 경우도 있어 키 기반 디바운스로는 부족
- 따라서 글로벌 시간 기반 디바운스 사용

트레이드오프: 진짜로 짧은 시간 안에 다른 알림 두 개가 와도 두 번째는 무시됨. `debounceMs` 값을 줄이면(예: 300ms) 더 민감하게, 늘리면 더 보수적으로 동작.

### 알람 중첩 OFF시 화면 정리 순서
새 알람이 들어와 알람 중첩이 OFF인 경우:
1. `instances.toList().forEach { it.cleanup(checkStopSelf = false) }` — 기존 인스턴스 정리, 단 stopSelf는 호출 안 함
2. 새 `OverlayInstance` 생성 + 추가
3. `showOverlay`로 새 미디어 표시

`cleanup(checkStopSelf = true)` 기본값으로 호출되는 경우(자연스러운 dismiss):
- 인스턴스 정리 후 `instances`가 비어있으면 `stopSelf()` 호출 → 서비스 종료

이 분리가 없으면 새 알람이 와도 화면에 미디어가 안 나타나는 버그 발생 (시스템이 stopSelf 영향으로 onDestroy 호출하면서 새 인스턴스도 정리됨).

---

## 빌드 / 실행

1. Android Studio (Panda 4 권장)에서 프로젝트 열기
2. Gradle sync 완료 대기
3. 안드로이드 폰 USB 디버깅 ON, USB로 PC 연결
4. ▶️ Run → 자동 빌드 + 설치
5. 처음 실행 시 두 가지 시스템 권한을 사용자가 직접 허용해야 함:
   - **알림 접근**: 폰 설정 → 알림 → 알림 접근 → "Custom Animation Alert" ON
   - **다른 앱 위에 표시**: 폰 설정 → 검색 "다른 앱 위에 표시" → "Custom Animation Alert" ON

---

## 디버깅

### Logcat 태그
- `AlertListener` — 알림 매칭 흐름 (어떤 알림이 들어왔고 매칭 결과가 어떻게 나왔는지, 디바운스 통과/스킵)
- `OverlayService` — 오버레이 동작 (사운드 재생, 시각 효과 등)

### 자주 만나는 문제
- **알림 접근 권한 켰는데 안 받아짐** → 폰 재부팅 한 번
- **OEM 배터리 매니저(샤오미/화웨이/오포 등)가 백그라운드 서비스 종료** → 배터리 최적화 제외 권장
- **카톡 그룹 알림이 매칭 안 됨** → 일부 알림은 다른 패키지명으로 옴. Logcat에서 실제 패키지명 확인
- **`<?xml ...?>` 두 번 박힘 에러** → Android Studio Image Asset Studio가 만든 XML이 가끔 망가짐. 직접 XML 작성 또는 mipmap webp만 사용
- **알람 중첩 OFF인데 알림 와도 미디어 안 뜸** → `cleanup(checkStopSelf = false)` 분리가 안 된 버전. 위 "동작 정책" 참조
- **카톡 등에서 한 메시지에 두 개의 미디어가 동시에 뜸** → 디바운스 시간(`debounceMs`) 늘리기 (1000~1500ms)

---

## 향후 작업 (TODO)

### 우선순위 높음 (작업 중)
- [ ] **다중 규칙 지원** — JSON 직렬화 + RuleStore 클래스 + 규칙 목록 UI + 규칙 편집 화면. SharedPreferences에 JSONArray로 저장. AlertNotificationListener는 모든 활성 규칙에 대해 매칭, OverlayService는 매칭된 ruleId로 해당 규칙의 설정 사용.

### 우선순위 중간
- [ ] **연락처 기반 매칭** — `READ_CONTACTS` 권한, 연락처 선택 UI, 전화/문자 발신자 매칭
- [ ] **앱별 정교한 매칭 디테일** — "인스타 좋아요만" 같은 본문 패턴 매칭
- [ ] **MVP 자체 사용 검증** — 1주일 일상에서 사용해보고 피드백

### 우선순위 낮음 / 나중에
- [ ] **Lottie 애니메이션 프리셋** — 고정 mp4/이미지 외에 Lottie JSON 애니메이션
- [ ] **Play Store 출시** — Core Purpose(알림 접근) 신고 작성 필요
- [ ] **iOS 라이트 버전** — 자기 앱 푸시 한정으로 일부 기능만 (시스템적 한계로 동등 구현 불가)

---

## 알려진 이슈 / 제약

- 단일 규칙만 저장 가능 (다중 규칙은 작업 중)
- 동영상이 회전할 때 정사각 영역 외부가 잘릴 수 있음
- 알람 중첩 ON일 때 11번째 알람부터는 가장 오래된 것부터 자동 제거 (메모리 보호, 최대 10개)
- 시스템 헤드업 알림 배너는 본 앱과 별개로 폰이 따로 띄움 (사용자 폰 설정에서 끌 수 있음)
- 키워드 OFF + 앱 매칭 시 일부 패키지명이 알림 송신 시 미묘하게 다를 수 있음 (예: 카톡 그룹 알림). Logcat의 `AlertListener` 로그로 실제 패키지명 확인 후 그 값으로 등록
- 글로벌 디바운스로 인해 빠르게 들어오는 다른 알림 두 개 중 두 번째는 무시될 수 있음 (의도된 트레이드오프)

---

## 의존성 (build.gradle.kts)

기본 Android Compose 프로젝트 템플릿 그대로:
- AndroidX Core, Activity Compose, Material3
- Compose BOM
- (Lottie, Coil, Room, Gson 등 외부 라이브러리는 아직 사용 안 함)

---

## 라이선스

미정 (개인 프로젝트)
