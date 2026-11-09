package com.strikerr212.minepc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Окраска корпусов и мониторов: ПКМ красителем — покрасить, ПКМ ведром воды — смыть краску.
 * Красится основа корпуса (в моделях это грани с tintindex 0); лампочки, экраны и детали остаются как были.
 */
public enum Paint implements StringRepresentable {
    NONE(null),
    WHITE(DyeColor.WHITE), ORANGE(DyeColor.ORANGE), MAGENTA(DyeColor.MAGENTA), LIGHT_BLUE(DyeColor.LIGHT_BLUE),
    YELLOW(DyeColor.YELLOW), LIME(DyeColor.LIME), PINK(DyeColor.PINK), GRAY(DyeColor.GRAY),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY), CYAN(DyeColor.CYAN), PURPLE(DyeColor.PURPLE), BLUE(DyeColor.BLUE),
    BROWN(DyeColor.BROWN), GREEN(DyeColor.GREEN), RED(DyeColor.RED), BLACK(DyeColor.BLACK);

    public static final EnumProperty<Paint> PROPERTY = EnumProperty.create("paint", Paint.class);

    public final DyeColor dye;

    Paint(DyeColor dye) {
        this.dye = dye;
    }

    @Override
    public String getSerializedName() {
        return dye == null ? "none" : dye.getSerializedName();
    }

    public static Paint of(DyeColor dye) {
        for (Paint p : values()) if (p.dye == dye) return p;
        return NONE;
    }

    /**
     * Цвет-множитель для граней с краской: некрашеный блок — свой цвет основы (base), крашеный — цвет красителя.
     * Белый краситель чуть светлее, чёрный — не совсем чёрный, чтобы были видны объём и шум материала.
     */
    public int tint(int base) {
        if (dye == null) return base;
        return switch (dye) {
            case WHITE -> 0xFFFFFF;
            case BLACK -> 0x3A3A40;
            default -> dye.getTextureDiffuseColor() & 0xFFFFFF;
        };
    }

    /**
     * ПКМ предметом по крашеному блоку: краситель красит, ведро воды смывает краску.
     * Возвращает null, если предмет не про краску.
     */
    public static ItemInteractionResult use(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player) {
        Paint target;
        if (stack.getItem() instanceof DyeItem dyeItem) {
            target = of(dyeItem.getDyeColor());
        } else if (stack.is(Items.WATER_BUCKET) && state.getValue(PROPERTY) != NONE) {
            target = NONE;
        } else {
            return null;
        }
        if (state.getValue(PROPERTY) == target) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, state.setValue(PROPERTY, target));
            level.playSound(null, pos, target == NONE ? SoundEvents.BUCKET_EMPTY : SoundEvents.DYE_USE, SoundSource.BLOCKS, 1f, 1f);
            if (target != NONE && !player.getAbilities().instabuild) stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
