package com.strikerr212.minepc.client;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.TrayBlockEntity;
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

/** Лоток: сетчатый короб по направлениям соединений, внутри — цветные провода (tintindex = краситель). */
public class TrayModel extends BakedModelWrapper<BakedModel> {

    private static final ResourceLocation MESH = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "block/cable_tray");
    private static final ResourceLocation WIRE = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "block/network_cable");
    private static final ResourceLocation FIBER = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "block/network_fiber");
    private static final FaceBakery BAKERY = new FaceBakery();
    private static final Map<String, List<BakedQuad>> CACHE = new ConcurrentHashMap<>();

    public TrayModel(BakedModel base) {
        super(base);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        if (side != null) return List.of();
        int[] wires = data.get(TrayBlockEntity.WIRES);
        Integer conn = data.get(TrayBlockEntity.CONN);
        if (wires == null || conn == null) return super.getQuads(state, side, rand, data, renderType);
        String key = conn + ":" + Arrays.toString(wires);
        return CACHE.computeIfAbsent(key, k -> build(conn, wires));
    }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    private static boolean has(int conn, Direction d) {
        return (conn & 1 << d.ordinal()) != 0;
    }

    private static List<BakedQuad> build(int conn, int[] wires) {
        TextureAtlasSprite mesh = sprite(MESH), copper = sprite(WIRE), fiber = sprite(FIBER);
        List<BakedQuad> out = new ArrayList<>();
        if (conn == 0) conn = 1 << Direction.WEST.ordinal() | 1 << Direction.EAST.ordinal();
        // короб: середина и рукава
        box(out, 4, 4, 4, 12, 12, 12, mesh, -1);
        for (Direction d : Direction.values()) {
            if (!has(conn, d)) continue;
            float[] b = arm(d, 4, 12);
            box(out, b[0], b[1], b[2], b[3], b[4], b[5], mesh, -1);
        }
        // провода по дну короба (у вертикальных рукавов — по северной стенке)
        for (int i = 0; i < wires.length; i++) {
            int w = wires[i];
            TextureAtlasSprite tex = (w & 16) != 0 ? fiber : copper;
            int color = w & 15;
            float o = 4.5f + i * 0.875f;
            for (Direction d : Direction.values()) {
                if (!has(conn, d)) continue;
                switch (d) {
                    case EAST -> box(out, 8, 4.2f, o, 16, 4.9f, o + 0.7f, tex, color);
                    case WEST -> box(out, 0, 4.2f, o, 8, 4.9f, o + 0.7f, tex, color);
                    case SOUTH -> box(out, o, 4.2f, 8, o + 0.7f, 4.9f, 16, tex, color);
                    case NORTH -> box(out, o, 4.2f, 0, o + 0.7f, 4.9f, 8, tex, color);
                    case UP -> box(out, o, 4.2f, 4.2f, o + 0.7f, 16, 4.9f, tex, color);
                    case DOWN -> box(out, o, 0, 4.2f, o + 0.7f, 4.9f, 4.9f, tex, color);
                }
            }
        }
        return List.copyOf(out);
    }

    private static float[] arm(Direction d, float lo, float hi) {
        float x0 = lo, y0 = lo, z0 = lo, x1 = hi, y1 = hi, z1 = hi;
        switch (d) {
            case DOWN -> { y0 = 0; y1 = lo; }
            case UP -> { y0 = hi; y1 = 16; }
            case NORTH -> { z0 = 0; z1 = lo; }
            case SOUTH -> { z0 = hi; z1 = 16; }
            case WEST -> { x0 = 0; x1 = lo; }
            case EAST -> { x0 = hi; x1 = 16; }
        }
        return new float[]{x0, y0, z0, x1, y1, z1};
    }

    private static void box(List<BakedQuad> out, float x0, float y0, float z0, float x1, float y1, float z1,
                            TextureAtlasSprite sprite, int tint) {
        Vector3f from = new Vector3f(x0, y0, z0), to = new Vector3f(x1, y1, z1);
        for (Direction d : Direction.values()) {
            float[] uv = switch (d) {
                case UP, DOWN -> new float[]{x0, z0, x1, z1};
                case NORTH -> new float[]{16 - x1, 16 - y1, 16 - x0, 16 - y0};
                case SOUTH -> new float[]{x0, 16 - y1, x1, 16 - y0};
                case WEST -> new float[]{z0, 16 - y1, z1, 16 - y0};
                case EAST -> new float[]{16 - z1, 16 - y1, 16 - z0, 16 - y0};
            };
            BlockElementFace face = new BlockElementFace(null, tint, "#all", new BlockFaceUV(uv, 0));
            out.add(BAKERY.bakeQuad(from, to, face, sprite, d, BlockModelRotation.X0_Y0, null, true));
        }
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return sprite(MESH);
    }
}
