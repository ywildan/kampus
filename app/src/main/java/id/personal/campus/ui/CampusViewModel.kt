package id.personal.campus.ui

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import id.personal.campus.CampusApplication
import id.personal.campus.agent.AgentConfig
import id.personal.campus.agent.AgentResult
import id.personal.campus.agent.AgentUrlResult
import id.personal.campus.agent.ModelCatalog
import id.personal.campus.agent.normalizeBaseUrl
import id.personal.campus.agent.sanitized
import id.personal.campus.data.CampusSnapshot
import id.personal.campus.data.CourseEntity
import id.personal.campus.data.NoteEntity
import id.personal.campus.data.SettingsEntity
import id.personal.campus.data.TaskEntity
import id.personal.campus.reminder.ScheduleCoordinator
import id.personal.campus.schedule.OccurrenceResolver
import id.personal.campus.schedule.ScheduleSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Channel absence is not a denial: the coordinator creates it on reconciliation. */
data class ReminderAccess(
    val runtimeGranted: Boolean = true,
    val appEnabled: Boolean = true,
    val channelEnabled: Boolean = true,
    val exactAllowed: Boolean = true,
) {
    val notificationsAllowed: Boolean get() = runtimeGranted && appEnabled && channelEnabled
}

data class CampusUiState(
    val data: CampusSnapshot? = null,
    val schedule: ScheduleSnapshot? = null,
    val loading: Boolean = true,
    val loadError: String? = null,
    val saving: Boolean = false,
    val access: ReminderAccess = ReminderAccess(),
)

