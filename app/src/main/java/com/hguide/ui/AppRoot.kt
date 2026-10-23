package com.hguide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hguide.core.FamilyState

private enum class Tab(val label: String, val glyph: String) {
    HOME("홈", "🏠"), PLAYLIST("추억", "🎵"), RECAP("지난 주행", "🏁"), SETTINGS("설정", "⚙"), DEMO("데모", "🧪")
}

@Composable
fun AppRoot(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val parked by vm.parked.collectAsStateWithLifecycle()
    val family by vm.familyState.collectAsStateWithLifecycle()
    val aiVoice by vm.aiVoice.collectAsStateWithLifecycle()
    val ctx by vm.tripContext.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                Tab.entries.filter { it != Tab.DEMO || vm.demo != null }.forEach { t ->
                    NavigationRailItem(selected = tab == t, onClick = { tab = t }, icon = { Text(t.glyph) }, label = { Text(t.label) })
                }
            }
            Column(Modifier.fillMaxSize()) {
                // Always-visible status strip: recording indicator, AI voice badge, driving/parked state.
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("hguide", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (family == FamilyState.RECORDING) Chip("● 기록 중", Color(0xFFD32F2F))
                    if (family == FamilyState.PAUSED) Chip("⏸ 기록 일시정지", Color(0xFF8D6E00))
                    if (aiVoice) Chip("AI 목소리", Color(0xFF6A1B9A))
                    ctx?.let { Chip(it.name, Color(0xFF455A64)) }
                    Chip(if (parked) "주차 중 — 화면 사용 가능" else "주행 중 — 음성만", if (parked) Color(0xFF2E7D32) else Color(0xFF37474F))
                }
                val locked = !parked && tab != Tab.DEMO
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(vm)
                        Tab.PLAYLIST -> PlaylistScreen(vm)
                        Tab.RECAP -> RecapScreen(vm)
                        Tab.SETTINGS -> SettingsScreen(vm)
                        Tab.DEMO -> DemoScreen(vm)
                    }
                    if (locked) {
                        // Swallows every touch: no screen input while the car is not parked.
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)).pointerInput(Unit) { detectTapGestures { } },
                            contentAlignment = Alignment.Center,
                        ) { Text("주행 중에는 음성으로만 사용할 수 있어요.\n주차 후에 화면을 사용해 주세요.", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(text, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
}
