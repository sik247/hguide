package com.hguide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hguide.core.PersonRole
import com.hguide.core.PlaceType
import com.hguide.core.TripContext
import kotlinx.coroutines.launch

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val people by vm.people.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val s = vm.settings
    var msg by remember { mutableStateOf("") }
    var greetVoice by remember { mutableStateOf(s.greetingVoicePersonId) }
    var mStart by remember { mutableIntStateOf(s.morningStartHour) }
    var mEnd by remember { mutableIntStateOf(s.morningEndHour) }
    var eStart by remember { mutableIntStateOf(s.eveningStartHour) }
    var eEnd by remember { mutableIntStateOf(s.eveningEndHour) }
    var homeAddr by remember { mutableStateOf("") }
    var workAddr by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf(PersonRole.PARENT) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.secondary)

        Section("집 · 회사") {
            for ((type, label, addr, setAddr) in listOf(
                Quad(PlaceType.HOME, "집", homeAddr) { v: String -> homeAddr = v },
                Quad(PlaceType.WORK, "회사", workAddr) { v: String -> workAddr = v },
            )) {
                val p = places.firstOrNull { it.type == type }
                Text("$label: ${p?.let { "%.4f, %.4f".format(it.lat, it.lng) } ?: "미설정"}")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { msg = if (vm.savePlaceHere(type)) "$label 를 현재 위치로 저장했어요" else "아직 위치를 받지 못했어요" }) { Text("현재 위치로 저장") }
                    OutlinedTextField(value = addr, onValueChange = setAddr, label = { Text("주소 검색") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedButton(onClick = { scope.launch { msg = if (vm.savePlaceByAddress(type, addr)) "$label 저장 완료" else "주소를 찾지 못했어요 (이 기기에는 주소 검색이 없을 수 있어요)" } }) { Text("주소로 저장") }
                }
            }
        }

        Section("출근 · 퇴근 시간대") {
            Stepper("출근 시작", mStart, 0..12) { mStart = it }
            Stepper("출근 끝", mEnd, 1..14) { mEnd = it }
            Stepper("퇴근 시작", eStart, 12..22) { eStart = it }
            Stepper("퇴근 끝 (이후는 야근)", eEnd, 13..24) { eEnd = it }
            Button(onClick = { vm.setHours(mStart, mEnd, eStart, eEnd); msg = "시간대를 저장했어요" }) { Text("저장") }
        }

        Section("인사 받을 목소리") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                people.filter { it.role == PersonRole.PARENT }.forEach { p ->
                    FilterChip(selected = greetVoice == p.id, onClick = { greetVoice = p.id; vm.setGreetingVoice(p.id) }, label = { Text(p.name) })
                }
                FilterChip(selected = greetVoice == null, onClick = { greetVoice = null; vm.setGreetingVoice(null) }, label = { Text("끔") })
            }
            Text("AI 목소리는 목소리 주인이 직접 승인한 경우에만 쓰여요. 아니면 기본 음성으로 말해요.")
        }

        Section("부모 목소리 · AI 목소리 승인") {
            people.filter { it.role == PersonRole.PARENT }.forEach { p ->
                Text("${p.name} — AI 목소리: ${if (p.voiceCloneApproved) "승인됨" else "미승인"}", style = MaterialTheme.typography.titleMedium)
                Text("등록 문장 3개를 차례로 읽어 주세요: " + "\"오늘도 안전하게 운전하자.\" / \"천천히 가도 괜찮아, 서두르지 마.\" / \"조심히 다녀오고, 집에 오면 맛있는 거 먹자.\"")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { msg = "${p.name} 목소리 녹음 중…"; vm.enroll(p.id) { ok -> msg = if (ok) "${p.name} 목소리를 등록했어요" else "등록에 실패했어요" } }) { Text("목소리 등록") }
                    if (!p.voiceCloneApproved) OutlinedButton(onClick = { vm.approveClone(p.id) { ok -> msg = if (ok) "${p.name} AI 목소리를 승인했어요" else "먼저 목소리를 등록해 주세요" } }) { Text("AI 목소리 승인") }
                    else OutlinedButton(onClick = { vm.revokeClone(p.id); msg = "${p.name} 승인을 철회하고 프로필과 캐시를 삭제했어요" }) { Text("승인 철회 (즉시 삭제)") }
                }
            }
        }

        Section("직접 남기기 (AI보다 먼저 재생)") {
            val owner = greetVoice
            if (owner == null) Text("인사 목소리를 먼저 선택해 주세요.") else {
                listOf(TripContext.MORNING_COMMUTE to "출근", TripContext.EVENING_HOME to "퇴근", TripContext.LATE_NIGHT_HOME to "야근", TripContext.WEEKEND to "주말").forEach { (ctx, label) ->
                    val has = s.customGreetingPath(owner, ctx) != null
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$label 인사: ${if (has) "녹음됨" else "없음"}", Modifier.weight(1f))
                        Button(onClick = { msg = "$label 인사 녹음 중 (5초)…"; vm.recordCustomGreeting(owner, ctx) { ok -> msg = if (ok) "$label 인사를 저장했어요" else "녹음 실패" } }) { Text("녹음") }
                        if (has) OutlinedButton(onClick = { vm.clearCustomGreeting(owner, ctx); msg = "삭제했어요" }) { Text("삭제") }
                    }
                }
            }
        }

        Section("가족 추가") {
            OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("이름 (예: 아빠)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = newRole == PersonRole.PARENT, onClick = { newRole = PersonRole.PARENT }, label = { Text("부모") })
                FilterChip(selected = newRole == PersonRole.CHILD, onClick = { newRole = PersonRole.CHILD }, label = { Text("자녀") })
                Button(onClick = { if (newName.isNotBlank()) { vm.addPerson(newName, newRole); newName = "" } }) { Text("추가") }
            }
            Text("현재: " + people.joinToString { it.name })
        }

        Section("개인정보") {
            Text("원본 녹음은 30일 후 자동 삭제돼요 (저장된 추억 클립은 유지). 트랜스크립트 원문은 로그에 남기지 않아요.")
            if (!confirmDelete) OutlinedButton(onClick = { confirmDelete = true }) { Text("모든 녹음·추억 삭제…") }
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.deleteAllRecordings(); confirmDelete = false; msg = "모두 삭제했어요" }) { Text("정말 삭제") }
                OutlinedButton(onClick = { confirmDelete = false }) { Text("취소") }
            }
        }
    }
}

private data class Quad(val a: PlaceType, val b: String, val c: String, val d: (String) -> Unit)

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.padding(end = 8.dp))
        OutlinedButton(onClick = { if (value > range.first) onChange(value - 1) }) { Text("−") }
        Text("%02d시".format(value))
        OutlinedButton(onClick = { if (value < range.last) onChange(value + 1) }) { Text("＋") }
    }
}
