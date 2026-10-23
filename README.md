# hguide

가족이 함께 탄 차 안의 좋은 순간을 모아 주는 Pleos Connect(AAOS) 차량 앱 — 현대자동차그룹 차량용 앱 개발 공모전 MVP.

동의 하에 대화를 기록 → 부모 목소리의 좋은 순간만 5–20초 클립으로 저장 → 그 장소를 지나가면 "들어볼까요?" 하고 묻고 재생 → 같은 목소리로 출퇴근 인사와 운전 레슨.

## 상태
| | |
|---|---|
| 앱 코드 (M0–M6) | 완료 — Pleos SDK 실제 시그니처 기준 (Vehicle 2.3.2, FusedLocation 1.1.3, NaviHelper 3.0.5, TTS/STT 3.3.1) |
| 자동 테스트 | 35개 — S1–S7 시나리오 + 규칙/엔진/하이라이트 검증 |
| 에뮬레이터 | Android 34 에뮬레이터에서 설치·실행·S1 확인. **Pleos Emulator + Virtual Console 실기 검증은 미실시** |
| 실제 벤더 | LLM / 화자 분리 / 보이스 클로닝은 인터페이스 + Fake. 실제 벤더는 `VendorDefaults.kt` 자리에 연결 |

## 빌드 변형
| | USE_FAKES (LLM·화자분리·클론) | USE_FAKE_GATEWAYS (Vehicle·Location·Navi·STT·TTS) |
|---|---|---|
| debug | true | true — 앱 안 **데모 콘솔**이 차량을 대신함 |
| release | true | false — 실제 Pleos SDK |

`-PfakeGateways=true|false`로 덮어쓸 수 있다. Pleos 에뮬레이터에서 실제 게이트웨이를 쓰려면 release 변형(또는 `-PfakeGateways=false`)을 설치.

## 데모 영상
[docs/demo/hguide-demo.mp4](docs/demo/hguide-demo.mp4) — 에뮬레이터 자동 시연(약 40초): 목소리 선택 → 운전 시작 → AI 인사 → 가족 동의 → 코칭 → 추억 장소 재생 → 주차 후 추억.

자동 시연 다시 돌리기 (debug 빌드):
```
adb shell am start -n com.hguide.app/com.hguide.ui.MainActivity --ez autoplay true   # 또는 데모 탭 ▶ 자동 시연
```
에뮬레이터 데모 음성은 Typecast(`local.properties`에 `typecast.apiKey`, `typecast.voice.dad|mom|guide`)로 나오고, 키가 없으면 안드로이드 기본 TTS로 대신한다. 실제 차량 빌드는 Pleos TTS만 쓴다. 시드 추억 음성은 `sample/generate_demo_memories.py`로 만든다.

## 데모 (debug)
앱의 **데모** 탭에서 시간·위치·기어·방향지시등·주행을 조작한다. 예) S1: `평일 08:10` → `집` → `주차 해제 (D)` → 인사가 나오고 가족 동의 질문이 이어진다. 차가 물어보면 데모 탭의 `"응"/"아니"`로 대답.
샘플 대화(`sample/`)는 `python3 sample/generate_sample.py`(macOS)로 다시 만들 수 있다.

## 구조
`vehicle/ location/ navi/ speech/` Pleos Gateway · `engine/` TripDetector·DrivingEventEngine·TripContextClassifier·TripController · `family/` 동의·녹음·청크 처리 · `memory/` 하이라이트·클립·장소 재생 · `voice/` 등록·클론·인사·코치·우선순위 · `ai/` LlmProvider + `assets/prompts/*.md` · `data/` Room · `ui/` Compose · `demo/` 데모 콘솔.

자세한 규칙과 제약은 [CLAUDE.md](CLAUDE.md), Pleos API 메모는 [docs/pleos-api-notes.md](docs/pleos-api-notes.md).
