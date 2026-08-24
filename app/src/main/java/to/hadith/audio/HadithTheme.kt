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

private val Paper = Color(0xFFF8F7F3)
private val Ink = Color(0xFF24251F)
private val MutedInk = Color(0xFF686960)
private val Olive = Color(0xFF59624B)
private val PaleOlive = Color(0xFFE4E8DD)
private val Night = Color(0xFF1B1D19)
private val NightSurface = Color(0xFF292C26)
private val WarmWhite = Color(0xFFF4F3ED)
private val Amber = Color(0xFFC28A3A)

private val LightColors = lightColorScheme(
    primary = Olive,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaleOlive,
    onSurfaceVariant = MutedInk,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBBC5A9),
    onPrimary = Night,
    secondary = Color(0xFFE0B16A),
    onSecondary = Night,
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
