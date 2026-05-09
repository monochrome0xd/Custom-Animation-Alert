package io.github.monochromex.customanimationalert.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary             = InkPrimary,
    onPrimary           = InkOnPrimary,
    primaryContainer    = InkSecondaryBg,
    onPrimaryContainer  = InkPrimary,

    secondary           = InkSecondary,
    onSecondary         = InkOnPrimary,
    secondaryContainer  = InkSecondaryBg,
    onSecondaryContainer = InkOnSecondaryBg,

    tertiary            = AccentBlue,
    onTertiary          = InkOnPrimary,
    tertiaryContainer   = AccentBlueBg,
    onTertiaryContainer = AccentBlueOnBg,

    background          = Background,
    onBackground        = OnBackground,
    surface             = Surface,
    onSurface           = OnBackground,
    surfaceVariant      = SurfaceVariant,
    onSurfaceVariant    = OnSurfaceVariant,

    outline             = Outline,
    outlineVariant      = OutlineVariant,

    error               = Danger,
    onError             = InkOnPrimary,
    errorContainer      = DangerBg,
    onErrorContainer    = DangerOnBg
)

private val DarkColorScheme = darkColorScheme(
    primary             = DarkInkPrimary,
    onPrimary           = DarkInkOnPrimary,
    primaryContainer    = DarkInkSecondaryBg,
    onPrimaryContainer  = DarkInkPrimary,

    secondary           = DarkOnSurfaceVariant,
    onSecondary         = DarkInkOnPrimary,
    secondaryContainer  = DarkInkSecondaryBg,
    onSecondaryContainer = DarkInkOnSecondaryBg,

    tertiary            = AccentBlue,
    onTertiary          = DarkInkOnPrimary,

    background          = DarkBackground,
    onBackground        = DarkOnBackground,
    surface             = DarkSurface,
    onSurface           = DarkOnBackground,
    surfaceVariant      = DarkSurfaceVariant,
    onSurfaceVariant    = DarkOnSurfaceVariant,

    outline             = DarkOutline,
    outlineVariant      = DarkOutlineVariant,

    error               = Danger,
    onError             = DarkInkOnPrimary,
    errorContainer      = DarkDangerBg,
    onErrorContainer    = DarkDangerOnBg
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // dynamicColor(시스템 배경화면 색 자동 적용)는 일관된 디자인을 위해 사용 안 함
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
