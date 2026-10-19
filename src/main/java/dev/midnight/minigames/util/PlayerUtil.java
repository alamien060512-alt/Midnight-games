package dev.midnight.minigames.util;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

/** Player state helpers. */
public final class PlayerUtil {

    private PlayerUtil() {
    }

    public static double maxHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    /** Clears inventory, effects, health, food, xp and sets the game mode. */
    public static void reset(Player p, GameMode mode) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        p.getInventory().setItemInOffHand(null);
        p.setItemOnCursor(null);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        p.setGameMode(mode);
        p.setHealth(maxHealth(p));
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setRemainingAir(p.getMaximumAir());
        boolean fly = mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
        p.setAllowFlight(fly);
        p.setFlying(false);
        p.setWalkSpeed(0.2f);
    }

    public static void heal(Player p) {
        p.setHealth(maxHealth(p));
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
    }
}
