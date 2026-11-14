package com.strikerr212.minepc.lan;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Патч-корды устройства для отрисовки: копия того, что лежит в схеме сети ({@link LanData}), — едет к игрокам
 * вместе с блоком. Каждый корд рисует тот конец, у которого координата меньше.
 */
public final class CordList {

    /** Другой конец корда, его порт и цвет. */
    public record End(BlockPos other, int port, int otherPort, int color) {
    }

    /** Блок, у которого есть патч-корды (корпус, коммутатор, роутер, патч-панель). */
    public interface Holder {
        CordList cords();
    }

    private List<End> ends = List.of();

    public List<End> ends() {
        return ends;
    }

    public void set(List<End> list) {
        ends = List.copyOf(list);
    }

    public void save(CompoundTag tag) {
        if (ends.isEmpty()) return;
        ListTag list = new ListTag();
        for (End e : ends) {
            CompoundTag t = new CompoundTag();
            t.putLong("o", e.other().asLong());
            t.putInt("p", e.port());
            t.putInt("q", e.otherPort());
            t.putInt("c", e.color());
            list.add(t);
        }
        tag.put("cords", list);
    }

    public void load(CompoundTag tag) {
        List<End> list = new ArrayList<>();
        for (Tag t : tag.getList("cords", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            list.add(new End(BlockPos.of(c.getLong("o")), c.getInt("p"), c.getInt("q"), c.getInt("c")));
        }
        ends = List.copyOf(list);
    }

    /** Воткнут ли патч-корд прямо в гнездо port устройства (у игрока и на сервере — по копии у блока). */
    public static boolean inSocket(net.minecraft.world.level.BlockGetter level, BlockPos dev, int port) {
        if (!(level.getBlockEntity(dev) instanceof Holder h)) return false;
        for (End e : h.cords().ends()) if (e.port() == port) return true;
        return false;
    }

    /** Переписать корды блока из схемы сети и отправить игрокам. */
    public static void refresh(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof BlockEntity be) || !(be instanceof Holder h)) return;
        List<End> list = new ArrayList<>();
        for (LanData.Cord c : LanData.get(level).cords(pos)) {
            if (c.a().equals(pos)) list.add(new End(c.b(), c.portA(), c.portB(), c.color()));
            else list.add(new End(c.a(), c.portB(), c.portA(), c.color()));
        }
        h.cords().set(list);
        be.setChanged();
        level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
    }
}
