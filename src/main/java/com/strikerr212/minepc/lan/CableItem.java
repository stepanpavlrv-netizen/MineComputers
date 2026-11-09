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
 * Моток сетевого кабеля. ПКМ по полу — положить провод; куда кликнул внутри блока, на ту дорожку он и ляжет
 * (до 4 проводов в блоке, каждый на своей дорожке, — так они входят в разные гнёзда коммутатора).
 */
public class CableItem extends Item {

    public CableItem(Properties properties) {
        super(properties);
    }

    private static int lane(double pixel) {
        return Math.max(0, Math.min(3, (int) (pixel / 4)));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        BlockPos pos;
        Vec3 hit = ctx.getClickLocation();
        if (level.getBlockState(clicked).getBlock() instanceof CableBlock) {
            pos = clicked;                                   // ещё один провод в тот же блок
        } else {
            if (ctx.getClickedFace() != Direction.UP) return InteractionResult.FAIL;
            pos = clicked.above();
            BlockState there = level.getBlockState(pos);
            if (!(there.getBlock() instanceof CableBlock)) {
                if (!there.canBeReplaced(new BlockPlaceContext(ctx))) return InteractionResult.FAIL;
                BlockState cable = ModBlocks.CABLE.get().defaultBlockState();
                if (!cable.canSurvive(level, pos)) return InteractionResult.FAIL;
                if (!level.isClientSide) level.setBlockAndUpdate(pos, cable);
            }
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof CableBlockEntity be)) return InteractionResult.FAIL;
        double px = (hit.x - pos.getX()) * 16, pz = (hit.z - pos.getZ()) * 16;
        int[] lanes = be.nearestFree(lane(px), lane(pz));
        if (lanes == null) {
            if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("message.minepc.cable.full"), true);
            return InteractionResult.FAIL;
        }
        be.add(new Wire(DyeColor.BLUE.getId(), lanes[0], lanes[1]));
        be.reconnect();
        be.reconnectNeighbours();
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1f, 1.1f);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.minepc.cable").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
