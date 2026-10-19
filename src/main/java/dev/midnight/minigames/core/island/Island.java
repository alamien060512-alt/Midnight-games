package dev.midnight.minigames.core.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A player island on the shared island grid. */
public final class Island {

    private final UUID owner;
    private final int index;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Map<String, Integer> upgrades = new HashMap<>();
    private final Map<String, String> data = new HashMap<>();
    private String home;
    private final long created;

    Island(UUID owner, int index, String world, int x, int y, int z, long created) {
        this.owner = owner;
        this.index = index;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.created = created;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getIndex() {
        return index;
    }

    public String getWorldName() {
        return world;
    }

    public int getCenterX() {
        return x;
    }

    public int getCenterY() {
        return y;
    }

    public int getCenterZ() {
        return z;
    }

    public long getCreated() {
        return created;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean isMember(UUID id) {
        return owner.equals(id) || members.contains(id);
    }

    public Map<String, Integer> getUpgrades() {
        return upgrades;
    }

    public int getUpgrade(String id) {
        return upgrades.getOrDefault(id, 0);
    }

    public Map<String, String> getData() {
        return data;
    }

    public long getLong(String key, long def) {
        String v = data.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public void setLong(String key, long value) {
        data.put(key, Long.toString(value));
    }

    public String getHomeRaw() {
        return home;
    }

    public void setHomeRaw(String home) {
        this.home = home;
    }

    public World getWorld() {
        return Bukkit.getWorld(world);
    }

    public Location getCenter() {
        World w = getWorld();
        return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
    }

    public boolean contains(Location l, int radius) {
        return l.getWorld() != null && l.getWorld().getName().equals(world)
                && Math.abs(l.getBlockX() - x) <= radius && Math.abs(l.getBlockZ() - z) <= radius;
    }
}
