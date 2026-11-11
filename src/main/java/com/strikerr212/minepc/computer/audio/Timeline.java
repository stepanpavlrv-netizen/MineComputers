package com.strikerr212.minepc.computer.audio;

/**
 * Привязка времени сервера (мс по часам компьютера) ко времени синтезатора у игрока (отсчёты).
 * Пакеты приходят по сети неровно, поэтому звук играет с небольшим запасом {@link #LEAD_MS}:
 * пакеты, отправленные заранее, встают точно друг за другом, а опоздавшие — сдвигают привязку.
 */
public final class Timeline {

    public static final int LEAD_MS = 40;
    private static final long LEAD = (long) Synth.RATE * LEAD_MS / 1000;
    /** Насколько можно отстать от идеала, прежде чем догонять. */
    private static final long SLACK = (long) Synth.RATE * 150 / 1000;

    private final Synth synth;
    private boolean anchored;
    private long anchor;
    private long lastServerNow = Long.MIN_VALUE;

    public Timeline(Synth synth) {
        this.synth = synth;
    }

    public Synth synth() {
        return synth;
    }

    private static long samples(long ms) {
        return ms * Synth.RATE / 1000;
    }

    /** Пакет с сервера. startMs = -1 — играть сразу. */
    public void receive(long startMs, long nowMs, byte[] commands) {
        long here = synth.now();
        if (startMs < 0) {
            synth.schedule(here + LEAD / 2, commands);
            return;
        }
        // Компьютер перезагрузился (часы начались заново) или пакет первый — привязаться заново
        if (!anchored || nowMs < lastServerNow) {
            anchor = here + LEAD - samples(nowMs);
            anchored = true;
        }
        lastServerNow = nowMs;
        // Где сейчас «сейчас» сервера относительно нашего времени: в идеале ровно на LEAD впереди
        long serverNowHere = anchor + samples(nowMs) - here;
        if (serverNowHere < LEAD / 4 || serverNowHere > LEAD + SLACK) anchor += LEAD - serverNowHere;
        synth.schedule(anchor + samples(startMs), commands);
    }
}
