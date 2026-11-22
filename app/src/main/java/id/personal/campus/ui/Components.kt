package id.personal.campus.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val Indonesian: Locale = Locale.forLanguageTag("id-ID")
val DayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
private val DateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Indonesian)
val FullDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Indonesian)
fun dateLabel(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DateFormat)
fun minuteLabel(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
fun zoneLabel(zone: String): String = when (zone) {
    "Asia/Jakarta" -> "WIB"
    "Asia/Makassar" -> "WITA"
    "Asia/Jayapura" -> "WIT"
    else -> zone
}

// ───────── Bayangan (cardShadow / popShadow / brandShadow di asset-karsa) ─────────

val CardShape: Shape = RoundedCornerShape(28.dp)
val TileShape: Shape = RoundedCornerShape(16.dp)
val ListShape: Shape = RoundedCornerShape(20.dp)

fun Modifier.cardShadow(shape: Shape = CardShape): Modifier =
    shadow(10.dp, shape, ambientColor = Espresso.copy(alpha = 0.14f), spotColor = Espresso.copy(alpha = 0.24f))

fun Modifier.popShadow(shape: Shape = CardShape): Modifier =
    shadow(22.dp, shape, ambientColor = Espresso.copy(alpha = 0.30f), spotColor = Espresso.copy(alpha = 0.42f))

fun Modifier.brandShadow(shape: Shape = CircleShape): Modifier =
    shadow(10.dp, shape, ambientColor = Brand500.copy(alpha = 0.42f), spotColor = Brand500.copy(alpha = 0.62f))

// ───────── Kartu ─────────

/**
 * Kartu putih bersudut 28 dp dengan bayangan lembut dan guratan 1 dp tinta 5%.
 * Saat bisa diketuk, kartu mengecil sedikit (0,98) ketika ditekan agar terasa "empuk".
 */
@Composable
fun KarsaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Color.White,
    shape: Shape = CardShape,
    border: BorderStroke? = BorderStroke(1.dp, Ink.copy(alpha = 0.05f)),
    shadowed: Boolean = true,
    pressScale: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val m = modifier.let { if (shadowed) it.cardShadow(shape) else it }
    if (onClick != null) {
        val scale by pressScaleState(interactionSource, if (pressScale) 0.98f else 1f)
        Surface(
            onClick = onClick,
            modifier = m.graphicsLayer { scaleX = scale; scaleY = scale },
            interactionSource = interactionSource,
            color = color,
            shape = shape,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = content,
        )
    } else {
        Surface(
            modifier = m, color = color, shape = shape, border = border,
            tonalElevation = 0.dp, shadowElevation = 0.dp, content = content,
        )
    }
}

@Composable
private fun pressScaleState(source: MutableInteractionSource, pressed: Float) =
    pressScale(source, pressed)

@Composable
fun PaperPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    KarsaCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/**
 * Panel kosong dengan ikon di dalam ubin oranye lembut. Muncul dengan fade + naik tipis
 * supaya keadaan kosong tidak terasa "patah" saat muncul.
 */
@Composable
fun EmptyPanel(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    val progress by rememberEnterProgress()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = riseProgress(progress) * density
            },
        color = Color.White.copy(alpha = 0.62f),
        shape = CardShape,
        border = BorderStroke(1.dp, Paper3),
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(Brand100),
                contentAlignment = Alignment.Center,
            ) { TruntumMark(30.dp, tile = null, ink = Brand600) }
            Text(
                title,
                Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink2,
                textAlign = TextAlign.Center,
            )
            if (action != null) Box(Modifier.padding(top = 14.dp)) { action() }
        }
    }
}

// ───────── Judul ─────────

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Brand700) {
    Text(
        text.uppercase(Indonesian),
        modifier,
        color = color,
        style = EyebrowStyle,
    )
}

@Composable
fun PageHeading(
    title: String,
    subtitle: String,
    eyebrow: String? = null,
    color: Color = Ink,
    subtitleColor: Color = Ink2,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val progress by rememberEnterProgress()
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = riseProgress(progress) * density
            },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (eyebrow != null) Eyebrow(eyebrow, color = if (color == Ink) Brand700 else Brand300)
            Text(title, style = MaterialTheme.typography.headlineLarge, color = color)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun SectionHeading(title: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, Modifier.weight(1f).padding(vertical = 6.dp), style = MaterialTheme.typography.titleLarge)
        if (actionLabel != null && action != null) {
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(color = Brand500.copy(alpha = 0.14f), bounded = true),
                        role = Role.Button,
                        onClick = action,
                    )
                    .pressFeedback(interaction, KarsaMotion.TapScale)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(actionLabel, color = Brand600, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, Modifier.size(16.dp), tint = Brand600)
            }
        }
    }
}

