package id.personal.campus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import id.personal.campus.agent.AGENT_MAX_MAX_TOKENS
import id.personal.campus.agent.AGENT_MIN_MAX_TOKENS
import id.personal.campus.agent.AgentConfig
import id.personal.campus.agent.AgentPresets
import id.personal.campus.agent.AgentUrlResult
import id.personal.campus.agent.agentPreset
import id.personal.campus.agent.normalizeBaseUrl

/** Status bagian "Asisten AI" di layar Setelan. Tidak memuat kunci API. */
data class AgentUiState(
    val config: AgentConfig = AgentConfig(),
    val hasKey: Boolean = false,
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val models: List<String> = emptyList(),
    val message: String? = null,
    val messageOk: Boolean = false,
    val detail: String? = null,
)

/**
 * Bagian Setelan → Asisten AI (BYOK). Hanya fondasi: alamat server, kunci, model,
 * dan uji koneksi. Layar chat menyusul di tahap berikutnya.
 */
@Composable
fun AgentSettingsSection(
    state: AgentUiState,
    enabled: Boolean,
    onSave: (config: AgentConfig, newApiKey: String?, clearKey: Boolean) -> Unit,
    onCheck: (fetchModels: Boolean) -> Unit,
) {
    SectionHeading("Asisten AI")
    Text(
        "Opsional dan membawa kunci sendiri. Tanpa kunci API maupun tanpa internet, jadwal, tugas, " +
            "catatan, pengingat, dan widget tetap berjalan seperti biasa.",
        style = MaterialTheme.typography.bodySmall, color = MutedInk,
    )
    if (!state.loaded) {
        Text("Memuat setelan Asisten AI...", color = MutedInk)
        return
    }

    // Draft di-key pada setelan tersimpan supaya nilai yang baru dimuat/di-save langsung tampil.
    var presetId by rememberSaveable(state.config) { mutableStateOf("") }
    var baseUrl by rememberSaveable(state.config) { mutableStateOf(state.config.baseUrl) }
    var modelCepat by rememberSaveable(state.config) { mutableStateOf(state.config.modelCepat) }
    var modelPintar by rememberSaveable(state.config) { mutableStateOf(state.config.modelPintar) }
    var temperature by rememberSaveable(state.config) { mutableStateOf(state.config.temperature.toString()) }
    var maxTokens by rememberSaveable(state.config) { mutableStateOf(state.config.maxTokens.toString()) }
    // Kunci API sengaja hanya di `remember` (bukan `rememberSaveable`): jangan sampai
    // ikut tersimpan ke Bundle saved-instance-state.
    var draftKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val locked = !enabled || state.busy
    val urlCheck = normalizeBaseUrl(baseUrl)
    val draftDirty = baseUrl.trim() != state.config.baseUrl ||
        modelCepat.trim() != state.config.modelCepat ||
        modelPintar.trim() != state.config.modelPintar ||
        draftKey.isNotBlank()

    ChoiceField(
        "Preset penyedia", presetId, AgentPresets.map { it.id to it.label }, enabled = !locked,
    ) { id ->
        presetId = id
        agentPreset(id)?.let { baseUrl = it.baseUrl }
    }
    agentPreset(presetId)?.let { preset ->
        Text(preset.keterangan, style = MaterialTheme.typography.bodySmall, color = MutedInk)
    }

    OutlinedTextField(
        value = baseUrl, onValueChange = { baseUrl = it; presetId = "" },
        modifier = Modifier.fillMaxWidth(), enabled = !locked, singleLine = true,
        label = { Text("Base URL") },
        placeholder = { Text("https://openrouter.ai/api/v1") },
        isError = baseUrl.isNotBlank() && urlCheck is AgentUrlResult.Invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    when (urlCheck) {
        is AgentUrlResult.Invalid -> ValidationText(urlCheck.message)
        is AgentUrlResult.Valid -> Text(
            "Alamat dipakai: ${urlCheck.baseUrl}/chat/completions",
            style = MaterialTheme.typography.bodySmall, color = MutedInk,
        )
    }
    Text(
        "Protokol yang didukung: OpenAI-compatible (/chat/completions dan /models), termasuk Ollama " +
            "dan LM Studio di jaringan lokal. Aplikasi tidak menambahkan /v1 sendiri.",
        style = MaterialTheme.typography.bodySmall, color = MutedInk,
    )

    OutlinedTextField(
        value = draftKey, onValueChange = { draftKey = it },
        modifier = Modifier.fillMaxWidth(), enabled = !locked, singleLine = true,
        label = { Text(if (state.hasKey) "Kunci API baru (opsional)" else "Kunci API") },
        supportingText = {
            Text(if (state.hasKey) "Kunci tersimpan terenkripsi di perangkat ini."
            else "Belum ada kunci. Server lokal seperti Ollama biasanya tidak memerlukannya.")
        },
        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { keyVisible = !keyVisible }, enabled = !locked) {
                Icon(
                    if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (keyVisible) "Sembunyikan kunci" else "Tampilkan kunci",
                )
            }
        },
    )
    if (state.hasKey) TextButton(enabled = !locked, onClick = { confirmClear = true }) { Text("Hapus kunci API") }

    OutlinedTextField(
        value = modelCepat, onValueChange = { modelCepat = it },
        modifier = Modifier.fillMaxWidth(), enabled = !locked, singleLine = true,
        label = { Text("Model cepat — pemanggilan tool") },
        placeholder = { Text("mis. llama3.1:8b atau gpt-4o-mini") },
    )
    OutlinedTextField(
        value = modelPintar, onValueChange = { modelPintar = it },
        modifier = Modifier.fillMaxWidth(), enabled = !locked, singleLine = true,
        label = { Text("Model pintar — penulisan jawaban") },
        supportingText = { Text("Boleh sama dengan model cepat; biarkan kosong untuk memakai model cepat.") },
    )
    if (state.models.isNotEmpty()) {
        ChoiceField("Pilih model cepat", modelCepat, modelOptions(modelCepat, state.models), enabled = !locked) { modelCepat = it }
        ChoiceField("Pilih model pintar", modelPintar, modelOptions(modelPintar, state.models), enabled = !locked) { modelPintar = it }
        Text("${state.models.size} model terakhir diambil dari server.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
    }

    OutlinedTextField(
        value = temperature,
        onValueChange = { input -> if (input.all { it.isDigit() || it == '.' || it == ',' }) temperature = input },
        modifier = Modifier.fillMaxWidth(), enabled = !locked, singleLine = true,
        label = { Text("Temperature") },
        supportingText = { Text("0.0–2.0. Disarankan 0.2–0.4 supaya jawaban jadwal tidak mengarang.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = temperature.replace(',', '.').toDoubleOrNull()?.let { it !in 0.0..2.0 } ?: true,
    )
    OutlinedTextField(
        value = maxTokens,
        onValueChange = { input -> if (input.all { it.isDigit() }) maxTokens = input },
        modifier = Modifier.fillMaxWidth(), enabled = !locked, singleLine = true,
        label = { Text("Batas token jawaban") },
        supportingText = { Text("$AGENT_MIN_MAX_TOKENS–$AGENT_MAX_MAX_TOKENS token.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )

    Button(
        enabled = !locked && urlCheck is AgentUrlResult.Valid,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        onClick = {
            onSave(
                AgentConfig(
                    baseUrl = baseUrl,
                    modelCepat = modelCepat,
                    modelPintar = modelPintar,
                    temperature = temperature.replace(',', '.').toDoubleOrNull() ?: state.config.temperature,
                    maxTokens = maxTokens.toIntOrNull() ?: state.config.maxTokens,
                ),
                draftKey.takeIf { it.isNotBlank() },
                false,
            )
            draftKey = ""
        },
    ) { Text(if (state.busy) "Menyimpan..." else "Simpan setelan Asisten AI") }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { onCheck(false) },
            enabled = !locked && urlCheck is AgentUrlResult.Valid,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text("Tes koneksi") }
        OutlinedButton(
            onClick = { onCheck(true) },
            enabled = !locked && urlCheck is AgentUrlResult.Valid,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text("Ambil daftar model") }
    }
    Text(
        if (draftDirty) "Perubahan belum disimpan: tombol tes memakai setelan tersimpan. Simpan dulu supaya hasilnya sesuai isian di atas."
        else "Tes memakai setelan tersimpan dan kunci tersimpan. Kunci tidak dikirim ke mana pun selain alamat di atas.",
        style = MaterialTheme.typography.bodySmall,
        color = if (draftDirty) CampusGreen else MutedInk,
    )

    state.message?.let { message ->
        PaperPanel {
            Text(
                if (state.messageOk) "Berhasil" else "Gagal",
                style = MaterialTheme.typography.titleLarge,
                color = if (state.messageOk) CampusGreen else MaterialTheme.colorScheme.error,
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
            state.detail?.takeIf { it.isNotBlank() }?.let {
                Text("Pesan server: $it", style = MaterialTheme.typography.bodySmall, color = MutedInk)
            }
            Text("Kunci API tidak pernah ditampilkan, dicatat, atau ditaruh di pesan error.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Hapus kunci API?") },
        text = { Text("Setelan lain tetap tersimpan. Asisten AI tidak dapat dipakai sampai kunci diisi ulang.") },
        confirmButton = {
            Button(enabled = !locked, onClick = {
                confirmClear = false
                draftKey = ""
                onSave(state.config, null, true)
            }) { Text("Hapus") }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
        containerColor = Paper, tonalElevation = 0.dp,
    )
}

private fun modelOptions(current: String, models: List<String>): List<Pair<String, String>> =
    (listOf(current.trim()) + models).filter { it.isNotBlank() }.distinct().map { it to it }
