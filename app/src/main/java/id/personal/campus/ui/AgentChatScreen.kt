package id.personal.campus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.rememberMarkdownState
import id.personal.campus.agent.AgentSkill
import id.personal.campus.agent.AgentSkills
import id.personal.campus.agent.AgentWriteKind
import id.personal.campus.agent.AgentWriteProposal

import id.personal.campus.ui.AgentChatMessage
import id.personal.campus.ui.AgentChatRole
import id.personal.campus.ui.AgentChatState
import id.personal.campus.ui.AgentPlanStep
import id.personal.campus.ui.AgentPlanStatus
import id.personal.campus.ui.AgentSetup

/**
 * Layar Asisten AI.
 *
 * Layar ini hanya menampilkan [state] dan meneruskan aksi user — semua keputusan ada di
 * `AgentChatViewModel`/`AgentChatReducer` supaya bisa diuji tanpa perangkat.
 *
 * Dua hal yang sengaja selalu terlihat:
 * 1. **Panel rencana aksi** — user tahu agen sedang membaca apa (bukan kotak hitam).
 * 2. **Kartu konfirmasi** — tidak ada perubahan data tanpa "Setuju", dan setelah tersimpan
 *    selalu tersedia tombol "Batalkan".
 */
@Composable
fun AgentChatScreen(
    state: AgentChatState,
    onInput: (String) -> Unit,
    onSkill: (AgentSkill) -> Unit,
    onSmartToggle: (Boolean) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onUndo: () -> Unit,
    onConsumeNotice: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshSetup: () -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    val lastText = state.messages.lastOrNull()?.text

    // Ikuti jawaban yang sedang mengalir tanpa mengganggu user yang menggulir ke atas.
    LaunchedEffect(state.messages.size, lastText) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.messages.isEmpty()) item(key = "intro") { IntroCard(state.skill) }

            if (!state.setup.ready) {
                item(key = "setup") {
                    SetupCard(state.setup, onOpenSettings = onOpenSettings, onRetry = onRefreshSetup)
                }
            }

            items(state.messages, key = { it.id }) { message -> MessageBubble(message, onRetry) }

            if (state.plan.isNotEmpty()) item(key = "plan") { PlanPanel(state.plan, state.busy) }

            state.proposal?.let { proposal ->
                item(key = "proposal") {
                    ProposalCard(proposal = proposal, onApprove = onApprove, onDeny = onDeny)
                }
            }

            state.undoLabel?.let { label ->
                item(key = "undo") {
                    OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Undo, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Batalkan: $label")
                    }
                }
            }

            if (state.busy && state.plan.isEmpty()) item(key = "thinking") { ThinkingRow() }
        }

        HorizontalDivider(color = Rule)
        SkillPicker(state.skill, enabled = !state.busy && !state.awaitingDecision, onSkill = onSkill)
        ModelRow(state, onSmartToggle = onSmartToggle)
        Composer(state, onInput = onInput, onSend = onSend, onStop = onStop)
        state.notice?.let { Notice(message = it, onDismiss = onConsumeNotice) }
    }
}

@Composable
private fun IntroCard(skill: AgentSkill) {
    PaperPanel {
        Text("Asisten AI", style = MaterialTheme.typography.titleLarge)
        Text(
            "Tanya apa saja soal jadwal, tugas, dan catatanmu. Asisten hanya membaca data di " +
                "aplikasi ini, dan setiap perubahan harus kamu setujui dulu.",
            color = MutedInk,
        )
        Text("Peran sekarang: ${skill.label} — ${skill.hint}", color = CampusGreen)
    }
}

@Composable
private fun SetupCard(setup: AgentSetup, onOpenSettings: () -> Unit, onRetry: () -> Unit) {
    PaperPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text("Asisten belum siap", style = MaterialTheme.typography.titleMedium)
        }
        Text(setup.problem ?: "Lengkapi alamat server, model, dan kunci API di Setelan.", color = MutedInk)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenSettings) { Text("Buka Setelan") }
            OutlinedButton(onClick = onRetry) { Text("Periksa lagi") }
        }
    }
}

