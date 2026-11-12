package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.item.HardwareItem;
import com.strikerr212.minepc.lan.LanDevice;
import com.strikerr212.minepc.registry.ModBlockEntities;
import com.strikerr212.minepc.registry.ModBlocks;
import com.strikerr212.minepc.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Серверная стойка: рама на 4 модуля по 1U. Модули ставятся ПКМ модулем по свободному месту.
 * <ul>
 *     <li>ПКМ по модулю — выдвинуть его (остальные задвигаются); перед стойкой должно быть свободно;</li>
 *     <li>ПКМ по выдвинутому серверу — его окно (детали, питание, состояние), по выдвинутой консоли — выбор сервера
 *     и его экран; деталью в руке — поставить её в выдвинутый сервер;</li>
 *     <li>Shift + ПКМ (не блоком) — задвинуть выдвинутый; по задвинутому выключенному — вынуть модуль целиком.</li>
 * </ul>
 * Сзади у каждого модуля своё гнездо (порт 1 — нижний модуль).
 */
public class RackBlock extends HorizontalDirectionalBlock implements EntityBlock, LanDevice {

    public static final MapCodec<RackBlock> CODEC = simpleCodec(RackBlock::new);
    /** Насколько выдвигается модуль, пикселей. */
    public static final int TRAVEL = 11;

