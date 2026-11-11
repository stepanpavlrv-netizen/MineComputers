"""Собрать звуки мода: часть синтезируется, часть — из бесплатных записей (CC0 / MIT / Unlicense, см. CREDITS.md).

Запуск: python3 tools/sounds/make_sounds.py [папка_для_wav]
Результат — .ogg в src/main/resources/assets/minepc/sounds/ (моно, 44.1 кГц). Нужны numpy, scipy и ffmpeg.
Случайность с фиксированным зерном: при каждом запуске получаются одни и те же звуки.
"""
import os
import subprocess
import sys

import numpy as np
from scipy import signal

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "src")
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "minepc", "sounds")
rng = np.random.default_rng(212)


# ---------------------------------------------------------------- помощники

def load(name):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", os.path.join(SRC, name), "-ac", "1", "-ar", str(SR),
                          "-f", "f32le", "-"], capture_output=True, check=True).stdout
    x = np.frombuffer(raw, np.float32).astype(np.float64)
    # обрезать тишину по краям
    e = np.abs(x)
    idx = np.where(e > e.max() * 0.02)[0]
    return x[max(0, idx[0] - 20): idx[-1] + 200] if len(idx) else x


def t(sec):
    return np.arange(int(sec * SR)) / SR


def silence(sec):
    return np.zeros(int(sec * SR))


def noise(sec):
    return rng.standard_normal(int(sec * SR))


def band(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, hi], btype="bandpass", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def low(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, btype="lowpass", fs=SR, output="sos"), x)


def high(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, btype="highpass", fs=SR, output="sos"), x)


def decay(sec, tau):
    return np.exp(-t(sec) / tau)


def mix(*parts):
    """parts: (сдвиг в секундах, звук, громкость)."""
    n = max(int(off * SR) + len(x) for off, x, _ in parts)
    out = np.zeros(n)
    for off, x, g in parts:
        i = int(off * SR)
        out[i:i + len(x)] += x * g
    return out


def fade(x, fin=0.002, fout=0.01):
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a: x[:a] *= np.linspace(0, 1, a)
    if b: x[-b:] *= np.linspace(1, 0, b)
    return x


def norm(x, peak=0.9):
    m = np.abs(x).max()
    return x * (peak / m) if m > 0 else x


def resonate(x, freqs, taus, gains):
    """Пропустить удар через несколько резонансов (корпус, пластик, металл)."""
    out = np.zeros(len(x) + int(max(taus) * 5 * SR))
    xx = np.concatenate([x, np.zeros(len(out) - len(x))])
    for f, tau, g in zip(freqs, taus, gains):
        r = np.exp(-1 / (tau * SR))
        w = 2 * np.pi * f / SR
        b = [1 - r]
        a = [1, -2 * r * np.cos(w), r * r]
        out += signal.lfilter(b, a, xx) * g
    return out


def click(sec=0.002, color=(1500, 9000)):
    return band(noise(sec) * decay(sec, sec / 3), *color)


def periodic_noise(n, shape):
    """Шум, который ровно повторяется через n отсчётов (для бесшовных петель). shape(freqs) — спектр."""
    freqs = np.fft.rfftfreq(n, 1 / SR)
    mag = shape(freqs)
    ph = rng.uniform(0, 2 * np.pi, len(freqs))
    spec = mag * np.exp(1j * ph)
    spec[0] = 0
    return np.fft.irfft(spec, n)


SOUNDS = {}


def save(name, x, peak=0.9, loop=False):
    if not loop:
        e = np.abs(x)
        idx = np.where(e > e.max() * 0.003)[0]
        x = fade(x[: idx[-1] + int(0.005 * SR)], 0, 0.005)
    SOUNDS[name] = norm(x, peak)


# ---------------------------------------------------------------- корпус

def fan_loop():
    sec = 3.0
    n = int(sec * SR)
    tt = np.arange(n) / SR
    # поток воздуха: шум с горбом 250..900 Гц и мягким спадом
    air = periodic_noise(n, lambda f: 1 / (1 + (f / 900) ** 2) * (0.4 + np.exp(-((f - 450) / 300) ** 2)) * (f > 40))
    air = air / np.abs(air).max()
    # лопасти: 2000 об/мин × 7 лопастей ≈ 233 Гц; частоты кратны 1/3 Гц — петля бесшовная
    bp = 233.0 + 1 / 3
    blade = (np.sin(2 * np.pi * bp * tt) * 0.30 + np.sin(2 * np.pi * 2 * bp * tt + 1) * 0.12
             + np.sin(2 * np.pi * 3 * bp * tt + 2) * 0.05)
    blade *= 1 + 0.15 * np.sin(2 * np.pi * (1 / 3) * tt)       # лёгкое «плавание»
    motor = np.sin(2 * np.pi * 66.0 * tt) * 0.08 + np.sin(2 * np.pi * 1600.0 * tt) * 0.012
    save("fan", air * 0.55 + blade * 0.35 + motor, 0.6, loop=True)


