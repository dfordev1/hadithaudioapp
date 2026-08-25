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

private val Paper = Color(0xFFF7F4EC)
private val Ink = Color(0xFF181A16)
private val MutedInk = Color(0xFF686B62)
private val Olive = Color(0xFF626B55)
private val PaleOlive = Color(0xFFE8E9E0)
private val WarmStone = Color(0xFFEDE8DD)
private val Night = Color(0xFF1B1D19)
private val NightSurface = Color(0xFF292C26)
private val WarmWhite = Color(0xFFF4F3ED)
private val Bronze = Color(0xFFA97942)

private val LightColors = lightColorScheme(
    primary = Olive,
    onPrimary = Color.White,
    primaryContainer = PaleOlive,
    onPrimaryContainer = Ink,
    secondary = Bronze,
    onSecondary = Ink,
    secondaryContainer = WarmStone,
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaleOlive,
    onSurfaceVariant = MutedInk,
    outline = Color(0xFF85877D),
    outlineVariant = Color(0xFFD4D2C8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBBC5A9),
    onPrimary = Night,
    primaryContainer = Color(0xFF424A39),
    onPrimaryContainer = WarmWhite,
    secondary = Color(0xFFE0B16A),
    onSecondary = Night,
    secondaryContainer = Color(0xFF4B3A25),
    onSecondaryContainer = WarmWhite,
    background = Night,
    onBackground = WarmWhite,
    surface = NightSurface,
    onSurface = WarmWhite,
    surfaceVariant = Color(0xFF3D4438),
    onSurfaceVariant = Color(0xFFCBD0C4),
)

private val HadithTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 38.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
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
