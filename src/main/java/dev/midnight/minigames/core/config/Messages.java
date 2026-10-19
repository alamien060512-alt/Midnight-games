package dev.midnight.minigames.core.config;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** All player facing text comes from messages.yml (with bundled fallbacks). */
public final class Messages {

    private final MidnightMinigames plugin;
    private final Set<String> warned = new HashSet<>();

    public Messages(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    private String lookup(YamlConfiguration yaml, YamlConfiguration defaults, String key) {
        Object v = yaml.get(key);
        if (v == null) {
            v = defaults.get(key);
        }
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object o : list) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(o);
            }
            return sb.toString();
        }
        return String.valueOf(v);
    }

    public String raw(String key) {
        ConfigFile f = plugin.getConfigurationManager().messagesFile();
        String s = lookup(f.yaml(), f.defaults(), key);
        if (s == null) {
            if (warned.add(key)) {
                plugin.getLogger().warning("[config] messages.yml -> " + key + ": message is missing");
            }
            return "<red>[missing message: " + key + "]";
        }
        return s;
    }

    private String prefix() {
        ConfigFile f = plugin.getConfigurationManager().messagesFile();
        String p = lookup(f.yaml(), f.defaults(), "prefix");
        return p == null ? "" : p;
    }

    /** Renders a message with {prefix} and the given key/value placeholder pairs. */
    public Component get(String key, String... vars) {
        String s = raw(key).replace("{prefix}", prefix());
        return TextUtil.parse(TextUtil.replace(s, vars));
    }

    public String text(String key, String... vars) {
        return TextUtil.replace(raw(key).replace("{prefix}", prefix()), vars);
    }

    public void send(CommandSender to, String key, String... vars) {
        to.sendMessage(get(key, vars));
    }

    public void sendAll(Collection<? extends CommandSender> to, String key, String... vars) {
        if (to.isEmpty()) {
            return;
        }
        Component c = get(key, vars);
        for (CommandSender s : to) {
            s.sendMessage(c);
        }
    }

    public void actionBar(Player player, String key, String... vars) {
        player.sendActionBar(get(key, vars));
    }

    /** Shows titles.&lt;key&gt;.title / .subtitle. */
    public void title(Player player, String key, String... vars) {
        player.showTitle(buildTitle(key, vars));
    }

    public void titleAll(Collection<Player> players, String key, String... vars) {
        if (players.isEmpty()) {
            return;
        }
        Title t = buildTitle(key, vars);
        for (Player p : players) {
            p.showTitle(t);
        }
    }

    private Title buildTitle(String key, String... vars) {
        var cfg = plugin.getConfigurationManager().config();
        long in = cfg.getLong("titles.fade-in", 10) * 50L;
        long stay = cfg.getLong("titles.stay", 40) * 50L;
        long out = cfg.getLong("titles.fade-out", 10) * 50L;
        ConfigFile f = plugin.getConfigurationManager().messagesFile();
        String t = lookup(f.yaml(), f.defaults(), "titles." + key + ".title");
        String st = lookup(f.yaml(), f.defaults(), "titles." + key + ".subtitle");
        Component title = TextUtil.parse(TextUtil.replace(t == null ? "" : t, vars));
        Component sub = TextUtil.parse(TextUtil.replace(st == null ? "" : st, vars));
        return Title.title(title, sub, Title.Times.times(Duration.ofMillis(in), Duration.ofMillis(stay), Duration.ofMillis(out)));
    }
}
