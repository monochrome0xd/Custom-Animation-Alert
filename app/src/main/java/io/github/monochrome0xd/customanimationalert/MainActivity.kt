package io.github.monochrome0xd.customanimationalert

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.monochrome0xd.customanimationalert.ui.theme.AppTheme
import io.github.monochrome0xd.customanimationalert.ui.theme.MyApplicationTheme
import io.github.monochrome0xd.customanimationalert.ui.theme.ThemeMode
import io.github.monochrome0xd.customanimationalert.ui.theme.ThemeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// 서비스(OverlayService)가 RuleStore를 갱신한 뒤 UI에 알리기 위한 작은 이벤트 버스.
// (timestamp, ruleId) 쌍으로 매번 새 값이 되어 LaunchedEffect 트리거.
object RuleUpdateBus {
    var lastUpdate by mutableStateOf<Pair<Long, String>?>(null)
    fun notifyUpdated(ruleId: String) {
        lastUpdate = System.currentTimeMillis() to ruleId
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.init(applicationContext)
        AuthManager.init()
        // AdMob 광고는 V1에서 제외. V1.1 이후 재활성화 시 MobileAds.initialize(...) 추가.
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // 로그인 상태 변경 시 프로필 자동 로드
                LaunchedEffect(AuthManager.currentUser?.uid) {
                    ProfileStore.load()
                }
                AppRoot()
            }
        }
    }
}

sealed class Tab(val label: String) {
    object Library : Tab("홈")
    object Market : Tab("마켓")
    object Settings : Tab("설정")
}

