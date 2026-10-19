package dev.midnight.minigames.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Shared persistent-data keys. */
public final class Keys {

    public static NamespacedKey ACTION;
    public static NamespacedKey SHOP;
    public static NamespacedKey LUCKY;
    public static NamespacedKey GENERATOR;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        ACTION = new NamespacedKey(plugin, "action");
        SHOP = new NamespacedKey(plugin, "shop");
        LUCKY = new NamespacedKey(plugin, "lucky");
        GENERATOR = new NamespacedKey(plugin, "generator");
    }
}
