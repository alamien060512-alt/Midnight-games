package dev.midnight.minigames.core.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory view of a player's persistent data: global stats plus free-form key/value data per namespace
 * (a namespace is usually a minigame id). Only touched on the main thread; saved asynchronously.
 */
public final class PlayerProfile {

    private final UUID uuid;
    private String name;
    private PlayerStats stats = new PlayerStats();
    private final Map<String, Map<String, String>> data = new HashMap<>();
    private volatile boolean loaded;
    private boolean dirty;

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PlayerStats getStats() {
        return stats;
    }

    void applyLoaded(PlayerStats stats, Map<String, Map<String, String>> loadedData) {
        this.stats = stats;
        // Merge: anything changed while the load was in flight wins over stored values.
        for (Map.Entry<String, Map<String, String>> e : loadedData.entrySet()) {
            Map<String, String> target = data.computeIfAbsent(e.getKey(), k -> new HashMap<>());
            for (Map.Entry<String, String> kv : e.getValue().entrySet()) {
                target.putIfAbsent(kv.getKey(), kv.getValue());
            }
        }
        loaded = true;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }

    // ---- generic key/value data ---------------------------------------------------------------

    public String getString(String ns, String key, String def) {
        Map<String, String> m = data.get(ns);
        String v = m == null ? null : m.get(key);
        return v == null ? def : v;
    }

    /** Storing an empty/null value deletes the key on the next save. */
    public void setString(String ns, String key, String value) {
        data.computeIfAbsent(ns, k -> new HashMap<>()).put(key, value == null ? "" : value);
        dirty = true;
    }

    public long getLong(String ns, String key, long def) {
        String v = getString(ns, key, null);
        if (v == null || v.isEmpty()) {
            return def;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public void setLong(String ns, String key, long value) {
        setString(ns, key, Long.toString(value));
    }

    public double getDouble(String ns, String key, double def) {
        String v = getString(ns, key, null);
        if (v == null || v.isEmpty()) {
            return def;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public void setDouble(String ns, String key, double value) {
        setString(ns, key, Double.toString(value));
    }

    public long increment(String ns, String key, long by) {
        long v = getLong(ns, key, 0) + by;
        setLong(ns, key, v);
        return v;
    }

    public Map<String, Map<String, String>> snapshotData() {
        Map<String, Map<String, String>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> e : data.entrySet()) {
            copy.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        return copy;
    }
}
