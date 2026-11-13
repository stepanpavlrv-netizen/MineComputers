package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.item.DriveItem;
import com.strikerr212.minepc.item.HardwareItem;
import com.strikerr212.minepc.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Системный блок.
 *
 *   ПКМ пустой рукой — окно корпуса: слоты деталей, кнопка питания, характеристики
 *   ПКМ деталью      — быстро установить её в подходящий свободный слот
 *   Shift + ПКМ      — показать отчёт о сборке в чате
 */
public class PcCaseBlock extends BaseEntityBlock implements com.strikerr212.minepc.lan.LanDevice {

    public static final MapCodec<PcCaseBlock> CODEC = simpleCodec(PcCaseBlock::new);

    // В какую сторону смотрит передняя панель
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Форма блока: корпус уже полного блока (10 пикселей в ширину)
    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(3, 0, 0, 13, 16, 16);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(0, 0, 3, 16, 16, 13);

    /** Облик корпуса; null — обычный корпус со стеклянным боком, сквозь который видно детали. */
    private final CaseVariant variant;

    public PcCaseBlock(Properties properties) {
        this(properties, null);
    }

    public PcCaseBlock(Properties properties, CaseVariant variant) {
        super(properties);
        this.variant = variant;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Paint.PROPERTY, Paint.NONE));
    }

    /** Тематика корпуса (оформление окна). */
    public CaseStyle style() {
        return variant == null ? CaseStyle.CLASSIC : variant.style;
    }

    /** Облик корпуса (null — обычный). */
    public CaseVariant variant() {
        return variant;
    }

    /** Встроенная клавиатура (моноблок, домашний компьютер, пульт, стойка с консолью). */
    public boolean hasKeyboard() {
        return variant != null && variant.keyboard;
    }

    /** Цвет граней с краской (tintindex 0) для этого состояния; -1 — блок не красится. */
    public int paintColor(BlockState state) {
        return variant == null ? -1 : state.getValue(Paint.PROPERTY).tint(variant.paintBase);
    }

    /** Видно ли детали внутри (стеклянный бок обычного корпуса). */
    public boolean showsParts() {
        return variant == null;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Paint.PROPERTY);
    }

    /** При установке корпус поворачивается передней панелью к игроку. */
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (variant != null) return variant.shape(state.getValue(FACING));
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST;
    }

    /** Блоки с BlockEntity по умолчанию невидимы — включаем обычную модель. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PcCaseBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.PC_CASE.get(), PcCaseBlockEntity::serverTick);
    }

    /** ПКМ предметом в руке. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (variant != null) {
            ItemInteractionResult painted = Paint.use(stack, state, level, pos, player);
            if (painted != null) return painted;
        }
        boolean flash = stack.getItem() instanceof DriveItem d && d.kind() == DriveItem.Kind.FLASH;
        if (!(stack.getItem() instanceof HardwareItem) && !flash || player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PcCaseBlockEntity pc) {
            player.displayClientMessage(pc.tryInstall(stack, player), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** ПКМ пустой рукой. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (variant != null && variant.screen != null) {
            // Корпус со встроенным экраном: ПКМ — экран, Shift + ПКМ — окно корпуса
            if (!player.isShiftKeyDown()) {
                if (level.isClientSide) com.strikerr212.minepc.client.ClientAccess.openTerminal(pos);
            } else if (!level.isClientSide && level.getBlockEntity(pos) instanceof PcCaseBlockEntity pc) {
                player.openMenu(pc, buf -> buf.writeBlockPos(pos));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PcCaseBlockEntity pc) {
            if (player.isShiftKeyDown()) {
                pc.sendStatus(player);
            } else {
                player.openMenu(pc, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Сигнал красного камня: BIOS может включать компьютер по нему. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block,
                                   BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PcCaseBlockEntity pc) pc.redstone(level.hasNeighborSignal(pos));
    }

    /** Когда корпус ломают, детали выпадают на землю. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PcCaseBlockEntity pc) {
            pc.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide && !state.is(newState.getBlock())) com.strikerr212.minepc.lan.LanManager.updateDevice(level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide) com.strikerr212.minepc.lan.LanManager.updateDevice(level, pos);
    }

    // ======================= Сеть: гнездо на плате =======================

    @Override
    public Kind lanKind() {
        return Kind.COMPUTER;
    }

    /** Кабель втыкается только сзади — в сетевой разъём на задней панели. */
    @Override
    public boolean acceptsCable(BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    /**
     * Одно гнездо — сетевой разъём на задней панели: у заглушки I/O, слева вверху, если смотреть на корпус сзади
     * (как на модели: x считается у корпуса, повёрнутого лицом на север).
     */
    @Override
    public java.util.List<Socket> sockets(BlockState state) {
        net.minecraft.world.phys.AABB box = com.strikerr212.minepc.lan.LanDevice.bounds(state.setValue(FACING, Direction.NORTH));
        float x = (float) Math.floor(box.minX * 16 + (box.maxX - box.minX) * 16 * 0.2);
        float y = (float) Math.floor(box.minY * 16 + (box.maxY - box.minY) * 16 * 0.7);
        float depth = (float) (16 - box.maxZ * 16);
        return java.util.List.of(com.strikerr212.minepc.lan.LanDevice.local(state.getValue(FACING), true, x, y, depth, true, false));
    }

    /** Скорость порта зависит от платы: 100 Мбит/с, 1 Гбит/с, 2,5 Гбит/с, 10 Гбит/с. Нет платы — нет порта. */
    @Override
    public long lanBitsPerSecond(Level level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof PcCaseBlockEntity pc)) return 0;
        com.strikerr212.minepc.hardware.MotherboardData board = pc.motherboard();
        if (board == null) return 0;
        return portBits(board);
    }

    /** Скорость сетевой платы: у серверной — 10 Гбит/с (SFP+), у обычной — по уровню. */
    public static long portBits(com.strikerr212.minepc.hardware.MotherboardData board) {
        return board.server() ? 10_000_000_000L : portBits(board.tier());
    }

    public static long portBits(int boardTier) {
        return switch (boardTier) {
            case 1 -> 100_000_000L;
            case 2, 3 -> 1_000_000_000L;
            case 4 -> 2_500_000_000L;
            default -> 10_000_000_000L;
        };
    }
}
