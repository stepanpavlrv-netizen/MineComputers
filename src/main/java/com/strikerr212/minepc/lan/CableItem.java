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
 * Моток сетевого кабеля. Провод прокладывается щелчками: ПКМ по гнезду (или по полу, стене) — начало, дальше ПКМ —
 * точки излома (до пикселя), ПКМ по гнезду другого устройства — конец, провод ложится целиком. Повторный щелчок
 * по последней точке — закончить со свободным концом; Shift + ПКМ — убрать последнюю точку. Два гнезда без изломов
 * между ними — путь найдётся сам («от гнезда до гнезда»), два кронштейна — воздушная линия.
 * Краситель во второй руке — цвет провода.
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

    // ======================= Прокладка щелчками =======================
    // Точки ставит игрок (CableDrawClient у него), сервер получает весь путь и кладёт провод (CableLaying).

    /** ПКМ по блоку — точка пути (по устройству — гнездо). Перехватываем раньше устройства, чтобы не открылось его окно. */
    @Override
    public InteractionResult onItemUseFirst(net.minecraft.world.item.ItemStack stack, UseOnContext ctx) {
        if (ctx.getLevel().isClientSide) {
            com.strikerr212.minepc.client.ClientAccess.cableClick(ctx.getHand(), ctx.getClickedPos(), ctx.getClickedFace(), ctx.getClickLocation());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    /** ПКМ в воздух: Shift — убрать последнюю точку. */
    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
                                                                                               net.minecraft.world.InteractionHand hand) {
        if (level.isClientSide) com.strikerr212.minepc.client.ClientAccess.cableAir(hand);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /** Воздушная линия между двумя кронштейнами (мотком: щелчок по одному, потом по другому). */
    public static boolean aerial(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.player.Player player,
                                 net.minecraft.world.item.ItemStack stack, BlockPos from, BlockPos pos, int color) {
        boolean creative = player != null && player.getAbilities().instabuild;
        double dist = Math.sqrt(from.distSqr(pos));
        if (stack.getItem() instanceof CableItem c && c.fiber) return fail(player, "message.minepc.route.aerial_fiber");
        if (dist > BracketBlock.MAX_SPAN) return fail(player, "message.minepc.route.too_far", BracketBlock.MAX_SPAN);
        BlockPos blocked = CordShape.blocker(level, from, pos);
        if (blocked != null) return fail(player, "message.minepc.cord.blocked", level.getBlockState(blocked).getBlock().getName(),
                blocked.getX() + " " + blocked.getY() + " " + blocked.getZ());
        LanData data = LanData.get(level);
        int pa = freeCord(data, from), pb = freeCord(data, pos);
        if (pa < 0 || pb < 0) return fail(player, "message.minepc.route.bracket_full");
        LanData.Cord cord = new LanData.Cord(from, pa, pos, pb, color, CableLabels.id(level, stack));
        if (!creative && stack.getCount() < cord.length()) return fail(player, "message.minepc.route.short", cord.length());
        data.addCord(cord);
        CordList.refresh(level, from);
        CordList.refresh(level, pos);
        if (!creative) stack.shrink(cord.length());
        level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1f, 1f);
        if (player != null) player.displayClientMessage(Component.translatable("message.minepc.route.aerial_done", cord.length()), true);
        return true;
    }

    private static int freeCord(LanData data, BlockPos pos) {
        for (int port = LanData.CABLE_PORTS; port < LanData.PORTS; port++) if (!data.cordAt(pos, port)) return port;
        return -1;
    }

    static boolean fail(net.minecraft.world.entity.player.Player player, String key, Object... args) {
        if (player != null) player.displayClientMessage(Component.translatable(key, args).withStyle(net.minecraft.ChatFormatting.RED), true);
        return false;
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
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.minepc.cable_length", stack.getCount()).withStyle(net.minecraft.ChatFormatting.WHITE));
        tooltip.add(Component.translatable("tooltip.minepc.cable").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.minepc.cable_draw").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.minepc.cable_draw2").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        if (fiber) tooltip.add(Component.translatable("tooltip.minepc.fiber").withStyle(net.minecraft.ChatFormatting.GOLD));
        String label = CableLabels.of(stack);
        tooltip.add(label.isEmpty() ? Component.translatable("tooltip.minepc.cable_label_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                : Component.translatable("tooltip.minepc.cable_label", label).withStyle(net.minecraft.ChatFormatting.WHITE));
    }
}
