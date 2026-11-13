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
    public static final int TRAVEL = 14;

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
        if (stack.getItem() instanceof com.strikerr212.minepc.item.RackModuleItem) {
            if (rack.kind(slot) != RackBlockEntity.EMPTY) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide && rack.insert(slot, stack)) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.6f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        slot = rack.base(slot);   // верхняя половина модуля 2U — это он же
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
        return use(level, pos, state, rack, rack.base(slotAt(pos, hit)), player);
    }

    /** Общее для пустой руки и предмета: выдвинуть, задвинуть, открыть, вынуть. */
    public static InteractionResult use(Level level, BlockPos pos, BlockState state, RackBlockEntity rack, int slot, Player player) {
        int kind = rack.kind(slot);
        if (player.isShiftKeyDown()) {
            if (rack.extended() >= 0) {
                if (!level.isClientSide) retract(level, pos, state, rack);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (kind == RackBlockEntity.EMPTY || kind == RackBlockEntity.UPPER) return InteractionResult.PASS;
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
        // Сетевые модули не выдвигаются: щелчок — окно портов шины стойки (коммутатор, роутер, патч-панель)
        if (RackBlockEntity.isNetwork(kind)) {
            if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                if (!hasModule(level, pos, RackBlockEntity.SWITCH)) {
                    sp.displayClientMessage(Component.translatable("message.minepc.rack.no_switch").withStyle(ChatFormatting.YELLOW), true);
                }
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                        com.strikerr212.minepc.lan.SwitchPorts.openPayload((net.minecraft.server.level.ServerLevel) level, pos));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (kind == RackBlockEntity.UPS || kind == RackBlockEntity.BLANK) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable(kind == RackBlockEntity.UPS
                    ? "message.minepc.rack.ups" : "message.minepc.rack.blank"), true);
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
                .setValue(RackDrawerBlock.FACING, f).setValue(RackDrawerBlock.SLOT, slot)
                .setValue(RackDrawerBlock.TALL, rack.size(slot) == 2), Block.UPDATE_ALL);
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

    /**
     * Стойки одного ряда: колонна (вплотную друг над другом) и стойки бок о бок, смотрящие в ту же сторону.
     * У них общая шина: сетевые модули и консоль работают на весь ряд.
     */
    public static List<BlockPos> group(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        List<BlockPos> out = new ArrayList<>();
        BlockState start = level.getBlockState(pos);
        if (!(start.getBlock() instanceof RackBlock)) return out;
        Direction facing = start.getValue(FACING);
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        q.add(pos.immutable());
        seen.add(pos.immutable());
        while (!q.isEmpty() && out.size() < 64) {
            BlockPos p = q.poll();
            out.add(p);
            for (Direction d : new Direction[]{Direction.UP, Direction.DOWN, facing.getClockWise(), facing.getCounterClockWise()}) {
                BlockPos n = p.relative(d).immutable();
                if (seen.contains(n)) continue;
                BlockState ns = level.getBlockState(n);
                if (ns.getBlock() instanceof RackBlock && ns.getValue(FACING) == facing) {
                    seen.add(n);
                    q.add(n);
                }
            }
        }
        out.sort(java.util.Comparator.comparingInt((BlockPos p) -> p.getY()).reversed()
                .thenComparingInt(p -> p.getX() * facing.getClockWise().getStepX() + p.getZ() * facing.getClockWise().getStepZ()));
        return out;
    }

    /** Есть ли в ряду стоек модуль этого вида. */
    public static boolean hasModule(net.minecraft.world.level.BlockGetter level, BlockPos pos, int kind) {
        for (BlockPos p : group(level, pos)) {
            if (level.getBlockEntity(p) instanceof RackBlockEntity r && r.has(kind)) return true;
        }
        return false;
    }

    /** Есть ли в ряду стоек модуль консоли (тогда у серверов есть клавиатура). */
    public static boolean hasConsole(Level level, BlockPos pos) {
        return hasModule(level, pos, RackBlockEntity.CONSOLE);
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
        Direction f = state.getValue(FACING);
        return face == f.getOpposite() || face == f;
    }

    @Override
    public boolean acceptsFiber(BlockState state, Direction face) {
        return acceptsCable(state, face);
    }

    /** Без содержимого (предмет, тесты): гнёзд нет — они появляются с модулями. */
    @Override
    public List<Socket> sockets(BlockState state) {
        return sockets(state, new int[RackBlockEntity.SLOTS]);
    }

    @Override
    public List<Socket> sockets(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        int[] kinds = new int[RackBlockEntity.SLOTS];
        if (level.getBlockEntity(pos) instanceof RackBlockEntity r) for (int i = 0; i < kinds.length; i++) kinds[i] = r.kind(i);
        return sockets(state, kinds);
    }

    /**
     * Гнёзда по модулям. Порт i (0–3) — главное гнездо места i: у сервера — сзади (как раньше), у коммутатора —
     * SFP спереди, у патч-панели — сзади, у роутера — WAN спереди. Порты 4+3i … 6+3i — ещё три гнезда модуля:
     * у коммутатора и роутера — спереди, у патч-панели — сзади (кабели здания). У остальных модулей гнёзд нет.
     */
    static List<Socket> sockets(BlockState state, int[] kinds) {
        Direction f = state.getValue(FACING);
        Socket none = new Socket(f.getOpposite(), 0, 0, 0, false, false);
        Socket[] main = new Socket[RackBlockEntity.SLOTS];
        Socket[] extra = new Socket[RackBlockEntity.SLOTS * 3];
        java.util.Arrays.fill(extra, none);
        for (int i = 0; i < RackBlockEntity.SLOTS; i++) {
            int y = 1 + 4 * i, k = kinds[i];
            main[i] = switch (k) {
                case RackBlockEntity.SWITCH -> LanDevice.local(f, false, 11, y, 0, false, true);
                case RackBlockEntity.PATCH -> LanDevice.local(f, true, 7, y, 0, true, false);
                case RackBlockEntity.ROUTER -> LanDevice.local(f, false, 11, y, 0, true, false);
                // У пустого места гнезда нет: иначе провод сзади втыкался «в никуда» мимо гнёзд модулей
                case RackBlockEntity.EMPTY, RackBlockEntity.CONSOLE, RackBlockEntity.UPS, RackBlockEntity.BLANK, RackBlockEntity.UPPER -> none;
                default -> LanDevice.local(f, true, 7, y, 0, true, false);    // сервер
            };
            for (int j = 0; j < 3; j++) {
                extra[3 * i + j] = switch (k) {
                    case RackBlockEntity.SWITCH, RackBlockEntity.ROUTER -> LanDevice.local(f, false, 3 + 2 * j, y, 0, true, false);
                    case RackBlockEntity.PATCH -> LanDevice.local(f, true, j == 2 ? 11 : 3 + 2 * j, y, 0, j < 2, j == 2);
                    default -> none;
                };
            }
        }
        List<Socket> out = new ArrayList<>(List.of(main));
        out.addAll(List.of(extra));
        return out;
    }

    /** Для схемы сети: виды модулей по местам (по 4 бита) и куда смотрит стойка. */
    @Override
    public int lanMaskExtra(Level level, BlockPos pos, BlockState state) {
        int m = state.getValue(FACING).get2DDataValue() << 16;
        if (level.getBlockEntity(pos) instanceof RackBlockEntity rack) {
            for (int i = 0; i < RackBlockEntity.SLOTS; i++) m |= (rack.kind(i) & 15) << (4 * i);
        }
        return m;
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
