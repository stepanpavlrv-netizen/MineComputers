package com.strikerr212.minepc.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.BlockTextures;
import com.strikerr212.minepc.network.CameraTexturesPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Собирает текстуры блоков для камер из моделей и ресурсов игры (с пакетами ресурсов): верх, бок и низ 16×16,
 * уже окрашенные (трава, листва — как в равнинах), растения крестом. Отдаёт серверу, когда тот попросит:
 * в одиночной игре — напрямую, иначе — кусками по сети.
 */
@EventBusSubscriber(modid = MineComputers.MODID, value = Dist.CLIENT)
public final class BlockTextureBaker {

    private BlockTextureBaker() {
    }

    private static volatile byte[] baked;
    private static volatile boolean baking;
    private static volatile boolean dirty = true;
    /** Сервер попросил, а таблица ещё не готова — отдать, как соберётся. */
    private static volatile boolean pending;

    /** Ресурсы перезагрузились — собрать заново. */
    public static void invalidate() {
        dirty = true;
    }

    /** Сервер просит таблицу. */
    public static void requested() {
        pending = true;
        if (baked != null && !dirty) deliver();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || baking || mc.getOverlay() != null) return;
        if (dirty || (pending && baked == null)) start(mc);
    }

    /** Что брать с грани: спрайты (по порядку слоёв) и их окраска. */
    private record Layer(ResourceLocation sprite, int tint) {
    }

    private record Job(String name, List<Layer> top, List<Layer> side, List<Layer> bottom, boolean cross) {
    }

    private static void start(Minecraft mc) {
        dirty = false;
        baking = true;
        List<Job> jobs = new ArrayList<>();
        RandomSource rnd = RandomSource.create(42);
        for (Block block : BuiltInRegistries.BLOCK) {
            try {
                BlockState st = block.defaultBlockState();
                if (st.isAir() || st.getRenderShape() != RenderShape.MODEL) continue;
                BakedModel model = mc.getBlockRenderer().getBlockModel(st);
                List<BakedQuad> loose = model.getQuads(st, null, rnd, ModelData.EMPTY, null);
                boolean anyCulled = false;
                for (Direction d : Direction.values()) {
                    if (!model.getQuads(st, d, rnd, ModelData.EMPTY, null).isEmpty()) {
                        anyCulled = true;
                        break;
                    }
                }
                boolean cross = !anyCulled && loose.size() >= 2 && diagonal(loose.get(0))
                        && st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
                String name = BuiltInRegistries.BLOCK.getKey(block).toString();
                if (cross) {
                    jobs.add(new Job(name, List.of(), layers(mc, st, List.of(loose.get(0))), List.of(), true));
                    continue;
                }
                List<Layer> top = layers(mc, st, faceQuads(model, st, Direction.UP, loose, rnd));
                List<Layer> side = layers(mc, st, faceQuads(model, st, Direction.NORTH, loose, rnd));
                if (side.isEmpty()) side = layers(mc, st, faceQuads(model, st, Direction.EAST, loose, rnd));
                List<Layer> bottom = layers(mc, st, faceQuads(model, st, Direction.DOWN, loose, rnd));
                if (top.isEmpty() && side.isEmpty() && bottom.isEmpty()) {
                    var p = model.getParticleIcon(ModelData.EMPTY);
                    if (p == null) continue;
                    side = List.of(new Layer(p.contents().name(), -1));
                }
                jobs.add(new Job(name, top, side, bottom, false));
            } catch (RuntimeException e) {
                // необычная модель — этот блок останется цветом с карты
            }
        }
        var resources = mc.getResourceManager();
        Thread t = new Thread(() -> {
            try {
                Map<ResourceLocation, int[]> cache = new HashMap<>();
                List<int[]> images = new ArrayList<>();
                Map<Integer, List<Integer>> byHash = new HashMap<>();
                List<BlockTextures.Named> named = new ArrayList<>();
                for (Job j : jobs) {
                    int top = BlockTextures.intern(images, byHash, compose(resources, cache, j.top()));
                    int side = BlockTextures.intern(images, byHash, compose(resources, cache, j.side()));
                    int bottom = BlockTextures.intern(images, byHash, compose(resources, cache, j.bottom()));
                    if (top < 0 && side < 0 && bottom < 0) continue;
                    named.add(new BlockTextures.Named(j.name(), top, side, bottom, j.cross()));
                }
                baked = BlockTextures.encode(images, named);
                com.mojang.logging.LogUtils.getLogger().info("MineComputers: текстуры для камер — {} блоков, {} картинок, {} КБ",
                        named.size(), images.size(), baked.length / 1024);
                Minecraft.getInstance().execute(() -> {
                    baking = false;
                    // в одиночной игре сервер в этой же игре — новая таблица сразу ему
                    if (pending || Minecraft.getInstance().getSingleplayerServer() != null) deliver();
                });
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("MineComputers: текстуры для камер не собрались", e);
                baking = false;
            }
        }, "MineComputers camera textures");
        t.setDaemon(true);
        t.start();
    }

    private static void deliver() {
        byte[] data = baked;
        Minecraft mc = Minecraft.getInstance();
        if (data == null || mc.getConnection() == null) return;
        pending = false;
        if (mc.getSingleplayerServer() != null) {
            BlockTextures.install(data);
            return;
        }
        int total = (data.length + CameraTexturesPayload.CHUNK - 1) / CameraTexturesPayload.CHUNK;
        for (int i = 0; i < total; i++) {
            int from = i * CameraTexturesPayload.CHUNK, to = Math.min(data.length, from + CameraTexturesPayload.CHUNK);
            PacketDistributor.sendToServer(new CameraTexturesPayload(i, total, java.util.Arrays.copyOfRange(data, from, to)));
        }
    }

    /** Четырёхугольники грани: отсекаемые этой стороной, иначе свободные, повёрнутые в эту сторону. */
    private static List<BakedQuad> faceQuads(BakedModel model, BlockState st, Direction d, List<BakedQuad> loose, RandomSource rnd) {
        List<BakedQuad> q = model.getQuads(st, d, rnd, ModelData.EMPTY, null);
        if (!q.isEmpty()) return q;
        List<BakedQuad> out = new ArrayList<>();
        for (BakedQuad b : loose) if (b.getDirection() == d) out.add(b);
        return out;
    }

    private static List<Layer> layers(Minecraft mc, BlockState st, List<BakedQuad> quads) {
        List<Layer> out = new ArrayList<>();
        for (BakedQuad q : quads) {
            int tint = -1;
            if (q.isTinted()) {
                try {
                    tint = mc.getBlockColors().getColor(st, null, null, q.getTintIndex());
                } catch (RuntimeException e) {
                    tint = -1;
                }
            }
            Layer l = new Layer(q.getSprite().contents().name(), tint);
            if (!out.contains(l)) out.add(l);
        }
        return out;
    }

    /** Четырёхугольник стоит наискось (растение крестом). */
    private static boolean diagonal(BakedQuad q) {
        int[] v = q.getVertices();
        int stride = v.length / 4;
        float x0 = Float.intBitsToFloat(v[0]), z0 = Float.intBitsToFloat(v[2]);
        float x2 = Float.intBitsToFloat(v[stride * 2]), z2 = Float.intBitsToFloat(v[stride * 2 + 2]);
        return Math.abs(x2 - x0) > 0.3f && Math.abs(z2 - z0) > 0.3f;
    }

    /** Слои грани одной картинкой 16×16 (с окраской и прозрачностью). null — нет слоёв. */
    private static int[] compose(net.minecraft.server.packs.resources.ResourceManager rm, Map<ResourceLocation, int[]> cache, List<Layer> layers) {
        if (layers.isEmpty()) return null;
        int[] out = new int[256];
        boolean any = false;
        for (Layer l : layers) {
            int[] img = cache.computeIfAbsent(l.sprite(), k -> read(rm, k));
            if (img == null) continue;
            any = true;
            for (int i = 0; i < 256; i++) {
                int c = img[i];
                int a = c >>> 24;
                if (a < 128) continue;
                if (l.tint() != -1) {
                    int t = l.tint();
                    c = 0xFF000000 | (((c >> 16) & 255) * ((t >> 16) & 255) / 255) << 16
                            | (((c >> 8) & 255) * ((t >> 8) & 255) / 255) << 8 | ((c & 255) * (t & 255) / 255);
                }
                out[i] = c | 0xFF000000;
            }
        }
        return any ? out : null;
    }

    /** Картинка спрайта 16×16 ARGB (анимация — первый кадр; крупные пакеты — уменьшены). */
    private static int[] read(net.minecraft.server.packs.resources.ResourceManager rm, ResourceLocation sprite) {
        ResourceLocation file = sprite.withPath("textures/" + sprite.getPath() + ".png");
        Optional<net.minecraft.server.packs.resources.Resource> res = rm.getResource(file);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = Math.min(img.getHeight(), w);
            if (w <= 0 || h <= 0) return null;
            int[] out = new int[256];
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int x0 = x * w / 16, x1 = Math.max(x0 + 1, (x + 1) * w / 16);
                    int y0 = y * h / 16, y1 = Math.max(y0 + 1, (y + 1) * h / 16);
                    long r = 0, g = 0, b = 0;
                    int opaque = 0, all = 0;
                    for (int yy = y0; yy < y1; yy++) {
                        for (int xx = x0; xx < x1; xx++) {
                            int abgr = img.getPixelRGBA(xx, yy);
                            all++;
                            if ((abgr >>> 24) < 128) continue;
                            opaque++;
                            r += abgr & 255;
                            g += (abgr >> 8) & 255;
                            b += (abgr >> 16) & 255;
                        }
                    }
                    out[y * 16 + x] = opaque * 2 < all ? 0
                            : 0xFF000000 | (int) (r / opaque) << 16 | (int) (g / opaque) << 8 | (int) (b / opaque);
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