def hdd_spin_loop():
    sec = 2.0
    n = int(sec * SR)
    tt = np.arange(n) / SR
    f0 = 120.0                       # 7200 об/мин
    whine = sum(np.sin(2 * np.pi * f0 * k * tt + k) * g for k, g in [(1, 0.25), (2, 0.1), (3, 0.05), (36, 0.012), (72, 0.005)])
    hiss = periodic_noise(n, lambda f: np.exp(-((f - 3000) / 2500) ** 2) * (f > 200))
    hiss = hiss / np.abs(hiss).max()
    save("hdd_spin", whine + hiss * 0.18, 0.5, loop=True)


def hdd_spinup():
    sec = 2.4
    tt = t(sec)
    k = np.clip(tt / 2.0, 0, 1)
    speed = 1 - (1 - k) ** 2.2
    f = 4 + 116 * speed
    ph = 2 * np.pi * np.cumsum(f) / SR
    whine = (np.sin(ph) * 0.4 + np.sin(2 * ph) * 0.2 + np.sin(36 * ph) * 0.05 * speed)
    hiss = band(noise(sec), 1500, 6000) * 0.25 * speed
    env = np.minimum(1, tt / 0.15) * (0.4 + 0.6 * speed)
    x = (whine + hiss) * env
    # в конце — головки выходят на диск: два щелчка
    tick = resonate(click(0.0015), [2400, 4100, 6300], [0.006, 0.004, 0.003], [1, 0.6, 0.4])
    x = mix((0, x, 1), (2.05, tick, 0.8), (2.11, tick, 0.5))
    save("hdd_spinup", fade(x, 0.01, 0.15), 0.7)


def hdd_tick():
    return mix((0, resonate(click(0.0012, (2000, 12000)), [1900, 3400, 5200, 7800], [0.008, 0.006, 0.004, 0.003],
                            [0.6, 1, 0.7, 0.4]), 1), (0, resonate(click(0.002, (100, 800)), [420], [0.01], [0.6]), 1))


def hdd_seek(i):
    parts = []
    pos = 0.0
    pattern = [[0.0, 0.03], [0.0, 0.018, 0.034, 0.08], [0.0, 0.012, 0.024, 0.036, 0.048, 0.06], [0.0, 0.05, 0.09]][i]
    for p in pattern:
        parts.append((p + rng.uniform(0, 0.004), hdd_tick(), rng.uniform(0.5, 1.0)))
    save(f"hdd_seek{i + 1}", fade(mix(*parts), 0.0005, 0.01), 0.8)


def floppy():
    step = load("floppy_head_step.wav")[: int(0.022 * SR)]
    step = fade(step, 0.0005, 0.006)

    def motor(sec):
        tt = t(sec)
        hum = np.sin(2 * np.pi * 300 * tt) * 0.06 + np.sin(2 * np.pi * 5 * tt) * 0.02
        whirr = band(noise(sec), 200, 1200) * 0.08
        return fade((hum + whirr) * np.minimum(1, tt / 0.05), 0.01, 0.05)

    # 1: короткое чтение — мотор и несколько шагов
    parts = [(0, motor(0.45), 1)] + [(0.05 + 0.06 * k, step, 0.9) for k in range(3)]
    save("floppy_seek1", mix(*parts), 0.8)
    # 2: «грызёт» дорожки — быстрые шаги, звучат как жужжание
    parts = [(0, motor(0.6), 1)] + [(0.05 + k / 140, step, 0.8 + 0.2 * np.sin(k)) for k in range(55)]
    save("floppy_seek2", mix(*parts), 0.8)
    # 3: туда и обратно
    parts = [(0, motor(0.7), 1)] + [(0.04 + k / 120, step, 0.8) for k in range(25)] \
        + [(0.32 + k / 90, step, 0.7) for k in range(18)]
    save("floppy_seek3", mix(*parts), 0.8)


def disk_insert_eject():
    latch = load("kenney_ui_switch16.wav")
    clack = load("kenney_ui_switch3.wav")
    slide_in = band(noise(0.16), 1200, 5000) * np.linspace(0.2, 1, int(0.16 * SR)) * 0.25
    thud = resonate(click(0.004, (60, 600)), [140, 260], [0.03, 0.02], [1, 0.5])
    save("disk_insert", fade(mix((0, slide_in, 1), (0.15, latch, 0.9), (0.155, thud, 0.6))))
    slide_out = band(noise(0.14), 1200, 5000) * np.linspace(1, 0.1, int(0.14 * SR)) * 0.25
    spring = resonate(click(0.002), [1800, 3100], [0.03, 0.02], [0.4, 0.3])
    save("disk_eject", fade(mix((0, clack, 1), (0.0, spring, 0.5), (0.03, slide_out, 1))))


