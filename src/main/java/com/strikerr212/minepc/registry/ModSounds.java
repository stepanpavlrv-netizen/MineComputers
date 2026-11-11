package com.strikerr212.minepc.registry;

import com.strikerr212.minepc.MineComputers;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Звуки мода (сами файлы и подписи — в assets/minepc/sounds.json). */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MineComputers.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> FAN = sound("computer.fan");
    public static final DeferredHolder<SoundEvent, SoundEvent> HDD_SPIN = sound("computer.hdd_spin");
    public static final DeferredHolder<SoundEvent, SoundEvent> HDD_SPINUP = sound("computer.hdd_spinup");
    public static final DeferredHolder<SoundEvent, SoundEvent> HDD_SEEK = sound("computer.hdd_seek");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOPPY = sound("computer.floppy");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISK_INSERT = sound("computer.disk_insert");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISK_EJECT = sound("computer.disk_eject");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEY = sound("computer.key");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_ON = sound("computer.power_on");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_OFF = sound("computer.power_off");
    public static final DeferredHolder<SoundEvent, SoundEvent> PART_INSTALL = sound("computer.part_install");
    public static final DeferredHolder<SoundEvent, SoundEvent> PART_REMOVE = sound("computer.part_remove");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAN_PLUG_IN = sound("lan.plug_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAN_PLUG_OUT = sound("lan.plug_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAN_RELAY = sound("lan.relay");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAN_RADIO = sound("lan.radio");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
