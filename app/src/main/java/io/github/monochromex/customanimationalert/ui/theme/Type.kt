package io.github.monochromex.customanimationalert.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Sans = FontFamily.Default

val Typography = Typography(
    headlineLarge   = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold,     fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium  = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineSmall   = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),

    titleLarge      = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium     = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium,   fontSize = 15.sp, lineHeight = 22.sp),
    titleSmall      = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium,   fontSize = 13.sp, lineHeight = 18.sp),

    bodyLarge       = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal,   fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium      = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall       = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),

    labelLarge      = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium     = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall      = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp)
)