// ───────── Badge ─────────

enum class BadgeTone(val bg: Color, val fg: Color) {
    Brand(Brand100, Brand700),
    Solid(Brand500, Color.White),
    Dark(Espresso, Color.White),
    Leaf(Leaf100, Leaf600),
    Butter(Butter100, Butter700),
    Danger(Danger100, Danger500),
    Neutral(Paper2, Ink2),
    Plum(Plum100, Plum600),
    Tide(Tide100, Tide600),
}

@Composable
fun Badge(text: String, tone: BadgeTone = BadgeTone.Brand, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(tone.bg, tween(KarsaMotion.Quick, easing = KarsaMotion.Standard), label = "badgeBg")
    val fg by animateColorAsState(tone.fg, tween(KarsaMotion.Quick, easing = KarsaMotion.Standard), label = "badgeFg")
    Surface(modifier = modifier, color = bg, contentColor = fg, shape = CircleShape) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Kode ruang sebagai blok menonjol (RoomBadge di replika web, mirip widget_room_badge). */
@Composable
fun RoomBadge(room: String, modifier: Modifier = Modifier, large: Boolean = false, onDark: Boolean = false) {
    Surface(
        modifier = modifier,
        color = if (onDark) Brand500 else Espresso,
        contentColor = Color.White,
        shape = RoundedCornerShape(if (large) 20.dp else 14.dp),
    ) {
        Text(
            room,
            Modifier.padding(horizontal = if (large) 16.dp else 12.dp, vertical = if (large) 10.dp else 6.dp),
            style = TimeStyle.copy(fontSize = if (large) 24.sp else 15.sp, letterSpacing = (-0.2).sp),
            maxLines = 1,
        )
    }
}

/** Lingkaran centang untuk tugas; ikon centang tumbuh dari 0 ke 1 saat dicentang. */
@Composable
fun CheckCircle(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val background by animateColorAsState(
        if (checked) Brand500 else Color.White,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "checkBg",
    )
    val borderColor by animateColorAsState(
        if (checked) Brand500 else Paper3,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "checkBorder",
    )
    val iconScale by animateFloatAsState(
        if (checked) 1f else 0.4f,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "checkIcon",
    )
    val iconAlpha by animateFloatAsState(
        if (checked) 1f else 0f,
        tween(KarsaMotion.Press, easing = KarsaMotion.Standard),
        label = "checkAlpha",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(28.dp)
            .pressFeedback(interaction, KarsaMotion.TapScale)
            .clip(CircleShape)
            .background(background)
            .border(2.dp, borderColor, CircleShape)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = ripple(color = Brand500.copy(alpha = 0.2f), bounded = true),
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp).graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
                alpha = iconAlpha
            },
        )
    }
}

// ───────── Field teks ─────────

/**
 * Kolom isian berlatar kertas (paper2) tanpa garis tepi; saat fokus latar memutih dan
 * guratan oranye 2 dp muncul dengan transisi halus, mengikuti TextField di asset-karsa.
 */
@Composable
fun KarsaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    isError: Boolean = false,
    minLines: Int = 1,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val container by animateColorAsState(
        when {
            !enabled -> Paper2
            isError -> Danger100.copy(alpha = 0.45f)
            focused -> Color.White
            else -> Paper2
        },
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "fieldBg",
    )
    val borderColor by animateColorAsState(
        when {
            isError -> Danger500.copy(alpha = 0.65f)
            focused -> Brand400
            else -> Color.Transparent
        },
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "fieldBorder",
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = label,
        singleLine = singleLine,
        isError = isError,
        minLines = if (singleLine) 1 else minLines,
        shape = TileShape,
        interactionSource = interaction,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ink, unfocusedTextColor = Ink, disabledTextColor = Muted,
            focusedContainerColor = container, unfocusedContainerColor = container,
            disabledContainerColor = Paper2, errorContainerColor = container,
            focusedBorderColor = borderColor, unfocusedBorderColor = borderColor,
            disabledBorderColor = Color.Transparent, errorBorderColor = borderColor,
            focusedLabelColor = Brand600, unfocusedLabelColor = Muted, errorLabelColor = Danger500,
            cursorColor = Brand500, errorCursorColor = Danger500,
            focusedPlaceholderColor = Muted, unfocusedPlaceholderColor = Muted,
        ),
    )
}

// ───────── Field tanggal & jam ─────────

