package id.personal.campus.ui

import id.personal.campus.agent.AgentConfig
import id.personal.campus.agent.AgentWriteProposal
import id.personal.campus.agent.LocalAgentModel

/**
 * Status layar Asisten AI sebagai data biasa.
 *
 * Berkas ini sengaja **bebas Compose/`android.*`** dan seluruh perubahannya lewat [AgentChatReducer],
 * sehingga alur streaming, panel rencana, dan kartu konfirmasi bisa diuji di CI JVM tanpa perangkat.
 */

enum class AgentChatRole { USER, ASSISTANT }

enum class AgentPlanStatus { RUNNING, DONE, CANCELLED }

/**
 * Model yang dipakai untuk satu pesan.
 *
 * Hemat mempertahankan pembagian lama (cepat untuk alat, pintar untuk jawaban); Pintar/Cepat
 * memakai satu model untuk semua langkah; Offline tidak memakai model online sama sekali —
 * jawaban dirangkai [id.personal.campus.agent.LocalAgentModel] dari data lokal.
 * Pilihan ini hanya hidup di sesi chat, bukan setelan.
 */
enum class AgentChatModelMode(val label: String) {
    HEMAT("Hemat"),
    PINTAR("Pintar"),
    CEPAT("Cepat"),
    OFFLINE("Offline"),
}

/** Pasangan model untuk [id.personal.campus.agent.AgentRunRequest]. */
data class AgentModelSelection(val toolModel: String, val answerModel: String)

fun AgentChatModelMode.resolveModels(config: AgentConfig): AgentModelSelection = when (this) {
    AgentChatModelMode.HEMAT -> AgentModelSelection(config.effectiveFast, config.effectiveSmart)
    AgentChatModelMode.PINTAR -> AgentModelSelection(config.effectiveSmart, config.effectiveSmart)
    AgentChatModelMode.CEPAT -> AgentModelSelection(config.effectiveFast, config.effectiveFast)
    // Nama sentinel: model offline mengabaikan nama model dan menjawab dari data lokal.
    AgentChatModelMode.OFFLINE -> AgentModelSelection(
        LocalAgentModel.MODEL_NAME,
        LocalAgentModel.MODEL_NAME,
    )
}

/** Perintah lokal yang dijalankan aplikasi, tidak dikirim ke model. */
internal sealed interface AgentChatCommand {
    /** `enabled = null` berarti toggle status auto-approval saat ini. */
    data class AutoApproval(val enabled: Boolean?) : AgentChatCommand
}

/**
 * `/autoapproval` (toggle), `/autoapproval on`, `/autoapproval off`.
 * Teks lain — termasuk slash command yang tidak dikenal — tetap dikirim sebagai pesan biasa.
 */
internal fun parseAgentCommand(text: String): AgentChatCommand? {
    val parts = text.trim().split(Regex("\\s+"), limit = 2)
    if (!parts.firstOrNull().orEmpty().equals("/autoapproval", ignoreCase = true)) return null
    val argument = parts.getOrNull(1)?.trim()?.lowercase().orEmpty()
    return when (argument) {
        "" -> AgentChatCommand.AutoApproval(null)
        "on" -> AgentChatCommand.AutoApproval(true)
        "off" -> AgentChatCommand.AutoApproval(false)
        else -> null
    }
}

/** Satu langkah di panel "rencana aksi" (tool apa yang sedang/baru dijalankan). */
data class AgentPlanStep(val label: String, val status: AgentPlanStatus)

/**
 * Satu balon percakapan.
 *
 * [failure] hanya diisi bila balon ini berisi pesan kesalahan — teksnya sudah disensor
 * (`AgentFailure` + `redact`) jauh sebelum sampai ke sini.
 */
data class AgentChatMessage(
    val id: Long,
    val role: AgentChatRole,
    val text: String = "",
    val streaming: Boolean = false,
    val trace: String? = null,
    val failureDetail: String? = null,
    val stopped: Boolean = false,
    val undoLabel: String? = null,
)

/** Kesiapan konfigurasi Asisten. `problem` menjelaskan apa yang masih kurang. */
data class AgentSetup(
    val ready: Boolean = false,
    val hasKey: Boolean = false,
    val baseUrl: String = "",
    val fastModel: String = "",
    val smartModel: String = "",
    val problem: String? = null,
)

