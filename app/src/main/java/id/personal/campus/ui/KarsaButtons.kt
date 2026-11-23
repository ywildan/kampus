package id.personal.campus.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * KarsaButton — satu keluarga tombol untuk seluruh aplikasi.
 *
 * Varian dan ukuran mengikuti asset-karsa (primary/dark/soft/outline/ghost/danger/dangerSoft,
 * Sm 36 dp pil · Md 48 dp r16 · Lg 56 dp r20). Semua varian berbagi:
 *  • skala tekan 0,96 dengan pegas halus,
 *  • perubahan warna latar 200 ms (hover/press) — bukan hentakan,
 *  • keadaan nonaktif yang memudar (bukan abu-abu mati),
 *  • indikator memuat berupa cincin berputar yang memudar masuk.
 */

enum class KarsaButtonVariant { Primary, Dark, Soft, Outline, Ghost, Danger, DangerSoft }

enum class KarsaButtonSize(val height: Dp, val horizontal: Dp, val fontSize: Int, val radius: Float) {
    Sm(36.dp, 14.dp, 13, 999f),
    Md(48.dp, 20.dp, 15, 16f),
    Lg(56.dp, 24.dp, 16, 20f),
}

private fun KarsaButtonSize.shape(): Shape = if (radius >= 999f) CircleShape else RoundedCornerShape(radius.dp)

@Composable
fun KarsaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KarsaButtonVariant = KarsaButtonVariant.Primary,
    size: KarsaButtonSize = KarsaButtonSize.Md,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    fullWidth: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed = isPressed(interaction)
    val active = enabled && !loading

    val rest = when (variant) {
        KarsaButtonVariant.Primary -> Brand500
        KarsaButtonVariant.Dark -> Espresso
        KarsaButtonVariant.Soft -> Brand100
        KarsaButtonVariant.Outline -> Color.White
        KarsaButtonVariant.Ghost -> Color.Transparent
        KarsaButtonVariant.Danger -> Danger500
        KarsaButtonVariant.DangerSoft -> Danger100
    }
    val hover = when (variant) {
        KarsaButtonVariant.Primary -> Brand600
        KarsaButtonVariant.Dark -> Espresso2
        KarsaButtonVariant.Soft -> Brand200
        KarsaButtonVariant.Outline -> Paper2
        KarsaButtonVariant.Ghost -> Paper2
        KarsaButtonVariant.Danger -> Color(0xFFA82C24)
        KarsaButtonVariant.DangerSoft -> Color(0xFFF6D3CE)
    }
    val disabledBg = when (variant) {
        KarsaButtonVariant.Outline, KarsaButtonVariant.Ghost -> Color.Transparent
        KarsaButtonVariant.Soft, KarsaButtonVariant.DangerSoft -> Paper2
        else -> Paper3
    }
    val content = when (variant) {
        KarsaButtonVariant.Primary, KarsaButtonVariant.Dark, KarsaButtonVariant.Danger -> Color.White
        KarsaButtonVariant.Soft -> Brand700
        KarsaButtonVariant.Outline -> Ink
        KarsaButtonVariant.Ghost -> Ink2
        KarsaButtonVariant.DangerSoft -> Danger500
    }

    val background by animateColorAsState(
        targetValue = when {
            !active -> disabledBg
            pressed -> hover
            else -> rest
        },
        animationSpec = tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "btnBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) content else Muted,
        animationSpec = tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "btnFg",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            variant == KarsaButtonVariant.Outline -> if (active) Line else Paper3
            variant == KarsaButtonVariant.Ghost -> if (pressed && active) Line else Color.Transparent
            else -> Color.Transparent
        },
        animationSpec = tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "btnBorder",
    )

    val shape = size.shape()
    val shadowed = variant == KarsaButtonVariant.Primary && active
    val scale by pressScale(interaction, if (active) KarsaMotion.PressScale else 1f)
    val tap = rememberKarsaTap()

    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = size.height)
            .then(if (shadowed) Modifier.brandShadow(shape) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = contentColor.copy(alpha = 0.18f)),
                enabled = active,
                role = Role.Button,
                onClick = {
                    // Ketukan ringan: sentuhan terasa "mendarat" tanpa berisik.
                    tap()
                    onClick()
                },
            )
            .padding(horizontal = size.horizontal, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            LoadingRing(size = 18.dp, color = contentColor)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
        Text(
            text,
            color = contentColor,
            fontSize = size.fontSize.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!loading && trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
    }
}

