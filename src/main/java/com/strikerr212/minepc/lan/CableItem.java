package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Моток сетевого кабеля. ПКМ по любой грани — положить провод; куда кликнул, на ту дорожку он и ляжет
 * (до 6 проводов на грани клетки, каждый на своей дорожке). Краситель во второй руке — цвет провода.
 */
public class CableItem extends Item {

    private final boolean fiber;

    public CableItem(Properties properties) {
        this(properties, false);
    }

    /** fiber — оптоволокно (жёлтое, до 2000 блоков, только в стоечный коммутатор). */
    public CableItem(Properties properties, boolean fiber) {
        super(properties);
        this.fiber = fiber;
    }

    public boolean fiber() {
        return fiber;
    }

    /**
     * ПКМ по грани блока — провод ложится на эту грань (пол, стена, потолок, верх блока), как лишайник.
     * ПКМ по уже лежащему проводу — ещё один провод на ту же грань той же клетки.
     */
    // ======================= Маршрут и воздушная линия =======================

    /** Откуда начат маршрут (первое устройство) или null. */
    public static BlockPos routeFrom(net.minecraft.world.item.ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var t = data.copyTag();
        return t.contains("route_from") ? BlockPos.of(t.getLong("route_from")) : null;
    }

    private static void setRouteFrom(net.minecraft.world.item.ItemStack stack, BlockPos pos) {
        if (pos == null) {
            stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        } else {
            var t = new net.minecraft.nbt.CompoundTag();
            t.putLong("route_from", pos.asLong());
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(t));
        }
    }

    @Override
    public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
        return routeFrom(stack) != null;
    }

    /** Цвет нового провода: краситель во второй руке, иначе синий (оптика — жёлтая). */
    public int colorFor(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        int color = (fiber ? DyeColor.YELLOW : DyeColor.BLUE).getId();
        if (player != null) {
            var other = player.getItemInHand(hand == net.minecraft.world.InteractionHand.MAIN_HAND
                    ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
            if (other.getItem() instanceof net.minecraft.world.item.DyeItem dye) color = dye.getDyeColor().getId();
        }
        return color;
    }

    /**
     * ПКМ по устройству: первый щелчок запоминает начало, второй (по другому устройству) прокладывает кабель от гнезда
     * до гнезда по полу — или натягивает воздушную линию, если оба конца — кронштейны. Shift + ПКМ — отменить начало.
     */
    private InteractionResult useOnDevice(UseOnContext ctx, BlockPos pos) {
        Level level = ctx.getLevel();
        var stack = ctx.getItemInHand();
        var player = ctx.getPlayer();
        BlockPos from = routeFrom(stack);
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player != null && player.isShiftKeyDown()) {
            setRouteFrom(stack, null);
            player.displayClientMessage(Component.translatable("message.minepc.route.cancel"), true);
            return InteractionResult.CONSUME;
        }
        if (from == null || from.equals(pos) || !(level.getBlockState(from).getBlock() instanceof LanDevice)) {
            setRouteFrom(stack, pos);
            if (player != null) player.displayClientMessage(Component.translatable(
                    level.getBlockState(pos).getBlock() instanceof BracketBlock ? "message.minepc.route.start_aerial" : "message.minepc.route.start",
                    level.getBlockState(pos).getBlock().getName()), true);
            level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6f, 1.4f);
            return InteractionResult.CONSUME;
        }
        boolean creative = player != null && player.getAbilities().instabuild;
        int color = colorFor(player, ctx.getHand());
        // Воздушная линия между двумя кронштейнами
        if (level.getBlockState(from).getBlock() instanceof BracketBlock && level.getBlockState(pos).getBlock() instanceof BracketBlock
                && level instanceof net.minecraft.server.level.ServerLevel server) {
            double dist = Math.sqrt(from.distSqr(pos));
            if (fiber) return fail(player, "message.minepc.route.aerial_fiber");
            if (dist > BracketBlock.MAX_SPAN) return fail(player, "message.minepc.route.too_far", BracketBlock.MAX_SPAN);
            BlockPos blocked = CordShape.blocker(level, from, pos);
            if (blocked != null) return fail(player, "message.minepc.cord.blocked", level.getBlockState(blocked).getBlock().getName(),
                    blocked.getX() + " " + blocked.getY() + " " + blocked.getZ());
            LanData data = LanData.get(server);
            int pa = freeCord(data, from), pb = freeCord(data, pos);
            if (pa < 0 || pb < 0) return fail(player, "message.minepc.route.bracket_full");
            LanData.Cord cord = new LanData.Cord(from, pa, pos, pb, color, CableLabels.id(level, stack));
            if (!creative && stack.getCount() < cord.length()) return fail(player, "message.minepc.route.short", cord.length());
            data.addCord(cord);
            CordList.refresh(server, from);
            CordList.refresh(server, pos);
            if (!creative) stack.shrink(cord.length());
            setRouteFrom(stack, null);
            level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1f, 1f);
            if (player != null) player.displayClientMessage(Component.translatable("message.minepc.route.aerial_done", cord.length()), true);
            return InteractionResult.CONSUME;
        }
        RoutePlanner.Result found = RoutePlanner.find(level, from, pos, fiber);
        RoutePlanner.Route route = found.route();
        if (route == null) return fail(player, found.reason(),
                level.getBlockState(found.stuck() != null ? found.stuck() : pos).getBlock().getName());
        int need = route.pieces().size();
        if (!creative && stack.getCount() < need) return fail(player, "message.minepc.route.short", need);
        RoutePlanner.lay(level, route, color, fiber, CableLabels.id(level, stack));
        if (!creative) stack.shrink(need);
        setRouteFrom(stack, null);
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1f, 1.1f);
        if (player != null) player.displayClientMessage(Component.translatable("message.minepc.route.done", route.length(), need), true);
        return InteractionResult.CONSUME;
    }

    private static int freeCord(LanData data, BlockPos pos) {
        for (int port = LanData.CABLE_PORTS; port < LanData.PORTS; port++) if (!data.cordAt(pos, port)) return port;
        return -1;
    }

    private static InteractionResult fail(net.minecraft.world.entity.player.Player player, String key, Object... args) {
        if (player != null) player.displayClientMessage(Component.translatable(key, args).withStyle(net.minecraft.ChatFormatting.RED), true);
        return InteractionResult.FAIL;
    }

    /** Щелчок по устройству перехватываем раньше самого устройства (иначе оно откроет своё окно). */
    @Override
    public InteractionResult onItemUseFirst(net.minecraft.world.item.ItemStack stack, UseOnContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        if (ctx.getLevel().getBlockState(pos).getBlock() instanceof LanDevice) return useOnDevice(ctx, pos);
        return InteractionResult.PASS;
    }

    // ======================= Моток с длиной =======================

    /** Полоска под значком — сколько кабеля осталось в мотке (полный моток — 64 м). */
    @Override
    public boolean isBarVisible(net.minecraft.world.item.ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(net.minecraft.world.item.ItemStack stack) {
        return Math.round(13f * Math.min(64, stack.getCount()) / 64f);
    }

    @Override
    public int getBarColor(net.minecraft.world.item.ItemStack stack) {
        return fiber ? 0xE8C23A : 0x3C78D8;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        if (level.getBlockState(clicked).getBlock() instanceof LanDevice) return InteractionResult.PASS;
        Vec3 hit = ctx.getClickLocation();
        BlockPos pos;
        Direction face;
        if (level.getBlockEntity(clicked) instanceof CableBlockEntity there && there.facade() == null && !there.wires().isEmpty()) {
            pos = clicked;
            Vec3 at = hit.subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16);
            Wire near = there.nearest(at.x, at.y, at.z);
            face = near.face;
        } else {
            face = ctx.getClickedFace().getOpposite();       // провод прижат к блоку, по которому щёлкнули
            pos = clicked.relative(ctx.getClickedFace());
            BlockState support = level.getBlockState(clicked);
            if (!CableSupport.holds(level, clicked, ctx.getClickedFace())) return InteractionResult.FAIL;
            BlockState here = level.getBlockState(pos);
            if (!(here.getBlock() instanceof CableBlock)) {
                if (!here.canBeReplaced(new BlockPlaceContext(ctx))) return InteractionResult.FAIL;
                if (!level.isClientSide) level.setBlockAndUpdate(pos, ModBlocks.CABLE.get().defaultBlockState());
            }
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof CableBlockEntity be)) return InteractionResult.FAIL;
        Direction.Axis[] ax = Wire.planeAxes(face);
        double[] px = {(hit.x - pos.getX()) * 16, (hit.y - pos.getY()) * 16, (hit.z - pos.getZ()) * 16};
        int[] lanes = be.nearestFree(face, CableBlockEntity.laneAt(px[ax[0].ordinal()]), CableBlockEntity.laneAt(px[ax[1].ordinal()]));
        if (lanes == null) {
            if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("message.minepc.cable.full"), true);
            if (be.wires().isEmpty()) level.removeBlock(pos, false);
            return InteractionResult.FAIL;
        }
        int color = colorFor(ctx.getPlayer(), ctx.getHand());
        Wire wire = CableBlockEntity.make(color, fiber, face, lanes[0], lanes[1]);
        wire.label = CableLabels.id(level, ctx.getItemInHand());
        be.add(wire);
        be.reconnect();
        be.reconnectNeighbours();
        be.reconnect();
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1f, 1.1f);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.minepc.cable_length", stack.getCount()).withStyle(net.minecraft.ChatFormatting.WHITE));
        tooltip.add(Component.translatable("tooltip.minepc.cable").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.minepc.cable_route").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        if (fiber) tooltip.add(Component.translatable("tooltip.minepc.fiber").withStyle(net.minecraft.ChatFormatting.GOLD));
        String label = CableLabels.of(stack);
        tooltip.add(label.isEmpty() ? Component.translatable("tooltip.minepc.cable_label_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                : Component.translatable("tooltip.minepc.cable_label", label).withStyle(net.minecraft.ChatFormatting.WHITE));
    }
}
