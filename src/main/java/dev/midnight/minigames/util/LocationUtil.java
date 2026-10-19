package dev.midnight.minigames.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Location (de)serialisation: "world,x,y,z,yaw,pitch". */
public final class LocationUtil {

    private LocationUtil() {
    }

    public static String serialize(Location l) {
        if (l == null || l.getWorld() == null) {
            return null;
        }
        return String.format(java.util.Locale.ROOT, "%s,%.3f,%.3f,%.3f,%.1f,%.1f",
                l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public static String serializeBlock(Block b) {
        return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ() + ",0.0,0.0";
    }

    /** Returns null when the string is malformed or the world is not loaded. */
    public static Location deserialize(String s) {
        if (s == null) {
            return null;
        }
        String[] p = s.split(",");
        if (p.length < 4) {
            return null;
        }
        World w = Bukkit.getWorld(p[0].trim());
        if (w == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(p[1].trim());
            double y = Double.parseDouble(p[2].trim());
            double z = Double.parseDouble(p[3].trim());
            float yaw = p.length > 4 ? Float.parseFloat(p[4].trim()) : 0f;
            float pitch = p.length > 5 ? Float.parseFloat(p[5].trim()) : 0f;
            return new Location(w, x, y, z, yaw, pitch);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** World name part of a serialized location (works when the world is not loaded). */
    public static String worldOf(String s) {
        if (s == null) {
            return null;
        }
        int i = s.indexOf(',');
        return i < 0 ? null : s.substring(0, i);
    }

    /** Packs block coordinates into one long (26 bits x, 26 bits z, 12 bits y). */
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    public static long pack(Block b) {
        return pack(b.getX(), b.getY(), b.getZ());
    }

    public static int unpackX(long k) {
        return (int) (k >> 38);
    }

    public static int unpackZ(long k) {
        return (int) (k << 26 >> 38);
    }

    public static int unpackY(long k) {
        return (int) (k << 52 >> 52);
    }

    public static boolean sameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ()
                && a.getWorld() == b.getWorld();
    }

    public static Location center(Location l) {
        return new Location(l.getWorld(), l.getBlockX() + 0.5, l.getBlockY(), l.getBlockZ() + 0.5, l.getYaw(), l.getPitch());
    }
}
