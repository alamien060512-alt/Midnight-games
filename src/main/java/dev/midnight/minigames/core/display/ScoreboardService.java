package dev.midnight.minigames.core.display;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.game.GameState;
import dev.midnight.minigames.core.game.PlayerContext;
import dev.midnight.minigames.core.player.PlayerStats;
import dev.midnight.minigames.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.scheduler.BukkitTask;

/**
 * Per-player sidebar scoreboards. One repeating task updates only the boards that exist, and lines are only
 * sent to the client when their rendered text changed. Layouts live in config.yml -&gt; scoreboard.layouts.
 */
public final class ScoreboardService {

    private static final int MAX_LINES = 15;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yy");

    private static final class Board {
        final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective;
        final Team[] teams = new Team[MAX_LINES];
        final String[] entries = new String[MAX_LINES];
        final String[] last = new String[MAX_LINES];
        String lastTitle = "";
        int shown = 0;
    }

    private final MidnightMinigames plugin;
    private final Map<UUID, Board> boards = new HashMap<>();
    private BukkitTask task;

    public ScoreboardService(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection cfg() {
        return plugin.getConfigurationManager().config().getConfigurationSection("scoreboard");
    }

    public boolean isEnabled() {
        ConfigurationSection s = cfg();
        return s != null && s.getBoolean("enabled", true);
    }

    public void start() {
        stop();
        if (!isEnabled()) {
            return;
        }
        long interval = Math.max(5L, cfg().getLong("update-ticks", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
        for (UUID id : new ArrayList<>(boards.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    private void updateAll() {
        Iterator<Map.Entry<UUID, Board>> it = boards.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Board> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) {
                it.remove();
                continue;
            }
            render(p, e.getValue());
        }
    }

    /** Creates the board if needed and renders it now (used on join and state changes). */
    public void refresh(Player player) {
        if (!isEnabled() || !player.isOnline()) {
            return;
        }
        PlayerContext ctx = plugin.getPlayerManager().getContext(player);
        if (ctx == null && !cfg().getBoolean("show-in-lobby", true)) {
            remove(player);
            return;
        }
        Board b = boards.get(player.getUniqueId());
        if (b == null) {
            b = new Board();
            b.objective = b.scoreboard.registerNewObjective("midnight", Criteria.DUMMY, Component.text("Midnight"));
            b.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            if (cfg().getBoolean("hide-score-numbers", true)) {
                b.objective.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
            }
            for (int i = 0; i < MAX_LINES; i++) {
                b.entries[i] = "§" + Integer.toHexString(i) + "§r";
                b.teams[i] = b.scoreboard.registerNewTeam("l" + i);
                b.teams[i].addEntry(b.entries[i]);
            }
            boards.put(player.getUniqueId(), b);
            player.setScoreboard(b.scoreboard);
            if (plugin.getRanks() != null) {
                plugin.getRanks().applyNametags(b.scoreboard);
            }
        }
        render(player, b);
    }

    public void remove(Player player) {
        if (boards.remove(player.getUniqueId()) != null && player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private ConfigurationSection layoutFor(PlayerContext ctx) {
        ConfigurationSection layouts = cfg().getConfigurationSection("layouts");
        if (layouts == null) {
            return null;
        }
        if (ctx == null) {
            return layouts.getConfigurationSection("lobby");
        }
        GameState st = ctx.getState();
        String stateKey = st.isJoinable() ? "waiting" : st == GameState.IN_GAME ? "ingame" : "ending";
        String game = ctx.getMinigame().getId();
        ConfigurationSection g = layouts.getConfigurationSection(game);
        ConfigurationSection l = g == null ? null : g.getConfigurationSection(stateKey);
        if (l == null && g != null && stateKey.equals("ending")) {
            l = g.getConfigurationSection("ingame");
        }
        if (l == null) {
            ConfigurationSection d = layouts.getConfigurationSection("default");
            l = d == null ? null : d.getConfigurationSection(stateKey);
            if (l == null && d != null) {
                l = d.getConfigurationSection("ingame");
            }
        }
        return l;
    }

    private void render(Player p, Board b) {
        PlayerContext ctx = plugin.getPlayerManager().getContext(p);
        ConfigurationSection layout = layoutFor(ctx);
        if (layout == null) {
            return;
        }
        Map<String, String> vars = new HashMap<>();
        PlayerStats stats = plugin.getPlayerManager().getStats(p);
        vars.put("player", p.getName());
        vars.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        vars.put("coins", TextUtil.formatNumber(stats.getCoins()));
        if (plugin.getRanks() != null) {
            var rank = plugin.getRanks().getRank(p);
            vars.put("rank", rank == null ? "-" : TextUtil.plain(TextUtil.parse(rank.name)));
            vars.put("rank_prefix", rank == null ? "" : rank.prefix);
        }
        vars.put("wins", String.valueOf(stats.getWins()));
        vars.put("losses", String.valueOf(stats.getLosses()));
        vars.put("kills", String.valueOf(stats.getKills()));
        vars.put("deaths", String.valueOf(stats.getDeaths()));
        vars.put("date", LocalDate.now().format(DATE));
        vars.put("in_games", String.valueOf(plugin.getPlayerManager().countInGames()));
        if (ctx != null) {
            vars.putAll(ctx.getScoreboardVariables(p));
        }
        boolean papi = plugin.isPlaceholderApiPresent() && cfg().getBoolean("use-placeholderapi", true);

        String title = TextUtil.replace(layout.getString("title", cfg().getString("title", "Midnight")), vars);
        if (papi) {
            title = plugin.parsePlaceholderApi(p, title);
        }
        if (!title.equals(b.lastTitle)) {
            b.objective.displayName(TextUtil.parse(title));
            b.lastTitle = title;
        }
        List<String> rendered = new ArrayList<>();
        for (String template : layout.getStringList("lines")) {
            String text = TextUtil.replace(template, vars);
            if (papi) {
                text = plugin.parsePlaceholderApi(p, text);
            }
            // A line that only consisted of placeholders which resolved to nothing is hidden.
            if (text.isEmpty() && template.contains("{")) {
                continue;
            }
            rendered.add(text);
            if (rendered.size() >= MAX_LINES) {
                break;
            }
        }
        int count = rendered.size();
        boolean countChanged = b.shown != count;
        for (int i = 0; i < count; i++) {
            String text = rendered.get(i);
            if (!text.equals(b.last[i])) {
                b.teams[i].prefix(TextUtil.parse(text));
                b.last[i] = text;
            }
            if (countChanged || i >= b.shown) {
                b.objective.getScore(b.entries[i]).setScore(count - i);
            }
        }
        for (int i = count; i < b.shown; i++) {
            b.scoreboard.resetScores(b.entries[i]);
            b.last[i] = null;
        }
        b.shown = count;
    }
}
