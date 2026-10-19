package dev.midnight.minigames.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Weighted loot table loaded from config:
 * <pre>
 * rolls: 4-7
 * items:
 *   - {material: IRON_SWORD, weight: 10}
 *   - {material: ARROW, amount: 8-16, weight: 6}
 * </pre>
 */
public final class LootTable {

    private record Entry(ConfigurationSection item, int[] amount) {
    }

    private final WeightedTable<Entry> table = new WeightedTable<>();
    private final int[] rolls;

    private LootTable(int[] rolls) {
        this.rolls = rolls;
    }

    public static LootTable load(ConfigurationSection s) {
        LootTable lt = new LootTable(ConfigUtil.range(s == null ? null : s.get("rolls"), 1));
        if (s == null) {
            return lt;
        }
        for (ConfigurationSection e : ConfigUtil.sections(s, "items")) {
            lt.table.add(new Entry(e, ConfigUtil.range(e.get("amount"), 1)), e.getDouble("weight", 1));
        }
        return lt;
    }

    public boolean isEmpty() {
        return table.isEmpty();
    }

    public ItemStack roll(Random r) {
        Entry e = table.pick(r);
        if (e == null) {
            return null;
        }
        ItemStack stack = ItemFactory.build(e.item());
        stack.setAmount(Math.max(1, ConfigUtil.randomInRange(e.amount(), r)));
        return stack;
    }

    public List<ItemStack> rollAll(Random r) {
        List<ItemStack> out = new ArrayList<>();
        int n = ConfigUtil.randomInRange(rolls, r);
        for (int i = 0; i < n; i++) {
            ItemStack s = roll(r);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    /** Clears the inventory and scatters the rolled items over random slots. */
    public void fill(Inventory inv, Random r) {
        inv.clear();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, r);
        int idx = 0;
        for (ItemStack s : rollAll(r)) {
            if (idx >= slots.size()) {
                break;
            }
            inv.setItem(slots.get(idx++), s);
        }
    }
}