@Composable
private fun MessageBubble(message: AgentChatMessage, onRetry: () -> Unit) {
    val fromUser = message.role == AgentChatRole.USER
    val clipboard = LocalClipboardManager.current
    val background = when {
        fromUser -> CampusGreen
        message.failureDetail != null -> MaterialTheme.colorScheme.errorContainer
        else -> Color.White
    }
    val ink = if (fromUser) Color.White else Ink

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = background,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = if (fromUser) 0.dp else 1.dp,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (fromUser) {
                    Text(
                        text = if (message.text.isEmpty() && message.streaming) "…" else message.text,
                        color = ink,
                    )
                } else {
                    // Jawaban model dirender sebagai Markdown (bold, list, tabel jadwal, kode):
                    // sebelumnya teks polos sehingga `**...**` dan `| ... |` tampil mentah.
                    // immediate = true: parse sinkron (pesan chat kecil) supaya balon langsung
                    // terukur di tinggi akhirnya dan tidak berkedip lewat state loading saat
                    // streaming. modifier fillMaxWidth (bukan bawaan fillMaxSize) supaya aman
                    // di dalam LazyColumn yang tingginya tak terbatas.
                    val markdownState = rememberMarkdownState(
                        content = if (message.text.isEmpty() && message.streaming) "…" else message.text,
                        immediate = true,
                    )
                    Markdown(
                        markdownState,
                        modifier = Modifier.fillMaxWidth(),
                        colors = markdownColor(text = ink),
                    )
                }

                message.trace?.let { trace ->
                    Text(trace, color = if (fromUser) Color.White else MutedInk, style = MaterialTheme.typography.bodySmall)
                }

                if (message.stopped) {
                    Text("Dihentikan sebelum selesai.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                }

                if (message.failureDetail != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            clipboard.setText(
                                AnnotatedString(
                                    listOfNotNull(message.text, message.failureDetail).joinToString("\n"),
                                ),
                            )
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Salin pesan", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = onRetry) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Coba lagi", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanPanel(plan: List<AgentPlanStep>, busy: Boolean) {
    PaperPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rencana aksi", style = MaterialTheme.typography.titleMedium)
            if (busy) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
        }
        plan.forEach { step -> PlanStepRow(step) }
    }
}

@Composable
private fun PlanStepRow(step: AgentPlanStep) {
    val color = when (step.status) {
        AgentPlanStatus.RUNNING -> CampusGreen
        AgentPlanStatus.DONE -> MutedInk
        AgentPlanStatus.CANCELLED -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (step.status) {
                AgentPlanStatus.RUNNING -> "${step.label}…"
                AgentPlanStatus.DONE -> step.label
                AgentPlanStatus.CANCELLED -> "${step.label} (dibatalkan)"
            },
            color = color,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProposalCard(proposal: AgentWriteProposal, onApprove: () -> Unit, onDeny: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (proposal.kind == AgentWriteKind.CREATE) "Perlu persetujuan" else "Perlu persetujuan",
                style = MaterialTheme.typography.labelMedium,
                color = CampusGreen,
            )
            Text(proposal.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (proposal.detail.isNotBlank()) Text(proposal.detail, color = MutedInk)

            proposal.changes.forEach { change ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(change.label, color = MutedInk)
                    Text(
                        text = if (change.before.isNullOrBlank()) change.after else "${change.before} → ${change.after}",
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                    )
                }
            }

            proposal.warnings.forEach { warning ->
                Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove) { Text("Setuju") }
                OutlinedButton(onClick = onDeny) { Text("Batal") }
            }
        }
    }
}

@Composable
private fun ThinkingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("Menghubungi model…", color = MutedInk)
    }
}

@Composable
private fun SkillPicker(skill: AgentSkill, enabled: Boolean, onSkill: (AgentSkill) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AgentSkills.all.forEach { candidate ->
            FilterChip(
                selected = candidate.id == skill.id,
                enabled = enabled,
                onClick = { onSkill(candidate) },
                label = { Text(candidate.label) },
            )
        }
    }
}

/** "nvidia/nemotron-3.5-lightning:free" → "nemotron-3.5-lightning": nama pendek untuk baris model. */
private fun shortModelName(value: String): String =
    value.substringAfterLast('/').substringBefore(':').ifBlank { value }

@Composable
private fun ModelRow(state: AgentChatState, onSmartToggle: (Boolean) -> Unit) {
    val setup = state.setup
    if (setup.fastModel.isBlank() && setup.smartModel.isBlank()) return

    val fast = shortModelName(setup.fastModel)
    val smart = shortModelName(setup.smartModel)
    val modelText = when {
        fast.isBlank() -> "Pintar: $smart"
        smart.isBlank() -> "Cepat: $fast"
        fast == smart -> "Model: $fast"
        else -> "Cepat: $fast · Pintar: $smart"
    }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = modelText,
            color = MutedInk,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Selalu pakai model pintar", color = MutedInk, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = state.useSmartModelForAll,
                onCheckedChange = onSmartToggle,
                enabled = !state.busy,
            )
        }
    }
}

/**
 * Kolom ketik + tombol kirim dalam satu pil: wadah membulat penuh berisi kolom teks,
 * tombol kirim bulat berisi warna hijau di ujung kanan (abu-abu saat tidak bisa dikirim),
 * dan lingkaran yang sama berubah jadi tombol Stop saat jawaban sedang mengalir.
 * Sebelumnya OutlinedTextField kotak bergaris + IconButton polos di luar kotak.
 */
@Composable
private fun Composer(
    state: AgentChatState,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.weight(1f).heightIn(min = 56.dp, max = 148.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicTextField(
                    value = state.input,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp, max = 132.dp),
                    enabled = !state.awaitingDecision,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                    cursorBrush = SolidColor(CampusGreen),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (state.input.isEmpty()) {
                                Text("Tanya jadwal, tugas, atau catatan…", color = MutedInk)
                            }
                            innerTextField()
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = if (state.busy) onStop else onSend,
                    enabled = if (state.busy) true else state.canSend,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CampusGreen,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MutedInk,
                    ),
                ) {
                    Icon(
                        if (state.busy) Icons.Outlined.Stop else Icons.Outlined.Send,
                        contentDescription = if (state.busy) "Berhenti" else "Kirim",
                    )
                }
            }
        }
    }

    if (state.awaitingDecision) {
        Text(
            "Pilih Setuju atau Batal untuk melanjutkan.",
            color = MutedInk,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Notice(message: String, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    }
}
