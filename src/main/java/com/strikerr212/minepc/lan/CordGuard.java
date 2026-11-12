package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.MineComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Висячие провода (патч-корды и воздушные линии) не проходят сквозь блоки: блок, поставленный поперёк провода,
 * не ставится; а если блок оказался там иначе (поршень, выросло дерево, упал песок) — провод обрывается и падает.
 */
@EventBusSubscriber(modid = MineComputers.MODID)
public final class CordGuard {

    private CordGuard() {
    }

    /** Игрок (или кто-то ещё) ставит блок: если он перегородил бы провод — не ставим. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (level.getBlockState(pos).getBlock() instanceof LanDevice) return;   // устройство на конце провода — можно
        for (LanData.Cord c : LanData.get(level).allCords()) {
            if (!CordShape.bounds(c.a(), c.b()).contains(Vec3.atCenterOf(pos))) continue;
            if (!level.isLoaded(c.a()) || !level.isLoaded(c.b())) continue;
            if (pos.equals(CordShape.blocker(level, c.a(), c.b()))) {
                event.setCanceled(true);
                if (event.getEntity() instanceof Player p) {
                    p.displayClientMessage(Component.translatable("message.minepc.cord.in_way")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                }
                return;
            }
        }
    }

    /** Раз в пару секунд: провода, через которые теперь проходит блок, обрываются. */
    public static void check(ServerLevel level) {
        LanData data = LanData.get(level);
        List<LanData.Cord> broken = new ArrayList<>();
        for (LanData.Cord c : data.allCords()) {
            if (!level.isLoaded(c.a()) || !level.isLoaded(c.b())) continue;
            if (CordShape.blocker(level, c.a(), c.b()) != null) broken.add(c);
        }
        for (LanData.Cord c : broken) {
            data.allCords().remove(c);
            data.setDirty();
            LanManager.invalidate(level);
            Vec3 mid = Vec3.atCenterOf(c.a()).lerp(Vec3.atCenterOf(c.b()), 0.5);
            ItemStack drop = c.length() > 4
                    ? new ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE.get(), c.length())
                    : new ItemStack(com.strikerr212.minepc.registry.ModItems.PATCH_CORD.get());
            Block.popResource(level, BlockPos.containing(mid), drop);
            level.playSound(null, BlockPos.containing(mid), SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 1f, 1f);
            CordList.refresh(level, c.a());
            CordList.refresh(level, c.b());
        }
    }
}
