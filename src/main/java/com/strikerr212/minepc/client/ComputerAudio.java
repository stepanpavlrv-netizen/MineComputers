package com.strikerr212.minepc.client;

import com.strikerr212.minepc.MineClientConfig;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.computer.audio.Synth;
import com.strikerr212.minepc.computer.audio.Timeline;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;

import java.nio.ShortBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

/**
 * Звук из программ (пищалка и звуковой чип) у игрока. Для каждого звучащего компьютера — свой синтезатор
 * и свой источник OpenAL в мире (у корпуса): звук стихает с расстоянием и слышен с нужной стороны.
 *
 * Звук генерируется кусочками по 5 мс в отдельном потоке и сразу уходит в OpenAL; в очереди — около 30 мс,
 * поэтому выстрелы в игре звучат вовремя (пакет звука к тому же приходит раньше кадра экрана). Громкость — «Музыкальные блоки» × общая × настройка мода.
 */
public final class ComputerAudio {

    private static final int CHUNK = 256;                    // 5.8 мс
    private static final int BUFFERS = 5;                    // 29 мс в очереди OpenAL
    /** Тишина дольше этого — динамик убираем (и источник OpenAL освобождаем). */
    private static final long IDLE_NANOS = 8_000_000_000L;
    private static final int MAX_SPEAKERS = 16;

    private record Packet(long startMs, long nowMs, byte[] commands) {
    }

    private static final class Speaker {
        final BlockPos pos;
        com.strikerr212.minepc.computer.HostRef key;
        final Synth synth = new Synth();
        final Timeline timeline = new Timeline(synth);
        final ConcurrentLinkedQueue<Packet> inbox = new ConcurrentLinkedQueue<>();
        final short[] pcm = new short[CHUNK];
        final float[] wave = new float[CHUNK];
        final ShortBuffer buf = BufferUtils.createShortBuffer(CHUNK);
        int source;
        int[] buffers;
        long lastSound = System.nanoTime();
        boolean paused;

        Speaker(BlockPos pos) {
            this.pos = pos;
        }
    }

    private static final Map<com.strikerr212.minepc.computer.HostRef, Speaker> SPEAKERS = new ConcurrentHashMap<>();
    /** Для проверки в CI: сколько пакетов пришло и сколько кусочков звука отдано в OpenAL. */
    public static volatile long packets, chunks;
    /** Для проверки в CI: сумма и число задержек «пришёл пакет → зазвучал» (мс, без задержки самой звуковой карты). */
    public static volatile double latencySum;
    public static volatile long latencyCount;
    private static volatile Thread thread;
    private static long context;
    private static boolean warned;

    private ComputerAudio() {
    }

    /** Пакет с сервера (из любого потока). */
    public static void receive(com.strikerr212.minepc.computer.HostRef host, long startMs, long nowMs, byte[] commands) {
        if (MineClientConfig.programs() <= 0) return;
        Speaker s = SPEAKERS.get(host);
        if (s == null) {
            if (SPEAKERS.size() >= MAX_SPEAKERS) return;
            s = SPEAKERS.computeIfAbsent(host, h -> {
                Speaker sp = new Speaker(h.pos());
                sp.key = h;
                return sp;
            });
        }
        s.inbox.add(new Packet(startMs, nowMs, commands));
        packets++;
        start();
    }

    /** Вышли из мира — всё заглушить. */
    public static void clear() {
        for (Speaker s : SPEAKERS.values()) s.inbox.clear();
        SPEAKERS.clear();
        // источники удалит поток (он держит их список); если потока нет — и удалять нечего
    }

