package id.personal.campus.agent

import java.net.URI
import java.net.URISyntaxException

/**
 * Konfigurasi Asisten AI (BYOK = bawa kunci sendiri).
 *
 * Berkas ini **bebas `android.*`** supaya bisa diuji lewat unit test JVM di CI.
 * Kunci API sengaja TIDAK ada di sini: kunci hanya hidup di [AgentSecrets]
 * (terenkripsi) dan di memori sesaat ketika user mengetiknya.
 */
/** Penyedia OpenAI-compatible buatan user: nama tampilan + base URL, tersimpan di perangkat. */
data class CustomProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
)

data class AgentConfig(
    val baseUrl: String = "",
    val modelCepat: String = "",
    val modelPintar: String = "",
    val temperature: Double = AGENT_DEFAULT_TEMPERATURE,
    val maxTokens: Int = AGENT_DEFAULT_MAX_TOKENS,
    /** Penyedia custom yang disimpan user; kosong bila belum ada. */
    val customProviders: List<CustomProvider> = emptyList(),
) {
    /** Model untuk pemilihan/pemanggilan tool (Tahap 5 memakai ini). */
    val effectiveFast: String get() = modelCepat.ifBlank { modelPintar }

    /** Model untuk penulisan jawaban akhir. Bila user hanya mengisi satu nilai, keduanya sama. */
    val effectiveSmart: String get() = modelPintar.ifBlank { modelCepat }

    val hasModel: Boolean get() = effectiveFast.isNotBlank()

    /** Path API yang dipakai klien; `null` bila [baseUrl] belum valid. */
    val modelsUrl: String? get() = normalizedOrNull()?.let { "$it/models" }
    val chatCompletionsUrl: String? get() = normalizedOrNull()?.let { "$it/chat/completions" }

    private fun normalizedOrNull(): String? =
        (normalizeBaseUrl(baseUrl) as? AgentUrlResult.Valid)?.baseUrl
}

const val AGENT_DEFAULT_TEMPERATURE = 0.3
const val AGENT_DEFAULT_MAX_TOKENS = 1024
const val AGENT_MIN_MAX_TOKENS = 64
const val AGENT_MAX_MAX_TOKENS = 8192

/** Rapikan nilai yang diketik user supaya tidak tersimpan angka tak masuk akal. */
fun AgentConfig.sanitized(): AgentConfig = copy(
    baseUrl = baseUrl.trim(),
    modelCepat = modelCepat.trim(),
    modelPintar = modelPintar.trim(),
    temperature = if (temperature.isFinite()) temperature.coerceIn(0.0, 2.0) else AGENT_DEFAULT_TEMPERATURE,
    maxTokens = if (maxTokens <= 0) AGENT_DEFAULT_MAX_TOKENS
    else maxTokens.coerceIn(AGENT_MIN_MAX_TOKENS, AGENT_MAX_MAX_TOKENS),
    customProviders = customProviders
        .map { it.copy(label = it.label.trim(), baseUrl = it.baseUrl.trim()) }
        .filter { it.id.isNotBlank() && it.label.isNotBlank() && it.baseUrl.isNotBlank() }
        .distinctBy { it.id },
)

/** Simpan daftar penyedia custom sebagai JSON (satu string di SharedPreferences). */
fun encodeCustomProviders(providers: List<CustomProvider>): String = AgentJson.write(
    arrOf(
        *providers.map {
            objOf(
                "id" to strOf(it.id),
                "label" to strOf(it.label),
                "baseUrl" to strOf(it.baseUrl),
            )
        }.toTypedArray(),
    ),
)

/** Baca kembali hasil [encodeCustomProviders]; rusak/kosong = daftar kosong. */
fun decodeCustomProviders(raw: String): List<CustomProvider> {
    if (raw.isBlank()) return emptyList()
    val parsed = AgentJson.parse(raw) as? AgentJsonValue.Arr ?: return emptyList()
    return parsed.items.mapNotNull { item ->
        val id = item.stringOrNull("id").orEmpty()
        val label = item.stringOrNull("label").orEmpty()
        val baseUrl = item.stringOrNull("baseUrl").orEmpty()
        if (id.isBlank() || label.isBlank() || baseUrl.isBlank()) null
        else CustomProvider(id, label, baseUrl)
    }.distinctBy { it.id }
}

sealed interface AgentUrlResult {
    data class Valid(val baseUrl: String) : AgentUrlResult
    data class Invalid(val message: String) : AgentUrlResult
}

/**
 * Normalisasi base URL.
 *
 * Aturan yang sengaja dipilih (lihat laporan Tahap 1):
 * - buang `/` di akhir,
 * - buang akhiran `/chat/completions` atau `/models` bila user menempelkan path lengkap
 *   (path itu ditambahkan aplikasi sendiri),
 * - **tidak** menambahkan `/v1` secara otomatis: hanya preset yang memang punya `/v1`.
 */
