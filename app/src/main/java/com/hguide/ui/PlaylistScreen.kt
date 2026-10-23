package com.hguide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlaylistScreen(vm: MainViewModel) {
    val clips by vm.clips.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    var person by remember { mutableStateOf<String?>(null) }
    var place by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf("전체") }
    val fmt = remember { SimpleDateFormat("yyyy.MM.dd", Locale.KOREA) }
    val names = people.associate { it.id to it.name }
    val now = System.currentTimeMillis()
    val shown = clips.filter {
        (person == null || it.personId == person) && (place == null || it.placeName == place) &&
            when (range) { "이번 달" -> now - it.createdAt < 31L * 86_400_000; "올해" -> now - it.createdAt < 366L * 86_400_000; else -> true }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("우리 가족 추억 플레이리스트", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = person == null, onClick = { person = null }, label = { Text("모든 사람") })
            clips.map { it.personId }.distinct().forEach { id -> FilterChip(selected = person == id, onClick = { person = id }, label = { Text(names[id] ?: "알 수 없음") }) }
            clips.map { it.placeName }.distinct().forEach { p -> FilterChip(selected = place == p, onClick = { place = if (place == p) null else p }, label = { Text(p) }) }
            listOf("전체", "이번 달", "올해").forEach { r -> FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r) }) }
        }
        if (shown.isEmpty()) Text("아직 저장된 추억이 없어요. 가족 모드로 대화를 기록하면 좋은 순간이 여기에 모여요.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.title, style = MaterialTheme.typography.titleMedium)
                        Text(c.summary)
                        Text("${names[c.personId] ?: "가족"} · ${c.placeName} · ${fmt.format(Date(c.createdAt))} · ${c.emotionTag}", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { vm.play(c) }) { Text("▶ 재생") }
                            OutlinedButton(onClick = { vm.toggleProximity(c) }) { Text(if (c.proximityEnabled) "이 클립 장소 알림 끄기" else "장소 알림 켜기") }
                            OutlinedButton(onClick = { vm.delete(c) }) { Text("삭제") }
                        }
                    }
                }
            }
        }
    }
}
