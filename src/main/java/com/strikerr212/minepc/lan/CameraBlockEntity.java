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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Камера наблюдения: адрес в сети, поворотная голова (влево-вправо, вверх-вниз), зум, разрешение и частота кадров,
 * ночное видение, патруль (сама водит головой). Пока кто-то смотрит, сервер держит снимок мира вокруг камеры —
 * по нему компьютеры и окно камеры рисуют кадр лучами ({@link CameraRender}).
 */
public class CameraBlockEntity extends BlockEntity implements NetEndpoint, CordList.Holder {

    /** Разрешения: от 64×36 до 320×180 (больше — не влезет в экран лучшей видеокарты). */
    public static final int[][] RES = {{64, 36}, {96, 54}, {128, 72}, {160, 90}, {192, 108}, {256, 144}, {320, 180}};
    public static final int[] FPS = {5, 10, 15, 20, 30};
    public static final float FOV_MIN = 20, FOV_MAX = 90;
    /** Радиус снимка: 40 блоков вбок, 24 вверх и вниз. */
    public static final int RADIUS = 40, HEIGHT = 24;
    public static final float MAX_DIST = 40;

    private String address = "";
    private String name = "";
    /** Поворот головы относительно крепления, градусы. */
    private float yaw, pitch = -15;
    private float fov = 70;
    private int res = 3, fps = 1;
    private boolean night, patrol;
    private int patrolDir = 1;
    private final CordList cords = new CordList();

