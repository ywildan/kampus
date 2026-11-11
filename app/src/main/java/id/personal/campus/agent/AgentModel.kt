package id.personal.campus.agent

import kotlinx.coroutines.flow.Flow

/**
 * Tipe data bersama antara loop agen, klien jaringan, dan (nanti) UI.
 *
 * Berkas ini **bebas `android.*`** supaya loop agen bisa diuji dengan model tiruan (MockModel).
 */

enum class AgentRole(val wire: String) {
    System("system"),
    User("user"),
    Assistant("assistant"),
    Tool("tool"),
    ;

    companion object {
        fun fromWire(value: String): AgentRole? = entries.find { it.wire == value }
    }
}

data class AgentToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class AgentMessage(
    val role: AgentRole,
    val content: String = "",
    val toolCalls: List<AgentToolCall> = emptyList(),
    val toolCallId: String? = null,
)

/** Deskripsi tool yang dikirim ke model (`tools[].function`). */
data class AgentToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String,
)

sealed interface AgentEvent {
    /** Potongan teks jawaban model. */
    data class Token(val text: String) : AgentEvent

    /** Model meminta menjalankan tool; argumen sudah lengkap. */
    data class ToolCalls(val calls: List<AgentToolCall>) : AgentEvent

    data class Done(
        val finishReason: String? = null,
        /** `true` bila permintaan akhir tidak memakai `tools` (server menolaknya) dan model diminta menulis blok JSON. */
        val usedPromptFallback: Boolean = false,
    ) : AgentEvent

    data class Failure(val failure: AgentFailure) : AgentEvent
}

data class AgentRequest(
    val config: AgentConfig,
    val apiKey: String,
    val model: String,
    val messages: List<AgentMessage>,
    val tools: List<AgentToolSpec> = emptyList(),
) {
    /**
     * `toString()` sengaja tidak mencetak [apiKey] maupun isi pesan: data class bawaan akan
     * membocorkan kunci API ke log/crash report (lihat larangan §3.2 brief).
     */
    override fun toString(): String =
        "AgentRequest(baseUrl=${config.baseUrl}, model=$model, pesan=${messages.size}, tool=${tools.size})"
}

/** Abstraksi klien model supaya loop agen dapat diuji tanpa jaringan. */
interface AgentModel {
    fun stream(request: AgentRequest): Flow<AgentEvent>
}
