package dev.midnight.minigames.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/** Shared "Lucky Mode" helpers: a configurable block that drops random loot when broken. */
public final class LuckyBlocks {

    private LuckyBlocks() {
    }

    /** games/&lt;id&gt;.yml -&gt; lucky.block */
    public static Material material(YamlConfiguration gameConfig) {
        return ItemFactory.material(gameConfig.getString("lucky.block", "SPONGE"), Material.SPONGE);
    }

    /** games/&lt;id&gt;.yml -&gt; loot.lucky */
    public static LootTable table(YamlConfiguration gameConfig) {
        return LootTable.load(gameConfig.getConfigurationSection("loot.lucky"));
    }

    public static void dropLoot(Location at, LootTable table, Random random) {
        if (at.getWorld() == null) {
            return;
        }
        Location c = at.clone().add(0.5, 0.5, 0.5);
        for (ItemStack item : table.rollAll(random)) {
            at.getWorld().dropItemNaturally(c, item);
        }
    }
}
