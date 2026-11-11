package com.strikerr212.minepc.computer;

import com.strikerr212.minepc.computer.audio.Audio;
import com.strikerr212.minepc.computer.audio.Synth;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.function.RegisteredFunction;

import static com.strikerr212.minepc.computer.LuaApis.error;
import static com.strikerr212.minepc.computer.LuaApis.str;
import static com.strikerr212.minepc.computer.LuaApis.text;
import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

/**
 * Lua: звуковой чип платы (таблица sound, как звуковая карта Computronics) и computer.beep.
 *
 * <pre>
 * sound.setWave(1, sound.modes.square)
 * sound.setFrequency(1, 440)
 * sound.open(1)  sound.delay(250)  sound.close(1)
 * sound.process()                    -- отправить очередь: звук играет у игроков рядом
 * sound.play(2, "C5", 0.2)            -- сразу сыграть ноту, не трогая очередь
 * </pre>
 */
final class SoundApis {

    private SoundApis() {
    }

    private static Audio audio(Computer c) {
        return c.audio();
    }

    private static int ch(Computer c, Varargs a, int i) throws LuaError {
        try {
            return audio(c).channel(a.arg(i).checkInteger());
        } catch (IllegalArgumentException e) {
            throw error(e.getMessage());
        }
    }

    private static Varargs run(Runnable r) throws LuaError {
        try {
            r.run();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw error(e.getMessage());
        }
        return Constants.NONE;
    }

    /** Частота: число в герцах или нота («A4», «C#5»). */
    static double frequency(LuaValue v) throws LuaError {
        if (v instanceof LuaString) {
            Double f = Audio.noteFrequency(text(v));
            if (f == null) throw error("Не понимаю ноту «" + text(v) + "» (пример: A4, C#5, Eb3)");
            return f;
        }
        double f = v.checkDouble();
        if (!(f >= 0 && f <= 20_000)) throw error("Частота — от 0 до 20000 Гц");
        return f;
    }

    static int wave(LuaValue v) throws LuaError {
        if (v instanceof LuaString) {
            return switch (text(v).toLowerCase(java.util.Locale.ROOT)) {
                case "square", "квадрат" -> Synth.SQUARE;
                case "sine", "синус" -> Synth.SINE;
                case "triangle", "треугольник" -> Synth.TRIANGLE;
                case "sawtooth", "saw", "пила" -> Synth.SAWTOOTH;
                case "noise", "шум" -> Synth.NOISE;
                default -> throw error("Нет такой волны: " + text(v) + " (square, sine, triangle, sawtooth, noise)");
            };
        }
        int w = v.checkInteger();
        if (w != Synth.NOISE && (w < Synth.SQUARE || w > Synth.SAWTOOTH)) throw error("Нет волны с номером " + w);
        return w;
    }

    private static float volume(Varargs a, int i) throws LuaError {
        double v = a.arg(i).checkDouble();
        return (float) Math.max(0, Math.min(1, v));
    }

