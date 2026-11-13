package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.block.BoxShapes;
import com.strikerr212.minepc.block.Paint;
import com.strikerr212.minepc.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Радиомодем: связывает сети на большом расстоянии без проводов, но медленно (около 2 КБ/с).
 * Дальность 250 блоков, каждая секция мачты над ним — ещё +250 (до 1000). Модемы слышат друг друга, только если
 * покрашены в один цвет (канал). Кабель — сзади или сбоку. Для сети — как коммутатор: +1 переход.
 */
public class RadioBlock extends HorizontalDirectionalBlock implements LanDevice, EntityBlock {

    public static final MapCodec<RadioBlock> CODEC = simpleCodec(RadioBlock::new);
    private static final BoxShapes SHAPE = BoxShapes.of(2, 0, 3, 14, 16, 13);
    public static final int BASE_RANGE = 250, MAST_RANGE = 250, MAX_MASTS = 3;
    /** 2 КБ/с в игре (байт/с = бит/с / 800). */
    public static final long BITS = 2048L * 800;
    public static final int PAINT_BASE = 0xFCFFF7;

    public RadioBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Paint.PROPERTY, Paint.NONE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Paint.PROPERTY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LanBlockEntity(pos, state);
    }

    public int paintColor(BlockState state) {
        return state.getValue(Paint.PROPERTY).tint(PAINT_BASE);
    }

    /** Секции мачты прямо над модемом (не больше трёх считаются). */
    public static int masts(BlockGetter level, BlockPos pos) {
        int n = 0;
        BlockPos p = pos.above();
        while (n < 8 && level.getBlockState(p).is(ModBlocks.MAST.get())) {
            n++;
            p = p.above();
        }
        return Math.min(MAX_MASTS, n);
    }

    /**
     * Дальность во все стороны (штырь или секторы — по высоте вышки; вышка без антенн или без вышки — антенна
     * самого шкафа и секции мачты над ним, как раньше).
     */
    public static int range(BlockGetter level, BlockPos pos) {
        Tower t = Tower.near(level, pos);
        if (t != null && (t.pin() || t.sectors() != 0)) return t.range();
        return BASE_RANGE + MAST_RANGE * masts(level, pos);
    }

    /** Вышка у модема → биты для схемы сети ({@link Tower#mask}); у модема с мачтой — высота мачты для прямой видимости. */
    @Override
    public int lanMaskExtra(Level level, BlockPos pos, BlockState state) {
        Tower t = Tower.near(level, pos);
        if (t != null) return t.mask();
        return Math.min(63, masts(level, pos) + 1) << 11;
    }

    /** Канал = цвет: некрашеные — канал 0, крашеные — номер красителя + 1. */
    public static int channel(BlockState state) {
        Paint p = state.getValue(Paint.PROPERTY);
        return p == Paint.NONE ? 0 : p.ordinal();
    }

    // ======================= Сеть =======================

    @Override
    public Kind lanKind() {
        return Kind.SWITCH;
    }

    @Override
    public int lanType(BlockState state) {
        return LanData.RADIO;
    }

    @Override
    public boolean acceptsCable(BlockState state, Direction face) {
        return face.getAxis().isHorizontal() && face != state.getValue(FACING);
    }

    @Override
    public long lanBitsPerSecond(Level level, BlockPos pos, BlockState state) {
        return BITS;
    }

    @Override
    public int lanExtra(Level level, BlockPos pos, BlockState state) {
        return range(level, pos) | channel(state) << 16;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult painted = Paint.use(stack, state, level, pos, player);
        return painted != null ? painted : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** ПКМ — дальность, канал и кого слышно. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            LanManager.updateDevice(level, pos);
            LanData data = LanData.get(server);
            LanData.Device d = data.device(pos);
            Paint p = state.getValue(Paint.PROPERTY);
            Component channel = p == Paint.NONE ? Component.translatable("message.minepc.radio.nochannel")
                    : Component.translatable("color.minecraft." + p.getSerializedName());
            Tower t = Tower.near(level, pos);
            if (t == null) {
                player.displayClientMessage(Component.translatable("message.minepc.radio.info", range(level, pos), masts(level, pos), channel,
                        d == null ? 0 : LanManager.radioLinks(data, pos, d).size()), false);
            } else {
                player.displayClientMessage(Component.translatable("message.minepc.radio.tower", t.sections(), antennas(t), range(level, pos),
                        channel, Component.translatable(t.rod() ? "message.minepc.radio.rod_yes" : "message.minepc.radio.rod_no")), false);
                if (t.dishes() != 0) player.displayClientMessage(Component.translatable("message.minepc.radio.dish_range", 2 * t.range()), false);
            }
            long down = data.radioDownUntil(pos) - level.getGameTime();
            if (down > 0) player.displayClientMessage(Component.translatable("message.minepc.radio.down", (down + 19) / 20)
                    .withStyle(net.minecraft.ChatFormatting.RED), false);
            if (d != null) {
                var links = LanManager.radioLinks(data, pos, d);
                if (links.isEmpty()) player.displayClientMessage(Component.translatable("message.minepc.radio.none"), false);
                for (var l : links) {
                    player.displayClientMessage(Component.translatable("message.minepc.radio.link", l.pos().getX() + " " + l.pos().getY() + " " + l.pos().getZ(),
                            (int) Math.sqrt(l.pos().distSqr(pos)), Component.translatable("message.minepc.radio.via." + l.via()),
                            Math.round(l.bits() / 800.0 / 1024.0 * 10) / 10.0, Component.translatable("message.minepc.radio.los." + l.los())), false);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Антенны вышки одной строкой: «штырь, 2 сектора, тарелка». */
    private static Component antennas(Tower t) {
        java.util.List<Component> parts = new java.util.ArrayList<>();
        if (t.pin()) parts.add(Component.translatable("message.minepc.radio.ant_pin"));
        if (t.sectors() != 0) parts.add(Component.translatable("message.minepc.radio.ant_sector", Integer.bitCount(t.sectors())));
        if (t.dishes() != 0) parts.add(Component.translatable("message.minepc.radio.ant_dish", Integer.bitCount(t.dishes())));
        if (parts.isEmpty()) return Component.translatable("message.minepc.radio.ant_none");
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(parts.get(i));
        }
        return out;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide) LanManager.updateDevice(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && !state.is(newState.getBlock())) LanManager.updateDevice(level, pos);
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.radio_modem").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
