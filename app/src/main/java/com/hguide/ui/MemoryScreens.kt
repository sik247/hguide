package com.hguide.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hguide.R
import com.hguide.data.MemoryClip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)

/** Playlist: filter by person / place / period, tap a card to open the player. */
@Composable
fun MemoriesScreen(vm: MainViewModel, nav: Nav) {
    val clips by vm.clips.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val recap by vm.recap.collectAsStateWithLifecycle()
    var person by remember { mutableStateOf<String?>(null) }
    var place by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf("전체") }
    val names = people.associate { it.id to it.name }
    val now = System.currentTimeMillis()
    val shown = clips.filter {
        (person == null || it.personId == person) && (place == null || it.placeName == place) &&
            when (range) { "이번 달" -> now - it.createdAt < 31L * 86_400_000; "올해" -> now - it.createdAt < 366L * 86_400_000; else -> true }
    }

    Column(Modifier.fillMaxSize().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("추억", style = MaterialTheme.typography.headlineMedium, color = Hg.Text)
            Spacer(Modifier.size(12.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Filter("모두", person == null && place == null && range == "전체") { person = null; place = null; range = "전체" }
                clips.map { it.personId }.distinct().forEach { id -> Filter(names[id] ?: "가족", person == id) { person = if (person == id) null else id } }
                clips.map { it.placeName }.distinct().forEach { p -> Filter(p, place == p, R.drawable.ic_place) { place = if (place == p) null else p } }
                listOf("이번 달", "올해").forEach { r -> Filter(r, range == r) { range = if (range == r) "전체" else r } }
            }
        }
        recap?.takeIf { it.clips.isNotEmpty() }?.let { r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Waveform(Modifier.size(40.dp, 26.dp), bars = 7)
                Label("방금 주행에서 ${r.clips.size}개의 순간을 남겼어요", color = Hg.Amber)
            }
        }
        if (shown.isEmpty()) {
            Panel(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    HgIcon(R.drawable.ic_photo, Hg.Dim, 72.dp)
                    Text("아직 추억이 없어요", style = MaterialTheme.typography.titleLarge, color = Hg.Text)
                    Label("가족과 대화를 기록하면 좋은 순간이 모여요")
                    GhostButton("대화 기록", icon = R.drawable.ic_people, onClick = { nav.go(Tab.FAMILY, Detail.Consent) })
                }
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(shown, key = { it.id }) { c -> ClipCard(c, names[c.personId]) { nav.detail = Detail.Player(c.id) } }
            }
        }
    }
}

@Composable
private fun Filter(text: String, selected: Boolean, icon: Int? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(52.dp).clip(shape)
            .background(if (selected) Hg.Amber.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (selected) Hg.Amber else Hg.Line, shape)
            .clickable(onClick = onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { HgIcon(it, if (selected) Hg.Amber else Hg.Muted, 22.dp) }
        Text(text, color = if (selected) Hg.Amber else Hg.Text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ClipCard(c: MemoryClip, who: String?, onClick: () -> Unit) {
    Column(Modifier.panel(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Art(memoryArt(c.id), Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(14.dp)) {
            Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(48.dp).clip(RoundedCornerShape(50)).background(Hg.Bg.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                HgIcon(R.drawable.ic_play, Hg.Text, 28.dp)
            }
        }
        Text(c.title, style = MaterialTheme.typography.titleMedium, color = Hg.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(listOfNotNull(who, c.placeName).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Label(mmss(c.endMs - c.startMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** One clip: artwork, title, waveform progress, big play/pause. */
@Composable
fun PlayerScreen(vm: MainViewModel, clipId: Long, onBack: () -> Unit) {
    val clips by vm.clips.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val c = clips.firstOrNull { it.id == clipId } ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val playing = speaking?.file?.path == c.audioPath
    val duration = (c.endMs - c.startMs).coerceAtLeast(1)
    val progress = remember(c.id) { Animatable(0f) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (playing) { progress.snapTo(0f); progress.animateTo(1f, tween(duration.toInt(), easing = LinearEasing)) }
    }

    Panel(Modifier.fillMaxSize(), padding = 28.dp) {
        ScreenHeader("추억", onBack)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
            Art(memoryArt(c.id), Modifier.weight(0.55f).fillMaxHeight(), RoundedCornerShape(20.dp)) {
                Box(Modifier.matchParentSize().border(1.dp, Hg.Line, RoundedCornerShape(20.dp)))
            }
            Column(Modifier.weight(0.45f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Tag("직접 녹음")
                Text(c.title, style = MaterialTheme.typography.displayMedium, color = Hg.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Label(listOfNotNull(dateFmt.format(Date(c.createdAt)), c.placeName, people.firstOrNull { it.id == c.personId }?.name).joinToString("  ·  "), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Waveform(Modifier.fillMaxWidth().height(64.dp), bars = 64, progress = if (playing) progress.value else 0f, seed = c.id.toInt())
                Row(Modifier.fillMaxWidth()) {
                    Label(mmss((duration * (if (playing) progress.value else 0f)).toLong()), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Label(mmss(duration), style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
                    GhostButton(if (c.proximityEnabled) "장소 알림 켜짐" else "장소 알림 꺼짐", icon = R.drawable.ic_place, color = if (c.proximityEnabled) Hg.Cyan else Hg.Muted, onClick = { vm.toggleProximity(c) })
                    CircleButton(if (playing) R.drawable.ic_pause else R.drawable.ic_play, onClick = { if (playing) vm.stopPlayback() else vm.play(c) }, size = 104.dp, filled = true)
                    GhostButton(if (confirmDelete) "정말 삭제" else "삭제", icon = R.drawable.ic_delete, color = if (confirmDelete) Hg.Rec else Hg.Muted, onClick = {
                        if (confirmDelete) { vm.delete(c); onBack() } else confirmDelete = true
                    })
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
