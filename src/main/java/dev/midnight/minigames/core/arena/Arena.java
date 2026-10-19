package dev.midnight.minigames.core.arena;

import dev.midnight.minigames.util.Cuboid;
import dev.midnight.minigames.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable arena definition backed by a YAML file ({@code arenas/<game>/<name>.yml}).
 * Common fields have typed accessors; minigames store their own data under {@code data.*}.
 */
public final class Arena {

    private final String name;
    private final String gameId;
    private final File file;
    private final YamlConfiguration yaml;

    Arena(String gameId, String name, File file, YamlConfiguration yaml) {
        this.gameId = gameId;
        this.name = name;
        this.file = file;
        this.yaml = yaml;
    }

    public String getName() {
        return name;
    }

    public String getGameId() {
        return gameId;
    }

    File getFile() {
        return file;
    }

    public YamlConfiguration getYaml() {
        return yaml;
    }

    // ---- common settings -------------------------------------------------------------------

    public boolean isEnabled() {
        return yaml.getBoolean("enabled", false);
    }

    public void setEnabled(boolean enabled) {
        yaml.set("enabled", enabled);
    }

    public String getMode() {
        return yaml.getString("mode", "default");
    }

    public void setMode(String mode) {
        yaml.set("mode", mode);
    }

    public String getWorldName() {
        return yaml.getString("world", "");
    }

    public void setWorldName(String world) {
        yaml.set("world", world);
    }

    /** Returns the loaded world or null; use {@code WorldService#require} for admin-friendly errors. */
    public World getWorld() {
        String w = getWorldName();
        return w.isEmpty() ? null : Bukkit.getWorld(w);
    }

    public int getMinPlayers() {
        return Math.max(1, yaml.getInt("min-players", 2));
    }

    public void setMinPlayers(int v) {
        yaml.set("min-players", v);
    }

    public int getMaxPlayers() {
        return Math.max(getMinPlayers(), yaml.getInt("max-players", 8));
    }

    public void setMaxPlayers(int v) {
        yaml.set("max-players", v);
    }

    /** Lobby countdown in seconds. */
    public int getCountdown() {
        return Math.max(1, yaml.getInt("countdown", 15));
    }

    public void setCountdown(int v) {
        yaml.set("countdown", v);
    }

    /** Maximum game length in seconds (0 = unlimited). */
    public int getGameTime() {
        return Math.max(0, yaml.getInt("game-time", 1800));
    }

    public void setGameTime(int v) {
        yaml.set("game-time", v);
    }

    /** Y level below which a player counts as fallen into the void. */
    public int getVoidY() {
        World w = getWorld();
        return yaml.getInt("void-y", w == null ? -64 : w.getMinHeight());
    }

    // ---- locations ---------------------------------------------------------------------------

    public Location getLobby() {
        return LocationUtil.deserialize(yaml.getString("lobby"));
    }

    public void setLobby(Location l) {
        yaml.set("lobby", LocationUtil.serialize(l));
    }

    public Location getSpectator() {
        Location l = LocationUtil.deserialize(yaml.getString("spectator"));
        return l != null ? l : getLobby();
    }

    public void setSpectator(Location l) {
        yaml.set("spectator", LocationUtil.serialize(l));
    }

    public List<Location> getSpawns() {
        return getLocationList("spawns");
    }

    public void addSpawn(Location l) {
        List<String> list = new ArrayList<>(yaml.getStringList("spawns"));
        list.add(LocationUtil.serialize(l));
        yaml.set("spawns", list);
    }

    public void clearSpawns() {
        yaml.set("spawns", null);
    }

    /** Team id (lower case) to raw serialized spawn. */
    public Map<String, String> getRawTeamSpawns() {
        Map<String, String> out = new LinkedHashMap<>();
        ConfigurationSection s = yaml.getConfigurationSection("team-spawns");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                out.put(k.toLowerCase(), s.getString(k));
            }
        }
        return out;
    }

    public Location getTeamSpawn(String team) {
        return LocationUtil.deserialize(yaml.getString("team-spawns." + team.toLowerCase()));
    }

    public void setTeamSpawn(String team, Location l) {
        yaml.set("team-spawns." + team.toLowerCase(), LocationUtil.serialize(l));
    }

    public Cuboid getRegion(String key) {
        return Cuboid.deserialize(yaml.getString("region." + key));
    }

    public void setRegion(String key, Cuboid c) {
        yaml.set("region." + key, c == null ? null : c.serialize());
    }

    // ---- game specific data ------------------------------------------------------------------

    /** Section for module specific values; created on demand. */
    public ConfigurationSection getData() {
        ConfigurationSection s = yaml.getConfigurationSection("data");
        return s != null ? s : yaml.createSection("data");
    }

    public Location getLocation(String path) {
        return LocationUtil.deserialize(yaml.getString(path));
    }

    public void setLocation(String path, Location l) {
        yaml.set(path, LocationUtil.serialize(l));
    }

    public List<Location> getLocationList(String path) {
        List<Location> out = new ArrayList<>();
        for (String raw : yaml.getStringList(path)) {
            Location l = LocationUtil.deserialize(raw);
            if (l != null) {
                out.add(l);
            }
        }
        return out;
    }

    public void addLocationToList(String path, Location l) {
        List<String> list = new ArrayList<>(yaml.getStringList(path));
        list.add(LocationUtil.serialize(l));
        yaml.set(path, list);
    }

    /** Basic validation shared by all arena games; modules add their own on top. */
    public List<String> validateBasics() {
        List<String> problems = new ArrayList<>();
        if (getWorldName().isEmpty()) {
            problems.add("No world set (use /minigame arena setworld or stand in the arena world when creating it)");
        } else if (getWorld() == null) {
            problems.add("World '" + getWorldName() + "' is not loaded (use /minigame world load " + getWorldName() + ")");
        }
        if (getLobby() == null) {
            problems.add("Lobby location missing or its world is not loaded (/minigame arena setlobby)");
        }
        if (getMinPlayers() > getMaxPlayers()) {
            problems.add("min-players is greater than max-players");
        }
        return problems;
    }
}
