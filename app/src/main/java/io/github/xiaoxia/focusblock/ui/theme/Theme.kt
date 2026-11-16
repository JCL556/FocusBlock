package io.github.xiaoxia.focusblock.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.xiaoxia.focusblock.data.ThemeMode

// ------------------------------------------------------------------ 色板

/** 浅色 */
private val LPageBg = Color(0xFFFFF8F5)
private val LCard = Color(0xFFFFFFFF)
private val LText = Color(0xFF3A3335)
private val LTextAux = Color(0xFF6E6769)
private val LOutline = Color(0xFFEADDD8)
private val LSoft = Color(0xFFF8EFEB)

/** 深色 */
private val DPageBg = Color(0xFF2B2734)
private val DCard = Color(0xFF383444)
private val DText = Color(0xFFFFFFFF)
private val DTextAux = Color(0xFFBBBBBB)
private val DOutline = Color(0xFF4E4859)
private val DSoft = Color(0xFF403B4E)

/**
 * 状态色。页面不许直接写色值，一律从这里取。
 * 马卡龙色只做色块/图标/描边，不承载白字。
 */
@Immutable
data class ToneColors(
    val focus: Color,
    val shortBreak: Color,
    val longBreak: Color,
    val warn: Color,
    val success: Color,
    val error: Color,
    /** 主按钮底色 */
    val primaryButton: Color,
    /** 主按钮文字色 */
    val onPrimaryButton: Color,
    /** 压在马卡龙色块上的文字色 */
    val onTone: Color,
    /** 矢量插画用的叶子绿 */
    val leaf: Color,
    /** 矢量插画用的柠檬黄 */
    val lemon: Color,
    /** 日历勾这类小图形的绿，比 success 深一档才压得住浅底 */
    val mark: Color,
)

private val LightTones = ToneColors(
    focus = Color(0xFFFF7A7A),
    shortBreak = Color(0xFF86E3CE),
    longBreak = Color(0xFF94C7FF),
    warn = Color(0xFFFFBC88),
    success = Color(0xFF74D385),
    error = Color(0xFFFF8686),
    primaryButton = Color(0xFFFF7A7A),
    onPrimaryButton = Color(0xFF3A3335),
    onTone = Color(0xFF3A3335),
    leaf = Color(0xFF5FBF6E),
    lemon = Color(0xFFF5D06B),
    mark = Color(0xFF4E9E5C),
)

private val DarkTones = ToneColors(
    focus = Color(0xFFD85C5C),
    shortBreak = Color(0xFF58A89F),
    longBreak = Color(0xFF5288C2),
    warn = Color(0xFFD48E58),
    success = Color(0xFF4A9958),
    error = Color(0xFFC95454),
    // 主题色本身在深色下配白字不够，主按钮单独用 #C24A4A（4.80:1）
    primaryButton = Color(0xFFC24A4A),
    onPrimaryButton = Color(0xFFFFFFFF),
    onTone = Color(0xFFFFFFFF),
    leaf = Color(0xFF4E9E5C),
    lemon = Color(0xFFD9B65A),
    mark = Color(0xFF5FBF6E),
)

val LocalTones = staticCompositionLocalOf { LightTones }

/** 补充语义色（背景 / 卡片 / 辅助文字），页面统一从这里取 */
@Immutable
data class ExtraColors(
    val pageBg: Color,
    val card: Color,
    val text: Color,
    val textAux: Color,
    val outline: Color,
    val soft: Color,
)

val LocalExtra = staticCompositionLocalOf {
    ExtraColors(LPageBg, LCard, LText, LTextAux, LOutline, LSoft)
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFFFF7A7A),
    onPrimary = Color(0xFF3A3335),
    primaryContainer = Color(0xFFFFE3E3),
    onPrimaryContainer = Color(0xFF3A3335),
    secondary = Color(0xFF86E3CE),
    onSecondary = Color(0xFF3A3335),
    background = LPageBg,
    onBackground = LText,
    surface = LCard,
    onSurface = LText,
    surfaceVariant = LSoft,
    onSurfaceVariant = LTextAux,
    outline = LOutline,
    outlineVariant = Color(0xFFF2E7E2),
    error = Color(0xFFFF8686),
    onError = LText,
    errorContainer = Color(0xFFFFE3E3),
    onErrorContainer = LText,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD85C5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4A3A3E),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF58A89F),
    onSecondary = Color(0xFFFFFFFF),
    background = DPageBg,
    onBackground = DText,
    surface = DCard,
    onSurface = DText,
    surfaceVariant = DSoft,
    onSurfaceVariant = DTextAux,
    outline = DOutline,
    outlineVariant = Color(0xFF454052),
    error = Color(0xFFC95454),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF4A3A3E),
    onErrorContainer = Color(0xFFFFFFFF),
)

// ------------------------------------------------------------------ 字体

val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

/** 倒计时数字：80sp ExtraBold，等宽，不内置中文字体只走系统等宽 */
val TimerTextStyle = TextStyle(
    fontSize = 80.sp,
    fontWeight = FontWeight.ExtraBold,
    fontFamily = FontFamily.Monospace,
    lineHeight = 88.sp,
)

/** 主页面标题行用的中等数字 */
val MonoSmall = TextStyle(
    fontSize = 15.sp,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
)

// ------------------------------------------------------------------ Theme

@Composable
fun FocusBlockTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val tones = if (dark) DarkTones else LightTones
    val extra = if (dark) {
        ExtraColors(DPageBg, DCard, DText, DTextAux, DOutline, DSoft)
    } else {
        ExtraColors(LPageBg, LCard, LText, LTextAux, LOutline, LSoft)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            val window = activity?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }

    CompositionLocalProvider(LocalTones provides tones, LocalExtra provides extra) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
