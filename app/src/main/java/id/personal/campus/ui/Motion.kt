package id.personal.campus.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/*
 * Bahasa gerak Karsa.
 *
 * Satu set durasi + easing dipakai di seluruh aplikasi supaya setiap perpindahan terasa satu
 * tarikan napas: sentuhan membal cepat, konten masuk naik sambil memudar, pergantian layar
 * menyilang lembut, dan pil Dock meluncur dengan pegas.
 */

object KarsaMotion {
    /** Sentuhan tombol: cukup cepat untuk terasa langsung, cukup lambat untuk terlihat halus. */
    const val Press = 130

    /** Perubahan warna/state kecil (pil Dock, chip, ikon, border fokus). */
    const val Quick = 200

    /** Konten masuk/keluar, kartu, dan panel. */
    const val Medium = 320

    /** Perpindahan layar penuh, sheet, dan dialog. */
    const val Long = 440

    /** Kurva standar: cepat di awal, melunak di akhir (kurva "rise" di asset-karsa). */
    val Standard: Easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)

    /** Kurva untuk elemen yang datang: sedikit lebih tegas di awal. */
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Kurva untuk elemen yang pergi: percepatan halus tanpa hentakan. */
    val Exit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** Jarak angkat konten yang masuk, dalam dp. */
    const val Rise = 12f

    /** Skala tekan tombol. */
    const val PressScale = 0.96f

    /** Skala tekan untuk ikon/tab kecil. */
    const val TapScale = 0.92f
}

/** Skala 1 -> [pressed] saat jari menekan, memakai durasi sentuh yang sama di seluruh aplikasi. */
@Composable
fun pressScale(
    interactionSource: InteractionSource,
    pressed: Float = KarsaMotion.PressScale,
): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = tween(KarsaMotion.Press, easing = KarsaMotion.Standard),
        label = "pressScale",
    )
}

/** `Modifier.pressFeedback()`: skala tekan halus tanpa mengubah perilaku klik. */
@Composable
fun Modifier.pressFeedback(
    interactionSource: InteractionSource,
    pressed: Float = KarsaMotion.PressScale,
): Modifier {
    val scale by pressScale(interactionSource, pressed)
    return this.scale(scale)
}

/**
 * Progres masuk 0 → 1 sekali saat komposisi pertama kali tampil.
 * Dipakai untuk "rise": konten naik sedikit sambil memudar masuk.
 */
@Composable
fun rememberEnterProgress(
    delayMillis: Int = 0,
    durationMillis: Int = KarsaMotion.Medium,
): State<Float> {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        animated.animateTo(1f, tween(durationMillis, easing = KarsaMotion.Emphasized))
    }
    return animated.asState()
}

/** Apakah interaksi sedang ditekan (untuk mematikan efek lain saat menekan). */
@Composable
fun isPressed(interactionSource: InteractionSource): Boolean =
    interactionSource.collectIsPressedAsState().value

/** Offset vertikal masuk: dari [distance] ke 0. */
fun riseProgress(progress: Float, distance: Float = KarsaMotion.Rise): Float = (1f - progress) * distance

/**
 * Skala tekan berpasangan dengan ripple: dipakai permukaan Surface berbasis onClick yang sudah
 * punya indikasi sendiri. `indication` dibiarkan null lalu diberi [ripple] oranye oleh pemanggil.
 */
@Composable
fun Modifier.pressScaleLayer(
    interactionSource: InteractionSource,
    pressed: Float = KarsaMotion.PressScale,
): Modifier {
    val scale by pressScale(interactionSource, pressed)
    return this.then(Modifier.scale(scale))
}

/**
 * Ketukan halus untuk aksi utama (tab Dock, tombol primer).
 * Memakai HapticFeedbackConstants platform supaya tidak bergantung pada konstanta
 * HapticFeedbackType Compose yang berubah antar versi.
 */
@Composable
fun rememberKarsaTap(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(
                android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
            Unit
        }
    }
}

/** Easing siap pakai untuk layar yang membutuhkan kurva Karsa. */
val KarsaEaseOut: Easing = KarsaMotion.Standard
val KarsaEaseIn: Easing = KarsaMotion.Exit
