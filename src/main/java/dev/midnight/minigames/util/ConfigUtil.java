package dev.midnight.minigames.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small helpers for reading flexible YAML structures. */
public final class ConfigUtil {

    private ConfigUtil() {
    }

    /**
     * Reads a node that is either a list of maps or a section of sections, returning each entry as a section.
     * For sections the section key is exposed through the "id" value when the entry lacks one.
     */
    public static List<ConfigurationSection> sections(ConfigurationSection parent, String path) {
        List<ConfigurationSection> out = new ArrayList<>();
        if (parent == null) {
            return out;
        }
        if (parent.isConfigurationSection(path)) {
            ConfigurationSection sec = parent.getConfigurationSection(path);
            for (String key : sec.getKeys(false)) {
                ConfigurationSection child = sec.getConfigurationSection(key);
                if (child != null) {
                    if (!child.contains("id")) {
                        child.set("id", key);
                    }
                    out.add(child);
                }
            }
            return out;
        }
        int i = 0;
        for (Map<?, ?> map : parent.getMapList(path)) {
            out.add(fromMap(map, "entry" + (i++)));
        }
        return out;
    }

    public static ConfigurationSection fromMap(Map<?, ?> map, String name) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            copy.put(String.valueOf(e.getKey()), e.getValue());
        }
        return new MemoryConfiguration().createSection(name, copy);
    }

    /** Parses "5" or "3-7" into {min,max}. */
    public static int[] range(Object raw, int def) {
        if (raw == null) {
            return new int[]{def, def};
        }
        String s = String.valueOf(raw).trim();
        try {
            int dash = s.indexOf('-', 1);
            if (dash > 0) {
                int a = Integer.parseInt(s.substring(0, dash).trim());
                int b = Integer.parseInt(s.substring(dash + 1).trim());
                return new int[]{Math.min(a, b), Math.max(a, b)};
            }
            int v = Integer.parseInt(s);
            return new int[]{v, v};
        } catch (NumberFormatException ex) {
            return new int[]{def, def};
        }
    }

    public static int randomInRange(int[] range, java.util.Random r) {
        return range[0] == range[1] ? range[0] : range[0] + r.nextInt(range[1] - range[0] + 1);
    }
}
