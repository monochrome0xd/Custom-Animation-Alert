package io.github.monochromex.customanimationalert.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // dynamicColor(시스템 배경화면 색 자동 적용)는 일관된 디자인을 위해 사용 안 함.
    // 사용자가 설정에서 고른 테마(ThemeStore.current)에 따라 ColorScheme이 바뀜.
    val theme = ThemeStore.current
    val colorScheme = if (darkTheme) theme.darkScheme() else theme.lightScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
