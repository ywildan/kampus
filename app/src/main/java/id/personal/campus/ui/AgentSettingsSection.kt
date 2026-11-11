package id.personal.campus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.personal.campus.agent.AGENT_MAX_MAX_TOKENS
import id.personal.campus.agent.AGENT_MIN_MAX_TOKENS
import id.personal.campus.agent.AgentConfig
import id.personal.campus.agent.AgentPresets
import id.personal.campus.agent.AgentUrlResult
import id.personal.campus.agent.CustomProvider
import id.personal.campus.agent.agentPreset
import id.personal.campus.agent.decodeCustomProviders
import id.personal.campus.agent.encodeCustomProviders
import id.personal.campus.agent.normalizeBaseUrl
import kotlinx.coroutines.launch

/** Status bagian "Asisten AI" di layar Setelan. Tidak memuat kunci API. */
data class AgentUiState(
    val config: AgentConfig = AgentConfig(),
    val hasKey: Boolean = false,
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val models: List<String> = emptyList(),
    /** URL yang menghasilkan [models]; daftar lama tidak dipakai untuk penyedia lain. */
    val modelsUrl: String? = null,
    val message: String? = null,
    val messageOk: Boolean = false,
    val detail: String? = null,
)

/**
 * Bagian Setelan → Asisten AI (BYOK), disusun mengikuti urutan kerja:
 * 1. PENYEDIA & KUNCI — alamat server, kunci, dan status koneksi.
 * 2. MODEL — satu pemilih per peran (membaca data / menulis jawaban).
 * 3. LANJUTAN — temperature dan batas token, terlipat.
 *
 * Semua perubahan adalah satu draf; baru tersimpan lewat bilah Simpan di bawah.
 */
