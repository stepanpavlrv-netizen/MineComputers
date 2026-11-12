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
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
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
            if (!support.isFaceSturdy(level, clicked, ctx.getClickedFace())) return InteractionResult.FAIL;
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
        // Цвет: краситель во второй руке, иначе синий (оптика — жёлтая)
        int color = (fiber ? DyeColor.YELLOW : DyeColor.BLUE).getId();
        if (ctx.getPlayer() != null) {
            var other = ctx.getPlayer().getItemInHand(ctx.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                    ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
            if (other.getItem() instanceof net.minecraft.world.item.DyeItem dye) color = dye.getDyeColor().getId();
        }
        Wire wire = CableBlockEntity.make(color, fiber, face, lanes[0], lanes[1]);
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
        tooltip.add(Component.translatable("tooltip.minepc.cable").withStyle(net.minecraft.ChatFormatting.GRAY));
        if (fiber) tooltip.add(Component.translatable("tooltip.minepc.fiber").withStyle(net.minecraft.ChatFormatting.GOLD));
    }
}
