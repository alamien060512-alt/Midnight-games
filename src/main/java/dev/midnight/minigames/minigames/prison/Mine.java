package dev.midnight.minigames.minigames.prison;

import dev.midnight.minigames.util.Cuboid;
import dev.midnight.minigames.util.WeightedTable;
import org.bukkit.Location;
import org.bukkit.Material;

/** A resettable mining area. */
final class Mine {

    final String name;
    final Cuboid region;
    final WeightedTable<Material> table;
    final int requiredRank;
    final Location spawn;
    final Material icon;
    final long resetSeconds;
    final double resetPercent;
    long total;
    long remaining;
    long nextReset;
    boolean resetting;

    Mine(String name, Cuboid region, WeightedTable<Material> table, int requiredRank, Location spawn, Material icon,
         long resetSeconds, double resetPercent) {
        this.name = name;
        this.region = region;
        this.table = table;
        this.requiredRank = requiredRank;
        this.spawn = spawn;
        this.icon = icon;
        this.resetSeconds = resetSeconds;
        this.resetPercent = resetPercent;
        this.total = region.volume();
        this.remaining = total;
        this.nextReset = resetSeconds > 0 ? System.currentTimeMillis() + resetSeconds * 1000L : 0;
    }

    double remainingFraction() {
        return total == 0 ? 0 : (double) remaining / total;
    }
}
