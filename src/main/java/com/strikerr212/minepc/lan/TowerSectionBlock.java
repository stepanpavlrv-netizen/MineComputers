package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Секция решётчатой радиовышки (1 блок высоты). Ставится на землю или на другую секцию; вышка сама сужается
 * кверху: ширина секции зависит от того, сколько секций над ней ({@link #ABOVE}: наверху 1×1, через 16 секций — 3×3, у вышки в 32 секции внизу почти 5×5;
 * шире 3×3 — с опорами {@link TowerLegBlock} вокруг).
 * Окраска — красно-белыми полосами по 2 блока ({@link #BAND} по номеру секции снизу), серый краситель — вся вышка
 * оцинкованная, красный — обратно. Ноги и пояса не мешают ходить, а внутри вышки можно лезть вверх, как по лестнице.
 * На макушке и каждые 6 секций — красные огни, ночью мигают. Радиомодем ставится вплотную к нижней секции.
 */
public class TowerSectionBlock extends BaseEntityBlock {

    public static final MapCodec<TowerSectionBlock> CODEC = simpleCodec(TowerSectionBlock::new);
    /** Сколько секций выше (до 31). */
    public static final IntegerProperty ABOVE = IntegerProperty.create("above", 0, 31);
    /** Номер секции снизу (0 — нижняя, на ней бетонные опоры). */
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 0, 31);
    public static final BooleanProperty BAND = BooleanProperty.create("band");
    public static final BooleanProperty GALVANIZED = BooleanProperty.create("galvanized");
    /** Самая высокая вышка, секций. */
    public static final int MAX_SECTIONS = 32;
    /** С этого числа секций сверху вышка шире 3×3 и вокруг секции стоят опоры ({@link TowerLegBlock}). */
    public static final int WIDE_FROM = 17;
    private static final VoxelShape OUTLINE = Block.box(3, 0, 3, 13, 16, 13);

    public TowerSectionBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ABOVE, 0).setValue(HEIGHT, 0).setValue(BAND, false).setValue(GALVANIZED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ABOVE, HEIGHT, BAND, GALVANIZED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TowerSectionBlockEntity(pos, state);
    }

    /** Ширина вышки (в пикселях) у секции, над которой ещё k секций. */
    public static int width(int k) {
        return Math.min(80, 16 + 2 * k);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Внутри вышки можно лезть вверх. */
    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }

    // ======================= Форма вышки =======================

    /** Состояние секции в pos по соседям: сколько секций сверху, какая по счёту снизу, полоса, окраска. */
    public BlockState shaped(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState up = level.getBlockState(pos.above()), down = level.getBlockState(pos.below());
        int above = up.getBlock() instanceof TowerSectionBlock ? Math.min(MAX_SECTIONS - 1, up.getValue(ABOVE) + 1) : 0;
        int height = down.getBlock() instanceof TowerSectionBlock ? Math.min(31, down.getValue(HEIGHT) + 1) : 0;
        boolean galv = down.getBlock() instanceof TowerSectionBlock ? down.getValue(GALVANIZED) : state.getValue(GALVANIZED);
        return state.setValue(ABOVE, above).setValue(HEIGHT, height).setValue(BAND, (height / 2) % 2 == 1).setValue(GALVANIZED, galv);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = shaped(defaultBlockState(), context.getLevel(), context.getClickedPos());
        if (!s.canSurvive(context.getLevel(), context.getClickedPos())) return null;
        BlockPos blocked = blockedFor(context.getLevel(), context.getClickedPos());
        if (blocked != null) {
            if (!context.getLevel().isClientSide && context.getPlayer() != null) blockedMessage(context.getPlayer(), blocked);
            return null;
        }
        return s;
    }

    // ======================= Опоры широкой вышки =======================

    /** Место опоры свободно: воздух, трава и прочее заменяемое (но не вода) или уже опора. */
    private static boolean legFits(BlockState st) {
        return st.getBlock() instanceof TowerLegBlock || (st.canBeReplaced() && st.getFluidState().isEmpty());
    }

    /**
     * Что помешает опорам, если в newPos появится секция (вышка станет на одну выше): первый занятый блок или null.
     * Широкими (с опорами) станут нижние секции, над которыми окажется {@link #WIDE_FROM} и больше секций.
     */
    public static BlockPos blockedFor(BlockGetter level, BlockPos newPos) {
        BlockPos bottom = newPos;
        while (level.getBlockState(bottom.below()).getBlock() instanceof TowerSectionBlock) bottom = bottom.below();
        BlockPos top = newPos;
        while (level.getBlockState(top.above()).getBlock() instanceof TowerSectionBlock) top = top.above();
        int n = top.getY() - bottom.getY() + 1;
        for (int i = 0; i < n && n - 1 - i >= WIDE_FROM; i++) {
            BlockPos c = bottom.above(i);
            for (int[] d : TowerLegBlock.CELLS) {
                BlockPos p = c.offset(d[0], 0, d[1]);
                BlockState st = level.getBlockState(p);
                if (!legFits(st)) return p;
                if (st.getBlock() instanceof TowerLegBlock && !TowerLegBlock.owner(st, p).equals(c)) return p;
            }
        }
        return null;
    }

    private static void blockedMessage(Player player, BlockPos p) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.minepc.tower.blocked",
                p.getX(), p.getY(), p.getZ()), true);
    }

    /** Поставить, обновить или убрать опоры секции по её состоянию. Занятые места пропускаются (у старых вышек). */
    public static void syncLegs(Level level, BlockPos pos, BlockState state) {
        boolean wide = state.getBlock() instanceof TowerSectionBlock && state.getValue(ABOVE) >= WIDE_FROM;
        for (int i = 0; i < TowerLegBlock.CELLS.length; i++) {
            int[] d = TowerLegBlock.CELLS[i];
            BlockPos p = pos.offset(d[0], 0, d[1]);
            BlockState st = level.getBlockState(p);
            boolean mine = st.getBlock() instanceof TowerLegBlock && TowerLegBlock.owner(st, p).equals(pos);
            if (!wide) {
                if (mine) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                continue;
            }
            BlockState want = com.strikerr212.minepc.registry.ModBlocks.TOWER_LEG.get().defaultBlockState()
                    .setValue(TowerLegBlock.ABOVE, state.getValue(ABOVE)).setValue(TowerLegBlock.CELL, i)
                    .setValue(BAND, state.getValue(BAND)).setValue(GALVANIZED, state.getValue(GALVANIZED))
                    .setValue(TowerLegBlock.FOOT, state.getValue(HEIGHT) == 0);
            if (mine) {
                if (st != want) level.setBlock(p, want, Block.UPDATE_CLIENTS);
            } else if (!(st.getBlock() instanceof TowerLegBlock) && legFits(st)) {
                level.setBlock(p, want, Block.UPDATE_ALL);
            }
        }
    }

    private static boolean legsMatter(BlockState a, BlockState b) {
        return a.getBlock() instanceof TowerSectionBlock && a.getValue(ABOVE) >= WIDE_FROM
                || b.getBlock() instanceof TowerSectionBlock && b.getValue(ABOVE) >= WIDE_FROM;
    }

    /** На земле (крепкий верх блока под ней) или на другой секции; не выше {@link #MAX_SECTIONS}. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState b = level.getBlockState(below);
        if (b.getBlock() instanceof TowerSectionBlock) return b.getValue(HEIGHT) + 1 < MAX_SECTIONS;
        return b.isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            // Убрали опору — вышка рушится секция за секцией (с добычей)
            level.scheduleTick(pos, this, 2);
            return state;
        }
        return dir.getAxis() == Direction.Axis.Y ? shaped(state, level, pos) : state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) level.destroyBlock(pos, true);
        else syncLegs(level, pos, state);
    }

    // ======================= Игрок =======================

    /**
     * Секцией по вышке — новая секция сразу на макушку. Серым красителем — вся вышка оцинкованная, красным —
     * обратно красно-белая.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() == this) {
            BlockPos top = pos;
            while (level.getBlockState(top.above()).getBlock() instanceof TowerSectionBlock) top = top.above();
            BlockPos target = top.above();
            if (!level.getBlockState(target).canBeReplaced()) return ItemInteractionResult.FAIL;
            BlockState s = shaped(defaultBlockState(), level, target);
            if (!s.canSurvive(level, target)) {
                if (!level.isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.minepc.tower.too_high", MAX_SECTIONS), true);
                return ItemInteractionResult.FAIL;
            }
            BlockPos blocked = blockedFor(level, target);
            if (blocked != null) {
                if (!level.isClientSide) blockedMessage(player, blocked);
                return ItemInteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                level.setBlockAndUpdate(target, s);
                level.playSound(null, target, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1f, 0.9f);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.getItem() instanceof DyeItem dye && (dye.getDyeColor() == DyeColor.GRAY || dye.getDyeColor() == DyeColor.LIGHT_GRAY
                || dye.getDyeColor() == DyeColor.RED)) {
            boolean galv = dye.getDyeColor() != DyeColor.RED;
            if (state.getValue(GALVANIZED) == galv) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide) {
                BlockPos p = pos;
                while (level.getBlockState(p.below()).getBlock() instanceof TowerSectionBlock) p = p.below();
                for (int i = 0; i < MAX_SECTIONS && level.getBlockState(p).getBlock() instanceof TowerSectionBlock; i++, p = p.above()) {
                    level.setBlock(p, level.getBlockState(p).setValue(GALVANIZED, galv), Block.UPDATE_CLIENTS);
                    syncLegs(level, p, level.getBlockState(p));
                }
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1f, 1f);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide && !old.is(this)) Tower.notifyModems(level, pos);
        // Опоры — тиком позже: секции под ней в этот момент ещё пересчитывают свою ширину
        if (!level.isClientSide && legsMatter(state, old)) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && !newState.is(this)) {
            Tower.notifyModems(level, pos);
            syncLegs(level, pos, newState);   // секции нет — её опоры убираются
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.tower_section").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.tower_section_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
