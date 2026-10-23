#!/usr/bin/env python3
"""Generates the demo conversations (macOS only: uses `say` + `ffmpeg`).

Outputs, next to this file:
  conversation1.wav / conversation1.labels.json   아빠 + 나: highway merge (praise, a joke)
  conversation2.wav / conversation2.labels.json   엄마 + 나: parking practice (a scolding, then praise)
  expected_highlights.json                         what the highlight step should keep (the scolding must be excluded)

Audio is 16 kHz mono PCM16, padded to 60 s so it divides evenly into 10 s / 30 s recording chunks.
"""
import json, subprocess, tempfile, wave, os

HERE = os.path.dirname(os.path.abspath(__file__))
# Only Yuna ships with real Korean speech on macOS, so speakers are told apart by pitch (ffmpeg) rather than by voice.
VOICE = "Yuna"
PITCH = {"아빠": 0.78, "엄마": 1.0, "나": 1.22}
RATE = 16000
TOTAL_MS = 60_000

CONVERSATIONS = {
    "conversation1": [  # (speaker, text, pause_after_ms)
        ("아빠", "자, 이제 고속도로로 합류해 보자. 천천히 가속해.", 900),
        ("나", "네, 알겠어요. 깜빡이 켰어요.", 800),
        ("아빠", "좋아, 차선 잘 보고 들어가.", 1200),
        ("나", "들어왔어요! 생각보다 쉬워요.", 900),
        ("아빠", "오, 정말 잘했어! 이제 혼자 가도 되겠다.", 600),
        ("나", "진짜요? 첫 고속도로인데 신기해요.", 3500),
        ("아빠", "다음 출구에서 빠지면 돼.", 900),
        ("나", "네, 알겠어요.", 3000),
        ("아빠", "하하, 아빠도 처음 운전할 땐 차선을 못 찾아서 웃기게 갔었어.", 900),
        ("나", "에이, 거짓말이죠?", 1000),
    ],
    "conversation2": [
        ("엄마", "주차 연습 해볼까? 후진 기어 넣고 천천히.", 900),
        ("나", "어, 잠깐만요. 벽이 너무 가까워요.", 900),
        ("엄마", "아니 그렇게 하면 어떡해! 왜 그랬어, 소리 지르게 하지 마.", 1000),
        ("나", "죄송해요. 다시 할게요.", 1000),
        ("엄마", "휴, 알았어. 핸들 반대로 돌려봐.", 900),
        ("나", "이렇게요? 됐다!", 3500),
        ("엄마", "와, 대단하다! 정말 잘했어, 한 번에 들어갔네.", 900),
        ("나", "고마워요.", 800),
    ],
}

EXPECTED = {  # emotion / speaker we expect the highlight step to pick; text must appear in the clip
    "conversation1": [("아빠", "proud", "잘했어"), ("아빠", "funny", "웃기게")],
    "conversation2": [("엄마", "proud", "대단하다")],
}
EXCLUDED = {"conversation2": ["소리 지르게"]}


def synth(speaker, text):
    f = PITCH[speaker]
    with tempfile.TemporaryDirectory() as d:
        aiff, wav = os.path.join(d, "a.aiff"), os.path.join(d, "a.wav")
        subprocess.run(["say", "-v", VOICE, "-o", aiff, text], check=True)
        # shift pitch by resampling, then restore tempo
        flt = f"aresample=44100,asetrate=44100*{f},atempo={1 / f:.4f},aresample={RATE}"
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", aiff, "-af", flt, "-ac", "1", "-ar", str(RATE), "-sample_fmt", "s16", wav], check=True)
        with wave.open(wav) as w:
            return w.readframes(w.getnframes())


def build(name, lines):
    pcm, labels, t = bytearray(), [], 0
    for spk, text, pause in lines:
        audio = synth(spk, text)
        ms = len(audio) // 2 * 1000 // RATE
        labels.append({"speaker": spk, "start_ms": t, "end_ms": t + ms, "text": text})
        pcm += audio + b"\x00\x00" * (RATE * pause // 1000)
        t += ms + pause
    assert t < TOTAL_MS, f"{name} is {t} ms, longer than {TOTAL_MS}"
    pcm += b"\x00\x00" * (RATE * (TOTAL_MS - t) // 1000)
    with wave.open(os.path.join(HERE, f"{name}.wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE); w.writeframes(bytes(pcm))
    with open(os.path.join(HERE, f"{name}.labels.json"), "w", encoding="utf-8") as f:
        json.dump(labels, f, ensure_ascii=False, indent=2)
    return labels


expected = []
for name, lines in CONVERSATIONS.items():
    labels = build(name, lines)
    for spk, emotion, snippet in EXPECTED.get(name, []):
        seg = next(s for s in labels if snippet in s["text"])
        expected.append({"conversation": name, "speaker": spk, "emotion": emotion, "start_ms": seg["start_ms"], "contains": snippet})
with open(os.path.join(HERE, "expected_highlights.json"), "w", encoding="utf-8") as f:
    json.dump({"highlights": expected, "must_exclude": [x for v in EXCLUDED.values() for x in v]}, f, ensure_ascii=False, indent=2)
print("ok")
