#!/usr/bin/env python3
"""Generates the seeded demo playlist (assets/demo_memories/*.mp3) with Typecast voices.

Reads typecast.apiKey and typecast.voice.{dad,mom} from ../local.properties (git-ignored).
Needs ffmpeg on PATH or FFMPEG=/path/to/ffmpeg. Output is committed, so this only reruns when the lines change.
"""
import json, os, subprocess, tempfile, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "demo_memories")
FFMPEG = os.environ.get("FFMPEG", "ffmpeg")
ME_VOICE = "tc_68785db8ba9cd7503f27d921"  # Gowoon: the daughter
GAP_MS = 450

# file name -> [(speaker, line)]; titles/places live in DemoSeeder.kt
CLIPS = {
    "merge_practice": [("dad", "자, 이제 고속도로로 합류해 보자. 천천히 가속해."), ("me", "네, 깜빡이 켰어요.")],
    "parking_success": [("mom", "핸들 반대로 돌려봐."), ("me", "이렇게요? 됐다!"), ("mom", "와, 대단하다! 정말 잘했어, 한 번에 들어갔네.")],
    "dad_rookie_story": [("dad", "하하, 아빠도 처음 운전할 땐 차선을 못 찾아서 웃기게 갔었어."), ("me", "에이, 거짓말이죠?")],
    "first_drive": [("dad", "오, 정말 잘했어! 이제 혼자 가도 되겠다."), ("me", "진짜요? 첫 고속도로인데 신기해요.")],
}


def props():
    p = {}
    for line in open(os.path.join(HERE, "..", "local.properties"), encoding="utf-8"):
        if "=" in line and not line.startswith("#"):
            k, v = line.strip().split("=", 1)
            p[k] = v
    return p


def tts(key, voice, text, out, emotion="normal"):
    body = json.dumps({"voice_id": voice, "text": text, "model": "ssfm-v30", "language": "kor",
                       "prompt": {"emotion_preset": emotion, "emotion_intensity": 1}, "output": {"audio_format": "mp3"}}).encode()
    req = urllib.request.Request("https://api.typecast.ai/v1/text-to-speech", body, {"X-API-KEY": key, "Content-Type": "application/json"})
    with urllib.request.urlopen(req) as r, open(out, "wb") as f:
        f.write(r.read())


def main():
    p = props()
    voices = {"dad": p["typecast.voice.dad"], "mom": p["typecast.voice.mom"], "me": ME_VOICE}
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, lines in CLIPS.items():
            parts = []
            for i, (who, text) in enumerate(lines):
                f = os.path.join(tmp, f"{name}_{i}.mp3")
                tts(p["typecast.apiKey"], voices[who], text, f, "happy" if "!" in text else "normal")
                parts.append(f)
            inputs = sum((["-i", f] for f in parts), [])
            n = len(parts)
            chain = "".join(f"[{i}:a]aresample=44100,apad=pad_dur={GAP_MS / 1000}[a{i}];" for i in range(n))
            chain += "".join(f"[a{i}]" for i in range(n)) + f"concat=n={n}:v=0:a=1,loudnorm=I=-16:TP=-1.5[out]"
            dst = os.path.join(OUT, f"{name}.mp3")
            subprocess.run([FFMPEG, "-y", "-loglevel", "error", *inputs, "-filter_complex", chain, "-map", "[out]", "-b:a", "128k", dst], check=True)
            print("wrote", dst)


if __name__ == "__main__":
    main()
