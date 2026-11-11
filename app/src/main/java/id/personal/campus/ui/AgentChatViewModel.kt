package id.personal.campus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.personal.campus.CampusApplication
import id.personal.campus.agent.AgentChatModelFactory
import id.personal.campus.agent.AgentMessage
import id.personal.campus.agent.AgentRole
import id.personal.campus.agent.AgentRunRequest
import id.personal.campus.agent.AgentSession
import id.personal.campus.agent.AgentSessionEvent
import id.personal.campus.agent.AgentSkill
import id.personal.campus.agent.AgentSnapshotSource
import id.personal.campus.agent.AgentToolRegistry
import id.personal.campus.agent.AgentTools
import id.personal.campus.agent.AgentWriteProposal
import id.personal.campus.agent.AgentWriteTools
import id.personal.campus.agent.AgentUrlResult
import id.personal.campus.agent.normalizeBaseUrl
import id.personal.campus.data.AgentRepositoryWriteStore
import id.personal.campus.reminder.ScheduleCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel layar Asisten AI.
 *
 * Tanggung jawabnya hanya menjembatani: config + kunci (dari Setelan) → [AgentSession] →
 * [AgentChatReducer]. Semua aturan percakapan ada di reducer, dan semua aturan data ada di
 * paket `agent` — jadi layar ini tidak menambah logika baru.
 *
 * Invarian yang dijaga di sini:
 * - Kunci API hanya dibaca sesaat sebelum satu permintaan, tidak disimpan di state UI
 *   ([AgentChatState] tidak punya field kunci sama sekali).
 * - Setiap tulisan melewati `AgentRepositoryWriteStore` → jalur `ScheduleCoordinator.refresh`
 *   yang sama dengan fitur manual, sehingga widget/pengingat ikut menyesuaikan.
 */
class AgentChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CampusApplication
    private val configStore = app.agentConfigStore
    private val secrets = app.agentSecrets

    /** Data lewat sumber yang sama dengan layar lain: `CampusRepository`. */
    private val readTools = AgentTools(AgentSnapshotSource { app.repository.snapshot() })

    private val writeTools = AgentWriteTools(
        store = AgentRepositoryWriteStore(app.repository) {
            ScheduleCoordinator.refresh(app, deliverDue = false)
        },
    )

    private val session = AgentSession(
        model = AgentChatModelFactory.create(),
        registry = AgentToolRegistry(readTools.tools + writeTools.tools),
    )

    private val mutableState = MutableStateFlow(AgentChatState())
    val state: StateFlow<AgentChatState> = mutableState.asStateFlow()

    /** Riwayat percakapan selama sesi hidup; dipangkas lagi oleh `AgentLimits`. */
    private val history = ArrayList<AgentMessage>()

    private var job: Job? = null
    private var decision: CompletableDeferred<Boolean>? = null

    init {
        refreshSetup()
    }

    fun onInput(text: String) = reduce(AgentChatEvent.InputChanged(text))

    fun chooseSkill(skill: AgentSkill) = reduce(AgentChatEvent.SkillChosen(skill))

    fun chooseModelMode(mode: AgentChatModelMode) = reduce(AgentChatEvent.ModelModeChosen(mode))

    fun consumeNotice() = reduce(AgentChatEvent.NoticeShown)

    /** Dipanggil ulang saat user kembali dari Setelan supaya status kesiapan tidak basi. */
    fun refreshSetup() {
        viewModelScope.launch {
            val config = withContext(Dispatchers.IO) { configStore.load() }
            val hasKey = withContext(Dispatchers.IO) { secrets.hasKey() }
            val problem = when {
                config.baseUrl.isBlank() -> "Alamat server belum diisi."
                normalizeBaseUrl(config.baseUrl) is AgentUrlResult.Invalid ->
                    (normalizeBaseUrl(config.baseUrl) as AgentUrlResult.Invalid).message
                !config.hasModel -> "Nama model belum diisi."
                !hasKey -> "Kunci API belum disimpan."
                else -> null
            }
            reduce(
                AgentChatEvent.SetupChanged(
                    AgentSetup(
                        ready = problem == null,
                        hasKey = hasKey,
                        baseUrl = config.baseUrl,
                        fastModel = config.effectiveFast,
                        smartModel = config.effectiveSmart,
                        problem = problem,
                    ),
                ),
            )
        }
    }

    fun send() {
        val text = mutableState.value.input.trim()
        if (text.isEmpty() || job?.isActive == true) return

        val setup = mutableState.value.setup
        if (!setup.ready) {
            reduce(AgentChatEvent.Notice(setup.problem ?: "Lengkapi Setelan Asisten AI dulu."))
            return
        }

        val skill = mutableState.value.skill
        val mode = mutableState.value.modelMode
        // Nilai dibaca sekali di awal sesi supaya perubahan Setelan di tengah jalan tidak
        // membuat percakapan berpindah model tanpa disadari.
        val config = configStore.load()
        val selection = mode.resolveModels(config)

        job = viewModelScope.launch {
            reduce(AgentChatEvent.Sent(text))
            history += AgentMessage(role = AgentRole.User, content = text)

            val apiKey = withContext(Dispatchers.IO) { secrets.loadKey().orEmpty() }
            val zoneId = withContext(Dispatchers.IO) { app.repository.snapshot().settings.zoneId }

            val request = AgentRunRequest(
                config = config,
                apiKey = apiKey,
                messages = history.toList(),
                toolModel = selection.toolModel,
                answerModel = selection.answerModel,
                skill = skill,
                zoneId = zoneId,
                confirmWrite = { proposal -> awaitDecision(proposal) },
            )

            try {
                session.run(request).collect { event -> handle(event) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Jaring terakhir: hanya pesan ramah yang sampai ke user. Pesan exception
                // mentah (nama kelas, konteks coroutine) tidak pernah ditampilkan — dulu
                // `unexpected.message` bocor ke baris notice sebagai teks "Flow exception
                // transparency is violated..." yang tidak bisa ditindaklanjuti user.
                reduce(
                    AgentChatEvent.Failed(
                        "Terjadi kesalahan tak terduga saat menghubungi model. Coba lagi.",
                    ),
                )
            }
        }
    }

    /** Tombol "Coba lagi" pada balon gagal: kirim ulang pesan user terakhir. */
    fun retry() {
        if (job?.isActive == true) return
        val lastUser = history.lastOrNull { it.role == AgentRole.User }?.content?.trim().orEmpty()
        if (lastUser.isEmpty()) return
        reduce(AgentChatEvent.InputChanged(lastUser))
        send()
    }

    /** Tombol "Berhenti": membatalkan permintaan yang sedang menunggu dan menutup kartu. */
    fun stop() {
        decision?.complete(false)
        job?.cancel()
        reduce(AgentChatEvent.Stopped)
    }

    fun approve() {
        decision?.complete(true)
    }

    fun deny() {
        decision?.complete(false)
    }

    /** Undo hanya untuk aksi tulis terakhir; tidak ada tool hapus yang bisa dipanggil model. */
    fun undoLastWrite() {
        viewModelScope.launch {
            val outcome = writeTools.undoLast()
            if (outcome == null) {
                reduce(AgentChatEvent.Notice("Tidak ada perubahan yang bisa dibatalkan."))
            } else {
                val label = listOf(outcome.title, outcome.detail).filter { it.isNotBlank() }.joinToString(". ")
                reduce(AgentChatEvent.Undone(label))
            }
            refreshSetup()
        }
    }

    private suspend fun awaitDecision(proposal: AgentWriteProposal): Boolean {
        val gate = CompletableDeferred<Boolean>()
        decision = gate
        try {
            return gate.await()
        } finally {
            decision = null
        }
    }

    private suspend fun handle(event: AgentSessionEvent) {
        when (event) {
            is AgentSessionEvent.Token -> reduce(AgentChatEvent.Token(event.text))

            AgentSessionEvent.ResetStream -> reduce(AgentChatEvent.ResetStream)

            is AgentSessionEvent.ToolStarted -> reduce(AgentChatEvent.ToolStarted(event.call.name))

            is AgentSessionEvent.ToolFinished ->
                reduce(AgentChatEvent.ToolFinished(event.name, event.cancelled))

            // Kartu juga dipasang di sini supaya tampil tepat saat keputusan diminta.
            is AgentSessionEvent.WriteProposed -> reduce(AgentChatEvent.Proposed(event.proposal))

            is AgentSessionEvent.WriteApplied -> reduce(AgentChatEvent.Applied(event.summary))

            is AgentSessionEvent.Completed -> {
                history += AgentMessage(role = AgentRole.Assistant, content = event.text)
                reduce(
                    AgentChatEvent.Completed(
                        text = event.text,
                        trace = event.trace.describe(),
                        stoppedByLimit = event.stoppedByLimit,
                    ),
                )
            }

            is AgentSessionEvent.Failed -> reduce(
                AgentChatEvent.Failed(
                    message = event.failure.message,
                    detail = event.failure.detail.ifBlank { null },
                ),
            )
        }
    }

    private fun reduce(event: AgentChatEvent) {
        mutableState.value = AgentChatReducer.reduce(mutableState.value, event)
    }
}
