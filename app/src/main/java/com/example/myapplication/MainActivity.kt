package com.example.myapplication

import android.content.Context
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
                    KeywordSettingScreen(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun KeywordSettingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("rules", Context.MODE_PRIVATE)
    }

    var keywordEnabled by remember { mutableStateOf(prefs.getBoolean("keywordEnabled", true)) }
    var keyword by remember { mutableStateOf(prefs.getString("keyword", "테스트") ?: "테스트") }
    var savedKeyword by remember { mutableStateOf(prefs.getString("keyword", "테스트") ?: "테스트") }
    var selectedPackage by remember { mutableStateOf(prefs.getString("packageName", null)) }
    var selectedAppLabel by remember { mutableStateOf(prefs.getString("appLabel", null)) }
    var showAppPicker by remember { mutableStateOf(false) }
    var mediaUri by remember {
        mutableStateOf((prefs.getString("mediaUri", null) ?: prefs.getString("imageUri", null))?.let { Uri.parse(it) })
    }
    var mediaType by remember { mutableStateOf(prefs.getString("mediaType", "image") ?: "image") }
    var mediaName by remember { mutableStateOf(prefs.getString("mediaName", null)) }
    var useVideoSound by remember { mutableStateOf(prefs.getBoolean("useVideoSound", true)) }
    var mediaSize by remember { mutableStateOf(prefs.getFloat("mediaSize", 250f)) }
    var mediaSizeRandom by remember { mutableStateOf(prefs.getBoolean("mediaSizeRandom", false)) }
    var appIconSize by remember { mutableStateOf(prefs.getFloat("appIconSize", 100f)) }
    var appIconSizeRandom by remember { mutableStateOf(prefs.getBoolean("appIconSizeRandom", false)) }
    var soundName by remember { mutableStateOf(prefs.getString("soundName", null)) }
    var volume by remember { mutableStateOf(prefs.getFloat("volume", 1.0f)) }
    var playInVibrate by remember { mutableStateOf(prefs.getBoolean("playInVibrate", false)) }
    var playInSilent by remember { mutableStateOf(prefs.getBoolean("playInSilent", false)) }
    var entryAnimation by remember { mutableStateOf(prefs.getBoolean("entryAnimation", true)) }
    var entryMode by remember { mutableStateOf(prefs.getString("entryMode", "spring") ?: "spring") }
    var bouncePeak by remember { mutableStateOf(prefs.getFloat("bouncePeak", 0.5f)) }
    var bouncePeakRandom by remember { mutableStateOf(prefs.getBoolean("bouncePeakRandom", false)) }
    var gravityScale by remember { mutableStateOf(prefs.getFloat("gravityScale", 1.0f)) }
    var gravityScaleRandom by remember { mutableStateOf(prefs.getBoolean("gravityScaleRandom", false)) }
    var spinScale by remember { mutableStateOf(prefs.getFloat("spinScale", 1.0f)) }
    var spinScaleRandom by remember { mutableStateOf(prefs.getBoolean("spinScaleRandom", false)) }
    var elasticity by remember { mutableStateOf(prefs.getFloat("elasticity", 0.5f)) }
    var elasticityRandom by remember { mutableStateOf(prefs.getBoolean("elasticityRandom", false)) }
    var dragEnabled by remember { mutableStateOf(prefs.getBoolean("dragEnabled", true)) }
    var flingToDismiss by remember { mutableStateOf(prefs.getBoolean("flingToDismiss", true)) }
    var tapToDismiss by remember { mutableStateOf(prefs.getBoolean("tapToDismiss", false)) }
    var stackOverlays by remember { mutableStateOf(prefs.getBoolean("stackOverlays", false)) }
    var showAdvanced by remember { mutableStateOf(prefs.getBoolean("showAdvanced", false)) }

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
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "미디어 파일"
            mediaUri = uri
            mediaType = type
            mediaName = name
            prefs.edit()
                .putString("mediaUri", uri.toString())
                .putString("mediaType", type)
                .putString("mediaName", name)
                .apply()
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
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "사운드 파일"
            soundName = name
            prefs.edit()
                .putString("soundUri", uri.toString())
                .putString("soundName", name)
                .apply()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Custom Animation Alert", style = MaterialTheme.typography.headlineMedium)

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("키워드 사용", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = keywordEnabled,
                onCheckedChange = {
                    keywordEnabled = it
                    prefs.edit().putBoolean("keywordEnabled", it).apply()
                }
            )
        }
        if (keywordEnabled) {
            Text("현재: \"$savedKeyword\"")
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("키워드 입력") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = {
                prefs.edit().putString("keyword", keyword).apply()
                savedKeyword = keyword
            }) { Text("키워드 저장") }
        }

        HorizontalDivider()

        Text("앱 선택", style = MaterialTheme.typography.titleMedium)
        Text(if (selectedAppLabel != null) "현재: $selectedAppLabel" else "선택 안 됨 (모든 앱)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAppPicker = true }) {
                Text(if (selectedPackage == null) "앱 선택" else "앱 변경")
            }
            if (selectedPackage != null) {
                Button(onClick = {
                    selectedPackage = null
                    selectedAppLabel = null
                    prefs.edit().remove("packageName").remove("appLabel").apply()
                }) { Text("선택 해제") }
            }
        }

        HorizontalDivider()

        Text("미디어", style = MaterialTheme.typography.titleMedium)
        when {
            mediaUri != null && mediaType == "image" -> {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                    },
                    update = { it.setImageURI(mediaUri) },
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
            }
            mediaUri != null && mediaType == "video" -> {
                Text("동영상: ${mediaName ?: "이름 없음"}")
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
            if (mediaUri != null) {
                Button(onClick = {
                    mediaUri = null
                    mediaType = "image"
                    mediaName = null
                    prefs.edit().remove("mediaUri").remove("mediaType").remove("mediaName").apply()
                }) { Text("미디어 해제") }
            }
        }

        if (mediaType == "video" && mediaUri != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("동영상 자체 사운드 사용")
                Switch(
                    checked = useVideoSound,
                    onCheckedChange = {
                        useVideoSound = it
                        prefs.edit().putBoolean("useVideoSound", it).apply()
                    }
                )
            }
        }

        HorizontalDivider()

        Text("사운드", style = MaterialTheme.typography.titleMedium)
        Text(if (soundName != null) "선택됨: $soundName" else "기본 알림음")
        Button(onClick = { soundPicker.launch(arrayOf("audio/*")) }) { Text("사운드 선택") }

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("등장 애니메이션", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = entryAnimation,
                onCheckedChange = {
                    entryAnimation = it
                    prefs.edit().putBoolean("entryAnimation", it).apply()
                }
            )
        }
        if (entryAnimation) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = entryMode == "spring",
                    onClick = {
                        entryMode = "spring"
                        prefs.edit().putString("entryMode", "spring").apply()
                    }
                )
                Text("스프링")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = entryMode == "marble",
                    onClick = {
                        entryMode = "marble"
                        prefs.edit().putString("entryMode", "marble").apply()
                    }
                )
                Text("구슬 (포물선 + 튕김 + 굴러감)")
            }
        }

        Button(onClick = {
            context.startService(Intent(context, OverlayService::class.java))
        }) { Text("테스트 재생") }

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showAdvanced = !showAdvanced
                    prefs.edit().putBoolean("showAdvanced", showAdvanced).apply()
                }
                .padding(vertical = 8.dp)
        ) {
            Text(
                "${if (showAdvanced) "▼" else "▶"} 상세 설정",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }

        if (showAdvanced) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("미디어 크기: ${mediaSize.toInt()}dp", modifier = Modifier.weight(1f))
                Text("랜덤", style = MaterialTheme.typography.bodySmall)
                Switch(checked = mediaSizeRandom, onCheckedChange = {
                    mediaSizeRandom = it
                    prefs.edit().putBoolean("mediaSizeRandom", it).apply()
                })
            }
            Slider(
                value = mediaSize,
                onValueChange = { mediaSize = it },
                onValueChangeFinished = {
                    prefs.edit().putFloat("mediaSize", mediaSize).apply()
                },
                valueRange = 50f..600f,
                enabled = !mediaSizeRandom,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("앱 아이콘 크기: ${appIconSize.toInt()}dp", modifier = Modifier.weight(1f))
                Text("랜덤", style = MaterialTheme.typography.bodySmall)
                Switch(checked = appIconSizeRandom, onCheckedChange = {
                    appIconSizeRandom = it
                    prefs.edit().putBoolean("appIconSizeRandom", it).apply()
                })
            }
            Slider(
                value = appIconSize,
                onValueChange = { appIconSize = it },
                onValueChangeFinished = {
                    prefs.edit().putFloat("appIconSize", appIconSize).apply()
                },
                valueRange = 50f..200f,
                enabled = !appIconSizeRandom,
                modifier = Modifier.fillMaxWidth()
            )

            Text("음량: ${(volume * 100).toInt()}%")
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = {
                    prefs.edit().putFloat("volume", volume).apply()
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("진동 모드에서도 재생")
                Switch(checked = playInVibrate, onCheckedChange = {
                    playInVibrate = it
                    prefs.edit().putBoolean("playInVibrate", it).apply()
                })
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("무음 모드에서도 재생")
                Switch(checked = playInSilent, onCheckedChange = {
                    playInSilent = it
                    prefs.edit().putBoolean("playInSilent", it).apply()
                })
            }

            if (entryAnimation && entryMode == "marble") {
                HorizontalDivider()
                Text("구슬 모드 세부", style = MaterialTheme.typography.bodyMedium)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("튕김 높이: ${(bouncePeak * 100).toInt()}%", modifier = Modifier.weight(1f))
                    Text("랜덤", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = bouncePeakRandom, onCheckedChange = {
                        bouncePeakRandom = it
                        prefs.edit().putBoolean("bouncePeakRandom", it).apply()
                    })
                }
                Slider(
                    value = bouncePeak, onValueChange = { bouncePeak = it },
                    onValueChangeFinished = { prefs.edit().putFloat("bouncePeak", bouncePeak).apply() },
                    valueRange = 0.3f..0.8f, enabled = !bouncePeakRandom,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("중력: ${"%.1f".format(gravityScale)}x", modifier = Modifier.weight(1f))
                    Text("랜덤", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = gravityScaleRandom, onCheckedChange = {
                        gravityScaleRandom = it
                        prefs.edit().putBoolean("gravityScaleRandom", it).apply()
                    })
                }
                Slider(
                    value = gravityScale, onValueChange = { gravityScale = it },
                    onValueChangeFinished = { prefs.edit().putFloat("gravityScale", gravityScale).apply() },
                    valueRange = 0.5f..2.5f, enabled = !gravityScaleRandom,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("스핀 강도: ${"%.1f".format(spinScale)}x", modifier = Modifier.weight(1f))
                    Text("랜덤", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = spinScaleRandom, onCheckedChange = {
                        spinScaleRandom = it
                        prefs.edit().putBoolean("spinScaleRandom", it).apply()
                    })
                }
                Slider(
                    value = spinScale, onValueChange = { spinScale = it },
                    onValueChangeFinished = { prefs.edit().putFloat("spinScale", spinScale).apply() },
                    valueRange = 0f..3f, enabled = !spinScaleRandom,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("탄성: ${"%.2f".format(elasticity)}", modifier = Modifier.weight(1f))
                    Text("랜덤", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = elasticityRandom, onCheckedChange = {
                        elasticityRandom = it
                        prefs.edit().putBoolean("elasticityRandom", it).apply()
                    })
                }
                Slider(
                    value = elasticity, onValueChange = { elasticity = it },
                    onValueChangeFinished = { prefs.edit().putFloat("elasticity", elasticity).apply() },
                    valueRange = 0f..1f, enabled = !elasticityRandom,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            Text("인터랙션", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("드래그로 이동")
                Switch(checked = dragEnabled, onCheckedChange = {
                    dragEnabled = it
                    prefs.edit().putBoolean("dragEnabled", it).apply()
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("던져서 닫기")
                Switch(checked = flingToDismiss, onCheckedChange = {
                    flingToDismiss = it
                    prefs.edit().putBoolean("flingToDismiss", it).apply()
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("탭해서 닫기")
                Switch(checked = tapToDismiss, onCheckedChange = {
                    tapToDismiss = it
                    prefs.edit().putBoolean("tapToDismiss", it).apply()
                })
            }

            HorizontalDivider()

            Text("알림 처리", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("알람 중첩 (이전 미디어 유지)")
                Switch(checked = stackOverlays, onCheckedChange = {
                    stackOverlays = it
                    prefs.edit().putBoolean("stackOverlays", it).apply()
                })
            }
            Text(
                "* OFF: 새 알람 오면 이전 미디어 사라짐 (기본)\n* ON: 여러 알람의 미디어가 동시에 화면에 떠있음 (최대 10개)",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelect = { pkg, label ->
                selectedPackage = pkg
                selectedAppLabel = label
                prefs.edit()
                    .putString("packageName", pkg)
                    .putString("appLabel", label)
                    .apply()
                showAppPicker = false
            }
        )
    }
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
            .map {
                AppInfo(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName
                )
            }
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
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)
                ) {
                    items(filtered) { app ->
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