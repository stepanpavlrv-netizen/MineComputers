package com.strikerr212.minepc.client;

import com.strikerr212.minepc.MineClientConfig;
import com.strikerr212.minepc.MineComputers;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** Громкость звуков мода из настроек игрока (minepc-client.toml): клавиатура, диски, сеть. */
@EventBusSubscriber(modid = MineComputers.MODID, value = Dist.CLIENT)
public final class ModSoundsClient {

    private ModSoundsClient() {
    }

    /** Звук, который мы сами создали уже с нужной громкостью (второй раз не уменьшаем). */
    public static final class Local extends SimpleSoundInstance {
        public Local(ResourceLocation id, SoundSource source, float volume, float pitch, RandomSource random, boolean looping,
                     int delay, SoundInstance.Attenuation attenuation, double x, double y, double z, boolean relative) {
            super(id, source, volume, pitch, random, looping, delay, attenuation, x, y, z, relative);
        }
    }

    private static float factor(ResourceLocation id) {
        if (!id.getNamespace().equals(MineComputers.MODID)) return 1;
        String p = id.getPath();
        if (p.equals("computer.key")) return MineClientConfig.keys();
        if (p.equals("computer.hdd_seek") || p.equals("computer.floppy") || p.equals("computer.hdd_spinup")) return MineClientConfig.drives();
        if (p.equals("lan.relay") || p.equals("lan.radio")) return MineClientConfig.network();
        return 1;
    }

    /** Вышли из мира: экраны, звук программ и гул больше не нужны. */
    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTerminals.clear();
    }

    @SubscribeEvent
    public static void onPlay(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s == null || s instanceof Local) return;
        float k = factor(s.getLocation());
        if (k >= 1) return;
        if (k <= 0) {
            event.setSound(null);
            return;
        }
        event.setSound(new Scaled(s, k));
    }

    /** Тот же звук, только тише. */
    private record Scaled(SoundInstance base, float k) implements SoundInstance {
        @Override public ResourceLocation getLocation() { return base.getLocation(); }
        @Override public WeighedSoundEvents resolve(SoundManager manager) { return base.resolve(manager); }
        @Override public Sound getSound() { return base.getSound(); }
        @Override public SoundSource getSource() { return base.getSource(); }
        @Override public boolean isLooping() { return base.isLooping(); }
        @Override public boolean isRelative() { return base.isRelative(); }
        @Override public int getDelay() { return base.getDelay(); }
        @Override public float getVolume() { return base.getVolume() * k; }
        @Override public float getPitch() { return base.getPitch(); }
        @Override public double getX() { return base.getX(); }
        @Override public double getY() { return base.getY(); }
        @Override public double getZ() { return base.getZ(); }
        @Override public SoundInstance.Attenuation getAttenuation() { return base.getAttenuation(); }
        @Override public boolean canStartSilent() { return base.canStartSilent(); }
        @Override public boolean canPlaySound() { return base.canPlaySound(); }
    }
}
