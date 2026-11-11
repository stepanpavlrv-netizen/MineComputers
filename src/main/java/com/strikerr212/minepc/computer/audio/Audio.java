package com.strikerr212.minepc.computer.audio;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Звук компьютера на стороне сервера: системная пищалка (computer.beep) и звуковой чип платы (sound).
 * Программа складывает команды в очередь (sound.setFrequency, sound.open, sound.delay...),
 * а sound.process() отправляет их игрокам рядом одним пакетом. Сам звук собирает {@link Synth} у игрока.
 *
 * Время — по часам компьютера (миллисекунды): пакеты встают друг за другом без щелей,
 * даже если программа отправляет их заранее.
 */
public final class Audio {

    /** Как отправить пакет игрокам: начало (мс по часам компьютера, -1 — сразу), «сейчас» и команды. */
    public interface Output {
        void send(long startMs, long nowMs, byte[] commands);
    }

    /** Длиннее пакет не бывает (сумма задержек). */
    public static final double MAX_BATCH_MS = 5000;
    /** Насколько вперёд можно поставить звук в очередь. */
    public static final double MAX_AHEAD_MS = 6000;
    public static final int MAX_COMMANDS = 4096;

    private final int channels;
    private ByteArrayOutputStream queue = new ByteArrayOutputStream();
    private DataOutputStream out = new DataOutputStream(queue);
    private int commands;
    private double queuedMs;
    /** Когда (по часам компьютера) кончится уже отправленный звук чипа и пищалки. */
    private double soundEnd, beepEnd;

    public Audio(int channels) {
        this.channels = Math.max(0, Math.min(Synth.MAX_CHANNELS, channels));
    }

    public int channels() {
        return channels;
    }

    public synchronized void reset() {
        clearQueue();
        soundEnd = beepEnd = 0;
    }

    // ======================= Очередь звукового чипа =======================

    /** Номер канала из Lua (с 1) → внутренний (с 0); бросает ошибку, если такого нет. */
    public int channel(int luaChannel) {
        if (luaChannel < 1 || luaChannel > channels) {
            throw new IllegalArgumentException(channels == 0 ? "На плате нет звукового чипа"
                    : "Нет канала " + luaChannel + " (каналы 1.." + channels + ")");
        }
        return luaChannel - 1;
    }

    private void check() {
        if (commands >= MAX_COMMANDS) throw new IllegalStateException("Слишком много команд — вызовите sound.process()");
        commands++;
    }

    public synchronized void op(int op, int ch) {
        check();
        try {
            out.writeByte(op);
            out.writeByte(ch);
        } catch (IOException ignored) {
        }
    }

    public synchronized void opByte(int op, int ch, int value) {
        check();
        try {
            out.writeByte(op);
            out.writeByte(ch);
            out.writeByte(value);
        } catch (IOException ignored) {
        }
    }

    public synchronized void opFloats(int op, int ch, float... values) {
        check();
        try {
            out.writeByte(op);
            out.writeByte(ch);
            for (float v : values) out.writeFloat(v);
        } catch (IOException ignored) {
        }
    }

    public synchronized void fm(int ch, int source, float intensity) {
        check();
        try {
            out.writeByte(Synth.OP_FM);
            out.writeByte(ch);
            out.writeByte(source);
            out.writeFloat(intensity);
        } catch (IOException ignored) {
        }
    }

    public synchronized void lfsr(int ch, int initial, int mask) {
        check();
        try {
            out.writeByte(Synth.OP_LFSR);
            out.writeByte(ch);
            out.writeInt(initial);
            out.writeInt(mask);
        } catch (IOException ignored) {
        }
    }

    /** Пауза в очереди. false — пакет стал бы длиннее 5 секунд. */
    public synchronized boolean delay(double ms) {
        if (ms < 0 || Double.isNaN(ms)) throw new IllegalArgumentException("Задержка должна быть ≥ 0");
        if (queuedMs + ms > MAX_BATCH_MS) return false;
        check();
        queuedMs += ms;
        try {
            out.writeByte(Synth.OP_DELAY);
            out.writeFloat((float) ms);
        } catch (IOException ignored) {
        }
        return true;
    }

    /** Отправить очередь. null — получилось, иначе — почему нет (очередь не трогаем). */
    public synchronized String process(double nowMs, Output output) {
        if (soundEnd - nowMs > MAX_AHEAD_MS - queuedMs) return "Звук ещё играет — подождите";
        double start = Math.max(nowMs, soundEnd);
        byte[] data = queue.toByteArray();
        soundEnd = start + queuedMs;
        clearQueue();
        if (data.length > 0) output.send(Math.round(start), Math.round(nowMs), data);
        return null;
    }

