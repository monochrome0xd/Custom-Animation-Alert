package io.github.monochrome0xd.customanimationalert.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 사용자가 설정에서 선택할 수 있는 테마 프리셋.
 * 각 테마는 라이트/다크 모드 둘 다 정의. 시스템 다크모드 토글 따라감.
 */
enum class AppTheme(val displayName: String) {
    NOTION_MONO("Notion Mono"),
    CAFE_CREAM("Café Cream"),
    FOREST("Forest"),
    LAVENDER("Lavender"),
    SUNSET("Sunset"),
    SAKURA("사쿠라");

    /** 미리보기용 대표 색 4개: primary, surface, tertiary(accent), outline */
    fun previewSwatches(): List<Color> = when (this) {
        NOTION_MONO -> listOf(Color(0xFF18181B), Color(0xFFFFFFFF), Color(0xFF3B82F6), Color(0xFFE4E4E7))
        CAFE_CREAM  -> listOf(Color(0xFF3D2E1F), Color(0xFFFFFCF4), Color(0xFFC9A86B), Color(0xFFD8C4A6))
        FOREST      -> listOf(Color(0xFF2D3E2D), Color(0xFFFFFFFF), Color(0xFF5C8F4E), Color(0xFFD6E3CF))
        LAVENDER    -> listOf(Color(0xFF4A3B5C), Color(0xFFFFFFFF), Color(0xFF8B5CF6), Color(0xFFDDD3F0))
        SUNSET      -> listOf(Color(0xFF6B3E2F), Color(0xFFFFFFFF), Color(0xFFF97316), Color(0xFFF2D5C2))
        SAKURA      -> listOf(Color(0xFFB94A6E), Color(0xFFFFF5F8), Color(0xFFF59AB8), Color(0xFFF5C9D8))
    }
}

