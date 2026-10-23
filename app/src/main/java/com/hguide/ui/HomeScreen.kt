package com.hguide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hguide.core.FamilyState

@Composable
fun HomeScreen(vm: MainViewModel) {
    val family by vm.familyState.collectAsStateWithLifecycle()
    val prompt by vm.consentPrompt.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val tripId by vm.tripId.collectAsStateWithLifecycle()
    val lesson by vm.lessonActive.collectAsStateWithLifecycle()

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (tripId == null) "대기 중 — 주행을 시작하면 인사를 해 드려요" else "주행 중 (기록 #$tripId)", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("운전 레슨 모드")
                    Switch(checked = lesson, onCheckedChange = vm::lesson)
                }
                if (tripId != null) OutlinedButton(onClick = vm::endTrip) { Text("주행 종료 → 추억 만들기") }
            }
        }

        when (family) {
            FamilyState.ASKING -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("오늘 가족이 함께 타고 있나요? 대화를 기록할까요?", style = MaterialTheme.typography.titleMedium)
                    Text("탑승자 모두가 동의해야 기록을 시작해요. 한 명이라도 거부하면 기록하지 않아요.")
                    prompt?.let { p ->
                        p.riders.forEach { r ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(r.name, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.titleMedium)
                                when (p.answers[r.id]) {
                                    true -> Text("✔ 동의")
                                    false -> Text("✖ 거부")
                                    null -> {
                                        Button(onClick = { vm.consent(r.id, true) }) { Text("동의") }
                                        OutlinedButton(onClick = { vm.consent(r.id, false) }) { Text("거부") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            FamilyState.RECORDING -> Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("● 기록 중 — \"기록 그만\"이라고 말하면 바로 멈춰요", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = vm::pauseRecording) { Text("일시정지") }
                    Button(onClick = vm::stopRecording) { Text("기록 그만") }
                }
            }
            FamilyState.PAUSED -> Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("일시정지됨", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = vm::resumeRecording) { Text("다시 시작") }
                    OutlinedButton(onClick = vm::stopRecording) { Text("종료") }
                }
            }
            FamilyState.OFF -> Unit
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("최근 활동", style = MaterialTheme.typography.titleMedium)
                if (events.isEmpty()) Text("아직 없어요")
                events.take(12).forEach { Text(it) }
            }
        }
    }
}