sealed class Screen {
    object RuleList : Screen()
    data class RuleEdit(val ruleId: String) : Screen()
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf<Tab>(Tab.Library) }
    // 첫 실행 시 테마 선택 다이얼로그 표시
    if (!ThemeStore.hasOnboarded) {
        ThemeOnboardingDialog(
            onSelect = { theme ->
                ThemeStore.set(context, theme)
                ThemeStore.markOnboarded(context)
            }
        )
    }

    var barVisible by remember { mutableStateOf(true) }
    var libraryOnEditScreen by remember { mutableStateOf(false) }

    // 스크롤 방향 감지 — 위로 스와이프(컨텐츠 위로) → 숨김 / 아래로 스와이프 → 표시
    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -1f) barVisible = false
                else if (available.y > 1f) barVisible = true
                return Offset.Zero
            }
        }
    }

    // 규칙 편집 화면에선 메뉴 바 숨김 — 편집 중 스크롤로 올라오는 버그 방지
    val showBar = barVisible && !(currentTab == Tab.Library && libraryOnEditScreen)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .nestedScroll(nestedScroll)
    ) {
        when (currentTab) {
            Tab.Library -> LibraryTab(
                modifier = Modifier.fillMaxSize(),
                onEditScreenChange = { libraryOnEditScreen = it }
            )
            Tab.Market -> MarketTab(modifier = Modifier.fillMaxSize())
            Tab.Settings -> SettingsTab(modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(
            visible = showBar,
            enter = slideInVertically(initialOffsetY = { it * 2 }),
            exit = slideOutVertically(targetOffsetY = { it * 2 }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PillBottomBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }
    }
}

/**
 * 첫 실행 시 1회만 표시되는 테마 선택 다이어로그.
 * 선택 → ThemeStore.set + markOnboarded → 이후로는 안 나옴.
 */
@Composable
fun ThemeOnboardingDialog(onSelect: (AppTheme) -> Unit) {
    Dialog(onDismissRequest = { /* 강제 선택 — 바깥 탭/뒤로 무시 */ }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("테마 선택", style = MaterialTheme.typography.titleLarge)
                Text(
                    "마음에 드는 컬러 테마를 골라주세요. 테마는 나중에 설정에서도 변경 가능합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                AppTheme.values().forEach { theme ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(theme) }
                            .padding(vertical = 8.dp, horizontal = 8.dp)
                    ) {
                        // 색상 스와치 4개 — 미리보기
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            theme.previewSwatches().forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                            }
                        }
                        Text(
                            theme.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PillBottomBar(currentTab: Tab, onTabSelected: (Tab) -> Unit, modifier: Modifier = Modifier) {
    data class TabSpec(val tab: Tab, val icon: androidx.compose.ui.graphics.vector.ImageVector)
    val tabs = listOf(
        TabSpec(Tab.Library, Icons.Filled.Home),
        TabSpec(Tab.Market, Icons.Filled.ShoppingCart),
        TabSpec(Tab.Settings, Icons.Filled.Settings)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { spec ->
                    val selected = currentTab == spec.tab
                    // 선택된 pill: 테마의 tertiary(액센트) 색 → 테마별로 색이 확실히 달라짐
                    val tint = if (selected)
                        MaterialTheme.colorScheme.onTertiary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = if (selected) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(32.dp))
                            .clickable { onTabSelected(spec.tab) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (spec.tab == Tab.Market) {
                                MarketTabIcon(
                                    tint = tint,
                                    selected = selected,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Icon(
                                    spec.icon,
                                    contentDescription = spec.tab.label,
                                    tint = tint,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            if (selected) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    spec.tab.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarketTabIcon(tint: Color, selected: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resId = remember { context.resources.getIdentifier("cloud", "raw", context.packageName) }

    if (resId == 0) {
        Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = tint, modifier = modifier)
        return
    }

    val composition by com.airbnb.lottie.compose.rememberLottieComposition(
        com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(resId)
    )
    val animatable = com.airbnb.lottie.compose.rememberLottieAnimatable()

    // 모든 스트로크 색을 테마 색으로, "bg" 솔리드 레이어는 투명 처리
    val tintArgb = tint.toArgb()
    val dynamicProperties = com.airbnb.lottie.compose.rememberLottieDynamicProperties(
        com.airbnb.lottie.compose.rememberLottieDynamicProperty(
            property = com.airbnb.lottie.LottieProperty.STROKE_COLOR,
            value = tintArgb,
            keyPath = arrayOf("**", "Stroke 1")
        ),
        com.airbnb.lottie.compose.rememberLottieDynamicProperty(
            property = com.airbnb.lottie.LottieProperty.OPACITY,
            value = 0,
            keyPath = arrayOf("bg")
        )
    )

    // 메뉴 바 첫 등장(composition 로드) + 선택 상태 변경 시에만 1회 재생
    LaunchedEffect(composition, selected) {
        val comp = composition ?: return@LaunchedEffect
        animatable.animate(comp, iterations = 1)
    }

    // Lottie 캔버스에 빈 공간이 많고 컨텐츠가 위쪽에 몰려있어
    // 시각적으로 1.6배 확대 + 살짝 아래로 이동해서 다른 아이콘과 수평 정렬
    com.airbnb.lottie.compose.LottieAnimation(
        composition = composition,
        progress = { animatable.progress },
        dynamicProperties = dynamicProperties,
        modifier = modifier.graphicsLayer {
            scaleX = 1.6f
            scaleY = 1.6f
            translationY = 5.dp.toPx()
        }
    )
}

@Composable
fun LibraryTab(
    modifier: Modifier = Modifier,
    onEditScreenChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.RuleList) }
    var rules by remember { mutableStateOf(RuleStore.loadAll(context)) }

    LaunchedEffect(currentScreen) {
        onEditScreenChange(currentScreen is Screen.RuleEdit)
    }

    when (val screen = currentScreen) {
        is Screen.RuleList -> RuleListScreen(
            rules = rules,
            modifier = modifier,
            onAdd = { groupName ->
                val newRule = Rule(groupName = groupName.ifBlank { "기본" })
                RuleStore.upsert(context, newRule)
                rules = RuleStore.loadAll(context)
                currentScreen = Screen.RuleEdit(newRule.id)
            },
            onEdit = { id -> currentScreen = Screen.RuleEdit(id) },
            onDelete = { id ->
                RuleStore.remove(context, id)
                rules = RuleStore.loadAll(context)
            },
            onToggleEnabled = { id, enabled ->
                val r = RuleStore.find(context, id) ?: return@RuleListScreen
                RuleStore.upsert(context, r.copy(enabled = enabled))
                rules = RuleStore.loadAll(context)
            },
            onRenameGroup = { oldName, newName ->
                val target = newName.ifBlank { "기본" }
                if (target != oldName) {
                    rules.filter { it.groupName == oldName }
                        .map { it.copy(groupName = target) }
                        .forEach { RuleStore.upsert(context, it) }
                    rules = RuleStore.loadAll(context)
                }
            },
            onChanged = { rules = RuleStore.loadAll(context) }
        )
        is Screen.RuleEdit -> {
            val initial = rules.find { it.id == screen.ruleId }
            if (initial == null) {
                LaunchedEffect(Unit) { currentScreen = Screen.RuleList }
            } else {
                BackHandler {
                    rules = RuleStore.loadAll(context)
                    currentScreen = Screen.RuleList
                }
                RuleEditScreen(
                    initialRule = initial,
                    modifier = modifier,
                    onBack = {
                        rules = RuleStore.loadAll(context)
                        currentScreen = Screen.RuleList
                    },
                    onUpdate = { updated -> RuleStore.upsert(context, updated) }
                )
            }
        }
    }
}

@Composable
fun MarketTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var recent by remember { mutableStateOf<List<AnimationStore.RemoteAnimation>?>(null) }
    var popular by remember { mutableStateOf<List<AnimationStore.RemoteAnimation>?>(null) }
    var detail by remember { mutableStateOf<AnimationStore.RemoteAnimation?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var likedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var blockedUids by remember { mutableStateOf<Set<String>>(emptySet()) }

    // #38 검색
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AnimationStore.RemoteAnimation>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // #44 신고 / #40 관리자 삭제 대상
    var reportTarget by remember { mutableStateOf<AnimationStore.RemoteAnimation?>(null) }
    var adminDeleteTarget by remember { mutableStateOf<AnimationStore.RemoteAnimation?>(null) }

    // #39b 비밀번호: 잠금 해제된 항목 + 비밀번호 입력 대상
    var unlockedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var passwordTarget by remember { mutableStateOf<AnimationStore.RemoteAnimation?>(null) }
    // 본인 콘텐츠는 잠금 없음. passwordHash가 있고 아직 해제 안 됐으면 잠김.
    val isLocked: (AnimationStore.RemoteAnimation) -> Boolean = { a ->
        a.passwordHash.isNotEmpty() &&
            a.creatorUid != AuthManager.currentUser?.uid &&
            a.id !in unlockedIds
    }
    // 카드 클릭: 잠긴 항목은 비밀번호 입력, 아니면 상세로
    val onCardClick: (AnimationStore.RemoteAnimation) -> Unit = { a ->
        if (isLocked(a)) passwordTarget = a else detail = a
    }

    LaunchedEffect(refreshTick) {
        scope.launch { recent = AnimationStore.listRecent().getOrNull() ?: emptyList() }
        scope.launch { popular = AnimationStore.listPopular().getOrNull() ?: emptyList() }
        scope.launch { likedIds = AnimationStore.fetchMyLikes() }
        scope.launch { blockedUids = ModerationStore.fetchBlockedUids() }
    }

    fun runSearch() {
        val q = searchQuery.trim()
        keyboard?.hide()
        if (q.isBlank()) { searchResults = null; return }
        searching = true
        scope.launch {
            searchResults = AnimationStore.search(q).getOrNull() ?: emptyList()
            searching = false
        }
    }

    // #46 차단: 즉시 차단 + 목록 필터, 토스트
    val onBlock: (AnimationStore.RemoteAnimation) -> Unit = { animation ->
        if (AuthManager.currentUser == null) {
            Toast.makeText(context, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                val r = ModerationStore.blockUser(animation.creatorUid, animation.creatorName)
                if (r.isSuccess) {
                    blockedUids = blockedUids + animation.creatorUid
                    Toast.makeText(context, "${animation.creatorName} 님을 차단했습니다", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "차단 실패: ${r.exceptionOrNull()?.message ?: "?"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val onAdminDelete: (AnimationStore.RemoteAnimation) -> Unit = { animation ->
        adminDeleteTarget = animation
    }

    val blockFilter: (List<AnimationStore.RemoteAnimation>?) -> List<AnimationStore.RemoteAnimation>? = { list ->
        list?.filter { it.creatorUid !in blockedUids }
    }

    // 좋아요 토글 — 로컬 상태 즉시 갱신 후 서버 호출
    val onToggleLike: (AnimationStore.RemoteAnimation) -> Unit = { animation ->
        if (AuthManager.currentUser == null) {
            Toast.makeText(context, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
        } else {
            val wasLiked = animation.id in likedIds
            // 낙관적 업데이트
            likedIds = if (wasLiked) likedIds - animation.id else likedIds + animation.id
            val delta = if (wasLiked) -1L else 1L
            val updater: (AnimationStore.RemoteAnimation) -> AnimationStore.RemoteAnimation = {
                if (it.id == animation.id) it.copy(likes = (it.likes + delta).coerceAtLeast(0)) else it
            }
            recent = recent?.map(updater)
            popular = popular?.map(updater)
            scope.launch {
                val r = AnimationStore.toggleLike(animation.id)
                if (r.isFailure) {
                    // 롤백
                    likedIds = if (wasLiked) likedIds + animation.id else likedIds - animation.id
                    val rollback: (AnimationStore.RemoteAnimation) -> AnimationStore.RemoteAnimation = {
                        if (it.id == animation.id) it.copy(likes = (it.likes - delta).coerceAtLeast(0)) else it
                    }
                    recent = recent?.map(rollback)
                    popular = popular?.map(rollback)
                    Toast.makeText(context, "좋아요 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (detail != null) {
        AnimationDetailScreen(
            animation = detail!!,
            onBack = { detail = null },
            onImported = {
                detail = null
                refreshTick++  // 다운로드 카운트 +1 → 목록 재조회
            },
            onReport = { reportTarget = it },
            onBlock = onBlock,
            onAdminDelete = onAdminDelete
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item("header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text("마켓", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
        }
        // #38 검색창
        item("search") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("제목 검색") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { runSearch() }
                ),
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchResults = null
                            keyboard?.hide()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "지우기")
                        }
                    } else {
                        IconButton(onClick = { runSearch() }) {
                            Icon(Icons.Filled.Search, contentDescription = "검색")
                        }
                    }
                }
            )
        }
        // 검색 결과 (검색 중이거나 결과가 있을 때만)
        if (searching || searchResults != null) {
            item("section_search") {
                if (searching) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                } else {
                    MarketSection(
                        title = "검색 결과",
                        items = blockFilter(searchResults),
                        likedIds = likedIds,
                        onCardClick = onCardClick,
                        onToggleLike = onToggleLike,
                        onReport = { reportTarget = it },
                        onBlock = onBlock,
                        onAdminDelete = onAdminDelete,
                        isLocked = isLocked,
                        emptyText = "검색 결과가 없습니다 (비공개는 제목을 정확히 입력해야 노출됩니다)"
                    )
                }
            }
        }
        item("section_recent") {
            MarketSection(
                title = "최근",
                items = blockFilter(recent),
                likedIds = likedIds,
                onCardClick = onCardClick,
                onToggleLike = onToggleLike,
                onReport = { reportTarget = it },
                onBlock = onBlock,
                onAdminDelete = onAdminDelete,
                isLocked = isLocked
            )
        }
        item("section_popular") {
            MarketSection(
                title = "인기 (다운로드 많은 순)",
                items = blockFilter(popular),
                likedIds = likedIds,
                onCardClick = onCardClick,
                onToggleLike = onToggleLike,
                onReport = { reportTarget = it },
                onBlock = onBlock,
                onAdminDelete = onAdminDelete,
                isLocked = isLocked
            )
        }
    }

    // #44 신고 다이얼로그
    reportTarget?.let { target ->
        ReportDialog(
            animation = target,
            onDismiss = { reportTarget = null },
            onSubmit = { category, note ->
                reportTarget = null
                scope.launch {
                    val r = ModerationStore.report(target, category, note)
                    val msg = if (r.isSuccess) "신고가 접수되었습니다" else "신고 실패: ${r.exceptionOrNull()?.message ?: "?"}"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // #40 관리자 삭제 확인
    adminDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { adminDeleteTarget = null },
            title = { Text("관리자 삭제") },
            text = { Text("'${target.title.ifBlank { "(이름 없음)" }}'을(를) 마켓에서 영구 삭제합니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    adminDeleteTarget = null
                    scope.launch {
                        val r = AnimationStore.adminDelete(target)
                        if (r.isSuccess) {
                            ModerationStore.dismissReportsForAnimation(target.id)
                            Toast.makeText(context, "삭제 완료", Toast.LENGTH_SHORT).show()
                            refreshTick++
                        } else {
                            Toast.makeText(context, "삭제 실패: ${r.exceptionOrNull()?.message ?: "?"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { adminDeleteTarget = null }) { Text("취소") }
            }
        )
    }

    // #39b 비밀번호 입력 (잠긴 비공개 항목)
    passwordTarget?.let { target ->
        PasswordPromptDialog(
            animation = target,
            onDismiss = { passwordTarget = null },
            onUnlock = {
                unlockedIds = unlockedIds + target.id
                passwordTarget = null
                detail = target
            }
        )
    }
}

@Composable
private fun PasswordPromptDialog(
    animation: AnimationStore.RemoteAnimation,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("비밀번호 입력") },
        text = {
            Column {
                Text(
                    "'${animation.title.ifBlank { "(이름 없음)" }}'은(는) 비공개 항목입니다. 비밀번호를 입력하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = false },
                    label = { Text("비밀번호") },
                    singleLine = true,
                    isError = error,
                    supportingText = if (error) {
                        { Text("비밀번호가 일치하지 않습니다", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    visualTransformation = if (showPassword)
                        androidx.compose.ui.text.input.VisualTransformation.None
                    else
                        androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "숨김" else "표시")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (AnimationStore.verifyPassword(animation, input.trim())) onUnlock()
                    else error = true
                },
                enabled = input.isNotBlank()
            ) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun MarketSection(
    title: String,
    items: List<AnimationStore.RemoteAnimation>?,
    likedIds: Set<String>,
    onCardClick: (AnimationStore.RemoteAnimation) -> Unit,
    onToggleLike: (AnimationStore.RemoteAnimation) -> Unit,
    onReport: (AnimationStore.RemoteAnimation) -> Unit = {},
    onBlock: (AnimationStore.RemoteAnimation) -> Unit = {},
    onAdminDelete: (AnimationStore.RemoteAnimation) -> Unit = {},
    isLocked: (AnimationStore.RemoteAnimation) -> Boolean = { false },
    emptyText: String = "아직 공유된 애니메이션이 없습니다"
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        when {
            items == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
            items.isEmpty() -> Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                items(items, key = { it.id }) { animation ->
                    AnimationCard(
                        animation = animation,
                        isLiked = animation.id in likedIds,
                        onClick = { onCardClick(animation) },
                        onToggleLike = { onToggleLike(animation) },
                        onReport = { onReport(animation) },
                        onBlock = { onBlock(animation) },
                        onAdminDelete = { onAdminDelete(animation) },
                        locked = isLocked(animation)
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AnimationCard(
    animation: AnimationStore.RemoteAnimation,
    isLiked: Boolean,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    onReport: () -> Unit = {},
    onBlock: () -> Unit = {},
    onAdminDelete: () -> Unit = {},
    locked: Boolean = false
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val isMine = AuthManager.currentUser?.uid == animation.creatorUid
    Box {
    Surface(
        modifier = Modifier
            .width(110.dp)
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (locked) {
                    // #39b 비밀번호 잠금 — 내용 숨김
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "잠김",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "비밀번호 필요",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // 실제 미리보기 — ruleJson을 파싱해 마블/드리프트 물리 시뮬 + Coil로 원격 이미지 로드
                    MarketMarblePreview(animation = animation)
                }
                // 비공개 표시 (#39)
                if (animation.isPrivate) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "비공개",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(3.dp).size(14.dp)
                        )
                    }
                }
                // 좌상단: 클릭 즉시 재생 (다운로드 → 임시 Rule로 OverlayService 시작) — 잠긴 항목은 숨김
                if (!locked) {
                    IconButton(
                        onClick = {
                            val appCtx = context.applicationContext
                            AppCoroutineScope.scope.launch {
                                Toast.makeText(appCtx, "준비 중…", Toast.LENGTH_SHORT).show()
                                val r = AnimationStore.download(appCtx, animation, countAsDownload = false)
                                val downloadedRule = r.getOrNull()
                                if (downloadedRule == null) {
                                    Toast.makeText(appCtx, "재생 실패: ${r.exceptionOrNull()?.message ?: "?"}", Toast.LENGTH_LONG).show()
                                } else {
                                    val intent = Intent(appCtx, OverlayService::class.java)
                                    intent.putExtra("ruleJson", downloadedRule.toJson().toString())
                                    appCtx.startService(intent)
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.TopStart).size(24.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_play_rounded),
                            contentDescription = "재생",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 우상단 다운로드 수 배지
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Text(
                        "↓ ${animation.downloadCount}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            // 제목 + 좋아요 (홈 카드의 토글 위치)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    animation.title.ifBlank { "(이름 없음)" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f).basicMarquee()
                )
                LikeButton(
                    liked = isLiked,
                    count = animation.likes,
                    onClick = onToggleLike
                )
            }
            Text(
                animation.creatorName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
        // 롱프레스 컨텍스트 메뉴 (#44 신고 / #46 차단 / #40 관리자 삭제)
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (!isMine) {
                DropdownMenuItem(
                    text = { Text("신고") },
                    onClick = { menuExpanded = false; onReport() }
                )
                DropdownMenuItem(
                    text = { Text("이 사용자 차단") },
                    onClick = { menuExpanded = false; onBlock() }
                )
            }
            if (Admin.isAdmin) {
                DropdownMenuItem(
                    text = { Text("관리자 삭제", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onAdminDelete() }
                )
            }
        }
    }
}

@Composable
private fun LikeButton(liked: Boolean, count: Long, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            if (liked) "♥" else "♡",
            style = MaterialTheme.typography.bodyMedium,
            color = if (liked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (count > 0) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AnimationDetailScreen(
    animation: AnimationStore.RemoteAnimation,
    onBack: () -> Unit,
    onImported: () -> Unit,
    onReport: (AnimationStore.RemoteAnimation) -> Unit = {},
    onBlock: (AnimationStore.RemoteAnimation) -> Unit = {},
    onAdminDelete: (AnimationStore.RemoteAnimation) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var resultIsError by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    val isMine = AuthManager.currentUser?.uid == animation.creatorUid

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("뒤로", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Spacer(Modifier.weight(1f))
            if (!isMine || Admin.isAdmin) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "더보기")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (!isMine) {
                            DropdownMenuItem(
                                text = { Text("신고") },
                                onClick = { menuExpanded = false; onReport(animation) }
                            )
                            DropdownMenuItem(
                                text = { Text("이 사용자 차단") },
                                onClick = { menuExpanded = false; onBlock(animation); onBack() }
                            )
                        }
                        if (Admin.isAdmin) {
                            DropdownMenuItem(
                                text = { Text("관리자 삭제", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuExpanded = false; onAdminDelete(animation); onBack() }
                            )
                        }
                    }
                }
            }
        }

        Text(
            animation.title.ifBlank { "(이름 없음)" },
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "by ${animation.creatorName} · 카테고리 ${animation.categoryName} · ↓ ${animation.downloadCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 미리보기 모드 토글: 애니메이션(물리 시뮬, 기본) ↔ 원본 미디어
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = !showOriginal,
                onClick = { showOriginal = false },
                label = { Text("애니메이션") }
            )
            FilterChip(
                selected = showOriginal,
                onClick = { showOriginal = true },
                label = { Text("원본 미디어") }
            )
        }

        // 큰 미리보기 (9:16). 기본은 실제 재생 모습(물리 시뮬), 토글 시 원본 미디어 풀 표시.
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (!showOriginal) {
                // 카드와 동일한 물리 시뮬레이션을 크게 — "어떻게 재생되는지" 그대로 보여줌
                MarketMarblePreview(animation = animation)
            } else {
                when (animation.mediaType) {
                    "video" -> FullVideoPreview(url = animation.mediaUrl, modifier = Modifier.fillMaxSize())
                    "lottie" -> RemoteLottiePreview(url = animation.mediaUrl, modifier = Modifier.fillMaxSize())
                    else -> coil.compose.AsyncImage(
                        model = animation.mediaUrl,
                        contentDescription = animation.title,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Button(
            onClick = {
                importing = true
                progress = 0f
                resultMsg = null
                scope.launch {
                    val result = AnimationStore.download(context, animation) { p -> progress = p }
                    importing = false
                    if (result.isSuccess) {
                        RuleStore.upsert(context, result.getOrNull()!!)
                        resultMsg = "내 규칙으로 추가됨 — 홈 탭에서 확인"
                        resultIsError = false
                        onImported()
                    } else {
                        resultMsg = "다운로드 실패: ${result.exceptionOrNull()?.message ?: "알 수 없음"}"
                        resultIsError = true
                    }
                }
            },
            enabled = !importing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (importing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("${(progress * 100).toInt()}%")
            } else {
                Text("내 규칙으로 추가")
            }
        }

        if (resultMsg != null) {
            Text(
                resultMsg!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (resultIsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * #46 — 차단한 사용자 관리 화면. 목록 + 차단 해제.
 */
@Composable
fun BlockedUsersScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ModerationStore.BlockedUser>?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    BackHandler { onBack() }
    LaunchedEffect(refreshTick) {
        items = ModerationStore.listBlockedUsers().getOrNull() ?: emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("뒤로", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("차단한 사용자", style = MaterialTheme.typography.headlineSmall)
        }

        when {
            items == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
            items!!.isEmpty() -> Text(
                "차단한 사용자가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items!!, key = { it.uid }) { blocked ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(blocked.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    blocked.uid,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    val r = ModerationStore.unblockUser(blocked.uid)
                                    if (r.isSuccess) {
                                        Toast.makeText(context, "차단 해제됨", Toast.LENGTH_SHORT).show()
                                        refreshTick++
                                    } else {
                                        Toast.makeText(context, "실패: ${r.exceptionOrNull()?.message ?: "?"}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) { Text("차단 해제") }
                        }
                    }
                }
            }
        }
    }
}

/**
 * #40 — 관리자 신고 검토 화면. 신고 목록 + 콘텐츠 삭제 / 신고 무시.
 */
@Composable
fun AdminReportsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ModerationStore.Report>?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    BackHandler { onBack() }
    LaunchedEffect(refreshTick) {
        items = ModerationStore.listReports().getOrNull() ?: emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("뒤로", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("신고 검토", style = MaterialTheme.typography.headlineSmall)
        }

        when {
            items == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
            items!!.isEmpty() -> Text(
                "접수된 신고가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items!!, key = { it.id }) { report ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                report.animationTitle.ifBlank { "(이름 없음)" },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "사유: ${report.category}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            if (report.note.isNotBlank()) {
                                Text(
                                    "상세: ${report.note}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "제작자: ${report.creatorName} (${report.creatorUid.take(8)}…)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                TextButton(
                                    enabled = !busy,
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            val r = AnimationStore.adminDeleteById(report.animationId)
                                            if (r.isSuccess) {
                                                ModerationStore.dismissReportsForAnimation(report.animationId)
                                                Toast.makeText(context, "콘텐츠 삭제 완료", Toast.LENGTH_SHORT).show()
                                                refreshTick++
                                            } else {
                                                Toast.makeText(context, "삭제 실패: ${r.exceptionOrNull()?.message ?: "?"}", Toast.LENGTH_SHORT).show()
                                            }
                                            busy = false
                                        }
                                    }
                                ) { Text("콘텐츠 삭제", color = MaterialTheme.colorScheme.error) }
                                TextButton(
                                    enabled = !busy,
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            val r = ModerationStore.dismissReport(report.id)
                                            if (r.isSuccess) {
                                                Toast.makeText(context, "신고 처리됨", Toast.LENGTH_SHORT).show()
                                                refreshTick++
                                            } else {
                                                Toast.makeText(context, "실패: ${r.exceptionOrNull()?.message ?: "?"}", Toast.LENGTH_SHORT).show()
                                            }
                                            busy = false
                                        }
                                    }
                                ) { Text("신고 무시") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 서브 화면 네비게이션: null=설정 본문, "blocked"=차단 관리, "reports"=신고 검토
    var subScreen by remember { mutableStateOf<String?>(null) }

    when (subScreen) {
        "blocked" -> { BlockedUsersScreen(modifier = modifier, onBack = { subScreen = null }); return }
        "reports" -> { AdminReportsScreen(modifier = modifier, onBack = { subScreen = null }); return }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("설정", style = MaterialTheme.typography.headlineSmall)

        PermissionBanner()

        HorizontalDivider()

        Text("계정", style = MaterialTheme.typography.titleMedium)
        AccountSection(
            onOpenBlocked = { subScreen = "blocked" },
            onOpenReports = { subScreen = "reports" }
        )

        // 로그인 시에만 노출되는 섹션들
        if (AuthManager.currentUser != null) {
            HorizontalDivider()
            Text("프로필 (마켓 표시 이름)", style = MaterialTheme.typography.titleMedium)
            NicknameSection()

            HorizontalDivider()
            Text("내가 공유한 애니메이션", style = MaterialTheme.typography.titleMedium)
            MySharedAnimationsSection()

            HorizontalDivider()
            Text("내 규칙 클라우드 동기화", style = MaterialTheme.typography.titleMedium)
            RuleSyncSection()
        }

        HorizontalDivider()

        // 테마 — 접기/펼치기 토글
        var themeExpanded by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { themeExpanded = !themeExpanded }
                .padding(vertical = 4.dp)
        ) {
            Text(
                "${if (themeExpanded) "▼" else "▶"} 테마",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                ThemeStore.current.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = themeExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 라이트/다크 모드 선택 (세그먼티드 3-옵션)
                Text("모드", style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = ThemeStore.mode == mode
                        OutlinedButton(
                            onClick = { ThemeStore.setMode(context, mode) },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.tertiaryContainer
                                                 else androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = if (selected) MaterialTheme.colorScheme.onTertiaryContainer
                                               else MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.outline
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(mode.displayName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("프리셋", style = MaterialTheme.typography.bodyMedium)
                AppTheme.entries.forEach { theme ->
                    ThemeOptionRow(
                        theme = theme,
                        selected = ThemeStore.current == theme,
                        onClick = { ThemeStore.set(context, theme) }
                    )
                }
            }
        }

        HorizontalDivider()

        Text("앱 정보", style = MaterialTheme.typography.titleMedium)
        Text(
            "Custom Animation Alert · 버전 1.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RuleSyncSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backingUp by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }  // done/total
    var lastBackupMs by remember { mutableStateOf<Long?>(null) }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var resultIsError by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lastBackupMs = RuleSync.lastBackupAtMs()
    }

    val localCount = remember { RuleStore.loadAll(context).size }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "현재 기기의 모든 규칙(미디어/사운드 파일 포함)을 클라우드에 백업하거나, 다른 기기에서 백업한 규칙을 가져옵니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (lastBackupMs != null) {
            val dateFmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
            Text(
                "마지막 백업: ${dateFmt.format(java.util.Date(lastBackupMs!!))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    backingUp = true
                    resultMsg = null
                    progress = 0 to localCount
                    scope.launch {
                        val r = RuleSync.backupAll(context) { d, t -> progress = d to t }
                        backingUp = false
                        if (r.isSuccess) {
                            resultMsg = "백업 완료 (${r.getOrNull()}개)"
                            resultIsError = false
                            lastBackupMs = RuleSync.lastBackupAtMs()
                        } else {
                            resultMsg = "백업 실패: ${r.exceptionOrNull()?.message ?: "?"}"
                            resultIsError = true
                        }
                    }
                },
                enabled = !backingUp && !restoring,
                modifier = Modifier.weight(1f)
            ) {
                if (backingUp) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("${progress.first}/${progress.second}")
                } else {
                    Text("백업 (${localCount}개)")
                }
            }
            OutlinedButton(
                onClick = { showRestoreConfirm = true },
                enabled = !backingUp && !restoring,
                modifier = Modifier.weight(1f)
            ) {
                if (restoring) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${progress.first}/${progress.second}")
                } else {
                    Text("복원")
                }
            }
        }
        if (resultMsg != null) {
            Text(
                resultMsg!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (resultIsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showRestoreConfirm) {
        Dialog(onDismissRequest = { if (!restoring) showRestoreConfirm = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("클라우드에서 복원", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "현재 기기의 모든 로컬 규칙을 클라우드의 백업으로 교체합니다. 로컬에만 있고 백업에 없는 규칙은 사라집니다. 계속할까요?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showRestoreConfirm = false }, enabled = !restoring) {
                            Text("취소")
                        }
                        TextButton(
                            onClick = {
                                restoring = true
                                resultMsg = null
                                showRestoreConfirm = false
                                scope.launch {
                                    val r = RuleSync.restoreAll(context) { d, t -> progress = d to t }
                                    restoring = false
                                    if (r.isSuccess) {
                                        resultMsg = "복원 완료 (${r.getOrNull()}개) — 앱을 다시 시작하면 적용됨"
                                        resultIsError = false
                                    } else {
                                        resultMsg = "복원 실패: ${r.exceptionOrNull()?.message ?: "?"}"
                                        resultIsError = true
                                    }
                                }
                            },
                            enabled = !restoring
                        ) {
                            Text("복원", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NicknameSection() {
    val scope = rememberCoroutineScope()
    val current = ProfileStore.currentNickname
    var input by remember(current) { mutableStateOf(current ?: "") }
    var checking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<ProfileStore.CheckResult?>(null) }

    // 입력 변경 시 디바운스 + 자동 중복 체크
    LaunchedEffect(input) {
        if (input.isBlank() || input.trim() == current) {
            checkResult = null
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(500)  // 500ms 디바운스
        checking = true
        checkResult = ProfileStore.checkAvailability(input)
        checking = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (current != null) {
            Text(
                "현재: $current",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "닉네임 미설정 — 공유 시 계정 이름으로 표시됩니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; status = null },
            label = { Text("닉네임") },
            placeholder = { Text("2~16자, 한/영/숫자/_-") },
            singleLine = true,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        )

        val hint: Pair<String, androidx.compose.ui.graphics.Color>? = when {
            checking -> "확인 중..." to MaterialTheme.colorScheme.onSurfaceVariant
            checkResult is ProfileStore.CheckResult.Available -> "사용 가능" to MaterialTheme.colorScheme.tertiary
            checkResult is ProfileStore.CheckResult.Taken -> "이미 사용 중" to MaterialTheme.colorScheme.error
            checkResult is ProfileStore.CheckResult.TooShort -> "너무 짧음 (최소 2자)" to MaterialTheme.colorScheme.error
            checkResult is ProfileStore.CheckResult.TooLong -> "너무 김 (최대 16자)" to MaterialTheme.colorScheme.error
            checkResult is ProfileStore.CheckResult.InvalidChars -> "한/영/숫자/_- 만 허용" to MaterialTheme.colorScheme.error
            checkResult is ProfileStore.CheckResult.Same -> "현재 닉네임과 동일" to MaterialTheme.colorScheme.onSurfaceVariant
            else -> null
        }
        if (hint != null) {
            Text(hint.first, style = MaterialTheme.typography.bodySmall, color = hint.second)
        }

        Button(
            onClick = {
                saving = true
                scope.launch {
                    val r = ProfileStore.setNickname(input)
                    saving = false
                    if (r.isSuccess) {
                        status = "저장됨"; statusIsError = false
                    } else {
                        status = "저장 실패: ${r.exceptionOrNull()?.message ?: "알 수 없음"}"
                        statusIsError = true
                    }
                }
            },
            enabled = !saving && !checking
                    && checkResult is ProfileStore.CheckResult.Available,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("닉네임 저장")
            }
        }
        if (status != null) {
            Text(
                status!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusIsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MySharedAnimationsSection() {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<AnimationStore.RemoteAnimation>?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf<AnimationStore.RemoteAnimation?>(null) }
    var bulkDeleteOpen by remember { mutableStateOf(false) }
    var bulkProgress by remember { mutableStateOf(0 to 0) }  // (done, total)

    LaunchedEffect(refreshTick) {
        items = AnimationStore.listMine().getOrNull() ?: emptyList()
    }

    when {
        items == null -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
        items!!.isEmpty() -> Text(
            "아직 공유한 애니메이션이 없습니다",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items!!.forEach { animation ->
                MyShareItemRow(
                    animation = animation,
                    onDelete = { deleting = animation }
                )
            }
            // 전체 삭제 버튼 — 테스트 단계 청소용
            OutlinedButton(
                onClick = { bulkDeleteOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "내 공유 전체 삭제 (${items!!.size}개)",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // 전체 삭제 확인 다이얼로그
    if (bulkDeleteOpen) {
        val total = items?.size ?: 0
        var working by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { if (!working) bulkDeleteOpen = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("전체 삭제", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (working) "삭제 중… (${bulkProgress.first}/${bulkProgress.second})"
                        else "내가 올린 $total 개 애니메이션을 클라우드에서 모두 삭제합니다. 되돌릴 수 없음.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { bulkDeleteOpen = false }, enabled = !working) {
                            Text("취소")
                        }
                        TextButton(
                            onClick = {
                                working = true
                                scope.launch {
                                    val snapshot = items.orEmpty().toList()
                                    bulkProgress = 0 to snapshot.size
                                    snapshot.forEachIndexed { i, a ->
                                        AnimationStore.delete(a)
                                        bulkProgress = (i + 1) to snapshot.size
                                    }
                                    working = false
                                    bulkDeleteOpen = false
                                    refreshTick++
                                }
                            },
                            enabled = !working
                        ) {
                            if (working) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("모두 삭제", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // 삭제 확인 다이얼로그
    val target = deleting
    if (target != null) {
        var working by remember(target.id) { mutableStateOf(false) }
        Dialog(onDismissRequest = { if (!working) deleting = null }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("공유 삭제", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "\"${target.title}\" 을(를) 클라우드에서 영구 삭제합니다. 마켓에서도 사라지고, 받아간 다른 사용자에겐 영향 없음. 계속할까요?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { deleting = null }, enabled = !working) { Text("취소") }
                        TextButton(
                            onClick = {
                                working = true
                                scope.launch {
                                    val r = AnimationStore.delete(target)
                                    working = false
                                    deleting = null
                                    refreshTick++  // 목록 새로고침
                                }
                            },
                            enabled = !working
                        ) {
                            if (working) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("삭제", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyShareItemRow(
    animation: AnimationStore.RemoteAnimation,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 썸네일 (40dp)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                coil.compose.AsyncImage(
                    model = animation.mediaUrl,
                    contentDescription = animation.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    animation.title.ifBlank { "(이름 없음)" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    "↓ ${animation.downloadCount} · 카테고리 ${animation.categoryName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}


@Composable
private fun AccountSection(
    onOpenBlocked: () -> Unit = {},
    onOpenReports: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val webClientId = stringResource(R.string.default_web_client_id)
    val scope = rememberCoroutineScope()
    val user = AuthManager.currentUser
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    if (user != null) {
        // 로그인 상태: 프로필 사진 + 이름 + 로그아웃
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                val photo = user.photoUrl
                if (photo != null) {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                // 비동기 로드는 추후 — 일단 OkHttp나 Coil 없이 단순 setImageURI 시도
                                try { setImageURI(photo) } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Text((user.displayName ?: "?").take(1)) }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.displayName ?: "(이름 없음)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        user.email ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { AuthManager.signOut() }) { Text("로그아웃") }
            }
        }

        // 차단한 사용자 관리 (#46)
        Surface(
            onClick = onOpenBlocked,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text("차단한 사용자 관리", modifier = Modifier.weight(1f))
                Text("›", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 관리자 — 신고 검토 (#40)
        if (Admin.isAdmin) {
            Surface(
                onClick = onOpenReports,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text("🛡 신고 검토 (관리자)", modifier = Modifier.weight(1f))
                    Text("›", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        // 미로그인 상태: 로그인 버튼
        Text(
            "로그인하면 만든 애니메이션을 클라우드에 공유하거나 다른 사용자가 올린 걸 받아 쓸 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = {
                if (activity == null) {
                    errorMsg = "Activity context를 가져올 수 없습니다"
                    return@Button
                }
                loading = true
                errorMsg = null
                scope.launch {
                    val result = AuthManager.signInWithGoogle(activity, webClientId)
                    loading = false
                    if (result.isFailure) {
                        val e = result.exceptionOrNull()
                        // 사용자가 취소한 케이스는 에러로 표시 안 함
                        if (e !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                            errorMsg = "로그인 실패: ${e?.message ?: "알 수 없는 오류"}"
                        }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Google 계정으로 로그인")
            }
        }
        if (errorMsg != null) {
            Text(
                errorMsg!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                theme.displayName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                theme.previewSwatches().forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun RuleListScreen(
    rules: List<Rule>,
    modifier: Modifier = Modifier,
    onAdd: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onChanged: () -> Unit = {}
) {
    // groupBy는 LinkedHashMap을 반환 → rules 추가 순서대로 그룹 유지. "기본"만 강제로 맨 앞.
    val rawGrouped = rules.groupBy { it.groupName.ifBlank { "기본" } }
    val grouped = buildMap {
        rawGrouped["기본"]?.let { put("기본", it) }
        rawGrouped.forEach { (k, v) -> if (k != "기본") put(k, v) }
    }
    var expandedGroups by remember { mutableStateOf(grouped.keys.toSet()) }
    var renamingGroup by remember { mutableStateOf<String?>(null) }
    var showAddGroupDialog by remember { mutableStateOf(false) }

    // 진입 시 헤더(타이틀+버튼)는 살짝 스크롤된 상태로 시작 → 첫 그룹부터 보이게
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 1)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
    ) {
        item("header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    "Custom Animation Alert",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                CircleIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "새 그룹",
                    onClick = { showAddGroupDialog = true },
                    filled = true,
                    size = 44.dp
                )
            }
        }
        item("banner") {
            Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                PermissionBanner()
            }
        }
        if (rules.isEmpty()) {
            item("empty") {
                Text(
                    "아직 등록된 규칙이 없습니다.\n우측 상단 + 버튼을 눌러 시작하세요.",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        } else {
            grouped.forEach { (groupName, groupRules) ->
                    item(key = "header_$groupName") {
                        GroupHeader(
                            name = groupName,
                            ruleCount = groupRules.size,
                            isExpanded = groupName in expandedGroups,
                            onToggleExpand = {
                                expandedGroups = if (groupName in expandedGroups)
                                    expandedGroups - groupName
                                else expandedGroups + groupName
                            },
                            onAddRule = { onAdd(groupName) },
                            onRename = { renamingGroup = groupName }
                        )
                    }
                    if (groupName in expandedGroups) {
                        item(key = "row_$groupName") {
                            LazyRow(
                                // 초기 80px 우측 스크롤 → 3.n 카드 peek 효과
                                state = rememberLazyListState(initialFirstVisibleItemScrollOffset = 40),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                            ) {
                                items(groupRules, key = { it.id }) { rule ->
                                    RuleCard(
                                        rule = rule,
                                        onClick = { onEdit(rule.id) },
                                        onDelete = { onDelete(rule.id) },
                                        onToggleEnabled = { onToggleEnabled(rule.id, it) },
                                        onChanged = onChanged
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 자체 프로모 배너 (Custom Animation 시리즈 예정 홍보)
            // AdMob 배너는 PromoBanner.kt에서 대체 — 추후 다시 활성화 시 AdBanner로 교체
            item("promo_banner") {
                Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    PromoBanner(modifier = Modifier.fillMaxWidth())
                }
            }
        }

    renamingGroup?.let { oldName ->
        RenameGroupDialog(
            currentName = oldName,
            onDismiss = { renamingGroup = null },
            onConfirm = { newName ->
                onRenameGroup(oldName, newName)
                renamingGroup = null
            }
        )
    }

    if (showAddGroupDialog) {
        AddGroupDialog(
            existingGroupNames = grouped.keys,
            onDismiss = { showAddGroupDialog = false },
            onConfirm = { name ->
                onAdd(name)
                showAddGroupDialog = false
            }
        )
    }
}

@Composable
fun AddGroupDialog(
    existingGroupNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val isDuplicate = trimmed in existingGroupNames
    val canConfirm = trimmed.isNotBlank() && !isDuplicate

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("새 그룹 추가", style = MaterialTheme.typography.titleLarge)
                Text(
                    "그룹 이름을 입력하면 그 그룹에 첫 규칙이 만들어집니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("그룹 이름") },
                    isError = isDuplicate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (isDuplicate) {
                    Text(
                        "같은 이름의 그룹이 이미 있습니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    TextButton(
                        onClick = { onConfirm(trimmed) },
                        enabled = canConfirm
                    ) { Text("추가") }
                }
            }
        }
    }
}

@Composable
fun GroupHeader(
    name: String,
    ruleCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddRule: () -> Unit,
    onRename: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                if (isExpanded) "▼" else "▶",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable(onClick = onToggleExpand).padding(end = 8.dp)
            )
            Text(
                text = "$name  ($ruleCount)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleExpand)
            )
            CircleIconButton(
                icon = Icons.Filled.Edit,
                contentDescription = "이름 변경",
                onClick = onRename
            )
            Spacer(Modifier.width(6.dp))
            CircleIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "규칙 추가",
                onClick = onAddRule
            )
        }
    }
}

@Composable
fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

@Composable
fun RenameGroupDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("그룹 이름 변경", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("새 그룹 이름") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    TextButton(onClick = { onConfirm(name) }) { Text("변경") }
                }
            }
        }
    }
}

private val ToggleOn = Color(0xFF7C8F6D)   // 세이지 그린 — 토글 "켜짐" 점등 (액센트와 구분, 테마 무관)

// 미디어/앱 모두 미설정 시 폴백으로 띄울 이모지 풀
val FALLBACK_EMOJIS = listOf(
    "❤️", "💕", "💖", "💘",
    "⭐", "✨", "🌟", "💫",
    "⚡", "🔥", "💥", "🎉",
    "🔔", "🚨", "📢", "📣",
    "👀", "👋", "🙌", "👏",
    "🐱", "🐶", "🐰", "🦊",
    "🍀", "🌸", "🌈", "☀️",
    "🎵", "🎶", "🎯", "💡"
)

/** rule.id 기반 결정적 이모지 — 같은 규칙은 항상 같은 이모지 (미리보기와 실제 재생 일치) */
fun fallbackEmojiFor(ruleId: String): String =
    FALLBACK_EMOJIS[abs(ruleId.hashCode()) % FALLBACK_EMOJIS.size]

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RuleCard(
    rule: Rule,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onChanged: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            // 9:16 휴대폰 화면 미리보기 — 마블 물리 + 좌상단 ▶ 재생 + 우상단 ⋯ 메뉴(삭제)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                MarblePreview(rule = rule)

                // 좌상단: 클릭 즉시 재생
                IconButton(
                    onClick = {
                        val intent = Intent(context, OverlayService::class.java)
                        intent.putExtra("ruleId", rule.id)
                        if (rule.mediaUri == null && rule.packageName != null) {
                            intent.putExtra("sourcePackage", rule.packageName)
                        }
                        context.startService(intent)
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(24.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_play_rounded),
                        contentDescription = "재생",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 우상단: ⋯ 토글 → 아래로 공유/삭제 Lottie 아이콘 슬라이드
                Column(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { menuExpanded = !menuExpanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "메뉴",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(
                        visible = menuExpanded,
                        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // 공유 — 로그인 + 미디어 있을 때만 표시. Material Share 아이콘 (Lottie는 도트 크기/색이 들쑥날쑥해서 교체)
                            if (AuthManager.currentUser != null && rule.mediaUri != null) {
                                IconButton(
                                    onClick = {
                                        menuExpanded = false
                                        showShareDialog = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = "공유",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            // 복사 — 이 규칙을 클립보드에 담음. 체크 애니메이션이 보이도록 메뉴는 닫지 않음.
                            CopyLottieButton("복사") {
                                RuleClipboard.copied = rule
                                Toast.makeText(context, "규칙 복사됨 — 다른 카드에서 붙여넣기 가능", Toast.LENGTH_SHORT).show()
                            }
                            // 붙여넣기 — 클립보드에 뭔가 있을 때만 표시. 이 카드 위로 덮어쓰기(새 카드 X).
                            if (RuleClipboard.copied != null) {
                                PasteLottieButton("붙여넣기") {
                                    menuExpanded = false
                                    val source = RuleClipboard.copied ?: return@PasteLottieButton
                                    // 이름: 원본 이름에서 기존 _숫자 접미사를 떼고 A_1, A_2, A_3… 오름차순으로
                                    val base = source.name.replace(Regex("_\\d+$"), "").ifBlank { "규칙" }
                                    val existing = RuleStore.loadAll(context).map { it.name }.toSet()
                                    var n = 1
                                    while ("${base}_$n" in existing) n++
                                    // 현재 카드(rule.id) 위로 원본 내용을 덮어쓴다
                                    val pasted = source.copy(
                                        id = rule.id,
                                        name = "${base}_$n",
                                        // 코인 룰이면 lastPolledPrice는 새로 시작 (안 그러면 첫 트리거 누락 가능성)
                                        lastPolledPrice = null
                                    )
                                    RuleStore.upsert(context, pasted)
                                    onChanged()
                                    Toast.makeText(context, "붙여넣기 완료 — ${base}_$n", Toast.LENGTH_SHORT).show()
                                }
                            }
                            // 삭제 — 항상 표시, 흰색 트래시 → 테마 onSurface로 틴트
                            LottieIconButton(
                                resName = "delete",
                                contentDescription = "삭제",
                                tintToOnSurface = true,
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            // 앱아이콘 + 키워드 + 점등 토글 (미설정 항목은 빈칸)
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 앱 아이콘 (없으면 빈 공간)
                Box(modifier = Modifier.size(18.dp)) {
                    if (rule.packageName != null) {
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    try {
                                        setImageDrawable(ctx.packageManager.getApplicationIcon(rule.packageName))
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 키워드 (여러 개면 쉼표 구분, 없거나 OFF면 빈 텍스트)
                Text(
                    if (rule.keywordEnabled) rule.keywords.joinToString(", ") else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee()
                )

                ToggleDot(enabled = rule.enabled, onToggle = onToggleEnabled)
            }

            // 규칙 이름 — 너무 길면 가로 마키 스크롤
            Text(
                rule.name.ifBlank { rule.appLabel ?: "(이름 없음)" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .basicMarquee()
            )
        }
    }

    if (showShareDialog) {
        ShareDialog(
            rule = rule,
            onDismiss = { showShareDialog = false },
            onConfirm = { title, includeApp, isPrivate, password ->
                showShareDialog = false
                val appCtx = context.applicationContext
                AppCoroutineScope.scope.launch {
                    Toast.makeText(appCtx, "업로드 중…", Toast.LENGTH_SHORT).show()
                    val r = AnimationStore.upload(
                        appCtx, rule,
                        customTitle = title,
                        includeApp = includeApp,
                        isPrivate = isPrivate,
                        password = password
                    )
                    val msg = if (r.isSuccess) "공유 완료" else "공유 실패: ${r.exceptionOrNull()?.message ?: "알 수 없음"}"
                    Toast.makeText(appCtx, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

@Composable
fun ShareDialog(
    rule: Rule,
    onDismiss: () -> Unit,
    onConfirm: (title: String, includeApp: Boolean, isPrivate: Boolean, password: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var includeApp by remember { mutableStateOf(false) }
    var isPrivate by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val defaultTitle = rule.name.ifBlank { rule.appLabel ?: "이름 없음" }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("클라우드 공유", style = MaterialTheme.typography.titleMedium)
                Text(
                    "애니메이션 설정 + 인터랙션 + 사운드 + 미디어가 함께 업로드됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        if (title.isBlank()) {
                            Text(
                                "제목을 입력해주세요",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    isError = title.isBlank()
                )

                if (rule.packageName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("앱 정보 포함 (${rule.appLabel ?: rule.packageName})")
                            Text(
                                "체크 시 받는 사용자도 같은 앱이 매칭됩니다",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = includeApp, onCheckedChange = { includeApp = it })
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("비공개 공유")
                        Text(
                            "마켓 목록에 노출되지 않고, 제목을 정확히 검색해야만 찾을 수 있습니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }

                // 비공개일 때만 비밀번호 옵션 노출
                if (isPrivate) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("비밀번호 (선택)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword)
                            androidx.compose.ui.text.input.VisualTransformation.None
                        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                        ),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "숨김" else "표시",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        supportingText = {
                            Text(
                                "설정 시 받는 사람이 비밀번호를 입력해야 미리보기/다운로드할 수 있습니다",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    TextButton(
                        onClick = {
                            val pw = password.trim().takeIf { isPrivate && it.isNotBlank() }
                            onConfirm(title.trim(), includeApp, isPrivate, pw)
                        },
                        enabled = title.isNotBlank()
                    ) { Text("공유") }
                }
            }
        }
    }
}

/**
 * #44 신고 다이얼로그 — 카테고리 선택(필수) + 상세 설명(선택).
 */
@Composable
fun ReportDialog(
    animation: AnimationStore.RemoteAnimation,
    onDismiss: () -> Unit,
    onSubmit: (category: String, note: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("신고", style = MaterialTheme.typography.titleMedium)
                Text(
                    "'${animation.title.ifBlank { "(이름 없음)" }}' 콘텐츠를 신고합니다. 사유를 선택해주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ModerationStore.REPORT_CATEGORIES.forEach { category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedCategory = category }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(category, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("상세 설명 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    TextButton(
                        onClick = { selectedCategory?.let { onSubmit(it, note.trim()) } },
                        enabled = selectedCategory != null
                    ) { Text("신고하기", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

/**
 * 각도기 UI — 사용자가 원 위에서 드래그/탭으로 방향(0~360°)을 설정.
 * 0°=오른쪽, 90°=아래, 180°=왼쪽, 270°=위 (화면 좌표계).
 * 좌측에 + - 각도 텍스트, 우측에 8방향 빠른 설정 버튼.
 */
@Composable
fun AngleDialPicker(angle: Float, onAngleChange: (Float) -> Unit) {
    val dialColor = MaterialTheme.colorScheme.outline
    val pointerColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val v = change.position - center
                            val rad = atan2(v.y, v.x)
                            val deg = ((rad * 180f / PI.toFloat()) + 360f) % 360f
                            onAngleChange(deg)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val v = pos - center
                            val rad = atan2(v.y, v.x)
                            val deg = ((rad * 180f / PI.toFloat()) + 360f) % 360f
                            onAngleChange(deg)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = (kotlin.math.min(size.width, size.height) / 2f) - 6f
                    drawCircle(color = dialColor, radius = r, center = Offset(cx, cy), style = Stroke(width = 2f))
                    // 8방향 눈금
                    for (i in 0 until 8) {
                        val a = (i * 45f) * PI.toFloat() / 180f
                        val tickLen = if (i % 2 == 0) 12f else 6f
                        val sx = cx + (r - tickLen) * cos(a)
                        val sy = cy + (r - tickLen) * sin(a)
                        val ex = cx + r * cos(a)
                        val ey = cy + r * sin(a)
                        drawLine(
                            color = dialColor,
                            start = Offset(sx, sy),
                            end = Offset(ex, ey),
                            strokeWidth = 1.5f
                        )
                    }
                    // 방향 표시 화살표
                    val rad = angle * PI.toFloat() / 180f
                    val px = cx + r * cos(rad)
                    val py = cy + r * sin(rad)
                    drawLine(
                        color = pointerColor,
                        start = Offset(cx, cy),
                        end = Offset(px, py),
                        strokeWidth = 4f
                    )
                    drawCircle(color = pointerColor, radius = 8f, center = Offset(px, py))
                    drawCircle(color = onSurfaceColor, radius = 3f, center = Offset(cx, cy))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text("${angle.roundToInt()}°", style = MaterialTheme.typography.headlineSmall)
                Text(
                    when {
                        angle < 22.5f || angle >= 337.5f -> "→ 오른쪽"
                        angle < 67.5f -> "↘ 우하"
                        angle < 112.5f -> "↓ 아래"
                        angle < 157.5f -> "↙ 좌하"
                        angle < 202.5f -> "← 왼쪽"
                        angle < 247.5f -> "↖ 좌상"
                        angle < 292.5f -> "↑ 위"
                        else -> "↗ 우상"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = { onAngleChange(((angle - 15f) + 360f) % 360f) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("-15°", style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(
                        onClick = { onAngleChange((angle + 15f) % 360f) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("+15°", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        // 4방향 빠른 설정
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            val presets = listOf("←" to 180f, "↑" to 270f, "→" to 0f, "↓" to 90f)
            presets.forEach { (label, deg) ->
                OutlinedButton(
                    onClick = { onAngleChange(deg) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) { Text(label) }
            }
        }
    }
}

@Composable
fun ToggleDot(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val resId = remember { context.resources.getIdentifier("checkmark", "raw", context.packageName) }

    if (resId == 0) {
        // Lottie 파일 없음 → 단순 점등 토글로 폴백
        val borderColor = if (enabled) ToggleOn else MaterialTheme.colorScheme.outline
        val fillColor = if (enabled) ToggleOn else Color.Transparent
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(1.5.dp, borderColor, CircleShape)
                .background(fillColor)
                .clickable { onToggle(!enabled) }
        )
        return
    }

    val composition by com.airbnb.lottie.compose.rememberLottieComposition(
        com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(resId)
    )
    val animatable = com.airbnb.lottie.compose.rememberLottieAnimatable()
    var playOnce by remember { mutableStateOf(false) }

    // playOnce가 true이고 enabled일 때만 1회 재생, 그 외 enabled면 마지막 프레임 고정
    LaunchedEffect(playOnce, composition, enabled) {
        val comp = composition ?: return@LaunchedEffect
        when {
            playOnce && enabled -> {
                animatable.animate(comp, iterations = 1, speed = 2.5f, initialProgress = 0f)
                playOnce = false
            }
            enabled -> animatable.snapTo(comp, progress = 1f)
            else -> animatable.snapTo(comp, progress = 0f)
        }
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)  // 클릭 ripple도 원형으로 클리핑
            .clickable {
                if (!enabled) playOnce = true  // OFF→ON 전환 시에만 애니메이션
                onToggle(!enabled)
            },
        contentAlignment = Alignment.Center
    ) {
        if (enabled && composition != null) {
            com.airbnb.lottie.compose.LottieAnimation(
                composition = composition,
                progress = { animatable.progress },
                modifier = Modifier.size(28.dp)
            )
        } else {
            // OFF 상태 — 빈 외곽선 원 (앱 아이콘과 동일 크기)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
    }
}

/**
 * 시각 선택기 — 시간(0-23) + 분(00/15/30/45) 두 드롭다운. minutesOfDay = hour*60 + minute.
 */
@Composable
fun TimeOfDayPicker(
    label: String,
    minutesOfDay: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hour = minutesOfDay / 60
    val minute = minutesOfDay % 60
    var hourMenu by remember { mutableStateOf(false) }
    var minMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                OutlinedButton(
                    onClick = { hourMenu = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("%02d".format(hour)) }
                DropdownMenu(expanded = hourMenu, onDismissRequest = { hourMenu = false }) {
                    (0..23).forEach { h ->
                        DropdownMenuItem(
                            text = { Text("%02d시".format(h)) },
                            onClick = {
                                hourMenu = false
                                onChange(h * 60 + minute)
                            }
                        )
                    }
                }
            }
            Text(":", style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedButton(
                    onClick = { minMenu = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("%02d".format(minute)) }
                DropdownMenu(expanded = minMenu, onDismissRequest = { minMenu = false }) {
                    listOf(0, 15, 30, 45).forEach { m ->
                        DropdownMenuItem(
                            text = { Text("%02d분".format(m)) },
                            onClick = {
                                minMenu = false
                                onChange(hour * 60 + m)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 카드 메뉴용 — 24dp 동그라미 안에 1글자 텍스트 (복/붙). 아이콘 부족한 경우 대체용. */
@Composable
fun TextChipButton(label: String, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun LottieIconButton(
    resName: String,
    contentDescription: String,
    onClick: () -> Unit,
    tintToOnSurface: Boolean = true,
    size: Dp = 24.dp
) {
    val context = LocalContext.current
    val resId = remember(resName) { context.resources.getIdentifier(resName, "raw", context.packageName) }
    if (resId == 0) {
        // 리소스 없으면 폴백: 빈 박스
        Box(modifier = Modifier.size(size).clickable(onClick = onClick))
        return
    }

    val composition by com.airbnb.lottie.compose.rememberLottieComposition(
        com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(resId)
    )
    val animatable = com.airbnb.lottie.compose.rememberLottieAnimatable()
    val scope = rememberCoroutineScope()
    val tintColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()

    val dynamicProperties = if (tintToOnSurface) {
        com.airbnb.lottie.compose.rememberLottieDynamicProperties(
            com.airbnb.lottie.compose.rememberLottieDynamicProperty(
                property = com.airbnb.lottie.LottieProperty.COLOR,
                value = tintColorArgb,
                keyPath = arrayOf("**")
            )
        )
    } else null

    // composition 로드 직후 1회 자동 재생 (메뉴 펼쳐질 때)
    LaunchedEffect(composition) {
        composition?.let { animatable.animate(it, iterations = 1, speed = 1.5f) }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable {
                scope.launch {
                    composition?.let { animatable.animate(it, iterations = 1, speed = 1.5f) }
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        com.airbnb.lottie.compose.LottieAnimation(
            composition = composition,
            progress = { animatable.progress },
            dynamicProperties = dynamicProperties,
            modifier = Modifier.size(size)
        )
    }
}

/**
 * 복제(복사/붙여넣기) Lottie 버튼. raw/paste.json = 카드 두 장이 겹쳤다 떨어지는 "복제" 모션.
 *  - 선(STROKE_COLOR) → onSurface : 테마 라인색 (다크/라이트 자동 대응)
 *  - 면(COLOR) → surfaceVariant : 카드 미리보기 박스 배경색과 동일.
 *      └ 배경 위에선 같은 색이라 "투명"하게 보이고, 두 카드가 겹치는 구간에선
 *        전면 카드의 면이 후면 카드의 선을 가린다(종이 두 장 포갠 효과). 흰 박스 X.
 *  - mirror = true 면 좌우 대칭(복사 ↔ 붙여넣기 시각 구분)
 */
@Composable
fun DuplicateLottieButton(
    contentDescription: String,
    mirror: Boolean = false,
    size: Dp = 24.dp,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val resId = remember { context.resources.getIdentifier("paste", "raw", context.packageName) }
    if (resId == 0) {
        // 폴백: 리소스 없으면 기존 텍스트 칩
        TextChipButton(if (mirror) "복" else "붙", contentDescription, onClick)
        return
    }

    val composition by com.airbnb.lottie.compose.rememberLottieComposition(
        com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(resId)
    )
    val animatable = com.airbnb.lottie.compose.rememberLottieAnimatable()
    val scope = rememberCoroutineScope()
    val strokeArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    // 면은 박스 배경(surfaceVariant)과 같은 색으로 채워 "투명"하게 보이게 하되,
    // 겹치는 구간에선 전면이 후면 선을 덮어 가림 효과를 낸다.
    val fillArgb = MaterialTheme.colorScheme.surfaceVariant.toArgb()

    val dynamicProperties = com.airbnb.lottie.compose.rememberLottieDynamicProperties(
        com.airbnb.lottie.compose.rememberLottieDynamicProperty(
            property = com.airbnb.lottie.LottieProperty.STROKE_COLOR,
            value = strokeArgb,
            keyPath = arrayOf("**")
        ),
        com.airbnb.lottie.compose.rememberLottieDynamicProperty(
            property = com.airbnb.lottie.LottieProperty.COLOR,
            value = fillArgb,
            keyPath = arrayOf("**")
        )
    )

    // 메뉴 펼쳐질 때 1회 자동 재생
    LaunchedEffect(composition) {
        composition?.let { animatable.animate(it, iterations = 1, speed = 1.2f) }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable {
                scope.launch {
                    composition?.let { animatable.animate(it, iterations = 1, speed = 1.2f) }
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        com.airbnb.lottie.compose.LottieAnimation(
            composition = composition,
            progress = { animatable.progress },
            dynamicProperties = dynamicProperties,
            modifier = Modifier
                .size(size)
                .then(if (mirror) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier)
        )
    }
}

/** 붙여넣기 = 복제 아이콘 (그대로). */
@Composable
fun PasteLottieButton(contentDescription: String, size: Dp = 24.dp, onClick: () -> Unit) =
    DuplicateLottieButton(contentDescription, mirror = false, size = size, onClick = onClick)

/** 복사 = 붙여넣기 아이콘의 좌우 대칭. */
@Composable
fun CopyLottieButton(contentDescription: String, size: Dp = 24.dp, onClick: () -> Unit) =
    DuplicateLottieButton(contentDescription, mirror = true, size = size, onClick = onClick)

@Composable
fun MediaContent(rule: Rule, sizeDp: Dp, modifier: Modifier = Modifier) {
    // 원형 크롭 적용 — Compose 측 modifier에 clip(CircleShape) 추가
    val maybeCircle = if (rule.mediaCircleCrop) Modifier.clip(CircleShape) else Modifier
    when {
        rule.mediaUri != null && rule.mediaType == "image" -> {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = if (rule.mediaCircleCrop) ImageView.ScaleType.CENTER_CROP
                                    else ImageView.ScaleType.FIT_CENTER
                        try {
                            val uri = Uri.parse(rule.mediaUri)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val src = ImageDecoder.createSource(ctx.contentResolver, uri)
                                val drawable = ImageDecoder.decodeDrawable(src)
                                setImageDrawable(drawable)
                                if (drawable is AnimatedImageDrawable) drawable.start()
                            } else setImageURI(uri)
                        } catch (_: Exception) {}
                    }
                },
                modifier = modifier.size(sizeDp).then(maybeCircle)
            )
        }
        rule.mediaUri != null && rule.mediaType == "video" -> {
            VideoThumbnail(uri = Uri.parse(rule.mediaUri), modifier = modifier.size(sizeDp).then(maybeCircle))
        }
        rule.mediaUri != null && rule.mediaType == "lottie" -> {
            LottieMediaFromUri(uri = rule.mediaUri, modifier = modifier.size(sizeDp).then(maybeCircle))
        }
        rule.packageName != null -> {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        try {
                            setImageDrawable(ctx.packageManager.getApplicationIcon(rule.packageName))
                        } catch (_: Exception) {}
                    }
                },
                modifier = modifier
                    .size(sizeDp)
                    .clip(CircleShape)
            )
        }
        else -> {
            // 미디어/앱 모두 미설정 → 랜덤 이모지 폴백 (rule.id 기반 결정적)
            Box(modifier = modifier.size(sizeDp), contentAlignment = Alignment.Center) {
                Text(
                    fallbackEmojiFor(rule.id),
                    fontSize = (sizeDp.value * 0.7f).sp
                )
            }
        }
    }
}

/**
 * 사운드 트림 편집기 — 파형 위에 RangeSlider 두 핸들. 미리듣기 버튼 포함.
 * waveform=null이면 평평한 막대로 표시 (분석 중 또는 추출 실패).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SoundTrimEditor(rule: Rule, waveform: FloatArray?, onChange: (start: Int, end: Int) -> Unit) {
    val context = LocalContext.current
    val total = rule.soundDurationMs.coerceAtLeast(1)
    val effectiveEnd = if (rule.soundEndMs in 1..total) rule.soundEndMs else total
    var range by remember(rule.id, rule.soundUri, total) {
        mutableStateOf(rule.soundStartMs.toFloat()..effectiveEnd.toFloat())
    }
    var previewing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val waveColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.outline

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("사운드 컷 편집", style = MaterialTheme.typography.bodyMedium)
        // 파형 캔버스 — 슬라이더 위 배경
        Box(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bins = waveform?.size ?: 64
                val barW = size.width / bins
                val startFrac = range.start / total
                val endFrac = range.endInclusive / total
                for (i in 0 until bins) {
                    val v = waveform?.getOrNull(i) ?: 0.3f
                    val barH = (v * size.height * 0.9f).coerceAtLeast(2f)
                    val frac = (i + 0.5f) / bins
                    val color = if (frac in startFrac..endFrac) waveColor else mutedColor
                    drawRect(
                        color = color,
                        topLeft = Offset(i * barW, (size.height - barH) / 2f),
                        size = androidx.compose.ui.geometry.Size(barW * 0.7f, barH)
                    )
                }
            }
        }
        androidx.compose.material3.RangeSlider(
            value = range,
            onValueChange = {
                range = it
                onChange(it.start.toInt(), it.endInclusive.toInt())
            },
            valueRange = 0f..total.toFloat()
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "${msToTime(range.start.toInt())} ~ ${msToTime(range.endInclusive.toInt())}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { onChange(0, total); range = 0f..total.toFloat() },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) { Text("전체", style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    if (previewing) return@Button
                    previewing = true
                    scope.launch {
                        try {
                            val mp = android.media.MediaPlayer()
                            mp.setDataSource(context, android.net.Uri.parse(rule.soundUri!!))
                            mp.prepare()
                            mp.seekTo(range.start.toInt())
                            mp.start()
                            val dur = (range.endInclusive - range.start).toLong().coerceAtLeast(200)
                            kotlinx.coroutines.delay(dur)
                            try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
                            mp.release()
                        } catch (_: Exception) {}
                        previewing = false
                    }
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(if (previewing) "재생중…" else "▶ 미리듣기", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 밀리초 → "mm:ss.s" 포맷. */
private fun msToTime(ms: Int): String {
    val s = ms / 1000
    val mm = s / 60
    val ss = s % 60
    val tenth = (ms % 1000) / 100
    return "%d:%02d.%d".format(mm, ss, tenth)
}

/**
 * Lottie JSON 파일을 Composable에서 재생. file:// 또는 content:// URI 모두 지원.
 * JSON을 문자열로 읽은 뒤 LottieCompositionSpec.JsonString으로 파싱.
 */
@Composable
fun LottieMediaFromUri(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val jsonString = remember(uri) {
        try {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { input ->
                input.bufferedReader().readText()
            }
        } catch (_: Exception) { null }
    }
    if (jsonString == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Lottie 로드 실패", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        return
    }
    val composition by com.airbnb.lottie.compose.rememberLottieComposition(
        com.airbnb.lottie.compose.LottieCompositionSpec.JsonString(jsonString)
    )
    val progress by com.airbnb.lottie.compose.animateLottieCompositionAsState(
        composition = composition,
        iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever
    )
    com.airbnb.lottie.compose.LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
    )
}

// 카드 안에서 마블 물리 시뮬레이션을 돌려 미디어가 실제 등장 애니메이션처럼 튕기도록 함.
// 한 번 시뮬 후 약 2.5초 쉬었다가 반복.
@Composable
fun MarblePreview(rule: Rule) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isAppIcon = rule.mediaUri == null && rule.packageName != null

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val boxWidthPx = with(density) { maxWidth.toPx() }
        val boxHeightPx = with(density) { maxHeight.toPx() }
        val cardWidthDp = maxWidth.value

        // 카드를 "작은 휴대폰 화면"으로 취급. 실제 디바이스 폭(dp)과 카드 폭의 비율로 스케일.
        // 미디어가 카드보다 크면 부모 Box의 clip(RoundedCornerShape)이 자동으로 잘라줌.
        val phoneRefWidthDp = configuration.screenWidthDp.toFloat().coerceAtLeast(1f)
        val scaleFactor = cardWidthDp / phoneRefWidthDp
        val sourceDp = remember(rule.id, rule.mediaSize, rule.mediaSizeRandom, rule.appIconSize, rule.appIconSizeRandom, isAppIcon, rule.previewBoost) {
            val raw = if (isAppIcon)
                effectiveValue(rule.appIconSize, rule.appIconSizeRandom, 50f, 200f)
            else
                effectiveValue(rule.mediaSize, rule.mediaSizeRandom, 50f, 600f)
            // 미리보기 보정: 작은 미디어(<80dp)는 카드에서 잘 안 보이니 미리보기에서만 키움
            if (rule.previewBoost && raw < 80f) 80f else raw
        }
        // 최소 10dp 보장 (0 사이즈 방지), 상한 캡 없음 — 큰 미디어는 카드 밖으로 넘쳐 클리핑됨
        val sizeDp = (sourceDp * scaleFactor).coerceAtLeast(10f).dp
        val sizePx = with(density) { sizeDp.toPx() }

        var x by remember(rule.id) { mutableStateOf(boxWidthPx / 2f - sizePx / 2f) }
        var y by remember(rule.id) { mutableStateOf(boxHeightPx) }
        var rotation by remember(rule.id) { mutableStateOf(0f) }

        // 등장 애니메이션 OFF면 사용자 설정 위치에 정적 표시
        if (!rule.entryAnimation) {
            MediaContent(
                rule = rule,
                sizeDp = sizeDp,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (rule.targetXFraction * boxWidthPx - sizePx / 2f).roundToInt(),
                            (rule.targetYFraction * boxHeightPx - sizePx / 2f).roundToInt()
                        )
                    }
                    .graphicsLayer { rotationZ = rule.targetRotation }
            )
            return@BoxWithConstraints
        }

        LaunchedEffect(rule.id, boxWidthPx, boxHeightPx, sizePx, rule.entryMode) {
            delay(400)
            val previewRule = previewSpeedCapped(rule)
            while (isActive) {
                when (rule.entryMode) {
                    "drift" -> runDriftSim(
                        boxWidth = boxWidthPx,
                        boxHeight = boxHeightPx,
                        sizePx = sizePx,
                        densityFactor = density.density,
                        rule = previewRule,
                        maxDurationSec = 5f
                    ) { newX, newY, newRot -> x = newX; y = newY; rotation = newRot }
                    "directional" -> runDirectionalSim(
                        boxWidth = boxWidthPx,
                        boxHeight = boxHeightPx,
                        sizePx = sizePx,
                        densityFactor = density.density,
                        rule = previewRule,
                        maxDurationSec = 5f
                    ) { newX, newY, newRot -> x = newX; y = newY; rotation = newRot }
                    "peek" -> runPeekSim(
                        boxWidth = boxWidthPx,
                        boxHeight = boxHeightPx,
                        sizePx = sizePx,
                        rule = previewRule
                    ) { newX, newY -> x = newX; y = newY; rotation = 0f }
                    else -> runMarbleSim(
                        boxWidth = boxWidthPx,
                        boxHeight = boxHeightPx,
                        sizePx = sizePx,
                        densityFactor = density.density,
                        rule = previewRule
                    ) { newX, newY, newRot -> x = newX; y = newY; rotation = newRot }
                }
                delay(2500)
            }
        }

        MediaContent(
            rule = rule,
            sizeDp = sizeDp,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .graphicsLayer { rotationZ = rotation + rule.targetRotation }
        )
    }
}

/**
 * 마켓 카드 미리보기 — 원격 URL 미디어 + ruleJson 물리 시뮬레이션.
 * Coil이 GIF/이미지/동영상(첫 프레임)을 자동 디코드.
 * 영상은 첫 프레임만 표시 (실제 재생은 무게가 너무 큼).
 */
@Composable
fun MarketMarblePreview(animation: AnimationStore.RemoteAnimation) {
    val rule = remember(animation.id, animation.ruleJson) {
        try { Rule.fromJson(org.json.JSONObject(animation.ruleJson)) }
        catch (_: Exception) { Rule() }
    }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val boxWidthPx = with(density) { maxWidth.toPx() }
        val boxHeightPx = with(density) { maxHeight.toPx() }
        val cardWidthDp = maxWidth.value
        val phoneRefWidthDp = configuration.screenWidthDp.toFloat().coerceAtLeast(1f)
        val scaleFactor = cardWidthDp / phoneRefWidthDp
        val sourceDp = remember(rule.id, rule.mediaSize, rule.previewBoost) {
            val raw = effectiveValue(rule.mediaSize, rule.mediaSizeRandom, 50f, 600f)
            if (rule.previewBoost && raw < 80f) 80f else raw
        }
        val sizeDp = (sourceDp * scaleFactor).coerceAtLeast(10f).dp
        val sizePx = with(density) { sizeDp.toPx() }

        var x by remember(rule.id) { mutableStateOf(0f) }
        var y by remember(rule.id) { mutableStateOf(boxHeightPx) }
        var rotation by remember(rule.id) { mutableStateOf(0f) }

        if (!rule.entryAnimation) {
            RemoteMediaImage(
                animation = animation,
                sizeDp = sizeDp,
                circleCrop = rule.mediaCircleCrop,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (rule.targetXFraction * boxWidthPx - sizePx / 2f).roundToInt(),
                            (rule.targetYFraction * boxHeightPx - sizePx / 2f).roundToInt()
                        )
                    }
                    .graphicsLayer { rotationZ = rule.targetRotation }
            )
            return@BoxWithConstraints
        }

        LaunchedEffect(rule.id, boxWidthPx, boxHeightPx, sizePx, rule.entryMode) {
            delay(400)
            while (isActive) {
                when (rule.entryMode) {
                    "drift" -> runDriftSim(boxWidthPx, boxHeightPx, sizePx, density.density, previewSpeedCapped(rule), 5f) {
                        nx, ny, nr -> x = nx; y = ny; rotation = nr
                    }
                    "directional" -> runDirectionalSim(boxWidthPx, boxHeightPx, sizePx, density.density, previewSpeedCapped(rule), 5f) {
                        nx, ny, nr -> x = nx; y = ny; rotation = nr
                    }
                    else -> runMarbleSim(boxWidthPx, boxHeightPx, sizePx, density.density, previewSpeedCapped(rule)) {
                        nx, ny, nr -> x = nx; y = ny; rotation = nr
                    }
                }
                delay(2500)
            }
        }

        RemoteMediaImage(
            animation = animation,
            sizeDp = sizeDp,
            circleCrop = rule.mediaCircleCrop,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .graphicsLayer { rotationZ = rotation + rule.targetRotation }
        )
    }
}

@Composable
private fun RemoteMediaImage(
    animation: AnimationStore.RemoteAnimation,
    sizeDp: Dp,
    circleCrop: Boolean,
    modifier: Modifier = Modifier
) {
    val maybeCircle = if (circleCrop) Modifier.clip(CircleShape) else Modifier
    when (animation.mediaType) {
        "video" -> RemoteVideoPreview(
            url = animation.mediaUrl,
            modifier = modifier.size(sizeDp).then(maybeCircle)
        )
        "lottie" -> RemoteLottiePreview(
            url = animation.mediaUrl,
            modifier = modifier.size(sizeDp).then(maybeCircle)
        )
        else -> coil.compose.AsyncImage(
            model = animation.mediaUrl,
            contentDescription = animation.title,
            contentScale = if (circleCrop) androidx.compose.ui.layout.ContentScale.Crop
                           else androidx.compose.ui.layout.ContentScale.Fit,
            modifier = modifier.size(sizeDp).then(maybeCircle)
        )
    }
}

/** 마켓 카드의 Lottie — Lottie compose가 URL 직접 지원 (LottieCompositionSpec.Url). */
@Composable
private fun RemoteLottiePreview(url: String, modifier: Modifier = Modifier) {
    val composition by com.airbnb.lottie.compose.rememberLottieComposition(
        com.airbnb.lottie.compose.LottieCompositionSpec.Url(url)
    )
    val progress by com.airbnb.lottie.compose.animateLottieCompositionAsState(
        composition = composition,
        iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever
    )
    com.airbnb.lottie.compose.LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
    )
}

/**
 * 마켓 카드용 원격 동영상 재생 — ExoPlayer + 음소거 + 무한 루프.
 * Composable 분리(remember + DisposableEffect)로 LazyRow 스크롤 시 자동 release.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun RemoteVideoPreview(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    // 카드 미리보기는 최대 6초만 재생 → 무거운 영상은 처음 일부만 보고 멈춤.
    // 자세히 보려면 카드 탭 → 상세 화면.
    LaunchedEffect(player) {
        delay(6000)
        try { player.pause() } catch (_: Exception) {}
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier
    )
}

/** 상세 화면용 — 시간 제한 없이 영상 무한 루프 재생. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun FullVideoPreview(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier
    )
}

private suspend fun runMarbleSim(
    boxWidth: Float,
    boxHeight: Float,
    sizePx: Float,
    densityFactor: Float,
    rule: Rule,
    onUpdate: (x: Float, y: Float, rot: Float) -> Unit
) {
    val pxPerMeter = 200f * densityFactor
    val gravityScale = effectiveValue(rule.gravityScale, rule.gravityScaleRandom, 0.5f, 2.5f)
    val spinScale = effectiveValue(rule.spinScale, rule.spinScaleRandom, 0f, 3f)
    val elasticity = effectiveValue(rule.elasticity, rule.elasticityRandom, 0f, 1f)
    val bouncePeak = effectiveValue(rule.bouncePeak, rule.bouncePeakRandom, 0.3f, 0.8f)

    val g = 9.81f * pxPerMeter * gravityScale
    // 미디어가 카드보다 크면 음수가 나옴 → 0으로 클램프 (마블이 그 자리에 머무름)
    val groundY = (boxHeight - sizePx - 4f).coerceAtLeast(0f)
    val maxX = (boxWidth - sizePx).coerceAtLeast(0f)
    val minX = 0f
    val radius = sizePx / 2f

    // 사용자 설정 X 위치에서 시작 (실제 OverlayService와 동일)
    val startX = (rule.targetXFraction * (maxX - minX) + minX).coerceIn(minX, maxX)
    val startY = boxHeight
    val peakY = (1f - bouncePeak) * boxHeight
    val peakHeight = (startY - peakY).coerceAtLeast(1f)
    val v0y = -sqrt(2f * g * peakHeight)
    val firstFlightT = (-v0y + sqrt(v0y * v0y + 2f * g * (groundY - startY).coerceAtLeast(1f))) / g
    val endX = (Math.random() * maxX).toFloat()
    val initialVx = (endX - startX) / firstFlightT.coerceAtLeast(0.1f)

    var x = startX
    var y = startY
    var vx = initialVx
    var vy = v0y
    var rotation = 0f
    var rolling = false
    val bounceCutoff = 30f * densityFactor
    val friction = pxPerMeter * 0.5f * gravityScale

    var lastTime = System.nanoTime()

    while (true) {
        val now = System.nanoTime()
        val dt = ((now - lastTime) / 1_000_000_000f).coerceAtMost(0.05f)
        lastTime = now

        if (!rolling) {
            vy += g * dt
            y += vy * dt
            x += vx * dt
            if (x < minX) { x = minX; vx = -vx * elasticity }
            else if (x > maxX) { x = maxX; vx = -vx * elasticity }
            if (y >= groundY && vy > 0) {
                y = groundY
                vy = -vy * elasticity
                if (abs(vy) < bounceCutoff) { vy = 0f; rolling = true }
            }
        } else {
            y = groundY
            val sign = if (vx >= 0) 1f else -1f
            val newSpeed = (abs(vx) - friction * dt).coerceAtLeast(0f)
            vx = newSpeed * sign
            x += vx * dt
            if (x < minX) { x = minX; vx = -vx * elasticity }
            else if (x > maxX) { x = maxX; vx = -vx * elasticity }
            if (abs(vx) < 0.5f) {
                onUpdate(x, y, rotation)
                return
            }
        }

        val angularVelDeg = (vx / radius) * (180f / PI.toFloat()) * spinScale
        rotation += angularVelDeg * dt

        onUpdate(x, y, rotation)
        delay(16)
    }
}

/**
 * 천천히 이동 (drift) 모드.
 * - 화면 가장자리 랜덤 진입 → 직선 등속 이동
 * - 벽/바닥/천장 토글에 따라 충돌 시 반사 또는 통과(off-screen → 종료)
 * - maxDurationSec 경과 후 자동 종료 (preview 루프용; overlay에선 Long.MAX_VALUE 등 큰 값)
 */
private suspend fun runDriftSim(
    boxWidth: Float,
    boxHeight: Float,
    sizePx: Float,
    densityFactor: Float,
    rule: Rule,
    maxDurationSec: Float = Float.MAX_VALUE,
    onUpdate: (x: Float, y: Float, rot: Float) -> Unit
) {
    val targetSpeedPxs = rule.driftSpeed * densityFactor  // dp 단위 → px
    val maxX = (boxWidth - sizePx).coerceAtLeast(0f)
    val maxY = (boxHeight - sizePx).coerceAtLeast(0f)

    // 시작: 사용자 위치 기준 (랜덤 옵션 ON이면 X 무작위). #41 — Y도 화면 아래가 아닌 설정 위치에서 상승
    var x = if (rule.driftRandomStartX) (Math.random() * maxX).toFloat()
            else (rule.targetXFraction * maxX).coerceIn(0f, maxX)
    var y = (rule.targetYFraction * maxY).coerceIn(0f, maxY)  // 지정 위치에서 시작

    // 진행 방향: 기본은 위쪽 (-90°) ± 30° 랜덤. 충돌 시 ux/uy의 부호만 뒤집어 재사용.
    val angle = (-PI / 2 + (Math.random() - 0.5) * (PI / 3)).toFloat()
    var ux = kotlin.math.cos(angle)
    var uy = kotlin.math.sin(angle)
    var rotation = 0f
    // 회전 방향 — 매 사이클 시계(+1)/반시계(-1) 랜덤
    val rotateDir = if (Math.random() < 0.5) 1f else -1f
    // 가속도 페이드: ON일 때 처음 1.5초 동안 0% → 100% 선형 보간
    val accelRampSec = 1.5f

    val startNanos = System.nanoTime()
    var lastNanos = startNanos
    while (true) {
        val now = System.nanoTime()
        val dt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
        lastNanos = now
        val elapsed = (now - startNanos) / 1_000_000_000f
        if (elapsed > maxDurationSec) {
            onUpdate(x, y, rotation)
            return
        }

        val speedScale = if (rule.driftAccelerate) {
            (elapsed / accelRampSec).coerceAtMost(1f)
        } else 1f
        val curSpeed = targetSpeedPxs * speedScale
        val vx = ux * curSpeed
        val vy = uy * curSpeed

        x += vx * dt
        y += vy * dt

        // 벽 (좌우)
        if (rule.driftBounceWalls) {
            if (x < 0f) { x = 0f; ux = -ux }
            else if (x > maxX) { x = maxX; ux = -ux }
        }
        // 천장 (위)
        if (rule.driftBounceCeiling) {
            if (y < 0f) { y = 0f; uy = -uy }
        }
        // 바닥 (아래)
        if (rule.driftBounceFloor) {
            if (y > maxY) { y = maxY; uy = -uy }
        }

        // 화면 완전 이탈 (벽 OFF + 진행 방향이 화면 밖) → 종료
        if (x < -sizePx || x > boxWidth || y < -sizePx || y > boxHeight + sizePx) {
            return
        }

        // 천천히 회전 (30deg/s) — 가속도 비례, 시계/반시계 랜덤
        if (rule.driftRotate) {
            rotation += 30f * speedScale * rotateDir * dt
        }

        onUpdate(x, y, rotation)
        delay(16)
    }
}

/**
 * 방향 이동(directional) 모드.
 * - 사용자 위치(targetXFraction/YFraction)에서 시작
 * - directionalAngleDeg 방향(0°=오른쪽)으로 직선 이동
 * - driftBounceWalls/Floor/Ceiling 토글에 따라 반사
 */
private suspend fun runDirectionalSim(
    boxWidth: Float,
    boxHeight: Float,
    sizePx: Float,
    densityFactor: Float,
    rule: Rule,
    maxDurationSec: Float = Float.MAX_VALUE,
    onUpdate: (x: Float, y: Float, rot: Float) -> Unit
) {
    val targetSpeedPxs = rule.driftSpeed * densityFactor
    val maxX = (boxWidth - sizePx).coerceAtLeast(0f)
    val maxY = (boxHeight - sizePx).coerceAtLeast(0f)

    // 시작: 사용자 위치 그대로
    var x = (rule.targetXFraction * maxX).coerceIn(0f, maxX)
    var y = (rule.targetYFraction * maxY).coerceIn(0f, maxY)

    val rad = rule.directionalAngleDeg * PI.toFloat() / 180f
    var ux = cos(rad)
    var uy = sin(rad)
    var rotation = 0f
    val rotateDir = if (Math.random() < 0.5) 1f else -1f
    val accelRampSec = 1.5f

    val startNanos = System.nanoTime()
    var lastNanos = startNanos
    while (true) {
        val now = System.nanoTime()
        val dt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
        lastNanos = now
        val elapsed = (now - startNanos) / 1_000_000_000f
        if (elapsed > maxDurationSec) { onUpdate(x, y, rotation); return }

        val speedScale = if (rule.driftAccelerate) (elapsed / accelRampSec).coerceAtMost(1f) else 1f
        val curSpeed = targetSpeedPxs * speedScale
        x += ux * curSpeed * dt
        y += uy * curSpeed * dt

        if (rule.driftBounceWalls) {
            if (x < 0f) { x = 0f; ux = -ux }
            else if (x > maxX) { x = maxX; ux = -ux }
        }
        if (rule.driftBounceCeiling) {
            if (y < 0f) { y = 0f; uy = -uy }
        }
        if (rule.driftBounceFloor) {
            if (y > maxY) { y = maxY; uy = -uy }
        }

        if (x < -sizePx || x > boxWidth || y < -sizePx || y > boxHeight + sizePx) {
            return
        }

        if (rule.driftRotate) {
            rotation += 30f * speedScale * rotateDir * dt
        }

        onUpdate(x, y, rotation)
        delay(16)
    }
}

/**
 * 쿨타임 입력 — 숫자 + 단위(초/분/시) 드롭다운. 내부 저장은 항상 초 단위.
 * 단위는 가장 깔끔하게 떨어지는 걸 기본으로 (3600의 배수면 시, 60의 배수면 분, 아니면 초).
 */
@Composable
fun CooldownInput(seconds: Int, onChange: (Int) -> Unit) {
    // 현재 초 값을 가장 적절한 단위로 표시
    val (initialDisplay, initialUnit) = when {
        seconds > 0 && seconds % 3600 == 0 -> (seconds / 3600) to "시"
        seconds > 0 && seconds % 60 == 0 -> (seconds / 60) to "분"
        else -> seconds to "초"
    }
    var unit by remember(seconds) { mutableStateOf(initialUnit) }
    var text by remember(seconds, unit) { mutableStateOf(initialDisplay.toString()) }
    var menuOpen by remember { mutableStateOf(false) }

    fun applyChange(textValue: String, unitValue: String) {
        val n = textValue.toIntOrNull() ?: return
        if (n < 0) return
        val multiplier = when (unitValue) { "시" -> 3600; "분" -> 60; else -> 1 }
        onChange(n * multiplier)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                text = new
                applyChange(new, unit)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Box {
            OutlinedButton(
                onClick = { menuOpen = true },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) { Text(unit) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                listOf("초", "분", "시").forEach { u ->
                    DropdownMenuItem(
                        text = { Text(u) },
                        onClick = {
                            unit = u
                            menuOpen = false
                            applyChange(text, u)
                        }
                    )
                }
            }
        }
    }
}

/** 카드 미리보기에서 driftSpeed를 150 dp/s로 캡 (previewSpeedCap=ON 일 때). 실제 재생엔 영향 X. */
private fun previewSpeedCapped(rule: Rule): Rule =
    if (rule.previewSpeedCap && rule.driftSpeed > 150f) rule.copy(driftSpeed = 150f) else rule

/**
 * Peek 모드 — 화면 4모서리 중 한 곳에서 절반쯤 나왔다 사라지는 애니메이션.
 * peekSide == "random"이면 매번 4방향 중 랜덤. 그 외 4가지는 고정.
 * 진행: off-screen → 슬라이드 인 → 머무름 → 슬라이드 아웃 → 끝
 */
private suspend fun runPeekSim(
    boxWidth: Float,
    boxHeight: Float,
    sizePx: Float,
    rule: Rule,
    onUpdate: (x: Float, y: Float) -> Unit
) {
    val side = if (rule.peekSide == "random") {
        listOf("top", "bottom", "left", "right").random()
    } else rule.peekSide

    // 시작/종료 (절반 노출) 좌표 — 모서리에 직각으로 들어옴
    val centerX = (boxWidth - sizePx) / 2f
    val centerY = (boxHeight - sizePx) / 2f
    val (startX, startY, peekX, peekY) = when (side) {
        "top" -> floatArrayOf(centerX, -sizePx, centerX, -sizePx / 2f)
        "bottom" -> floatArrayOf(centerX, boxHeight, centerX, boxHeight - sizePx / 2f)
        "left" -> floatArrayOf(-sizePx, centerY, -sizePx / 2f, centerY)
        else -> floatArrayOf(boxWidth, centerY, boxWidth - sizePx / 2f, centerY)  // right
    }.let { listOf(it[0], it[1], it[2], it[3]) }

    val slideInMs = 400
    val holdMs = (rule.peekHoldSec * 1000).toInt()
    val slideOutMs = 400

    // 슬라이드 인 — easeOut 느낌 (1 - (1-t)^2)
    val frameDelay = 16L
    val inFrames = (slideInMs / frameDelay).toInt().coerceAtLeast(1)
    for (i in 0..inFrames) {
        val t = i.toFloat() / inFrames
        val ease = 1f - (1f - t) * (1f - t)
        onUpdate(startX + (peekX - startX) * ease, startY + (peekY - startY) * ease)
        delay(frameDelay)
    }
    // 머무름
    onUpdate(peekX, peekY)
    delay(holdMs.toLong())
    // 슬라이드 아웃 — easeIn (t^2)
    val outFrames = (slideOutMs / frameDelay).toInt().coerceAtLeast(1)
    for (i in 0..outFrames) {
        val t = i.toFloat() / outFrames
        val ease = t * t
        onUpdate(peekX + (startX - peekX) * ease, peekY + (startY - peekY) * ease)
        delay(frameDelay)
    }
}

private fun effectiveValue(value: Float, isRandom: Boolean, minVal: Float, maxVal: Float): Float =
    if (isRandom) (Math.random() * (maxVal - minVal) + minVal).toFloat() else value

@Composable
fun VideoThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    // 카드에서 음소거 + 루프 자동 재생. 보관함 미리보기용.
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(uri)
                setOnPreparedListener { mp ->
                    mp.setVolume(0f, 0f)
                    mp.isLooping = true
                    start()
                }
                setOnErrorListener { _, _, _ -> true }
            }
        },
        modifier = modifier
    )
}

@Composable
fun RuleEditScreen(
    initialRule: Rule,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onUpdate: (Rule) -> Unit
) {
    var rule by remember(initialRule.id) { mutableStateOf(initialRule) }
    var isFirst by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // 위치 편집 오버레이가 RuleStore를 갱신하면 알림 받아서 로컬 state 새로고침
    LaunchedEffect(RuleUpdateBus.lastUpdate) {
        val update = RuleUpdateBus.lastUpdate
        if (update != null && update.second == initialRule.id) {
            val fresh = RuleStore.find(context, initialRule.id)
            if (fresh != null) {
                isFirst = true  // 외부 변경 → onUpdate 자동 호출 방지
                rule = fresh
            }
        }
    }

    LaunchedEffect(rule) {
        if (isFirst) { isFirst = false; return@LaunchedEffect }
        onUpdate(rule)
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }
    var analyzingLoudness by remember { mutableStateOf(false) }
    var coinLookupState by remember { mutableStateOf<String?>(null) }  // 상태 문구
    val coroutineScope = rememberCoroutineScope()

    // 제목이 "$XXX"로 시작하면 코인 자동 감지 + 아이콘 다운로드 + 24h 쿨타임 적용
    LaunchedEffect(rule.name) {
        val raw = rule.name.trim()
        val match = Regex("""^\$([A-Za-z]{2,10})\b""").find(raw)
        if (match == null) {
            // 코인 표기 사라지면 (사용자가 지움) 코인 메타데이터 클리어
            if (rule.coinSymbol != null) {
                rule = rule.copy(coinSymbol = null, coinId = null, coinIconUrl = null, lastPolledPrice = null)
                coinLookupState = null
            }
            return@LaunchedEffect
        }
        val symbol = match.groupValues[1].uppercase()
        if (rule.coinSymbol == symbol && rule.coinId != null) return@LaunchedEffect  // 이미 매칭됨

        kotlinx.coroutines.delay(600)  // 타이핑 디바운스
        coinLookupState = "코인 정보 조회 중…"
        val info = CoinRegistry.lookup(context, symbol)
        if (info == null) {
            coinLookupState = "$$symbol — 알 수 없는 심볼 (CoinGecko에 없음)"
            return@LaunchedEffect
        }
        // 아이콘 다운로드
        val iconFile = CoinRegistry.downloadIcon(context, symbol, info.image)
        val updated = rule.copy(
            coinSymbol = symbol,
            coinId = info.id,
            coinIconUrl = info.image,
            mediaUri = iconFile?.let { android.net.Uri.fromFile(it).toString() } ?: rule.mediaUri,
            mediaType = if (iconFile != null) "image" else rule.mediaType,
            mediaName = if (iconFile != null) "${info.name} 아이콘" else rule.mediaName,
            mediaCircleCrop = true,  // 코인 아이콘은 원형이 자연스러움
            // 코인 룰은 가격 알람 → 같은 가격이 24시간 안에 또 트리거되면 보통 노이즈
            sameContentCooldownSec = if (rule.sameContentCooldownSec == 5) 86400 else rule.sameContentCooldownSec,
            blockSameContentRepeat = true
        )
        rule = updated
        coinLookupState = "${info.name} ($$symbol) 연결됨"
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val mime = context.contentResolver.getType(uri) ?: ""
            val type = if (mime.startsWith("video/")) "video" else "image"
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "미디어"
            rule = rule.copy(mediaUri = uri.toString(), mediaType = type, mediaName = name)
        }
    }
    // SAF 기반 폴백 피커. 사진 피커가 못 잡는 .mov, .json (Lottie) 등 파일을 모든 폴더에서 선택 가능.
    val mediaFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val mime = context.contentResolver.getType(uri) ?: ""
            val lastSeg = uri.lastPathSegment?.substringAfterLast('/') ?: ""
            val type = when {
                mime.startsWith("video/") -> "video"
                mime.contains("json") || lastSeg.endsWith(".json", ignoreCase = true) -> "lottie"
                else -> "image"
            }
            val name = lastSeg.ifBlank { "미디어" }
            rule = rule.copy(mediaUri = uri.toString(), mediaType = type, mediaName = name)
        }
    }
    var showLottieUrlDialog by remember { mutableStateOf(false) }
    var waveform by remember { mutableStateOf<FloatArray?>(null) }
    // 기존 룰에 사운드가 있는데 파형이 아직 없으면 자동 추출
    LaunchedEffect(rule.soundUri) {
        val uriStr = rule.soundUri
        if (uriStr != null && waveform == null) {
            val wave = WaveformExtractor.extract(context, android.net.Uri.parse(uriStr))
            if (wave != null) {
                waveform = wave.rms
                if (rule.soundDurationMs == 0) {
                    rule = rule.copy(soundDurationMs = wave.durationMs,
                                     soundEndMs = if (rule.soundEndMs < 0) wave.durationMs else rule.soundEndMs)
                }
            }
        }
    }
    val soundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "사운드"
            // 새 사운드 → 트림 리셋
            rule = rule.copy(
                soundUri = uri.toString(),
                soundName = name,
                measuredLoudnessDb = null,
                soundStartMs = 0,
                soundEndMs = -1,
                soundDurationMs = 0
            )
            waveform = null
            analyzingLoudness = true
            coroutineScope.launch {
                val measured = LoudnessAnalyzer.measureDbfs(context, uri)
                rule = rule.copy(measuredLoudnessDb = measured)
                // 파형 추출 (별도 단계 — measured는 LoudnessAnalyzer 결과)
                val wave = WaveformExtractor.extract(context, uri)
                if (wave != null) {
                    waveform = wave.rms
                    rule = rule.copy(soundDurationMs = wave.durationMs, soundEndMs = wave.durationMs)
                }
                analyzingLoudness = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            onClick = onBack,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(
                "뒤로",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedTextField(
            value = rule.name,
            onValueChange = { rule = rule.copy(name = it) },
            label = { Text("제목") },
            supportingText = {
                Text(
                    "팁: \$BTC, \$ETH 등으로 시작하면 코인 아이콘이 자동 연결됩니다. 키워드에 가격(예: 76000)을 적으면 그 가격 도달 시 알람.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (coinLookupState != null) {
            Text(
                coinLookupState!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (coinLookupState!!.contains("알 수 없는")) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("키워드 사용", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = rule.keywordEnabled,
                onCheckedChange = { rule = rule.copy(keywordEnabled = it) }
            )
        }
        if (rule.keywordEnabled) {
            KeywordListEditor(
                keywords = rule.keywords,
                onChange = { rule = rule.copy(keywords = it) }
            )
        }

        HorizontalDivider()

        Text("앱 선택", style = MaterialTheme.typography.titleMedium)
        Text(if (rule.appLabel != null) "현재: ${rule.appLabel}" else "선택 안 됨")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAppPicker = true }) {
                Text(if (rule.packageName == null) "앱 선택" else "앱 변경")
            }
            if (rule.packageName != null) {
                Button(onClick = { rule = rule.copy(packageName = null, appLabel = null) }) {
                    Text("해제")
                }
            }
        }

        HorizontalDivider()

        Text("미디어", style = MaterialTheme.typography.titleMedium)
        when {
            rule.mediaUri != null && rule.mediaType == "image" -> {
                AndroidView(
                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                    // when 분기 빠진 후에도 update가 한 번 더 fire되는 Compose race가 있어서 null-safe로
                    update = { it.setImageURI(rule.mediaUri?.let(Uri::parse)) },
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
            }
            rule.mediaUri != null && rule.mediaType == "video" -> {
                Text("동영상: ${rule.mediaName ?: "이름 없음"}")
            }
            rule.mediaUri != null && rule.mediaType == "lottie" -> {
                LottieMediaFromUri(uri = rule.mediaUri!!, modifier = Modifier.fillMaxWidth().height(160.dp))
                Text("Lottie: ${rule.mediaName ?: "이름 없음"}", style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                Text("선택된 미디어 없음 (앱 아이콘 폴백)")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    mediaPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) { Text("갤러리", style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = { mediaFilePicker.launch(arrayOf("image/*", "video/*", "application/json")) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) { Text("파일", style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = { showLottieUrlDialog = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Lottie URL", style = MaterialTheme.typography.bodySmall) }
            if (rule.mediaUri != null) {
                Button(
                    onClick = {
                        rule = rule.copy(mediaUri = null, mediaType = "image", mediaName = null)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("해제", style = MaterialTheme.typography.bodySmall) }
            }
        }

        // 선택된 미디어가 GIF/WebP일 때만 반복 재생 토글 노출
        val isAnimatedImage = remember(rule.mediaUri, rule.mediaType) {
            if (rule.mediaType != "image" || rule.mediaUri == null) false
            else {
                val mime = try {
                    context.contentResolver.getType(Uri.parse(rule.mediaUri)) ?: ""
                } catch (_: Exception) { "" }
                mime == "image/gif" || mime == "image/webp"
            }
        }
        if (isAnimatedImage) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("반복 재생")
                Switch(
                    checked = rule.mediaLoop,
                    onCheckedChange = { rule = rule.copy(mediaLoop = it) }
                )
            }
        }
        // 원형 크롭 — 미디어 있을 때만 노출
        if (rule.mediaUri != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("원형으로 자르기")
                Switch(
                    checked = rule.mediaCircleCrop,
                    onCheckedChange = { rule = rule.copy(mediaCircleCrop = it) }
                )
            }
        }

        SliderRow(
            label = "미디어 크기",
            value = rule.mediaSize,
            range = 50f..600f,
            onValueChange = { rule = rule.copy(mediaSize = it) },
            isRandom = rule.mediaSizeRandom,
            onRandomChange = { rule = rule.copy(mediaSizeRandom = it) },
            suffix = "dp"
        )

        Text("미디어 위치", style = MaterialTheme.typography.bodyMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = {
                onUpdate(rule)
                val editIntent = Intent(context, OverlayService::class.java)
                editIntent.action = "EDIT_POSITION"
                editIntent.putExtra("ruleId", rule.id)
                context.startService(editIntent)
            }) { Text("위치 조절") }

            OutlinedTextField(
                value = rule.targetRotation.toInt().toString(),
                onValueChange = {
                    val r = it.toFloatOrNull()
                    if (r != null) rule = rule.copy(targetRotation = r)
                    else if (it.isBlank()) rule = rule.copy(targetRotation = 0f)
                },
                label = { Text("회전 (°)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    val next = (rule.targetRotation + 90f) % 360f
                    val normalized = if (next < 0f) next + 360f else next
                    rule = rule.copy(targetRotation = normalized)
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) { Text("+90°", style = MaterialTheme.typography.bodySmall) }
        }

        HorizontalDivider()

        Text("사운드", style = MaterialTheme.typography.titleMedium)

        val isVideoSelected = rule.mediaType == "video" && rule.mediaUri != null

        if (isVideoSelected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("동영상 사운드 사용")
                Switch(
                    checked = rule.useVideoSound,
                    onCheckedChange = { rule = rule.copy(useVideoSound = it) }
                )
            }
        }

        // 동영상 + 동영상 사운드 사용 ON일 때는 사운드 선택 숨김
        if (!isVideoSelected || !rule.useVideoSound) {
            Text(if (rule.soundName != null) "선택됨: ${rule.soundName}" else "기본 알림음")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { soundPicker.launch(arrayOf("audio/*")) }) { Text("사운드 선택") }
                OutlinedButton(onClick = { showUrlImportDialog = true }) { Text("URL로 가져오기") }
            }
            // 분석 진행 상태만 표시 (정규화 dBFS 수치는 노출 X)
            if (analyzingLoudness) {
                Text(
                    "분석 중…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 컷 편집 — 사운드 있고 길이 측정됐을 때만
            if (rule.soundUri != null && rule.soundDurationMs > 0) {
                SoundTrimEditor(
                    rule = rule,
                    waveform = waveform,
                    onChange = { start, end -> rule = rule.copy(soundStartMs = start, soundEndMs = end) }
                )
            }
        }

        SliderRow(
            label = "음량",
            value = rule.userVolume * 100f,
            range = 0f..100f,
            onValueChange = { rule = rule.copy(userVolume = (it / 100f).coerceIn(0f, 1f)) },
            suffix = "%"
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Checkbox(
                    checked = rule.playInVibrate,
                    onCheckedChange = { rule = rule.copy(playInVibrate = it) }
                )
                Text("진동 모드에서도 재생", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Checkbox(
                    checked = rule.playInSilent,
                    onCheckedChange = { rule = rule.copy(playInSilent = it) }
                )
                Text("무음 모드에서도 재생", style = MaterialTheme.typography.bodySmall)
            }
        }

        HorizontalDivider()

        // 사운드만 재생 — ON일 때 오버레이/애니메이션 없이 사운드만
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("사운드만 재생", style = MaterialTheme.typography.titleMedium)
                Text(
                    "애니메이션 없이 사운드만 출력합니다. 미디어/오버레이 설정은 무시됨",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = rule.soundOnly,
                onCheckedChange = { rule = rule.copy(soundOnly = it) }
            )
        }

        if (!rule.soundOnly) {
            HorizontalDivider()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("등장 애니메이션", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = rule.entryAnimation,
                    onCheckedChange = { rule = rule.copy(entryAnimation = it) }
                )
            }
        }
        if (!rule.soundOnly && rule.entryAnimation) {
            // 모드 선택 (라디오 2-옵션 — 마블 / 천천히 이동)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp)
            ) {
                val modes = listOf(
                    "marble" to "구슬",
                    "drift" to "천천히 상승",
                    "directional" to "방향 이동",
                    "peek" to "살짝 보임"
                )
                modes.forEach { (key, label) ->
                    val selected = rule.entryMode == key
                    OutlinedButton(
                        onClick = { rule = rule.copy(entryMode = key) },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.tertiaryContainer
                                             else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onTertiaryContainer
                                           else MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.outline
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Button(onClick = {
            onUpdate(rule)
            val intent = Intent(context, OverlayService::class.java)
            intent.putExtra("ruleId", rule.id)
            // 미디어 미설정 시에도 폴백 아이콘이 뜨도록 선택된 앱 패키지 전달
            if (rule.mediaUri == null && rule.packageName != null) {
                intent.putExtra("sourcePackage", rule.packageName)
            }
            context.startService(intent)
        }) { Text("테스트 재생") }

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvanced = !showAdvanced }
                .padding(vertical = 8.dp)
        ) {
            Text(
                "${if (showAdvanced) "▼" else "▶"} 상세 설정",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }

        if (showAdvanced) {
            Text("네거티브 키워드", style = MaterialTheme.typography.bodyMedium)
            Text(
                "설정해둔 키워드론 알림이 재생되지 않습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            KeywordListEditor(
                keywords = rule.negativeKeywords,
                onChange = { rule = rule.copy(negativeKeywords = it) }
            )

            HorizontalDivider()

            SliderRow(
                label = "앱 아이콘 크기",
                value = rule.appIconSize,
                range = 25f..200f,
                onValueChange = { rule = rule.copy(appIconSize = it) },
                isRandom = rule.appIconSizeRandom,
                onRandomChange = { rule = rule.copy(appIconSizeRandom = it) },
                suffix = "dp"
            )

            HorizontalDivider()

            Text("미리보기", style = MaterialTheme.typography.bodyMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("미리보기에서 크기 보정")
                    Text(
                        "80dp 미만 미디어를 카드에서 80dp로 키워서 보여줌 — 실제 재생 크기는 그대로",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.previewBoost,
                    onCheckedChange = { rule = rule.copy(previewBoost = it) }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("미리보기에서 속도 보정")
                    Text(
                        "150dp/s 초과 속도를 카드에서 150으로 제한 — 실제 재생 속도는 그대로",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.previewSpeedCap,
                    onCheckedChange = { rule = rule.copy(previewSpeedCap = it) }
                )
            }

            HorizontalDivider()

            Text("인터랙션", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("드래그로 이동")
                Switch(checked = rule.dragEnabled, onCheckedChange = { rule = rule.copy(dragEnabled = it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("던져서 닫기")
                Switch(checked = rule.flingToDismiss, onCheckedChange = { rule = rule.copy(flingToDismiss = it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("탭해서 닫기")
                Switch(checked = rule.tapToDismiss, onCheckedChange = { rule = rule.copy(tapToDismiss = it) })
            }

            // 마블 모드 전용 물리 설정
            if (rule.entryAnimation && rule.entryMode == "marble") {
                HorizontalDivider()
                Text("구슬 물리 설정", style = MaterialTheme.typography.bodyMedium)

                SliderRow(
                    label = "중력",
                    value = rule.gravityScale,
                    range = 0.5f..2.5f,
                    onValueChange = { rule = rule.copy(gravityScale = it) },
                    isRandom = rule.gravityScaleRandom,
                    onRandomChange = { rule = rule.copy(gravityScaleRandom = it) },
                    suffix = "x", decimals = 1
                )
                SliderRow(
                    label = "스핀 강도",
                    value = rule.spinScale,
                    range = 0f..3f,
                    onValueChange = { rule = rule.copy(spinScale = it) },
                    isRandom = rule.spinScaleRandom,
                    onRandomChange = { rule = rule.copy(spinScaleRandom = it) },
                    suffix = "x", decimals = 1
                )
                SliderRow(
                    label = "탄성",
                    value = rule.elasticity,
                    range = 0f..1f,
                    onValueChange = { rule = rule.copy(elasticity = it) },
                    isRandom = rule.elasticityRandom,
                    onRandomChange = { rule = rule.copy(elasticityRandom = it) },
                    decimals = 2
                )
                SliderRow(
                    label = "바닥 위치",
                    value = rule.floorOffset,
                    range = 0f..60f,
                    onValueChange = { rule = rule.copy(floorOffset = it) },
                    suffix = "dp"
                )
                SliderRow(
                    label = "튕김 높이",
                    value = rule.bouncePeak,
                    range = 0.3f..0.8f,
                    onValueChange = { rule = rule.copy(bouncePeak = it) },
                    isRandom = rule.bouncePeakRandom,
                    onRandomChange = { rule = rule.copy(bouncePeakRandom = it) },
                    suffix = "%", displayMultiplier = 100f
                )
            }

            // 천천히 상승(drift) 모드 전용 설정
            if (rule.entryAnimation && rule.entryMode == "drift") {
                HorizontalDivider()
                Text("천천히 상승 설정", style = MaterialTheme.typography.bodyMedium)

                SliderRow(
                    label = "상승 속도",
                    value = rule.driftSpeed,
                    range = 50f..1000f,
                    onValueChange = { rule = rule.copy(driftSpeed = it) },
                    suffix = "dp/s"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("시작 위치 랜덤")
                        Text(
                            "매번 가로축 랜덤 위치에서 출발 (지정 X 무시, 높이는 지정 위치 유지)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rule.driftRandomStartX,
                        onCheckedChange = { rule = rule.copy(driftRandomStartX = it) }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("가속도")
                        Text(
                            "처음엔 느리다가 점점 빨라짐 (0%→100%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rule.driftAccelerate,
                        onCheckedChange = { rule = rule.copy(driftAccelerate = it) }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("회전")
                        Text(
                            "상승하면서 천천히 회전 (30°/s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rule.driftRotate,
                        onCheckedChange = { rule = rule.copy(driftRotate = it) }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("벽 충돌 (좌/우)")
                    Switch(
                        checked = rule.driftBounceWalls,
                        onCheckedChange = { rule = rule.copy(driftBounceWalls = it) }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("바닥 충돌")
                    Switch(
                        checked = rule.driftBounceFloor,
                        onCheckedChange = { rule = rule.copy(driftBounceFloor = it) }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("천장 충돌")
                    Switch(
                        checked = rule.driftBounceCeiling,
                        onCheckedChange = { rule = rule.copy(driftBounceCeiling = it) }
                    )
                }
                Text(
                    "충돌 OFF 시 화면 밖으로 빠져나가면 자동 종료",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 방향 이동(directional) 모드 전용 설정
            if (rule.entryAnimation && rule.entryMode == "directional") {
                HorizontalDivider()
                Text("방향 이동 설정", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "위치 조절값에서 시작해 지정한 방향으로 직선 이동",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AngleDialPicker(
                    angle = rule.directionalAngleDeg,
                    onAngleChange = { rule = rule.copy(directionalAngleDeg = it) }
                )

                SliderRow(
                    label = "이동 속도",
                    value = rule.driftSpeed,
                    range = 50f..1000f,
                    onValueChange = { rule = rule.copy(driftSpeed = it) },
                    suffix = "dp/s"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("가속도")
                        Text(
                            "처음엔 느리다가 점점 빨라짐 (0%→100%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rule.driftAccelerate,
                        onCheckedChange = { rule = rule.copy(driftAccelerate = it) }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("회전")
                        Text(
                            "이동하면서 천천히 회전 (30°/s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rule.driftRotate,
                        onCheckedChange = { rule = rule.copy(driftRotate = it) }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("벽 충돌 (좌/우) 시 반사")
                    Switch(
                        checked = rule.driftBounceWalls,
                        onCheckedChange = { rule = rule.copy(driftBounceWalls = it) }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("바닥 충돌 시 반사")
                    Switch(
                        checked = rule.driftBounceFloor,
                        onCheckedChange = { rule = rule.copy(driftBounceFloor = it) }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("천장 충돌 시 반사")
                    Switch(
                        checked = rule.driftBounceCeiling,
                        onCheckedChange = { rule = rule.copy(driftBounceCeiling = it) }
                    )
                }
                Text(
                    "충돌 OFF 시 화면 밖으로 빠져나가면 자동 종료",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 살짝 보임(peek) 모드 전용 설정
            if (rule.entryAnimation && rule.entryMode == "peek") {
                HorizontalDivider()
                Text("살짝 보임 설정", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "화면 모서리에서 절반쯤 나왔다가 머문 후 다시 사라집니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("등장 방향", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val sides = listOf(
                        "random" to "랜덤",
                        "top" to "↑ 위",
                        "bottom" to "↓ 아래",
                        "left" to "← 왼쪽",
                        "right" to "→ 오른쪽"
                    )
                    sides.forEach { (key, label) ->
                        val selected = rule.peekSide == key
                        OutlinedButton(
                            onClick = { rule = rule.copy(peekSide = key) },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.tertiaryContainer
                                                 else androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = if (selected) MaterialTheme.colorScheme.onTertiaryContainer
                                               else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text(label, style = MaterialTheme.typography.bodySmall) }
                    }
                }

                SliderRow(
                    label = "머무는 시간",
                    value = rule.peekHoldSec,
                    range = 0.3f..5f,
                    onValueChange = { rule = rule.copy(peekHoldSec = it) },
                    suffix = "초",
                    decimals = 1
                )
            }

            HorizontalDivider()

            Text("알림 처리", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("애니메이션 중첩")
                Switch(checked = rule.stackOverlays, onCheckedChange = { rule = rule.copy(stackOverlays = it) })
            }
            // 중첩 ON일 때만 최대 개수 표시
            if (rule.stackOverlays) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
                ) {
                    Text("최대 동시 표시:", modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = rule.stackMaxCount.toString(),
                        onValueChange = {
                            val n = it.toIntOrNull()
                            if (n != null) rule = rule.copy(stackMaxCount = n.coerceIn(1, 20))
                            else if (it.isBlank()) rule = rule.copy(stackMaxCount = 1)
                        },
                        suffix = { Text("개", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(100.dp)
                    )
                }
                Text(
                    "1~20개. 초과 시 가장 오래된 것부터 사라짐",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("충돌 시 같이 재생")
                    Text(
                        "다른 규칙이 더 구체적이어도 이 규칙도 함께 발동합니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.playAlongside,
                    onCheckedChange = { rule = rule.copy(playAlongside = it) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("키워드 중복시 재생안함")
                    Text(
                        "같은 내용의 알림이 수신되면 설정해둔 시간만큼 무시합니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.blockSameContentRepeat,
                    onCheckedChange = { rule = rule.copy(blockSameContentRepeat = it) }
                )
            }
            if (rule.blockSameContentRepeat) {
                CooldownInput(
                    seconds = rule.sameContentCooldownSec,
                    onChange = { rule = rule.copy(sameContentCooldownSec = it) }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("재생시간 제한")
                    Text(
                        "OFF 시 사운드 길이까지 또는 화면 이탈 시 자동 종료",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.animationDurationEnabled,
                    onCheckedChange = { rule = rule.copy(animationDurationEnabled = it) }
                )
            }
            if (rule.animationDurationEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
                ) {
                    Text("재생시간:", modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = rule.animationDurationSec.toString(),
                        onValueChange = {
                            val v = it.toFloatOrNull()
                            if (v != null && v >= 0f) rule = rule.copy(animationDurationSec = v)
                            else if (it.isBlank()) rule = rule.copy(animationDurationSec = 0f)
                        },
                        suffix = { Text("초", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("화면 꺼져있을 때 깨우기")
                    Text(
                        "잠금화면 위에 미디어가 표시됩니다. 배터리 소모 증가",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = rule.wakeScreen, onCheckedChange = { rule = rule.copy(wakeScreen = it) })
            }

            // 재생 시간대 제한
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("재생 시간대 제한")
                    Text(
                        "지정한 시간 외에는 알림이 발동하지 않습니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.scheduleEnabled,
                    onCheckedChange = { rule = rule.copy(scheduleEnabled = it) }
                )
            }
            if (rule.scheduleEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
                ) {
                    TimeOfDayPicker(
                        label = "시작",
                        minutesOfDay = rule.scheduleStartMin,
                        onChange = { rule = rule.copy(scheduleStartMin = it) },
                        modifier = Modifier.weight(1f)
                    )
                    TimeOfDayPicker(
                        label = "종료",
                        minutesOfDay = rule.scheduleEndMin,
                        onChange = { rule = rule.copy(scheduleEndMin = it) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rule.scheduleStartMin == rule.scheduleEndMin) {
                    Text(
                        "⚠ 시작과 종료가 같음 — 24시간 발동되지 않습니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                } else if (rule.scheduleEndMin < rule.scheduleStartMin) {
                    Text(
                        "야간 모드 — 자정을 넘어가는 구간 (예: 22:00 → 06:00)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("가로모드에서 알림 비활성화")
                    Text(
                        "가로 화면 회전 시 이 규칙을 발동하지 않음",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.disableInLandscape,
                    onCheckedChange = { rule = rule.copy(disableInLandscape = it) }
                )
            }
            if (!rule.disableInLandscape) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
                ) {
                    Text("가로모드에서 애니메이션만 재생", modifier = Modifier.weight(1f))
                    Switch(
                        checked = rule.landscapeAnimationOnly,
                        onCheckedChange = {
                            rule = rule.copy(
                                landscapeAnimationOnly = it,
                                landscapeSoundOnly = if (it) false else rule.landscapeSoundOnly
                            )
                        }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
                ) {
                    Text("가로모드에서 사운드만 재생", modifier = Modifier.weight(1f))
                    Switch(
                        checked = rule.landscapeSoundOnly,
                        onCheckedChange = {
                            rule = rule.copy(
                                landscapeSoundOnly = it,
                                landscapeAnimationOnly = if (it) false else rule.landscapeAnimationOnly
                            )
                        }
                    )
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelect = { pkg, label ->
                // 규칙 이름이 비어있으면 앱 이름으로 자동 채움 (사용자가 이미 입력했으면 보존)
                val newName = if (rule.name.isBlank()) label else rule.name
                rule = rule.copy(packageName = pkg, appLabel = label, name = newName)
                showAppPicker = false
            }
        )
    }

    if (showUrlImportDialog) {
        SoundUrlImportDialog(
            onDismiss = { showUrlImportDialog = false },
            onImported = { uri, name, measuredDb ->
                rule = rule.copy(
                    soundUri = uri, soundName = name, measuredLoudnessDb = measuredDb,
                    soundStartMs = 0, soundEndMs = -1, soundDurationMs = 0
                )
                waveform = null
                // URL 임포트 후에도 파형 추출
                coroutineScope.launch {
                    val wave = WaveformExtractor.extract(context, android.net.Uri.parse(uri))
                    if (wave != null) {
                        waveform = wave.rms
                        rule = rule.copy(soundDurationMs = wave.durationMs, soundEndMs = wave.durationMs)
                    }
                }
                showUrlImportDialog = false
            }
        )
    }

    if (showLottieUrlDialog) {
        LottieUrlImportDialog(
            onDismiss = { showLottieUrlDialog = false },
            onImported = { uri, name ->
                rule = rule.copy(mediaUri = uri, mediaType = "lottie", mediaName = name)
                showLottieUrlDialog = false
            }
        )
    }
}

/**
 * Lottie URL 임포트 — 직접 .json URL만 지원 (LottieFiles 페이지 URL은 안 됨).
 * 다운로드해서 filesDir/lottie/{uuid}.json에 저장하고 file:// URI 반환.
 */
@Composable
fun LottieUrlImportDialog(
    onDismiss: () -> Unit,
    onImported: (uri: String, name: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!downloading) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Lottie URL로 가져오기", style = MaterialTheme.typography.titleMedium)
                Text(
                    "직접 .json URL을 입력하세요 (예: https://lottie.host/...json). LottieFiles 페이지 URL은 지원하지 않음 — 페이지에서 JSON 파일을 따로 받으세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("Lottie JSON URL") },
                    placeholder = { Text("https://...json") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !downloading
                )
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss, enabled = !downloading, modifier = Modifier.weight(1f)) { Text("취소") }
                    Button(
                        onClick = {
                            val cleaned = url.trim()
                            if (!cleaned.startsWith("http")) {
                                error = "URL은 http:// 또는 https://로 시작해야 합니다"
                                return@Button
                            }
                            downloading = true; error = null
                            scope.launch {
                                val result = downloadLottieJson(context, cleaned)
                                downloading = false
                                if (result != null) {
                                    onImported(result.first, result.second)
                                } else {
                                    error = "다운로드 실패 — 직접 .json URL인지 확인해주세요"
                                }
                            }
                        },
                        enabled = !downloading && url.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (downloading) CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        ) else Text("다운로드")
                    }
                }
            }
        }
    }
}

/** Lottie JSON을 다운로드 → 로컬 파일 저장 → (URI, 이름) 반환. 실패 시 null. */
private suspend fun downloadLottieJson(
    context: android.content.Context,
    url: String
): Pair<String, String>? = withContext(Dispatchers.IO) {
    try {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            // 최소 검증: JSON으로 파싱 시도
            org.json.JSONObject(text)
            val dir = java.io.File(context.filesDir, "lottie").apply { mkdirs() }
            val out = java.io.File(dir, "${java.util.UUID.randomUUID()}.json")
            out.writeText(text)
            val name = url.substringBefore('?').substringAfterLast('/').ifBlank { "lottie.json" }
            android.net.Uri.fromFile(out).toString() to name
        } finally { conn.disconnect() }
    } catch (e: Exception) {
        android.util.Log.e("LottieImport", "fail", e); null
    }
}

@Composable
fun SoundUrlImportDialog(
    onDismiss: () -> Unit,
    onImported: (uri: String, name: String, measuredDb: Float?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!downloading) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("URL로 사운드 가져오기", style = MaterialTheme.typography.titleMedium)
                Text(
                    "오디오 파일 URL 또는 사운드 페이지 URL(myinstants 등)을 붙여넣으세요. 최대 20MB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("URL") },
                    placeholder = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !downloading
                )
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !downloading,
                        modifier = Modifier.weight(1f)
                    ) { Text("취소") }
                    Button(
                        onClick = {
                            downloading = true
                            error = null
                            scope.launch {
                                val result = SoundDownloader.downloadFromUrl(context, url)
                                downloading = false
                                if (result.fileUri != null) {
                                    onImported(result.fileUri, result.displayName ?: "사운드", result.measuredLoudnessDb)
                                } else {
                                    error = result.error ?: "알 수 없는 오류"
                                }
                            }
                        },
                        enabled = !downloading && url.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (downloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("다운로드")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 슬라이더 + 직접 입력 통합 컴포넌트.
 * - 헤더: 라벨 + 직접 입력 필드 + (선택) 랜덤 토글 + ▶/▼ 펼침 토글
 * - 펼친 상태에서만 슬라이더 노출
 * - displayMultiplier로 표시값 변환 (예: 0.5 → "50" + suffix "%")
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    isRandom: Boolean = false,
    onRandomChange: ((Boolean) -> Unit)? = null,
    suffix: String = "",
    displayMultiplier: Float = 1f,
    decimals: Int = 0
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = value * displayMultiplier
    var textState by remember(displayValue) {
        mutableStateOf(
            if (decimals == 0) displayValue.toInt().toString()
            else "%.${decimals}f".format(displayValue)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = textState,
                onValueChange = { txt ->
                    textState = txt
                    val n = txt.toFloatOrNull()
                    if (n != null) {
                        val raw = (n / displayMultiplier).coerceIn(range.start, range.endInclusive)
                        onValueChange(raw)
                    }
                },
                suffix = if (suffix.isNotEmpty()) { { Text(suffix, style = MaterialTheme.typography.bodySmall) } } else null,
                singleLine = true,
                enabled = !isRandom,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(if (suffix.length > 1) 110.dp else 92.dp)
            )

            if (onRandomChange != null) {
                Text("랜덤", style = MaterialTheme.typography.bodySmall)
                Switch(checked = isRandom, onCheckedChange = onRandomChange)
            }

            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(28.dp)
            ) {
                Text(if (expanded) "▼" else "▶", style = MaterialTheme.typography.bodySmall)
            }
        }
        AnimatedVisibility(visible = expanded) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                enabled = !isRandom,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

data class AppInfo(val label: String, val packageName: String)

// 앱 아이콘 로드 캐시 — 같은 앱 다시 로드 안 함
private val appIconCache = mutableMapOf<String, Drawable>()

// 앱 리스트 캐시 — userOnly 별로 라벨 포함 리스트 저장. 다이얼로그 재열기 시 재계산 방지.
private val appListCache = mutableMapOf<Boolean, List<AppInfo>>()

@Composable
fun LoadingDots(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resId = remember { context.resources.getIdentifier("loader", "raw", context.packageName) }
    if (resId == 0) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("로딩 중...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val composition by com.airbnb.lottie.compose.rememberLottieComposition(
        com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(resId)
    )
    val progress by com.airbnb.lottie.compose.animateLottieCompositionAsState(
        composition = composition,
        iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
        isPlaying = composition != null
    )
    com.airbnb.lottie.compose.LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
    )
}

@Composable
fun AsyncAppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf(appIconCache[packageName]) }

    LaunchedEffect(packageName) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.IO) {
                appIconCache[packageName] ?: try {
                    context.packageManager.getApplicationIcon(packageName)
                        .also { appIconCache[packageName] = it }
                } catch (_: Exception) { null }
            }
            icon = loaded
        }
    }

    val current = icon
    if (current != null) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            },
            update = { it.setImageDrawable(current) },
            modifier = modifier
        )
    } else {
        // 로딩 중 — 빈 자리만 차지 (레이아웃 흔들림 방지)
        Box(modifier = modifier)
    }
}

@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String, String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var userOnly by remember { mutableStateOf(true) }
    // 캐시에서 즉시 가져오거나, 없으면 비동기 로드 (라벨도 IO 스레드에서)
    var apps by remember(userOnly) { mutableStateOf(appListCache[userOnly] ?: emptyList()) }
    var loading by remember(userOnly) { mutableStateOf(appListCache[userOnly] == null) }

    LaunchedEffect(userOnly) {
        if (appListCache[userOnly] != null) return@LaunchedEffect
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            try {
                pm.getInstalledApplications(0)
                    .filter { info ->
                        if (!userOnly) return@filter true
                        val isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
                        val isUpdatedSystem = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                        !isSystem || isUpdatedSystem
                    }
                    .map { info ->
                        AppInfo(
                            label = info.loadLabel(pm).toString(),
                            packageName = info.packageName
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            } catch (e: Exception) {
                emptyList()
            }
        }
        appListCache[userOnly] = loaded
        apps = loaded
        loading = false
    }
    val filtered = if (query.isBlank()) apps
    else apps.filter { it.label.contains(query, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(500.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("앱 선택", style = MaterialTheme.typography.titleLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        "사용자 앱만 보기",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = userOnly,
                        onCheckedChange = { userOnly = it }
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("검색") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (loading && apps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingDots(modifier = Modifier.size(120.dp))
                    }
                } else LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(app.packageName, app.label) }
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            AsyncAppIcon(
                                packageName = app.packageName,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("닫기")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordListEditor(keywords: List<String>, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (keywords.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                keywords.forEach { kw ->
                    AssistChip(
                        onClick = { onChange(keywords - kw) },
                        label = { Text(kw) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "삭제",
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("키워드 추가") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    val trimmed = input.trim()
                    if (trimmed.isNotEmpty() && trimmed !in keywords) {
                        onChange(keywords + trimmed)
                    }
                    input = ""
                },
                enabled = input.trim().isNotEmpty()
            ) { Text("추가") }
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}

private fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

@Composable
fun PermissionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notifPerm by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var overlayPerm by remember { mutableStateOf(canDrawOverlays(context)) }

    // 사용자가 시스템 설정에 다녀온 후 자동 재확인
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifPerm = isNotificationListenerEnabled(context)
                overlayPerm = canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (notifPerm && overlayPerm) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("권한 설정이 필요합니다", style = MaterialTheme.typography.titleMedium)
            if (!notifPerm) {
                Text("• 알림 접근: 다른 앱의 알림을 감지하기 위해 필요", style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {}
                }) { Text("알림 접근 설정 열기") }
            }
            if (!overlayPerm) {
                Text("• 다른 앱 위에 표시: 미디어를 화면 위에 띄우기 위해 필요", style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {}
                }) { Text("오버레이 권한 설정 열기") }
            }
        }
    }
}