    static LuaTable sound(Computer c) {
        LuaTable t = new LuaTable();
        LuaTable modes = new LuaTable();
        modes.rawset("noise", valueOf(Synth.NOISE));
        modes.rawset("square", valueOf(Synth.SQUARE));
        modes.rawset("sine", valueOf(Synth.SINE));
        modes.rawset("triangle", valueOf(Synth.TRIANGLE));
        modes.rawset("sawtooth", valueOf(Synth.SAWTOOTH));
        t.rawset("modes", modes);
        t.rawset("channel_count", valueOf(audio(c).channels()));
        RegisteredFunction.bind(t, new RegisteredFunction[]{
                RegisteredFunction.ofV("channels", (s, a) -> valueOf(audio(c).channels())),
                RegisteredFunction.ofV("setWave", (s, a) -> {
                    int ch = ch(c, a, 1), w = wave(a.arg(2));
                    return run(() -> audio(c).opByte(Synth.OP_WAVE, ch, w));
                }),
                RegisteredFunction.ofV("setFrequency", (s, a) -> {
                    int ch = ch(c, a, 1);
                    float f = (float) frequency(a.arg(2));
                    return run(() -> audio(c).opFloats(Synth.OP_FREQ, ch, f));
                }),
                RegisteredFunction.ofV("setVolume", (s, a) -> {
                    int ch = ch(c, a, 1);
                    float v = volume(a, 2);
                    return run(() -> audio(c).opFloats(Synth.OP_VOLUME, ch, v));
                }),
                RegisteredFunction.ofV("setADSR", (s, a) -> {
                    int ch = ch(c, a, 1);
                    float at = (float) a.arg(2).checkDouble(), de = (float) a.arg(3).checkDouble(),
                            su = (float) a.arg(4).checkDouble(), re = (float) a.arg(5).checkDouble();
                    return run(() -> audio(c).opFloats(Synth.OP_ADSR, ch, at, de, su, re));
                }),
                RegisteredFunction.ofV("resetEnvelope", (s, a) -> {
                    int ch = ch(c, a, 1);
                    return run(() -> audio(c).op(Synth.OP_RESET_ENV, ch));
                }),
                RegisteredFunction.ofV("setAM", (s, a) -> {
                    int ch = ch(c, a, 1), src = ch(c, a, 2);
                    return run(() -> audio(c).opByte(Synth.OP_AM, ch, src));
                }),
                RegisteredFunction.ofV("resetAM", (s, a) -> {
                    int ch = ch(c, a, 1);
                    return run(() -> audio(c).op(Synth.OP_RESET_AM, ch));
                }),
                RegisteredFunction.ofV("setFM", (s, a) -> {
                    int ch = ch(c, a, 1), src = ch(c, a, 2);
                    float k = (float) a.arg(3).checkDouble();
                    return run(() -> audio(c).fm(ch, src, k));
                }),
                RegisteredFunction.ofV("resetFM", (s, a) -> {
                    int ch = ch(c, a, 1);
                    return run(() -> audio(c).op(Synth.OP_RESET_FM, ch));
                }),
                RegisteredFunction.ofV("setLFSR", (s, a) -> {
                    int ch = ch(c, a, 1), init = a.arg(2).checkInteger(), mask = a.arg(3).checkInteger();
                    return run(() -> audio(c).lfsr(ch, init, mask));
                }),
                RegisteredFunction.ofV("open", (s, a) -> {
                    int ch = ch(c, a, 1);
                    return run(() -> audio(c).op(Synth.OP_OPEN, ch));
                }),
                RegisteredFunction.ofV("close", (s, a) -> {
                    int ch = ch(c, a, 1);
                    return run(() -> audio(c).op(Synth.OP_CLOSE, ch));
                }),
                RegisteredFunction.ofV("delay", (s, a) -> {
                    double ms = a.arg(1).checkDouble();
                    try {
                        if (!audio(c).delay(ms)) return varargsOf(Constants.FALSE, str("Очередь длиннее 5 секунд — вызовите sound.process()"));
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        throw error(e.getMessage());
                    }
                    return Constants.TRUE;
                }),
                RegisteredFunction.ofV("process", (s, a) -> {
                    String err = audio(c).process(c.audioNow(), c.audioOut());
                    return err == null ? Constants.TRUE : varargsOf(Constants.FALSE, str(err));
                }),
                RegisteredFunction.ofV("clear", (s, a) -> {
                    audio(c).clearQueue();
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("stop", (s, a) -> {
                    audio(c).stop(c.audioNow(), c.audioOut());
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("pending", (s, a) -> valueOf(audio(c).pending(c.audioNow()))),
                RegisteredFunction.ofV("queued", (s, a) -> valueOf(audio(c).queued() / 1000.0)),
                RegisteredFunction.ofV("note", (s, a) -> {
                    Double f = Audio.noteFrequency(text(a.arg(1).checkLuaString()));
                    return f == null ? Constants.NIL : valueOf(f);
                }),
                // Сыграть звук сразу, мимо очереди: play(канал, частота или нота, секунд [, громкость [, волна [, до частоты]]])
                RegisteredFunction.ofV("play", (s, a) -> {
                    int ch = ch(c, a, 1);
                    double f = frequency(a.arg(2));
                    double sec = a.arg(3).checkDouble();
                    if (!(sec > 0 && sec <= 10)) throw error("Длительность — от 0 до 10 секунд");
                    float vol = a.arg(4).isNil() ? 1f : volume(a, 4);
                    Integer w = a.arg(5).isNil() ? null : wave(a.arg(5));
                    Double to = a.arg(6).isNil() ? null : frequency(a.arg(6));
                    c.audioOut().send(c.audioNow(), c.audioNow(), playCommands(ch, f, sec, vol, w, to));
                    return Constants.TRUE;
                }),
        });
        return t;
    }

    /** Нота «сразу»: волна, громкость, частота (с плавным переходом, если задан), открыть, подождать, закрыть. */
    static byte[] playCommands(int ch, double freq, double sec, float vol, Integer wave, Double to) {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream o = new java.io.DataOutputStream(b);
        try {
            if (wave != null) {
                o.writeByte(Synth.OP_WAVE);
                o.writeByte(ch);
                o.writeByte(wave);
            }
            o.writeByte(Synth.OP_VOLUME);
            o.writeByte(ch);
            o.writeFloat(vol);
            o.writeByte(Synth.OP_FREQ);
            o.writeByte(ch);
            o.writeFloat((float) freq);
            o.writeByte(Synth.OP_OPEN);
            o.writeByte(ch);
            if (to == null) {
                o.writeByte(Synth.OP_DELAY);
                o.writeFloat((float) (sec * 1000));
            } else {
                // плавно к другой частоте: шагами по 5 мс
                int steps = Math.max(1, (int) Math.round(sec * 200));
                for (int i = 1; i <= steps; i++) {
                    o.writeByte(Synth.OP_DELAY);
                    o.writeFloat((float) (sec * 1000 / steps));
                    double k = i / (double) steps;
                    o.writeByte(Synth.OP_FREQ);
                    o.writeByte(ch);
                    o.writeFloat((float) (freq * Math.pow(Math.max(1e-3, to) / Math.max(1e-3, freq), k)));
                }
            }
            o.writeByte(Synth.OP_CLOSE);
            o.writeByte(ch);
        } catch (java.io.IOException ignored) {
        }
        return b.toByteArray();
    }

    /**
     * computer.beep: (частота, секунд) как в OpenComputers, или узор «.-» (точка — коротко, тире — длинно),
     * или нота. Не ждёт, пока прозвучит: писки встают в очередь пищалки.
     */
    static Varargs beep(Computer c, Varargs a) throws LuaError {
        double[] freqs, secs;
        double gap = 0;
        LuaValue first = a.arg(1);
        if (first instanceof LuaString ls && (text(ls).indexOf('.') >= 0 || text(ls).indexOf('-') >= 0)) {
            double[][] p = Audio.pattern(text(ls), a.arg(2).isNil() ? 1000 : frequency(a.arg(2)));
            freqs = p[0];
            secs = p[1];
            gap = 0.1;
            if (freqs.length == 0) return Constants.FALSE;
            if (freqs.length > 64) throw error("Слишком длинный узор");
        } else {
            double f = first.isNil() ? 1000 : frequency(first);
            double sec = a.arg(2).isNil() ? 0.1 : a.arg(2).checkDouble();
            if (!(sec > 0 && sec <= 5)) throw error("Длительность — от 0 до 5 секунд");
            freqs = new double[]{Math.max(20, Math.min(5000, f))};
            secs = new double[]{sec};
        }
        return valueOf(c.audio().beep(c.audioNow(), c.audioOut(), freqs, secs, gap));
    }
}
