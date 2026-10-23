package com.hguide.ui

import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hguide.R
import com.hguide.core.FamilyState
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

enum class Tab(val label: String, @DrawableRes val icon: Int) {
    HOME("홈", R.drawable.ic_home),
    PRACTICE("운전 연습", R.drawable.ic_school),
    MEMORIES("추억", R.drawable.ic_photo),
    FAMILY("가족", R.drawable.ic_people),
    SETTINGS("설정", R.drawable.ic_settings),
    DEMO("데모", R.drawable.ic_tune),
}

/** Screens opened on top of a tab. */
sealed interface Detail {
    data object VoicePick : Detail
    data object Consent : Detail
    data class Player(val clipId: Long) : Detail
}

class Nav {
    var tab by mutableStateOf(Tab.HOME)
    var detail by mutableStateOf<Detail?>(null)
    /** True while the scripted demo runs: dev-only controls are hidden so the recording shows the product only. */
    var autoplay by mutableStateOf(false)
    fun go(t: Tab, d: Detail? = null) { tab = t; detail = d }
}

@Composable
fun AppRoot(vm: MainViewModel) {
    val nav = remember { Nav() }
    val autoplayReq by vm.autoplayRequests.collectAsStateWithLifecycle()
    LaunchedEffect(autoplayReq) {
        if (autoplayReq == 0) return@LaunchedEffect
        nav.autoplay = true
        try { runAutoplay(vm, nav) } finally { nav.autoplay = false; vm.scriptPick(null) }
    }

    val parked by vm.parked.collectAsStateWithLifecycle()
    val family by vm.familyState.collectAsStateWithLifecycle()

    // The consent question pops up on screen whenever it can be answered by touch (parked).
    LaunchedEffect(family, parked) { if (family == FamilyState.ASKING && parked) nav.go(Tab.FAMILY, Detail.Consent) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Hg.BgTop, Hg.Bg)))) {
        if (!parked && nav.tab != Tab.DEMO) {
            DrivingScreen(vm, onDemo = vm.demo?.takeIf { !nav.autoplay }?.let { { nav.go(Tab.DEMO) } })
            return@Box
        }
        Column(Modifier.fillMaxSize()) {
            TopBar(vm)
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp)) {
                when (val d = nav.detail) {
                    Detail.VoicePick -> VoicePickScreen(vm, onBack = { nav.detail = null })
                    Detail.Consent -> ConsentScreen(vm, onBack = { nav.detail = null })
                    is Detail.Player -> PlayerScreen(vm, d.clipId, onBack = { nav.detail = null })
                    null -> when (nav.tab) {
                        Tab.HOME -> HomeScreen(vm, nav)
                        Tab.PRACTICE -> PracticeScreen(vm, nav)
                        Tab.MEMORIES -> MemoriesScreen(vm, nav)
                        Tab.FAMILY -> VoicePickScreen(vm, onBack = null)
                        Tab.SETTINGS -> SettingsScreen(vm)
                        Tab.DEMO -> DemoScreen(vm)
                    }
                }
            }
            BottomNav(nav, showDemo = vm.demo != null && !nav.autoplay)
        }
    }
}

/** Brand, recording indicator, AI-voice badge, clock, parked/driving state, destination. */
@Composable
fun TopBar(vm: MainViewModel) {
    val parked by vm.parked.collectAsStateWithLifecycle()
    val family by vm.familyState.collectAsStateWithLifecycle()
    val aiVoice by vm.aiVoice.collectAsStateWithLifecycle()
    val dest by vm.destination.collectAsStateWithLifecycle()
    val time by produceState(vm.now()) { while (true) { value = vm.now(); delay(1000) } }
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Row(
        Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("나현수", color = Hg.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        when (family) {
            FamilyState.RECORDING -> StatusPill { Box(Modifier.size(12.dp).clip(CircleShape).background(Hg.Rec)); Text("기록 중", color = Hg.Text, style = MaterialTheme.typography.labelLarge) }
            FamilyState.PAUSED -> StatusPill { Text("기록 일시정지", color = Hg.Muted, style = MaterialTheme.typography.labelLarge) }
            else -> Unit
        }
        if (aiVoice) StatusPill(border = Hg.Amber) { Waveform(Modifier.size(26.dp, 18.dp), bars = 6); Text("AI 목소리", color = Hg.Amber, style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.weight(1f))
        Text(time.format(fmt), color = Hg.Text, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        BarDivider()
        if (parked) StatusPill(border = Hg.Blue) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Hg.Blue), contentAlignment = Alignment.Center) {
                Text("P", color = Hg.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text("주차 중", color = Hg.Text, style = MaterialTheme.typography.labelLarge)
        } else StatusPill(border = Hg.Blue) { Text("주행 중", color = Hg.Text, style = MaterialTheme.typography.labelLarge) }
        dest?.let { d ->
            BarDivider()
            HgIcon(R.drawable.ic_home, Hg.Text, 30.dp)
            val km = vm.distanceKm(d)
            Text("${d.name ?: "목적지"}까지" + (km?.let { " %.1fkm".format(it) } ?: ""), color = Hg.Text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatusPill(border: androidx.compose.ui.graphics.Color = Hg.Line, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.height(46.dp).clip(shape).border(1.dp, border.copy(alpha = 0.8f), shape).background(Hg.PanelDeep.copy(alpha = 0.7f)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun BarDivider() = Box(Modifier.width(1.dp).height(30.dp).background(Hg.Line))

@Composable
private fun BottomNav(nav: Nav, showDemo: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Divider()
        Row(Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.filter { it != Tab.DEMO || showDemo }.forEachIndexed { i, t ->
                if (i > 0) Box(Modifier.width(1.dp).height(36.dp).background(Hg.LineSoft))
                val selected = nav.tab == t
                val color = if (selected) Hg.Amber else Hg.Muted
                Column(
                    Modifier.weight(if (t == Tab.DEMO) 0.6f else 1f).fillMaxHeight().clickable { nav.go(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    HgIcon(t.icon, color, 34.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(t.label, color = color, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(100.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) Hg.Amber else androidx.compose.ui.graphics.Color.Transparent))
                }
            }
        }
    }
}
