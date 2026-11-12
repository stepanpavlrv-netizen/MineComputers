package com.strikerr212.minepc.block;

import com.strikerr212.minepc.registry.ModBlockEntities;
import com.strikerr212.minepc.registry.ModBlocks;
import com.strikerr212.minepc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок серверной стойки: 4 места по 1U (снизу вверх). В месте — модуль сервера (свой компьютер со своими деталями,
 * адресом и гнездом сзади) или модуль консоли (экран и клавиатура для серверов стойки). Один модуль может быть
 * выдвинут — его видно целиком, у сервера открыта крышка.
 */
public class RackBlockEntity extends BlockEntity {

    public static final int SLOTS = 4;
    public static final int EMPTY = 0, SERVER = 1, CONSOLE = 2;

    private final int[] kinds = new int[SLOTS];
    private final PcCaseBlockEntity[] servers = new PcCaseBlockEntity[SLOTS];
    /** Какой модуль выдвинут (-1 — все задвинуты). */
    private int extended = -1;

    public RackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RACK.get(), pos, state);
    }

    public int kind(int slot) {
        return slot < 0 || slot >= SLOTS ? EMPTY : kinds[slot];
    }

    /** Сервер в месте slot или null. */
    public PcCaseBlockEntity server(int slot) {
        return slot < 0 || slot >= SLOTS || kinds[slot] != SERVER ? null : servers[slot];
    }

    public int extended() {
        return extended;
    }

    public boolean hasConsole() {
        for (int k : kinds) if (k == CONSOLE) return true;
        return false;
    }

    private PcCaseBlockEntity newServer(int slot) {
        PcCaseBlockEntity s = new PcCaseBlockEntity(worldPosition, ModBlocks.PC_CASE.get().defaultBlockState(), slot);
        if (level != null) s.setLevel(level);
        return s;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        for (PcCaseBlockEntity s : servers) if (s != null) s.setLevel(level);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RackBlockEntity be) {
        for (int i = 0; i < SLOTS; i++) {
            PcCaseBlockEntity s = be.server(i);
            if (s != null) PcCaseBlockEntity.serverTick(level, pos, state, s);
        }
    }

    // ======================= Модули =======================

    /** Поставить модуль из предмета в свободное место. */
    public boolean insert(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS || kinds[slot] != EMPTY) return false;
        if (stack.is(ModItems.SERVER_MODULE.get())) {
            kinds[slot] = SERVER;
            servers[slot] = newServer(slot);
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null && data.copyTag().contains("machine") && level != null) {
                servers[slot].loadWithComponents(data.copyTag().getCompound("machine"), level.registryAccess());
            }
        } else if (stack.is(ModItems.CONSOLE_MODULE.get())) {
            kinds[slot] = CONSOLE;
        } else {
            return false;
        }
        changed();
        return true;
    }

    /** Вынуть модуль целиком (сервер — вместе с деталями внутри). Пустой стек — места нет. */
    public ItemStack take(int slot) {
        if (slot < 0 || slot >= SLOTS || kinds[slot] == EMPTY) return ItemStack.EMPTY;
        ItemStack out;
        if (kinds[slot] == SERVER) {
            out = new ItemStack(ModItems.SERVER_MODULE.get());
            PcCaseBlockEntity s = servers[slot];
            if (s != null && level != null) {
                CompoundTag t = new CompoundTag();
                t.put("machine", s.saveWithoutMetadata(level.registryAccess()));
                out.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
                s.setRemoved();
            }
            servers[slot] = null;
        } else {
            out = new ItemStack(ModItems.CONSOLE_MODULE.get());
        }
        kinds[slot] = EMPTY;
        if (extended == slot) extended = -1;
        changed();
        return out;
    }

    public void setExtended(int slot) {
        extended = slot;
        changed();
    }

    /** Всё содержимое — в добычу (стойку сломали). */
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = take(i);
            if (!s.isEmpty()) Block.popResource(level, pos, s);
        }
    }

    /** Изменилось, что стоит в стойке: сохранить, показать игрокам, обновить гнёзда в схеме сети. */
    void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            com.strikerr212.minepc.lan.LanManager.updateDevice(level, worldPosition);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) com.strikerr212.minepc.lan.LanManager.updateDevice(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        for (PcCaseBlockEntity s : servers) if (s != null) s.unloadModule();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        for (PcCaseBlockEntity s : servers) if (s != null) s.unloadModule();
    }

    // ======================= Сохранение =======================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray("kinds", kinds.clone());
        tag.putInt("extended", extended);
        for (int i = 0; i < SLOTS; i++) {
            if (kinds[i] == SERVER && servers[i] != null) tag.put("server" + i, servers[i].saveWithoutMetadata(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] k = tag.getIntArray("kinds");
        for (int i = 0; i < SLOTS; i++) {
            kinds[i] = i < k.length ? k[i] : EMPTY;
            if (kinds[i] == SERVER) {
                if (servers[i] == null) servers[i] = newServer(i);
                if (tag.contains("server" + i)) servers[i].loadWithComponents(tag.getCompound("server" + i), registries);
            } else if (servers[i] != null) {
                servers[i].setRemoved();
                servers[i] = null;
            }
        }
        extended = tag.contains("extended") ? tag.getInt("extended") : -1;
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
