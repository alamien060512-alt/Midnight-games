package dev.midnight.minigames.core.game;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared plumbing for minigame modules: ids, config access, modes and arena creation. */
public abstract class AbstractMinigame implements Minigame {

    protected final MidnightMinigames plugin;
    private final String id;
    private final String defaultDisplayName;
    protected List<ModeDefinition> modes = new ArrayList<>();
    private boolean enabled;

    protected AbstractMinigame(MidnightMinigames plugin, String id, String defaultDisplayName) {
        this.plugin = plugin;
        this.id = id.toLowerCase(Locale.ROOT);
        this.defaultDisplayName = defaultDisplayName;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        ConfigurationSection s = section();
        return s == null ? defaultDisplayName : s.getString("display-name", defaultDisplayName);
    }

    /** Display name without colour formatting, for scoreboards and logs. */
    public String getDisplayPlain() {
        return TextUtil.plain(TextUtil.parse(getDisplayName()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** config.yml games.&lt;id&gt; */
    protected ConfigurationSection section() {
        return plugin.getConfigurationManager().gameSection(id);
    }

    /** games/&lt;id&gt;.yml (for 1v1 this resolves to its own section in config.yml; see subclass). */
    protected YamlConfiguration config() {
        return plugin.getConfigurationManager().game(id);
    }

    @Override
    public void enable() {
        enabled = true;
        loadModes();
        onEnable();
    }

    @Override
    public void disable() {
        try {
            onDisable();
        } finally {
            enabled = false;
        }
    }

    @Override
    public void reload() {
        loadModes();
        onReload();
    }

    protected abstract void onEnable();

    protected abstract void onDisable();

    protected void onReload() {
    }

    protected void loadModes() {
        List<ModeDefinition> list = new ArrayList<>();
        ConfigurationSection s = section();
        ConfigurationSection ms = s == null ? null : s.getConfigurationSection("modes");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                ConfigurationSection m = ms.getConfigurationSection(key);
                if (m != null) {
                    list.add(ModeDefinition.fromConfig(key.toLowerCase(Locale.ROOT), m));
                }
            }
        }
        if (list.isEmpty() && usesArenas()) {
            plugin.getLogger().warning("[config] config.yml -> games." + id + ".modes: no modes defined - the " + id + " menu will be empty.");
        }
        modes = list;
    }

    @Override
    public List<ModeDefinition> getModes() {
        return modes;
    }

    public ModeDefinition findMode(String modeId) {
        if (modeId == null) {
            return modes.isEmpty() ? null : modes.get(0);
        }
        for (ModeDefinition m : modes) {
            if (m.getId().equalsIgnoreCase(modeId)) {
                return m;
            }
        }
        return null;
    }

    @Override
    public Arena createArena(String name) {
        ModeDefinition def = modes.isEmpty() ? null : modes.get(0);
        ConfigurationSection s = def == null ? null : def.getRaw();
        int min = s == null ? 2 : s.getInt("min-players", 2);
        int max = s == null ? 8 : s.getInt("max-players", 8);
        return plugin.getArenaManager().create(id, name, null, def == null ? "default" : def.getId(), min, max);
    }

    @Override
    public boolean deleteArena(String name) {
        stop(name);
        return plugin.getArenaManager().delete(id, name);
    }

    @Override
    public GameState getState() {
        return GameState.WAITING;
    }
}
