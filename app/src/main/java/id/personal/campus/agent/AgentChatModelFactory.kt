package id.personal.campus.agent

import java.time.ZoneId

/**
 * Pabrik model untuk layar Asisten AI.
 *
 * Satu-satunya tempat yang membuat [AgentModel] konkret. Dipisahkan agar ViewModel
 * (yang punya akses Android) bisa mengganti transport tanpa mengubah logika sesi.
 */
object AgentChatModelFactory {
    fun create(): AgentModel = OpenAiModel()

    /** Model offline berbasis aturan; zona dipakai untuk "hari ini"/"besok"/"lusa". */
    fun createOffline(zoneId: ZoneId): AgentModel = LocalAgentModel(zoneId = zoneId)
}
