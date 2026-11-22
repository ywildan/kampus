package id.personal.campus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.personal.campus.data.CourseEntity

@Composable
fun ScheduleScreen(courses: List<CourseEntity>, zoneId: String, busy: Boolean, onAdd: () -> Unit, onOpen: (Long) -> Unit, onToggle: (CourseEntity) -> Unit) {
    val ordered = courses.sortedWith(compareBy<CourseEntity> { it.weekday }.thenBy { it.startMinute }.thenBy { it.id })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("Jadwal kuliah", "Satu minggu, tertata. Semua jam dalam ${zoneLabel(zoneId)}.", eyebrow = "Minggu ini") }
        item {
            Button(onClick = onAdd, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).brandShadow(CircleShape)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Tambah mata kuliah", Modifier.padding(start = 8.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge("${courses.count { it.enabled }} aktif", BadgeTone.Leaf)
                Badge("${courses.count { !it.enabled }} nonaktif", BadgeTone.Neutral)
            }
        }
        if (courses.isEmpty()) item {
            EmptyPanel("Belum ada mata kuliah", "Tambahkan jadwal mingguan Anda. Jadwal yang dihapus tidak dipulihkan otomatis.")
        }
        (1..7).forEach { day ->
            val dayCourses = ordered.filter { it.weekday == day }
            item(key = "day-$day") { SectionHeading(DayNames[day - 1]) }
            if (dayCourses.isEmpty()) item(key = "empty-$day") {
                Text("Tidak ada kuliah.", color = Muted, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(dayCourses, key = { "course-${it.id}" }) { course ->
                val overlaps = if (!course.enabled) emptyList() else dayCourses.filter {
                    it.id != course.id && it.enabled && it.startMinute < course.endMinute && course.startMinute < it.endMinute
                }
                KarsaCard(Modifier.fillMaxWidth(), onClick = { onOpen(course.id) }, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${minuteLabel(course.startMinute)}–${minuteLabel(course.endMinute)}", style = TimeStyle.copy(fontSize = 24.sp, letterSpacing = (-0.4).sp), color = if (course.enabled) Ink else Muted)
                                Text(course.name, style = MaterialTheme.typography.titleMedium, color = if (course.enabled) Ink else Muted)
                                if (course.section.isNotBlank()) Text("Kelas ${course.section}", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                            RoomBadge(course.room)
                        }
                        if (course.roomNote.isNotBlank()) Text(course.roomNote, style = MaterialTheme.typography.bodyMedium, color = Ink2)
                        if (overlaps.isNotEmpty()) ValidationText("Jadwal bentrok dengan ${overlaps.joinToString { it.name }}.")
                        ToggleField(
                            title = if (course.enabled) "Aktif" else "Nonaktif",
                            description = "${course.name} · Ketuk kartu untuk detail dan edit.",
                            checked = course.enabled, enabled = !busy,
                            onCheckedChange = { onToggle(course.copy(enabled = it)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CourseDetails(course: CourseEntity, zoneId: String, busy: Boolean, snackbar: SnackbarHostState, onDismiss: () -> Unit, onEdit: () -> Unit, onToggle: (CourseEntity) -> Unit, onDelete: () -> Unit) {
    EditorFrame(
        title = "Detail mata kuliah", busy = busy, dirty = false, snackbar = snackbar,
        onDismiss = onDismiss, onDelete = onDelete,
        deleteExplanation = "“${course.name}” dihapus dari jadwal. Tugas terkait tetap tersimpan tanpa tautan mata kuliah.",
    ) {
        Eyebrow("${DayNames[course.weekday - 1]} · ${zoneLabel(zoneId)}")
        Text(course.name, style = MaterialTheme.typography.headlineMedium)
        Text("${minuteLabel(course.startMinute)}–${minuteLabel(course.endMinute)}", style = TimeStyle.copy(fontSize = 34.sp, letterSpacing = (-0.8).sp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow("Ruang", color = Muted)
            RoomBadge(course.room, large = true)
        }
        Text(if (course.section.isBlank()) "Kelas / seksi belum diisi" else "Kelas ${course.section}", color = Ink2)
        if (course.roomNote.isNotBlank()) Text(course.roomNote, color = Ink2)
        ToggleField("Aktif di jadwal", "Perubahan status langsung disimpan. Kuliah nonaktif tidak memicu pengingat.", course.enabled, !busy) { onToggle(course.copy(enabled = it)) }
        Button(onClick = onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = null)
            Text("Edit mata kuliah", Modifier.padding(start = 8.dp))
        }
    }
}