@Composable
fun AgentSettingsSection(
    state: AgentUiState,
    enabled: Boolean,
    onSave: (config: AgentConfig, newApiKey: String?, clearKey: Boolean) -> Unit,
    onCheck: (config: AgentConfig, newApiKey: String?) -> Unit,
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

    // Draf di-key pada setelan tersimpan dan resetToken: Simpan/Batal otomatis memuat ulang nilai.
    var resetToken by rememberSaveable { mutableStateOf(0) }
    var presetId by rememberSaveable(state.config, resetToken) {
        mutableStateOf(providerChoiceFor(state.config.baseUrl, state.config.customProviders))
    }
    var baseUrl by rememberSaveable(state.config, resetToken) { mutableStateOf(state.config.baseUrl) }
    var modelCepat by rememberSaveable(state.config, resetToken) { mutableStateOf(state.config.modelCepat) }
    var modelPintar by rememberSaveable(state.config, resetToken) { mutableStateOf(state.config.modelPintar) }
    var temperature by rememberSaveable(state.config, resetToken) { mutableStateOf(state.config.temperature.toString()) }
    var maxTokens by rememberSaveable(state.config, resetToken) { mutableStateOf(state.config.maxTokens.toString()) }
    var providersJson by rememberSaveable(state.config, resetToken) {
        mutableStateOf(encodeCustomProviders(state.config.customProviders))
    }
    var customName by rememberSaveable(state.config, resetToken) { mutableStateOf("") }
    var editingUrl by rememberSaveable(state.config, resetToken) { mutableStateOf(state.config.baseUrl.isBlank()) }
    var advancedOpen by rememberSaveable(state.config, resetToken) { mutableStateOf(false) }
    var modelPickerFor by rememberSaveable(state.config, resetToken) { mutableStateOf("") }
    // Kunci API sengaja hanya di `remember` (bukan `rememberSaveable`): jangan sampai
    // ikut tersimpan ke Bundle saved-instance-state.
    var draftKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val customProviders = remember(providersJson) { decodeCustomProviders(providersJson) }
    val locked = !enabled || state.busy
    val urlCheck = normalizeBaseUrl(baseUrl)
    val normalizedUrl = (urlCheck as? AgentUrlResult.Valid)?.baseUrl
    val temperatureValue = temperature.replace(',', '.').toDoubleOrNull()
    val maxTokensValue = maxTokens.toIntOrNull()
    val draftDirty = baseUrl.trim() != state.config.baseUrl ||
        modelCepat.trim() != state.config.modelCepat ||
        modelPintar.trim() != state.config.modelPintar ||
        temperatureValue != state.config.temperature ||
        maxTokensValue != state.config.maxTokens ||
        customProviders != state.config.customProviders ||
        draftKey.isNotBlank()
    val canSave = !locked && draftDirty && urlCheck is AgentUrlResult.Valid
    val urlAlreadyKnown = normalizedUrl != null &&
        (AgentPresets.any { normalizedUrlOf(it.baseUrl) == normalizedUrl } ||
            customProviders.any { normalizedUrlOf(it.baseUrl) == normalizedUrl })
    // Daftar model hanya berlaku untuk URL yang menghasilkannya.
    val availableModels = if (state.modelsUrl == normalizedUrl) state.models else emptyList()

    fun resetDraft() {
        resetToken++
        draftKey = ""
        keyVisible = false
        confirmClear = false
    }

    fun draftConfig() = AgentConfig(
        baseUrl = baseUrl,
        modelCepat = modelCepat,
        modelPintar = modelPintar,
        temperature = temperatureValue ?: state.config.temperature,
        maxTokens = maxTokensValue ?: state.config.maxTokens,
        customProviders = customProviders,
    )

    SettingsGroup("PENYEDIA & KUNCI") {
        val providerOptions = AgentPresets.map { it.id to it.label } +
            customProviders.map { "custom:${it.id}" to "${it.label} (custom)" }
        ChoiceField("Penyedia", presetId, providerOptions, enabled = !locked) { choice ->
            presetId = choice
            val url = agentPreset(choice)?.baseUrl ?: customProviders.find { "custom:${it.id}" == choice }?.baseUrl
            if (url != null) baseUrl = url
        }
        agentPreset(presetId)?.let { preset ->
            Text(preset.keterangan, style = MaterialTheme.typography.bodySmall, color = MutedInk)
        }
        customProviders.find { "custom:${it.id}" == presetId }?.let { custom ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Penyedia custom: ${custom.baseUrl}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedInk,
                )
                TextButton(enabled = !locked, onClick = {
                    providersJson = encodeCustomProviders(customProviders.filterNot { it.id == custom.id })
                    presetId = ""
                }) { Text("Hapus") }
            }
        }

        if (editingUrl) {
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
                "Protokol yang didukung: OpenAI-compatible (/chat/completions dan /models), termasuk " +
                    "Ollama dan LM Studio di jaringan lokal. Aplikasi tidak menambahkan /v1 sendiri.",
                style = MaterialTheme.typography.bodySmall, color = MutedInk,
            )
            // URL yang belum dikenal bisa disimpan sebagai penyedia custom, ikut draf yang sama.
            if (normalizedUrl != null && !urlAlreadyKnown) {
                OutlinedTextField(
                    value = customName, onValueChange = { customName = it },
                    modifier = Modifier.fillMaxWidth(), enabled = !locked, singleLine = true,
                    label = { Text("Nama penyedia custom") },
                    placeholder = { Text("mis. Server kampus") },
                )
                OutlinedButton(
                    onClick = {
                        val provider = CustomProvider(
                            id = "c${java.util.UUID.randomUUID().toString().take(8)}",
                            label = customName.trim(),
                            baseUrl = normalizedUrl,
                        )
                        providersJson = encodeCustomProviders(customProviders + provider)
                        presetId = "custom:${provider.id}"
                        customName = ""
                    },
                    enabled = !locked && customName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Tambah ke daftar penyedia") }
                Text(
                    "Penyedia baru ikut tersimpan lewat tombol Simpan di bawah.",
                    style = MaterialTheme.typography.bodySmall, color = MutedInk,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alamat server", style = MaterialTheme.typography.bodySmall, color = MutedInk)
                    Text(
                        baseUrl,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(enabled = !locked, onClick = { editingUrl = true }) { Text("Ubah") }
            }
        }

        OutlinedTextField(
            value = draftKey, onValueChange = { draftKey = it },
            modifier = Modifier.fillMaxWidth(), enabled = !locked, singleLine = true,
            label = { Text(if (state.hasKey) "Kunci API baru (opsional)" else "Kunci API") },
            supportingText = {
                Text(
                    if (state.hasKey) "Kunci tersimpan terenkripsi di perangkat ini."
                    else "Belum ada kunci. Server lokal seperti Ollama biasanya tidak memerlukannya.",
                )
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
        if (state.hasKey) {
            TextButton(enabled = !locked, onClick = { confirmClear = true }) { Text("Hapus kunci API") }
        }

        HorizontalDivider(color = Rule)
        ConnectionStatus(state)
        OutlinedButton(
            onClick = { onCheck(draftConfig(), draftKey.takeIf { it.isNotBlank() }) },
            enabled = !locked && urlCheck is AgentUrlResult.Valid,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Uji koneksi & ambil model") }
        Text(
            "Menguji alamat dan kunci yang terlihat di atas, lalu mengambil daftar model. " +
                "Kunci yang baru diketik hanya dipakai untuk uji ini dan belum tersimpan.",
            style = MaterialTheme.typography.bodySmall,
            color = if (draftDirty) CampusGreen else MutedInk,
        )
    }

    SettingsGroup("MODEL") {
        ModelPickerField(
            label = "Membaca data (alat)",
            value = modelCepat,
            enabled = !locked,
            supporting = "Dipakai mode Hemat dan Cepat.",
        ) { modelPickerFor = "fast" }
        ModelPickerField(
            label = "Menulis jawaban",
            value = modelPintar,
            enabled = !locked,
            supporting = "Dipakai mode Hemat dan Pintar.",
        ) { modelPickerFor = "smart" }
        Text(
            buildString {
                append("Mode Hemat memakai keduanya; Pintar/Cepat memakai satu model untuk semua langkah.")
                if (availableModels.isNotEmpty()) append(" ${availableModels.size} model tersedia dari server.")
                else append(" Uji koneksi di grup Penyedia untuk mengambil daftar model, atau ketik nama manual.")
            },
            style = MaterialTheme.typography.bodySmall, color = MutedInk,
        )
    }

    SettingsGroup("LANJUTAN") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Temperature ${temperature.ifBlank { "—" }} · ${maxTokens.ifBlank { "—" }} token",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MutedInk,
            )
            TextButton(enabled = !locked, onClick = { advancedOpen = !advancedOpen }) {
                Text(if (advancedOpen) "Tutup" else "Buka")
            }
        }
        if (advancedOpen) {
            OutlinedTextField(
                value = temperature,
                onValueChange = { input -> if (input.all { it.isDigit() || it == '.' || it == ',' }) temperature = input },
                modifier = Modifier.fillMaxWidth(), enabled = !locked, singleLine = true,
                label = { Text("Temperature") },
                supportingText = { Text("0.0–2.0. Disarankan 0.2–0.4 supaya jawaban jadwal tidak mengarang.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = temperatureValue?.let { it !in 0.0..2.0 } ?: true,
            )
            OutlinedTextField(
                value = maxTokens,
                onValueChange = { input -> if (input.all { it.isDigit() }) maxTokens = input },
                modifier = Modifier.fillMaxWidth(), enabled = !locked, singleLine = true,
                label = { Text("Batas token jawaban") },
                supportingText = { Text("$AGENT_MIN_MAX_TOKENS–$AGENT_MAX_MAX_TOKENS token.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (draftDirty) "Ada perubahan belum disimpan." else "Semua perubahan tersimpan.",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (draftDirty) CampusGreen else MutedInk,
        )
        OutlinedButton(
            onClick = { resetDraft() },
            enabled = !locked && draftDirty,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Batal") }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                onSave(draftConfig(), draftKey.takeIf { it.isNotBlank() }, false)
                draftKey = ""
            },
            enabled = canSave,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(if (state.busy) "Menyimpan..." else "Simpan") }
    }

    when (modelPickerFor) {
        "fast" -> ModelPickerSheet(
            title = "Model untuk membaca data",
            value = modelCepat,
            models = availableModels,
            onPick = { modelCepat = it },
            onDismiss = { modelPickerFor = "" },
        )
        "smart" -> ModelPickerSheet(
            title = "Model untuk menulis jawaban",
            value = modelPintar,
            models = availableModels,
            onPick = { modelPintar = it },
            onDismiss = { modelPickerFor = "" },
        )
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

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    PaperPanel {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = CampusGreen,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun ConnectionStatus(state: AgentUiState) {
    val color = when {
        state.message == null -> MutedInk
        state.messageOk -> CampusGreen
        else -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            state.message ?: "Koneksi belum diuji",
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.message == null) MutedInk else color,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    state.detail?.takeIf { it.isNotBlank() }?.let {
        Text(
            "Pesan server: $it",
            style = MaterialTheme.typography.bodySmall,
            color = MutedInk,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModelPickerField(
    label: String,
    value: String,
    enabled: Boolean,
    supporting: String,
    onOpen: () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MutedInk)
        OutlinedButton(
            onClick = onOpen,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(
                value.ifBlank { "Belum dipilih" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.ExpandMore, contentDescription = null)
        }
        Text(supporting, style = MaterialTheme.typography.bodySmall, color = MutedInk)
    }
}

/**
 * Pilih model dari daftar server (bila sudah diambil) atau ketik manual.
 * Pilihan hanya mengubah draf; penyimpanan tetap lewat bilah Simpan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    title: String,
    value: String,
    models: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable(value) { mutableStateOf(value) }
    val filtered = remember(query, models) {
        if (query.isBlank()) models else models.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    fun choose(model: String) {
        val chosen = model.trim()
        if (chosen.isEmpty()) return
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onPick(chosen)
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Paper,
        contentColor = Ink,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                if (models.isEmpty()) {
                    "Daftar model belum diambil dari server. Ketik nama model di bawah."
                } else {
                    "${models.size} model dari server."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MutedInk,
            )
            if (models.isNotEmpty()) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Cari model") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(filtered) { model ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { choose(model) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                model,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (model == value) {
                                Icon(Icons.Outlined.Check, contentDescription = null, tint = CampusGreen)
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    Text(
                        "Tidak ada model yang cocok. Pakai isian manual di bawah.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk,
                    )
                }
                HorizontalDivider(color = Rule)
            }
            OutlinedTextField(
                value = manual, onValueChange = { manual = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Atau ketik nama model") },
                placeholder = { Text("mis. llama3.1:8b") },
            )
            Button(
                onClick = { choose(manual) },
                enabled = manual.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Pakai model ini") }
        }
    }
}

private fun normalizedUrlOf(raw: String): String? =
    (normalizeBaseUrl(raw) as? AgentUrlResult.Valid)?.baseUrl

/** Pilihan penyedia yang cocok dengan URL tersimpan, supaya dropdown tidak menulis "Belum dipilih". */
internal fun providerChoiceFor(baseUrl: String, customProviders: List<CustomProvider>): String {
    val normalized = normalizedUrlOf(baseUrl) ?: return ""
    customProviders.firstOrNull { normalizedUrlOf(it.baseUrl) == normalized }?.let { return "custom:${it.id}" }
    return AgentPresets.firstOrNull { normalizedUrlOf(it.baseUrl) == normalized }?.id.orEmpty()
}
