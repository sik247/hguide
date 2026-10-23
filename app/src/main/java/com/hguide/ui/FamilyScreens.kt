package com.hguide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hguide.R
import com.hguide.core.FamilyState
import com.hguide.core.PersonRole
import com.hguide.data.Person

/** "함께할 목소리": whose voice greets and coaches, plus enrollment and the owner's AI-voice approval. */
@Composable
fun VoicePickScreen(vm: MainViewModel, onBack: (() -> Unit)?) {
    val people by vm.people.collectAsStateWithLifecycle()
    val current by vm.greetVoice.collectAsStateWithLifecycle()
    var sel by remember(current) { mutableStateOf(current) }
    val scripted by vm.scriptedPick.collectAsStateWithLifecycle()
    LaunchedEffect(scripted) { scripted?.let { sel = it } }
    var msg by remember { mutableStateOf("") }
    var enrolledTick by remember { mutableStateOf(0) }
    val parents = people.filter { it.role == PersonRole.PARENT }
    val selPerson = parents.firstOrNull { it.id == sel }
    val enrolled = remember { mutableStateMapOf<String, Boolean>() }
    parents.forEach { p -> LaunchedEffect(p.id, p.voiceCloneApproved, enrolledTick) { vm.enrolled(p.id) { enrolled[p.id] = it } } }

    Panel(Modifier.fillMaxSize(), padding = 28.dp) {
        ScreenHeader("함께할 목소리", onBack) {
            if (selPerson != null) GhostButton("목소리 등록", icon = R.drawable.ic_add, onClick = {
                msg = "${selPerson.name} 목소리 녹음 중…"
                vm.enroll(selPerson.id) { ok -> msg = if (ok) "${selPerson.name} 목소리를 등록했어요" else "등록에 실패했어요"; enrolledTick++ }
            })
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)) {
            parents.forEach { p ->
                VoiceCard(selected = sel == p.id, onClick = { sel = p.id }) {
                    Avatar(p, 128.dp, ring = if (sel == p.id) Hg.Amber else Hg.Line)
                    Text(p.name, style = MaterialTheme.typography.headlineMedium, color = Hg.Text)
                    when {
                        p.voiceCloneApproved -> Tag("AI 목소리", Hg.Cyan)
                        enrolled[p.id] == true -> Tag("승인 전 · 기본 목소리", Hg.Muted)
                        else -> Tag("기본 목소리", Hg.Muted)
                    }
                }
            }
            VoiceCard(selected = sel == null, onClick = { sel = null }) {
                Box(Modifier.size(128.dp).clip(CircleShape).background(Hg.Blue.copy(alpha = 0.25f)).border(3.dp, if (sel == null) Hg.Amber else Hg.Line, CircleShape), contentAlignment = Alignment.Center) {
                    HgIcon(R.drawable.ic_volume, Hg.Blue, 64.dp)
                }
                Text("인사 끄기", style = MaterialTheme.typography.headlineMedium, color = Hg.Text)
                Spacer(Modifier.height(30.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable(enabled = selPerson != null) { selPerson?.let { vm.previewVoice(it.id) } }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(52.dp).clip(CircleShape).border(1.5.dp, Hg.Muted, CircleShape), contentAlignment = Alignment.Center) { HgIcon(R.drawable.ic_volume, Hg.Text, 26.dp) }
                Waveform(Modifier.size(48.dp, 34.dp), bars = 9)
                Label("미리 듣기", color = Hg.Text)
            }
            AmberButton(
                selPerson?.let { "${it.name} 목소리로 설정" } ?: "인사 끄기",
                onClick = { vm.setGreetingVoice(sel); msg = "설정했어요"; onBack?.invoke() },
                enabled = sel != current, height = 72.dp, modifier = Modifier.width(440.dp),
                leading = { SteeringWheel(size = 36.dp, color = if (sel != current) Hg.OnAmber else Hg.Dim) },
            )
            selPerson?.let { p ->
                if (p.voiceCloneApproved) GhostButton("AI 승인 철회", onClick = { vm.revokeClone(p.id); msg = "${p.name} AI 목소리 승인을 철회하고 프로필을 삭제했어요"; enrolledTick++ })
                else GhostButton("AI 목소리 승인", enabled = enrolled[p.id] == true, onClick = {
                    vm.approveClone(p.id) { ok -> msg = if (ok) "${p.name} AI 목소리를 승인했어요" else "먼저 목소리를 등록해 주세요" }
                })
            }
        }
        if (msg.isNotBlank()) Label(msg, color = Hg.Amber, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VoiceCard(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.width(360.dp).fillMaxHeight().clip(shape)
            .background(if (selected) Hg.Amber.copy(alpha = 0.07f) else Hg.PanelDeep.copy(alpha = 0.5f))
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Hg.Amber else Hg.Line, shape)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) { content() }
        if (selected) Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(40.dp).clip(CircleShape).background(Hg.Amber), contentAlignment = Alignment.Center) {
            HgIcon(R.drawable.ic_check, Hg.OnAmber, 28.dp)
        }
    }
}

