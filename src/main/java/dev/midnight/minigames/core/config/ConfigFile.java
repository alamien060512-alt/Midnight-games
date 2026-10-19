package dev.midnight.minigames.core.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * One YAML file in the plugin folder, seeded from the jar. Syntax errors are reported with the exact
 * file and YAML position; the previous (or bundled default) content stays active so the plugin keeps running.
 */
public final class ConfigFile {

    private final Plugin plugin;
    private final String resource;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();
    private YamlConfiguration defaults = new YamlConfiguration();
    private boolean valid = true;

    public ConfigFile(Plugin plugin, String resource) {
        this.plugin = plugin;
        this.resource = resource;
        this.file = new File(plugin.getDataFolder(), resource);
    }

    /** @return false if the user's file had errors (defaults / previous content in use). */
    public boolean load() {
        if (!file.exists()) {
            if (plugin.getResource(resource) != null) {
                plugin.saveResource(resource, false);
            }
        }
        defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource(resource)) {
            if (in != null) {
                defaults.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, "Bundled default " + resource + " is broken: " + ex.getMessage(), ex);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
            yaml = loaded;
            valid = true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not read " + resource + ": " + ex.getMessage());
            valid = false;
        } catch (InvalidConfigurationException ex) {
            plugin.getLogger().severe("Invalid YAML in " + resource + " - " + firstLine(ex.getMessage())
                    + ". Keeping previous/default values until fixed.");
            valid = false;
        }
        if (!valid && yaml.getKeys(false).isEmpty()) {
            yaml = copyOf(defaults);
        }
        // Top-level sections that are missing entirely are taken from the bundled defaults.
        for (String key : defaults.getKeys(false)) {
            if (!yaml.contains(key)) {
                yaml.set(key, defaults.get(key));
            }
        }
        return valid;
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "unknown error";
        }
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    private static YamlConfiguration copyOf(YamlConfiguration src) {
        YamlConfiguration c = new YamlConfiguration();
        for (String k : src.getKeys(false)) {
            c.set(k, src.get(k));
        }
        return c;
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public YamlConfiguration defaults() {
        return defaults;
    }

    public boolean isValid() {
        return valid;
    }

    public File file() {
        return file;
    }

    public String name() {
        return resource;
    }
}
