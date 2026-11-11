package id.personal.campus.agent

/**
 * Pabrik model untuk layar Asisten AI.
 *
 * Satu-satunya tempat yang membuat [AgentModel] konkret. Dipisahkan agar ViewModel
 * (yang punya akses Android) bisa mengganti transport tanpa mengubah logika sesi.
 */
object AgentChatModelFactory {
    fun create(): AgentModel = OpenAiModel()
}