fun AppTheme.lightScheme(): ColorScheme = when (this) {
    AppTheme.NOTION_MONO -> lightColorScheme(
        primary             = Color(0xFF18181B),
        onPrimary           = Color(0xFFFFFFFF),
        primaryContainer    = Color(0xFFF4F4F5),
        onPrimaryContainer  = Color(0xFF18181B),
        secondary           = Color(0xFF52525B),
        onSecondary         = Color(0xFFFFFFFF),
        secondaryContainer  = Color(0xFFF4F4F5),
        onSecondaryContainer = Color(0xFF27272A),
        tertiary            = Color(0xFF3B82F6),
        onTertiary          = Color(0xFFFFFFFF),
        tertiaryContainer   = Color(0xFFDBEAFE),
        onTertiaryContainer = Color(0xFF1E40AF),
        background          = Color(0xFFFAFAFA),
        onBackground        = Color(0xFF18181B),
        surface             = Color(0xFFFFFFFF),
        onSurface           = Color(0xFF18181B),
        surfaceVariant      = Color(0xFFF8F8F8),
        onSurfaceVariant    = Color(0xFF71717A),
        outline             = Color(0xFFE4E4E7),
        outlineVariant      = Color(0xFFF1F1F3),
        error               = Color(0xFFDC2626),
        onError             = Color(0xFFFFFFFF),
        errorContainer      = Color(0xFFFEF2F2),
        onErrorContainer    = Color(0xFF991B1B)
    )
    AppTheme.CAFE_CREAM -> lightColorScheme(
        primary             = Color(0xFF3D2E1F),
        onPrimary           = Color(0xFFFAF6EE),
        primaryContainer    = Color(0xFFEAD9C0),
        onPrimaryContainer  = Color(0xFF3D2E1F),
        secondary           = Color(0xFF8B7355),
        onSecondary         = Color(0xFFFAF6EE),
        secondaryContainer  = Color(0xFFEAD9C0),
        onSecondaryContainer = Color(0xFF3D2E1F),
        tertiary            = Color(0xFFC9A86B),
        onTertiary          = Color(0xFFFAF6EE),
        tertiaryContainer   = Color(0xFFF5E9CC),
        onTertiaryContainer = Color(0xFF7A5D2B),
        background          = Color(0xFFFAF6EE),
        onBackground        = Color(0xFF3D2E1F),
        surface             = Color(0xFFFFFCF4),
        onSurface           = Color(0xFF3D2E1F),
        surfaceVariant      = Color(0xFFF2EAD9),
        onSurfaceVariant    = Color(0xFF8B7355),
        outline             = Color(0xFFD8C4A6),
        outlineVariant      = Color(0xFFE8DDC9),
        error               = Color(0xFFB23A48),
        onError             = Color(0xFFFAF6EE),
        errorContainer      = Color(0xFFF9E3DD),
        onErrorContainer    = Color(0xFF7B2E2E)
    )
    AppTheme.FOREST -> lightColorScheme(
        primary             = Color(0xFF2D3E2D),
        onPrimary           = Color(0xFFF4F7F0),
        primaryContainer    = Color(0xFFD6E3CF),
        onPrimaryContainer  = Color(0xFF1F2D1F),
        secondary           = Color(0xFF5A6B58),
        onSecondary         = Color(0xFFFFFFFF),
        secondaryContainer  = Color(0xFFE0EAD9),
        onSecondaryContainer = Color(0xFF2D3E2D),
        tertiary            = Color(0xFF5C8F4E),
        onTertiary          = Color(0xFFFFFFFF),
        tertiaryContainer   = Color(0xFFD6EBD0),
        onTertiaryContainer = Color(0xFF2F5226),
        background          = Color(0xFFF4F7F0),
        onBackground        = Color(0xFF1F2D1F),
        surface             = Color(0xFFFFFFFF),
        onSurface           = Color(0xFF1F2D1F),
        surfaceVariant      = Color(0xFFE8EFE2),
        onSurfaceVariant    = Color(0xFF5A6B58),
        outline             = Color(0xFFC2D1B8),
        outlineVariant      = Color(0xFFDDE8D5),
        error               = Color(0xFFB23A48),
        onError             = Color(0xFFFFFFFF),
        errorContainer      = Color(0xFFFEE4E2),
        onErrorContainer    = Color(0xFF7B2E2E)
    )
    AppTheme.LAVENDER -> lightColorScheme(
        primary             = Color(0xFF4A3B5C),
        onPrimary           = Color(0xFFF8F4FF),
        primaryContainer    = Color(0xFFDDD3F0),
        onPrimaryContainer  = Color(0xFF3A2D49),
        secondary           = Color(0xFF6F5E82),
        onSecondary         = Color(0xFFFFFFFF),
        secondaryContainer  = Color(0xFFE8E0F2),
        onSecondaryContainer = Color(0xFF4A3B5C),
        tertiary            = Color(0xFF8B5CF6),
        onTertiary          = Color(0xFFFFFFFF),
        tertiaryContainer   = Color(0xFFEDE4FE),
        onTertiaryContainer = Color(0xFF5B21B6),
        background          = Color(0xFFF8F4FF),
        onBackground        = Color(0xFF3A2D49),
        surface             = Color(0xFFFFFFFF),
        onSurface           = Color(0xFF3A2D49),
        surfaceVariant      = Color(0xFFF0EAFA),
        onSurfaceVariant    = Color(0xFF6F5E82),
        outline             = Color(0xFFCFC2E0),
        outlineVariant      = Color(0xFFE5DDF0),
        error               = Color(0xFFDC2626),
        onError             = Color(0xFFFFFFFF),
        errorContainer      = Color(0xFFFEF2F2),
        onErrorContainer    = Color(0xFF991B1B)
    )
    AppTheme.SUNSET -> lightColorScheme(
        primary             = Color(0xFF6B3E2F),
        onPrimary           = Color(0xFFFFF4ED),
        primaryContainer    = Color(0xFFF2D5C2),
        onPrimaryContainer  = Color(0xFF4A2A20),
        secondary           = Color(0xFF8E6453),
        onSecondary         = Color(0xFFFFFFFF),
        secondaryContainer  = Color(0xFFF5DCC8),
        onSecondaryContainer = Color(0xFF6B3E2F),
        tertiary            = Color(0xFFF97316),
        onTertiary          = Color(0xFFFFFFFF),
        tertiaryContainer   = Color(0xFFFEE1CC),
        onTertiaryContainer = Color(0xFF9A3412),
        background          = Color(0xFFFFF4ED),
        onBackground        = Color(0xFF4A2A20),
        surface             = Color(0xFFFFFFFF),
        onSurface           = Color(0xFF4A2A20),
        surfaceVariant      = Color(0xFFFAE8DA),
        onSurfaceVariant    = Color(0xFF8E6453),
        outline             = Color(0xFFE5C5AC),
        outlineVariant      = Color(0xFFF2DEC9),
        error               = Color(0xFFB23A48),
        onError             = Color(0xFFFFFFFF),
        errorContainer      = Color(0xFFFCE4DC),
        onErrorContainer    = Color(0xFF7B2E2E)
    )
    AppTheme.SAKURA -> lightColorScheme(
        primary             = Color(0xFFB94A6E),
        onPrimary           = Color(0xFFFFF5F8),
        primaryContainer    = Color(0xFFFAD4E0),
        onPrimaryContainer  = Color(0xFF6E1F3A),
        secondary           = Color(0xFFC97A93),
        onSecondary         = Color(0xFFFFFFFF),
        secondaryContainer  = Color(0xFFFCE2EC),
        onSecondaryContainer = Color(0xFF6E1F3A),
        tertiary            = Color(0xFFF59AB8),
        onTertiary          = Color(0xFFFFFFFF),
        tertiaryContainer   = Color(0xFFFFDEE9),
        onTertiaryContainer = Color(0xFF8A2B4F),
        background          = Color(0xFFFFF5F8),
        onBackground        = Color(0xFF4A1F2E),
        surface             = Color(0xFFFFFAFC),
        onSurface           = Color(0xFF4A1F2E),
        surfaceVariant      = Color(0xFFFDEAF0),
        onSurfaceVariant    = Color(0xFF8E5A6C),
        outline             = Color(0xFFF5C9D8),
        outlineVariant      = Color(0xFFFAE0E8),
        error               = Color(0xFFB23A48),
        onError             = Color(0xFFFFFFFF),
        errorContainer      = Color(0xFFFCE4DC),
        onErrorContainer    = Color(0xFF7B2E2E)
    )
}

