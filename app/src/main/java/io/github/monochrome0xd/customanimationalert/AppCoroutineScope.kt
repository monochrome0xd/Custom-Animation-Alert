package io.github.monochrome0xd.customanimationalert

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * UI 생명주기와 무관하게 살아있는 코루틴 스코프.
 * Compose의 rememberCoroutineScope는 컴포저블이 unmount되면 취소되는데,
 * 파일 업로드/다운로드 같은 fire-and-forget 작업은 화면 전환·LazyRow 리사이클 등으로 끊겨선 안 됨.
 *
 * 사용처: 카드의 공유 버튼, 마켓 다운로드 등.
 * 주의: 이 스코프에서 시작한 작업은 Activity가 죽어도 계속 돌 수 있음 → 누수 방지를 위해 가능하면
 *      ApplicationContext만 캡쳐할 것 (Activity context 캡쳐 시 메모리 누수 위험).
 */
object AppCoroutineScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
