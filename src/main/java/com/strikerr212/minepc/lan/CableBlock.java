package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Сетевой кабель: провода лежат на полу, как редстоун, до 4 в одном блоке.
 * Ломается по одному проводу — тот, на который смотришь. ПКМ красителем — перекрасить провод.
 */
public class CableBlock extends BaseEntityBlock {

    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

    public CableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof CableBlockEntity be ? be.shape() : Block.box(6, 0, 6, 10, 2, 10);
    }

    /** По проводам можно ходить, как по редстоуну. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.DOWN && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        // Любой сосед (и блок сверху — он может закрыть подъём по стенке) — пересчитать соединения
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CableBlockEntity be) be.reconnect();
        return state;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof CableBlockEntity be && be.wires().size() > 1) {
                // Блок пропал целиком (взрыв, вода): лишние провода — в добычу; один выпадет по таблице добычи
                popResource(level, pos, new ItemStack(ModItems.CABLE.get(), be.wires().size() - 1));
            }
        }
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server) {
            LanData.get(server).removeCable(pos);
            // Блока уже нет: провода на блок выше/ниже (подъёмы по стенкам) должны отцепиться; соседи на том же уровне
            // узнают сами (updateShape)
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (BlockPos n : new BlockPos[]{pos.relative(d).above(), pos.relative(d).below()}) {
                    if (level.isLoaded(n) && level.getBlockEntity(n) instanceof CableBlockEntity other) other.reconnect();
                }
            }
        }
    }

    /** Ломаем один провод, на который смотрит игрок; последний — вместе с блоком. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (level.getBlockEntity(pos) instanceof CableBlockEntity be && be.wires().size() > 1) {
            HitResult hit = player.pick(player.blockInteractionRange(), 1f, false);
            Vec3 at = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16);
            Wire w = be.nearest(at.x, at.z);
            if (w != null) {
                if (!level.isClientSide) {
                    be.remove(w);
                    be.reconnectNeighbours();
                    if (!player.isCreative()) popResource(level, pos, new ItemStack(ModItems.CABLE.get()));
                    level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1f, 1f);
                }
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** ПКМ красителем по проводу — перекрасить его (разные цвета — разные сети). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof DyeItem dye && level.getBlockEntity(pos) instanceof CableBlockEntity be) {
            Vec3 at = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16);
            Wire w = be.nearest(at.x, at.z);
            int color = dye.getDyeColor().getId();
            if (w == null || w.color == color) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide) {
                w.color = color;
                be.changed();
                be.reconnect();
                be.reconnectNeighbours();
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1f, 1f);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
