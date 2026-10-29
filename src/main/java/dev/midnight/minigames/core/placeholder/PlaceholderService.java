package dev.midnight.minigames.core.placeholder;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.Minigame;
import dev.midnight.minigames.core.game.PlayerContext;
import dev.midnight.minigames.core.player.PlayerStats;
import dev.midnight.minigames.core.storage.TopEntry;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves {@code %midnight_<params>%}. Works with or without PlaceholderAPI - the scoreboard uses it directly,
 * the PlaceholderAPI expansion simply delegates here.
 */
public final class PlaceholderService {

    private static final class CachedTop {
        volatile long time;
        volatile List<TopEntry> entries = List.of();
        volatile boolean loading;
    }

    private final MidnightMinigames plugin;
    private final Map<String, CachedTop> topCache = new HashMap<>();

    public PlaceholderService(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    private String none() {
        return plugin.getMessages().text("placeholders.none");
    }

    /** @return the replacement or null when the placeholder is unknown. */
    public String resolve(Player player, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        switch (p) {
            case "online":
                return String.valueOf(Bukkit.getOnlinePlayers().size());
            case "player_count":
                return String.valueOf(plugin.getPlayerManager().countInGames());
            default:
                break;
        }
        if (p.startsWith("top_")) {
            return resolveTop(p);
        }
        if (p.startsWith("var_")) {
            if (player == null) {
                return "";
            }
            PlayerContext ctx = plugin.getPlayerManager().getContext(player);
            if (ctx == null) {
                return none();
            }
            String v = ctx.getScoreboardVariables(player).get(p.substring(4));
            return v == null ? "" : v;
        }
        PlayerContext ctx = player == null ? null : plugin.getPlayerManager().getContext(player);
        switch (p) {
            case "current_game":
                return ctx == null ? none() : displayPlain(ctx.getMinigame());
            case "game_state":
                return ctx == null ? none() : plugin.getMessages().text("state." + ctx.getState().name().toLowerCase());
            case "game_players":
                return ctx == null ? "0" : String.valueOf(ctx.getPlayerCount());
            case "game_max_players":
                return ctx == null ? "0" : String.valueOf(ctx.getMaxPlayers());
            case "arena":
                return ctx == null || ctx.getArenaName() == null ? none() : ctx.getArenaName();
            case "mode":
                return ctx == null ? none() : ctx.getModeName();
            default:
                break;
        }
        if (player != null && plugin.getRanks() != null) {
            switch (p) {
                case "rank" -> {
                    var r = plugin.getRanks().getRank(player);
                    return r == null ? "-" : TextUtil.plain(TextUtil.parse(r.name));
                }
                case "rank_prefix" -> {
                    return TextUtil.plain(TextUtil.parse(plugin.getRanks().prefixOf(player)));
                }
                case "rank_multiplier" -> {
                    return String.format(java.util.Locale.ROOT, "%.2f", plugin.getRanks().multiplier(player));
                }
                default -> {
                }
            }
            if (p.startsWith("cosmetic_") && plugin.getCosmetics() != null) {
                var item = plugin.getCosmetics().getActive(player, p.substring(9).replace('_', '-'));
                return item == null ? "-" : TextUtil.plain(TextUtil.parse(item.name));
            }
        }
        if (player != null) {
            PlayerStats s = plugin.getPlayerManager().getStats(player);
            switch (p) {
                case "wins":
                    return String.valueOf(s.getWins());
                case "losses":
                    return String.valueOf(s.getLosses());
                case "kills":
                    return String.valueOf(s.getKills());
                case "deaths":
                    return String.valueOf(s.getDeaths());
                case "coins":
                    return TextUtil.formatNumber(s.getCoins());
                case "games_played":
                    return String.valueOf(s.getGamesPlayed());
                case "kdr":
                    return String.valueOf(s.getKdr());
                default:
                    break;
            }
        }
        // <game>_players, <game>_wins ... and game specific placeholders from the registry
        int i = p.indexOf('_');
        if (i > 0) {
            String gameId = p.substring(0, i);
            String key = p.substring(i + 1);
            Minigame mg = plugin.getMinigameManager().get(gameId);
            if (mg != null) {
                if (key.equals("players")) {
                    return String.valueOf(mg.getPlayers().size());
                }
                if (key.equals("state")) {
                    return plugin.getMessages().text("state." + mg.getState().name().toLowerCase());
                }
                if (mg instanceof AbstractArenaMinigame am && key.startsWith("players_")) {
                    return String.valueOf(am.countPlayers(key.substring(8)));
                }
                String custom = plugin.getPlaceholderRegistry().resolve(player, p);
                if (custom != null) {
                    return custom;
                }
                if (player != null && (key.equals("wins") || key.equals("losses") || key.equals("kills")
                        || key.equals("deaths") || key.equals("played"))) {
                    return String.valueOf((long) plugin.getPlayerManager().getGameStat(player, gameId, key));
                }
            }
        }
        return null;
    }

    private String displayPlain(Minigame mg) {
        return TextUtil.plain(TextUtil.parse(mg.getDisplayName()));
    }

    /** top_&lt;column&gt;_&lt;rank&gt;_name|value, e.g. top_wins_1_name. Backed by a 60 second async cache. */
    private String resolveTop(String p) {
        String[] parts = p.split("_");
        // top, [games, played | column], rank, field
        if (parts.length < 4) {
            return null;
        }
        String field = parts[parts.length - 1];
        int rank;
        try {
            rank = Integer.parseInt(parts[parts.length - 2]);
        } catch (NumberFormatException ex) {
            return null;
        }
        StringBuilder col = new StringBuilder();
        for (int i = 1; i < parts.length - 2; i++) {
            col.append(i > 1 ? "_" : "").append(parts[i]);
        }
        String column = col.toString();
        CachedTop cached;
        synchronized (topCache) {
            cached = topCache.computeIfAbsent(column, k -> new CachedTop());
        }
        long now = System.currentTimeMillis();
        if (!cached.loading && now - cached.time > 60_000L) {
            cached.loading = true;
            final CachedTop target = cached;
            plugin.getStorage().topStat(column, 10).whenComplete((list, ex) -> {
                if (ex == null) {
                    target.entries = list;
                }
                target.time = System.currentTimeMillis();
                target.loading = false;
            });
        }
        List<TopEntry> list = cached.entries;
        if (rank < 1 || rank > list.size()) {
            return field.equals("name") ? "-" : "0";
        }
        TopEntry e = list.get(rank - 1);
        return field.equals("name") ? e.name() : TextUtil.formatNumber(e.value());
    }
}
