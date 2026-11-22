package id.personal.campus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.plus
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.personal.campus.agent.AgentConfig
import id.personal.campus.data.SettingsEntity
import java.time.LocalDate

/**
 * Layar Pengaturan memakai sub-menu: daftar menu dulu, tiap baris membuka satu bagian.
 * Draf (nama/tanggal/zona/pengingat) tetap hidup di level layar supaya pindah bagian
 * tidak menghilangkan perubahan yang belum disimpan.
 */
private const val SECTION_SEMESTER = "semester"
private const val SECTION_REMINDER = "pengingat"
private const val SECTION_ACCESS = "akses"
private const val SECTION_AGENT = "asisten"
private const val SECTION_ABOUT = "tentang"

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsScreen(
    settings: SettingsEntity,
    access: ReminderAccess,
    busy: Boolean,
    today: LocalDate,
    onSave: (SettingsEntity, () -> Unit) -> Unit,
    onPermissionResult: () -> Unit,
    onNotice: (String) -> Unit,
    agent: AgentUiState,
    onSaveAgent: (config: AgentConfig, newApiKey: String?, clearKey: Boolean) -> Unit,
    onCheckAgent: (config: AgentConfig, newApiKey: String?) -> Unit,
) {
    val context = LocalContext.current
    // Drafts are independent of the shared clock and survive tab switches/configuration changes.
    var name by rememberSaveable { mutableStateOf(settings.semesterName) }
    var start by rememberSaveable { mutableStateOf(settings.startEpochDay) }
    var end by rememberSaveable { mutableStateOf(settings.endEpochDay) }
    var zone by rememberSaveable { mutableStateOf(settings.zoneId) }
    var reminders by rememberSaveable { mutableStateOf(settings.remindersEnabled) }
    var offset by rememberSaveable { mutableStateOf(settings.reminderMinutes) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var requestedThisSession by rememberSaveable { mutableStateOf(false) }
    // Sub-menu yang sedang dibuka; null = daftar menu.
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onPermissionResult()
        if (!granted) onNotice("Notifikasi diblokir oleh Android. Pengingat tidak dapat tampil; Anda bisa mengubahnya di pengaturan notifikasi aplikasi.")
    }
    val draft = settings.copy(semesterName = name, startEpochDay = start, endEpochDay = end, zoneId = zone, remindersEnabled = reminders, reminderMinutes = offset)
    val validDates = (start == null && end == null) || draft.semesterConfirmed
    val valid = name.isNotBlank() && validDates && (!reminders || draft.semesterConfirmed)
    val dirty = draft != settings

    val resetDraft = {
        name = settings.semesterName
        start = settings.startEpochDay
        end = settings.endEpochDay
        zone = settings.zoneId
        reminders = settings.remindersEnabled
        offset = settings.reminderMinutes
        attempted = false
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Judul tetap di tempat; isi sub-menu berganti dengan geser + pudar ke arah yang sesuai
        // (masuk ke bagian = dari kanan, kembali ke menu = dari kiri).
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                val entering = targetState != null
                val enter = slideInHorizontally(
                    tween(KarsaMotion.Medium, easing = KarsaMotion.Emphasized),
                    initialOffsetX = { w -> if (entering) w / 8 else -w / 8 },
                ) + fadeIn(tween(KarsaMotion.Medium, easing = KarsaMotion.Standard))
                val exit = slideOutHorizontally(
                    tween(KarsaMotion.Quick + 40, easing = KarsaMotion.Exit),
                    targetOffsetX = { w -> if (entering) -w / 12 else w / 12 },
                ) + fadeOut(tween(KarsaMotion.Quick, easing = KarsaMotion.Exit))
                enter togetherWith exit
            },
            label = "settingsSection",
        ) { current ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeading("Pengaturan", "Semester, waktu, dan pengingat dalam kendali Anda.", eyebrow = "Kampus")
        if (current == null) BrandCard(settings)

        if (current == null) {
            SettingsMenuRow(
                icon = Icons.Outlined.CalendarMonth, tone = BadgeTone.Brand,
                title = "Semester & zona waktu",
                subtitle = if (settings.semesterName.isNotBlank()) settings.semesterName
                    else "Nama semester, tanggal, dan zona jadwal",
                onClick = { section = SECTION_SEMESTER },
            )
            SettingsMenuRow(
                icon = Icons.Outlined.NotificationsActive, tone = BadgeTone.Butter,
                title = "Pengingat kuliah",
                subtitle = if (settings.remindersEnabled) "Aktif · ${settings.reminderMinutes} menit sebelum mulai"
                    else "Aktifkan pengingat dan atur waktunya",
                onClick = { section = SECTION_REMINDER },
            )
            SettingsMenuRow(
                icon = Icons.Outlined.AdminPanelSettings, tone = BadgeTone.Tide,
                title = "Akses dari Android",
                subtitle = "Notifikasi dan alarm tepat waktu",
                onClick = { section = SECTION_ACCESS },
            )
            SettingsMenuRow(
                icon = Icons.Outlined.AutoAwesome, tone = BadgeTone.Plum,
                title = "Asisten AI",
                subtitle = if (agent.config.baseUrl.isNotBlank()) "Penyedia tersimpan: ${agent.config.baseUrl}"
                    else "Penyedia, kunci API, dan model",
                onClick = { section = SECTION_AGENT },
            )
            SettingsMenuRow(
                icon = Icons.Outlined.Info, tone = BadgeTone.Neutral,
                title = "Tentang & batas",
                subtitle = "Widget, data lokal, dan batas pengingat",
                onClick = { section = SECTION_ABOUT },
            )
            if (dirty) Text("Ada perubahan yang belum disimpan.", color = Brand600, style = MaterialTheme.typography.labelLarge)
            return@Column
        }

        KarsaBackRow("Kembali ke Pengaturan") { section = null }

        when (current) {
            SECTION_SEMESTER -> SemesterSection(
                settings = settings,
                today = today,
                busy = busy,
                name = name,
                onNameChange = { name = it },
                start = start,
                onStartChange = { start = it },
                end = end,
                onEndChange = { end = it },
                zone = zone,
                onZoneChange = { zone = it },
                attempted = attempted,
                validDates = validDates,
                onClearDates = { start = null; end = null; reminders = false },
                saveRow = {
                    SaveRow(
                        busy = busy,
                        dirty = dirty,
                        onSave = {
                            attempted = true
                            if (valid) {
                                val savedName = name.trim()
                                onSave(draft.copy(semesterName = savedName)) {
                                    name = savedName
                                    onNotice("Pengaturan tersimpan.")
                                }
                            } else onNotice("Pengaturan belum disimpan. Periksa nama semester dan kedua tanggal.")
                        },
                        onCancel = resetDraft,
                    )
                },
            )
            SECTION_REMINDER -> ReminderSection(
                draft = draft,
                reminders = reminders,
                offset = offset,
                busy = busy,
                access = access,
                requestedThisSession = requestedThisSession,
                onToggle = { enabled ->
                    if (enabled && !draft.semesterConfirmed) {
                        attempted = true
                        onNotice("Pilih tanggal mulai dan akhir semester yang valid sebelum mengaktifkan pengingat.")
                    } else {
                        reminders = enabled
                        if (enabled && Build.VERSION.SDK_INT >= 33 && !access.runtimeGranted && !requestedThisSession) {
                            requestedThisSession = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                },
                onOffsetChange = { offset = it },
                saveRow = {
                    SaveRow(
                        busy = busy,
                        dirty = dirty,
                        onSave = {
                            attempted = true
                            if (valid) {
                                val savedName = name.trim()
                                onSave(draft.copy(semesterName = savedName)) {
                                    name = savedName
                                    onNotice("Pengaturan tersimpan.")
                                }
                            } else onNotice("Pengaturan belum disimpan. Periksa nama semester dan kedua tanggal.")
                        },
                        onCancel = resetDraft,
                    )
                },
            )
            SECTION_ACCESS -> AndroidAccessSection(
                settings = settings,
                access = access,
                context = context,
                onNotice = onNotice,
            )
            SECTION_AGENT -> AgentSettingsSection(agent, enabled = !busy, onSave = onSaveAgent, onCheck = onCheckAgent)
            SECTION_ABOUT -> AboutSection()
        }
        }
        }
    }
}

