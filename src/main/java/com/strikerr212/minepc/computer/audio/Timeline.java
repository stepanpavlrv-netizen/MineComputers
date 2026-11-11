package com.strikerr212.minepc.computer.audio;

/**
 * Привязка времени сервера (мс по часам компьютера) ко времени синтезатора у игрока (отсчёты).
 *
 * Звук «сразу» (выстрел в игре, писк) играет без всякого запаса — как только пакет пришёл.
 * Музыка программы отправляет заранее (очередь sound.process на секунду-другую вперёд), поэтому её пакеты
 * встают точно друг за другом по часам сервера. Если пакет опоздал (сеть задержала) — играем сразу и
 * сдвигаем привязку; если привязка убежала вперёд больше чем на {@link #SLACK_MS} — подтягиваем.
 */
public final class Timeline {

    public static final int SLACK_MS = 120;
    private static final long SLACK = (long) Synth.RATE * SLACK_MS / 1000;

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

    /** Пакет с сервера. startMs = -1 — играть сразу. Возвращает отсчёт, с которого он зазвучит. */
    public long receive(long startMs, long nowMs, byte[] commands) {
        long here = synth.now();
        if (startMs < 0) {
            synth.schedule(here, commands);
            return here;
        }
        // Звук «сразу» (или пакет первый, или компьютер перезагрузился и часы начались заново):
        // играем немедленно и привязываем «сейчас» сервера к этому месту — следующие пакеты встанут вплотную
        if (!anchored || nowMs < lastServerNow || startMs <= nowMs) {
            anchor = here - samples(nowMs);
            anchored = true;
        }
        lastServerNow = nowMs;
        // Где сейчас «сейчас» сервера относительно нашего времени: в идеале — ровно здесь
        long serverNowHere = anchor + samples(nowMs) - here;
        if (serverNowHere < 0 || serverNowHere > SLACK) anchor -= serverNowHere;
        long start = Math.max(here, anchor + samples(startMs));
        synth.schedule(start, commands);
        return start;
    }
}