    private static synchronized void start() {
        if (thread != null && thread.isAlive()) return;
        Thread t = new Thread(ComputerAudio::loop, "MineComputers-Audio");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY - 1);
        thread = t;
        t.start();
    }

    // ======================= Поток звука =======================

    /** Динамики, у которых есть источник OpenAL (их знает только этот поток). */
    private static final Map<com.strikerr212.minepc.computer.HostRef, Speaker> LIVE = new java.util.HashMap<>();

    private static void loop() {
        while (true) {
            try {
                pump();
            } catch (Throwable e) {
                if (!warned) {
                    warned = true;
                    MineComputers.LOGGER.warn("Звук компьютеров: {}", e.toString());
                }
                dropAll(false);
            }
            if (LIVE.isEmpty() && SPEAKERS.isEmpty()) {
                // Нечего играть — поток засыпает до следующего пакета
                synchronized (ComputerAudio.class) {
                    if (SPEAKERS.isEmpty()) {
                        thread = null;
                        return;
                    }
                }
            }
            try {
                // sleep(1), а не park: на Windows так включается точный (1 мс) таймер
                Thread.sleep(1);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void pump() {
        long ctx = ALC10.alcGetCurrentContext();
        if (ctx == 0) {
            // Звук в игре выключен или перезапускается — ждём, а пакеты копятся недолго
            dropAll(false);
            for (Speaker s : SPEAKERS.values()) s.inbox.clear();
            return;
        }
        if (ctx != context) {
            // Звуковой движок перезапустили (сменили устройство) — старые источники недействительны
            dropAll(false);
            context = ctx;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean paused = mc.isPaused();
        float gain = MineClientConfig.programs() * mc.options.getSoundSourceVolume(SoundSource.MASTER)
                * mc.options.getSoundSourceVolume(SoundSource.RECORDS);
        // Удалённые (вышли из мира) динамики
        for (var it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (SPEAKERS.get(e.getKey()) != e.getValue()) {
                delete(e.getValue(), true);
                it.remove();
            }
        }
        long now = System.nanoTime();
        for (Speaker s : SPEAKERS.values()) {
            Packet p;
            while ((p = s.inbox.poll()) != null) {
                long start = s.timeline.receive(p.startMs, p.nowMs, p.commands);
                s.lastSound = now;
                if (s.source != 0 && (p.startMs < 0 || p.startMs <= p.nowMs)) {
                    // Сколько ещё не проиграно из очереди OpenAL: зазвучит через это время
                    int queued = AL10.alGetSourcei(s.source, AL10.AL_BUFFERS_QUEUED);
                    int offset = AL10.alGetSourcei(s.source, AL11.AL_SAMPLE_OFFSET);
                    long playing = s.synth.now() - Math.max(0, (long) queued * CHUNK - offset);
                    latencySum += (start - playing) * 1000.0 / Synth.RATE;
                    latencyCount++;
                }
            }
            if (s.source == 0) {
                if (!create(s)) continue;
                LIVE.put(s.key, s);
            }
            AL10.alSourcef(s.source, AL10.AL_GAIN, gain);
            if (paused) {
                if (!s.paused) {
                    AL10.alSourcePause(s.source);
                    s.paused = true;
                }
                continue;
            }
            int processed = AL10.alGetSourcei(s.source, AL10.AL_BUFFERS_PROCESSED);
            for (int i = 0; i < processed; i++) {
                int b = AL10.alSourceUnqueueBuffers(s.source);
                fill(s, b);
                AL10.alSourceQueueBuffers(s.source, b);
            }
            if (s.paused || AL10.alGetSourcei(s.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                // Не успели подать звук (или была пауза) — запустить снова
                AL10.alSourcePlay(s.source);
                s.paused = false;
            }
            if (s.synth.busy()) s.lastSound = now;
            else if (now - s.lastSound > IDLE_NANOS) SPEAKERS.remove(s.key, s);
        }
    }

    private static boolean create(Speaker s) {
        AL10.alGetError();
        int src = AL10.alGenSources();
        if (AL10.alGetError() != AL10.AL_NO_ERROR || src == 0) return false;
        int[] bufs = new int[BUFFERS];
        AL10.alGenBuffers(bufs);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            AL10.alDeleteSources(src);
            return false;
        }
        s.source = src;
        s.buffers = bufs;
        AL10.alSource3f(src, AL10.AL_POSITION, s.pos.getX() + 0.5f, s.pos.getY() + 0.5f, s.pos.getZ() + 0.5f);
        AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcei(src, AL10.AL_LOOPING, AL10.AL_FALSE);
        // Затухание с расстоянием — как у звуков Minecraft: линейно до 24 блоков
        AL10.alSourcei(src, AL10.AL_DISTANCE_MODEL, AL11.AL_LINEAR_DISTANCE);
        AL10.alSourcef(src, AL10.AL_MAX_DISTANCE, 24f);
        AL10.alSourcef(src, AL10.AL_ROLLOFF_FACTOR, 1f);
        AL10.alSourcef(src, AL10.AL_REFERENCE_DISTANCE, 0f);
        AL10.alGetError();
        for (int b : bufs) {
            fill(s, b);
            AL10.alSourceQueueBuffers(src, b);
        }
        AL10.alSourcePlay(src);
        return true;
    }

    private static void fill(Speaker s, int buffer) {
        s.synth.render(s.wave, CHUNK);
        for (int i = 0; i < CHUNK; i++) s.pcm[i] = (short) Math.round(Math.max(-1f, Math.min(1f, s.wave[i])) * 32767);
        s.buf.clear();
        s.buf.put(s.pcm).flip();
        AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, s.buf, Synth.RATE);
        chunks++;
    }

    private static void delete(Speaker s, boolean alive) {
        if (s.source != 0 && alive) {
            AL10.alSourceStop(s.source);
            AL10.alSourcei(s.source, AL10.AL_BUFFER, 0);
            AL10.alDeleteSources(s.source);
            AL10.alDeleteBuffers(s.buffers);
            AL10.alGetError();
        }
        s.source = 0;
        s.buffers = null;
    }

    /** Забыть все источники (alive=false — контекст уже уничтожен, удалять нечего). */
    private static void dropAll(boolean alive) {
        for (Speaker s : LIVE.values()) delete(s, alive);
        LIVE.clear();
        for (Speaker s : SPEAKERS.values()) {
            s.source = 0;
            s.buffers = null;
        }
    }
}
