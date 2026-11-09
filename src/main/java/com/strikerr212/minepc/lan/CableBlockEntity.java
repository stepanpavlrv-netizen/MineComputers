package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.List;

/** Провода в блоке кабеля (до 4) и их соединения. */
public class CableBlockEntity extends BlockEntity {

    public static final int MAX_WIRES = 4;
    /** Для модели: провода как числа ({@link Wire#packed()}). */
    public static final ModelProperty<int[]> WIRES = new ModelProperty<>();

    private final List<Wire> wires = new ArrayList<>();
    private VoxelShape shape;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABLE.get(), pos, state);
    }

    public List<Wire> wires() {
        return wires;
    }

    /** Свободна ли такая пара дорожек: два провода не могут идти по одной дорожке. */
    public boolean free(int xi, int zi) {
        for (Wire w : wires) if (w.xi == xi || w.zi == zi) return false;
        return wires.size() < MAX_WIRES;
    }

    /** Ближайшая свободная пара дорожек к желаемой или null. */
    public int[] nearestFree(int xi, int zi) {
        int[] best = null;
        int bestD = Integer.MAX_VALUE;
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                if (!free(x, z)) continue;
                int d = Math.abs(x - xi) + Math.abs(z - zi);
                if (d < bestD) {
                    bestD = d;
                    best = new int[]{x, z};
                }
            }
        }
        return best;
    }

    public void add(Wire w) {
        wires.add(w);
        changed();
    }

    public void remove(Wire w) {
        wires.remove(w);
        changed();
    }

    /** Провод, ближайший к точке (в пикселях блока). */
    public Wire nearest(double px, double pz) {
        Wire best = null;
        double bestD = Double.MAX_VALUE;
        for (Wire w : wires) {
            for (int[] b : boxes(w)) {
                double dx = Math.max(0, Math.max(b[0] - px, px - b[3]));
                double dz = Math.max(0, Math.max(b[2] - pz, pz - b[5]));
                double d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = w;
                }
            }
        }
        return best;
    }

    /** Коробки провода в пикселях: {x0,y0,z0,x1,y1,z1}. Узел и отрезки до краёв в сторону соединений. */
    public static List<int[]> boxes(Wire w) {
        List<int[]> out = new ArrayList<>();
        int cx = Wire.LANE[w.xi], cz = Wire.LANE[w.zi];
        int d = w.drawnDirs();
        out.add(new int[]{cx - 1, 0, cz - 1, cx + 1, 2, cz + 1});
        if ((d & Wire.E) != 0) out.add(new int[]{cx + 1, 0, cz - 1, 16, 2, cz + 1});
        if ((d & Wire.W) != 0) out.add(new int[]{0, 0, cz - 1, cx - 1, 2, cz + 1});
        if ((d & Wire.S) != 0) out.add(new int[]{cx - 1, 0, cz + 1, cx + 1, 2, 16});
        if ((d & Wire.N) != 0) out.add(new int[]{cx - 1, 0, 0, cx + 1, 2, cz - 1});
        return out;
    }

    public VoxelShape shape() {
        if (shape == null) {
            VoxelShape s = Shapes.empty();
            for (Wire w : wires) {
                for (int[] b : boxes(w)) {
                    s = Shapes.or(s, Block.box(b[0], b[1], b[2], b[3], Math.max(b[4], 2.5), b[5]));
                }
            }
            shape = s.isEmpty() ? Block.box(6, 0, 6, 10, 2, 10) : s;
        }
        return shape;
    }

    /** Пересчитать, с кем соединён каждый провод. true — что-то изменилось. */
    public boolean reconnect() {
        if (level == null) return false;
        boolean changed = false;
        for (Wire w : wires) {
            int dirs = 0, plugs = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = worldPosition.relative(d);
                if (!level.isLoaded(n)) continue;
                BlockState ns = level.getBlockState(n);
                int lane = w.laneToward(d);
                if (level.getBlockEntity(n) instanceof CableBlockEntity other) {
                    for (Wire o : other.wires) {
                        if (o.color == w.color && o.laneToward(d.getOpposite()) == lane) {
                            dirs |= Wire.bit(d);
                            break;
                        }
                    }
                } else if (ns.getBlock() instanceof LanDevice dev && dev.acceptsCable(ns, d.getOpposite())) {
                    dirs |= Wire.bit(d);
                    plugs |= Wire.bit(d);
                }
            }
            if (dirs != w.dirs || plugs != w.plugs) {
                w.dirs = dirs;
                w.plugs = plugs;
                changed = true;
            }
        }
        if (changed) changed();
        return changed;
    }

    /** Провода изменились: сохранить, обновить форму, модель у клиентов и сеть. */
    public void changed() {
        shape = null;
        setChanged();
        if (level != null) {
            if (!level.isClientSide) LanManager.invalidate(level);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Соседние провода (добавили/убрали провод — у соседей могли поменяться соединения). */
    public void reconnectNeighbours() {
        if (level == null) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof CableBlockEntity other) other.reconnect();
        }
    }

    @Override
    public ModelData getModelData() {
        int[] packed = new int[wires.size()];
        for (int i = 0; i < packed.length; i++) packed[i] = wires.get(i).packed();
        return ModelData.builder().with(WIRES, packed).build();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Wire w : wires) list.add(w.save());
        tag.put("wires", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wires.clear();
        for (Tag t : tag.getList("wires", Tag.TAG_COMPOUND)) wires.add(Wire.load((CompoundTag) t));
        shape = null;
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
