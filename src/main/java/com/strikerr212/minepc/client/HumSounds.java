package com.strikerr212.minepc.client;

import com.strikerr212.minepc.MineClientConfig;
import com.strikerr212.minepc.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Гул работающих компьютеров: вентиляторы (выше и громче под нагрузкой и при нагреве) и жёсткий диск.
 * При включении звук раскручивается, при выключении — затихает. Сервер сообщает обороты раз в пару секунд.
 */
public final class HumSounds {

    private static final Map<com.strikerr212.minepc.block.HostRef, Hum> FANS = new HashMap<>();
    private static final Map<com.strikerr212.minepc.block.HostRef, Hum> DISKS = new HashMap<>();

    private HumSounds() {
    }

    /** Главный поток: пришли обороты от сервера. */
    public static void update(com.strikerr212.minepc.block.HostRef host, int fan, boolean hdd, boolean start) {
        set(FANS, host, fan / 100f, false, start);
        set(DISKS, host, hdd ? 1f : 0f, true, start);
    }

    private static void set(Map<com.strikerr212.minepc.block.HostRef, Hum> map, com.strikerr212.minepc.block.HostRef host,
                            float target, boolean disk, boolean start) {
        BlockPos pos = host.pos();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Hum h = map.get(host);
        if (h != null && h.isStopped()) {
            map.remove(host);
            h = null;
        }
        if (h == null) {
            if (target <= 0) return;
            h = new Hum(disk ? ModSounds.HDD_SPIN.get() : ModSounds.FAN.get(), pos, disk);
            // Компьютер уже работал (игрок подошёл) — сразу на нужных оборотах, без раскрутки
            if (!start) h.level = target;
            map.put(host, h);
            mc.getSoundManager().play(h);
        }
        h.target(target);
    }

    public static void clear() {
        FANS.values().forEach(Hum::finish);
        DISKS.values().forEach(Hum::finish);
        FANS.clear();
        DISKS.clear();
    }

    private static final class Hum extends AbstractTickableSoundInstance {
        private final boolean disk;
        private float level, target;
        private long lastUpdate = System.currentTimeMillis();

        Hum(SoundEvent event, BlockPos pos, boolean disk) {
            super(event, SoundSource.BLOCKS, RandomSource.create());
            this.disk = disk;
            this.looping = true;
            this.delay = 0;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
            this.attenuation = Attenuation.LINEAR;
            this.volume = 0.001f;
            this.pitch = disk ? 0.5f : 0.55f;
        }

        void target(float t) {
            target = t;
            lastUpdate = System.currentTimeMillis();
        }

        void finish() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            // Сервер замолчал (чанк выгрузился, корпус сломали) — затихаем
            if (System.currentTimeMillis() - lastUpdate > 6000) target = 0;
            // Раскрутка ~2 с, остановка ~3 с
            level += (target - level) * (target > level ? 0.04f : 0.025f);
            if (target <= 0 && level < 0.02f) {
                stop();
                return;
            }
            float k = MineClientConfig.hum();
            if (disk) {
                volume = Math.max(0.001f, 0.1f * Math.min(1, level) * k);
                pitch = 0.5f + 0.5f * level;
            } else {
                volume = Math.max(0.001f, (0.06f + 0.22f * level) * Math.min(1, level * 5) * k);
                pitch = 0.6f + 0.6f * level;
            }
        }
    }
}