def keys():
    for i in range(1, 6):
        down = load(f"kailh_down{i}.wav")
        up = load(f"kailh_up{i}.wav")
        f = rng.uniform(170, 260)
        body = resonate(click(0.003, (80, 1500)), [f, f * 2.7, 1800 + rng.uniform(-200, 200)],
                        [0.012, 0.006, 0.004], [1, 0.4, 0.3])
        bottom = band(noise(0.006) * decay(0.006, 0.0015), 800, 4000)
        x = mix((0, down, 1), (0.002, body, 0.35), (0.004, bottom, 0.15),
                (rng.uniform(0.07, 0.095), up, 0.7))
        save(f"key{i}", fade(x, 0.0005, 0.01), 0.8)


def power_button():
    a = load("kenney_ui_switch22.wav")
    b = load("kenney_ui_switch23.wav")
    thump = resonate(click(0.004, (60, 800)), [180], [0.02], [1])
    save("power_on", mix((0, a, 1), (0.001, thump, 0.4)), 0.85)
    save("power_off", mix((0, b, 1), (0.001, thump, 0.4)), 0.85)


def parts_sounds():
    latch = load("kenney_ui_switch18.wav")
    latch2 = load("kenney_ui_switch25.wav")
    metal = resonate(click(0.002), [1730, 2890, 4120, 5630, 7310], [0.09, 0.07, 0.05, 0.04, 0.03], [0.5, 0.4, 0.3, 0.25, 0.15])
    slide = band(noise(0.12), 900, 4500) * np.linspace(0.3, 1, int(0.12 * SR)) * 0.15
    save("part_install", fade(mix((0, slide, 1), (0.11, latch, 1), (0.112, metal, 0.35), (0.2, latch2, 0.5))))
    slide2 = band(noise(0.12), 900, 4500) * np.linspace(1, 0.2, int(0.12 * SR)) * 0.15
    save("part_remove", fade(mix((0, latch2, 0.8), (0.02, metal, 0.2), (0.05, slide2, 1))))


def lan_sounds():
    snap = load("kenney_ui_click3.wav")
    tick = load("kenney_ui_mouseclick1.wav")
    plastic = resonate(click(0.0015), [2600, 4400], [0.01, 0.006], [0.6, 0.4])
    save("plug_in", fade(mix((0, band(noise(0.04), 2000, 6000) * 0.1, 1), (0.035, snap, 1), (0.036, plastic, 0.5))))
    save("plug_out", fade(mix((0, tick, 1), (0.03, band(noise(0.05), 2000, 6000) * np.linspace(1, 0, int(0.05 * SR)) * 0.12, 1))))
    # реле в коммутаторе — едва слышный двойной щелчок
    r = resonate(click(0.001), [3200, 5100], [0.003, 0.002], [1, 0.6])
    save("relay1", mix((0, r, 1), (0.012, r, 0.5)), 0.5)
    save("relay2", mix((0, r, 0.7), (0.009, r, 1)), 0.5)


def radio():
    for i in range(1, 4):
        sec = rng.uniform(0.45, 0.8)
        tt = t(sec)
        hiss = band(noise(sec), 300, 3200) * 0.25
        crackle = np.zeros(len(tt))
        for _ in range(int(sec * 60)):
            p = rng.integers(0, len(tt) - 200)
            crackle[p:p + 60] += rng.standard_normal(60) * np.exp(-np.arange(60) / (0.0004 * SR)) * rng.uniform(0.3, 1.2)
        crackle = band(crackle, 400, 5000)
        # данные: тональная манипуляция 1200/2200 Гц
        sym = 1 / 300
        bits = rng.integers(0, 2, int(sec / sym) + 1)
        fr = np.where(bits[(tt / sym).astype(int)] == 1, 1200, 2200)
        data = np.sin(2 * np.pi * np.cumsum(fr) / SR) * 0.18
        dstart, dend = 0.12, sec - 0.12
        data *= ((tt > dstart) & (tt < dend)) * 1.0
        x = (hiss + crackle * 0.6 + data) * (1 + 0.3 * np.sin(2 * np.pi * 7 * tt))
        save(f"radio{i}", fade(x, 0.02, 0.08), 0.7)


def main():
    fan_loop()
    hdd_spin_loop()
    hdd_spinup()
    for i in range(4):
        hdd_seek(i)
    floppy()
    disk_insert_eject()
    keys()
    power_button()
    parts_sounds()
    lan_sounds()
    radio()
    os.makedirs(OUT, exist_ok=True)
    wav_dir = sys.argv[1] if len(sys.argv) > 1 else None
    if wav_dir:
        os.makedirs(wav_dir, exist_ok=True)
    for name, x in SOUNDS.items():
        pcm = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
        dst = os.path.join(OUT, name + ".ogg")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "-",
                        "-c:a", "libvorbis", "-q:a", "5", "-map_metadata", "-1", dst], input=pcm, check=True)
        if wav_dir:
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "-",
                            os.path.join(wav_dir, name + ".wav")], input=pcm, check=True)
        print(f"{name:14s} {len(x) / SR:5.2f} с")


if __name__ == "__main__":
    main()
