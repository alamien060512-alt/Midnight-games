package dev.midnight.minigames.minigames.bedwars;

import dev.midnight.minigames.core.team.GameTeam;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

/** A resource generator. Progress is fractional so speed upgrades work at any rate with the 1 Hz game tick. */
final class Generator {

    final String type;
    final Location location;
    /** Owning team for base generators, null for global (diamond / emerald / island) generators. */
    final GameTeam team;
    private final Material material;
    private final List<Double> intervals;
    private final int maxStack;
    private final int amount;
    private double progress;

    Generator(String type, Location location, GameTeam team, Material material, List<Double> intervals, int maxStack, int amount) {
        this.type = type;
        this.location = location;
        this.team = team;
        this.material = material;
        this.intervals = intervals;
        this.maxStack = maxStack;
        this.amount = Math.max(1, amount);
    }

    /** Advances one second. Returns true when items were spawned. */
    boolean tick(int tier, double speedMultiplier) {
        double interval = intervals.get(Math.min(Math.max(tier, 1), intervals.size()) - 1);
        progress += speedMultiplier / Math.max(0.1, interval);
        if (progress < 1.0) {
            return false;
        }
        int spawns = (int) progress;
        progress -= spawns;
        World w = location.getWorld();
        if (w == null) {
            return false;
        }
        Location drop = location.clone().add(0.5, 0.6, 0.5);
        int nearby = 0;
        for (Entity e : w.getNearbyEntities(drop, 1.5, 1.5, 1.5)) {
            if (e instanceof Item item && item.getItemStack().getType() == material) {
                nearby += item.getItemStack().getAmount();
            }
        }
        boolean spawned = false;
        for (int i = 0; i < spawns && nearby < maxStack; i++) {
            Item item = w.dropItem(drop, new ItemStack(material, amount));
            item.setVelocity(new Vector(0, 0.05, 0));
            nearby += amount;
            spawned = true;
        }
        return spawned;
    }
}
