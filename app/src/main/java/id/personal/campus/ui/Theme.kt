package id.personal.campus.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Paper = Color(0xFFF6F4EF)
val Ink = Color(0xFF14161A)
val Rule = Color(0xFFD9D4C7)
val CampusGreen = Color(0xFF2F6B4F)
val MutedInk = Color(0xFF5E625C)
val TimeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")

private val CampusColors = lightColorScheme(
    primary = CampusGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EBE0),
    onPrimaryContainer = Ink,
    secondary = CampusGreen,
    secondaryContainer = Color(0xFFE3EBE0),
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEEBE2),
    onSurfaceVariant = MutedInk,
    outline = MutedInk,
    outlineVariant = Rule,
    error = Color(0xFF9B302C),
    onError = Color.White,
    errorContainer = Color(0xFFF7E5DF),
    onErrorContainer = Color(0xFF68241F),
    surfaceTint = Color.Transparent,
)

private val BaseTypography = Typography()
private val CampusTypography = BaseTypography.copy(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 39.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 35.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp, lineHeight = 31.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 22.sp, lineHeight = 29.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
)

@Composable
fun CampusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CampusColors,
        typography = CampusTypography,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
        ),
        content = content,
    )
}
