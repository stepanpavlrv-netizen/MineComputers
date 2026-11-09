package com.strikerr212.minepc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * Общее для периферии (монитор, клавиатура, дисковод): поворот к игроку и обычная модель.
 * Устройство работает, если стоит вплотную к системному блоку (клавиатура — ещё и рядом с монитором).
 */
public abstract class DeviceBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected DeviceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Найти системный блок, к которому подключено устройство в позиции pos (или null). */
    public static BlockPos findCase(BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.getBlockEntity(n) instanceof PcCaseBlockEntity) return n;
        }
        // Клавиатура может стоять у монитора, а монитор — у системного блока
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.getBlockEntity(n) instanceof MonitorBlockEntity monitor && monitor.casePos() != null) {
                return monitor.casePos();
            }
        }
        return null;
    }

    /** Клавиатура в этом блоке: отдельная, на стойке, вставленная в монитор или встроенная в терминал. */
    public static boolean providesKeyboard(BlockState s) {
        return s.getBlock() instanceof KeyboardBlock || s.getBlock() instanceof BaseMonitorBlock m && m.hasKeyboard(s);
    }

    /** Есть ли клавиатура рядом с системным блоком или с одним из его мониторов. */
    public static boolean hasKeyboard(Level level, BlockPos casePos) {
        for (Direction d : Direction.values()) {
            BlockPos n = casePos.relative(d);
            BlockState s = level.getBlockState(n);
            if (providesKeyboard(s)) return true;
            if (level.getBlockEntity(n) instanceof MonitorBlockEntity) {
                for (BlockPos m : MonitorGroup.of(level, n).blocks()) {
                    if (providesKeyboard(level.getBlockState(m))) return true;
                    for (Direction d2 : Direction.values()) {
                        if (providesKeyboard(level.getBlockState(m.relative(d2)))) return true;
                    }
                }
            }
        }
        return false;
    }
}
