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

/**
 * Провода в клетке кабеля и их соединения. Провода лежат на гранях клетки (пол, стены, потолок), на каждой грани —
 * до {@link #MAX_PER_FACE} проводов, каждый на своих дорожках (два провода одной грани не делят дорожку ни по одной
 * оси — поэтому никогда не пересекаются).
 */
public class CableBlockEntity extends BlockEntity {

    public static final int MAX_PER_FACE = Wire.LANES;
    /** Для модели: провода как числа ({@link Wire#packed()}). */
    public static final ModelProperty<long[]> WIRES = new ModelProperty<>();
    /** Для модели: где гнёзда, в которые воткнуты вилки (по 4 числа на провод: сторона → гнездо или -1). */
    public static final ModelProperty<int[]> SOCKETS = new ModelProperty<>();
    /** Для модели: блок, под которым спрятан кабель (или null). */
    public static final ModelProperty<BlockState> FACADE = new ModelProperty<>();

    private final List<Wire> wires = new ArrayList<>();
    private VoxelShape shape;
    private BlockState facade;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABLE.get(), pos, state);
    }

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

    /** Предмет, в который превращается провод. */
    public static net.minecraft.world.item.ItemStack item(Wire w) {
        return new net.minecraft.world.item.ItemStack(w.fiber ? com.strikerr212.minepc.registry.ModItems.FIBER.get()
                : com.strikerr212.minepc.registry.ModItems.CABLE.get());
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

    public List<Wire> wires() {
        return wires;
    }

    // ======================= Дорожки =======================

    /** Свободна ли пара дорожек (a, b — по осям плоскости грани в порядке X, Y, Z) на грани face. */
    public boolean free(Direction face, int a, int b) {
        Direction.Axis[] ax = Wire.planeAxes(face);
        int n = 0;
        for (Wire w : wires) {
            if (w.face != face) continue;
            n++;
            if (w.lane[ax[0].ordinal()] == a || w.lane[ax[1].ordinal()] == b) return false;
        }
        return n < MAX_PER_FACE;
    }

    /** Ближайшая свободная пара дорожек на грани или null. */
    public int[] nearestFree(Direction face, int a, int b) {
        int[] best = null;
        int bestD = Integer.MAX_VALUE;
        for (int x = 0; x < Wire.LANES; x++) {
            for (int y = 0; y < Wire.LANES; y++) {
                if (!free(face, x, y)) continue;
                int d = Math.abs(x - a) + Math.abs(y - b);
                if (d < bestD) {
                    bestD = d;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    /** Новый провод на грани face с дорожками a, b по осям плоскости. */
    public static Wire make(int color, boolean fiber, Direction face, int a, int b) {
        Direction.Axis[] ax = Wire.planeAxes(face);
        int[] l = new int[3];
        l[ax[0].ordinal()] = a;
        l[ax[1].ordinal()] = b;
        Wire w = new Wire(color, face, l[0], l[1], l[2]);
        w.fiber = fiber;
        return w;
    }

    /** Ближайшая дорожка к точке на оси (в пикселях блока). */
    public static int laneAt(double pixel) {
        return Math.max(0, Math.min(Wire.LANES - 1, (int) Math.round((pixel - Wire.LANE[0] - 0.5) / 2)));
    }

    public void add(Wire w) {
        wires.add(w);
        changed();
    }

    public void remove(Wire w) {
        wires.remove(w);
        changed();
    }

    // ======================= Форма =======================

    /** Пиксели, занятые проводом поперёк нормали: начало полоски по оси (дорожка) или слой у грани. */
    private static float[] across(Wire w, Direction.Axis a) {
        if (a == w.face.getAxis()) {
            return w.face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? new float[]{15, 16} : new float[]{0, 1};
        }
        int p = Wire.LANE[w.lane[a.ordinal()]];
        return new float[]{p, p + 1};
    }

    /** Коробки провода в пикселях: {x0,y0,z0,x1,y1,z1} — узел и отрезки до краёв в нарисованные стороны. */
    public static List<float[]> boxes(Wire w) {
        List<float[]> out = new ArrayList<>();
        float[][] r = new float[3][];
        for (Direction.Axis a : Direction.Axis.values()) r[a.ordinal()] = across(w, a);
        out.add(box(r));
        boolean[] drawn = w.drawn();
        Direction[] ds = w.dirs();
        for (int i = 0; i < 4; i++) {
            if (!drawn[i]) continue;
            Direction d = ds[i];
            float[][] s = r.clone();
            int ax = d.getAxis().ordinal();
            float[] c = r[ax];
            // Отрезок, где вилка, кончается раньше — дальше вилку рисует модель (до гнезда)
            boolean plug = w.plug[i] != 0;
            if (d.getAxisDirection() == Direction.AxisDirection.POSITIVE) s[ax] = new float[]{c[1], plug ? 13 : 16};
            else s[ax] = new float[]{plug ? 3 : 0, c[0]};
            if (s[ax][1] > s[ax][0]) out.add(box(s));
        }
        return out;
    }

    private static float[] box(float[][] r) {
        return new float[]{r[0][0], r[1][0], r[2][0], r[0][1], r[1][1], r[2][1]};
    }

    public VoxelShape shape() {
        if (facade != null) return Shapes.block();
        if (shape == null) {
            VoxelShape s = Shapes.empty();
            for (Wire w : wires) {
                for (float[] b : boxes(w)) {
                    // Тонкий провод трудно поймать курсором — форма чуть толще самого провода
                    s = Shapes.or(s, Block.box(Math.max(0, b[0] - 0.5), Math.max(0, b[1] - 0.5), Math.max(0, b[2] - 0.5),
                            Math.min(16, b[3] + 0.5), Math.min(16, b[4] + 0.5), Math.min(16, b[5] + 0.5)));
                }
            }
            shape = s.isEmpty() ? Block.box(6, 0, 6, 10, 1, 10) : s;
        }
        return shape;
    }

    /** Провод, ближайший к точке (в пикселях блока). */
    public Wire nearest(double px, double py, double pz) {
        Wire best = null;
        double bestD = Double.MAX_VALUE;
        for (Wire w : wires) {
            for (float[] b : boxes(w)) {
                double dx = Math.max(0, Math.max(b[0] - px, px - b[3]));
                double dy = Math.max(0, Math.max(b[1] - py, py - b[4]));
                double dz = Math.max(0, Math.max(b[2] - pz, pz - b[5]));
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = w;
                }
            }
        }
        return best;
    }

    // ======================= Соединения =======================

    /** Провод того же вида на грани face клетки p, идущий через то же ребро (та же дорожка по оси ребра). */
    private static Wire match(Level level, BlockPos p, Direction face, Wire w, Direction.Axis edge) {
        if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof CableBlockEntity other)) return null;
        for (Wire o : other.wires) {
            if (o != w && o.face == face && o.sameKind(w) && o.lane[edge.ordinal()] == w.lane[edge.ordinal()]) return o;
        }
        return null;
    }

    /** Опора грани: блок, к которому прижат провод, держит его. */
    public boolean supported(Wire w) {
        if (level == null) return true;
        BlockPos s = worldPosition.relative(w.face);
        return level.isLoaded(s) && level.getBlockState(s).isFaceSturdy(level, s, w.face.getOpposite());
    }

    /**
     * Пересчитать соединения каждого провода: лоток, провод на соседней грани этой клетки (внутренний угол), такой же
     * провод в соседней клетке, провод на боку опоры (внешний угол) — и вилка в устройство. В устройство втыкается
     * только конец провода: если провод идёт мимо (соединён с двух сторон), он в устройство не лезет; если с одной —
     * втыкается прямо по ходу, а если там устройства нет — в устройство сбоку. true — что-то изменилось.
     */
    public boolean reconnect() {
        if (level == null) return false;
        boolean changed = false;
        for (Wire w : wires) {
            Direction[] ds = w.dirs();
            int[] conn = new int[4], plug = new int[4];
            boolean[] device = new boolean[4], unknown = new boolean[4];
            int links = 0;
            for (int i = 0; i < 4; i++) {
                Direction d = ds[i];
                Direction.Axis edge = Wire.edgeAxis(w.face, d);
                BlockPos n = worldPosition.relative(d);
                if (!level.isLoaded(n)) {
                    unknown[i] = true;       // сосед не загружен — оставляем как было
                    conn[i] = w.conn[i];
                    plug[i] = w.plug[i];
                } else if (d.getAxis().isHorizontal() && level.getBlockState(n).getBlock() instanceof LanDevice) {
                    device[i] = true;
                } else if (level.getBlockEntity(n) instanceof TrayBlockEntity tray) {
                    if (tray.has(w)) conn[i] = Wire.FLAT;
                } else if (match(level, worldPosition, d, w, edge) != null) {
                    conn[i] = Wire.INNER;
                } else if (match(level, n, w.face, w, edge) != null) {
                    conn[i] = Wire.FLAT;
                } else if (match(level, n.relative(w.face), d.getOpposite(), w, edge) != null) {
                    conn[i] = Wire.OUTER;
                }
                if (conn[i] != Wire.NONE) links++;
            }
            // Куда втыкаться: по ходу провода, иначе сбоку; провод без соединений — в одно или два устройства напротив
            List<Integer> targets = new ArrayList<>();
            if (links == 1) {
                int c = 0;
                while (conn[c] == Wire.NONE) c++;
                if (device[c ^ 1]) targets.add(c ^ 1);
                else for (int i = 0; i < 4; i++) if (device[i] && (i >> 1) != (c >> 1)) {
                    targets.add(i);
                    break;
                }
            } else if (links == 0) {
                for (int pair = 0; pair < 2 && targets.isEmpty(); pair++) {
                    for (int i = pair * 2; i < pair * 2 + 2; i++) if (device[i]) targets.add(i);
                }
            }
            for (int i : targets) {
                plug[i] = choosePort(w, i, worldPosition.relative(ds[i]));
                if (plug[i] != 0) LanManager.updateDevice(level, worldPosition.relative(ds[i]));
            }
            for (int i = 0; i < 4; i++) {
                if (conn[i] != w.conn[i] || plug[i] != w.plug[i]) {
                    w.conn[i] = conn[i];
                    w.plug[i] = plug[i];
                    changed = true;
                }
            }
        }
        if (changed) changed();
        return changed;
    }

    /**
     * Порт устройства в клетке dev для провода w (сторона i): прежний, если гнездо на месте и не занято другим
     * проводом этой клетки, иначе ближайшее свободное гнездо подходящего вида на обращённой к нам стороне. 0 — некуда.
     */
    private int choosePort(Wire w, int i, BlockPos dev) {
        Direction d = w.dirs()[i];
        List<LanDevice.Socket> sockets = LanDevice.socketsAt(level, dev);
        boolean[] taken = new boolean[sockets.size()];
        for (Wire o : wires) {
            if (o == w) continue;
            int k = o.index(d);
            if (k >= 0 && o.port(k) >= 0 && o.port(k) < taken.length) taken[o.port(k)] = true;
        }
        int old = w.port(i);
        if (old >= 0 && old < sockets.size() && !taken[old] && fits(sockets.get(old), d, w)) return old + 1;
        // Где провод подходит к стороне устройства: по оси вдоль стороны и по высоте
        Direction.Axis along = d.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        float wa = across(w, along)[0], wy = across(w, Direction.Axis.Y)[0];
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int s = 0; s < sockets.size(); s++) {
            if (taken[s] || !fits(sockets.get(s), d, w)) continue;
            LanDevice.Socket sk = sockets.get(s);
            float dist = Math.abs(sk.along() - wa) + 0.25f * Math.abs(sk.height() - wy);
            if (dist < bestD) {
                bestD = dist;
                best = s;
            }
        }
        return best + 1;
    }

    private static boolean fits(LanDevice.Socket s, Direction d, Wire w) {
        return s.face() == d.getOpposite() && s.accepts(w.fiber);
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

    /** Все клетки кабеля вокруг (в том числе по диагонали — внешние углы). */
    public void reconnectNeighbours() {
        if (level != null) reconnectAround(level, worldPosition);
    }

    public static void reconnectAround(Level level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos n = pos.offset(dx, dy, dz);
                    if (level.isLoaded(n) && level.getBlockEntity(n) instanceof CableBlockEntity other) other.reconnect();
                }
            }
        }
    }

    public long[] packed() {
        long[] packed = new long[wires.size()];
        for (int i = 0; i < packed.length; i++) packed[i] = wires.get(i).packed();
        return packed;
    }

    /** Загрузился чанк: занести провода в схему сети; у проводов из старых миров выбрать порты. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof net.minecraft.server.level.ServerLevel server && !wires.isEmpty()) {
            boolean pending = false;
            for (Wire w : wires) for (int p : w.plug) if (p == Wire.PLUG_PENDING) pending = true;
            if (pending) server.getServer().execute(this::reconnect);
            LanData.get(server).putCable(worldPosition, packed());
        }
    }

    /** Где гнёзда для вилок: по 4 числа на провод — x/z вдоль стороны, высота, утопленность (в 1/4 пикселя). */
    private int[] sockets() {
        int[] out = new int[wires.size() * 4];
        java.util.Arrays.fill(out, -1);
        if (level == null) return out;
        for (int k = 0; k < wires.size(); k++) {
            Wire w = wires.get(k);
            for (int i = 0; i < 4; i++) {
                int port = w.port(i);
                if (port < 0) continue;
                List<LanDevice.Socket> s = LanDevice.socketsAt(level, worldPosition.relative(w.dirs()[i]));
                if (port >= s.size()) continue;
                LanDevice.Socket sk = s.get(port);
                out[k * 4 + i] = Math.round(sk.along() * 4) | Math.round(sk.height() * 4) << 10 | Math.round(sk.depth() * 4) << 20;
            }
        }
        return out;
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(WIRES, packed()).with(SOCKETS, sockets()).with(FACADE, facade).build();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLongArray("w2", packed());
        if (facade != null) tag.put("facade", net.minecraft.nbt.NbtUtils.writeBlockState(facade));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wires.clear();
        if (tag.contains("w2")) {
            for (long p : tag.getLongArray("w2")) wires.add(Wire.unpack(p));
        } else {
            // Мир до 0.22: провода только на полу, 4 дорожки
            for (Tag t : tag.getList("wires", Tag.TAG_COMPOUND)) wires.addAll(Wire.load((CompoundTag) t));
        }
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
