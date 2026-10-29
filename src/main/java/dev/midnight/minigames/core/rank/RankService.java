package dev.midnight.minigames.core.rank;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.display.TabService;
import dev.midnight.minigames.core.player.PlayerProfile;
import dev.midnight.minigames.util.ConfigUtil;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Ranks that players buy with coins (ranks.yml). A rank gives a chat/tab prefix, a coin reward multiplier, permission nodes
 * (as a temporary attachment, so no permission plugin is needed) and can run console commands (e.g. LuckPerms) when it is
 * granted or removed. The list order is the ladder: a higher rank replaces a lower one.
 */
public final class RankService implements TabService.TabProvider {

    private static final String NS = "rank";

    public static final class Rank {
        public final String id;
        public final String name;
        public final String prefix;
        public final double price;
        public final double multiplier;
        public final Material icon;
        public final List<String> lore;
        public final List<String> permissions;
        public final List<String> commands;
        public final List<String> removeCommands;
        public final int index;

        Rank(int index, ConfigurationSection s) {
            this.index = index;
            this.id = s.getString("id", "").toLowerCase(Locale.ROOT);
            this.name = s.getString("name", id);
            this.prefix = s.getString("prefix", "");
            this.price = Math.max(0, s.getDouble("price", 0));
            this.multiplier = Math.max(0, s.getDouble("coin-multiplier", 1.0));
            this.icon = ItemFactory.material(s.getString("icon", "NAME_TAG"), Material.NAME_TAG);
            this.lore = s.getStringList("lore");
            this.permissions = s.getStringList("permissions");
            this.commands = s.getStringList("commands");
            this.removeCommands = s.getStringList("remove-commands");
        }
    }

    public enum Result { OK, ALREADY_OWNED, HAVE_BETTER, NOT_ENOUGH, LOADING }

    private final MidnightMinigames plugin;
    private final List<Rank> ranks = new ArrayList<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public RankService(MidnightMinigames plugin) {
        this.plugin = plugin;
        plugin.getTabService().addProvider(this);
    }

    public void reload() {
        ranks.clear();
        YamlConfiguration y = plugin.getConfigurationManager().ranks();
        for (ConfigurationSection s : ConfigUtil.sections(y, "ranks")) {
            Rank r = new Rank(ranks.size(), s);
            if (r.id.isEmpty()) {
                plugin.getLogger().warning("[config] ranks.yml -> ranks: an entry without id was ignored");
                continue;
            }
            ranks.add(r);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            applyPermissions(p);
            refreshDisplay(p);
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, PermissionAttachment> e : attachments.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                p.removeAttachment(e.getValue());
            }
        }
        attachments.clear();
    }

    public List<Rank> getRanks() {
        return new ArrayList<>(ranks);
    }

    @Nullable
    public Rank find(String id) {
        for (Rank r : ranks) {
            if (r.id.equalsIgnoreCase(id)) {
                return r;
            }
        }
        return null;
    }

    @Nullable
    public Rank getRank(Player p) {
        return find(plugin.getPlayerManager().getProfile(p).getString(NS, "id", ""));
    }

    public String prefixOf(Player p) {
        Rank r = getRank(p);
        return r != null ? r.prefix : plugin.getConfigurationManager().ranks().getString("default-prefix", "");
    }

    public double multiplier(Player p) {
        Rank r = getRank(p);
        return r == null ? 1.0 : r.multiplier;
    }

    // ---- granting -------------------------------------------------------------------------------------------------------

    /** Sets (or with null clears) the rank, runs the grant/remove commands and refreshes permissions and the tab list. */
    public void set(Player p, @Nullable Rank rank) {
        Rank old = getRank(p);
        PlayerProfile pr = plugin.getPlayerManager().getProfile(p);
        pr.setString(NS, "id", rank == null ? "" : rank.id);
        if (old != null && old != rank) {
            runCommands(old.removeCommands, p);
        }
        if (rank != null && rank != old) {
            runCommands(rank.commands, p);
        }
        applyPermissions(p);
        refreshDisplay(p);
        plugin.getScoreboardService().refresh(p);
    }

    private void runCommands(List<String> commands, Player p) {
        for (String raw : commands) {
            String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), TextUtil.replace(cmd, "player", p.getName()));
        }
    }

    public void applyPermissions(Player p) {
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) {
            p.removeAttachment(old);
        }
        Rank r = getRank(p);
        if (r == null || r.permissions.isEmpty()) {
            return;
        }
        PermissionAttachment att = p.addAttachment(plugin);
        for (String node : r.permissions) {
            att.setPermission(node, true);
        }
        attachments.put(p.getUniqueId(), att);
    }

    public void onLoaded(Player p) {
        applyPermissions(p);
        refreshDisplay(p);
    }

    /**
     * Makes the rank visible: the player's display name (chat, other plugins' %player_displayname% style placeholders) and
     * the tab list name carry the prefix. When the plugin's own tab list (config.yml -> tab.enabled) is on, it draws the
     * prefix itself.
     */
    public void refreshDisplay(Player p) {
        var y = plugin.getConfigurationManager().ranks();
        String prefix = prefixOf(p);
        boolean show = !prefix.isEmpty();
        if (y.getBoolean("show-in-display-name", true)) {
            p.displayName(show ? TextUtil.parse(prefix + "<white>" + p.getName()) : null);
        }
        if (plugin.getTabService().isEnabled()) {
            plugin.getTabService().update(p);
        } else if (y.getBoolean("show-in-tab", true)) {
            p.playerListName(show ? TextUtil.parse(prefix + "<white>" + p.getName()) : null);
        }
    }

    public void onQuit(Player p) {
        attachments.remove(p.getUniqueId());
    }

    // ---- buying ----------------------------------------------------------------------------------------------------------

    /** What a player has to pay for a rank right now (the difference to the current rank when upgrade-pays-difference is on). */
    public double costFor(Player p, Rank target) {
        Rank cur = getRank(p);
        if (cur != null && plugin.getConfigurationManager().ranks().getBoolean("upgrade-pays-difference", true)) {
            return Math.max(0, target.price - cur.price);
        }
        return target.price;
    }

    public Result purchase(Player p, Rank target) {
        PlayerProfile pr = plugin.getPlayerManager().getProfile(p);
        if (!pr.isLoaded()) {
            return Result.LOADING;
        }
        Rank cur = getRank(p);
        if (cur == target) {
            return Result.ALREADY_OWNED;
        }
        if (cur != null && cur.index > target.index) {
            return Result.HAVE_BETTER;
        }
        if (!plugin.getEconomy().withdraw(p, costFor(p, target))) {
            return Result.NOT_ENOUGH;
        }
        set(p, target);
        return Result.OK;
    }

    // ---- chat and tab -----------------------------------------------------------------------------------------------------

    public boolean isChatEnabled() {
        return plugin.getConfigurationManager().ranks().getBoolean("chat.enabled", false);
    }

    public Component formatChat(Player source, Component message) {
        String format = plugin.getConfigurationManager().ranks().getString("chat.format", "{prefix}<white>{player}<gray>: <white>{message}");
        String token = "%%MESSAGE%%";
        Component base = TextUtil.parse(TextUtil.replace(format, "prefix", prefixOf(source), "player", source.getName(), "message", token));
        return base.replaceText(TextReplacementConfig.builder().matchLiteral(token).replacement(message).build());
    }

    @Override
    public String prefix(Player player) {
        String p = prefixOf(player);
        return p.isEmpty() ? null : p;
    }

    @Override
    public String suffix(Player player) {
        return null;
    }
}
