package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    AppRoot(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

sealed class Screen {
    object RuleList : Screen()
    data class RuleEdit(val ruleId: String) : Screen()
}

@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.RuleList) }
    var rules by remember { mutableStateOf(RuleStore.loadAll(context)) }

    when (val screen = currentScreen) {
        is Screen.RuleList -> RuleListScreen(
            rules = rules,
            modifier = modifier,
            onAdd = {
                val newRule = Rule(name = "규칙 ${rules.size + 1}")
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
            }
        )
        is Screen.RuleEdit -> {
            val initial = rules.find { it.id == screen.ruleId }
            if (initial == null) {
                LaunchedEffect(Unit) { currentScreen = Screen.RuleList }
            } else {
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
fun RuleListScreen(
    rules: List<Rule>,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Custom Animation Alert",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onAdd) { Text("+ 새 규칙") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (rules.isEmpty()) {
            Text("아직 등록된 규칙이 없습니다.\n우측 상단 [+ 새 규칙] 버튼을 눌러 시작하세요.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rules, key = { it.id }) { rule ->
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

@Composable
fun RuleCard(
    rule: Rule,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = rule.enabled, onCheckedChange = onToggleEnabled)
            }
            val matchInfo = buildString {
                if (rule.keywordEnabled && rule.keyword.isNotBlank()) {
                    append("키워드 \"${rule.keyword}\"")
                }
                if (!rule.appLabel.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("앱 ${rule.appLabel}")
                }
                if (isEmpty()) append("(매칭 조건 없음)")
            }
            Text(matchInfo, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDelete) { Text("삭제") }
            }
        }
    }
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

    LaunchedEffect(rule) {
        if (isFirst) { isFirst = false; return@LaunchedEffect }
        onUpdate(rule)
    }

    val context = LocalContext.current
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 목록") }
        }

        OutlinedTextField(
            value = rule.name,
            onValueChange = { rule = rule.copy(name = it) },
            label = { Text("규칙 이름") },
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
            OutlinedTextField(
                value = rule.keyword,
                onValueChange = { rule = rule.copy(keyword = it) },
                label = { Text("키워드 입력") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                mediaPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            }) { Text("이미지 / 동영상 선택") }
            if (rule.mediaUri != null) {
                Button(onClick = {
                    rule = rule.copy(mediaUri = null, mediaType = "image", mediaName = null)
                }) { Text("미디어 해제") }
            }
        }

        if (rule.mediaType == "video" && rule.mediaUri != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("동영상 자체 사운드 사용")
                Switch(
                    checked = rule.useVideoSound,
                    onCheckedChange = { rule = rule.copy(useVideoSound = it) }
                )
            }
        }

        HorizontalDivider()

        Text("사운드", style = MaterialTheme.typography.titleMedium)
        Text(if (rule.soundName != null) "선택됨: ${rule.soundName}" else "기본 알림음")
        Button(onClick = { soundPicker.launch(arrayOf("audio/*")) }) { Text("사운드 선택") }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = rule.entryMode == "spring",
                    onClick = { rule = rule.copy(entryMode = "spring") }
                )
                Text("스프링")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = rule.entryMode == "marble",
                    onClick = { rule = rule.copy(entryMode = "marble") }
                )
                Text("구슬 (포물선 + 튕김 + 굴러감)")
            }
        }

        Button(onClick = {
            // 현재 rule 즉시 저장 후 테스트
            onUpdate(rule)
            val intent = Intent(context, OverlayService::class.java)
            intent.putExtra("ruleId", rule.id)
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
            SliderRow("미디어 크기: ${rule.mediaSize.toInt()}dp",
                rule.mediaSize, rule.mediaSizeRandom, 50f..600f,
                { rule = rule.copy(mediaSize = it) },
                { rule = rule.copy(mediaSizeRandom = it) })

            SliderRow("앱 아이콘 크기: ${rule.appIconSize.toInt()}dp",
                rule.appIconSize, rule.appIconSizeRandom, 50f..200f,
                { rule = rule.copy(appIconSize = it) },
                { rule = rule.copy(appIconSizeRandom = it) })

            Text("음량: ${(rule.volume * 100).toInt()}%")
            Slider(
                value = rule.volume,
                onValueChange = { rule = rule.copy(volume = it) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("진동 모드에서도 재생")
                Switch(checked = rule.playInVibrate, onCheckedChange = { rule = rule.copy(playInVibrate = it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("무음 모드에서도 재생")
                Switch(checked = rule.playInSilent, onCheckedChange = { rule = rule.copy(playInSilent = it) })
            }

            if (rule.entryAnimation && rule.entryMode == "marble") {
                HorizontalDivider()
                Text("구슬 모드 세부", style = MaterialTheme.typography.bodyMedium)

                SliderRow("튕김 높이: ${(rule.bouncePeak * 100).toInt()}%",
                    rule.bouncePeak, rule.bouncePeakRandom, 0.3f..0.8f,
                    { rule = rule.copy(bouncePeak = it) },
                    { rule = rule.copy(bouncePeakRandom = it) })

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

            HorizontalDivider()

            Text("알림 처리", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("알람 중첩 (이전 미디어 유지)")
                Switch(checked = rule.stackOverlays, onCheckedChange = { rule = rule.copy(stackOverlays = it) })
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelect = { pkg, label ->
                rule = rule.copy(packageName = pkg, appLabel = label)
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
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

@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String, String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { AppInfo(label = it.loadLabel(pm).toString(), packageName = it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("검색") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Text(
                            text = "${app.label}\n${app.packageName}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(app.packageName, app.label) }
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("닫기")
                }
            }
        }
    }
}