class CampusViewModel(application: Application, private val savedState: SavedStateHandle) :
    AndroidViewModel(application) {
    private val app = CampusApplication.from(application)
    private val repository = app.repository
    private val resolver = OccurrenceResolver()
    private val mutableState = MutableStateFlow(CampusUiState())
    val state = mutableState.asStateFlow()
    private val mutableAgent = MutableStateFlow(AgentUiState())
    val agent = mutableAgent.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val notices = messages.receiveAsFlow()
    val navigation: StateFlow<String?> = savedState.getStateFlow("external_destination", null)
    private var loadJob: Job? = null

    init {
        load()
        loadAgentConfiguration()
    }

    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = it.data == null, loadError = null) }
            try {
                repository.initialize()
                accept(repository.snapshot())
                repository.snapshots.collect { accept(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(loading = false, loadError = "Data lokal belum dapat dibaca. Coba lagi.")
                }
                notify("Gagal membaca database. Data Anda tidak dihapus.")
            }
        }
    }

    private fun accept(snapshot: CampusSnapshot) {
        val schedule = resolver.resolve(snapshot.courses, snapshot.settings)
        mutableState.update {
            it.copy(data = snapshot, schedule = schedule, loading = false, loadError = null)
        }
    }

    /** Called only by the Activity's RESUMED lifecycle coroutine. */
    fun tick() {
        val snapshot = mutableState.value.data ?: return
        mutableState.update { it.copy(schedule = resolver.resolve(snapshot.courses, snapshot.settings)) }
    }

    fun onForeground() {
        refreshAccess()
        tick()
        refreshSchedule()
    }

    fun refreshAccess() {
        val runtimeGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            app, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val manager = app.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel("class_reminders")
        val access = ReminderAccess(
            runtimeGranted = runtimeGranted,
            appEnabled = NotificationManagerCompat.from(app).areNotificationsEnabled(),
            channelEnabled = channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE,
            exactAllowed = Build.VERSION.SDK_INT < 31 ||
                app.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        )
        mutableState.update { it.copy(access = access) }
    }

    fun openFromIntent(courseId: Long?, openSettings: Boolean) {
        savedState["external_destination"] = when {
            openSettings -> "settings"
            courseId != null && courseId > 0 -> "course:$courseId"
            else -> return
        }
    }

    fun consumeNavigation() {
        savedState["external_destination"] = null
    }

    fun notify(message: String) {
        messages.trySend(message)
    }

    private fun refreshSchedule() {
        // Use application scope so leaving an editor/activity cannot interrupt reconciliation.
        // Refreshes are not cancelled/coalesced: a mutation during an earlier refresh must be seen.
        app.applicationScope.launch {
            try {
                ScheduleCoordinator.refresh(app, deliverDue = false)
                refreshAccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notify("Data tetap tersimpan, tetapi pengingat/widget belum diperbarui. Buka ulang aplikasi untuk mencoba lagi.")
            }
        }
    }

    private fun mutate(operation: suspend () -> Unit, onSaved: () -> Unit) {
        if (mutableState.value.saving) return
        if (mutableState.value.data == null || mutableState.value.loadError != null) {
            notify("Muat ulang data melalui tombol Coba lagi sebelum menyimpan perubahan.")
            return
        }
        mutableState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notify("Perubahan belum tersimpan. Periksa isian dan coba lagi.")
                return@launch
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
            onSaved()
            refreshSchedule()
        }
    }

    fun saveCourse(course: CourseEntity, onSaved: () -> Unit = {}) {
        if (course.name.isBlank() || course.room.isBlank() || course.weekday !in 1..7 ||
            course.startMinute !in 0..1439 || course.endMinute !in 1..1439 ||
            course.startMinute >= course.endMinute
        ) {
            notify("Nama, ruang, hari, dan rentang jam kuliah harus valid.")
            return
        }
        mutate({ repository.saveCourse(course) }, onSaved)
    }

    fun deleteCourse(id: Long, onSaved: () -> Unit) = mutate({ repository.deleteCourse(id) }, onSaved)

    fun saveTask(task: TaskEntity, onSaved: () -> Unit = {}) {
        if (task.title.isBlank()) {
            notify("Judul tugas wajib diisi.")
            return
        }
        mutate({ repository.saveTask(task) }, onSaved)
    }

    fun deleteTask(id: Long, onSaved: () -> Unit) = mutate({ repository.deleteTask(id) }, onSaved)

    fun saveNote(note: NoteEntity, onSaved: () -> Unit) {
        if (note.title.isBlank()) {
            notify("Judul catatan wajib diisi.")
            return
        }
        mutate({ repository.saveNote(note) }, onSaved)
    }

    fun deleteNote(id: Long, onSaved: () -> Unit) = mutate({ repository.deleteNote(id) }, onSaved)

    fun saveSettings(settings: SettingsEntity, onSaved: () -> Unit) {
        val validZone = settings.zoneId in setOf("Asia/Jakarta", "Asia/Makassar", "Asia/Jayapura")
        val validDates = (settings.startEpochDay == null && settings.endEpochDay == null) ||
            settings.semesterConfirmed
        if (settings.semesterName.isBlank() || !validDates || !validZone ||
            settings.reminderMinutes !in listOf(5, 10, 15, 30, 60) ||
            (settings.remindersEnabled && !settings.semesterConfirmed)
        ) {
            notify("Periksa nama semester, tanggal, zona waktu, dan pengingat.")
            return
        }
        mutate({ repository.saveSettings(settings) }, onSaved)
    }

    private fun loadAgentConfiguration() {
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) {
                app.agentConfigStore.load() to app.agentSecrets.hasKey()
            }
            mutableAgent.update {
                it.copy(config = stored.first, hasKey = stored.second, loaded = true)
            }
        }
    }

    /**
     * Simpan setelan Asisten AI.
     *
     * [newApiKey] kosong/`null` = kunci lama tidak diubah, [clearKey] = hapus kunci.
     * Kunci tidak pernah masuk state UI, notifikasi, log, atau pesan error.
     */
    fun saveAgent(config: AgentConfig, newApiKey: String?, clearKey: Boolean, onSaved: () -> Unit = {}) {
        val candidate = config.sanitized()
        val url = normalizeBaseUrl(candidate.baseUrl)
        if (url is AgentUrlResult.Invalid) {
            mutableAgent.update { it.copy(message = url.message, messageOk = false, detail = null) }
            return
        }
        // Smart cast ke Valid hilang di dalam launch (lambda non-inline),
        // jadi ambil baseUrl yang sudah tervalidasi di sini.
        val validBaseUrl = (url as AgentUrlResult.Valid).baseUrl
        if (mutableAgent.value.busy) return
        mutableAgent.update { it.copy(busy = true, message = null, detail = null) }
        viewModelScope.launch {
            try {
                val normalized = candidate.copy(baseUrl = validBaseUrl)
                val hasKey = withContext(Dispatchers.IO) {
                    app.agentConfigStore.save(normalized)
                    when {
                        clearKey -> app.agentSecrets.clearKey()
                        !newApiKey.isNullOrBlank() -> app.agentSecrets.saveKey(newApiKey)
                    }
                    app.agentSecrets.hasKey()
                }
                mutableAgent.update {
                    it.copy(
                        config = normalized, hasKey = hasKey, busy = false, messageOk = true,
                        message = "Setelan Asisten AI tersimpan.", detail = null,
                    )
                }
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableAgent.update {
                    it.copy(
                        busy = false, messageOk = false, detail = null,
                        message = "Setelan Asisten AI belum tersimpan. Periksa alamat server, lalu coba lagi.",
                    )
                }
            }
        }
    }

    /** [fetchModels] `true` mengisi daftar model; `false` hanya menguji koneksi. */
    fun checkAgent(fetchModels: Boolean) {
        val url = normalizeBaseUrl(mutableAgent.value.config.baseUrl)
        if (url is AgentUrlResult.Invalid) {
            mutableAgent.update { it.copy(message = url.message, messageOk = false, detail = null) }
            return
        }
        // Smart cast ke Valid hilang di dalam launch (lambda non-inline),
        // jadi ambil baseUrl yang sudah tervalidasi di sini.
        val validBaseUrl = (url as AgentUrlResult.Valid).baseUrl
        if (mutableAgent.value.busy) return
        mutableAgent.update { it.copy(busy = true, message = null, detail = null) }
        viewModelScope.launch {
            val key = withContext(Dispatchers.IO) { app.agentSecrets.loadKey() }
            when (val result = ModelCatalog.listModels(validBaseUrl, key)) {
                is AgentResult.Success -> mutableAgent.update {
                    it.copy(
                        busy = false, messageOk = true, detail = null,
                        models = if (fetchModels) result.value else it.models,
                        message = if (result.value.isEmpty()) {
                            "Server menjawab (200) tetapi tidak mengirim daftar model. Nama model bisa diisi manual."
                        } else {
                            "Koneksi berhasil (200). ${result.value.size} model tersedia."
                        },
                    )
                }
                is AgentResult.Failure -> mutableAgent.update {
                    it.copy(
                        busy = false, messageOk = false,
                        message = result.failure.message,
                        detail = result.failure.detail.ifBlank { null },
                    )
                }
            }
        }
    }
}
