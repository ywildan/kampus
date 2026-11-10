package id.personal.campus.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

@Composable
fun PaperPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Paper,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Rule),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
    }
}

@Composable
fun SectionHeading(title: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f).padding(vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
        if (actionLabel != null && action != null) {
            TextButton(onClick = action, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
        }
    }
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
    Column {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            enabled = enabled,
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
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
            Text("$label: ${epochDay?.let(::dateLabel) ?: "Belum dipilih"}", Modifier.weight(1f).padding(start = 12.dp))
        }
        if (clearable && epochDay != null) {
            TextButton(enabled = enabled, onClick = { onChange(null) }) { Text("Hapus tanggal ${label.lowercase(Indonesian)}") }
        }
    }
}

@Composable
fun TimeField(label: String, minute: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        enabled = enabled,
        onClick = {
            TimePickerDialog(context, { _, hour, selectedMinute -> onChange(hour * 60 + selectedMinute) },
                minute / 60, minute % 60, true).apply {
                setTitle(label)
                setButton(TimePickerDialog.BUTTON_POSITIVE, "Pilih", this)
                setButton(TimePickerDialog.BUTTON_NEGATIVE, "Batal", this)
            }.show()
        },
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null)
        Text("$label: ${minuteLabel(minute)}", Modifier.weight(1f).padding(start = 12.dp), style = TimeStyle)
    }
}

@Composable
fun ValidationText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
fun DeleteConfirmation(title: String, explanation: String, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Hapus $title?") },
        text = { Text(explanation) },
        confirmButton = {
            Button(enabled = !busy, onClick = onConfirm) { Text(if (busy) "Menghapus…" else "Hapus") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Batal") } },
        containerColor = Paper,
        tonalElevation = 0.dp,
    )
}
