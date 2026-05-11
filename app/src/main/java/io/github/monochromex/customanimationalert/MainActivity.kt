package io.github.monochromex.customanimationalert

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
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.monochromex.customanimationalert.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
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
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
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
    var currentTab by remember { mutableStateOf<Tab>(Tab.Library) }

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
                    val tint = if (selected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
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
                                    color = MaterialTheme.colorScheme.onPrimary
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
            }
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
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("마켓", style = MaterialTheme.typography.headlineMedium)
            Text(
                "준비 중",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "다른 사용자가 만든 애니메이션을\n둘러보고 다운받을 수 있는 마켓이\n곧 열립니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SettingsTab(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("설정", style = MaterialTheme.typography.headlineSmall)

        PermissionBanner()

        HorizontalDivider()

        Text("앱 정보", style = MaterialTheme.typography.titleMedium)
        Text(
            "Custom Animation Alert · 버전 1.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            "추가 예정: 테마(라이트/다크), 계정, 디바운스 시간 조절 등",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    onRenameGroup: (String, String) -> Unit
) {
    // groupBy는 LinkedHashMap을 반환 → rules 추가 순서대로 그룹 유지. "기본"만 강제로 맨 앞.
    val rawGrouped = rules.groupBy { it.groupName.ifBlank { "기본" } }
    val grouped = buildMap {
        rawGrouped["기본"]?.let { put("기본", it) }
        rawGrouped.forEach { (k, v) -> if (k != "기본") put(k, v) }
    }
    var expandedGroups by remember { mutableStateOf(grouped.keys.toSet()) }
    var renamingGroup by remember { mutableStateOf<String?>(null) }

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
                    contentDescription = "새 규칙",
                    onClick = { onAdd("기본") },
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
                                        onToggleEnabled = { onToggleEnabled(rule.id, it) }
                                    )
                                }
                            }
                        }
                    }
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

private val PreviewBg = Color(0xFFE5DDD5)
private val ToggleOn = Color(0xFF22C55E)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RuleCard(
    rule: Rule,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            // 9:16 휴대폰 화면 미리보기 — 마블 물리 + 우상단 ⋯ 메뉴
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(PreviewBg)
            ) {
                MarblePreview(rule = rule)
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "메뉴",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("재생") },
                            onClick = {
                                menuExpanded = false
                                val intent = Intent(context, OverlayService::class.java)
                                intent.putExtra("ruleId", rule.id)
                                if (rule.mediaUri == null && rule.packageName != null) {
                                    intent.putExtra("sourcePackage", rule.packageName)
                                }
                                context.startService(intent)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("삭제") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
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
                .size(20.dp)
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
            // OFF 상태 — 빈 외곽선 원
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
    }
}

