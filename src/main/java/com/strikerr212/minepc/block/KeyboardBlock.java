package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Клавиатура: без неё на экране можно только смотреть. Ставится рядом с монитором или системным блоком.
 * ПКМ клавиатурой по боку монитора — она вешается на него полкой на кронштейнах.
 * Клавиатурная стойка — та же клавиатура на подставке.
 */
public class KeyboardBlock extends DeviceBlock {

    public static final MapCodec<KeyboardBlock> CODEC = simpleCodec(KeyboardBlock::new);

    public enum Mount implements StringRepresentable {
        FLOOR("floor"), WALL("wall");

        private final String name;

        Mount(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);

    private static final BoxShapes FLOOR = BoxShapes.of(1, 0, 4, 15, 2, 12);
    private static final BoxShapes WALL = BoxShapes.of(1, 3, 10, 15, 8, 16);
    private static final BoxShapes STAND = BoxShapes.of(4, 0, 4, 12, 9, 12).add(2, 9, 3, 14, 13, 13);

    private final boolean stand;

    public KeyboardBlock(Properties properties) {
        this(properties, false);
    }

    public KeyboardBlock(Properties properties, boolean stand) {
        super(properties);
        this.stand = stand;
        registerDefaultState(defaultBlockState().setValue(MOUNT, Mount.FLOOR));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MOUNT);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Кликнули по боку монитора — клавиатура вешается на него. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = super.getStateForPlacement(context);
        Direction face = context.getClickedFace();
        if (!stand && face.getAxis().isHorizontal()) {
            BlockPos against = context.getClickedPos().relative(face.getOpposite());
            if (context.getLevel().getBlockState(against).getBlock() instanceof BaseMonitorBlock) {
                return s.setValue(FACING, face).setValue(MOUNT, Mount.WALL);
            }
        }
        return s;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (stand) return STAND.get(facing);
        return (state.getValue(MOUNT) == Mount.WALL ? WALL : FLOOR).get(facing);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;   // клавиатуре не нужно хранить данные
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            BlockPos casePos = findCase(level, pos);
            if (casePos == null) {
                player.displayClientMessage(Component.translatable("message.minepc.keyboard.no_case"), true);
            } else {
                ClientAccess.openTerminal(casePos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