data class AgentChatState(
    val messages: List<AgentChatMessage> = emptyList(),
    val input: String = "",
    /** Status kesiapan konfigurasi; diisi ViewModel dari Setelan. */
    val setup: AgentSetup = AgentSetup(),
    /** Cara memilih model untuk pesan berikutnya; default mengikuti perilaku lama. */
    val modelMode: AgentChatModelMode = AgentChatModelMode.HEMAT,
    /** Bila true, tool tulis langsung diterapkan tanpa kartu persetujuan (selama sesi ini). */
    val autoApproveWrites: Boolean = false,
    val busy: Boolean = false,
    /** Terisi hanya saat kartu konfirmasi tampil; user wajib memilih Setuju/Batal. */
    val proposal: AgentWriteProposal? = null,
    val plan: List<AgentPlanStep> = emptyList(),
    /** Aksi tulis terakhir yang masih bisa dibatalkan. */
    val undoLabel: String? = null,
    val notice: String? = null,
) {
    /** Tombol kirim aktif hanya saat tidak sibuk dan input tidak kosong. */
    val canSend: Boolean get() = !busy && input.isNotBlank()

    /** Kartu konfirmasi memblokir input lain supaya tidak ada dua keputusan sekaligus. */
    val awaitingDecision: Boolean get() = proposal != null
}

/** Kejadian yang mengubah layar; dipetakan dari `AgentSessionEvent` + aksi user. */
sealed interface AgentChatEvent {
    data class InputChanged(val text: String) : AgentChatEvent
    data class SetupChanged(val setup: AgentSetup) : AgentChatEvent
    data class ModelModeChosen(val mode: AgentChatModelMode) : AgentChatEvent
    data class AutoApproveChanged(val enabled: Boolean) : AgentChatEvent

    /** Pesan singkat tanpa mengubah percakapan (mis. "belum siap", "tidak ada yang bisa dibatalkan"). */
    data class Notice(val text: String) : AgentChatEvent
    data class Sent(val text: String) : AgentChatEvent
    data class Token(val text: String) : AgentChatEvent
    data object ResetStream : AgentChatEvent
    data class ToolStarted(val tool: String) : AgentChatEvent
    data class ToolFinished(val tool: String, val cancelled: Boolean) : AgentChatEvent
    data class Proposed(val proposal: AgentWriteProposal) : AgentChatEvent
    data class Applied(val summary: String) : AgentChatEvent
    data class Completed(val text: String, val trace: String, val stoppedByLimit: Boolean) : AgentChatEvent
    data class Failed(val message: String, val detail: String? = null) : AgentChatEvent
    data object Stopped : AgentChatEvent
    data class Undone(val label: String) : AgentChatEvent
    data object NoticeShown : AgentChatEvent
}

/**
 * Pemetaan nama tool → kalimat panel rencana.
 *
 * Ini **bukan** logika data: hanya label tampilan supaya user tahu agen sedang membaca apa.
 * Kalimatnya sama dengan istilah di `AgentTrace`, jadi jejak dan panel rencana tidak bertentangan.
 */
fun agentToolLabel(tool: String): String = when (tool) {
    "kelas_hari" -> "membaca jadwal hari itu"
    "kelas_berikutnya" -> "mencari kelas berikutnya"
    "jadwal_seminggu" -> "membaca jadwal seminggu"
    "cari_ruangan" -> "mencari ruangan"
    "daftar_tugas" -> "membaca daftar tugas"
    "cari_catatan" -> "mencari catatan"
    "tambah_tugas" -> "menyiapkan tugas baru"
    "ubah_jadwal" -> "menyiapkan perubahan jadwal"
    "tambah_catatan" -> "menyiapkan catatan baru"
    "tandai_tugas_selesai" -> "menandai tugas selesai"
    else -> "menjalankan $tool"
}

/**
 * Semua perubahan layar ada di sini — satu fungsi murni tanpa efek samping.
 *
 * Aturan yang dijaga (lihat brief §6):
 * - Satu jawaban = satu balon: [AgentChatEvent.Completed] **mengganti** draf streaming, bukan menambah balon.
 * - Kartu konfirmasi hanya hilang setelah keputusan; [AgentChatEvent.Proposed] tidak menutup sesi.
 * - [AgentChatEvent.Failed] menutup sesi dan membuang kartu konfirmasi (tidak ada perubahan tersimpan).
 */
object AgentChatReducer {

