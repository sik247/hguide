package com.hguide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hguide.R
import com.hguide.core.PersonRole
import com.hguide.core.PlaceType
import com.hguide.core.TripContext
import kotlinx.coroutines.launch

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Panel(modifier.fillMaxWidth(), padding = 26.dp) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Hg.Text)
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val people by vm.people.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val greetVoice by vm.greetVoice.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val s = vm.settings
    var msg by remember { mutableStateOf("") }
    var mStart by remember { mutableIntStateOf(s.morningStartHour) }
    var mEnd by remember { mutableIntStateOf(s.morningEndHour) }
    var eStart by remember { mutableIntStateOf(s.eveningStartHour) }
    var eEnd by remember { mutableIntStateOf(s.eveningEndHour) }
    var homeAddr by remember { mutableStateOf("") }
    var workAddr by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf(PersonRole.PARENT) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) } // re-reads custom greeting presence after record/delete

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("설정", style = MaterialTheme.typography.headlineMedium, color = Hg.Text, modifier = Modifier.weight(1f))
            if (msg.isNotBlank()) Label(msg, color = Hg.Amber)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Section("집 · 회사") {
                    listOf(Triple(PlaceType.HOME, "집", homeAddr), Triple(PlaceType.WORK, "회사", workAddr)).forEach { (type, label, addr) ->
                        val p = places.firstOrNull { it.type == type }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            HgIcon(if (type == PlaceType.HOME) R.drawable.ic_home else R.drawable.ic_place, Hg.Cyan, 30.dp)
                            Text(label, style = MaterialTheme.typography.titleMedium, color = Hg.Text, modifier = Modifier.width(60.dp))
                            Label(p?.let { "%.4f, %.4f".format(it.lat, it.lng) } ?: "미설정", modifier = Modifier.weight(1f))
                            GhostButton("현재 위치", onClick = { msg = if (vm.savePlaceHere(type)) "$label 위치를 저장했어요" else "아직 위치를 받지 못했어요" })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            OutlinedTextField(
                                value = addr, onValueChange = { if (type == PlaceType.HOME) homeAddr = it else workAddr = it },
                                label = { Text("$label 주소 검색") }, modifier = Modifier.weight(1f), singleLine = true,
                            )
                            GhostButton("주소로 저장", onClick = { scope.launch { msg = if (vm.savePlaceByAddress(type, addr)) "$label 저장 완료" else "주소를 찾지 못했어요" } })
                        }
                    }
                }
                Section("출근 · 퇴근 시간") {
                    Stepper("출근 시작", mStart, 0..12) { mStart = it }
                    Stepper("출근 끝", mEnd, 1..14) { mEnd = it }
                    Stepper("퇴근 시작", eStart, 12..22) { eStart = it }
                    Stepper("퇴근 끝 (이후 야근)", eEnd, 13..24) { eEnd = it }
                    GhostButton("시간 저장", onClick = { vm.setHours(mStart, mEnd, eStart, eEnd); msg = "시간대를 저장했어요" }, color = Hg.Amber)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Section("직접 남긴 인사") {
                    val owner = greetVoice
                    val ownerName = people.firstOrNull { it.id == owner }?.name
                    if (owner == null) Label("가족 탭에서 목소리를 먼저 골라 주세요")
                    else {
                        Label("${ownerName ?: "가족"} · 직접 녹음한 인사는 AI 목소리보다 먼저 나와요", style = MaterialTheme.typography.bodyMedium)
                        listOf(TripContext.MORNING_COMMUTE to "출근", TripContext.EVENING_HOME to "퇴근", TripContext.LATE_NIGHT_HOME to "야근", TripContext.WEEKEND to "주말").forEach { (ctx, label) ->
                            val has = remember(tick, owner) { s.customGreetingPath(owner, ctx) != null }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("$label 인사", style = MaterialTheme.typography.titleMedium, color = Hg.Text, modifier = Modifier.width(130.dp))
                                if (has) Tag("녹음됨") else Tag("없음", Hg.Dim)
                                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                                    GhostButton("녹음", icon = R.drawable.ic_mic, onClick = { msg = "$label 인사 녹음 중 (5초)…"; vm.recordCustomGreeting(owner, ctx) { ok -> msg = if (ok) "$label 인사를 저장했어요" else "녹음 실패"; tick++ } })
                                    if (has) GhostButton("삭제", icon = R.drawable.ic_delete, color = Hg.Muted, onClick = { vm.clearCustomGreeting(owner, ctx); msg = "삭제했어요"; tick++ })
                                }
                            }
                        }
                    }
                }
                Section("가족") {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        people.forEach { p ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Avatar(p, 72.dp)
                                Label(p.name, color = Hg.Text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("이름 (예: 할머니)") }, singleLine = true, modifier = Modifier.weight(1f))
                        Choice("부모", newRole == PersonRole.PARENT) { newRole = PersonRole.PARENT }
                        Choice("자녀", newRole == PersonRole.CHILD) { newRole = PersonRole.CHILD }
                        GhostButton("추가", icon = R.drawable.ic_add, color = Hg.Amber, onClick = { if (newName.isNotBlank()) { vm.addPerson(newName, newRole); newName = "" } })
                    }
                }
                Section("개인정보") {
                    Label("원본 녹음은 30일 뒤 자동으로 지워져요. 추억 클립은 남아요.", style = MaterialTheme.typography.bodyMedium)
                    if (!confirmDelete) GhostButton("모든 녹음 · 추억 삭제", icon = R.drawable.ic_delete, color = Hg.Rec, onClick = { confirmDelete = true })
                    else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GhostButton("정말 삭제", color = Hg.Rec, onClick = { vm.deleteAllRecordings(); confirmDelete = false; msg = "모두 삭제했어요" })
                        GhostButton("취소", onClick = { confirmDelete = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text, color = if (selected) Hg.Amber else Hg.Muted, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.height(56.dp).clip(shape).background(if (selected) Hg.Amber.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (selected) Hg.Amber else Hg.Line, shape).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Label(label, color = Hg.Text, modifier = Modifier.weight(1f))
        GhostButton("−", onClick = { if (value > range.first) onChange(value - 1) }, height = 52.dp)
        Text("%02d시".format(value), style = MaterialTheme.typography.titleMedium, color = Hg.Text, modifier = Modifier.width(70.dp))
        GhostButton("＋", onClick = { if (value < range.last) onChange(value + 1) }, height = 52.dp)
    }
}
