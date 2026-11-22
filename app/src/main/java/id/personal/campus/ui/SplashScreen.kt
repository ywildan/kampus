package id.personal.campus.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Splash oranye dengan motif batik Truntum, kilau krem di kanan atas, bayangan espresso di kiri
 * bawah, serta logo bintang Truntum yang melayang pelan dengan cincin berdenyut.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "splash")
    val floaty by transition.animateFloat(
        initialValue = 0f, targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floaty",
    )
    val ring by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "ring",
    )
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }

    Box(
        modifier
            .fillMaxSize()
            .background(Brand500)
            // Menelan sentuhan selama splash masih menutupi aplikasi.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        BatikPattern(Modifier.fillMaxSize(), color = Color.White, alpha = 0.2f)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(Brand200.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.10f),
                    radius = size.maxDimension * 0.62f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Espresso.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(0f, size.height),
                    radius = size.maxDimension * 0.65f,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(104.dp)
                        .graphicsLayer {
                            val s = 1f + 0.7f * ring
                            scaleX = s
                            scaleY = s
                            alpha = 0.55f * (1f - ring)
                        }
                        .background(Color.White.copy(alpha = 0.4f), CircleShape),
                )
                TruntumMark(
                    size = 104.dp, tile = Color.White, ink = Brand500,
                    modifier = Modifier.graphicsLayer { translationY = floaty.dp.toPx() },
                )
            }
            Text(
                "Kampus",
                Modifier
                    .padding(top = 24.dp)
                    .graphicsLayer { alpha = rise.value; translationY = (1f - rise.value) * 14.dp.toPx() },
                color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp,
            )
            Text(
                "Membuka ruang kuliah Anda…",
                Modifier
                    .padding(top = 8.dp)
                    .graphicsLayer { alpha = rise.value; translationY = (1f - rise.value) * 14.dp.toPx() },
                color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Medium,
            )
        }
    }
}
