package to.hadith.audio

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val HadithJuniper = Color(0xFF10201C)
internal val HadithLime = Color(0xFFD5E78C)
internal val HadithChalk = Color(0xFFF4F6F0)
internal val HadithCopper = Color(0xFFC28A52)
internal val HadithSage = Color(0xFFE5EBE3)
internal val HadithMuted = Color(0xFF52615A)

private val LightLimeContainer = Color(0xFFEAF3C5)
private val LightCopperContainer = Color(0xFFF1D9BC)
private val DarkJuniperSurface = Color(0xFF1A3029)
private val DarkSage = Color(0xFF29453B)
private val DarkLime = Color(0xFFDCEB9D)
private val DarkCopper = Color(0xFFE5B77C)
private val DarkMuted = Color(0xFFB7C8BF)

private val LightColors = lightColorScheme(
    primary = HadithJuniper,
    onPrimary = HadithChalk,
    primaryContainer = HadithSage,
    onPrimaryContainer = HadithJuniper,
    secondary = HadithLime,
    onSecondary = HadithJuniper,
    secondaryContainer = LightLimeContainer,
    onSecondaryContainer = HadithJuniper,
    tertiary = HadithCopper,
    onTertiary = HadithJuniper,
    tertiaryContainer = LightCopperContainer,
    onTertiaryContainer = HadithJuniper,
    background = HadithChalk,
    onBackground = Color(0xFF0B110F),
    surface = HadithChalk,
    onSurface = Color(0xFF0B110F),
    surfaceVariant = HadithSage,
    onSurfaceVariant = HadithMuted,
    outline = Color(0xFF6E7C74),
    outlineVariant = Color(0xFFC8D2CB),
)

private val DarkColors = darkColorScheme(
    primary = DarkLime,
    onPrimary = HadithJuniper,
    primaryContainer = DarkSage,
    onPrimaryContainer = HadithChalk,
    secondary = HadithLime,
    onSecondary = HadithJuniper,
    secondaryContainer = DarkSage,
    onSecondaryContainer = HadithChalk,
    tertiary = DarkCopper,
    onTertiary = HadithJuniper,
    tertiaryContainer = Color(0xFF5B432B),
    onTertiaryContainer = HadithChalk,
    background = HadithJuniper,
    onBackground = HadithChalk,
    surface = DarkJuniperSurface,
    onSurface = HadithChalk,
    surfaceVariant = DarkSage,
    onSurfaceVariant = DarkMuted,
    outline = Color(0xFF8FA79A),
    outlineVariant = Color(0xFF49675A),
)

private val HadithTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        lineHeight = 28.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun HadithToTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HadithTypography,
        content = content,
    )
}
