package id.personal.campus.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.personal.campus.data.CampusSnapshot
import id.personal.campus.data.TaskEntity
import id.personal.campus.schedule.Occurrence
import id.personal.campus.schedule.ScheduleMode
import id.personal.campus.schedule.ScheduleSnapshot
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm")
private val SecondFormat = DateTimeFormatter.ofPattern(":ss")

private fun greeting(now: ZonedDateTime): String = when {
    now.hour < 11 -> "Selamat pagi"
    now.hour < 15 -> "Selamat siang"
    now.hour < 18 -> "Selamat sore"
    else -> "Selamat malam"
}

private fun durationLabel(minutes: Long): String {
    val m = minutes.coerceAtLeast(0)
    return when {
        m < 60 -> "$m mnt"
        m % 60 == 0L -> "${m / 60} jam"
        else -> "${m / 60}j ${m % 60}m"
    }
}

private fun relativeDay(occurrence: Occurrence, now: ZonedDateTime): String {
    val diff = occurrence.start.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()
    return when (diff) {
        0L -> "Hari ini"
        1L -> "Besok"
        else -> DayNames[occurrence.start.dayOfWeek.value - 1]
    }
}

@Composable
fun HomeScreen(
    data: CampusSnapshot,
    schedule: ScheduleSnapshot,
    busy: Boolean,
    onSettings: () -> Unit,
    onCourse: (Long) -> Unit,
    onTasks: () -> Unit,
    onTask: (Long) -> Unit,
    onTaskCompleted: (TaskEntity) -> Unit,
    onAddNote: () -> Unit,
    onNote: (Long) -> Unit,
    onAssistant: () -> Unit = {},
) {
    val nearTasks = data.tasks.filter { !it.completed }.sortedWith(
        compareBy<TaskEntity> { it.dueEpochDay ?: Long.MAX_VALUE }.thenBy { it.id },
    ).take(3)
    val preview = !data.settings.semesterConfirmed
    val zone = zoneLabel(data.settings.zoneId)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TruntumMark(36.dp)
                Text("Kampus", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold))
                Badge(zone, BadgeTone.Neutral)
                Badge(if (preview) "PRATINJAU" else "TERKONFIRMASI", if (preview) BadgeTone.Butter else BadgeTone.Leaf)
            }
        }
        item {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow(schedule.now.format(FullDateFormat))
                Text(greeting(schedule.now), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 36.sp))
                Text(
                    buildAnnotatedString {
                        append("${data.settings.semesterName} · ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Ink)) { append(schedule.now.format(ClockFormat)) }
                        withStyle(SpanStyle(color = Muted)) { append(schedule.now.format(SecondFormat)) }
                        append(" $zone")
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Ink2,
                )
            }
        }
        if (preview) item {
            Surface(color = Butter100, contentColor = Butter700, shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, Modifier.padding(top = 2.dp).size(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Jadwal masih pratinjau", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
                        Text(
                            "Tanggal semester belum dikonfirmasi. Anda boleh melihat dan mengedit jadwal sekarang; pengingat belum berjalan.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        )
                        Button(
                            onClick = onSettings,
                            modifier = Modifier.padding(top = 8.dp).heightIn(min = 40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Butter700, contentColor = Color.White),
                        ) {
                            Text("Atur tanggal semester", fontWeight = FontWeight.Bold)
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, Modifier.padding(start = 6.dp).size(16.dp))
                        }
                    }
                }
            }
        }
        if (schedule.active.size > 1) item {
            Surface(color = Danger100, contentColor = Danger500, shape = TileShape) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, Modifier.size(20.dp))
                    Text(
                        if (preview) "Bentrok pada pratinjau: ${schedule.active.size} kuliah memiliki jam yang bertumpuk."
                        else "Jadwal bentrok: ${schedule.active.size} kuliah berlangsung bersamaan. Periksa seluruh ruang di bawah.",
                        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(schedule.active, key = { "active-${it.key}" }) { occurrence ->
            OccurrenceHero(occurrence, if (preview) "PRATINJAU · JAM SAAT INI" else "BERLANGSUNG", data.settings.zoneId, schedule.now, active = true) { onCourse(occurrence.course.id) }
        }
        schedule.upcoming.firstOrNull()?.let { next ->
            item(key = "next-${next.key}") {
                if (schedule.active.isEmpty()) {
                    OccurrenceHero(next, if (preview) "PRATINJAU · BERIKUTNYA" else "BERIKUTNYA", data.settings.zoneId, schedule.now, active = false) { onCourse(next.course.id) }
                } else {
                    NextCompactCard(next, if (preview) "PRATINJAU · BERIKUTNYA" else "BERIKUTNYA", schedule.now) { onCourse(next.course.id) }
                }
            }
        }
        if (schedule.primary == null) item {
            val heading = when (schedule.mode) {
                ScheduleMode.EMPTY -> "Tidak ada kuliah aktif"
                ScheduleMode.FINISHED -> "Semester telah selesai"
                else -> "Pertemuan semester sudah berakhir"
            }
            EmptyPanel(
                title = heading,
                description = when (schedule.mode) {
                    ScheduleMode.EMPTY -> "Tambahkan mata kuliah atau aktifkan jadwal dari tab Jadwal."
                    ScheduleMode.FINISHED -> "Atur rentang semester berikutnya untuk melanjutkan jadwal dan pengingat."
                    else -> "Tidak ada pertemuan berikutnya dalam rentang semester yang dipilih."
                },
                action = if (schedule.mode != ScheduleMode.EMPTY) {
                    {
                        Button(
                            onClick = onSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = Brand100, contentColor = Brand700),
                        ) { Text("Tinjau semester") }
                    }
                } else null,
            )
        }
        item { SectionHeading("Tugas terdekat", "Lihat semua", onTasks) }
        if (nearTasks.isEmpty()) item {
            NoteSurface {
                Text("Tidak ada tugas tertunda.", fontWeight = FontWeight.Bold)
                Text("Nikmati jeda, atau catat tugas baru di tab Tugas.", color = Ink2, style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(nearTasks, key = { "task-${it.id}" }) { task ->
            TaskRow(task, data.courses, schedule.now.toLocalDate(), busy, { onTask(task.id) }, onTaskCompleted)
        }
        item { AssistantCta(onAssistant) }
        item { SectionHeading("Informasi pribadi", "Tambah", onAddNote) }
        item { Text("Catatan yang Anda isi sendiri, bukan informasi resmi dari kampus.", style = MaterialTheme.typography.bodySmall, color = Muted) }
        if (data.notes.isEmpty()) item {
            EmptyPanel(
                title = "Simpan hal yang perlu diingat",
                description = "Misalnya lokasi dosen, daftar bacaan, atau informasi administrasi.",
                action = {
                    Button(onClick = onAddNote, colors = ButtonDefaults.buttonColors(containerColor = Brand100, contentColor = Brand700)) { Text("Tambah catatan") }
                },
            )
        }
        items(data.notes.sortedByDescending { it.id }, key = { "note-${it.id}" }) { note ->
            Surface(
                onClick = { onNote(note.id) }, modifier = Modifier.fillMaxWidth(), color = Color.White,
                border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(20.dp),
                shadowElevation = 0.dp, tonalElevation = 0.dp,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp))
                    if (note.body.isNotBlank()) Text(note.body, style = MaterialTheme.typography.bodyMedium, color = Ink2, maxLines = 3)
                    Text("Edit atau hapus catatan", color = Brand600, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp))
                }
            }
        }
        item {
            Text(
                "Kampus · Ruang pribadi, sepenuhnya offline.",
                Modifier.fillMaxWidth().padding(top = 16.dp), color = Muted,
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NoteSurface(content: @Composable () -> Unit) {
    Surface(color = Color.White, border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

/** Kartu espresso besar untuk kuliah berlangsung / berikutnya, bermotif Truntum. */
@Composable
private fun OccurrenceHero(occurrence: Occurrence, label: String, zoneId: String, now: ZonedDateTime, active: Boolean, onClick: () -> Unit) {
    val course = occurrence.course
    val shape = CardShape
    val total = Duration.between(occurrence.start, occurrence.end).toMillis().coerceAtLeast(1)
    val progress = (Duration.between(occurrence.start, now).toMillis().toFloat() / total).coerceIn(0f, 1f)
    val leftMinutes = (Duration.between(now, occurrence.end).toMillis() + 59_999) / 60_000
    val untilMinutes = (Duration.between(now, occurrence.start).toMillis() + 59_999) / 60_000
    val trailing = if (active) "Sisa ${durationLabel(leftMinutes)}" else {
        relativeDay(occurrence, now) + if (occurrence.start.toLocalDate() == now.toLocalDate()) " · ${durationLabel(untilMinutes)} lagi" else ""
    }
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth().popShadow(shape),
        color = Espresso, contentColor = Color.White, shape = shape, tonalElevation = 0.dp, shadowElevation = 0.dp,
    ) {
        Box {
            BatikPattern(Modifier.matchParentSize(), color = Color.White, alpha = 0.10f)
            // Kilau oranye di pojok kanan atas.
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 48.dp, y = (-48).dp).size(176.dp)
                    .background(Brush.radialGradient(listOf(Brand500.copy(alpha = if (active) 0.45f else 0.22f), Color.Transparent)), CircleShape),
            )
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(if (active) Brand400 else Color.White.copy(alpha = 0.5f), CircleShape))
                    Text(label, color = Brand300, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp, modifier = Modifier.weight(1f, fill = false))
                    Box(Modifier.weight(1f))
                    Text(trailing, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Text(course.name, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(
                    occurrence.start.format(FullDateFormat) + if (course.section.isNotBlank()) " · Kelas ${course.section}" else "",
                    Modifier.padding(top = 4.dp), color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                )
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            buildAnnotatedString {
                                append(minuteLabel(course.startMinute))
                                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.45f))) { append("–") }
                                append(minuteLabel(course.endMinute))
                            },
                            style = TimeStyle.copy(fontSize = 34.sp, letterSpacing = (-0.8).sp), maxLines = 1,
                        )
                        Text(
                            "${zoneLabel(zoneId)} · ${durationLabel((course.endMinute - course.startMinute).toLong()).uppercase()}",
                            Modifier.padding(top = 6.dp), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("RUANG", Modifier.padding(bottom = 6.dp), color = Color.White.copy(alpha = 0.55f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        RoomBadge(course.room, large = true, onDark = true)
                    }
                }
                if (course.roomNote.isNotBlank()) {
                    Surface(Modifier.padding(top = 12.dp), color = Color.White.copy(alpha = 0.10f), contentColor = Color.White.copy(alpha = 0.85f), shape = CircleShape) {
                        Text(course.roomNote, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (active) {
                    Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(Brand400))
                    }
                }
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Buka detail mata kuliah", color = Brand300, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Brand300, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Baris ringkas "Berikutnya" saat sudah ada kuliah yang berlangsung. */
@Composable
private fun NextCompactCard(occurrence: Occurrence, label: String, now: ZonedDateTime, onClick: () -> Unit) {
    KarsaCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Brand100), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Brand700, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Eyebrow(label)
                Text(occurrence.course.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "${relativeDay(occurrence, now)} · ${minuteLabel(occurrence.course.startMinute)} · ${occurrence.course.room}",
                    style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1,
                )
            }
        }
    }
}

/** Ajakan ke tab Asisten: kartu oranye bermotif Truntum. */
@Composable
private fun AssistantCta(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).brandShadow(shape),
        color = Brand500, contentColor = Color.White, shape = shape, tonalElevation = 0.dp, shadowElevation = 0.dp,
    ) {
        Box {
            BatikPattern(Modifier.matchParentSize(), color = Color.White, alpha = 0.20f)
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Tanya Asisten", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
                    Text("“Kuliah berikutnya di ruang mana?”", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
    }
}