    // ----- снимок мира (только сервер) -----
    private volatile CameraRender.Snapshot snapshot;
    private int buildLayer;
    private boolean snapshotComplete;
    private long lastWatched = -100000;
    private int age;
    /** Сколько зрителей смотрело за последние секунды (для лампы «идёт запись»). */
    private boolean live;

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA.get(), pos, state);
    }

    @Override
    public CordList cords() {
        return cords;
    }

    // ======================= Сеть =======================

    @Override
    public String address() {
        if (address.isEmpty()) {
            address = String.format(Locale.ROOT, "%08x", new java.util.Random().nextInt());
            setChanged();
        }
        return address;
    }

    @Override
    public String label() {
        return name;
    }

    public void setLabel(String label) {
        name = label.length() > LanData.MAX_LABEL ? label.substring(0, LanData.MAX_LABEL) : label;
        changed();
    }

    @Override
    public boolean netOnline() {
        return true;
    }

    /** Камере сообщения не нужны: управление — через camera.* у компьютера. */
    @Override
    public void netReceive(String from, int port, int hops, Object[] payload, int bytes) {
    }

    // ======================= Поворот и настройки =======================

    public AttachFace face() {
        BlockState s = getBlockState();
        return s.hasProperty(CameraBlock.FACE) ? s.getValue(CameraBlock.FACE) : AttachFace.WALL;
    }

    /** Куда смотрит крепление (градусы мира: 0 — север, 90 — восток). */
    public float baseYaw() {
        BlockState s = getBlockState();
        Direction f = s.hasProperty(CameraBlock.FACING) ? s.getValue(CameraBlock.FACING) : Direction.NORTH;
        // на полу и потолке камера ставится «лицом» к тому, кто её ставит
        if (face() != AttachFace.WALL) f = f.getOpposite();
        return switch (f) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    public float yawLimit() {
        return face() == AttachFace.WALL ? 80 : 180;
    }

    public float pitchMin() {
        return switch (face()) {
            case CEILING -> -85;
            case FLOOR -> -30;
            default -> -70;
        };
    }

    public float pitchMax() {
        return switch (face()) {
            case CEILING -> 10;
            case FLOOR -> 60;
            default -> 40;
        };
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    /** Куда смотрит объектив в мире. */
    public float worldYaw() {
        return baseYaw() + yaw;
    }

    public float fov() {
        return fov;
    }

    public int resIndex() {
        return res;
    }

    public int fpsIndex() {
        return fps;
    }

    public int width() {
        return RES[res][0];
    }

    public int height() {
        return RES[res][1];
    }

    public int frameRate() {
        return FPS[fps];
    }

    public boolean night() {
        return night;
    }

    public boolean patrol() {
        return patrol;
    }

    public boolean live() {
        return live;
    }

    public void look(float y, float p) {
        float lim = yawLimit();
        if (lim >= 180) {
            y = ((y + 180) % 360 + 360) % 360 - 180;
        } else {
            y = Math.max(-lim, Math.min(lim, y));
        }
        yaw = y;
        pitch = Math.max(pitchMin(), Math.min(pitchMax(), p));
        changed();
    }

    public void turn(float dy, float dp) {
        look(yaw + dy, pitch + dp);
    }

    public void setFov(float f) {
        fov = Math.max(FOV_MIN, Math.min(FOV_MAX, f));
        changed();
    }

    public void setRes(int i) {
        res = Math.max(0, Math.min(RES.length - 1, i));
        changed();
    }

    public void setFps(int i) {
        fps = Math.max(0, Math.min(FPS.length - 1, i));
        changed();
    }

    public void setNight(boolean v) {
        night = v;
        changed();
    }

    public void setPatrol(boolean v) {
        patrol = v;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    /** Точка объектива в мире (шарнир + немного вперёд). */
    public double[] eye() {
        double[] j = CameraBlock.joint(getBlockState());
        double yr = Math.toRadians(worldYaw()), pr = Math.toRadians(pitch);
        double fx = Math.sin(yr) * Math.cos(pr), fy = Math.sin(pr), fz = -Math.cos(yr) * Math.cos(pr);
        return new double[]{worldPosition.getX() + j[0] + fx * 0.32, worldPosition.getY() + j[1] + fy * 0.32 - 0.03,
                worldPosition.getZ() + j[2] + fz * 0.32};
    }

    // ======================= Снимок и кадр =======================

    /** Кто-то смотрит: снимок держится свежим ещё 10 секунд. */
    public void watched() {
        if (level != null) lastWatched = level.getGameTime();
    }

    public CameraRender.Snapshot snapshot() {
        return snapshot;
    }

    /** Кадр w×h (любой поток). null — снимок ещё не готов. */
    public int[] render(int w, int h) {
        CameraRender.Snapshot s = snapshot;
        if (s == null) return null;
        int[] out = new int[w * h];
        double[] e = eye();
        CameraRender.render(s, e[0], e[1], e[2], worldYaw(), pitch, fov, night, out, w, h, MAX_DIST);
        return out;
    }

    /** Кадр для окна уже рисуется — следующий запрос подождёт (не копим очередь). */
    public final java.util.concurrent.atomic.AtomicBoolean framing = new java.util.concurrent.atomic.AtomicBoolean();

    /** Кадры для окон камер рисуются здесь, а не в потоке сервера. */
    public static final java.util.concurrent.ExecutorService RENDER = java.util.concurrent.Executors.newFixedThreadPool(
            Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 2)), r -> {
                Thread t = new Thread(r, "MineComputers camera");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });

    public static void serverTick(Level level, BlockPos pos, BlockState state, CameraBlockEntity be) {
        be.age++;
        long now = level.getGameTime();
        boolean active = now - be.lastWatched < 200;
        if (active != be.live) {
            be.live = active;
            be.changed();
        }
        if (be.patrol) {
            float lim = Math.min(60, be.yawLimit());
            be.yaw += be.patrolDir * 0.75f;
            if (be.yaw > lim) {
                be.yaw = lim;
                be.patrolDir = -1;
            } else if (be.yaw < -lim) {
                be.yaw = -lim;
                be.patrolDir = 1;
            }
            if (be.age % 4 == 0) be.changed();
        }
        if (!active) {
            if (now - be.lastWatched > 1200 && be.snapshot != null) {
                be.snapshot = null;        // никто не смотрит минуту — память отдаём
                be.snapshotComplete = false;
            }
            return;
        }
        if (!(level instanceof ServerLevel server)) return;
        if (be.snapshot == null) {
            be.snapshot = new CameraRender.Snapshot(pos.getX() - RADIUS, pos.getY() - HEIGHT, pos.getZ() - RADIUS,
                    RADIUS * 2 + 1, HEIGHT * 2 + 1, RADIUS * 2 + 1);
            be.buildLayer = 0;
            be.snapshotComplete = false;
        }
        CameraRender.Snapshot s = be.snapshot;
        // пока снимок не собран — по 6 слоёв за тик, потом по одному (весь куб обновляется за ~2,5 с)
        int layers = be.snapshotComplete ? 1 : 6;
        for (int k = 0; k < layers; k++) {
            be.buildSlice(server, s, be.buildLayer);
            be.buildLayer++;
            if (be.buildLayer >= s.sy) {
                be.buildLayer = 0;
                be.snapshotComplete = true;
            }
        }
        if (be.age % 2 == 0) be.snapEntities(server, s);
        float day = 1f - server.getSkyDarken() / 11f;
        s.daylight = Math.max(0.05f, day);
        s.sky = level.isRaining() ? 0x8A96A6 : 0x87B4F0;
        s.tex = BlockTextures.table();
        // солнце: восходит на востоке, в полдень — над головой; тени пересчитываются раз в 5 с
        double a = level.getSunAngle(1f);
        s.sunX = (float) -Math.sin(a);
        s.sunY = (float) Math.cos(a);
        s.sunZ = 0.12f;
        float len = (float) Math.sqrt(s.sunX * s.sunX + s.sunY * s.sunY + s.sunZ * s.sunZ);
        s.sunX /= len;
        s.sunY /= len;
        s.sunZ /= len;
        if (be.age % 100 == 0) s.resetShadows();
    }

    private void buildSlice(ServerLevel level, CameraRender.Snapshot s, int y) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int wy = s.oy + y;
        CameraRender.Tex[] table = BlockTextures.table();
        for (int z = 0; z < s.sz; z++) {
            for (int x = 0; x < s.sx; x++) {
                p.set(s.ox + x, wy, s.oz + z);
                int idx = s.index(x, y, z);
                if (!level.isLoaded(p)) {
                    s.cells[idx] = 0;
                    s.blocks[idx] = 0;
                    s.shape[idx] = 0;
                    s.light[idx] = 0;
                    continue;
                }
                BlockState st = level.getBlockState(p);
                int cell = 0, shape = 0;
                char block = 0;
                if (!st.isAir() && !(st.getBlock() instanceof CameraBlock)) {   // камеры (и своя тоже) не заслоняют вид
                    FluidState fl = st.getFluidState();
                    int id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(st.getBlock());
                    CameraRender.Tex tex = id >= 0 && id < table.length ? table[id] : null;
                    if (!fl.isEmpty() && (fl.is(Fluids.WATER) || fl.is(Fluids.FLOWING_WATER))
                            && (tex == null || st.getCollisionShape(level, p).isEmpty())) {
                        cell = 0x3F76E4 | CameraRender.SOLID | CameraRender.WATER;
                    } else if (!fl.isEmpty() && tex == null) {
                        cell = 0xD96415 | CameraRender.SOLID;                  // лава
                    } else if (tex != null && tex.cross) {
                        cell = tex.average() | CameraRender.PLANT;             // трава, цветы — крестом
                        block = (char) (id + 1);
                    } else {
                        VoxelShape col = st.getCollisionShape(level, p);
                        VoxelShape outline = col.isEmpty() ? st.getShape(level, p) : col;
                        boolean leaves = st.is(net.minecraft.tags.BlockTags.LEAVES);
                        // блок виден: твёрдый, листва — или у него есть текстура и форма (факел, рельсы, снег)
                        if (!col.isEmpty() || leaves || (tex != null && !outline.isEmpty())) {
                            MapColor mc = st.getMapColor(level, p);
                            int c = mc == MapColor.NONE ? 0 : mc.col;
                            if (tex != null) {
                                cell = (c != 0 ? c : tex.average()) | CameraRender.SOLID;
                                block = (char) (id + 1);
                            } else if (c != 0) {
                                cell = c | CameraRender.SOLID;          // стекло без текстур (без цвета) — прозрачное
                            }
                            if (cell != 0 && !leaves && !outline.isEmpty()) shape = shapeOf(outline.bounds());
                        }
                    }
                }
                s.cells[idx] = cell;
                s.blocks[idx] = block;
                s.shape[idx] = shape;
                int bl = level.getBrightness(LightLayer.BLOCK, p), sk = level.getBrightness(LightLayer.SKY, p);
                s.light[idx] = (byte) (bl << 4 | sk);
            }
        }
    }

    /** Коробка блока в шестнадцатых; 0 — целый блок. */
    private static int shapeOf(AABB b) {
        int x0 = clamp16((int) Math.floor(b.minX * 16 + 1e-4)), y0 = clamp16((int) Math.floor(b.minY * 16 + 1e-4)), z0 = clamp16((int) Math.floor(b.minZ * 16 + 1e-4));
        int x1 = clamp16((int) Math.ceil(b.maxX * 16 - 1e-4) - 1), y1 = clamp16((int) Math.ceil(b.maxY * 16 - 1e-4) - 1), z1 = clamp16((int) Math.ceil(b.maxZ * 16 - 1e-4) - 1);
        if (x0 == 0 && y0 == 0 && z0 == 0 && x1 == 15 && y1 == 15 && z1 == 15) return 0;
        return CameraRender.packShape(x0, y0, z0, Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1));
    }

    private static int clamp16(int v) {
        return Math.max(0, Math.min(15, v));
    }

    private void snapEntities(ServerLevel level, CameraRender.Snapshot s) {
        AABB area = new AABB(s.ox, s.oy, s.oz, s.ox + s.sx, s.oy + s.sy, s.oz + s.sz);
        List<Entity> list = level.getEntities((Entity) null, area, e -> !e.isSpectator() && !e.isInvisible());
        EntityParts parts = new EntityParts();
        int n = 0;
        for (Entity e : list) {
            if (n++ >= 64) break;
            parts.add(e);
        }
        s.entities = parts.toArray();
    }

    /**
     * Существа для кадра — простыми фигурами из частей: голова, тело, ноги (у животных — четыре и голова впереди),
     * цветами яйца призыва (пятна — вторым цветом). Игрок — в цветах Стива.
     */
    private static final class EntityParts {
        private float[] out = new float[256];
        private int len;

        private void put(float v) {
            if (len == out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
            out[len++] = v;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(out, len);
        }

        private int head;
        private int count;

        private void begin(AABB b) {
            head = len;
            put((float) b.minX);
            put((float) b.minY);
            put((float) b.minZ);
            put((float) b.maxX);
            put((float) b.maxY);
            put((float) b.maxZ);
            put(0);
            count = 0;
        }

        private void end() {
            out[head + 6] = count;
        }

        /** Часть: коробка с центром (cx, cz) по горизонтали, размерами along × across (вдоль взгляда и поперёк), по высоте y0..y1. */
        private void part(double cx, double cz, double along, double across, double y0, double y1, boolean alongX, int col, int col2, int pattern) {
            double hx = (alongX ? along : across) / 2, hz = (alongX ? across : along) / 2;
            put((float) (cx - hx));
            put((float) y0);
            put((float) (cz - hz));
            put((float) (cx + hx));
            put((float) y1);
            put((float) (cz + hz));
            put(col);
            put(col2);
            put(pattern);
            count++;
        }

        void add(Entity e) {
            AABB b = e.getBoundingBox();
            double w = b.getXsize(), h = b.getYsize();
            double cx = (b.minX + b.maxX) / 2, cz = (b.minZ + b.maxZ) / 2, y = b.minY;
            float yaw = e instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : e.getYRot();
            double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
            boolean alongX = Math.abs(fx) > Math.abs(fz);
            double sx = alongX ? Math.signum(fx) : 0, sz = alongX ? 0 : Math.signum(fz);
            int c1 = 0x9A9A9A, c2 = 0x6A6A6A;
            var egg = net.minecraft.world.item.SpawnEggItem.byId(e.getType());
            if (egg != null) {
                c1 = egg.getColor(0) & 0xFFFFFF;
                c2 = egg.getColor(1) & 0xFFFFFF;
            }
            // дальний край коробки для рамки: голова животного торчит вперёд
            begin(b.inflate(0.4, 0.05, 0.4));
            if (e instanceof Player) {
                humanoid(cx, cz, y, h, w, alongX, 0xC8946A, 0x3AAFA9, 0x46409A, PLAIN);
            } else if (e instanceof ItemEntity item) {
                int col = 0xE8C840;
                if (item.getItem().getItem() instanceof net.minecraft.world.item.BlockItem bi) {
                    MapColor m = bi.getBlock().defaultMapColor();
                    if (m != MapColor.NONE) col = m.col;
                }
                part(cx, cz, 0.25, 0.25, y, y + 0.25, alongX, col, col, PLAIN);
            } else if (e instanceof net.minecraft.world.entity.monster.Creeper) {
                part(cx, cz, 0.5, 0.5, y + h * 0.75, y + h, alongX, c1, c2, SPOTS);
                part(cx, cz, 0.3, 0.5, y + h * 0.3, y + h * 0.75, alongX, c1, c2, SPOTS);
                part(cx, cz, 0.5, 0.5, y, y + h * 0.3, alongX, c1, c2, SPOTS);
            } else if (e instanceof net.minecraft.world.entity.LivingEntity && h >= 1.3 && h >= w * 2.2) {
                // человекоподобные: зомби, скелет, житель, эндермен…
                humanoid(cx, cz, y, h, w, alongX, c2, c1, shade(c1, 0.7f), PLAIN);
            } else if (e instanceof net.minecraft.world.entity.LivingEntity && h < 1.8) {
                // четвероногие: тело, четыре ноги, голова впереди
                double legH = h * 0.42, len = Math.max(w, 0.4) * 1.15;
                part(cx, cz, len, w, y + legH, y + h * 0.82, alongX, c1, c2, SPOTS);
                double lx = len * 0.32, lz = w * 0.28, leg = Math.min(0.28, w * 0.3);
                for (int k = 0; k < 4; k++) {
                    double a = (k < 2 ? 1 : -1) * lx, c = (k % 2 == 0 ? 1 : -1) * lz;
                    double px = cx + (alongX ? a : c), pz = cz + (alongX ? c : a);
                    part(px, pz, leg, leg, y, y + legH, alongX, shade(c1, 0.8f), c2, PLAIN);
                }
                double head = Math.min(0.55, h * 0.45);
                part(cx + sx * (len / 2 + head * 0.35), cz + sz * (len / 2 + head * 0.35), head, head * 0.95,
                        y + h - head, y + h + head * 0.05, alongX, c2 == 0 ? c1 : mixColor(c1, c2), c2, PLAIN);
            } else {
                part(cx, cz, w, w, y, y + h, alongX, c1, c2, PLAIN);
            }
            end();
        }

        private void humanoid(double cx, double cz, double y, double h, double w, boolean alongX, int headCol, int bodyCol, int legCol, int pat) {
            double u = h / 1.8;   // рост в долях роста игрока
            double hs = 0.5 * u;
            part(cx, cz, hs, hs, y + h - hs, y + h, alongX, headCol, headCol, pat);
            part(cx, cz, 0.28 * u, 0.5 * u, y + h * 0.375, y + h - hs, alongX, bodyCol, bodyCol, pat);
            // руки по бокам тела
            double side = 0.375 * u;
            double ax = alongX ? 0 : side, az = alongX ? side : 0;
            part(cx + ax, cz + az, 0.24 * u, 0.24 * u, y + h * 0.42, y + h - hs, alongX, headCol, headCol, pat);
            part(cx - ax, cz - az, 0.24 * u, 0.24 * u, y + h * 0.42, y + h - hs, alongX, headCol, headCol, pat);
            part(cx, cz, 0.26 * u, 0.48 * u, y, y + h * 0.375, alongX, legCol, legCol, pat);
        }

        private static final int PLAIN = CameraRender.PATTERN_PLAIN, SPOTS = CameraRender.PATTERN_SPOTS;

        private static int shade(int c, float k) {
            return (int) (((c >> 16) & 255) * k) << 16 | (int) (((c >> 8) & 255) * k) << 8 | (int) ((c & 255) * k);
        }

        private static int mixColor(int a, int b) {
            return (((a >> 16) & 255) + ((b >> 16) & 255)) / 2 << 16 | (((a >> 8) & 255) + ((b >> 8) & 255)) / 2 << 8 | ((a & 255) + (b & 255)) / 2;
        }
    }

    /** Таблица для программ. */
    public java.util.Map<Object, Object> info() {
        java.util.Map<Object, Object> m = new java.util.LinkedHashMap<>();
        m.put("address", address());
        m.put("name", name);
        m.put("yaw", (double) yaw);
        m.put("pitch", (double) pitch);
        m.put("fov", (double) fov);
        m.put("width", width());
        m.put("height", height());
        m.put("fps", frameRate());
        m.put("night", night);
        m.put("patrol", patrol);
        m.put("res", res + 1);
        m.put("mount", face().getSerializedName());
        m.put("yawMin", (double) -yawLimit());
        m.put("yawMax", (double) yawLimit());
        m.put("pitchMin", (double) pitchMin());
        m.put("pitchMax", (double) pitchMax());
        return m;
    }

    /** Поток видео, байт в секунду (сжатый кадр — примерно полбайта на точку). */
    public long bytesPerSecond() {
        return (long) width() * height() / 2 * frameRate();
    }

    // ======================= Сохранение =======================

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LanManager.updateDevice(level, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Address", address());
        tag.putString("Name", name);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putFloat("Fov", fov);
        tag.putInt("Res", res);
        tag.putInt("Fps", fps);
        tag.putBoolean("Night", night);
        tag.putBoolean("Patrol", patrol);
        tag.putBoolean("Live", live);
        cords.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        address = tag.getString("Address");
        name = tag.getString("Name");
        yaw = tag.getFloat("Yaw");
        pitch = tag.contains("Pitch") ? tag.getFloat("Pitch") : -15;
        fov = tag.contains("Fov") ? tag.getFloat("Fov") : 70;
        res = tag.contains("Res") ? tag.getInt("Res") : 3;
        fps = tag.contains("Fps") ? tag.getInt("Fps") : 1;
        night = tag.getBoolean("Night");
        patrol = tag.getBoolean("Patrol");
        live = tag.getBoolean("Live");
        cords.load(tag);
    }

    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!name.isEmpty()) components.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        var n = input.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (n != null) name = n.getString();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ----- для клиента: плавный поворот головы -----
    public float renderYaw = Float.NaN, renderPitch;
}
