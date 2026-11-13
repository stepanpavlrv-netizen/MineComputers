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
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Сетевая розетка: пластина на стене, полу или потолке с двумя гнёздами спереди (линии A и B). Кабель здания
 * подходит к ней снизу, сверху или сбоку (по стене) — у каждой кромки тоже по гнезду на линию, — а компьютер
 * втыкается коротким кабелем или патч-кордом в гнездо спереди. Пассивная: всё, что воткнуто в одну линию, — один провод.
 *
 * <p>Две розетки на двух сторонах одной стены (одна — напротив другой) соединены сквозь стену: линия A с линией A,
 * B с B. Так кабель проходит сквозь стену, не ломая её.
 */
public class OutletBlock extends FaceAttachedHorizontalDirectionalBlock implements LanDevice, EntityBlock {

    public static final MapCodec<OutletBlock> CODEC = simpleCodec(OutletBlock::new);
    /** Где гнёзда линий A и B вдоль стороны (начало полоски, симметрично относительно середины). */
    private static final float LINE_A = 5, LINE_B = 10;

    public OutletBlock(Properties properties) {
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

    /** Сторона, к которой прикручена розетка. */
    public static Direction attached(BlockState state) {
        return getConnectedDirection(state).getOpposite();
    }

    /** Пластина 8×8 толщиной 2 пикселя у опоры: {x0,y0,z0,x1,y1,z1} в пикселях. */
    private static float[] plate(Direction a) {
        float[] lo = {4, 4, 4}, hi = {12, 12, 12};
        int t = a.getAxis().ordinal();
        boolean pos = a.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        lo[t] = pos ? 14 : 0;
        hi[t] = pos ? 16 : 2;
        return new float[]{lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]};
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        float[] p = plate(attached(state));
        return Block.box(p[0], p[1], p[2], p[3], p[4], p[5]);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LanBlockEntity(pos, state);
    }

    // ======================= Сеть =======================

    @Override
    public Kind lanKind() {
        return Kind.SWITCH;
    }

    @Override
    public int lanType(BlockState state) {
        return LanData.OUTLET;
    }

    @Override
    public boolean acceptsCable(BlockState state, Direction face) {
        return face != attached(state);
    }

    /** Для схемы: к какой стороне прикручена (по ней ищется розетка на другой стороне стены). */
    @Override
    public int lanMaskExtra(Level level, BlockPos pos, BlockState state) {
        return attached(state).get3DDataValue();
    }

    /**
     * Гнёзда: два спереди (A, B), потом по два у каждой из четырёх кромок. Чётный номер — линия A, нечётный — B.
     * Гнёзда у кромок — у самой опоры, там же, где лежит провод на стене (или на полу).
     */
    @Override
    public List<Socket> sockets(BlockState state) {
        Direction a = attached(state);
        int t = a.getAxis().ordinal();
        boolean pos = a.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float layer = pos ? 15 : 0;
        List<Socket> out = new ArrayList<>();
        Direction front = a.getOpposite();
        // На полу и потолке гнёзда стоят поперёк того, куда смотрит розетка (как на модели)
        boolean across = a.getAxis() == Direction.Axis.Y && state.getValue(FACING).getAxis() == Direction.Axis.X;
        for (float line : new float[]{LINE_A, LINE_B}) {
            out.add(across ? new Socket(front, 7.5f, line, 14, true, false) : new Socket(front, line, 7.5f, 14, true, false));
        }
        for (Direction f : Direction.values()) {
            if (f.getAxis() == a.getAxis()) continue;
            Direction.Axis[] fa = LanDevice.faceAxes(f);
            for (float line : new float[]{LINE_A, LINE_B}) {
                if (fa[0].ordinal() == t) out.add(new Socket(f, layer, line, 4, true, false));
                else out.add(new Socket(f, line, layer, 4, true, false));
            }
        }
        return out;
    }

    /** Линия гнезда или патч-корда (0 — A, 1 — B). */
    public static int line(int port) {
        return (port < LanData.CABLE_PORTS ? port : port - LanData.CABLE_PORTS) & 1;
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
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.outlet").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.outlet_wall").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
