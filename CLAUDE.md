# hguide — Claude Code 개발 지시서

Pleos Connect(현대자동차그룹 차량 인포테인먼트, Android Automotive OS)용 차량 앱. 현대자동차그룹 차량용 앱 개발 공모전(1차 마감 2026-11-15) 제출용 MVP.

## 제품 한 줄
가족이 함께 탄 차 안의 대화를 (동의 하에) 기록하고, 부모의 목소리를 찾아 좋은 순간만 짧은 클립으로 플레이리스트에 저장한다. 그 장소를 다시 지나가면 클립을 틀어주고, 같은 목소리로 운전 레슨과 출퇴근 인사를 해준다.

## 작업 원칙
- Pleos SDK 시그니처를 추측하지 않는다. 확인이 안 되면 `// TODO(verify-api)` + 인터페이스 뒤로 숨긴다. 확인된 시그니처는 `docs/pleos-api-notes.md`.
- 외부 서비스(LLM, 화자 분리, 보이스 클로닝)는 인터페이스 + Fake 먼저. 데모는 Fake만으로 끝까지 돈다 (`BuildConfig.USE_FAKES`, `USE_FAKE_GATEWAYS`).
- 단계마다 빌드 + 시나리오(S1–S7) 테스트 통과 후 다음 단계. 큰 결정(라이브러리 추가, 구조 변경)은 먼저 묻는다.

## 기술 스택 / 제약
- Kotlin, Android API 28+, Gradle 8.9, AGP 8.5, Compose. 단일 모듈, 패키지로 분리 (MVVM + Repository, Coroutines/Flow, 수동 DI = `AppContainer`).
- Room. 오디오는 앱 내부 저장소 (암호화는 Phase 1, MVP는 TODO).
- 주행 중 UI 입력 금지. 주차(PARK) 상태에서만 화면 인터랙션, 주행 중은 음성만.
- Vehicle SDK는 `checkXXXCapabilities`로 지원 여부 확인 후 사용. 미지원이면 해당 기능만 비활성.
- 매니페스트에는 실제 쓰는 `pleos.car.permission.*`만. CONTROL 계열은 쓰지 않음 (`ENABLE_CONTROL_PERMISSIONS=false`).

## 기능 (요약)
- **F1 가족 탑승 모드**: OFF → ASKING → RECORDING → PAUSED → OFF. 전원 동의 시에만 녹음, 한 명이라도 거부하면 녹음 0. 상단 "● 기록 중", 음성 "기록 그만"으로 즉시 중지.
- **F2 녹음 + 화자 분리**: 30초 청크(타임스탬프·위치·주행 상태 태그) → STT(HYBRID) → `DiarizationProvider` → 등록된 `VoiceProfile`과 매칭(`personId`, 실패 시 unknown). 부모 목소리 등록(문장 3개).
- **F3 추억 요약**: 주행 종료(5분 정차 또는 앱 종료) 시 LLM(`highlight_extract.md`)으로 하이라이트 최대 5개 → 5~20초 클립. 플레이리스트(날짜/장소/사람 필터, 재생, 삭제, 장소 알림 끄기).
- **F4 장소 기반 재생**: 클립 좌표마다 proximity alert(반경 기본 150 m, 가까운 N개만). 진입 시 먼저 묻고 "응"이면 재생. 같은 장소 쿨다운 30일, 하루 최대 2회, 레슨 중/방향지시등 ON/급감속 후 10초는 금지.
- **F5 부모 목소리 운전 레슨**: 이벤트(방향지시등 없는 진로 변경, 급감속, 구간 진입) → 코칭 큐 → `VoiceCloneProvider.synthesize` → 실패 시 Pleos TTS 폴백. 클론은 목소리 주인이 승인한 경우에만, 철회 시 프로필·캐시 즉시 삭제.
- **F6 집·회사 + 시동 인사**: 주행 시작 판정 = 앱 시작 + 첫 위치 + 주차 해제. `TripContext`(MORNING_COMMUTE / EVENING_HOME / LATE_NIGHT_HOME / WEEKEND / OTHER)별 인사를 정차 상태에서, 하루 컨텍스트당 1회, "AI 목소리" 표시. "직접 남기기" 녹음이 클론보다 우선.

## 안전·개인정보 (코드로 강제)
- 동의 없는 녹음 경로 없음: `Recorder.start()`는 `ConsentManager.allConsented(tripId)`가 true일 때만, 청크마다 재확인.
- 클론 생성/합성은 `Person.voiceCloneApproved == true`일 때만 (`VoiceEnrollment.cloneAllowed`). 철회 시 `deleteProfile()` + 캐시 + 임베딩 삭제.
- 클론 음성 재생 시 "AI 목소리" 표시. 전화·메시지 발신에는 절대 사용하지 않음 (차량 스피커 재생 경로만 존재).
- 음성 우선순위: 레슨 큐 > 인사 > 추억 (`SpeechArbiter`).
- 원본 오디오 30일 후 자동 삭제(하이라이트 클립 유지), 설정에서 전체 삭제. 로그에 트랜스크립트 원문 금지.

## 마일스톤
M0 프로젝트/SDK/퍼미션 · M1 Gateway 4종 · M2 TripDetector/Engine/Classifier · M3 Family Mode · M4 하이라이트 + 플레이리스트 · M5 Proximity 재생 · M6 VoiceClone + 인사 + 레슨 · M7 데모/README/release APK.

## 시나리오 (자동화: `app/src/test/.../ScenarioTests.kt`)
S1 출근 인사 1회, 같은 날 재시동 시 없음 · S2 야근 인사 · S3 한 명 거부 → 녹음 파일 0개 · S4 칭찬2+다툼1+웃음1 → 클립 3개(다툼 제외) · S5 장소 재생(질문→"응"→재생, 같은 날 재진입/방향지시등 ON 시 없음) · S6 방향지시등 없는 진로 변경 → 코칭 1문장 · S7 승인 철회 → 기본 TTS 폴백 + 프로필 삭제.

## 빌드/테스트
```
export JAVA_HOME=<JDK 17> ANDROID_HOME=<sdk>
./gradlew :app:testDebugUnitTest      # S1–S7 + 로직 테스트
./gradlew :app:assembleDebug          # 가짜 게이트웨이(데모 콘솔) 포함
./gradlew :app:assembleRelease        # 실제 Pleos 게이트웨이 (-PfakeGateways=true 로 변경 가능)
```
