package com.strikerr212.minepc.menu;

import com.strikerr212.minepc.hardware.HardwarePresets;
import com.strikerr212.minepc.hardware.spec.Cost;
import com.strikerr212.minepc.hardware.spec.PartDesign;
import com.strikerr212.minepc.hardware.spec.PartSpec;
import com.strikerr212.minepc.hardware.spec.Specs;
import com.strikerr212.minepc.registry.ModBlocks;
import com.strikerr212.minepc.registry.ModMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Логика сборочного стола (работает на сервере).
 *
 * Экран на клиенте ничего не меняет сам: он отправляет "нажатия кнопок" с номерами,
 * а сервер меняет выбранные варианты. Числа в {@link #data} автоматически
 * передаются обратно на клиент, и экран показывает актуальное состояние.
 */
public class AssemblyMenu extends AbstractContainerMenu {

    // ----- Номера кнопок -----
    public static final int BTN_TYPE = 0;      // 0..7 — вкладки деталей
    public static final int BTN_PAGE_PREV = 10;
    public static final int BTN_PAGE_NEXT = 11;
    public static final int BTN_BUILD = 12;
    public static final int BTN_RESET = 13;
    public static final int BTN_PARAM = 20;    // 20 + номер параметра * 2 (+1 — вперёд, +0 — назад)

    // ----- Раскладка данных -----
    private static final int DATA_TYPE = 0;
    private static final int DATA_PAGE = 1;
    private static final int DATA_VALUES = 2;

    private final ContainerLevelAccess access;
    private final ContainerData data = new SimpleContainerData(DATA_VALUES + Specs.MAX_PARAMS);
    /** Сервер помнит выбор на каждой вкладке, чтобы при переключении он не терялся. */
    private final List<List<Integer>> remembered = new ArrayList<>();
    private String label = "";

    /** Конструктор для клиента. */
    public AssemblyMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    /** Конструктор для сервера. */
    public AssemblyMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.ASSEMBLY.get(), containerId);
        this.access = access;
        for (PartSpec spec : Specs.ALL) {
            remembered.add(new ArrayList<>(spec.defaults()));
        }
        addDataSlots(data);
        showType(0);
    }

    // ======================= Чтение состояния (и клиентом, и сервером) =======================

    public int typeIndex() {
        return Math.max(0, Math.min(Specs.ALL.size() - 1, data.get(DATA_TYPE)));
    }

    public PartSpec spec() {
        return Specs.ALL.get(typeIndex());
    }

    public int page() {
        return Math.max(0, Math.min(spec().pages() - 1, data.get(DATA_PAGE)));
    }

    public List<Integer> values() {
        PartSpec spec = spec();
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < spec.params().size(); i++) {
            list.add(data.get(DATA_VALUES + i));
        }
        return spec.sanitize(list);
    }

    // ======================= Изменение (только сервер) =======================

    private void showType(int type) {
        data.set(DATA_TYPE, type);
        data.set(DATA_PAGE, 0);
        List<Integer> v = remembered.get(type);
        for (int i = 0; i < Specs.MAX_PARAMS; i++) {
            data.set(DATA_VALUES + i, i < v.size() ? v.get(i) : 0);
        }
    }

    private void setValues(List<Integer> v) {
        remembered.set(typeIndex(), new ArrayList<>(v));
        for (int i = 0; i < v.size(); i++) {
            data.set(DATA_VALUES + i, v.get(i));
        }
    }

    public void setLabel(String text) {
        String clean = text == null ? "" : ChatFormatting.stripFormatting(text).trim();
        label = clean.length() > PartDesign.MAX_LABEL ? clean.substring(0, PartDesign.MAX_LABEL) : clean;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        PartSpec spec = spec();
        if (id >= BTN_TYPE && id < BTN_TYPE + Specs.ALL.size()) {
            showType(id - BTN_TYPE);
            return true;
        }
        if (id == BTN_PAGE_PREV || id == BTN_PAGE_NEXT) {
            int page = page() + (id == BTN_PAGE_NEXT ? 1 : -1);
            data.set(DATA_PAGE, Math.floorMod(page, spec.pages()));
            return true;
        }
        if (id == BTN_RESET) {
            setValues(spec.defaults());
            return true;
        }
        if (id == BTN_BUILD) {
            build(player);
            return true;
        }
        if (id >= BTN_PARAM) {
            int param = (id - BTN_PARAM) / 2;
            boolean forward = (id - BTN_PARAM) % 2 == 1;
            if (param >= spec.params().size()) {
                return false;
            }
            List<Integer> v = new ArrayList<>(values());
            int count = spec.params().get(param).count();
            v.set(param, Math.floorMod(v.get(param) + (forward ? 1 : -1), count));
            setValues(v);
            return true;
        }
        return false;
    }

    /** Собрать деталь: проверить и списать материалы, выдать предмет. */
    private void build(Player player) {
        PartSpec spec = spec();
        List<Integer> v = values();
        Cost cost = spec.cost(v);
        boolean free = player.getAbilities().instabuild;
        if (!free && !hasMaterials(player, cost)) {
            player.displayClientMessage(Component.translatable("message.minepc.no_materials")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!free) {
            consume(player, cost);
        }
        ItemStack result = HardwarePresets.build(spec.type(), label, v);
        Component name = result.getHoverName();
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
        player.displayClientMessage(Component.translatable("message.minepc.built", name)
                .withStyle(ChatFormatting.GREEN), true);
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1f, 1f));
    }

    public static boolean hasMaterials(Player player, Cost cost) {
        for (Cost.Entry e : cost.entries()) {
            if (player.getInventory().countItem(e.item()) < e.count()) {
                return false;
            }
        }
        return true;
    }

    private static void consume(Player player, Cost cost) {
        Inventory inventory = player.getInventory();
        for (Cost.Entry e : cost.entries()) {
            int left = e.count();
            for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.is(e.item())) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        inventory.setChanged();
    }

    // ======================= Обязательное для меню =======================

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;   // слотов нет
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ASSEMBLY_TABLE.get());
    }
}
