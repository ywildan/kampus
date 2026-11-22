package id.personal.campus.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Satu tujuan di Dock. */
data class DockItem(val label: String, val icon: ImageVector)

/**
 * Dock navigasi Karsa.
 *
 * Pil espresso 82 dp dengan empat tab biasa dan satu tombol tengah (Asisten) yang menonjol.
 * Semua perpindahan tab memakai indikator pil oranye yang sama: posisinya meluncur dengan pegas
 * dan lebarnya mekar sedikit, sehingga mata mengikuti satu objek — bukan lima pil yang berkedip.
 * Sentuhan memberi umpan balik haptic ringan; ikon aktif naik 1 dp dan membesar 1,06×.
 */
@Composable
fun KarsaDock(
    items: List<DockItem>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    centerIndex: Int = 2,
) {
    val tap = rememberKarsaTap()
    // Indeks yang sedang "bernyawa" — dipakai indikator geser. Tertinggal satu frame supaya
    // pil lama sudah pudar saat pil baru tumbuh.
    var indicatorIndex by remember { mutableIntStateOf(selected) }
    LaunchedEffect(selected) { indicatorIndex = selected }

    Row(
        modifier
            .fillMaxWidth()
            .popShadow(RoundedCornerShape(41.dp))
            .clip(RoundedCornerShape(41.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Espresso2, Espresso, Espresso),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(41.dp))
            .height(82.dp)
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEachIndexed { index, item ->
            val on = selected == index
            val isCenter = index == centerIndex
            val interaction = remember(index) { MutableInteractionSource() }
            val scale by pressScale(interaction, KarsaMotion.TapScale)

            val labelColor by animateColorAsState(
                if (on) Color.White else Color.White.copy(alpha = 0.52f),
                tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
                label = "dockLabel",
            )

            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .selectable(
                        selected = on,
                        enabled = enabled,
                        role = Role.Tab,
                        interactionSource = interaction,
                        // Ripple oranye lembut di dalam pil yang meluncur.
                        indication = androidx.compose.material3.ripple(color = Color.White.copy(alpha = 0.16f), bounded = true),
                        onClick = {
                            if (!on) tap()
                            onSelect(index)
                        },
                    )
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (isCenter) {
                    CenterSlot(item, on, scale)
                } else {
                    SideSlot(item.icon, on, scale)
                }
                DockLabel(item.label, labelColor, if (isCenter) 2.dp else 4.dp)
            }
        }
    }
}

/** Tab biasa: pil oranye yang mekar dari 34 → 56 dp saat aktif. */
@Composable
private fun SideSlot(icon: ImageVector, on: Boolean, pressScale: Float) {
    val pillWidth by animateDpAsState(
        if (on) 56.dp else 30.dp,
        spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "dockPillWidth",
    )
    val pillAlpha by animateFloatAsState(
        if (on) 1f else 0f,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "dockPillAlpha",
    )
    val iconLift by animateDpAsState(
        if (on) (-1).dp else 0.dp,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "dockIconLift",
    )
    val iconScale by animateFloatAsState(
        if (on) 1.06f else 1f,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "dockIconScale",
    )
    Box(
        Modifier
            .widthIn(min = 56.dp)
            .height(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(pillWidth)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brand500.copy(alpha = pillAlpha))
                .then(
                    if (on) Modifier.brandShadow(RoundedCornerShape(18.dp))
                    else Modifier,
                ),
        )
        Icon(
            icon,
            contentDescription = null,
            tint = if (on) Color.White else Color.White.copy(alpha = 0.55f),
            modifier = Modifier
                .size(22.dp)
                .offset(y = iconLift)
                .graphicsLayer {
                    scaleX = iconScale * pressScale
                    scaleY = iconScale * pressScale
                },
        )
    }
}

/** Tombol tengah (Asisten): lingkaran oranye 60 dp dengan cincin kertas dan pendar lembut. */
@Composable
private fun CenterSlot(item: DockItem, on: Boolean, pressScale: Float) {
    val fill by animateColorAsState(
        if (on) Brand400 else Brand500,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "dockCenter",
    )
    val ringColor by animateColorAsState(
        if (on) Paper else Paper.copy(alpha = 0.85f),
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "dockRing",
    )
    val glow by animateFloatAsState(
        if (on) 0.55f else 0.28f,
        tween(KarsaMotion.Medium, easing = KarsaMotion.Standard),
        label = "dockGlow",
    )
    val iconSpin by animateFloatAsState(
        if (on) 8f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "dockIconRotate",
    )
    Box(contentAlignment = Alignment.Center) {
        // Pendar oranye yang mengembang lembut saat Asisten aktif.
        Box(
            Modifier
                .size(92.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Brand500.copy(alpha = 0.42f * glow), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .size(60.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .shadow(12.dp, CircleShape, ambientColor = Brand500.copy(alpha = 0.5f), spotColor = Brand500.copy(alpha = 0.7f))
                .clip(CircleShape)
                .background(fill)
                .border(4.dp, ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = iconSpin },
            )
        }
    }
}

@Composable
private fun DockLabel(text: String, color: Color, topPadding: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        Modifier.padding(top = topPadding),
        color = color,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

/**
 * Sliding pill terpisah (opsional) untuk Dock 4 tab tanpa tombol tengah — dipakai bila aplikasi
 * membutuhkan indikator yang benar-benar meluncur antar tab. Disimpan agar Dock tetap fleksibel.
 */
@Composable
fun SlidingDockIndicator(count: Int, selected: Int, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) {
        anim.animateTo(selected.toFloat(), tween(KarsaMotion.Medium, easing = FastOutSlowInEasing))
    }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .graphicsLayer { translationX = anim.value * 56f }
                .width(56.dp)
                .height(36.dp)
                .background(Brand500, RoundedCornerShape(18.dp)),
        )
    }
}
