package dev.midnight.minigames.util;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.function.Supplier;

/** Fills a cuboid across several ticks so large regions never lag the server. */
public final class RegionFiller {

    private RegionFiller() {
    }

    public static void fill(Plugin plugin, World world, Cuboid c, Supplier<Material> supplier, int perTick, Runnable done) {
        if (world == null) {
            if (done != null) {
                done.run();
            }
            return;
        }
        final int budget = Math.max(100, perTick);
        new BukkitRunnable() {
            int x = c.minX, y = c.minY, z = c.minZ;

            @Override
            public void run() {
                int n = 0;
                while (n < budget) {
                    Material m = supplier.get();
                    if (m != null) {
                        world.getBlockAt(x, y, z).setType(m, false);
                    }
                    n++;
                    if (++z > c.maxZ) {
                        z = c.minZ;
                        if (++x > c.maxX) {
                            x = c.minX;
                            if (++y > c.maxY) {
                                cancel();
                                if (done != null) {
                                    done.run();
                                }
                                return;
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
