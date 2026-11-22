package id.personal.campus.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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

// ───────── Bayangan (cardShadow / popShadow / brandShadow di replika web) ─────────

val CardShape: Shape = RoundedCornerShape(28.dp)
val TileShape: Shape = RoundedCornerShape(16.dp)

fun Modifier.cardShadow(shape: Shape = CardShape): Modifier =
    shadow(8.dp, shape, ambientColor = Espresso.copy(alpha = 0.16f), spotColor = Espresso.copy(alpha = 0.26f))

fun Modifier.popShadow(shape: Shape = CardShape): Modifier =
    shadow(20.dp, shape, ambientColor = Espresso.copy(alpha = 0.30f), spotColor = Espresso.copy(alpha = 0.45f))

fun Modifier.brandShadow(shape: Shape = CircleShape): Modifier =
    shadow(10.dp, shape, ambientColor = Brand500.copy(alpha = 0.40f), spotColor = Brand500.copy(alpha = 0.65f))

// ───────── Kartu ─────────

/** Kartu putih bersudut 28 dp dengan bayangan lembut (Card di replika web). */
@Composable
fun KarsaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Color.White,
    shape: Shape = CardShape,
    border: BorderStroke? = BorderStroke(1.dp, Ink.copy(alpha = 0.05f)),
    shadowed: Boolean = true,
    content: @Composable () -> Unit,
) {
    val m = modifier.let { if (shadowed) it.cardShadow(shape) else it }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = m, color = color, shape = shape, border = border, tonalElevation = 0.dp, shadowElevation = 0.dp, content = content)
    } else {
        Surface(modifier = m, color = color, shape = shape, border = border, tonalElevation = 0.dp, shadowElevation = 0.dp, content = content)
    }
}

@Composable
fun PaperPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    KarsaCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Panel kosong bergaris putus dengan ikon (EmptyState di replika web). */
@Composable
fun EmptyPanel(title: String, description: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.6f),
        shape = CardShape,
        border = BorderStroke(1.dp, Paper3),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Brand100), contentAlignment = Alignment.Center) {
                TruntumMark(28.dp, tile = null, ink = Brand600)
            }
            Text(title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge)
            Text(description, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = Ink2)
            if (action != null) Box(Modifier.padding(top = 20.dp)) { action() }
        }
    }
}

// ───────── Judul ─────────

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Brand700) {
    Text(text.uppercase(Indonesian), modifier, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
}

@Composable
fun PageHeading(title: String, subtitle: String, eyebrow: String? = null, color: Color = Ink, subtitleColor: Color = Ink2) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (eyebrow != null) Eyebrow(eyebrow, color = if (color == Ink) Brand700 else Brand300)
        Text(title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 36.sp), color = color)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
    }
}

@Composable
fun SectionHeading(title: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
        if (actionLabel != null && action != null) {
            TextButton(onClick = action, modifier = Modifier.heightIn(min = 40.dp), colors = ButtonDefaults.textButtonColors(contentColor = Brand600)) {
                Text(actionLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, Modifier.size(16.dp))
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
    Surface(modifier = modifier, color = tone.bg, contentColor = tone.fg, shape = CircleShape) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp, maxLines = 1)
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

/** Lingkaran centang untuk tugas (CheckCircle di replika web). */
@Composable
fun CheckCircle(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (checked) Brand500 else Color.White)
            .border(2.dp, if (checked) Brand500 else Paper3, CircleShape)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

// ───────── Field teks ─────────

/** Kolom isian berlatar kertas (paper2) tanpa garis tepi, oranye saat fokus (TextField di replika web). */
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
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, label = label,
        singleLine = singleLine, isError = isError, minLines = if (singleLine) 1 else minLines,
        shape = TileShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ink, unfocusedTextColor = Ink, disabledTextColor = Muted,
            focusedContainerColor = Color.White, unfocusedContainerColor = Paper2,
            disabledContainerColor = Paper2, errorContainerColor = Danger100.copy(alpha = 0.5f),
            focusedBorderColor = Brand400, unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent, errorBorderColor = Danger500.copy(alpha = 0.6f),
            focusedLabelColor = Brand600, unfocusedLabelColor = Muted, errorLabelColor = Danger500,
            cursorColor = Brand500, errorCursorColor = Danger500,
        ),
    )
}

// ───────── Field tanggal & jam ─────────

@Composable
internal fun fieldButtonColors() = ButtonDefaults.outlinedButtonColors(containerColor = Paper2, contentColor = Ink, disabledContainerColor = Paper2, disabledContentColor = Muted)

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
    Column {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            enabled = enabled,
            shape = TileShape,
            colors = fieldButtonColors(),
            border = BorderStroke(0.dp, Color.Transparent),
            onClick = {
                val initial = epochDay?.let(LocalDate::ofEpochDay) ?: today
                DatePickerDialog(context, { _, year, month, day ->
                    onChange(LocalDate.of(year, month + 1, day).toEpochDay())
                }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                    setTitle(label)
                    setButton(DatePickerDialog.BUTTON_POSITIVE, "Pilih", this)
                    setButton(DatePickerDialog.BUTTON_NEGATIVE, "Batal", this)
                }.show()
            },
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = Brand600)
            Text("$label: ${epochDay?.let(::dateLabel) ?: "Belum dipilih"}", Modifier.weight(1f).padding(start = 12.dp))
        }
        if (clearable && epochDay != null) {
            TextButton(enabled = enabled, onClick = { onChange(null) }, colors = ButtonDefaults.textButtonColors(contentColor = Brand600)) {
                Text("Hapus tanggal ${label.lowercase(Indonesian)}")
            }
        }
    }
}

@Composable
fun TimeField(label: String, minute: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        enabled = enabled,
        shape = TileShape,
        colors = fieldButtonColors(),
        border = BorderStroke(0.dp, Color.Transparent),
        onClick = {
            TimePickerDialog(context, { _, hour, selectedMinute -> onChange(hour * 60 + selectedMinute) },
                minute / 60, minute % 60, true).apply {
                setTitle(label)
                setButton(TimePickerDialog.BUTTON_POSITIVE, "Pilih", this)
                setButton(TimePickerDialog.BUTTON_NEGATIVE, "Batal", this)
            }.show()
        },
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Brand600)
        Text("$label: ${minuteLabel(minute)}", Modifier.weight(1f).padding(start = 12.dp), style = TimeStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
fun ValidationText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.error)
}

/** Dialog konfirmasi hapus: kartu putih 28 dp, tombol bahaya merah (Dialog di replika web). */
@Composable
fun DeleteConfirmation(title: String, explanation: String, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Hapus $title?") },
        text = { Text(explanation, color = Ink2) },
        confirmButton = {
            Button(
                enabled = !busy, onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Danger500, contentColor = Color.White),
            ) { Text(if (busy) "Menghapus…" else "Hapus") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Ink2)) { Text("Batal") } },
        containerColor = Color.White,
        shape = CardShape,
        tonalElevation = 0.dp,
    )
}
