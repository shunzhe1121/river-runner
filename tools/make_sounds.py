"""Synthesises the app's sound effects (no samples, so no licensing issues).

Run:  python3 tools/make_sounds.py   -> writes app/src/main/res/raw/sfx_*.wav
"""
import os
import wave
import numpy as np
from scipy.signal import butter, sosfilt

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
rng = np.random.default_rng(7)


def t(dur):
    return np.arange(int(SR * dur)) / SR


def band(x, lo, hi, order=4):
    sos = butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return sosfilt(sos, x)


def lowp(x, f, order=4):
    return sosfilt(butter(order, f, btype="low", fs=SR, output="sos"), x)


def highp(x, f, order=4):
    return sosfilt(butter(order, f, btype="high", fs=SR, output="sos"), x)


def place(buf, snd, at):
    i = int(at * SR)
    end = min(len(buf), i + len(snd))
    buf[i:end] += snd[: end - i]


def save(name, x, peak=0.8, fade=0.004):
    x = np.asarray(x, dtype=np.float64)
    n = int(SR * fade)
    if n and len(x) > 2 * n:
        x[:n] *= np.linspace(0, 1, n)
        x[-n:] *= np.linspace(1, 0, n)
    m = np.max(np.abs(x)) or 1.0
    x = x / m * peak
    data = (x * 32767).astype("<i2")
    with wave.open(os.path.join(OUT, f"sfx_{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())
    print(f"sfx_{name}.wav  {len(x) / SR * 1000:.0f} ms")


# ---------------------------------------------------------------- building blocks

def tail(x, frac=0.35):
    """Smoothly fades the last part of a tone so it never ends with a click."""
    n = int(len(x) * frac)
    x = x.copy()
    x[-n:] *= 0.5 * (1 + np.cos(np.linspace(0, np.pi, n)))
    return x


def clack(gain=1.0, pitch=1.0):
    """One clay chip hitting another: a few inharmonic modes + a tiny noise transient."""
    tt = t(0.07)
    modes = [(2650, 1.0, 0.018), (4100, 0.7, 0.012), (5750, 0.45, 0.009), (7300, 0.25, 0.006)]
    x = np.zeros_like(tt)
    for f, a, tau in modes:
        f *= pitch * rng.uniform(0.96, 1.04)
        x += a * np.sin(2 * np.pi * f * tt + rng.uniform(0, 6.28)) * np.exp(-tt / tau)
    nz = highp(rng.standard_normal(len(tt)), 3000) * np.exp(-tt / 0.0025) * 0.6
    return (x + nz) * gain


def chips(n, span, gain=1.0):
    buf = np.zeros(int(SR * (span + 0.1)))
    times = np.sort(rng.uniform(0, span, n))
    times[0] = 0
    for i, at in enumerate(times):
        place(buf, clack(gain * rng.uniform(0.55, 1.0) * (1 - 0.35 * i / max(1, n)), rng.uniform(0.9, 1.12)), at)
    return buf


def knock(gain=1.0):
    tt = t(0.12)
    x = (np.sin(2 * np.pi * 140 * tt) * 1.0 + np.sin(2 * np.pi * 310 * tt) * 0.6 + np.sin(2 * np.pi * 620 * tt) * 0.25) * np.exp(-tt / 0.035)
    nz = lowp(rng.standard_normal(len(tt)), 1800) * np.exp(-tt / 0.006) * 0.8
    return (x + nz) * gain


def bell(f, dur=0.9, gain=1.0):
    tt = t(dur)
    partials = [(1.0, 1.0, 0.45), (2.0, 0.35, 0.25), (2.76, 0.25, 0.18), (5.4, 0.08, 0.08)]
    x = sum(a * np.sin(2 * np.pi * f * k * tt) * np.exp(-tt / tau) for k, a, tau in partials)
    att = np.minimum(1, tt / 0.003)
    return tail(x * att * gain)


def marimba(f, dur=0.45, gain=1.0):
    tt = t(dur)
    x = np.sin(2 * np.pi * f * tt) * np.exp(-tt / 0.16) + 0.25 * np.sin(2 * np.pi * f * 4 * tt) * np.exp(-tt / 0.03)
    return tail(x * np.minimum(1, tt / 0.002) * gain)


# ---------------------------------------------------------------- sounds

def deal():
    tt = t(0.11)
    env = np.minimum(1, tt / 0.006) * np.exp(-tt / 0.03)
    swish = band(rng.standard_normal(len(tt)), 1800, 7500) * env
    snap = lowp(rng.standard_normal(len(tt)), 2500) * np.exp(-np.maximum(0, tt - 0.06) / 0.004) * (tt > 0.06) * 0.7
    return swish + snap


def flip():
    tt = t(0.09)
    snap = band(rng.standard_normal(len(tt)), 1500, 6000) * np.exp(-tt / 0.012)
    thump = np.sin(2 * np.pi * 170 * tt) * np.exp(-tt / 0.02) * 0.6
    return snap + thump


def fold():
    tt = t(0.24)
    env = np.minimum(1, tt / 0.04) * np.exp(-np.maximum(0, tt - 0.04) / 0.07)
    return band(rng.standard_normal(len(tt)), 400, 3200, order=2) * env


def check():
    buf = np.zeros(int(SR * 0.26))
    place(buf, knock(1.0), 0.0)
    place(buf, knock(0.8), 0.11)
    return buf


def turn():
    buf = np.zeros(int(SR * 0.6))
    place(buf, marimba(784), 0.0)        # G5
    place(buf, marimba(1175, gain=0.9), 0.11)  # D6
    return buf


def win():
    buf = np.zeros(int(SR * 1.4))
    for i, f in enumerate([1046.5, 1318.5, 1568.0, 2093.0]):   # C E G C
        place(buf, bell(f, 1.0, 0.55), i * 0.085)
    place(buf, chips(10, 0.45, 0.5), 0.05)
    return buf


def allin():
    tt = t(0.45)
    f = 90 * np.exp(-tt / 0.25) + 45
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt / 0.16)
    buf = np.zeros(int(SR * 0.6))
    place(buf, boom * 0.9, 0.0)
    place(buf, chips(22, 0.35, 0.8), 0.02)
    return buf


def blinds_up():
    buf = np.zeros(int(SR * 1.3))
    place(buf, bell(880, 1.0, 0.8), 0.0)
    place(buf, bell(1320, 1.0, 0.6), 0.22)
    return buf


def click():
    tt = t(0.03)
    return np.sin(2 * np.pi * 3200 * tt) * np.exp(-tt / 0.004) + highp(rng.standard_normal(len(tt)), 4000) * np.exp(-tt / 0.0015) * 0.4


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    save("deal", deal(), 0.55)
    save("flip", flip(), 0.6)
    save("chip", chips(3, 0.09), 0.7)
    save("collect", chips(9, 0.3, 0.9), 0.6)
    save("check", check(), 0.75)
    save("fold", fold(), 0.4)
    save("turn", turn(), 0.6)
    save("win", win(), 0.7)
    save("allin", allin(), 0.85)
    save("blinds", blinds_up(), 0.6)
    save("click", click(), 0.35)