    /** Сколько секунд ещё будет звучать уже отправленное. */
    public synchronized double pending(double nowMs) {
        return Math.max(0, soundEnd - nowMs) / 1000.0;
    }

    /** Сколько миллисекунд набрано в очереди (ещё не отправлено). */
    public synchronized double queued() {
        return queuedMs;
    }

    /** Очистить очередь и заглушить все каналы. */
    public synchronized void stop(double nowMs, Output output) {
        clearQueue();
        soundEnd = nowMs;
        output.send(-1, Math.round(nowMs), new byte[]{(byte) Synth.OP_RESET_ALL});
    }

    public synchronized void clearQueue() {
        queue = new ByteArrayOutputStream();
        out = new DataOutputStream(queue);
        commands = 0;
        queuedMs = 0;
    }

    // ======================= Пищалка =======================

    /**
     * Пискнуть: тоны (частота Гц, длительность с) один за другим, между ними — паузы gap с.
     * Возвращает false, если пищалка и так занята на 5 секунд вперёд.
     */
    public synchronized boolean beep(double nowMs, Output output, double[] freqs, double[] seconds, double gap) {
        if (beepEnd - nowMs > MAX_BATCH_MS) return false;
        double start = Math.max(nowMs, beepEnd);
        byte[] data = beepCommands(freqs, seconds, gap);
        double total = 0;
        for (int i = 0; i < seconds.length; i++) total += seconds[i] * 1000 + (i + 1 < seconds.length ? gap * 1000 : 0);
        beepEnd = start + total + 30;
        output.send(nowMs < 0 ? -1 : Math.round(start), Math.round(Math.max(0, nowMs)), data);
        return true;
    }

    /** Команды для пищалки (их же использует корпус для писков BIOS, когда компьютер ещё не работает). */
    public static byte[] beepCommands(double[] freqs, double[] seconds, double gap) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(b);
        try {
            o.writeByte(Synth.OP_RESET_ENV);
            o.writeByte(Synth.BEEPER);
            for (int i = 0; i < freqs.length; i++) {
                o.writeByte(Synth.OP_FREQ);
                o.writeByte(Synth.BEEPER);
                o.writeFloat((float) Math.max(20, Math.min(5000, freqs[i])));
                o.writeByte(Synth.OP_OPEN);
                o.writeByte(Synth.BEEPER);
                o.writeByte(Synth.OP_DELAY);
                o.writeFloat((float) (seconds[i] * 1000));
                o.writeByte(Synth.OP_CLOSE);
                o.writeByte(Synth.BEEPER);
                if (i + 1 < freqs.length && gap > 0) {
                    o.writeByte(Synth.OP_DELAY);
                    o.writeFloat((float) (gap * 1000));
                }
            }
        } catch (IOException ignored) {
        }
        return b.toByteArray();
    }

    /** Разобрать узор OpenComputers: «.» — короткий писк, «-» — длинный. */
    public static double[][] pattern(String pattern, double freq) {
        int n = 0;
        for (char ch : pattern.toCharArray()) if (ch == '.' || ch == '-') n++;
        double[] f = new double[n], s = new double[n];
        int i = 0;
        for (char ch : pattern.toCharArray()) {
            if (ch != '.' && ch != '-') continue;
            f[i] = freq;
            s[i] = ch == '.' ? 0.1 : 0.4;
            i++;
        }
        return new double[][]{f, s};
    }

    /** Частота ноты: «A4» = 440, «C#5», «Eb3»; null — не нота. */
    public static Double noteFrequency(String note) {
        if (note == null || note.isEmpty()) return null;
        String s = note.trim();
        int idx = "CDEFGAB".indexOf(Character.toUpperCase(s.charAt(0)));
        if (idx < 0) return null;
        int semis = new int[]{0, 2, 4, 5, 7, 9, 11}[idx];
        int i = 1;
        while (i < s.length() && (s.charAt(i) == '#' || s.charAt(i) == 'b' || s.charAt(i) == '♯' || s.charAt(i) == '♭')) {
            semis += s.charAt(i) == '#' || s.charAt(i) == '♯' ? 1 : -1;
            i++;
        }
        int octave = 4;
        if (i < s.length()) {
            try {
                octave = Integer.parseInt(s.substring(i));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        int midi = (octave + 1) * 12 + semis;
        return 440.0 * Math.pow(2, (midi - 69) / 12.0);
    }
}
