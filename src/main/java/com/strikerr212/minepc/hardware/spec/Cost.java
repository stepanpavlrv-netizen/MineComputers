package com.strikerr212.minepc.hardware.spec;

import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Стоимость детали в материалах. */
public final class Cost {

    public record Entry(Item item, int count) {
    }

    private final Map<Item, Integer> amounts = new LinkedHashMap<>();

    public Cost add(Item item, int count) {
        if (count > 0) {
            amounts.merge(item, count, Integer::sum);
        }
        return this;
    }

    public List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        amounts.forEach((item, count) -> list.add(new Entry(item, count)));
        return list;
    }
}