    fun reduce(state: AgentChatState, event: AgentChatEvent): AgentChatState = when (event) {
        is AgentChatEvent.InputChanged -> state.copy(input = event.text)

        is AgentChatEvent.SetupChanged -> state.copy(setup = event.setup)

        is AgentChatEvent.ModelModeChosen -> state.copy(modelMode = event.mode)

        is AgentChatEvent.AutoApproveChanged -> state.copy(autoApproveWrites = event.enabled)

        is AgentChatEvent.Notice -> state.copy(notice = event.text)

        is AgentChatEvent.Sent -> state.copy(
            messages = state.messages +
                AgentChatMessage(id = state.nextId(), role = AgentChatRole.USER, text = event.text.trim()),
            input = "",
            busy = true,
            proposal = null,
            plan = emptyList(),
            notice = null,
        )

        is AgentChatEvent.Token -> {
            // Jawaban teks murni tidak melewati ToolStarted, jadi balonnya belum ada.
            // Tanpa ini seluruh token dibuang dan jawaban tanpa tool tidak pernah tampil.
            val base = state.copy(messages = state.ensureStreaming())
            val id = base.streamingId() ?: return base.copy(busy = true)
            base.updateMessage(id) { it.copy(text = it.text + event.text) }
        }

        AgentChatEvent.ResetStream -> {
            val id = state.streamingId() ?: return state
            state.updateMessage(id) { it.copy(text = "") }
        }

        is AgentChatEvent.ToolStarted -> state.copy(
            messages = state.ensureStreaming(),
            plan = state.plan + AgentPlanStep(agentToolLabel(event.tool), AgentPlanStatus.RUNNING),
        )

        is AgentChatEvent.ToolFinished -> state.copy(
            plan = state.finishStep(event.tool, event.cancelled),
        )

        is AgentChatEvent.Proposed -> state.copy(proposal = event.proposal)

        is AgentChatEvent.Applied -> state.copy(proposal = null, undoLabel = event.summary)

        is AgentChatEvent.Completed -> {
            val withBubble = state.copy(messages = state.ensureStreaming())
            val id = withBubble.streamingId()
            val text = event.text.ifBlank { "Tidak ada jawaban yang bisa saya susun." }
            val base = if (id == null) withBubble else withBubble.updateMessage(id) {
                it.copy(text = text, streaming = false, trace = event.trace)
            }
            base.copy(
                busy = false,
                proposal = null,
                plan = base.plan.map { it.copy(status = AgentPlanStatus.DONE) },
                notice = if (event.stoppedByLimit) LIMIT_NOTICE else null,
            )
        }

        is AgentChatEvent.Failed -> {
            val id = state.streamingId()
            val base = if (id == null) {
                // Gagal sebelum satu token pun: tetap tampilkan balon supaya pesan tidak hilang.
                state.copy(
                    messages = state.messages + AgentChatMessage(
                        id = state.nextId(),
                        role = AgentChatRole.ASSISTANT,
                        text = event.message,
                        failureDetail = event.detail,
                    ),
                )
            } else {
                state.updateMessage(id) {
                    it.copy(text = event.message, failureDetail = event.detail, streaming = false)
                }
            }
            base.copy(
                busy = false,
                proposal = null,
                plan = emptyList(),
                notice = if (event.detail.isNullOrBlank()) event.message else "${event.message} ${event.detail}",
            )
        }

        AgentChatEvent.Stopped -> {
            val id = state.streamingId()
            val base = if (id == null) state else state.updateMessage(id) {
                it.copy(streaming = false, stopped = true)
            }
            base.copy(busy = false, proposal = null, notice = "Dihentikan. Tidak ada perubahan yang disimpan.")
        }

        is AgentChatEvent.Undone -> state.copy(
            undoLabel = null,
            notice = event.label,
        )

        AgentChatEvent.NoticeShown -> state.copy(notice = null)
    }

    private const val LIMIT_NOTICE =
        "Agen berhenti setelah batas langkah. Coba persempit pertanyaannya."

    private fun AgentChatState.nextId(): Long = (messages.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun AgentChatState.streamingId(): Long? =
        messages.lastOrNull { it.role == AgentChatRole.ASSISTANT && it.streaming }?.id

    /** Balon asisten dibuat sekali, saat sinyal pertama datang (token/tool/selesai). */
    private fun AgentChatState.ensureStreaming(): List<AgentChatMessage> =
        if (streamingId() != null) {
            messages
        } else {
            messages + AgentChatMessage(
                id = nextId(),
                role = AgentChatRole.ASSISTANT,
                streaming = true,
            )
        }

    private fun AgentChatState.updateMessage(
        id: Long,
        transform: (AgentChatMessage) -> AgentChatMessage,
    ): AgentChatState = copy(messages = messages.map { if (it.id == id) transform(it) else it })

    /** Menutup langkah terakhir yang masih RUNNING untuk tool ini. */
    private fun AgentChatState.finishStep(tool: String, cancelled: Boolean): List<AgentPlanStep> {
        val label = agentToolLabel(tool)
        val index = plan.indexOfLast { it.status == AgentPlanStatus.RUNNING && it.label == label }
        if (index < 0) return plan
        val status = if (cancelled) AgentPlanStatus.CANCELLED else AgentPlanStatus.DONE
        return plan.mapIndexed { position, step ->
            if (position == index) step.copy(status = status) else step
        }
    }
}
