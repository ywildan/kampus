package id.personal.campus.agent

import android.content.Context

/**
 * Setelan Asisten AI yang **bukan rahasia**: base URL, nama model, temperature, maxTokens.
 *
 * Sengaja bukan Room: menambah kolom ke `SettingsEntity` berarti migrasi skema database
 * (risiko bagi data jadwal user), dan kunci API memang dilarang ada di SQLite.
 * Untuk kunci API, lihat [AgentSecrets].
 */
class AgentConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): AgentConfig = AgentConfig(
        baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
        modelCepat = prefs.getString(KEY_MODEL_FAST, "").orEmpty(),
        modelPintar = prefs.getString(KEY_MODEL_SMART, "").orEmpty(),
        temperature = prefs.getString(KEY_TEMPERATURE, null)?.toDoubleOrNull() ?: AGENT_DEFAULT_TEMPERATURE,
        maxTokens = prefs.getInt(KEY_MAX_TOKENS, AGENT_DEFAULT_MAX_TOKENS),
    ).sanitized()

    fun save(config: AgentConfig) {
        val safe = config.sanitized()
        prefs.edit()
            .putString(KEY_BASE_URL, safe.baseUrl)
            .putString(KEY_MODEL_FAST, safe.modelCepat)
            .putString(KEY_MODEL_SMART, safe.modelPintar)
            .putString(KEY_TEMPERATURE, safe.temperature.toString())
            .putInt(KEY_MAX_TOKENS, safe.maxTokens)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "agent_config"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL_FAST = "model_cepat"
        const val KEY_MODEL_SMART = "model_pintar"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_MAX_TOKENS = "max_tokens"
    }
}
