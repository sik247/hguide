package com.hguide.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hguide.R
import com.hguide.core.DrivingEventType
import com.hguide.core.FamilyState
import com.hguide.core.SpeechPriority
import com.hguide.core.TripContext
import kotlinx.coroutines.delay

/** 운전 연습: turn the parent-voice coaching on/off and see what it caught on this (or the last) trip. */
@Composable
fun PracticeScreen(vm: MainViewModel, nav: Nav) {
    val lesson by vm.lessonActive.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val voiceId by vm.greetVoice.collectAsStateWithLifecycle()
    val askingId by vm.familyAsking.collectAsStateWithLifecycle()
    val tripId by vm.tripId.collectAsStateWithLifecycle()
    val counts by produceState(emptyMap<DrivingEventType, Int>(), tripId, lesson) { value = vm.lastTripEvents() }
    val voice = people.firstOrNull { it.id == voiceId }

    Row(Modifier.fillMaxSize().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Panel(Modifier.weight(0.55f).fillMaxHeight(), padding = 36.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VoiceMark(64.dp)
                Label(voice?.let { "${it.name} · ${if (it.voiceCloneApproved) "AI 목소리" else "기본 목소리"}" } ?: "기본 목소리", color = Hg.Text, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(32.dp))
            Text("운전 연습", style = MaterialTheme.typography.displayMedium, color = Hg.Text)
            Spacer(Modifier.height(12.dp))
            Label(if (lesson) "필요한 순간에 목소리로 알려줘요" else "깜빡이 · 급감속 · 합류 구간을 봐 드려요")
            Spacer(Modifier.weight(1f))
            if (!lesson) AmberButton("연습 시작", onClick = { vm.lesson(true) }, modifier = Modifier.widthIn(min = 420.dp), leading = { SteeringWheel(size = 44.dp) })
            else GhostButton("연습 끝내기", onClick = { vm.lesson(false) }, height = 84.dp, modifier = Modifier.widthIn(min = 320.dp))
        }
        Panel(Modifier.weight(0.45f).fillMaxHeight(), padding = 32.dp) {
            Text(if (tripId != null) "이번 주행" else "지난 주행", style = MaterialTheme.typography.titleLarge, color = Hg.Text)
            Spacer(Modifier.height(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StatRow(R.drawable.ic_merge, "깜빡이 없이 차선 변경", counts[DrivingEventType.LANE_CHANGE_NO_SIGNAL] ?: 0, Modifier.weight(1f))
                StatRow(R.drawable.ic_pause, "급감속", counts[DrivingEventType.HARSH_DECEL] ?: 0, Modifier.weight(1f))
                StatRow(R.drawable.ic_place, "합류 구간", counts[DrivingEventType.ZONE_ENTER] ?: 0, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatRow(icon: Int, label: String, count: Int, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Hg.PanelDeep.copy(alpha = 0.6f)).border(1.dp, Hg.LineSoft, RoundedCornerShape(18.dp)).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).border(1.5.dp, Hg.Cyan.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) { HgIcon(icon, Hg.Cyan, 28.dp) }
        Label(label, color = Hg.Text, modifier = Modifier.weight(1f))
        Text("${count}회", style = MaterialTheme.typography.headlineMedium, color = if (count == 0) Hg.Muted else Hg.Amber)
    }
}

private fun TripContext.label() = when (this) {
    TripContext.MORNING_COMMUTE -> "출근길"
    TripContext.EVENING_HOME -> "퇴근길"
    TripContext.LATE_NIGHT_HOME -> "늦은 귀갓길"
    TripContext.WEEKEND -> "주말 나들이"
    TripContext.OTHER -> "주행 중"
}

/**
 * Shown instead of every tab while the car is not parked. Read-only by design: all touches are swallowed
 * (voice only while driving). The demo-console shortcut exists only in fake-gateway builds.
 */
@Composable
fun DrivingScreen(vm: MainViewModel, onDemo: (() -> Unit)?) {
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val aiVoice by vm.aiVoice.collectAsStateWithLifecycle()
    val family by vm.familyState.collectAsStateWithLifecycle()
    val lesson by vm.lessonActive.collectAsStateWithLifecycle()
    val ctx by vm.tripContext.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val voiceId by vm.greetVoice.collectAsStateWithLifecycle()
    val askingId by vm.familyAsking.collectAsStateWithLifecycle()

    // Keep the last spoken line on screen a moment after the audio ends.
    var caption by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var captionKind by remember { mutableStateOf(SpeechPriority.GREETING) }
    LaunchedEffect(speaking) {
        val s = speaking
        if (s != null) {
            (s.caption ?: s.text)?.let { caption = it to s.aiVoice; captionKind = s.priority }
        } else if (caption != null) { delay(2_500); caption = null }
    }
    LaunchedEffect(family) { if (family == FamilyState.RECORDING) caption = null } // the consent question is answered

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Image(painterResource(R.drawable.art_drive_bg), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Hg.Bg.copy(alpha = 0.92f), 0.28f to Color.Transparent, 0.8f to Color.Transparent, 1f to Hg.Bg.copy(alpha = 0.8f))))
        Column(Modifier.fillMaxSize()) {
            TopBar(vm)
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 44.dp)) {
                val asking = family == FamilyState.ASKING
                // A rider's consent turn is silent: it replaces any lingering caption.
                val rider = people.firstOrNull { it.id == askingId }?.takeIf { asking }
                val line = if (rider != null) "${rider.name}, 동의하시나요?" to false else caption
                if (line != null || asking) {
                    val who = people.firstOrNull { it.id == voiceId }?.name
                    val label = when {
                        line == null || rider != null -> "가족 대화 기록 · \"응\" 또는 \"아니\"로 답해 주세요"
                        captionKind == SpeechPriority.MEMORY -> "추억 재생"
                        line.second -> "${who ?: "가족"} · AI 목소리"
                        else -> "안내"
                    }
                    SpeechPill(label, line?.first ?: "\"응\" 또는 \"아니\"라고 말해 주세요", Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                }
                Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (lesson) InfoCard(R.drawable.ic_school, "운전 연습 중", ctx?.label() ?: "주행 중")
                    else ctx?.let { InfoCard(R.drawable.ic_home, it.label(), "음성으로 안내해요") }
                    if (family == FamilyState.RECORDING) InfoCard(null, "대화 기록 중", "\"기록 그만\"이라고 말하면 멈춰요")
                }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HgIcon(R.drawable.ic_mic, Hg.Muted, 32.dp)
                    Label("음성으로 안내 중", color = Hg.Text)
                }
            }
        }
        onDemo?.let { go ->
            // Dev-only escape hatch to the virtual console (fake gateways); not present in real-vehicle builds.
            Box(Modifier.align(Alignment.BottomEnd).padding(24.dp)) { GhostButton("데모 콘솔", icon = R.drawable.ic_tune, onClick = go, color = Hg.Muted, height = 52.dp) }
        }
    }
}

@Composable
private fun SpeechPill(label: String, text: String, modifier: Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.widthIn(max = 1100.dp).clip(shape).background(Hg.Bg.copy(alpha = 0.82f)).border(2.dp, Hg.Amber.copy(alpha = 0.9f), shape).padding(start = 22.dp, end = 48.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        VoiceMark(76.dp)
        Column {
            Text(label, color = Hg.Amber, style = MaterialTheme.typography.labelLarge)
            Text(text, color = Hg.Text, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
        }
    }
}

@Composable
private fun InfoCard(icon: Int?, title: String, sub: String) {
    Row(
        Modifier.widthIn(min = 380.dp).panel(RoundedCornerShape(22.dp), Hg.Cyan.copy(alpha = 0.5f)).padding(horizontal = 28.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (icon != null) HgIcon(icon, Hg.Cyan, 52.dp)
        else Box(Modifier.size(22.dp).clip(CircleShape).background(Hg.Rec))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Hg.Text)
            Label(sub, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
