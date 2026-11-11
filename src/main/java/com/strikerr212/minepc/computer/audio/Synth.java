package com.strikerr212.minepc.computer.audio;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.PriorityQueue;

/**
 * Синтезатор звукового чипа компьютера. Работает у игрока: сервер присылает не звук, а короткие команды
 * («канал 2: частота 440 Гц, открыть, подождать 200 мс, закрыть»), а звук собирается здесь.
 *
 * Каналы как у звуковой карты Computronics: волна (квадрат, синус, треугольник, пила, шум, сдвиговый регистр),
 * частота, громкость, огибающая ADSR, амплитудная и частотная модуляция другим каналом.
 * Канал {@link #BEEPER} — системная пищалка платы (computer.beep), программам он недоступен.
 */
public final class Synth {

    public static final int RATE = 44100;
    /** Каналов для программ (сколько из них доступно — зависит от платы). */
    public static final int MAX_CHANNELS = 8;
    public static final int BEEPER = MAX_CHANNELS;
    private static final int VOICES = MAX_CHANNELS + 1;

    // Формы волны (номера — как в Computronics)
    public static final int NOISE = -1, SQUARE = 1, SINE = 2, TRIANGLE = 3, SAWTOOTH = 4, LFSR = 5;

    // Команды
    public static final int OP_OPEN = 1, OP_CLOSE = 2, OP_WAVE = 3, OP_FREQ = 4, OP_VOLUME = 5, OP_ADSR = 6,
            OP_RESET_ENV = 7, OP_AM = 8, OP_FM = 9, OP_RESET_AM = 10, OP_RESET_FM = 11, OP_LFSR = 12,
            OP_DELAY = 13, OP_RESET_ALL = 14;

    /** Больше событий в очереди не держим (защита от переполнения). */
    private static final int MAX_EVENTS = 50_000;

    private record Event(long at, long seq, int op, int ch, float a, float b, float c, float d) {
    }

    private final PriorityQueue<Event> events = new PriorityQueue<>((x, y) -> x.at != y.at ? Long.compare(x.at, y.at) : Long.compare(x.seq, y.seq));
    private long seq;
    private final Voice[] voices = new Voice[VOICES];
    private final boolean[] modFlags = new boolean[VOICES];
    /** Сколько отсчётов уже выдано (время синтезатора). */
    private long now;
    /** Когда кончается последний запланированный звук (для «тишины» и удаления динамика). */
    private long lastEventAt;

    public Synth() {
        for (int i = 0; i < VOICES; i++) voices[i] = new Voice(i);
        Voice b = voices[BEEPER];
        b.wave = SQUARE;
        b.volume = 0.35f;
    }

    public long now() {
        return now;
    }

    /** Звучит ли что-нибудь или ждёт очереди. */
    public boolean busy() {
        if (!events.isEmpty() || now < lastEventAt) return true;
        for (Voice v : voices) if (v.level > 1e-4 || v.gate) return true;
        return false;
    }

