package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Кабельный лоток: до 8 проводов (каждого цвета — один), идёт в любую сторону, в том числе вверх и вниз по стене
 * и под потолком. Соединяется с соседними лотками и с проводами на полу того же цвета.
 * Провод в лотке — число: цвет (0..15) | оптика << 4.
 */
public class TrayBlockEntity extends BlockEntity {

    public static final int MAX_WIRES = 8;
    public static final ModelProperty<int[]> WIRES = new ModelProperty<>();
    public static final ModelProperty<Integer> CONN = new ModelProperty<>();
    /** Порядок цветов для новых проводов. */
    private static final DyeColor[] ORDER = {DyeColor.BLUE, DyeColor.ORANGE, DyeColor.LIME, DyeColor.YELLOW, DyeColor.RED,
            DyeColor.WHITE, DyeColor.PURPLE, DyeColor.CYAN, DyeColor.PINK, DyeColor.GREEN, DyeColor.LIGHT_BLUE, DyeColor.MAGENTA,
            DyeColor.BROWN, DyeColor.GRAY, DyeColor.LIGHT_GRAY, DyeColor.BLACK};

    private final List<Integer> wires = new ArrayList<>();
    /** Куда соединён (6 бит по Direction.ordinal) — для модели. */
    private int conn;

    public TrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAY.get(), pos, state);
    }

    public static int color(int w) {
        return w & 15;
    }

    public static boolean fiber(int w) {
        return (w & 16) != 0;
    }

    public static int make(int color, boolean fiber) {
        return color | (fiber ? 16 : 0);
    }

    public int conn() {
        return conn;
    }

    public List<Integer> wires() {
        return wires;
    }

    public int[] packed() {
        int[] out = new int[wires.size()];
        for (int i = 0; i < out.length; i++) out[i] = wires.get(i);
        return out;
    }

    public boolean has(int wire) {
        return wires.contains(wire);
    }

    /** Есть ли провод этого цвета и вида. */
    public boolean has(Wire w) {
        return wires.contains(make(w.color, w.fiber));
    }

    /** Цвет для нового провода: желаемый (краситель во второй руке), иначе первый свободный. -1 — некуда. */
    public int pickColor(int wanted, boolean fiber) {
        if (wires.size() >= MAX_WIRES) return -1;
        if (wanted >= 0) return has(make(wanted, fiber)) ? -1 : wanted;
        for (DyeColor c : ORDER) if (!has(make(c.getId(), fiber))) return c.getId();
        return -1;
    }

    public void add(int wire) {
        wires.add(wire);
        changed();
    }

    public int removeLast() {
        int w = wires.remove(wires.size() - 1);
        changed();
        return w;
    }

    public boolean recolorLast(int color) {
        if (wires.isEmpty()) return false;
        int last = wires.get(wires.size() - 1);
        int w = make(color, fiber(last));
        if (has(w)) return false;
        wires.set(wires.size() - 1, w);
        changed();
        return true;
    }

    /** Соседи: лотки — всегда, кабель на полу — если там есть провод того же цвета. */
    public void reconnect() {
        if (level == null) return;
        int c = 0;
        for (Direction d : Direction.values()) {
            BlockPos n = worldPosition.relative(d);
            if (!level.isLoaded(n)) continue;
            BlockEntity be = level.getBlockEntity(n);
            if (be instanceof TrayBlockEntity) c |= 1 << d.ordinal();
            else if (be instanceof CableBlockEntity cable && d.getAxis().isHorizontal()) {
                for (Wire w : cable.wires()) if (has(w)) c |= 1 << d.ordinal();
            }
        }
        if (c != conn) {
            conn = c;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Провода изменились: схема сети, соседи, игроки. */
    public void changed() {
        setChanged();
        if (level instanceof ServerLevel server) {
            LanData.get(server).putTray(worldPosition, packed());
            reconnect();
            for (Direction d : Direction.values()) {
                BlockPos n = worldPosition.relative(d);
                if (!level.isLoaded(n)) continue;
                if (level.getBlockEntity(n) instanceof TrayBlockEntity t) t.reconnect();
                if (level.getBlockEntity(n) instanceof CableBlockEntity cable) cable.reconnect();
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) LanData.get(server).putTray(worldPosition, packed());
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(WIRES, packed()).with(CONN, conn).build();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray("wires", packed());
        tag.putInt("conn", conn);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wires.clear();
        for (int w : tag.getIntArray("wires")) if (wires.size() < MAX_WIRES) wires.add(w & 31);
        conn = tag.getInt("conn");
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
