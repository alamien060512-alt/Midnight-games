package dev.midnight.minigames.minigames.duels;

import dev.midnight.minigames.util.ConfigUtil;
import dev.midnight.minigames.util.ItemFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** A duel kit loaded from games/duels.yml -&gt; kits.&lt;id&gt;. */
public final class Kit {

    private final String id;
    private final ConfigurationSection section;

    public Kit(String id, ConfigurationSection section) {
        this.id = id;
        this.section = section;
    }

    public String getId() {
        return id;
    }

    public ConfigurationSection getSection() {
        return section;
    }

    public String getDisplayName() {
        return section.getString("name", id);
    }

    private ConfigurationSection options() {
        return section.getConfigurationSection("options");
    }

    public boolean naturalRegen() {
        return options() == null || options().getBoolean("natural-regen", true);
    }

    public boolean hunger() {
        return options() == null || options().getBoolean("hunger", true);
    }

    public boolean allowBuild() {
        return options() != null && options().getBoolean("build", false);
    }

    /** False for Boxing / Sumo style kits: hits deal no damage but still knock back. */
    public boolean damage() {
        return options() == null || options().getBoolean("damage", true);
    }

    /** Hits needed to win (Boxing); 0 = disabled. */
    public int maxHits() {
        return options() == null ? 0 : options().getInt("max-hits", 0);
    }

    public boolean liquidLoses() {
        return options() != null && options().getBoolean("liquid-loses", false);
    }

    public void apply(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        ConfigurationSection armor = section.getConfigurationSection("armor");
        if (armor != null) {
            setArmor(armor, "helmet", inv);
            setArmor(armor, "chestplate", inv);
            setArmor(armor, "leggings", inv);
            setArmor(armor, "boots", inv);
        }
        for (ConfigurationSection item : ConfigUtil.sections(section, "items")) {
            ItemStack stack = ItemFactory.build(item);
            int slot = item.getInt("slot", -1);
            if (slot >= 0 && slot < 36) {
                inv.setItem(slot, stack);
            } else if (slot == 40) {
                inv.setItemInOffHand(stack);
            } else {
                inv.addItem(stack);
            }
        }
    }

    private void setArmor(ConfigurationSection armor, String piece, PlayerInventory inv) {
        ConfigurationSection s = armor.getConfigurationSection(piece);
        if (s == null) {
            return;
        }
        ItemStack stack = ItemFactory.build(s);
        switch (piece) {
            case "helmet" -> inv.setHelmet(stack);
            case "chestplate" -> inv.setChestplate(stack);
            case "leggings" -> inv.setLeggings(stack);
            default -> inv.setBoots(stack);
        }
    }
}
