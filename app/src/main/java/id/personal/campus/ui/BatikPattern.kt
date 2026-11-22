package id.personal.campus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Motif batik Truntum (acuan: asset-karsa/src/lib/brand.ts).
 * Bintang 8 sudut: 16 titik, jari-jari 10 (di sumbu) / 6.4 / 2.6 (di diagonal),
 * disusun selang-seling dalam ubin 44×44 dengan 8 titik pengisi.
 * Menggantikan motif Kawung (4 elips) yang lama.
 */

/** Ukuran satu ubin Truntum dalam satuan ubin (juga dp pada skala 1:1). */
const val TruntumTile = 44f

/** Posisi bintang dalam satu ubin: pusat, lalu empat sudut (dipakai bersama oleh ubin tetangga). */
val TruntumStars: List<Offset> = listOf(
    Offset(22f, 22f),
    Offset(0f, 0f),
    Offset(44f, 0f),
    Offset(0f, 44f),
    Offset(44f, 44f),
)

/** Delapan titik pengisi dalam satu ubin. */
val TruntumDots: List<Offset> = listOf(
    Offset(11f, 11f),
    Offset(33f, 11f),
    Offset(11f, 33f),
    Offset(33f, 33f),
    Offset(22f, 5f),
    Offset(22f, 39f),
    Offset(5f, 22f),
    Offset(39f, 22f),
)

private const val StarOuter = 10f
private const val StarMid = 6.4f
private const val StarInner = 2.6f

/** Titik-titik poligon bintang 8 sudut (4 sudut panjang di sumbu, 4 sudut pendek di diagonal). */
fun truntumStarPoints(cx: Float, cy: Float): List<Offset> = List(16) { i ->
    val angle = i * PI / 8 - PI / 2
    val r = when {
        i % 4 == 0 -> StarOuter
        i % 2 == 0 -> StarMid
        else -> StarInner
    }
    Offset(cx + (r * cos(angle)).toFloat(), cy + (r * sin(angle)).toFloat())
}

private fun starPolygon(path: Path, cx: Float, cy: Float) {
    val pts = truntumStarPoints(cx, cy)
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
    path.close()
}

private fun dot(path: Path, c: Offset, r: Float) {
    path.addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r))
}

private class TruntumPaths {
    // Satu ubin: pusat + bintang di pojok (0,0). Pojok lain digambar oleh ubin tetangga,
    // jadi tiap bintang pojok hanya tergambar sekali (tidak menebal saat bertumpuk).
    val outline = Path().also { starPolygon(it, 22f, 22f); starPolygon(it, 0f, 0f) }
    val starCenters = Path().also { dot(it, TruntumStars[0], 1.4f); dot(it, TruntumStars[1], 1.4f) }
    val dots = Path().also { p -> TruntumDots.forEach { dot(p, it, 1.1f) } }
}

/**
 * Overlay motif Truntum yang berulang tiap [tileSize] (44 dp). Tempatkan di dalam Box
 * dengan `Modifier.matchParentSize()` (atau biarkan default `fillMaxSize()`), di bawah konten.
 * Pada latar gelap/oranye pakai [color] putih; pada kertas pakai oranye atau espresso.
 */
@Composable
fun BatikPattern(
    modifier: Modifier = Modifier.fillMaxSize(),
    color: Color = Color.White,
    alpha: Float = 0.12f,
    tileSize: Dp = 44.dp,
) {
    val paths = remember { TruntumPaths() }
    Canvas(modifier) {
        val tile = tileSize.toPx()
        if (tile <= 0f) return@Canvas
        val scale = tile / TruntumTile
        val strokeColor = color.copy(alpha = alpha.coerceIn(0f, 1f))
        val centerColor = color.copy(alpha = min(1f, alpha * 1.4f))
        val dotColor = color.copy(alpha = min(1f, alpha * 1.2f))
        val stroke = Stroke(width = 1.2f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        val cols = ceil(size.width / tile).toInt()
        val rows = ceil(size.height / tile).toInt()
        // +1 supaya bintang di tepi kanan/bawah ikut tergambar penuh.
        for (col in 0..cols) for (row in 0..rows) {
            withTransform({
                translate(col * tile, row * tile)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                drawPath(paths.outline, strokeColor, style = stroke)
                drawPath(paths.starCenters, centerColor, style = Fill)
                drawPath(paths.dots, dotColor, style = Fill)
            }
        }
    }
}

/**
 * Logo Kampus: bintang Truntum di atas ubin membulat (menggantikan logo 4 kelopak Kawung).
 * Skala mengikuti kanvas 48×48 di replika web.
 */
@Composable
fun TruntumMark(
    size: Dp = 40.dp,
    tile: Color? = Brand500,
    ink: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    val hole = tile ?: Color.Transparent
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 48f
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            if (tile != null) drawRoundRect(tile, Offset.Zero, Size(48f, 48f), CornerRadius(15f, 15f))
            val star = Path().also { starPolygon(it, 24f, 24f) }
            withTransform({ scale(1.9f, 1.9f, pivot = Offset(24f, 24f)) }) {
                drawPath(star, ink, style = Fill)
                drawPath(star, ink, style = Stroke(width = 0.9f, join = StrokeJoin.Round))
            }
            drawCircle(hole, radius = 2.6f, center = Offset(24f, 24f))
        }
    }
}