/** "함께한 대화, 남길까요?": everyone must tap agree; any "다음에" means nothing is recorded. */
@Composable
fun ConsentScreen(vm: MainViewModel, onBack: () -> Unit) {
    val family by vm.familyState.collectAsStateWithLifecycle()
    val prompt by vm.consentPrompt.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val tripId by vm.tripId.collectAsStateWithLifecycle()
    val picked = remember { mutableStateMapOf<String, Boolean>() }
    val riders = prompt?.riders ?: people
    val answers = prompt?.answers.orEmpty()

    Panel(Modifier.fillMaxSize(), padding = 28.dp) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { HgIcon(R.drawable.ic_back, Hg.Text, 36.dp) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (family) {
                        FamilyState.RECORDING -> "대화를 기록하고 있어요"
                        FamilyState.PAUSED -> "잠시 멈췄어요"
                        else -> "함께한 대화, 남길까요?"
                    },
                    style = MaterialTheme.typography.headlineMedium, color = Hg.Text,
                )
                Spacer(Modifier.height(6.dp))
                Label(
                    when (family) {
                        FamilyState.RECORDING -> "\"기록 그만\"이라고 말해도 멈춰요"
                        FamilyState.PAUSED -> "다시 시작하거나 끝낼 수 있어요"
                        else -> if (tripId == null) "출발하면 모두에게 물어볼게요" else "모두 동의하면 기록을 시작해요"
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(90.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            riders.forEach { r ->
                val answered = answers[r.id]
                val yes = when (family) {
                    FamilyState.RECORDING, FamilyState.PAUSED -> true
                    else -> answered ?: picked[r.id] ?: false
                }
                val canTap = (family == FamilyState.ASKING || (family == FamilyState.OFF && tripId != null)) && answered == null
                Rider(r, yes, refused = answered == false, enabled = canTap, size = 132.dp) { picked[r.id] = !(picked[r.id] ?: false) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            when (family) {
                FamilyState.RECORDING -> {
                    GhostButton("일시정지", icon = R.drawable.ic_pause, onClick = vm::pauseRecording, height = 72.dp)
                    AmberButton("기록 그만", onClick = vm::stopRecording, height = 72.dp, modifier = Modifier.width(380.dp))
                }
                FamilyState.PAUSED -> {
                    GhostButton("종료", onClick = vm::stopRecording, height = 72.dp)
                    AmberButton("다시 시작", onClick = vm::resumeRecording, height = 72.dp, modifier = Modifier.width(380.dp))
                }
                else -> {
                    val all = riders.isNotEmpty() && riders.all { answers[it.id] == true || picked[it.id] == true }
                    Text(
                        "다음에", color = Hg.Muted, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                            if (family == FamilyState.ASKING) riders.firstOrNull { answers[it.id] == null }?.let { vm.consent(it.id, false) }
                            onBack()
                        }.padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                    AmberButton("기록 시작", enabled = all && tripId != null, height = 72.dp, modifier = Modifier.width(380.dp), onClick = {
                        val ids = riders.filter { answers[it.id] == null }.map { it.id }
                        if (family == FamilyState.OFF) vm.askFamily().invokeOnCompletion { vm.consentAll(ids) } else vm.consentAll(ids)
                    })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Rider(p: Person, yes: Boolean, refused: Boolean, enabled: Boolean, size: Dp, onToggle: () -> Unit) {
    val ring = when { refused -> Hg.Rec; yes -> Hg.Amber; else -> Hg.Line }
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(enabled = enabled, onClick = onToggle).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(p, size, ring)
        Text(p.name, style = MaterialTheme.typography.titleLarge, color = Hg.Text, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).then(if (yes) Modifier.background(Hg.Amber) else Modifier.border(2.dp, if (refused) Hg.Rec else Hg.Muted, CircleShape)),
                contentAlignment = Alignment.Center,
            ) { if (yes) HgIcon(R.drawable.ic_check, Hg.OnAmber, 24.dp) }
            Label(when { refused -> "거부"; yes -> "동의함"; else -> "동의하기" }, color = if (refused) Hg.Rec else if (yes) Hg.Text else Hg.Muted)
        }
    }
}
