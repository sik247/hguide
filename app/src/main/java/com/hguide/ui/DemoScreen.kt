package com.hguide.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hguide.core.GeoPoint
import com.hguide.core.TripGear
import com.hguide.core.TurnSignal
import com.hguide.demo.DemoActions

/** Developer console for fake-gateway builds (the stand-in for Pleos Virtual Console). Always usable, even "while driving". */
@Composable
fun DemoScreen(vm: MainViewModel) {
    val d = vm.demo ?: return
    val clips by vm.clips.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()

    @Composable fun Row2(title: String, content: @Composable () -> Unit) = Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("개발용 가상 콘솔 (Fake Pleos)", style = MaterialTheme.typography.titleLarge)
        Row2("시간") {
            OutlinedButton(onClick = { d.setTime(true, 8, 10) }) { Text("평일 08:10") }
            OutlinedButton(onClick = { d.setTime(true, 18, 30) }) { Text("평일 18:30") }
            OutlinedButton(onClick = { d.setTime(true, 22, 30) }) { Text("평일 22:30") }
            OutlinedButton(onClick = { d.setTime(false, 11, 0) }) { Text("주말 11:00") }
        }
        Row2("위치 · 목적지") {
            OutlinedButton(onClick = { d.teleport(DemoActions.HOME, "집") }) { Text("집") }
            OutlinedButton(onClick = { d.teleport(DemoActions.WORK, "회사") }) { Text("회사") }
            OutlinedButton(onClick = { d.setDestination(DemoActions.WORK, "회사") }) { Text("목적지=회사") }
            OutlinedButton(onClick = { d.setDestination(null, null) }) { Text("목적지 해제") }
            clips.take(6).forEach { c -> OutlinedButton(onClick = { d.teleport(GeoPoint(c.lat, c.lng), c.title) }) { Text("📍 ${c.title}") } }
        }
        Row2("차량") {
            Button(onClick = { d.gear(TripGear.DRIVE) }) { Text("주차 해제 (D)") }
            OutlinedButton(onClick = { d.gear(TripGear.PARK) }) { Text("주차 (P)") }
            OutlinedButton(onClick = { d.signal(TurnSignal.LEFT) }) { Text("좌 깜빡이") }
            OutlinedButton(onClick = { d.signal(TurnSignal.RIGHT) }) { Text("우 깜빡이") }
            OutlinedButton(onClick = { d.signal(TurnSignal.OFF) }) { Text("깜빡이 끔") }
        }
        Row2("주행 시뮬레이션") {
            OutlinedButton(onClick = { d.cruise() }) { Text("직진 5초") }
            OutlinedButton(onClick = { d.laneChangeNoSignal() }) { Text("깜빡이 없이 진로 변경") }
            OutlinedButton(onClick = { d.harshBrake() }) { Text("급감속") }
            OutlinedButton(onClick = { d.enterMergeZone() }) { Text("합류 구간 진입") }
            OutlinedButton(onClick = { d.startLesson(true) }) { Text("레슨 시작") }
            OutlinedButton(onClick = { d.startLesson(false) }) { Text("레슨 종료") }
        }
        Row2("차가 물을 때 대답하기 (음성 대체)") {
            Button(onClick = { d.answer("응") }) { Text("\"응\"") }
            OutlinedButton(onClick = { d.answer("아니") }) { Text("\"아니\"") }
            OutlinedButton(onClick = { d.answer("기록 그만") }) { Text("\"기록 그만\"") }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("로그", style = MaterialTheme.typography.titleMedium)
                events.take(15).forEach { Text(it) }
            }
        }
    }
}
