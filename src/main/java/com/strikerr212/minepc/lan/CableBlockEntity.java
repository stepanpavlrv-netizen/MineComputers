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
import net.minecraft.world.level.BlockGetter;
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
    /** Для модели: блок, под которым спрятан кабель (или null). */
    public static final ModelProperty<BlockState> FACADE = new ModelProperty<>();

    private final List<Wire> wires = new ArrayList<>();
    private VoxelShape shape;
    private BlockState facade;

    /** Блок, которым закрыт кабель (ПКМ блоком) — провода внутри, снаружи обычный блок. */
    public BlockState facade() {
        return facade;
    }

    public void setFacade(BlockState state) {
        facade = state;
        changed();
    }

    /** Можно ли спрятать кабель под этим блоком: обычный непрозрачный куб без своей «начинки». */
    public static boolean canBeFacade(BlockState s, BlockGetter level, BlockPos pos) {
        return !s.hasBlockEntity() && s.isSolidRender(level, pos) && s.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL
                && Block.isShapeFullBlock(s.getShape(level, pos));
    }

    /** Предметы, которые выпадут, если блок сломать целиком: провода и блок-маскировка. */
    public List<net.minecraft.world.item.ItemStack> drops() {
        List<net.minecraft.world.item.ItemStack> out = new ArrayList<>();
        int copper = 0, fiber = 0;
        for (Wire w : wires) {
            if (w.fiber) fiber++;
            else copper++;
        }
        if (copper > 0) out.add(new net.minecraft.world.item.ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE.get(), copper));
        if (fiber > 0) out.add(new net.minecraft.world.item.ItemStack(com.strikerr212.minepc.registry.ModItems.FIBER.get(), fiber));
        if (facade != null) out.add(new net.minecraft.world.item.ItemStack(facade.getBlock()));
        return out;
    }

    /** Ломают в творческом режиме — ничего не выпадает. */
    public void clearForCreative() {
        wires.clear();
        facade = null;
    }

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
        // Подъём по стенке соседнего блока: вертикальная полоска у самого края
        if ((w.ups & Wire.E) != 0) out.add(new int[]{15, 0, cz - 1, 16, 18, cz + 1});
        if ((w.ups & Wire.W) != 0) out.add(new int[]{0, 0, cz - 1, 1, 18, cz + 1});
        if ((w.ups & Wire.S) != 0) out.add(new int[]{cx - 1, 0, 15, cx + 1, 18, 16});
        if ((w.ups & Wire.N) != 0) out.add(new int[]{cx - 1, 0, 0, cx + 1, 18, 1});
        return out;
    }

    public VoxelShape shape() {
        if (facade != null) return Shapes.block();
        if (shape == null) {
            VoxelShape s = Shapes.empty();
            for (Wire w : wires) {
                for (int[] b : boxes(w)) {
                    s = Shapes.or(s, Block.box(b[0], b[1], b[2], b[3], Math.min(16, Math.max(b[4], 2.5)), b[5]));
                }
            }
            shape = s.isEmpty() ? Block.box(6, 0, 6, 10, 2, 10) : s;
        }
        return shape;
    }

    /** Есть ли в блоке провод этого цвета, выходящий на эту дорожку в сторону toward. */
    private static boolean hasMatch(Level level, BlockPos p, Wire w, Direction toward, int lane) {
        if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof CableBlockEntity other)) return false;
        for (Wire o : other.wires) if (o.sameKind(w) && o.laneToward(toward) == lane) return true;
        return false;
    }

    private boolean solid(BlockPos p, Direction face) {
        return level.isLoaded(p) && level.getBlockState(p).isFaceSturdy(level, p, face);
    }

    /** Через клетку можно протянуть провод (воздух, трава, факел…). */
    private boolean open(BlockPos p) {
        return level.isLoaded(p) && level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /**
     * Пересчитать, с кем соединён каждый провод (как редстоун): сосед на том же уровне, устройство (вилка),
     * провод на блоке выше (поднимаемся по стенке, если сверху над нами не сплошной блок) или ниже. true — что-то изменилось.
     */
    public boolean reconnect() {
        if (level == null) return false;
        boolean changed = false;
        // Подъём по стенке возможен, только если над нами пусто (провод идёт по ребру стенки в клетку выше)
        boolean roofOpen = open(worldPosition.above());
        for (Wire w : wires) {
            int dirs = 0, plugs = 0, ups = 0, downs = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = worldPosition.relative(d);
                if (!level.isLoaded(n)) continue;
                BlockState ns = level.getBlockState(n);
                int lane = w.laneToward(d), bit = Wire.bit(d);
                if (level.getBlockEntity(n) instanceof CableBlockEntity) {
                    if (hasMatch(level, n, w, d.getOpposite(), lane)) dirs |= bit;
                } else if (level.getBlockEntity(n) instanceof TrayBlockEntity tray) {
                    if (tray.has(w)) dirs |= bit;             // провод уходит в лоток того же цвета
                } else if (ns.getBlock() instanceof LanDevice dev
                        && (w.fiber ? dev.acceptsFiber(ns, d.getOpposite()) : dev.acceptsCable(ns, d.getOpposite()))) {
                    dirs |= bit;
                    plugs |= bit;
                    LanManager.updateDevice(level, n);
                } else if (ns.isFaceSturdy(level, n, d.getOpposite())) {
                    if (roofOpen && hasMatch(level, n.above(), w, d.getOpposite(), lane)) {
                        dirs |= bit;
                        ups |= bit;
                    }
                } else if (open(n) && solid(worldPosition.below(), d) && hasMatch(level, n.below(), w, d.getOpposite(), lane)) {
                    // Мы лежим на стенке, а провод снизу поднимается к нам по её боку (те же условия, что у него)
                    dirs |= bit;
                    downs |= bit;
                }
            }
            if (dirs != w.dirs || plugs != w.plugs || ups != w.ups || downs != w.downs) {
                w.dirs = dirs;
                w.plugs = plugs;
                w.ups = ups;
                w.downs = downs;
                changed = true;
            }
        }
        if (changed) changed();
        return changed;
    }

    /** Провода изменились: сохранить, обновить форму, модель у клиентов и схему сети. */
    public void changed() {
        shape = null;
        setChanged();
        if (level != null) {
            if (level instanceof net.minecraft.server.level.ServerLevel server) LanData.get(server).putCable(worldPosition, packed());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Соседние провода, в том числе на блок выше и ниже (подъёмы по стенкам). */
    public void reconnectNeighbours() {
        if (level == null) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (BlockPos n : new BlockPos[]{worldPosition.relative(d), worldPosition.relative(d).above(), worldPosition.relative(d).below()}) {
                if (level.isLoaded(n) && level.getBlockEntity(n) instanceof CableBlockEntity other) other.reconnect();
            }
        }
    }

    public int[] packed() {
        int[] packed = new int[wires.size()];
        for (int i = 0; i < packed.length; i++) packed[i] = wires.get(i).packed();
        return packed;
    }

    /** Загрузился чанк: занести провода в схему сети (для миров, где кабель лежал до появления схемы). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof net.minecraft.server.level.ServerLevel server && !wires.isEmpty()) LanData.get(server).putCable(worldPosition, packed());
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(WIRES, packed()).with(FACADE, facade).build();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Wire w : wires) list.add(w.save());
        tag.put("wires", list);
        if (facade != null) tag.put("facade", net.minecraft.nbt.NbtUtils.writeBlockState(facade));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wires.clear();
        for (Tag t : tag.getList("wires", Tag.TAG_COMPOUND)) wires.add(Wire.load((CompoundTag) t));
        facade = tag.contains("facade") ? net.minecraft.nbt.NbtUtils.readBlockState(
                registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), tag.getCompound("facade")) : null;
        if (facade != null && facade.isAir()) facade = null;
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