fun AppTheme.darkScheme(): ColorScheme = when (this) {
    AppTheme.NOTION_MONO -> darkColorScheme(
        primary             = Color(0xFFFAFAFA),
        onPrimary           = Color(0xFF09090B),
        primaryContainer    = Color(0xFF27272A),
        onPrimaryContainer  = Color(0xFFFAFAFA),
        secondary           = Color(0xFFA1A1AA),
        onSecondary         = Color(0xFF09090B),
        secondaryContainer  = Color(0xFF27272A),
        onSecondaryContainer = Color(0xFFE4E4E7),
        tertiary            = Color(0xFF60A5FA),
        onTertiary          = Color(0xFF09090B),
        tertiaryContainer   = Color(0xFF1E3A8A),
        onTertiaryContainer = Color(0xFFDBEAFE),
        background          = Color(0xFF09090B),
        onBackground        = Color(0xFFFAFAFA),
        surface             = Color(0xFF18181B),
        onSurface           = Color(0xFFFAFAFA),
        surfaceVariant      = Color(0xFF27272A),
        onSurfaceVariant    = Color(0xFFA1A1AA),
        outline             = Color(0xFF3F3F46),
        outlineVariant      = Color(0xFF27272A),
        error               = Color(0xFFEF4444),
        onError             = Color(0xFF09090B),
        errorContainer      = Color(0xFF450A0A),
        onErrorContainer    = Color(0xFFFCA5A5)
    )
    AppTheme.CAFE_CREAM -> darkColorScheme(
        primary             = Color(0xFFF2EAD9),
        onPrimary           = Color(0xFF1F1810),
        primaryContainer    = Color(0xFF3A2F22),
        onPrimaryContainer  = Color(0xFFE8DDC9),
        secondary           = Color(0xFFB8A180),
        onSecondary         = Color(0xFF1F1810),
        secondaryContainer  = Color(0xFF3A2F22),
        onSecondaryContainer = Color(0xFFE8DDC9),
        tertiary            = Color(0xFFD4B975),
        onTertiary          = Color(0xFF1F1810),
        tertiaryContainer   = Color(0xFF5C4733),
        onTertiaryContainer = Color(0xFFF5E9CC),
        background          = Color(0xFF1F1810),
        onBackground        = Color(0xFFF2EAD9),
        surface             = Color(0xFF2A2118),
        onSurface           = Color(0xFFF2EAD9),
        surfaceVariant      = Color(0xFF3A2F22),
        onSurfaceVariant    = Color(0xFFB8A180),
        outline             = Color(0xFF5C4733),
        outlineVariant      = Color(0xFF3A2F22),
        error               = Color(0xFFE07585),
        onError             = Color(0xFF1F1810),
        errorContainer      = Color(0xFF3D1F1F),
        onErrorContainer    = Color(0xFFF0A8A8)
    )
    AppTheme.FOREST -> darkColorScheme(
        primary             = Color(0xFFD6E3CF),
        onPrimary           = Color(0xFF1A2517),
        primaryContainer    = Color(0xFF2D3E2D),
        onPrimaryContainer  = Color(0xFFD6E3CF),
        secondary           = Color(0xFFA1B79B),
        onSecondary         = Color(0xFF1A2517),
        secondaryContainer  = Color(0xFF2D3E2D),
        onSecondaryContainer = Color(0xFFD6E3CF),
        tertiary            = Color(0xFF8AB47C),
        onTertiary          = Color(0xFF1A2517),
        tertiaryContainer   = Color(0xFF3A5C2F),
        onTertiaryContainer = Color(0xFFD6EBD0),
        background          = Color(0xFF14201A),
        onBackground        = Color(0xFFD6E3CF),
        surface             = Color(0xFF1F2D1F),
        onSurface           = Color(0xFFD6E3CF),
        surfaceVariant      = Color(0xFF2D3E2D),
        onSurfaceVariant    = Color(0xFFA1B79B),
        outline             = Color(0xFF4A5C46),
        outlineVariant      = Color(0xFF2D3E2D),
        error               = Color(0xFFE07585),
        onError             = Color(0xFF14201A),
        errorContainer      = Color(0xFF3D1F1F),
        onErrorContainer    = Color(0xFFF0A8A8)
    )
    AppTheme.LAVENDER -> darkColorScheme(
        primary             = Color(0xFFE0D4F0),
        onPrimary           = Color(0xFF1F1828),
        primaryContainer    = Color(0xFF3D3050),
        onPrimaryContainer  = Color(0xFFE0D4F0),
        secondary           = Color(0xFFB8A8D0),
        onSecondary         = Color(0xFF1F1828),
        secondaryContainer  = Color(0xFF3D3050),
        onSecondaryContainer = Color(0xFFE0D4F0),
        tertiary            = Color(0xFFA78BFA),
        onTertiary          = Color(0xFF1F1828),
        tertiaryContainer   = Color(0xFF4C1D95),
        onTertiaryContainer = Color(0xFFEDE4FE),
        background          = Color(0xFF161020),
        onBackground        = Color(0xFFE0D4F0),
        surface             = Color(0xFF1F1828),
        onSurface           = Color(0xFFE0D4F0),
        surfaceVariant      = Color(0xFF3D3050),
        onSurfaceVariant    = Color(0xFFB8A8D0),
        outline             = Color(0xFF564668),
        outlineVariant      = Color(0xFF3D3050),
        error               = Color(0xFFEF4444),
        onError             = Color(0xFF161020),
        errorContainer      = Color(0xFF450A0A),
        onErrorContainer    = Color(0xFFFCA5A5)
    )
    AppTheme.SUNSET -> darkColorScheme(
        primary             = Color(0xFFF5D5BC),
        onPrimary           = Color(0xFF2A1812),
        primaryContainer    = Color(0xFF4A2A20),
        onPrimaryContainer  = Color(0xFFF5D5BC),
        secondary           = Color(0xFFC9A18A),
        onSecondary         = Color(0xFF2A1812),
        secondaryContainer  = Color(0xFF4A2A20),
        onSecondaryContainer = Color(0xFFF5D5BC),
        tertiary            = Color(0xFFFB923C),
        onTertiary          = Color(0xFF2A1812),
        tertiaryContainer   = Color(0xFF7C2D12),
        onTertiaryContainer = Color(0xFFFEE1CC),
        background          = Color(0xFF20120D),
        onBackground        = Color(0xFFF5D5BC),
        surface             = Color(0xFF2A1812),
        onSurface           = Color(0xFFF5D5BC),
        surfaceVariant      = Color(0xFF4A2A20),
        onSurfaceVariant    = Color(0xFFC9A18A),
        outline             = Color(0xFF6B4A38),
        outlineVariant      = Color(0xFF4A2A20),
        error               = Color(0xFFE07585),
        onError             = Color(0xFF20120D),
        errorContainer      = Color(0xFF3D1F1F),
        onErrorContainer    = Color(0xFFF0A8A8)
    )
    AppTheme.SAKURA -> darkColorScheme(
        primary             = Color(0xFFFAD4E0),
        onPrimary           = Color(0xFF2A0F1A),
        primaryContainer    = Color(0xFF5C2941),
        onPrimaryContainer  = Color(0xFFFAD4E0),
        secondary           = Color(0xFFE0A6BC),
        onSecondary         = Color(0xFF2A0F1A),
        secondaryContainer  = Color(0xFF5C2941),
        onSecondaryContainer = Color(0xFFFAD4E0),
        tertiary            = Color(0xFFFAB8CC),
        onTertiary          = Color(0xFF2A0F1A),
        tertiaryContainer   = Color(0xFF7A2D4B),
        onTertiaryContainer = Color(0xFFFFDEE9),
        background          = Color(0xFF1F0D15),
        onBackground        = Color(0xFFFAD4E0),
        surface             = Color(0xFF2A0F1A),
        onSurface           = Color(0xFFFAD4E0),
        surfaceVariant      = Color(0xFF5C2941),
        onSurfaceVariant    = Color(0xFFE0A6BC),
        outline             = Color(0xFF7A3F58),
        outlineVariant      = Color(0xFF5C2941),
        error               = Color(0xFFE07585),
        onError             = Color(0xFF1F0D15),
        errorContainer      = Color(0xFF3D1F1F),
        onErrorContainer    = Color(0xFFF0A8A8)
    )
}

