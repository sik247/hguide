package com.hguide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecapScreen(vm: MainViewModel) {
    val recap by vm.recap.collectAsStateWithLifecycle()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("지난 주행 요약", style = MaterialTheme.typography.titleLarge)
        val r = recap
        if (r == null) {
            Text("주행이 끝나면 오늘의 좋은 순간이 여기에 나와요.")
        } else if (r.clips.isEmpty()) {
            Text("이번 주행에서는 저장할 추억이 없었어요. (기록하지 않았거나, 고를 만한 순간이 없었어요)")
        } else {
            Text("이번 주행에서 ${r.clips.size}개의 순간을 저장했어요.")
            r.clips.forEach { c ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium)
                            Text(c.summary)
                            Text("${c.placeName} · ${(c.endMs - c.startMs) / 1000}초")
                        }
                        Button(onClick = { vm.play(c) }) { Text("▶ 재생") }
                    }
                }
            }
        }
    }
}
