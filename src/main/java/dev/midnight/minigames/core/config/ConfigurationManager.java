package dev.midnight.minigames.core.config;

import dev.midnight.minigames.MidnightMinigames;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owns config.yml, messages.yml, menus.yml, permissions.yml and the per-game files in games/. */
public final class ConfigurationManager {

    public static final List<String> GAME_FILES = List.of("bedwars", "skywars", "spleef", "prison", "skyblock",
            "duels", "parkour", "oneblock", "skygen");

    private final MidnightMinigames plugin;
    private ConfigFile config;
    private ConfigFile messages;
    private ConfigFile menus;
    private ConfigFile permissions;
    private final Map<String, ConfigFile> games = new HashMap<>();

    public ConfigurationManager(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    public void load() {
        config = new ConfigFile(plugin, "config.yml");
        messages = new ConfigFile(plugin, "messages.yml");
        menus = new ConfigFile(plugin, "menus.yml");
        permissions = new ConfigFile(plugin, "permissions.yml");
        config.load();
        messages.load();
        menus.load();
        permissions.load();
        games.clear();
        for (String id : GAME_FILES) {
            ConfigFile f = new ConfigFile(plugin, "games/" + id + ".yml");
            f.load();
            games.put(id, f);
        }
        validate();
    }

    public void reload() {
        load();
    }

    public YamlConfiguration config() {
        return config.yaml();
    }

    public ConfigFile messagesFile() {
        return messages;
    }

    public YamlConfiguration menus() {
        return menus.yaml();
    }

    public YamlConfiguration permissions() {
        return permissions.yaml();
    }

    /** games/&lt;id&gt;.yml - detailed per-game settings (shops, loot, ranks...). Empty config when absent. */
    public YamlConfiguration game(String id) {
        ConfigFile f = games.get(id);
        return f == null ? new YamlConfiguration() : f.yaml();
    }

    /** config.yml games.&lt;id&gt; - enabled flag, modes, scoreboard overrides. */
    public ConfigurationSection gameSection(String id) {
        return config().getConfigurationSection("games." + id);
    }

    public void problem(String file, String path, String message) {
        plugin.getLogger().severe("[config] " + file + (path == null ? "" : " -> " + path) + ": " + message);
    }

    /** Reports exact configuration problems to the console. */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        String db = config().getString("database.type", "sqlite").toLowerCase();
        if (!db.equals("sqlite") && !db.equals("mysql") && !db.equals("mariadb")) {
            problems.add("config.yml -> database.type: must be 'sqlite' or 'mysql' (found '" + db + "')");
        }
        ConfigurationSection gs = config().getConfigurationSection("games");
        if (gs == null) {
            problems.add("config.yml -> games: section is missing");
        } else {
            for (String id : gs.getKeys(false)) {
                ConfigurationSection g = gs.getConfigurationSection(id);
                if (g == null) {
                    problems.add("config.yml -> games." + id + ": must be a section");
                    continue;
                }
                ConfigurationSection modes = g.getConfigurationSection("modes");
                if (g.getBoolean("enabled", true) && modes != null) {
                    for (String m : modes.getKeys(false)) {
                        ConfigurationSection ms = modes.getConfigurationSection(m);
                        if (ms == null) {
                            problems.add("config.yml -> games." + id + ".modes." + m + ": must be a section");
                            continue;
                        }
                        String mat = ms.getString("material", "PAPER");
                        if (Material.matchMaterial(mat) == null) {
                            problems.add("config.yml -> games." + id + ".modes." + m + ".material: '" + mat + "' is not a valid material");
                        }
                        if (ms.getInt("team-size", 1) < 1) {
                            problems.add("config.yml -> games." + id + ".modes." + m + ".team-size: must be >= 1");
                        }
                    }
                }
            }
        }
        ConfigurationSection main = menus().getConfigurationSection("main-menu");
        if (main == null) {
            problems.add("menus.yml -> main-menu: section is missing");
        } else {
            int rows = main.getInt("rows", 3);
            if (rows < 1 || rows > 6) {
                problems.add("menus.yml -> main-menu.rows: must be 1-6 (found " + rows + ")");
            }
            ConfigurationSection items = main.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection it = items.getConfigurationSection(key);
                    if (it == null) {
                        continue;
                    }
                    int slot = it.getInt("slot", 0);
                    if (slot < 0 || slot >= rows * 9) {
                        problems.add("menus.yml -> main-menu.items." + key + ".slot: " + slot + " is outside the menu (0-" + (rows * 9 - 1) + ")");
                    }
                    String mat = it.getString("material", "PAPER");
                    if (Material.matchMaterial(mat) == null) {
                        problems.add("menus.yml -> main-menu.items." + key + ".material: '" + mat + "' is not a valid material");
                    }
                }
            }
        }
        for (String p : problems) {
            plugin.getLogger().severe("[config] " + p);
        }
        return problems;
    }
}
