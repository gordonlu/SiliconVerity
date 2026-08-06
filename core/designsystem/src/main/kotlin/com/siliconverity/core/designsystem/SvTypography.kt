package com.siliconverity.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SvTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontSize = 58.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 56.sp),
        displayMedium = displayMedium.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 15.sp),
        bodyMedium = bodyMedium.copy(fontSize = 13.sp),
        bodySmall = bodySmall.copy(fontSize = 11.sp),
        labelLarge = labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelMedium = labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
    )
}

val SvMono: TextStyle = TextStyle(
    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
)