/** Tombol bahaya merah — dipakai dialog hapus. */
@Composable
fun DangerButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    KarsaButton(text, onClick, variant = KarsaButtonVariant.Danger, size = KarsaButtonSize.Md, enabled = enabled)
}

/**
 * Tombol ikon bulat (IconButton di asset-karsa): plain bergaris, ghost transparan, atau dark espresso.
 * Ikon di dalamnya mengecil 0,92 saat ditekan — umpan balik yang sama dengan tab Dock.
 */
@Composable
fun KarsaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KarsaIconVariant = KarsaIconVariant.Plain,
    enabled: Boolean = true,
    size: Dp = 40.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed = isPressed(interaction)
    val rest = when (variant) {
        KarsaIconVariant.Plain -> Color.White
        KarsaIconVariant.Ghost -> Color.Transparent
        KarsaIconVariant.Dark -> Espresso
        KarsaIconVariant.Brand -> Brand500
    }
    val hover = when (variant) {
        KarsaIconVariant.Plain -> Paper2
        KarsaIconVariant.Ghost -> Paper2
        KarsaIconVariant.Dark -> Espresso2
        KarsaIconVariant.Brand -> Brand600
    }
    val tint = when (variant) {
        KarsaIconVariant.Dark, KarsaIconVariant.Brand -> Color.White
        else -> Ink
    }
    val background by animateColorAsState(
        if (!enabled) Color.Transparent else if (pressed) hover else rest,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "iconBtnBg",
    )
    val scale by pressScale(interaction, if (enabled) KarsaMotion.TapScale else 1f)
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .then(
                if (variant == KarsaIconVariant.Plain) Modifier.border(1.dp, if (enabled) Line else Paper3, CircleShape)
                else Modifier,
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = tint.copy(alpha = 0.2f)),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else Muted,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

enum class KarsaIconVariant { Plain, Ghost, Dark, Brand }

/** Cincin pemuat Karsa: oranye berputar dengan ekor memudar. */
@Composable
fun LoadingRing(size: Dp = 18.dp, color: Color = Brand500) {
    val transition = rememberInfiniteTransition(label = "loading")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(950, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "loadingAngle",
    )
    Box(
        Modifier.size(size).rotate(angle),
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            drawArc(
                color = color.copy(alpha = 0.22f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = this.size.minDimension * 0.16f),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = this.size.minDimension * 0.16f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
    }
}

/**
 * Chip pilihan (36 dp pil). Yang terpilih berlatar espresso dan ikon centangnya menyusut masuk;
 * perpindahan warna memakai kurva standar supaya daftar chip terasa "mengalir".
 */
@Composable
fun KarsaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        if (selected) Espresso else Color.White,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "chipBg",
    )
    val contentColor by animateColorAsState(
        if (selected) Color.White else Ink2,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "chipFg",
    )
    val borderColor by animateColorAsState(
        if (selected) Espresso else Line,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "chipBorder",
    )
    val scale by pressScale(interaction, KarsaMotion.TapScale)
    val checkScale by androidx.compose.animation.core.animateFloatAsState(
        if (selected && leading) 1f else 0f,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "chipCheck",
    )
    Row(
        modifier
            .height(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = contentColor.copy(alpha = 0.2f)),
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading) {
            Box(Modifier.size(14.dp).graphicsLayer { alpha = checkScale; scaleX = checkScale; scaleY = checkScale }) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Sheet dasar bergaya Karsa: sudut atas 32 dp, gagang kertas di tengah, latar kertas hangat.
 * Dipakai oleh sheet mode model dan pemilih penyedia agar transisinya seragam.
 */
@Composable
fun KarsaSheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(42.dp).height(4.dp).background(Paper3, CircleShape))
    }
}
