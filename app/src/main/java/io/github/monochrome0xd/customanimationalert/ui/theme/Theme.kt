package io.github.monochrome0xd.customanimationalert.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // dynamicColor(시스템 배경화면 색 자동 적용)는 일관된 디자인을 위해 사용 안 함.
    // 사용자가 설정에서 고른 테마(ThemeStore.current) + 라이트/다크 모드(ThemeStore.mode)에 따라 ColorScheme이 바뀜.
    val theme = ThemeStore.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (ThemeStore.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val colorScheme = if (darkTheme) theme.darkScheme() else theme.lightScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        // 윈도우 전체를 테마 배경색으로 채움 (XML windowBackground 위에 덮어그림).
        // 시스템 다크모드 진입 시 흰 배경이 뚫고 보이는 문제 방지.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background,
            content = content
        )
    }
}
