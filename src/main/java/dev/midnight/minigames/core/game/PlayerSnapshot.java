package dev.midnight.minigames.core.game;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/** Everything restored when a player leaves a round based game. Held in memory only. */
final class PlayerSnapshot {

    private final Location location;
    private final ItemStack[] contents;
    private final GameMode gameMode;
    private final double health;
    private final int food;
    private final float saturation;
    private final int level;
    private final float exp;
    private final boolean allowFlight;
    private final List<PotionEffect> effects;

    private PlayerSnapshot(Player p) {
        location = p.getLocation().clone();
        ItemStack[] src = p.getInventory().getContents();
        contents = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            contents[i] = src[i] == null ? null : src[i].clone();
        }
        gameMode = p.getGameMode();
        health = p.getHealth();
        food = p.getFoodLevel();
        saturation = p.getSaturation();
        level = p.getLevel();
        exp = p.getExp();
        allowFlight = p.getAllowFlight();
        effects = new ArrayList<>(p.getActivePotionEffects());
    }

    static PlayerSnapshot of(Player p) {
        return new PlayerSnapshot(p);
    }

    Location location() {
        return location;
    }

    void restore(Player p) {
        p.getInventory().clear();
        p.getInventory().setContents(contents);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        p.addPotionEffects(effects);
        p.setGameMode(gameMode);
        p.setAllowFlight(allowFlight);
        p.setFlying(false);
        double max = dev.midnight.minigames.util.PlayerUtil.maxHealth(p);
        p.setHealth(Math.max(1.0, Math.min(health, max)));
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setLevel(level);
        p.setExp(exp);
        p.setFireTicks(0);
        p.setFallDistance(0f);
    }
}
