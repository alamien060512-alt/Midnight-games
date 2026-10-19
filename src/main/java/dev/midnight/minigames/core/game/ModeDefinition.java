package dev.midnight.minigames.core.game;

import dev.midnight.minigames.util.ItemFactory;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** A selectable game mode (Solo, Doubles, Lucky...) as shown in the mode menu. */
public final class ModeDefinition {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final List<String> lore;
    private final int slot;
    private final int teamSize;
    private final boolean lucky;
    private final ConfigurationSection raw;

    public ModeDefinition(String id, String displayName, Material icon, List<String> lore, int slot,
                          int teamSize, boolean lucky, ConfigurationSection raw) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.lore = lore;
        this.slot = slot;
        this.teamSize = teamSize;
        this.lucky = lucky;
        this.raw = raw;
    }

    public static ModeDefinition fromConfig(String id, ConfigurationSection s) {
        return new ModeDefinition(id,
                s.getString("name", "<white>" + id),
                ItemFactory.material(s.getString("material", "PAPER"), Material.PAPER),
                s.getStringList("lore"),
                s.getInt("slot", -1),
                Math.max(1, s.getInt("team-size", 1)),
                s.getBoolean("lucky", false),
                s);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public List<String> getLore() {
        return lore;
    }

    /** Preferred menu slot or -1 for automatic placement. */
    public int getSlot() {
        return slot;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public boolean isLucky() {
        return lucky;
    }

    public ConfigurationSection getRaw() {
        return raw;
    }

    public ItemStack toItem(String... vars) {
        ConfigurationSection s = raw;
        ItemStack item = ItemFactory.build(s, vars);
        return item;
    }
}