    public RackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RackBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.RACK.get()) return null;
        return (l, p, s, be) -> RackBlockEntity.serverTick(l, p, s, (RackBlockEntity) be);
    }

    // ======================= Где щёлкнули =======================

    /** Место по высоте точки щелчка (0 — нижнее). */
    public static int slotAt(BlockPos pos, BlockHitResult hit) {
        double y = hit.getLocation().y - pos.getY();
        return Math.max(0, Math.min(RackBlockEntity.SLOTS - 1, (int) Math.floor(y * RackBlockEntity.SLOTS)));
    }

    // ======================= Игрок =======================

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RackBlockEntity rack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int slot = slotAt(pos, hit);
        // Модуль в руке — в свободное место
        if (stack.is(ModItems.SERVER_MODULE.get()) || stack.is(ModItems.CONSOLE_MODULE.get())) {
            if (rack.kind(slot) != RackBlockEntity.EMPTY) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide && rack.insert(slot, stack)) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.6f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // Деталь в руке — в выдвинутый сервер
        if (stack.getItem() instanceof HardwareItem && rack.extended() == slot && rack.server(slot) != null && !player.isShiftKeyDown()) {
            if (!level.isClientSide) player.displayClientMessage(rack.server(slot).tryInstall(stack, player), true);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // Блоком в руке Shift + ПКМ — это «поставить блок рядом», не трогаем
        if (stack.getItem() instanceof BlockItem && player.isShiftKeyDown()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        InteractionResult r = use(level, pos, state, rack, slot, player);
        return r.consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RackBlockEntity rack)) return InteractionResult.PASS;
        return use(level, pos, state, rack, slotAt(pos, hit), player);
    }

    /** Общее для пустой руки и предмета: выдвинуть, задвинуть, открыть, вынуть. */
    public static InteractionResult use(Level level, BlockPos pos, BlockState state, RackBlockEntity rack, int slot, Player player) {
        int kind = rack.kind(slot);
        if (player.isShiftKeyDown()) {
            if (rack.extended() >= 0) {
                if (!level.isClientSide) retract(level, pos, state, rack);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (kind == RackBlockEntity.EMPTY) return InteractionResult.PASS;
            PcCaseBlockEntity s = rack.server(slot);
            if (s != null && s.computerState() == 1) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.minepc.rack.running")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                ItemStack out = rack.take(slot);
                if (!player.getInventory().add(out)) player.drop(out, false);
                level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.6f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (kind == RackBlockEntity.EMPTY) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.minepc.rack.empty"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (rack.extended() != slot) {
            if (!level.isClientSide) extend(level, pos, state, rack, slot, player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // Выдвинут: сервер — его окно, консоль — выбор сервера и экран
        if (kind == RackBlockEntity.CONSOLE) {
            if (level.isClientSide) com.strikerr212.minepc.client.ClientAccess.openConsole(pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            PcCaseBlockEntity s = rack.server(slot);
            if (s != null) player.openMenu(s, buf -> {
                buf.writeBlockPos(pos);
                buf.writeVarInt(slot);
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Выдвинуть модуль: перед стойкой ставится «выдвижная часть» (её видно и об неё можно споткнуться). */
    public static void extend(Level level, BlockPos pos, BlockState state, RackBlockEntity rack, int slot, Player player) {
        Direction f = state.getValue(FACING);
        BlockPos front = pos.relative(f);
        BlockState there = level.getBlockState(front);
        boolean ours = there.getBlock() instanceof RackDrawerBlock;
        if (!ours && !(there.canBeReplaced() && there.getFluidState().isEmpty())) {
            if (player != null) player.displayClientMessage(Component.translatable("message.minepc.rack.blocked", there.getBlock().getName())
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        rack.setExtended(slot);
        level.setBlock(front, ModBlocks.RACK_DRAWER.get().defaultBlockState()
                .setValue(RackDrawerBlock.FACING, f).setValue(RackDrawerBlock.SLOT, slot), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.3f);
    }

    /** Задвинуть выдвинутый модуль. */
    public static void retract(Level level, BlockPos pos, BlockState state, RackBlockEntity rack) {
        BlockPos front = pos.relative(state.getValue(FACING));
        if (level.getBlockState(front).getBlock() instanceof RackDrawerBlock) level.removeBlock(front, false);
        if (rack.extended() >= 0) {
            rack.setExtended(-1);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof RackBlockEntity rack) {
                BlockPos front = pos.relative(state.getValue(FACING));
                if (level.getBlockState(front).getBlock() instanceof RackDrawerBlock) level.removeBlock(front, false);
                rack.dropContents(level, pos);
            }
            super.onRemove(state, level, pos, newState, moved);
            if (!level.isClientSide) {
                com.strikerr212.minepc.lan.LanManager.updateDevice(level, pos);
                com.strikerr212.minepc.lan.CableBlockEntity.reconnectAround(level, pos);
            }
            return;
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide) {
            com.strikerr212.minepc.lan.LanManager.updateDevice(level, pos);
            com.strikerr212.minepc.lan.CableBlockEntity.reconnectAround(level, pos);
        }
    }

    // ======================= Консоль и сеть =======================

    /** Стойки одной колонны (вплотную друг над другом), от нижней к верхней. */
    public static List<BlockPos> column(Level level, BlockPos pos) {
        BlockPos bottom = pos;
        for (int i = 0; i < 16 && level.getBlockState(bottom.below()).getBlock() instanceof RackBlock; i++) bottom = bottom.below();
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p = bottom; out.size() < 32 && level.getBlockState(p).getBlock() instanceof RackBlock; p = p.above()) out.add(p.immutable());
        return out;
    }

    /** Есть ли в колонне стоек модуль консоли (тогда у серверов есть клавиатура). */
    public static boolean hasConsole(Level level, BlockPos pos) {
        for (BlockPos p : column(level, pos)) {
            if (level.getBlockEntity(p) instanceof RackBlockEntity r && r.hasConsole()) return true;
        }
        return false;
    }

    /** Скорость сетевого порта сервера по его плате, бит/с (0 — платы нет). */
    public static long moduleBits(PcCaseBlockEntity be) {
        var board = be.motherboard();
        return board == null ? 0 : PcCaseBlock.portBits(board);
    }

    @Override
    public Kind lanKind() {
        return Kind.COMPUTER;
    }

    @Override
    public int lanType(BlockState state) {
        return com.strikerr212.minepc.lan.LanData.RACK;
    }

    @Override
    public boolean acceptsCable(BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    /** Гнёзда сзади, по одному на модуль: порт 1 — нижний модуль. */
    @Override
    public List<Socket> sockets(BlockState state) {
        Direction f = state.getValue(FACING);
        List<Socket> out = new ArrayList<>();
        for (int i = 0; i < RackBlockEntity.SLOTS; i++) out.add(LanDevice.local(f, true, 7, 1 + 4 * i, 0, true, false));
        return out;
    }

    @Override
    public long lanBitsPerSecond(Level level, BlockPos pos, BlockState state) {
        long best = 0;
        if (level.getBlockEntity(pos) instanceof RackBlockEntity rack) {
            for (int i = 0; i < RackBlockEntity.SLOTS; i++) {
                PcCaseBlockEntity s = rack.server(i);
                if (s != null) best = Math.max(best, moduleBits(s));
            }
        }
        return best;
    }

    /** Скорость каждого модуля — по 8 бит, в сотнях Мбит/с (см. LanData.Device.bitsFor). */
    @Override
    public int lanExtra(Level level, BlockPos pos, BlockState state) {
        int extra = 0;
        if (level.getBlockEntity(pos) instanceof RackBlockEntity rack) {
            for (int i = 0; i < RackBlockEntity.SLOTS; i++) {
                PcCaseBlockEntity s = rack.server(i);
                long b = s == null ? 0 : moduleBits(s);
                extra |= (int) Math.min(255, b / 100_000_000L) << (8 * i);
            }
        }
        return extra;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.minepc.server_rack").withStyle(ChatFormatting.GRAY));
    }
}
