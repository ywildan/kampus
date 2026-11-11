package id.personal.campus.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.personal.campus.agent.AgentConfig
import id.personal.campus.data.SettingsEntity
import java.time.LocalDate

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
    onCheckAgent: (fetchModels: Boolean) -> Unit,
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
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onPermissionResult()
        if (!granted) onNotice("Notifikasi diblokir oleh Android. Pengingat tidak dapat tampil; Anda bisa mengubahnya di pengaturan notifikasi aplikasi.")
    }
    val draft = settings.copy(semesterName = name, startEpochDay = start, endEpochDay = end, zoneId = zone, remindersEnabled = reminders, reminderMinutes = offset)
    val validDates = (start == null && end == null) || draft.semesterConfirmed
    val valid = name.isNotBlank() && validDates && (!reminders || draft.semesterConfirmed)
    val dirty = draft != settings

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeading("Pengaturan", "Semester, waktu, dan pengingat dalam kendali Anda.")
        PaperPanel {
            Text(if (settings.semesterConfirmed) "Semester terkonfirmasi" else "Mode pratinjau", style = MaterialTheme.typography.titleLarge)
            Text(if (settings.semesterConfirmed)
                "${dateLabel(checkNotNull(settings.startEpochDay))}–${dateLabel(checkNotNull(settings.endEpochDay))} · ${zoneLabel(settings.zoneId)}"
                else "Jadwal dapat dipakai tanpa mengaktifkan pengingat. Konfirmasi tanggal ketika sudah diketahui.")
        }
        SectionHeading("Semester & zona waktu")
        OutlinedTextField(
            value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
            label = { Text("Nama semester") }, singleLine = true, isError = attempted && name.isBlank(),
        )
        if (attempted && name.isBlank()) ValidationText("Nama semester wajib diisi.")
        DateField("Mulai semester", start, today, { start = it }, enabled = !busy, clearable = false)
        DateField("Akhir semester", end, today, { end = it }, enabled = !busy, clearable = false)
        if (!validDates) ValidationText("Pilih kedua tanggal. Tanggal akhir tidak boleh sebelum tanggal mulai.")
        Text("Kedua tanggal termasuk dalam semester. Menyimpan rentang valid berarti mengonfirmasi jadwal perkuliahan, bukan tanggal pada dokumen KRS.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
        if (start != null || end != null) TextButton(enabled = !busy, onClick = { start = null; end = null; reminders = false }) {
            Text("Kosongkan tanggal · kembali ke pratinjau")
        }
        ChoiceField(
            "Zona jadwal", zone,
            listOf("Asia/Jakarta" to "WIB · Asia/Jakarta", "Asia/Makassar" to "WITA · Asia/Makassar", "Asia/Jayapura" to "WIT · Asia/Jayapura"),
            enabled = !busy,
        ) { zone = it }
        Text("Jam kuliah mengikuti zona ini, tidak otomatis mengikuti zona perangkat saat Anda bepergian.", style = MaterialTheme.typography.bodySmall, color = MutedInk)

        SectionHeading("Pengingat kuliah")
        ToggleField(
            "Gunakan pengingat", "Pilihan ini baru berlaku setelah disimpan. Izin Android diperiksa terpisah di bawah.",
            checked = reminders, enabled = !busy,
        ) { enabled ->
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
        }
        if (reminders && !draft.semesterConfirmed) ValidationText("Pengingat memerlukan kedua tanggal semester yang valid.")
        ChoiceField("Ingatkan sebelum mulai", offset, listOf(5, 10, 15, 30, 60).map { it to "$it menit" }, enabled = !busy) { offset = it }
        Text("Hanya kuliah aktif yang mendapat pengingat. Tidak ada pengingat di luar semester atau setelah kuliah mulai.", style = MaterialTheme.typography.bodySmall, color = MutedInk)

        if (dirty) Text("Ada perubahan yang belum disimpan.", color = CampusGreen, style = MaterialTheme.typography.labelLarge)
        Button(
            enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            onClick = {
                attempted = true
                if (valid) {
                    val savedName = name.trim()
                    onSave(draft.copy(semesterName = savedName)) {
                        name = savedName
                        onNotice("Pengaturan tersimpan.")
                    }
                } else onNotice("Pengaturan belum disimpan. Periksa nama semester dan kedua tanggal.")
            },
        ) { Text(if (busy) "Menyimpan…" else "Simpan pengaturan") }
        if (dirty) TextButton(enabled = !busy, onClick = {
            name = settings.semesterName
            start = settings.startEpochDay
            end = settings.endEpochDay
            zone = settings.zoneId
            reminders = settings.remindersEnabled
            offset = settings.reminderMinutes
            attempted = false
        }) { Text("Batalkan perubahan") }

        SectionHeading("Akses dari Android")
        PaperPanel {
            Text("Notifikasi", style = MaterialTheme.typography.titleLarge)
            Text(when {
                !access.runtimeGranted || !access.appEnabled -> "Nonaktif di Android — pengingat tidak dapat ditampilkan."
                !access.channelEnabled -> "Kanal Pengingat Kuliah dimatikan — pengingat tidak dapat ditampilkan."
                !settings.remindersEnabled -> "Izin tersedia. Pilihan pengingat aplikasi masih nonaktif."
                else -> "Izin notifikasi dan kanal tersedia. Pengingat mengikuti semester dan jadwal aktif yang tersimpan."
            })
            if (!access.notificationsAllowed) Text("Mengaktifkan pilihan aplikasi tidak mengubah izin Android. Buka pengaturan sistem untuk memberi akses; tidak ada permintaan izin berulang otomatis.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
            OutlinedButton(onClick = {
                openSystemSettings(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName), onNotice)
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Notifikasi aplikasi") }
            OutlinedButton(onClick = {
                openSystemSettings(context, Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, "class_reminders"), onNotice)
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Kanal pengingat kuliah") }
        }
        PaperPanel {
            Text("Ketepatan waktu", style = MaterialTheme.typography.titleLarge)
            Text(if (access.exactAllowed) "Akses alarm tepat waktu tersedia." else "Pengingat perkiraan—dapat terlambat", color = if (access.exactAllowed) CampusGreen else MaterialTheme.colorScheme.error)
            Text("Akses alarm tepat waktu terpisah dari notifikasi. Android tetap dapat membatasi pengiriman saat perangkat idle atau menghemat daya.", style = MaterialTheme.typography.bodyMedium)
            if (Build.VERSION.SDK_INT >= 31) OutlinedButton(
                onClick = {
                    openSystemSettings(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")), onNotice)
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (access.exactAllowed) "Tinjau akses alarm tepat waktu" else "Atur akses alarm tepat waktu") }
        }
        AgentSettingsSection(agent, enabled = !busy, onSave = onSaveAgent, onCheck = onCheckAgent)

        SectionHeading("Batas yang perlu diketahui")
        Text("Pengingat tidak dapat dikirim saat HP mati atau aplikasi dipaksa berhenti (force-stop). Setelah membuka aplikasi kembali, jadwal diselaraskan; pengingat kuliah yang sudah mulai dilewati.")
        Text("Mode idle dan pembatasan Android dapat menunda alarm. Aplikasi tidak meminta pengecualian baterai dan tidak menjanjikan pengiriman tepat waktu dalam semua kondisi.")
        PaperPanel {
            Text("Widget tetap bisa dipakai", style = MaterialTheme.typography.titleLarge)
            Text("Tambahkan widget Kampus dari layar utama Android. Widget tidak membutuhkan izin notifikasi. Tanpa alarm tepat waktu atau saat perangkat tidur, pergantian jadwal pada widget dapat terlambat.")
        }
        Text("Data jadwal, tugas, dan catatan disimpan lokal. Tidak ada login, pengambilan informasi kampus otomatis, atau sinkronisasi awan. Menghapus data aplikasi atau mencopot aplikasi akan menghapus data pribadi Anda.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
    }
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
