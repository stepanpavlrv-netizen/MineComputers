package com.strikerr212.minepc.client;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.CableBlockEntity;
import com.strikerr212.minepc.lan.Wire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Модель блока кабеля: собирается из проводов, которые лежат на гранях клетки, — узел, отрезки до краёв и вилки
 * в гнёздах устройств. Провода красятся цветом через tintindex = номер красителя (0..15), вилки — нет.
 */
public class CableModel extends BakedModelWrapper<BakedModel> {

    private static final ResourceLocation WIRE = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "block/network_cable");
    private static final ResourceLocation PLUG = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "block/network_plug");
    private static final ResourceLocation FIBER = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "block/network_fiber");
    private static final ResourceLocation FIBER_PLUG = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "block/network_fiber_plug");
    private static final ResourceLocation TAG = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "block/cable_tag");
    private static final FaceBakery BAKERY = new FaceBakery();
    private static final Map<String, List<BakedQuad>> CACHE = new ConcurrentHashMap<>();

    public CableModel(BakedModel base) {
        super(base);
    }

    /** Модель блока-маскировки (кабель спрятан внутри). */
    private static BakedModel facadeModel(BlockState facade) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(facade);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        BlockState facade = data.get(CableBlockEntity.FACADE);
        if (facade != null) {
            List<BakedQuad> quads = facadeModel(facade).getQuads(facade, side, rand, ModelData.EMPTY, renderType);
            // Окраска травы и листвы шла бы через цвета кабеля — у маскировки её убираем
            List<BakedQuad> out = new ArrayList<>(quads.size());
            for (BakedQuad q : quads) {
                out.add(q.isTinted() ? new BakedQuad(q.getVertices(), -1, q.getDirection(), q.getSprite(), q.isShade()) : q);
            }
            return out;
        }
        if (side != null) return List.of();
        long[] wires = data.get(CableBlockEntity.WIRES);
        if (wires == null || wires.length == 0) return List.of();
        int[] sockets = data.get(CableBlockEntity.SOCKETS);
        String key = Arrays.toString(wires) + Arrays.toString(sockets);
        return CACHE.computeIfAbsent(key, k -> build(wires, sockets));
    }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    private static List<BakedQuad> build(long[] packed, int[] sockets) {
        if (CACHE.size() > 4096) CACHE.clear();
        TextureAtlasSprite copper = sprite(WIRE), copperPlug = sprite(PLUG), fiber = sprite(FIBER), fiberPlug = sprite(FIBER_PLUG), tag = sprite(TAG);
        List<BakedQuad> out = new ArrayList<>();
        for (int k = 0; k < packed.length; k++) {
            Wire w = Wire.unpack(packed[k]);
            TextureAtlasSprite wire = w.fiber ? fiber : copper, plug = w.fiber ? fiberPlug : copperPlug;
            for (float[] b : CableBlockEntity.boxes(w)) box(out, b[0], b[1], b[2], b[3], b[4], b[5], wire, w.color);
            Direction[] ds = w.dirs();
            for (int i = 0; i < 4; i++) {
                if (w.plug[i] == 0) continue;
                int s = sockets != null && k * 4 + i < sockets.length ? sockets[k * 4 + i] : -1;
                plugPath(out, w, ds[i], s, wire, plug);
                if (w.label != 0) tag(out, w, ds[i], tag);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Провод от точки, где он кончился (2–3 пикселя до края клетки), до гнезда устройства: сдвиг вдоль стороны
     * устройства, подъём до высоты гнезда и вилка, вставленная в гнездо (она может уходить в клетку устройства, если
     * устройство не во весь блок). Если гнездо неизвестно — вилка прямо у края клетки.
     */
    private static void plugPath(List<BakedQuad> out, Wire w, Direction d, int socket, TextureAtlasSprite wire, TextureAtlasSprite plug) {
        int ax = d.getAxis().ordinal();
        boolean pos = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        Direction.Axis[] fa = com.strikerr212.minepc.lan.LanDevice.faceAxes(d);
        int al = fa[0].ordinal(), y = fa[1].ordinal();
        // Где сейчас провод (поперёк направления к устройству)
        float[][] cur = new float[3][];
        for (Direction.Axis a : Direction.Axis.values()) {
            if (a == w.face.getAxis()) {
                cur[a.ordinal()] = w.face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? new float[]{15, 16} : new float[]{0, 1};
            } else {
                int p = Wire.LANE[w.lane[a.ordinal()]];
                cur[a.ordinal()] = new float[]{p, p + 1};
            }
        }
        float sAlong = cur[al][0], sY = cur[y][0], depth = 0;
        if (socket >= 0) {
            sAlong = (socket & 1023) / 4f;
            sY = ((socket >> 10) & 1023) / 4f;
            depth = ((socket >> 20) & 1023) / 4f;
        }
        // Отрезок «колена» — пиксели 13..14 (или 2..3) по направлению к устройству
        float[] knee = pos ? new float[]{13, 14} : new float[]{2, 3};
        float[][] r = new float[3][];
        // 1) провод доходит до колена
        r[ax] = pos ? new float[]{13, 14} : new float[]{2, 3};
        r[al] = cur[al];
        r[y] = cur[y];
        box(out, r, wire, w.color);
        // 2) вдоль стороны устройства — к гнезду
        if (Math.abs(cur[al][0] - sAlong) > 0.01f) {
            r[ax] = knee;
            r[al] = new float[]{Math.min(cur[al][0], sAlong), Math.max(cur[al][1], sAlong + 1)};
            r[y] = cur[y];
            box(out, r, wire, w.color);
        }
        // 3) вверх или вниз — к высоте гнезда
        if (Math.abs(cur[y][0] - sY) > 0.01f) {
            r[ax] = knee;
            r[al] = new float[]{sAlong, sAlong + 1};
            r[y] = new float[]{Math.min(cur[y][0], sY), Math.max(cur[y][1], sY + 1)};
            box(out, r, wire, w.color);
        }
        // 4) хвост провода и вилка до самого гнезда
        float end = pos ? 16 + depth : -depth;
        r[ax] = pos ? new float[]{14, end - 2} : new float[]{end + 2, 3};
        r[al] = new float[]{sAlong, sAlong + 1};
        r[y] = new float[]{sY, sY + 1};
        if (r[ax][1] > r[ax][0]) box(out, r, wire, w.color);
        r[ax] = pos ? new float[]{end - 2, end} : new float[]{end, end + 2};
        r[al] = new float[]{sAlong - 0.5f, sAlong + 1.5f};
        r[y] = new float[]{Math.max(0, sY - 0.5f), sY + 1.5f};
        box(out, r, plug, -1);
    }

    /**
     * Бирка у вилки: белая муфта на проводе у самого «колена» и флажок, торчащий от стены (пола, потолка) в комнату.
     */
    private static void tag(List<BakedQuad> out, Wire w, Direction d, TextureAtlasSprite sprite) {
        int ax = d.getAxis().ordinal(), n = w.face.getAxis().ordinal(), third = 3 - ax - n;
        boolean pos = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        boolean facePos = w.face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float[] along = pos ? new float[]{12.6f, 14.4f} : new float[]{1.6f, 3.4f};
        float layer = facePos ? 15 : 0;                                   // провод прижат к грани: слой 0..1 или 15..16
        float lane = Wire.LANE[w.lane[third]];
        float[][] r = new float[3][];
        // Муфта вокруг провода
        r[ax] = along;
        r[n] = new float[]{Math.max(0, layer - 0.4f), Math.min(16, layer + 1.4f)};
        r[third] = new float[]{lane - 0.4f, lane + 1.4f};
        box(out, r, sprite, -1);
        // Флажок в комнату
        r[n] = facePos ? new float[]{12.4f, 14.6f} : new float[]{1.4f, 3.6f};
        r[third] = new float[]{lane + 0.35f, lane + 0.65f};
        box(out, r, sprite, -1);
    }

    private static void box(List<BakedQuad> out, float[][] r, TextureAtlasSprite sprite, int tint) {
        box(out, r[0][0], r[1][0], r[2][0], r[0][1], r[1][1], r[2][1], sprite, tint);
    }

    /** Коробка (в пикселях): все шесть граней; развёртка текстуры — по координатам, как у обычной модели. */
    private static void box(List<BakedQuad> out, float x0, float y0, float z0, float x1, float y1, float z1,
                            TextureAtlasSprite sprite, int tint) {
        Vector3f from = new Vector3f(x0, y0, z0), to = new Vector3f(x1, y1, z1);
        // Части вилки уходят в клетку устройства — развёртку держим в пределах текстуры
        float u0 = clamp16(x0), u1 = clamp16(x1), v0 = clamp16(y0), v1 = clamp16(y1), w0 = clamp16(z0), w1 = clamp16(z1);
        for (Direction d : Direction.values()) {
            float[] uv = switch (d) {
                case UP, DOWN -> new float[]{u0, w0, Math.max(u1, u0 + 0.01f), Math.max(w1, w0 + 0.01f)};
                case NORTH -> new float[]{16 - u1, 16 - v1, 16 - u0, 16 - v0};
                case SOUTH -> new float[]{u0, 16 - v1, u1, 16 - v0};
                case WEST -> new float[]{w0, 16 - v1, w1, 16 - v0};
                default -> new float[]{16 - w1, 16 - v1, 16 - w0, 16 - v0};
            };
            BlockElementFace face = new BlockElementFace(null, tint, "#all", new BlockFaceUV(uv, 0));
            out.add(BAKERY.bakeQuad(from, to, face, sprite, d, BlockModelRotation.X0_Y0, null, true));
        }
    }

    private static float clamp16(float v) {
        return Math.max(0, Math.min(16, v));
    }

    @Override
    public net.neoforged.neoforge.client.ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        BlockState facade = data.get(CableBlockEntity.FACADE);
        return facade != null ? facadeModel(facade).getRenderTypes(facade, rand, ModelData.EMPTY) : super.getRenderTypes(state, rand, data);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState facade = data.get(CableBlockEntity.FACADE);
        return facade != null ? facadeModel(facade).getParticleIcon(ModelData.EMPTY) : super.getParticleIcon(data);
    }

    @Override
    public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level, net.minecraft.core.BlockPos pos,
                                  BlockState state, ModelData modelData) {
        return modelData;
    }
}
