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
    /** Сервер 2U: стоит на этом месте и занимает следующее над ним (там UPPER). */
    public static final int SERVER_2U = 3, UPPER = 4;

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

    /** Нижнее место модуля, которому принадлежит slot (у 2U верхняя половина — его же). */
    public int base(int slot) {
        return kind(slot) == UPPER ? slot - 1 : slot;
    }

    /** Высота модуля в местах (1U или 2U). */
    public int size(int slot) {
        return kind(slot) == SERVER_2U ? 2 : 1;
    }

    public static boolean isServer(int kind) {
        return kind == SERVER || kind == SERVER_2U;
    }

    /** Сервер в месте slot или null. */
    public PcCaseBlockEntity server(int slot) {
        return slot < 0 || slot >= SLOTS || !isServer(kinds[slot]) ? null : servers[slot];
    }

    public int extended() {
        return extended;
    }

    public boolean hasConsole() {
        for (int k : kinds) if (k == CONSOLE) return true;
        return false;
    }

    private PcCaseBlockEntity newServer(int slot, boolean tall) {
        PcCaseBlockEntity s = new PcCaseBlockEntity(worldPosition, ModBlocks.PC_CASE.get().defaultBlockState(), slot);
        s.setTall(tall);
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

    /** Поставить модуль из предмета в свободное место (2U — сюда и на место выше; если выше занято — на место ниже). */
    public boolean insert(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS || kinds[slot] != EMPTY) return false;
        boolean tall = stack.is(ModItems.SERVER_MODULE_2U.get());
        if (tall) {
            if (slot + 1 >= SLOTS || kinds[slot + 1] != EMPTY) {
                if (slot - 1 >= 0 && kinds[slot - 1] == EMPTY) slot--;
                else return false;
            }
        }
        if (stack.is(ModItems.SERVER_MODULE.get()) || tall) {
            kinds[slot] = tall ? SERVER_2U : SERVER;
            if (tall) kinds[slot + 1] = UPPER;
            servers[slot] = newServer(slot, tall);
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
        slot = base(slot);
        ItemStack out;
        if (isServer(kinds[slot])) {
            boolean tall = kinds[slot] == SERVER_2U;
            out = new ItemStack(tall ? ModItems.SERVER_MODULE_2U.get() : ModItems.SERVER_MODULE.get());
            if (tall) kinds[slot + 1] = EMPTY;
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
            if (isServer(kinds[i]) && servers[i] != null) tag.put("server" + i, servers[i].saveWithoutMetadata(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] k = tag.getIntArray("kinds");
        for (int i = 0; i < SLOTS; i++) {
            kinds[i] = i < k.length ? k[i] : EMPTY;
            if (isServer(kinds[i])) {
                if (servers[i] == null) servers[i] = newServer(i, kinds[i] == SERVER_2U);
                servers[i].setTall(kinds[i] == SERVER_2U);
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
        CompoundTag tag = saveWithoutMetadata(registries);
        for (int i = 0; i < SLOTS; i++) {
            if (tag.contains("server" + i)) PcCaseBlockEntity.clientView(tag.getCompound("server" + i));
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