@Composable
internal fun fieldButtonColors() = ButtonDefaults.outlinedButtonColors(
    containerColor = Paper2, contentColor = Ink,
    disabledContainerColor = Paper2, disabledContentColor = Muted,
)

/** Tombol field bergaya kertas: latar memutih + guratan oranye saat ditekan/difokus. */
@Composable
private fun FieldSurface(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed = isPressed(interaction)
    val container by animateColorAsState(
        if (pressed) Color.White else Paper2,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "fieldBtnBg",
    )
    val borderColor by animateColorAsState(
        if (pressed) Brand300 else Color.Transparent,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "fieldBtnBorder",
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(TileShape)
            .background(container)
            .border(2.dp, borderColor, TileShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Brand500.copy(alpha = 0.12f), bounded = true),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .pressFeedback(interaction, 0.985f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun DateField(
    label: String,
    epochDay: Long?,
    today: LocalDate,
    onChange: (Long?) -> Unit,
    enabled: Boolean = true,
    clearable: Boolean = true,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val initial = epochDay?.let(LocalDate::ofEpochDay) ?: today
        FieldSurface(
            enabled = enabled,
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day).toEpochDay()) },
                    initial.year, initial.monthValue - 1, initial.dayOfMonth,
                ).apply {
                    setTitle(label)
                    setButton(DatePickerDialog.BUTTON_POSITIVE, "Pilih", this)
                    setButton(DatePickerDialog.BUTTON_NEGATIVE, "Batal", this)
                }.show()
            },
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = Brand600)
            Text(
                "$label: ${epochDay?.let(::dateLabel) ?: "Belum dipilih"}",
                Modifier.weight(1f).padding(start = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (epochDay == null) Muted else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (epochDay == null) {
                Icon(Icons.Outlined.ArrowForwardIos, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
            }
        }
        if (clearable && epochDay != null) {
            KarsaTextButton("Hapus tanggal ${label.lowercase(Indonesian)}") { onChange(null) }
        }
    }
}

@Composable
fun TimeField(label: String, minute: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    FieldSurface(
        enabled = enabled,
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, selectedMinute -> onChange(hour * 60 + selectedMinute) },
                minute / 60, minute % 60, true,
            ).apply {
                setTitle(label)
                setButton(TimePickerDialog.BUTTON_POSITIVE, "Pilih", this)
                setButton(TimePickerDialog.BUTTON_NEGATIVE, "Batal", this)
            }.show()
        },
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Brand600)
        Text(
            label, Modifier.weight(1f).padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge, maxLines = 1,
        )
        Text(minuteLabel(minute), style = TimeStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
}

/** Tombol teks Karsa dengan umpan balik tekan dan warna brand (bukan abu-abu bawaan M3). */
@Composable
fun KarsaTextButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Brand600,
    fontWeight: FontWeight = FontWeight.Bold,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = color.copy(alpha = 0.14f), bounded = true),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .pressFeedback(interaction, KarsaMotion.TapScale)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            color = if (enabled) color else Muted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = fontWeight,
        )
    }
}

@Composable
fun ValidationText(text: String) {
    val progress by rememberEnterProgress(durationMillis = 220)
    Row(
        Modifier.graphicsLayer { alpha = progress; translationY = riseProgress(progress, 6f) * density },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(Danger500, CircleShape))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Dialog konfirmasi hapus: kartu putih 28 dp, tombol bahaya merah (Dialog di replika web). */
@Composable
fun DeleteConfirmation(title: String, explanation: String, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Hapus $title?", style = MaterialTheme.typography.titleLarge) },
        text = { Text(explanation, color = Ink2, style = MaterialTheme.typography.bodyMedium) },
        icon = {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Danger100),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = Danger500,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        confirmButton = {
            DangerButton(text = if (busy) "Menghapus…" else "Hapus", enabled = !busy, onClick = onConfirm)
        },
        dismissButton = {
            KarsaTextButton("Batal", enabled = !busy, color = Ink2, onClick = onDismiss)
        },
        containerColor = Color.White,
        shape = CardShape,
        tonalElevation = 0.dp,
    )
}

/**
 * Ayunan kecil pada logo/ikon: dipakai beberapa tempat untuk memberi "nafas" pada elemen statis.
 * Nilai awal 0f → dipakai bersama [graphicsLayer].
 */
@Composable
fun rememberBreathe(enabled: Boolean = true): State<Float> {
    val value = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            value.animateTo(1f, tween(2400, easing = KarsaMotion.Standard))
            value.animateTo(0f, tween(2400, easing = KarsaMotion.Standard))
        }
    }
    return value.asState()
}
