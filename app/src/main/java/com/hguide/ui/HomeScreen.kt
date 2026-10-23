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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hguide.R
import com.hguide.core.FamilyState

@Composable
fun HomeScreen(vm: MainViewModel, nav: Nav) {
    val people by vm.people.collectAsStateWithLifecycle()
    val voiceId by vm.greetVoice.collectAsStateWithLifecycle()
    val lesson by vm.lessonActive.collectAsStateWithLifecycle()
    val tripId by vm.tripId.collectAsStateWithLifecycle()
    val clips by vm.clips.collectAsStateWithLifecycle()
    val family by vm.familyState.collectAsStateWithLifecycle()
    val voice = people.firstOrNull { it.id == voiceId }

    Row(Modifier.fillMaxSize().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // Driving hero: one voice, one headline, one action.
        Box(Modifier.weight(0.68f).fillMaxHeight().panel()) {
            Art(
                R.drawable.art_home_road, Modifier.fillMaxHeight().fillMaxWidth(0.6f).align(Alignment.CenterEnd),
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
            ) {
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to Hg.Panel, 0.18f to Hg.Panel.copy(alpha = 0.6f), 0.42f to Color.Transparent)))
            }
            Column(Modifier.fillMaxHeight().padding(36.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    VoiceMark(64.dp)
                    VoiceChip(
                        text = voice?.let { "${it.name}  ·  ${if (it.voiceCloneApproved) "AI 목소리" else "기본 목소리"}" } ?: "인사 꺼짐",
                        onClick = { nav.detail = Detail.VoicePick },
                    )
                }
                Spacer(Modifier.height(36.dp))
                Text(voice?.let { "${withAnd(it.name)} 함께 운전" } ?: "오늘도 안전 운전", style = MaterialTheme.typography.displayMedium, color = Hg.Text)
                if (lesson) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Hg.Cyan))
                        Label("운전 연습 안내 켜짐", color = Hg.Cyan)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (!lesson) AmberButton("운전 시작", onClick = { vm.lesson(true) }, modifier = Modifier.width(440.dp), leading = { SteeringWheel(size = 44.dp) })
                    else GhostButton("안내 끄기", onClick = { vm.lesson(false) }, height = 84.dp, modifier = Modifier.width(300.dp))
                    if (tripId != null) GhostButton("주행 종료", onClick = vm::endTrip, height = 84.dp)
                }
            }
        }

        // Family memories: quiet, secondary.
        Panel(Modifier.weight(0.32f).fillMaxHeight(), padding = 26.dp) {
            Text("함께한 추억", style = MaterialTheme.typography.titleLarge, color = Hg.Text)
            Spacer(Modifier.height(16.dp))
            val latest = clips.firstOrNull()
            Art(if (latest != null) memoryArt(latest.id) else R.drawable.art_memory_han_river, Modifier.fillMaxWidth().weight(1f)) {
                if (latest == null) Box(Modifier.matchParentSize().background(Hg.Bg.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Label("아직 추억이 없어요", color = Hg.Text)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (latest != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Tag("직접 녹음")
                            HgIcon(R.drawable.ic_clock, Hg.Muted, 20.dp)
                            Label(mmss(latest.endMs - latest.startMs), style = MaterialTheme.typography.labelMedium)
                        }
                        Text(latest.title, style = MaterialTheme.typography.titleMedium, color = Hg.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    CircleButton(R.drawable.ic_play, onClick = { nav.go(Tab.MEMORIES, Detail.Player(latest.id)) })
                }
                Spacer(Modifier.height(12.dp))
            }
            Divider()
            Row(
                Modifier.fillMaxWidth().height(68.dp).clickable { nav.go(Tab.FAMILY, Detail.Consent) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (family) {
                    FamilyState.RECORDING -> { Box(Modifier.size(14.dp).clip(CircleShape).background(Hg.Rec)); Label("대화 기록 중", color = Hg.Text, modifier = Modifier.weight(1f)) }
                    FamilyState.PAUSED -> { HgIcon(R.drawable.ic_pause, Hg.Muted, 30.dp); Label("기록 일시정지", color = Hg.Text, modifier = Modifier.weight(1f)) }
                    else -> { HgIcon(R.drawable.ic_people, Hg.Muted, 34.dp); Label("대화 기록", color = Hg.Text, modifier = Modifier.weight(1f)) }
                }
                HgIcon(R.drawable.ic_chevron_right, Hg.Muted, 32.dp)
            }
        }
    }
}

@Composable
private fun VoiceChip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(56.dp).clip(shape).border(1.dp, Hg.Line, shape).background(Hg.PanelDeep.copy(alpha = 0.7f)).clickable(onClick = onClick).padding(start = 24.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, color = Hg.Text, style = MaterialTheme.typography.labelLarge)
        HgIcon(R.drawable.ic_chevron_right, Hg.Text, 26.dp)
    }
}
