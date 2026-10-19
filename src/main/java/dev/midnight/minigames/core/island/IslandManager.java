package dev.midnight.minigames.core.island;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Allocates islands on a spiral grid and persists them to {@code data/<game>.yml}. Used by Skyblock, OneBlock
 * and SkyGen. Island cells are never recycled, so a deleted island can never overlap a new one.
 */
public final class IslandManager {

    private final MidnightMinigames plugin;
    private final String gameId;
    private final File file;
    private final Map<UUID, Island> byOwner = new HashMap<>();
    private final Map<Long, Island> byCell = new HashMap<>();
    private final Map<UUID, UUID> memberToOwner = new HashMap<>();
    private int nextIndex;
    private boolean saveScheduled;

    public IslandManager(MidnightMinigames plugin, String gameId) {
        this.plugin = plugin;
        this.gameId = gameId;
        this.file = new File(plugin.getDataFolder(), "data/" + gameId + ".yml");
    }

    private int spacing() {
        return Math.max(64, plugin.getConfigurationManager().game(gameId).getInt("island.spacing", 200));
    }

    private int centerY() {
        return plugin.getConfigurationManager().game(gameId).getInt("island.center-y", 64);
    }

    // ---- persistence ---------------------------------------------------------------------------------

    public void load() {
        byOwner.clear();
        byCell.clear();
        memberToOwner.clear();
        nextIndex = 0;
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("Could not read island data file data/" + gameId + ".yml: " + ex.getMessage()
                    + " - existing islands are NOT loaded. Fix or restore the file, then /minigame reload.");
            return;
        }
        nextIndex = y.getInt("next-index", 0);
        ConfigurationSection islands = y.getConfigurationSection("islands");
        if (islands == null) {
            return;
        }
        for (String key : islands.getKeys(false)) {
            ConfigurationSection s = islands.getConfigurationSection(key);
            try {
                UUID owner = UUID.fromString(key);
                Island is = new Island(owner, s.getInt("index"), s.getString("world", ""), s.getInt("x"), s.getInt("y"),
                        s.getInt("z"), s.getLong("created"));
                is.setHomeRaw(s.getString("home"));
                for (String m : s.getStringList("members")) {
                    UUID id = UUID.fromString(m);
                    is.getMembers().add(id);
                    memberToOwner.put(id, owner);
                }
                ConfigurationSection up = s.getConfigurationSection("upgrades");
                if (up != null) {
                    for (String u : up.getKeys(false)) {
                        is.getUpgrades().put(u, up.getInt(u));
                    }
                }
                ConfigurationSection data = s.getConfigurationSection("data");
                if (data != null) {
                    for (String d : data.getKeys(false)) {
                        is.getData().put(d, data.getString(d));
                    }
                }
                register(is);
            } catch (IllegalArgumentException | NullPointerException ex) {
                plugin.getLogger().warning("Skipping corrupt island entry '" + key + "' in data/" + gameId + ".yml");
            }
        }
        plugin.getLogger().info(gameId + ": loaded " + byOwner.size() + " island(s).");
    }

    private String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-index", nextIndex);
        for (Island is : byOwner.values()) {
            String p = "islands." + is.getOwner();
            y.set(p + ".index", is.getIndex());
            y.set(p + ".world", is.getWorldName());
            y.set(p + ".x", is.getCenterX());
            y.set(p + ".y", is.getCenterY());
            y.set(p + ".z", is.getCenterZ());
            y.set(p + ".created", is.getCreated());
            y.set(p + ".home", is.getHomeRaw());
            List<String> members = new ArrayList<>();
            is.getMembers().forEach(m -> members.add(m.toString()));
            y.set(p + ".members", members);
            for (Map.Entry<String, Integer> e : is.getUpgrades().entrySet()) {
                y.set(p + ".upgrades." + e.getKey(), e.getValue());
            }
            for (Map.Entry<String, String> e : is.getData().entrySet()) {
                y.set(p + ".data." + e.getKey(), e.getValue());
            }
        }
        return y.saveToString();
    }

    /** Coalesces rapid changes: serialises on the main thread, writes asynchronously. */
    public void markDirty() {
        if (saveScheduled) {
            return;
        }
        saveScheduled = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            saveScheduled = false;
            final String data = serialize();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
        }, 100L);
    }

    public void saveNow() {
        write(serialize());
    }

    private void write(String data) {
        try {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), data, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save island data data/" + gameId + ".yml: " + ex.getMessage());
        }
    }

    // ---- grid ------------------------------------------------------------------------------------------

    private static int[] spiral(int n) {
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;
        for (int i = 0; i < n; i++) {
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int t = dx;
                dx = -dz;
                dz = t;
            }
            x += dx;
            z += dz;
        }
        return new int[]{x, z};
    }

    private static long cellKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private void register(Island is) {
        byOwner.put(is.getOwner(), is);
        int sp = spacing();
        byCell.put(cellKey(Math.floorDiv(is.getCenterX() + sp / 2, sp), Math.floorDiv(is.getCenterZ() + sp / 2, sp)), is);
    }

    // ---- queries ---------------------------------------------------------------------------------------

    public Island getByOwner(UUID owner) {
        return byOwner.get(owner);
    }

    /** The island the player owns or is a member of. */
    public Island of(UUID player) {
        Island own = byOwner.get(player);
        if (own != null) {
            return own;
        }
        UUID owner = memberToOwner.get(player);
        return owner == null ? null : byOwner.get(owner);
    }

    /** The island whose grid cell contains the location (regardless of protection radius). */
    public Island at(Location l) {
        if (l.getWorld() == null) {
            return null;
        }
        int sp = spacing();
        Island is = byCell.get(cellKey(Math.floorDiv(l.getBlockX() + sp / 2, sp), Math.floorDiv(l.getBlockZ() + sp / 2, sp)));
        return is != null && is.getWorldName().equals(l.getWorld().getName()) ? is : null;
    }

    public Collection<Island> all() {
        return byOwner.values();
    }

    // ---- mutations ---------------------------------------------------------------------------------------

    public Island create(UUID owner, String world) {
        int index = nextIndex++;
        int[] cell = spiral(index);
        int sp = spacing();
        Island is = new Island(owner, index, world, cell[0] * sp, centerY(), cell[1] * sp, System.currentTimeMillis());
        register(is);
        markDirty();
        return is;
    }

    public void delete(Island island) {
        byOwner.remove(island.getOwner());
        int sp = spacing();
        byCell.remove(cellKey(Math.floorDiv(island.getCenterX() + sp / 2, sp), Math.floorDiv(island.getCenterZ() + sp / 2, sp)));
        for (UUID m : island.getMembers()) {
            memberToOwner.remove(m);
        }
        markDirty();
    }

    public void addMember(Island island, UUID member) {
        island.getMembers().add(member);
        memberToOwner.put(member, island.getOwner());
        markDirty();
    }

    public void removeMember(Island island, UUID member) {
        island.getMembers().remove(member);
        memberToOwner.remove(member);
        markDirty();
    }

    public Location homeOf(Island island) {
        Location l = LocationUtil.deserialize(island.getHomeRaw());
        if (l != null) {
            return l;
        }
        Location c = island.getCenter();
        return c == null ? null : c.add(0, 2, 0);
    }
}
