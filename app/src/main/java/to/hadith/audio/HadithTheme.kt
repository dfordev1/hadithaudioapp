package to.hadith.audio

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

internal val ReadingSerif = FontFamily(Font(R.font.source_serif_regular), Font(R.font.source_serif_semibold, FontWeight.SemiBold))
internal val ReadingArabic = FontFamily(Font(R.font.scheherazade_new))
internal val ReadingAmiri = FontFamily(Font(R.font.amiri))
internal val ReadingNaskh = FontFamily(Font(R.font.noto_naskh))
internal fun ArabicTypeface.family() = when (this) {
    ArabicTypeface.SCHEHERAZADE -> ReadingArabic
    ArabicTypeface.AMIRI -> ReadingAmiri
    ArabicTypeface.NASKH -> ReadingNaskh
}

private val Stone = lightColorScheme(
    primary = Color(0xFF7F6938), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DDCB), onPrimaryContainer = Color(0xFF473A20),
    secondary = Color(0xFF7F6938), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5DDCB), onSecondaryContainer = Color(0xFF473A20),
    tertiary = Color(0xFF7F6938), background = Color(0xFFEEF1F3),
    onBackground = Color(0xFF182026), surface = Color(0xFFEEF1F3),
    onSurface = Color(0xFF182026), surfaceVariant = Color(0xFFE4E8EB),
    onSurfaceVariant = Color(0xFF59636B), outline = Color(0xFF788187),
    outlineVariant = Color(0xFFD5DADD), surfaceContainer = Color(0xFFF7F8F9),
    surfaceContainerLow = Color(0xFFF3F5F6), surfaceContainerHigh = Color(0xFFE8ECEF),
)
private val Sepia = Stone.copy(
    background = Color(0xFFF3EDDF), surface = Color(0xFFF3EDDF),
    onBackground = Color(0xFF29261F), onSurface = Color(0xFF29261F),
    surfaceVariant = Color(0xFFE7DFCE), onSurfaceVariant = Color(0xFF665F51),
    outlineVariant = Color(0xFFD9D0BF), surfaceContainer = Color(0xFFFAF6EC),
    surfaceContainerLow = Color(0xFFF7F1E5), surfaceContainerHigh = Color(0xFFECE4D4),
)
private val Night = darkColorScheme(
    primary = Color(0xFFC3AA73), onPrimary = Color(0xFF292317),
    primaryContainer = Color(0xFF403827), onPrimaryContainer = Color(0xFFE9D8AF),
    secondary = Color(0xFFC3AA73), onSecondary = Color(0xFF292317),
    secondaryContainer = Color(0xFF403827), onSecondaryContainer = Color(0xFFE9D8AF),
    tertiary = Color(0xFFC3AA73), background = Color(0xFF171C20),
    onBackground = Color(0xFFE8EBEC), surface = Color(0xFF171C20),
    onSurface = Color(0xFFE8EBEC), surfaceVariant = Color(0xFF252D33),
    onSurfaceVariant = Color(0xFFB0B8BE), outline = Color(0xFF89949C),
    outlineVariant = Color(0xFF354047), surfaceContainer = Color(0xFF20272C),
    surfaceContainerLow = Color(0xFF1B2227), surfaceContainerHigh = Color(0xFF2A333A),
)

private val ReadingTypography = Typography(
    displaySmall = TextStyle(fontFamily = ReadingSerif, fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontFamily = ReadingSerif, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-.7).sp),
    headlineMedium = TextStyle(fontFamily = ReadingSerif, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-.4).sp),
    headlineSmall = TextStyle(fontFamily = ReadingSerif, fontSize = 25.sp, lineHeight = 33.sp),
    titleLarge = TextStyle(fontFamily = ReadingSerif, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = ReadingSerif, fontSize = 19.sp, lineHeight = 27.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = ReadingSerif, fontSize = 18.sp, lineHeight = 29.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 1.sp),
)

@Composable
fun HadithToTheme(appearance: ReadingAppearance = ReadingAppearance.LIGHT, content: @Composable () -> Unit) {
    val view = LocalView.current
    val dark = appearance == ReadingAppearance.DARK
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = when (appearance) {
        ReadingAppearance.LIGHT -> Stone; ReadingAppearance.SEPIA -> Sepia; ReadingAppearance.DARK -> Night
    }, typography = ReadingTypography, content = content)
}
