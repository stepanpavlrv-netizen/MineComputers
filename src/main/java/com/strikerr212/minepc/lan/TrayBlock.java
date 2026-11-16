package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.block.Paint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кабельный лоток (вариант А «лестничный»): две боковины и перекладины, 10 пикселей шириной, 3 высотой. Ставится на
 * пол, стену (вдоль и вверх) и потолок; сам соединяется с соседними лотками (прямой, угол, тройник, крестовина),
 * лоток на полу поднимается к лотку на стене над ним (и с потолка опускается к лотку на стене под ним). Красится
 * красителем (оцинкованный по умолчанию), ведро воды смывает краску.
 *
 * <p>Провода в лотке лежат ровным рядом: внутри 8 дорожек, у каждого провода своя (поиск пути {@link
 * com.strikerr212.minepc.wire.LevelVoxels} считает занятые дорожки твёрдыми), и по лотку провод тянуть дешевле, чем
 * по полу ({@link com.strikerr212.minepc.wire.WireRouter}).
 *
 * <p>Раскладка частей описана для лотка на полу (как в генераторе tools/assetgen/tray.py), на потолок и стены её
 * поворачивает {@link #ROT} — те же повороты, что в blockstate.
 */
public class TrayBlock extends Block {

    public static final MapCodec<TrayBlock> CODEC = simpleCodec(TrayBlock::new);

    /** Связь стороны лотка: нет (закрыта боковиной), продолжается, поворачивает на стену. */
    public enum Link implements StringRepresentable {
        NONE, FLAT, BEND;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public boolean open() {
            return this != NONE;
        }
    }

    /** К чему прикреплён лоток: DOWN — пол, UP — потолок, стороны света — стена. */
    public static final DirectionProperty FACE = DirectionProperty.create("face");
    /** Стороны лотка в его собственной раскладке (как у лотка на полу: север, восток, юг, запад). */
    public static final EnumProperty<Link> N = EnumProperty.create("n", Link.class), E = EnumProperty.create("e", Link.class),
            S = EnumProperty.create("s", Link.class), W = EnumProperty.create("w", Link.class);
    private static final List<EnumProperty<Link>> SIDES = List.of(N, E, S, W);

    public TrayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, Direction.DOWN).setValue(N, Link.FLAT).setValue(S, Link.FLAT)
                .setValue(E, Link.NONE).setValue(W, Link.NONE).setValue(Paint.PROPERTY, Paint.NONE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, N, E, S, W, Paint.PROPERTY);
    }

    // ======================= Повороты =======================

    /** Поворот раскладки «на полу» к месту крепления: {x, y} в градусах — как в blockstate. */
    static int[] rot(Direction face) {
        return switch (face) {
            case DOWN -> new int[]{0, 0};
            case UP -> new int[]{180, 0};
            case SOUTH -> new int[]{90, 0};
            case WEST -> new int[]{90, 90};
            case NORTH -> new int[]{90, 180};
            case EAST -> new int[]{90, 270};
        };
    }

    /** Направление после поворота по x на 90° (верх → север → низ → юг) — как у модели с "x": 90. */
    private static Direction stepX(Direction d) {
        return switch (d) {
            case UP -> Direction.NORTH;
            case NORTH -> Direction.DOWN;
            case DOWN -> Direction.SOUTH;
            case SOUTH -> Direction.UP;
            default -> d;
        };
    }

    private static Direction rotate(Direction d, int xr, int yr) {
        for (int i = 0; i < xr / 90; i++) d = stepX(d);
        for (int i = 0; i < yr / 90; i++) d = d.getAxis().isVertical() ? d : d.getClockWise();
        return d;
    }

    /** Направление в мире стороны side (0 — север, 1 — восток, 2 — юг, 3 — запад раскладки) у лотка на face. */
    public static Direction world(Direction face, int side) {
        int[] r = rot(face);
        return rotate(Direction.from2DDataValue((side + 2) % 4), r[0], r[1]);
    }

    /** Сторона раскладки, смотрящая в мире в сторону d (-1 — такой нет: d — к опоре или от неё). */
    public static int side(Direction face, Direction d) {
        for (int i = 0; i < 4; i++) if (world(face, i) == d) return i;
        return -1;
    }

    /** Связь лотка state в сторону мира d. */
    public static Link linkToward(BlockState state, Direction d) {
        int i = side(state.getValue(FACE), d);
        return i < 0 ? Link.NONE : state.getValue(SIDES.get(i));
    }

    /** Коробка (пиксели) раскладки «на полу», повёрнутая к стороне side, затем к месту крепления. */
    private static double[] box(double[] b, int side, Direction face) {
        double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        for (int i = 0; i < side; i++) {        // по часовой, вид сверху: (x, z) → (16 - z, x)
            double nx0 = 16 - z1, nx1 = 16 - z0, nz0 = x0, nz1 = x1;
            x0 = nx0; x1 = nx1; z0 = nz0; z1 = nz1;
        }
        int[] r = rot(face);
        for (int i = 0; i < r[0] / 90; i++) {   // по x: (x, y, z) → (x, z, 16 - y)
            double ny0 = z0, ny1 = z1, nz0 = 16 - y1, nz1 = 16 - y0;
            y0 = ny0; y1 = ny1; z0 = nz0; z1 = nz1;
        }
        for (int i = 0; i < r[1] / 90; i++) {
            double nx0 = 16 - z1, nx1 = 16 - z0, nz0 = x0, nz1 = x1;
            x0 = nx0; x1 = nx1; z0 = nz0; z1 = nz1;
        }
        return new double[]{x0, y0, z0, x1, y1, z1};
    }

    // ======================= Форма =======================

    // Раскладка «на полу», сторона «север»; как в tray.py
    private static final double[][] PLATE = {{3, 0, 3, 13, 1, 13}};
    private static final double[][] POSTS = {{3, 0, 3, 4, 3, 4}, {12, 0, 3, 13, 3, 4}, {3, 0, 12, 4, 3, 13}, {12, 0, 12, 13, 3, 13}};
    private static final double[][] RAIL = {{3, 0, 3, 13, 3, 4}};
    private static final double[][] ARM = {{3, 0, 0, 13, 1, 3}, {3, 0, 0, 4, 3, 3}, {12, 0, 0, 13, 3, 3}};
    private static final double[][] BEND = {{3, 0, 0, 13, 16, 1}, {3, 0, 0, 4, 16, 3}, {12, 0, 0, 13, 16, 3}};
    /** Где лежат провода (над перекладинами, между боковинами): середина, сторона, подъём. */
    private static final double[][] CHANNEL = {{4, 1, 4, 12, 2, 12}};
    private static final double[][] CHANNEL_ARM = {{4, 1, 0, 12, 2, 4}};
    private static final double[][] CHANNEL_BEND = {{4, 1, 1, 12, 16, 2}};

    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();
    private static final Map<BlockState, List<double[]>> CHANNELS = new ConcurrentHashMap<>();

    private static void add(List<double[]> out, double[][] boxes, int side, Direction face) {
        for (double[] b : boxes) out.add(box(b, side, face));
    }

    /** Коробки лотка (пиксели блока): плита, боковины, стойки, подъём. */
    public static List<double[]> boxes(BlockState state) {
        Direction face = state.getValue(FACE);
        List<double[]> out = new ArrayList<>();
        add(out, PLATE, 0, face);
        add(out, POSTS, 0, face);
        for (int i = 0; i < 4; i++) {
            Link l = state.getValue(SIDES.get(i));
            add(out, l.open() ? ARM : RAIL, i, face);
            if (l == Link.BEND) add(out, BEND, i, face);
        }
        return out;
    }

    /** Желоб, где лежат провода (пиксели блока): по нему поиск пути тянет провод охотнее, чем по полу. */
    public static List<double[]> channel(BlockState state) {
        return CHANNELS.computeIfAbsent(state, s -> {
            Direction face = s.getValue(FACE);
            List<double[]> out = new ArrayList<>();
            add(out, CHANNEL, 0, face);
            for (int i = 0; i < 4; i++) {
                Link l = s.getValue(SIDES.get(i));
                if (l.open()) add(out, CHANNEL_ARM, i, face);
                if (l == Link.BEND) add(out, CHANNEL_BEND, i, face);
            }
            return out;
        });
    }

    private static VoxelShape shape(BlockState state) {
        return SHAPES.computeIfAbsent(state, s -> {
            VoxelShape v = Shapes.empty();
            for (double[] b : boxes(s)) v = Shapes.or(v, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
            return v.optimize();
        });
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    // ======================= Соединения =======================

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace().getOpposite();
        // Одиночный лоток — прямой кусок: на полу и потолке — вдоль взгляда, на стене — вдоль стены (вверх — если
        // смотришь круто вверх или вниз)
        Direction along = face.getAxis().isVertical() ? ctx.getHorizontalDirection()
                : Math.abs(ctx.getPlayer() == null ? 0 : ctx.getPlayer().getXRot()) > 40 ? Direction.UP : face.getClockWise();
        int i = side(face, along);
        boolean ns = i < 0 || i % 2 == 0;
        BlockState s = defaultBlockState().setValue(FACE, face)
                .setValue(N, ns ? Link.FLAT : Link.NONE).setValue(S, ns ? Link.FLAT : Link.NONE)
                .setValue(E, ns ? Link.NONE : Link.FLAT).setValue(W, ns ? Link.NONE : Link.FLAT);
        return connect(s, ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return connect(state, level, pos);
    }

    private boolean tray(BlockState s, Direction face) {
        return s.is(this) && s.getValue(FACE) == face;
    }

    /** Пересчитать связи сторон по соседям. */
    public BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        Direction face = state.getValue(FACE);
        Link[] links = new Link[4];
        int open = 0, last = -1;
        for (int i = 0; i < 4; i++) {
            Direction d = world(face, i);
            BlockState n = level.getBlockState(pos.relative(d));
            Link l = Link.NONE;
            if (tray(n, face)) {
                l = Link.FLAT;
            } else if (face == Direction.DOWN && tray(level.getBlockState(pos.above()), d)) {
                l = Link.BEND;                       // лоток на стене над нами: поднимаемся к нему
            } else if (face == Direction.UP && tray(level.getBlockState(pos.below()), d)) {
                l = Link.BEND;                       // с потолка — вниз к лотку на стене
            } else if (d == Direction.DOWN && n.is(this) && n.getValue(FACE) == Direction.DOWN
                    && linkToward(n, face) == Link.BEND) {
                l = Link.FLAT;                       // на стене: снизу лоток на полу поднимается к нам
            } else if (d == Direction.UP && n.is(this) && n.getValue(FACE) == Direction.UP && linkToward(n, face) == Link.BEND) {
                l = Link.FLAT;
            }
            links[i] = l;
            if (l.open()) {
                open++;
                last = i;
            }
        }
        if (open == 0) {
            // одиночный — прямой кусок, как был (по умолчанию север–юг)
            boolean ns = state.getValue(N).open() || state.getValue(S).open() || !(state.getValue(E).open() || state.getValue(W).open());
            links[ns ? 0 : 1] = Link.FLAT;
            links[ns ? 2 : 3] = Link.FLAT;
        } else if (open == 1 && links[(last + 2) % 4] == Link.NONE) {
            links[(last + 2) % 4] = Link.FLAT;     // конец ряда открыт — лоток не обрывается боковиной поперёк
        }
        return state.setValue(N, links[0]).setValue(E, links[1]).setValue(S, links[2]).setValue(W, links[3]);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.cable_tray").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    // ======================= Краска =======================

    public int paintColor(BlockState state) {
        return state.getValue(Paint.PROPERTY).tint(0xFFFFFF);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult painted = Paint.use(stack, state, level, pos, player);
        return painted != null ? painted : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
