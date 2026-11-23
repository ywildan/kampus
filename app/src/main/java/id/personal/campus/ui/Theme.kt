package id.personal.campus.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.personal.campus.R

/*
 * Token warna sistem desain Karsa (dari replika web / asset-karsa), dipakai ulang untuk Kampus.
 * Kertas hangat, tinta cokelat tua, Dock espresso, dan oranye brand.
 */

// Kertas & garis
val Paper = Color(0xFFFBF5EC)
val Paper2 = Color(0xFFF5ECE0)
val Paper3 = Color(0xFFEBDFCD)
val Line = Color(0xFFEBE0D1)

// Tinta
val Ink = Color(0xFF26180F)
val Ink2 = Color(0xFF5A483B)
val Muted = Color(0xFF8B7B6D)
val Espresso = Color(0xFF2A1A10)
val Espresso2 = Color(0xFF3B261A)

// Brand oranye (50–800)
val Brand50 = Color(0xFFFFF5EC)
val Brand100 = Color(0xFFFFE6D0)
val Brand200 = Color(0xFFFFCB9C)
val Brand300 = Color(0xFFFFA762)
val Brand400 = Color(0xFFF88A2E)
val Brand500 = Color(0xFFE36B0F)
val Brand600 = Color(0xFFC75A08)
val Brand700 = Color(0xFF8F4208)
val Brand800 = Color(0xFF5F2C06)

// Warna status
val Butter100 = Color(0xFFFDF0C4)
val Butter400 = Color(0xFFF7C948)
val Butter700 = Color(0xFF7A5A00)
val Leaf100 = Color(0xFFDCF2E4)
val Leaf500 = Color(0xFF1F7A4D)
val Leaf600 = Color(0xFF17623D)
val Danger100 = Color(0xFFFBE2DF)
val Danger500 = Color(0xFFC1352B)
val Tide100 = Color(0xFFDCEBF4)
val Tide600 = Color(0xFF2A6385)
val Plum100 = Color(0xFFF1DCEB)
val Plum600 = Color(0xFF7A3E6B)

// ───────── Tipografi: keluarga font brand (asset-karsa) ─────────

/** Bricolage Grotesque — judul, angka besar, nama aplikasi. */
val Display = FontFamily(
    Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
)

/** Plus Jakarta Sans — isi teks, label, tombol, dan meta. */
val Sans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

/** Angka jam: sans tebal dengan lebar angka tetap supaya tidak "bergetar" tiap detik. */
val TimeStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum")

/** Kicker huruf kapital, tracking 0.16em (Eyebrow 11/700 di asset-karsa). */
val EyebrowStyle = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 1.7.sp,
)

/** Label kecil untuk chip, badge, dan tombol Sm. */
val ChipStyle = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 16.sp,
)

private val CampusColors = lightColorScheme(
    primary = Brand500,
    onPrimary = Color.White,
    primaryContainer = Brand100,
    onPrimaryContainer = Brand800,
    secondary = Espresso,
    onSecondary = Color.White,
    secondaryContainer = Brand100,
    onSecondaryContainer = Brand800,
    tertiary = Leaf500,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Paper2,
    onSurfaceVariant = Muted,
    surfaceContainer = Paper2,
    surfaceContainerHigh = Paper2,
    surfaceContainerHighest = Paper3,
    outline = Color(0xFFD8C8B3),
    outlineVariant = Line,
    error = Danger500,
    onError = Color.White,
    errorContainer = Danger100,
    onErrorContainer = Danger500,
    surfaceTint = Color.Transparent,
)

private val BaseTypography = Typography()

/*
 * Skala tipe mengikuti TYPE_SCALE di asset-karsa:
 * Display 40/34 lewat Bricolage ExtraBold dengan tracking negatif, judul kartu 20/19,
 * isi 15, label 13, meta 12, eyebrow 11 kapital bertracking lebar.
 */
private val CampusTypography = BaseTypography.copy(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.1).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-0.9).sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp),
    labelSmall = EyebrowStyle,
)

/* Radius dari asset-karsa: Field 16, Button Lg 20, Pop 24, Card 28, Sheet 32, Pill 9999. */
val FieldShape = RoundedCornerShape(16.dp)
val ButtonShape = RoundedCornerShape(16.dp)
val ButtonLargeShape = RoundedCornerShape(20.dp)
val PopShape = RoundedCornerShape(24.dp)
val SheetShape = RoundedCornerShape(32.dp)
val PillShape = CircleShape

@Composable
fun CampusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CampusColors,
        typography = CampusTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(14.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