    /**
     * Запланировать пакет команд с отсчёта {@code at} (если он в прошлом — с текущего).
     * Возвращает отсчёт, на котором пакет закончится.
     */
    public long schedule(long at, byte[] commands) {
        long t = Math.max(at, now);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(commands))) {
            while (in.available() > 0) {
                int op = in.readUnsignedByte();
                if (op == OP_DELAY) {
                    float ms = in.readFloat();
                    if (ms > 0 && ms < 60_000) t += Math.round(ms * RATE / 1000.0);
                    continue;
                }
                if (op == OP_RESET_ALL) {
                    add(t, op, 0, 0, 0, 0, 0);
                    continue;
                }
                int ch = in.readUnsignedByte();
                float a = 0, b = 0, c = 0, d = 0;
                switch (op) {
                    case OP_WAVE, OP_AM -> a = in.readByte();
                    case OP_FREQ, OP_VOLUME -> a = in.readFloat();
                    case OP_ADSR -> {
                        a = in.readFloat();
                        b = in.readFloat();
                        c = in.readFloat();
                        d = in.readFloat();
                    }
                    case OP_FM -> {
                        a = in.readByte();
                        b = in.readFloat();
                    }
                    case OP_LFSR -> {
                        a = Float.intBitsToFloat(in.readInt());
                        b = Float.intBitsToFloat(in.readInt());
                    }
                    case OP_OPEN, OP_CLOSE, OP_RESET_ENV, OP_RESET_AM, OP_RESET_FM -> {
                    }
                    default -> {
                        return t;          // неизвестная команда — дальше не разбираем
                    }
                }
                if (ch < VOICES) add(t, op, ch, a, b, c, d);
            }
        } catch (IOException e) {
            // обрезанный пакет: что успели разобрать — сыграем
        }
        lastEventAt = Math.max(lastEventAt, t);
        return t;
    }

    private void add(long at, int op, int ch, float a, float b, float c, float d) {
        if (events.size() >= MAX_EVENTS) return;
        events.add(new Event(at, seq++, op, ch, a, b, c, d));
    }

    /** Выдать следующие out.length отсчётов (-1..1). */
    public void render(float[] out, int count) {
        for (int i = 0; i < count; i++) {
            while (!events.isEmpty() && events.peek().at <= now) apply(events.poll());
            out[i] = sample();
            now++;
        }
    }

    /** То же в 16-битный звук. */
    public void render(short[] out, int count) {
        float[] tmp = new float[count];
        render(tmp, count);
        for (int i = 0; i < count; i++) out[i] = (short) Math.round(tmp[i] * 32767);
    }

    private void apply(Event e) {
        if (e.op == OP_RESET_ALL) {
            for (int i = 0; i < MAX_CHANNELS; i++) voices[i].reset();
            return;
        }
        Voice v = voices[e.ch];
        switch (e.op) {
            case OP_OPEN -> v.open();
            case OP_CLOSE -> v.close();
            case OP_WAVE -> v.wave = (int) e.a;
            case OP_FREQ -> v.freq = Math.max(0, Math.min(20_000, e.a));
            case OP_VOLUME -> v.volume = Math.max(0, Math.min(1, e.a));
            case OP_ADSR -> {
                v.envelope = true;
                v.attack = Math.max(0, e.a) / 1000f;
                v.decay = Math.max(0, e.b) / 1000f;
                v.sustain = Math.max(0, Math.min(1, e.c));
                v.release = Math.max(0, e.d) / 1000f;
            }
            case OP_RESET_ENV -> v.envelope = false;
            case OP_AM -> v.am = (int) e.a;
            case OP_FM -> {
                v.fm = (int) e.a;
                v.fmIntensity = e.b;
            }
            case OP_RESET_AM -> v.am = -1;
            case OP_RESET_FM -> v.fm = -1;
            case OP_LFSR -> {
                v.wave = LFSR;
                int init = Float.floatToRawIntBits(e.a), mask = Float.floatToRawIntBits(e.b);
                v.lfsr = init == 0 ? 1 : init;
                v.lfsrMask = mask == 0 ? 0x6000 : mask;
            }
            default -> {
            }
        }
    }

    private float sample() {
        // Сначала все каналы считают свою волну (модуляторы нужны другим), потом смешиваем
        boolean[] mod = modFlags;
        java.util.Arrays.fill(mod, false);
        for (Voice v : voices) {
            v.raw = v.oscillate(voices);
            if (v.am >= 0 && v.am < VOICES && v.am != v.index) mod[v.am] = true;
            if (v.fm >= 0 && v.fm < VOICES && v.fm != v.index) mod[v.fm] = true;
        }
        for (Voice v : voices) v.out = v.raw * v.gain();
        float sum = 0;
        for (int i = 0; i < VOICES; i++) {
            if (mod[i]) continue;              // канал-модулятор сам не звучит
            Voice v = voices[i];
            float o = v.out;
            // АМ: громкость канала качает другой канал
            if (v.am >= 0 && v.am < VOICES && v.am != i) o *= 1 + voices[v.am].out;
            sum += o;
        }
        float x = sum * 0.32f;
        // мягкое ограничение вместо хрипа при перегрузке
        float ax = Math.abs(x);
        if (ax > 0.7f) x = Math.signum(x) * (0.7f + 0.3f * (float) Math.tanh((ax - 0.7f) / 0.3f));
        return x;
    }

    // ======================= Канал =======================

    private static final class Voice {
        final int index;
        int wave = SQUARE;
        float freq = 440, volume = 1;
        boolean gate;
        boolean envelope;
        float attack, decay, sustain = 1, release;
        int stage;          // 0 — тишина, 1 — атака, 2 — спад, 3 — удержание, 4 — затухание
        float env;          // огибающая 0..1
        float level;        // сглаженная громкость (без щелчков)
        int am = -1, fm = -1;
        float fmIntensity;
        double phase;
        float raw, out;
        int lfsr = 1, lfsrMask = 0x6000;
        float noiseValue;
        long noiseState;

        Voice(int index) {
            this.index = index;
            noiseState = 0x9E3779B97F4A7C15L ^ index;
        }

        void reset() {
            wave = SQUARE;
            freq = 440;
            volume = 1;
            gate = false;
            envelope = false;
            sustain = 1;
            attack = decay = release = 0;
            stage = 0;
            env = 0;
            am = fm = -1;
            fmIntensity = 0;
        }

        void open() {
            gate = true;
            stage = envelope ? 1 : 3;
            if (!envelope) env = 1;
        }

        void close() {
            gate = false;
            stage = envelope ? 4 : 0;
            if (!envelope) env = 0;
        }

        float gain() {
            if (envelope) {
                float dt = 1f / RATE;
                switch (stage) {
                    case 1 -> {
                        env += attack <= 0 ? 1 : dt / attack;
                        if (env >= 1) {
                            env = 1;
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        env -= decay <= 0 ? 1 : dt / decay * (1 - sustain);
                        if (env <= sustain) {
                            env = sustain;
                            stage = 3;
                        }
                    }
                    case 4 -> {
                        env -= release <= 0 ? 1 : dt / release;
                        if (env <= 0) {
                            env = 0;
                            stage = 0;
                        }
                    }
                    default -> {
                    }
                }
            }
            float target = volume * env;
            // примерно 1.5 мс на смену громкости — чтобы не щёлкало
            level += (target - level) * 0.015f;
            return level;
        }

        float oscillate(Voice[] all) {
            if (level < 1e-5f && env == 0 && !gate) {
                return 0;
            }
            double f = freq;
            if (fm >= 0 && fm < all.length && fm != index) f += all[fm].out * fmIntensity;
            double inc = Math.abs(f) / RATE;
            if (inc > 0.5) inc = 0.5;
            double p = phase;
            phase += inc;
            boolean wrapped = false;
            if (phase >= 1) {
                phase -= Math.floor(phase);
                wrapped = true;
            }
            return switch (wave) {
                case SINE -> (float) Math.sin(2 * Math.PI * p);
                case TRIANGLE -> (float) (p < 0.5 ? 4 * p - 1 : 3 - 4 * p);
                case SAWTOOTH -> (float) (2 * p - 1 - polyBlep(p, inc));
                case NOISE -> {
                    if (wrapped || inc == 0) noiseValue = nextNoise();
                    yield noiseValue;
                }
                case LFSR -> {
                    if (wrapped) {
                        int bit = Integer.bitCount(lfsr & lfsrMask) & 1;
                        lfsr = ((lfsr << 1) | bit) & 0x7FFFFFFF;
                        if (lfsr == 0) lfsr = 1;
                    }
                    yield (lfsr & 1) != 0 ? 0.8f : -0.8f;
                }
                default -> {   // квадрат
                    double v = p < 0.5 ? 1 : -1;
                    v += polyBlep(p, inc);
                    v -= polyBlep((p + 0.5) % 1.0, inc);
                    yield (float) (v * 0.8);
                }
            };
        }

        private float nextNoise() {
            noiseState ^= noiseState << 13;
            noiseState ^= noiseState >>> 7;
            noiseState ^= noiseState << 17;
            return ((noiseState >>> 40) / (float) (1L << 24)) * 2 - 1;
        }

        /** Сглаживание скачка волны (меньше «цифрового» звона на высоких нотах). */
        private static double polyBlep(double t, double dt) {
            if (dt <= 0) return 0;
            if (t < dt) {
                t /= dt;
                return t + t - t * t - 1;
            }
            if (t > 1 - dt) {
                t = (t - 1) / dt;
                return t * t + t + t + 1;
            }
            return 0;
        }
    }
}
