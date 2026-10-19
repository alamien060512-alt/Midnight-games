package dev.midnight.minigames.core.arena;

import dev.midnight.minigames.MidnightMinigames;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Loads, creates, saves and deletes arena files: {@code arenas/<game>/<name>.yml}. */
public final class ArenaManager {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

    private final MidnightMinigames plugin;
    private final File root;
    private final Map<String, Map<String, Arena>> arenas = new LinkedHashMap<>();

    private final Map<java.util.UUID, org.bukkit.Location[]> selections = new java.util.HashMap<>();

    /** Admin region selection (/minigame pos1, /minigame pos2). */
    public void setSelection(org.bukkit.entity.Player p, int index, org.bukkit.Location l) {
        selections.computeIfAbsent(p.getUniqueId(), k -> new org.bukkit.Location[2])[index] = l;
    }

    /** Returns the selected cuboid or null when both corners are not set in the same world. */
    public dev.midnight.minigames.util.Cuboid getSelection(org.bukkit.entity.Player p) {
        org.bukkit.Location[] sel = selections.get(p.getUniqueId());
        if (sel == null || sel[0] == null || sel[1] == null || sel[0].getWorld() != sel[1].getWorld()) {
            return null;
        }
        return dev.midnight.minigames.util.Cuboid.of(sel[0], sel[1]);
    }

    public ArenaManager(MidnightMinigames plugin) {
        this.plugin = plugin;
        this.root = new File(plugin.getDataFolder(), "arenas");
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    public void loadAll() {
        arenas.clear();
        if (!root.isDirectory() && !root.mkdirs()) {
            plugin.getLogger().warning("Could not create arenas folder " + root);
            return;
        }
        File[] gameDirs = root.listFiles(File::isDirectory);
        if (gameDirs == null) {
            return;
        }
        int loaded = 0;
        for (File gameDir : gameDirs) {
            String gameId = gameDir.getName().toLowerCase(Locale.ROOT);
            File[] files = gameDir.listFiles((d, n) -> n.endsWith(".yml"));
            if (files == null) {
                continue;
            }
            for (File f : files) {
                String name = f.getName().substring(0, f.getName().length() - 4);
                YamlConfiguration yaml = new YamlConfiguration();
                try {
                    yaml.load(f);
                } catch (IOException | InvalidConfigurationException ex) {
                    plugin.getLogger().severe("Invalid arena file arenas/" + gameDir.getName() + "/" + f.getName()
                            + ": " + ex.getMessage());
                    continue;
                }
                Arena arena = new Arena(gameId, name, f, yaml);
                arenas.computeIfAbsent(gameId, k -> new LinkedHashMap<>()).put(name.toLowerCase(Locale.ROOT), arena);
                loaded++;
                if (arena.isEnabled()) {
                    if (arena.getWorld() == null && plugin.getWorldService().getOrLoad(arena.getWorldName()) == null) {
                        plugin.getLogger().severe("Arena " + gameId + "/" + name + " is enabled but its world '"
                                + arena.getWorldName() + "' does not exist. Create/load the world or disable the arena.");
                    }
                }
            }
        }
        plugin.getLogger().info("Loaded " + loaded + " arena(s).");
    }

    public Arena get(String gameId, String name) {
        Map<String, Arena> m = arenas.get(gameId.toLowerCase(Locale.ROOT));
        return m == null ? null : m.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Arena> getArenas(String gameId) {
        Map<String, Arena> m = arenas.get(gameId.toLowerCase(Locale.ROOT));
        return m == null ? Collections.emptyList() : new ArrayList<>(m.values());
    }

    public List<Arena> getAll() {
        List<Arena> out = new ArrayList<>();
        arenas.values().forEach(m -> out.addAll(m.values()));
        return out;
    }

    /** Creates a new (disabled) arena. Returns null when the name is invalid or already used. */
    public Arena create(String gameId, String name, World world, String mode, int min, int max) {
        if (!isValidName(name) || get(gameId, name) != null) {
            return null;
        }
        File dir = new File(root, gameId.toLowerCase(Locale.ROOT));
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create folder " + dir);
            return null;
        }
        File file = new File(dir, name + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        Arena arena = new Arena(gameId.toLowerCase(Locale.ROOT), name, file, yaml);
        yaml.set("name", name);
        yaml.set("game", arena.getGameId());
        yaml.set("enabled", false);
        yaml.set("mode", mode);
        if (world != null) {
            yaml.set("world", world.getName());
        }
        yaml.set("min-players", min);
        yaml.set("max-players", max);
        yaml.set("countdown", plugin.getConfigurationManager().config().getInt("defaults.countdown", 15));
        yaml.set("game-time", plugin.getConfigurationManager().config().getInt("defaults.game-time", 1800));
        arenas.computeIfAbsent(arena.getGameId(), k -> new LinkedHashMap<>()).put(name.toLowerCase(Locale.ROOT), arena);
        save(arena);
        return arena;
    }

    public boolean delete(String gameId, String name) {
        Arena a = get(gameId, name);
        if (a == null) {
            return false;
        }
        arenas.get(a.getGameId()).remove(name.toLowerCase(Locale.ROOT));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.deleteIfExists(a.getFile().toPath());
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not delete " + a.getFile() + ": " + ex.getMessage());
            }
        });
        return true;
    }

    /** Serialises on the main thread and writes asynchronously. */
    public void save(Arena arena) {
        final String data = arena.getYaml().saveToString();
        final File file = arena.getFile();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(file, data));
    }

    public void saveAllBlocking() {
        for (Arena a : getAll()) {
            write(a.getFile(), a.getYaml().saveToString());
        }
    }

    private void write(File file, String data) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            Files.writeString(file.toPath(), data, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arena file " + file + ": " + ex.getMessage(), ex);
        }
    }
}