/** 라이트/다크 모드 강제 옵션 */
enum class ThemeMode(val displayName: String) {
    SYSTEM("시스템 설정"),
    LIGHT("라이트"),
    DARK("다크")
}

object ThemeStore {
    private const val PREFS = "theme_prefs"
    private const val KEY = "selected_theme"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_ONBOARDED = "theme_onboarded"
    private const val DEFAULT = "NOTION_MONO"

    var current by mutableStateOf(AppTheme.NOTION_MONO)
        private set

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** 첫 실행 시 false → 테마 선택 다이얼로그 표시. 한 번 선택하면 true. */
    var hasOnboarded by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY, DEFAULT) ?: DEFAULT
        current = runCatching { AppTheme.valueOf(saved) }.getOrDefault(AppTheme.NOTION_MONO)
        val savedMode = prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        mode = runCatching { ThemeMode.valueOf(savedMode) }.getOrDefault(ThemeMode.SYSTEM)
        hasOnboarded = prefs.getBoolean(KEY_ONBOARDED, false)
    }

    fun set(context: Context, theme: AppTheme) {
        current = theme
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, theme.name).apply()
    }

    fun setMode(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, newMode.name).apply()
    }

    fun markOnboarded(context: Context) {
        hasOnboarded = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ONBOARDED, true).apply()
    }
}
