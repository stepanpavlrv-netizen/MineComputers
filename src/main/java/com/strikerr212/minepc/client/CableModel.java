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
 * Модель блока кабеля: собирается из проводов, которые лежат в блоке (до 4), — узел, отрезки до краёв и вилки
 * у устройств. Провода красятся цветом через tintindex = номер красителя (0..15), вилки — нет.
 */
public class CableModel extends BakedModelWrapper<BakedModel> {

    private static final ResourceLocation WIRE = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "block/network_cable");
    private static final ResourceLocation PLUG = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "block/network_plug");
    private static final FaceBakery BAKERY = new FaceBakery();
    private static final Map<String, List<BakedQuad>> CACHE = new ConcurrentHashMap<>();

    public CableModel(BakedModel base) {
        super(base);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        if (side != null) return List.of();
        int[] wires = data.get(CableBlockEntity.WIRES);
        if (wires == null || wires.length == 0) return List.of();
        return CACHE.computeIfAbsent(Arrays.toString(wires), k -> build(wires));
    }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    private static List<BakedQuad> build(int[] packed) {
        TextureAtlasSprite wire = sprite(WIRE), plug = sprite(PLUG);
        List<BakedQuad> out = new ArrayList<>();
        for (int p : packed) {
            Wire w = Wire.unpack(p);
            for (int[] b : CableBlockEntity.boxes(w)) box(out, b[0], b[1], b[2], b[3], b[4], b[5], wire, w.color);
            int cx = Wire.LANE[w.xi], cz = Wire.LANE[w.zi];
            // Вилка RJ-45: прозрачный пластик у самого гнезда
            if ((w.plugs & Wire.E) != 0) box(out, 13, 0, cz - 1.5f, 16, 3, cz + 1.5f, plug, -1);
            if ((w.plugs & Wire.W) != 0) box(out, 0, 0, cz - 1.5f, 3, 3, cz + 1.5f, plug, -1);
            if ((w.plugs & Wire.S) != 0) box(out, cx - 1.5f, 0, 13, cx + 1.5f, 3, 16, plug, -1);
            if ((w.plugs & Wire.N) != 0) box(out, cx - 1.5f, 0, 0, cx + 1.5f, 3, 3, plug, -1);
        }
        return List.copyOf(out);
    }

    /** Коробка (в пикселях): все грани, кроме нижней; развёртка текстуры — по координатам, как у обычной модели. */
    private static void box(List<BakedQuad> out, float x0, float y0, float z0, float x1, float y1, float z1,
                            TextureAtlasSprite sprite, int tint) {
        Vector3f from = new Vector3f(x0, y0, z0), to = new Vector3f(x1, y1, z1);
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            // Полоска на стенке выходит на 2 пикселя в клетку выше — развёртку держим в пределах текстуры
            float y0t = Math.min(y0, 16), y1t = Math.min(y1, 16);
            float[] uv = switch (d) {
                case UP -> new float[]{x0, z0, x1, z1};
                case NORTH -> new float[]{16 - x1, 16 - y1t, 16 - x0, 16 - y0t};
                case SOUTH -> new float[]{x0, 16 - y1t, x1, 16 - y0t};
                case WEST -> new float[]{z0, 16 - y1t, z1, 16 - y0t};
                case EAST -> new float[]{16 - z1, 16 - y1t, 16 - z0, 16 - y0t};
                default -> new float[]{x0, z0, x1, z1};
            };
            BlockElementFace face = new BlockElementFace(null, tint, "#all", new BlockFaceUV(uv, 0));
            out.add(BAKERY.bakeQuad(from, to, face, sprite, d, BlockModelRotation.X0_Y0, null, true));
        }
    }

    @Override
    public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level, net.minecraft.core.BlockPos pos,
                                  BlockState state, ModelData modelData) {
        return modelData;
    }
}
