package dev.midnight.minigames.util;

import org.bukkit.Location;
import org.bukkit.World;

/** Axis-aligned block region. */
public final class Cuboid {

    public final String world;
    public final int minX, minY, minZ, maxX, maxY, maxZ;

    public Cuboid(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static Cuboid of(Location a, Location b) {
        return new Cuboid(a.getWorld().getName(), a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public boolean contains(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(world)
                && contains(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public Location center(World w) {
        return new Location(w, (minX + maxX + 1) / 2.0, maxY + 1, (minZ + maxZ + 1) / 2.0);
    }

    public String serialize() {
        return world + "," + minX + "," + minY + "," + minZ + "," + maxX + "," + maxY + "," + maxZ;
    }

    public static Cuboid deserialize(String s) {
        if (s == null) {
            return null;
        }
        String[] p = s.split(",");
        if (p.length != 7) {
            return null;
        }
        try {
            return new Cuboid(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                    Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
