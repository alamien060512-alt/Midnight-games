package dev.midnight.minigames.util;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.Locale;
import java.util.logging.Logger;

/** Builds ItemStacks from configuration sections so every item in the plugin is configurable. */
public final class ItemFactory {

    private static Logger log = Logger.getLogger("MidnightMinigames");

    private ItemFactory() {
    }

    public static void setLogger(Logger logger) {
        log = logger;
    }

    public static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material m = Material.matchMaterial(name.trim());
        if (m == null) {
            log.warning("Unknown material '" + name + "' - using " + fallback);
            return fallback;
        }
        return m;
    }

    public static Enchantment enchantment(String name) {
        if (name == null) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    /**
     * Section keys: material, amount, name, lore, enchants (map), unbreakable, glow, hide-flags,
     * potion (potion type key), color (leather armour: name or #RRGGBB), action (PDC action tag).
     * Extra key/value pairs replace {key} placeholders in material, name and lore.
     */
    public static ItemStack build(ConfigurationSection s, String... vars) {
        Material mat = material(TextUtil.replace(s.getString("material", "STONE"), vars), Material.STONE);
        ItemStack item = new ItemStack(mat, Math.max(1, s.getInt("amount", 1)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (s.contains("name")) {
            meta.displayName(TextUtil.item(TextUtil.replace(s.getString("name"), vars)));
        }
        if (s.contains("lore")) {
            java.util.List<String> lore = new java.util.ArrayList<>();
            for (String line : s.getStringList("lore")) {
                lore.add(TextUtil.replace(line, vars));
            }
            meta.lore(TextUtil.items(lore));
        }
        ConfigurationSection ench = s.getConfigurationSection("enchants");
        if (ench != null) {
            for (String key : ench.getKeys(false)) {
                Enchantment e = enchantment(key);
                if (e == null) {
                    log.warning("Unknown enchantment '" + key + "' in item config " + s.getCurrentPath());
                    continue;
                }
                meta.addEnchant(e, Math.max(1, ench.getInt(key, 1)), true);
            }
        }
        if (s.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
        }
        if (s.getBoolean("glow", false)) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (s.getBoolean("hide-flags", false)) {
            meta.addItemFlags(ItemFlag.values());
        }
        if (meta instanceof PotionMeta pm && s.contains("potion")) {
            String key = s.getString("potion", "water").toLowerCase(Locale.ROOT);
            NamespacedKey nk = NamespacedKey.fromString(key);
            PotionType type = nk == null ? null : Registry.POTION.get(nk);
            if (type == null) {
                log.warning("Unknown potion type '" + key + "' in item config " + s.getCurrentPath());
            } else {
                pm.setBasePotionType(type);
            }
        }
        if (meta instanceof LeatherArmorMeta lm && s.contains("color")) {
            Color c = parseColor(s.getString("color"));
            if (c != null) {
                lm.setColor(c);
            }
        }
        if (s.contains("special")) {
            meta.getPersistentDataContainer().set(Keys.SPECIAL, PersistentDataType.STRING, s.getString("special"));
        }
        if (s.contains("action")) {
            meta.getPersistentDataContainer().set(Keys.ACTION, PersistentDataType.STRING, s.getString("action"));
        }
        item.setItemMeta(meta);
        return item;
    }

    public static Color parseColor(String raw) {
        if (raw == null) {
            return null;
        }
        String c = raw.trim();
        try {
            if (c.startsWith("#") && c.length() == 7) {
                return Color.fromRGB(Integer.parseInt(c.substring(1), 16));
            }
            return switch (c.toUpperCase(Locale.ROOT)) {
                case "RED" -> Color.RED;
                case "BLUE" -> Color.BLUE;
                case "GREEN" -> Color.GREEN;
                case "YELLOW" -> Color.YELLOW;
                case "AQUA" -> Color.AQUA;
                case "WHITE" -> Color.WHITE;
                case "BLACK" -> Color.BLACK;
                case "ORANGE" -> Color.ORANGE;
                case "PURPLE" -> Color.PURPLE;
                case "LIME" -> Color.LIME;
                case "GRAY" -> Color.GRAY;
                case "PINK" -> Color.fromRGB(0xF38BAA);
                default -> null;
            };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static ItemStack simple(Material m, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (name != null) {
            meta.displayName(TextUtil.item(name));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(TextUtil.items(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack tag(ItemStack item, NamespacedKey key, String value) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    public static String readTag(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