@Composable
private fun SettingsMenuRow(icon: ImageVector, tone: BadgeTone, title: String, subtitle: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed = isPressed(interaction)
    val background by androidx.compose.animation.animateColorAsState(
        if (pressed) Paper2 else Color.White,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "menuRowBg",
    )
    // Panah bergeser 3 dp ke kanan saat baris ditekan: isyarat halus "akan membuka".
    val chevronShift by androidx.compose.animation.core.animateDpAsState(
        if (pressed) 3.dp else 0.dp,
        tween(KarsaMotion.Quick, easing = KarsaMotion.Standard),
        label = "menuChevron",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interaction,
        color = background,
        border = BorderStroke(1.dp, Line),
        shape = ListShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tone.bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tone.fg, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = if (pressed) Brand600 else Muted,
                modifier = Modifier.size(18.dp).offset(x = chevronShift),
            )
        }
    }
}

/** Baris kembali di dalam sub-menu: panah kiri dengan umpan balik tekan. */
@Composable
private fun KarsaBackRow(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale by pressScale(interaction, KarsaMotion.TapScale)
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Brand500.copy(alpha = 0.14f), bounded = true),
                role = Role.Button,
                onClick = onClick,
            )
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Outlined.ChevronLeft, contentDescription = null, tint = Brand600, modifier = Modifier.size(18.dp))
        Text(label, color = Brand600, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** Kartu merek oranye bermotif Truntum di puncak menu Pengaturan. */
@Composable
private fun BrandCard(settings: SettingsEntity) {
    val shape = CardShape
    Surface(
        modifier = Modifier.fillMaxWidth().brandShadow(shape),
        color = Brand500, contentColor = Color.White, shape = shape, tonalElevation = 0.dp, shadowElevation = 0.dp,
    ) {
        Box {
            BatikPattern(Modifier.matchParentSize(), color = Color.White, alpha = 0.20f)
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(listOf(Color.Transparent, Espresso.copy(alpha = 0.45f))),
                ),
            )
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TruntumMark(56.dp, tile = Color.White, ink = Brand500)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Kampus", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(
                        settings.semesterName.ifBlank { "Ruang pribadi, sepenuhnya offline" },
                        color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    )
                    Text(
                        if (settings.remindersEnabled) "Pengingat ${settings.reminderMinutes} menit sebelum mulai" else "Pengingat nonaktif",
                        color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Tombol simpan/batal yang dipakai bagian Semester dan Pengingat (satu draf yang sama). */
@Composable
private fun SaveRow(
    busy: Boolean,
    dirty: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    if (dirty) Text("Ada perubahan yang belum disimpan.", color = Brand600, style = MaterialTheme.typography.labelLarge)
    KarsaButton(
        if (busy) "Menyimpan…" else "Simpan pengaturan",
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        loading = busy,
        size = KarsaButtonSize.Lg,
    )
    if (dirty) KarsaTextButton("Batalkan perubahan", enabled = !busy, color = Ink2, onClick = onCancel)
}

@Composable
private fun SemesterSection(
    settings: SettingsEntity,
    today: LocalDate,
    busy: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    start: Long?,
    onStartChange: (Long?) -> Unit,
    end: Long?,
    onEndChange: (Long?) -> Unit,
    zone: String,
    onZoneChange: (String) -> Unit,
    attempted: Boolean,
    validDates: Boolean,
    onClearDates: () -> Unit,
    saveRow: @Composable () -> Unit,
) {
    PaperPanel {
        Text(if (settings.semesterConfirmed) "Semester terkonfirmasi" else "Mode pratinjau", style = MaterialTheme.typography.titleLarge)
        Text(if (settings.semesterConfirmed)
            "${dateLabel(checkNotNull(settings.startEpochDay))}–${dateLabel(checkNotNull(settings.endEpochDay))} · ${zoneLabel(settings.zoneId)}"
            else "Jadwal dapat dipakai tanpa mengaktifkan pengingat. Konfirmasi tanggal ketika sudah diketahui.")
    }
    SectionHeading("Semester & zona waktu")
    KarsaTextField(
        value = name, onValueChange = onNameChange, modifier = Modifier.fillMaxWidth(), enabled = !busy,
        label = { Text("Nama semester") }, singleLine = true, isError = attempted && name.isBlank(),
    )
    if (attempted && name.isBlank()) ValidationText("Nama semester wajib diisi.")
    DateField("Mulai semester", start, today, onStartChange, enabled = !busy, clearable = false)
    DateField("Akhir semester", end, today, onEndChange, enabled = !busy, clearable = false)
    if (!validDates) ValidationText("Pilih kedua tanggal. Tanggal akhir tidak boleh sebelum tanggal mulai.")
    Text("Kedua tanggal termasuk dalam semester. Menyimpan rentang valid berarti mengonfirmasi jadwal perkuliahan, bukan tanggal pada dokumen KRS.", style = MaterialTheme.typography.bodySmall, color = Muted)
    if (start != null || end != null) KarsaTextButton("Kosongkan tanggal · kembali ke pratinjau", onClick = onClearDates)
    ChoiceField(
        "Zona jadwal", zone,
        listOf("Asia/Jakarta" to "WIB · Asia/Jakarta", "Asia/Makassar" to "WITA · Asia/Makassar", "Asia/Jayapura" to "WIT · Asia/Jayapura"),
        enabled = !busy,
        onSelect = onZoneChange,
    )
    Text("Jam kuliah mengikuti zona ini, tidak otomatis mengikuti zona perangkat saat Anda bepergian.", style = MaterialTheme.typography.bodySmall, color = Muted)
    saveRow()
}

@Composable
private fun ReminderSection(
    draft: SettingsEntity,
    reminders: Boolean,
    offset: Int,
    busy: Boolean,
    access: ReminderAccess,
    requestedThisSession: Boolean,
    onToggle: (Boolean) -> Unit,
    onOffsetChange: (Int) -> Unit,
    saveRow: @Composable () -> Unit,
) {
    SectionHeading("Pengingat kuliah")
    ToggleField(
        "Gunakan pengingat", "Pilihan ini baru berlaku setelah disimpan. Izin Android diperiksa terpisah di bawah.",
        checked = reminders, enabled = !busy,
        onCheckedChange = onToggle,
    )
    if (reminders && !draft.semesterConfirmed) ValidationText("Pengingat memerlukan kedua tanggal semester yang valid.")
    ChoiceField("Ingatkan sebelum mulai", offset, listOf(5, 10, 15, 30, 60).map { it to "$it menit" }, enabled = !busy, onSelect = onOffsetChange)
    Text("Hanya kuliah aktif yang mendapat pengingat. Tidak ada pengingat di luar semester atau setelah kuliah mulai.", style = MaterialTheme.typography.bodySmall, color = Muted)
    saveRow()
}

@Composable
private fun AndroidAccessSection(
    settings: SettingsEntity,
    access: ReminderAccess,
    context: Context,
    onNotice: (String) -> Unit,
) {
    SectionHeading("Akses dari Android")
    PaperPanel {
        Text("Notifikasi", style = MaterialTheme.typography.titleLarge)
        Text(when {
            !access.runtimeGranted || !access.appEnabled -> "Nonaktif di Android — pengingat tidak dapat ditampilkan."
            !access.channelEnabled -> "Kanal Pengingat Kuliah dimatikan — pengingat tidak dapat ditampilkan."
            !settings.remindersEnabled -> "Izin tersedia. Pilihan pengingat aplikasi masih nonaktif."
            else -> "Izin notifikasi dan kanal tersedia. Pengingat mengikuti semester dan jadwal aktif yang tersimpan."
        })
        if (!access.notificationsAllowed) Text("Mengaktifkan pilihan aplikasi tidak mengubah izin Android. Buka pengaturan sistem untuk memberi akses; tidak ada permintaan izin berulang otomatis.", style = MaterialTheme.typography.bodySmall, color = Muted)
        KarsaButton(
            "Notifikasi aplikasi",
            onClick = {
                openSystemSettings(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName), onNotice)
            },
            modifier = Modifier.fillMaxWidth(),
            variant = KarsaButtonVariant.Outline,
        )
        KarsaButton(
            "Kanal pengingat kuliah",
            onClick = {
                openSystemSettings(context, Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, "class_reminders"), onNotice)
            },
            modifier = Modifier.fillMaxWidth(),
            variant = KarsaButtonVariant.Outline,
        )
    }
    PaperPanel {
        Text("Ketepatan waktu", style = MaterialTheme.typography.titleLarge)
        Text(if (access.exactAllowed) "Akses alarm tepat waktu tersedia." else "Pengingat perkiraan—dapat terlambat", color = if (access.exactAllowed) Brand600 else MaterialTheme.colorScheme.error)
        Text("Akses alarm tepat waktu terpisah dari notifikasi. Android tetap dapat membatasi pengiriman saat perangkat idle atau menghemat daya.", style = MaterialTheme.typography.bodyMedium)
        if (Build.VERSION.SDK_INT >= 31) KarsaButton(
            if (access.exactAllowed) "Tinjau akses alarm tepat waktu" else "Atur akses alarm tepat waktu",
            onClick = {
                openSystemSettings(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")), onNotice)
            },
            modifier = Modifier.fillMaxWidth(),
            variant = KarsaButtonVariant.Outline,
        )
    }
}

@Composable
private fun AboutSection() {
    SectionHeading("Batas yang perlu diketahui")
    Text("Pengingat tidak dapat dikirim saat HP mati atau aplikasi dipaksa berhenti (force-stop). Setelah membuka aplikasi kembali, jadwal diselaraskan; pengingat kuliah yang sudah mulai dilewati.")
    Text("Mode idle dan pembatasan Android dapat menunda alarm. Aplikasi tidak meminta pengecualian baterai dan tidak menjanjikan pengiriman tepat waktu dalam semua kondisi.")
    PaperPanel {
        Text("Widget tetap bisa dipakai", style = MaterialTheme.typography.titleLarge)
        Text("Tambahkan widget Kampus dari layar utama Android. Widget tidak membutuhkan izin notifikasi. Tanpa alarm tepat waktu atau saat perangkat tidur, pergantian jadwal pada widget dapat terlambat.")
    }
    Text("Data jadwal, tugas, dan catatan disimpan lokal. Tidak ada login, pengambilan informasi kampus otomatis, atau sinkronisasi awan. Menghapus data aplikasi atau mencopot aplikasi akan menghapus data pribadi Anda.", style = MaterialTheme.typography.bodySmall, color = Muted)
}

private fun openSystemSettings(context: Context, intent: Intent, onNotice: (String) -> Unit) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        openAppDetails(context, onNotice)
    } catch (_: SecurityException) {
        openAppDetails(context, onNotice)
    }
}

private fun openAppDetails(context: Context, onNotice: (String) -> Unit) {
    try {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        onNotice("Halaman khusus tidak tersedia. Gunakan pengaturan aplikasi Android.")
    } catch (_: ActivityNotFoundException) {
        onNotice("Buka Setelan Android → Aplikasi → Kampus untuk mengubah izin.")
    } catch (_: SecurityException) {
        onNotice("Android tidak mengizinkan halaman ini dibuka. Ubah izin melalui Setelan Android.")
    }
}