fun normalizeBaseUrl(raw: String): AgentUrlResult {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return AgentUrlResult.Invalid("Alamat server belum diisi. Contoh: https://openrouter.ai/api/v1")
    }
    if (trimmed.any { it.isWhitespace() }) {
        return AgentUrlResult.Invalid("Alamat server tidak boleh memuat spasi.")
    }
    val lower = trimmed.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
        return AgentUrlResult.Invalid("Alamat harus diawali https:// (atau http:// khusus server lokal).")
    }
    val cleaned = trimmed.trimEnd('/')
        .removeSuffix("/chat/completions")
        .removeSuffix("/models")
        .trimEnd('/')
    val uri = try {
        URI(cleaned)
    } catch (_: URISyntaxException) {
        null
    } ?: return AgentUrlResult.Invalid("Alamat server tidak dapat dibaca. Periksa penulisan URL.")
    val host = uri.host?.trim().orEmpty()
    if (host.isEmpty()) {
        return AgentUrlResult.Invalid("Alamat server tidak memuat nama host. Contoh: https://api.contoh.id/v1")
    }
    if (uri.rawQuery != null || uri.rawFragment != null || uri.userInfo != null) {
        return AgentUrlResult.Invalid(
            "Alamat server tidak boleh memuat tanda tanya (?), fragmen (#), atau user:password. " +
                "Cukup sampai bagian path, mis. https://api.contoh.id/v1",
        )
    }
    if (uri.scheme.equals("http", ignoreCase = true) && !isLocalHost(host)) {
        return AgentUrlResult.Invalid(
            "http:// hanya diizinkan untuk server lokal (localhost, 10.0.2.2, atau alamat LAN). " +
                "Untuk server di internet gunakan https://.",
        )
    }
    return AgentUrlResult.Valid(cleaned)
}

/** Host lokal/emulator/LAN yang boleh memakai `http://`. */
fun isLocalHost(host: String): Boolean {
    val value = host.trim().trim('[', ']').lowercase()
    if (value.isEmpty()) return false
    if (value in setOf("localhost", "127.0.0.1", "::1", "0.0.0.0", "10.0.2.2", "10.0.3.2")) return true
    if (value.endsWith(".local") || value.endsWith(".localhost")) return true
    // Nama host LAN tanpa domain, mis. "server-kampus" atau "pc-rumah".
    if (!value.contains('.')) return true
    val parts = value.split('.')
    if (parts.size == 4 && parts.all { part -> part.isNotEmpty() && part.all { it.isDigit() } }) {
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 10 || first == 127 ||
            (first == 192 && second == 168) ||
            (first == 172 && second in 16..31) ||
            (first == 169 && second == 254)
    }
    return false
}

/**
 * Preset penyedia OpenAI-compatible. `http://` hanya ada di preset lokal,
 * karena Android memblokir cleartext ke alamat internet (lihat network_security_config.xml).
 */
data class AgentPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val keterangan: String,
    val butuhKunci: Boolean = true,
)

val AgentPresets: List<AgentPreset> = listOf(
    AgentPreset(
        id = "openrouter",
        label = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        keterangan = "Banyak pilihan model gratis maupun berbayar.",
    ),
    AgentPreset(
        id = "groq",
        label = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        keterangan = "Model cepat, cocok untuk pemanggilan tool.",
    ),
    AgentPreset(
        id = "deepseek",
        label = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        keterangan = "Endpoint OpenAI-compatible milik DeepSeek.",
    ),
    AgentPreset(
        id = "openai",
        label = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        keterangan = "Butuh kunci resmi OpenAI.",
    ),
    AgentPreset(
        id = "ollama",
        label = "Ollama (lokal)",
        baseUrl = "http://10.0.2.2:11434/v1",
        keterangan = "10.0.2.2 = komputer host dari emulator. Di HP nyata pakai alamat LAN komputer.",
        butuhKunci = false,
    ),
    AgentPreset(
        id = "lmstudio",
        label = "LM Studio (lokal)",
        baseUrl = "http://10.0.2.2:1234/v1",
        keterangan = "Aktifkan server lokal di LM Studio lebih dulu.",
        butuhKunci = false,
    ),
)

fun agentPreset(id: String): AgentPreset? = AgentPresets.find { it.id == id }

/** Cari penyedia custom dari config berdasarkan id pilihannya (`custom:<id>`). */
fun AgentConfig.customProvider(choiceId: String): CustomProvider? {
    if (!choiceId.startsWith("custom:")) return null
    val id = choiceId.removePrefix("custom:")
    return customProviders.find { it.id == id }
}