@Composable
fun MediaContent(rule: Rule, sizeDp: Dp, modifier: Modifier = Modifier) {
    when {
        rule.mediaUri != null && rule.mediaType == "image" -> {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
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
                modifier = modifier.size(sizeDp)
            )
        }
        rule.mediaUri != null && rule.mediaType == "video" -> {
            VideoThumbnail(uri = Uri.parse(rule.mediaUri), modifier = modifier.size(sizeDp))
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
            Box(modifier = modifier.size(sizeDp), contentAlignment = Alignment.Center) {
                Text(
                    "?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// 카드 안에서 마블 물리 시뮬레이션을 돌려 미디어가 실제 등장 애니메이션처럼 튕기도록 함.
// 한 번 시뮬 후 약 2.5초 쉬었다가 반복.
@Composable
fun MarblePreview(rule: Rule) {
    val density = LocalDensity.current
    val isAppIcon = rule.mediaUri == null && rule.packageName != null

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val boxWidthPx = with(density) { maxWidth.toPx() }
        val boxHeightPx = with(density) { maxHeight.toPx() }
        val cardWidthDp = maxWidth.value

        // 실제 폰 화면(360dp 기준) 대비 카드 폭에 비례하여 미디어 크기 축소.
        // 마블이 튕길 공간 확보 위해 카드 폭의 60% 이내로 캡.
        val phoneRefWidthDp = 360f
        val scaleFactor = cardWidthDp / phoneRefWidthDp
        val maxAllowedDp = cardWidthDp * 0.6f
        val sourceDp = remember(rule.id, rule.mediaSize, rule.mediaSizeRandom, rule.appIconSize, rule.appIconSizeRandom, isAppIcon) {
            if (isAppIcon)
                effectiveValue(rule.appIconSize, rule.appIconSizeRandom, 50f, 200f)
            else
                effectiveValue(rule.mediaSize, rule.mediaSizeRandom, 50f, 600f)
        }
        val sizeDp = (sourceDp * scaleFactor).coerceIn(10f, maxAllowedDp).dp
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

        LaunchedEffect(rule.id, boxWidthPx, boxHeightPx, sizePx) {
            delay(400)
            while (isActive) {
                runMarbleSim(
                    boxWidth = boxWidthPx,
                    boxHeight = boxHeightPx,
                    sizePx = sizePx,
                    densityFactor = density.density,
                    rule = rule
                ) { newX, newY, newRot ->
                    x = newX
                    y = newY
                    rotation = newRot
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
    val groundY = boxHeight - sizePx - 4f
    val maxX = boxWidth - sizePx
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
    // SAF 기반 폴백 피커. 사진 피커가 못 잡는 .mov 등 파일을 모든 폴더에서 선택 가능.
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
            val type = if (mime.startsWith("video/")) "video" else "image"
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "미디어"
            rule = rule.copy(mediaUri = uri.toString(), mediaType = type, mediaName = name)
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
            rule = rule.copy(soundUri = uri.toString(), soundName = name)
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
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

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
                    update = { it.setImageURI(Uri.parse(rule.mediaUri)) },
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
            }
            rule.mediaUri != null && rule.mediaType == "video" -> {
                Text("동영상: ${rule.mediaName ?: "이름 없음"}")
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
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) { Text("갤러리", style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = { mediaFilePicker.launch(arrayOf("image/*", "video/*")) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) { Text("파일", style = MaterialTheme.typography.bodySmall) }
            if (rule.mediaUri != null) {
                Button(
                    onClick = {
                        rule = rule.copy(mediaUri = null, mediaType = "image", mediaName = null)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
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

        SliderRow("미디어 크기: ${rule.mediaSize.toInt()}dp",
            rule.mediaSize, rule.mediaSizeRandom, 50f..600f,
            { rule = rule.copy(mediaSize = it) },
            { rule = rule.copy(mediaSizeRandom = it) })

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
            Button(onClick = { soundPicker.launch(arrayOf("audio/*")) }) { Text("사운드 선택") }
        }

        Text("음량: ${(rule.volume * 100).toInt()}%")
        Slider(
            value = rule.volume,
            onValueChange = { rule = rule.copy(volume = it) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
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
        if (rule.entryAnimation) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("구슬", style = MaterialTheme.typography.bodyMedium)
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

            SliderRow("앱 아이콘 크기: ${rule.appIconSize.toInt()}dp",
                rule.appIconSize, rule.appIconSizeRandom, 25f..200f,
                { rule = rule.copy(appIconSize = it) },
                { rule = rule.copy(appIconSizeRandom = it) })

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

            SliderRow("중력: ${"%.1f".format(rule.gravityScale)}x",
                rule.gravityScale, rule.gravityScaleRandom, 0.5f..2.5f,
                { rule = rule.copy(gravityScale = it) },
                { rule = rule.copy(gravityScaleRandom = it) })

            SliderRow("스핀 강도: ${"%.1f".format(rule.spinScale)}x",
                rule.spinScale, rule.spinScaleRandom, 0f..3f,
                { rule = rule.copy(spinScale = it) },
                { rule = rule.copy(spinScaleRandom = it) })

            SliderRow("탄성: ${"%.2f".format(rule.elasticity)}",
                rule.elasticity, rule.elasticityRandom, 0f..1f,
                { rule = rule.copy(elasticity = it) },
                { rule = rule.copy(elasticityRandom = it) })

            Text("바닥 위치: ${rule.floorOffset.toInt()}dp")
            Slider(
                value = rule.floorOffset,
                onValueChange = { rule = rule.copy(floorOffset = it) },
                valueRange = 0f..60f,
                modifier = Modifier.fillMaxWidth()
            )

            if (rule.entryAnimation) {
                HorizontalDivider()
                Text("애니메이션 상세설정", style = MaterialTheme.typography.bodyMedium)

                SliderRow("튕김 높이: ${(rule.bouncePeak * 100).toInt()}%",
                    rule.bouncePeak, rule.bouncePeakRandom, 0.3f..0.8f,
                    { rule = rule.copy(bouncePeak = it) },
                    { rule = rule.copy(bouncePeakRandom = it) })
            }

            HorizontalDivider()

            Text("알림 처리", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("애니메이션 중첩")
                Switch(checked = rule.stackOverlays, onCheckedChange = { rule = rule.copy(stackOverlays = it) })
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
                OutlinedTextField(
                    value = rule.sameContentCooldownSec.toString(),
                    onValueChange = {
                        val v = it.toIntOrNull()
                        if (v != null && v >= 0) rule = rule.copy(sameContentCooldownSec = v)
                        else if (it.isBlank()) rule = rule.copy(sameContentCooldownSec = 0)
                    },
                    suffix = { Text("초") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
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
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    isRandom: Boolean,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onRandomChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text("랜덤", style = MaterialTheme.typography.bodySmall)
        Switch(checked = isRandom, onCheckedChange = onRandomChange)
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        enabled = !isRandom,
        modifier = Modifier.fillMaxWidth()
    )
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