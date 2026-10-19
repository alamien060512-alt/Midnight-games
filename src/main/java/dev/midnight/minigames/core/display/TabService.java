package dev.midnight.minigames.core.display;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.game.PlayerContext;
import dev.midnight.minigames.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Optional tab list prefixes/suffixes. Updates are event driven (join, leave, game start) - there is no
 * repeating task. Other plugins can add {@link TabProvider}s through the API.
 */
public final class TabService {

    /** Supplies tab list decoration. Return null for "no opinion". Strings are MiniMessage. */
    public interface TabProvider {
        String prefix(Player player);

        String suffix(Player player);
    }

    private final MidnightMinigames plugin;
    private final List<TabProvider> providers = new CopyOnWriteArrayList<>();

    public TabService(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    public void addProvider(TabProvider provider) {
        providers.add(provider);
    }

    public void removeProvider(TabProvider provider) {
        providers.remove(provider);
    }

    private ConfigurationSection cfg() {
        return plugin.getConfigurationManager().config().getConfigurationSection("tab");
    }

    public boolean isEnabled() {
        ConfigurationSection s = cfg();
        return s != null && s.getBoolean("enabled", false);
    }

    public void update(Player player) {
        if (!isEnabled() || !player.isOnline()) {
            return;
        }
        ConfigurationSection s = cfg();
        PlayerContext ctx = plugin.getPlayerManager().getContext(player);
        String prefix = ctx == null ? null : ctx.getTabPrefix(player);
        String suffix = ctx == null ? null : ctx.getTabSuffix(player);
        for (TabProvider p : providers) {
            if (prefix == null) {
                prefix = p.prefix(player);
            }
            if (suffix == null) {
                suffix = p.suffix(player);
            }
        }
        if (prefix == null && ctx != null) {
            prefix = s.getString("prefixes." + ctx.getMinigame().getId());
        }
        if (prefix == null) {
            prefix = s.getString("prefixes.lobby", "");
        }
        String format = s.getString("format", "{prefix}{player}{suffix}");
        String rendered = TextUtil.replace(format, "prefix", prefix == null ? "" : prefix,
                "suffix", suffix == null ? "" : suffix, "player", player.getName());
        player.playerListName(rendered.equals(player.getName()) ? null : TextUtil.parse(rendered));
    }

    public void sendHeaderFooter(Player player) {
        ConfigurationSection s = cfg();
        if (s == null || !s.getBoolean("enabled", false)) {
            return;
        }
        List<String> header = s.getStringList("header");
        List<String> footer = s.getStringList("footer");
        if (header.isEmpty() && footer.isEmpty()) {
            return;
        }
        String online = String.valueOf(Bukkit.getOnlinePlayers().size());
        Component h = TextUtil.parse(TextUtil.replace(String.join("\n", header), "online", online, "player", player.getName()));
        Component f = TextUtil.parse(TextUtil.replace(String.join("\n", footer), "online", online, "player", player.getName()));
        player.sendPlayerListHeaderAndFooter(h, f);
    }

    public void reset(Player player) {
        player.playerListName(null);
    }
}
