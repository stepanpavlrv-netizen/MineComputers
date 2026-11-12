package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Кронштейн воздушной линии: держатель с фарфоровым изолятором, ставится на пол, стену или потолок.
 * Кабель с мотка натягивается между двумя кронштейнами (до {@link #MAX_SPAN} блоков) — над улицей, между домами,
 * к вышке. В изолятор втыкаются провода снизу и с боков; всё, что воткнуто в кронштейн, и все линии от него —
 * один провод (пассивный, без задержки).
 */
public class BracketBlock extends FaceAttachedHorizontalDirectionalBlock implements LanDevice, EntityBlock {

    public static final MapCodec<BracketBlock> CODEC = simpleCodec(BracketBlock::new);
    /** Самый длинный пролёт воздушной линии, блоков. */
    public static final int MAX_SPAN = 24;

    private static final VoxelShape CENTER = Block.box(6, 6, 6, 10, 10, 10);

    public BracketBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.WALL));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    /** Сторона, к которой прикручен кронштейн. */
    public static Direction attached(BlockState state) {
        return getConnectedDirection(state).getOpposite();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction a = attached(state);
        // Изолятор посередине и лапа до опоры
        VoxelShape arm = switch (a) {
            case DOWN -> Block.box(7, 0, 7, 9, 6, 9);
            case UP -> Block.box(7, 10, 7, 9, 16, 9);
            case NORTH -> Block.box(7, 7, 0, 9, 9, 6);
            case SOUTH -> Block.box(7, 7, 10, 9, 9, 16);
            case WEST -> Block.box(0, 7, 7, 6, 9, 9);
            default -> Block.box(10, 7, 7, 16, 9, 9);
        };
        return Shapes.or(CENTER, arm);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LanBlockEntity(pos, state);
    }

    @Override
    public Kind lanKind() {
        return Kind.SWITCH;
    }

    @Override
    public int lanType(BlockState state) {
        return LanData.BRACKET;
    }

    @Override
    public boolean acceptsCable(BlockState state, Direction face) {
        return face != attached(state);
    }

    /** По гнезду в изолятор с каждой стороны, кроме той, где опора. */
    @Override
    public List<Socket> sockets(BlockState state) {
        List<Socket> out = new ArrayList<>();
        Direction a = attached(state);
        for (Direction f : Direction.values()) {
            if (f != a) out.add(new Socket(f, 7.5f, 7.5f, 6, true, false));
        }
        return out;
    }

    @Override
    public long lanBitsPerSecond(Level level, BlockPos pos, BlockState state) {
        return 10_000_000_000L;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide) {
            LanManager.updateDevice(level, pos);
            CableBlockEntity.reconnectAround(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            LanManager.updateDevice(level, pos);
            CableBlockEntity.reconnectAround(level, pos);
        }
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.bracket", MAX_SPAN)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
