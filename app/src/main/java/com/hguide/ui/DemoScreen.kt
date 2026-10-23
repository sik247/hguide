package com.hguide.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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

    @Composable fun Row2(title: String, content: @Composable () -> Unit) = Panel(Modifier.fillMaxWidth(), padding = 20.dp) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Hg.Text)
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
    @Composable fun B(text: String, primary: Boolean = false, onClick: () -> Unit) = GhostButton(text, onClick = onClick, color = if (primary) Hg.Amber else Hg.Text, height = 54.dp)

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("개발용 가상 콘솔 (Fake Pleos)", style = MaterialTheme.typography.headlineMedium, color = Hg.Text)
        Row2("시연 (녹화용)") {
            B("▶ 자동 시연 30초", true) { vm.startAutoplay() }
            B("① 초기화") { d.resetForDemo() }
            B("② 아침 출발") { d.morningStart() }
            B("③ 모두 \"응\"") { d.answerAllYes() }
            B("④ 코칭 받기") { d.coachLaneChange() }
            B("⑤ 추억 장소 지나기") { d.passMemory() }
            B("⑥ 도착 · 주차") { d.arriveAndPark() }
        }
        Row2("시간") {
            B("평일 08:10") { d.setTime(true, 8, 10) }
            B("평일 18:30") { d.setTime(true, 18, 30) }
            B("평일 22:30") { d.setTime(true, 22, 30) }
            B("주말 11:00") { d.setTime(false, 11, 0) }
        }
        Row2("위치 · 목적지") {
            B("집") { d.teleport(DemoActions.HOME, "집") }
            B("회사") { d.teleport(DemoActions.WORK, "회사") }
            B("목적지=회사") { d.setDestination(DemoActions.WORK, "회사") }
            B("목적지 해제") { d.setDestination(null, null) }
            clips.take(6).forEach { c -> B("📍 ${c.title}") { d.teleport(GeoPoint(c.lat, c.lng), c.title) } }
        }
        Row2("차량") {
            B("주차 해제 (D)", true) { d.gear(TripGear.DRIVE) }
            B("주차 (P)") { d.gear(TripGear.PARK) }
            B("좌 깜빡이") { d.signal(TurnSignal.LEFT) }
            B("우 깜빡이") { d.signal(TurnSignal.RIGHT) }
            B("깜빡이 끔") { d.signal(TurnSignal.OFF) }
        }
        Row2("주행 시뮬레이션") {
            B("직진 5초") { d.cruise() }
            B("깜빡이 없이 진로 변경") { d.laneChangeNoSignal() }
            B("급감속") { d.harshBrake() }
            B("합류 구간 진입") { d.enterMergeZone() }
            B("레슨 시작") { d.startLesson(true) }
            B("레슨 종료") { d.startLesson(false) }
        }
        Row2("차가 물을 때 대답하기 (음성 대체)") {
            B("\"응\"", true) { d.answer("응") }
            B("\"아니\"") { d.answer("아니") }
            B("\"기록 그만\"") { d.answer("기록 그만") }
        }
        Panel(Modifier.fillMaxWidth(), padding = 20.dp) {
            Text("로그", style = MaterialTheme.typography.titleMedium, color = Hg.Text)
            events.take(15).forEach { Label(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
