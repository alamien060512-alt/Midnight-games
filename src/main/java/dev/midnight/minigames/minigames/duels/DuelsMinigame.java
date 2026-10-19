package dev.midnight.minigames.minigames.duels;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.JoinResult;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.util.ConfigUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Duels: queue for a kit (each kit is a mode) and fight 1v1 on a free arena. Also supports direct challenges
 * ({@code /duels challenge <player> [kit]} + {@code /duels accept}). {@code OneVOneMinigame} reuses this class.
 */
public class DuelsMinigame extends AbstractArenaMinigame {

    private record Challenge(UUID challenger, String kit, long expires) {
    }

    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<UUID, Challenge> challenges = new HashMap<>();
    private final Random random = new Random();
    private final boolean kitModes;
    private final Map<String, List<dev.midnight.minigames.core.storage.TopEntry>> eloTop = new HashMap<>();
    private final Map<String, Long> eloTopTime = new HashMap<>();

    public DuelsMinigame(MidnightMinigames plugin) {
        this(plugin, "duels", "<gold>Duels", true);
    }

    protected DuelsMinigame(MidnightMinigames plugin, String id, String display, boolean kitModes) {
        super(plugin, id, display);
        this.kitModes = kitModes;
    }

    @Override
    protected void onEnable() {
        loadKits();
    }

    @Override
    protected void onReload() {
        loadKits();
    }

    private void loadKits() {
        kits.clear();
        for (ConfigurationSection k : ConfigUtil.sections(plugin.getConfigurationManager().game("duels"), "kits")) {
            kits.put(k.getString("id").toLowerCase(Locale.ROOT), new Kit(k.getString("id").toLowerCase(Locale.ROOT), k));
        }
        if (kits.isEmpty()) {
            plugin.getLogger().warning("[config] games/duels.yml -> kits: no kits defined");
        }
        if (kitModes) {
            modes = new ArrayList<>();
            for (Kit k : kits.values()) {
                modes.add(ModeDefinition.fromConfig(k.getId(), k.getSection()));
            }
        }
    }

    @Override
    protected void loadModes() {
        if (kitModes) {
            return; // built from the kits in loadKits()
        }
        super.loadModes();
    }

    @Override
    public void enable() {
        super.enable();
    }

    public Kit getKit(String id) {
        return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT));
    }

    /** Resolves the kit used for a mode. 1v1 modes point to a kit (or "random") via their "kit" setting. */
    Kit resolveKit(ModeDefinition mode) {
        String ref = kitModes ? mode.getId() : mode.getRaw().getString("kit", "random");
        if (ref.equalsIgnoreCase("random")) {
            List<Kit> list = new ArrayList<>(kits.values());
            return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
        }
        return kits.get(ref.toLowerCase(Locale.ROOT));
    }

    @Override
    protected GameInstance createInstance(Arena arena, ModeDefinition mode) {
        Kit kit = resolveKit(mode);
        if (kit == null) {
            throw new IllegalStateException("no kit available for mode '" + mode.getId() + "' (check games/duels.yml)");
        }
        return new DuelGame(plugin, this, arena, mode, kit);
    }

    @Override
    protected boolean arenaMatchesMode(Arena arena, ModeDefinition mode) {
        String m = arena.getMode();
        return m.equalsIgnoreCase("any") || m.equalsIgnoreCase(mode.getId());
    }

    @Override
    public Arena createArena(String name) {
        return plugin.getArenaManager().create(getId(), name, null, "any", 2, 2);
    }

    @Override
    public List<String> validateArena(Arena arena) {
        List<String> problems = new ArrayList<>(arena.validateBasics());
        for (String team : List.of("red", "blue")) {
            if (arena.getTeamSpawn(team) == null) {
                problems.add("Missing spawn for '" + team + "': /minigame arena setspawn " + getId() + " " + arena.getName() + " " + team);
            }
        }
        if (arena.getMaxPlayers() != 2) {
            problems.add("Duel arenas must have max-players 2 (/minigame arena setplayers)");
        }
        if (arena.getSpectator() == null) {
            problems.add("Spectator location missing: /minigame arena setspectator");
        }
        return problems;
    }

    // ---- ranked ladder -----------------------------------------------------------------------------------

    private List<dev.midnight.minigames.core.storage.TopEntry> eloTop(String kitId) {
        long now = System.currentTimeMillis();
        if (now - eloTopTime.getOrDefault(kitId, 0L) > 60_000L) {
            eloTopTime.put(kitId, now);
            plugin.getStorage().topData("elo", kitId, 10, false).whenComplete((list, ex) -> {
                if (ex == null) {
                    eloTop.put(kitId, list);
                }
            });
        }
        return eloTop.getOrDefault(kitId, List.of());
    }

    private long eloOf(Player p, String kitId) {
        return plugin.getPlayerManager().getProfile(p).getLong("elo", kitId,
                plugin.getConfigurationManager().game("duels").getInt("ranked.starting", 1000));
    }

    @Override
    public void registerPlaceholders(dev.midnight.minigames.core.placeholder.PlaceholderRegistry registry) {
        // %midnight_<game>_elo_<kit>%  %midnight_<game>_top_<kit>_<n>_name|elo%
        registry.register(getId(), (player, key) -> {
            if (key.startsWith("elo_") && player != null) {
                return String.valueOf(eloOf(player, key.substring(4)));
            }
            if (key.startsWith("top_")) {
                String[] parts = key.substring(4).split("_");
                if (parts.length < 3) {
                    return null;
                }
                try {
                    int rank = Integer.parseInt(parts[parts.length - 2]);
                    String field = parts[parts.length - 1];
                    String kitId = String.join("_", java.util.Arrays.copyOfRange(parts, 0, parts.length - 2));
                    List<dev.midnight.minigames.core.storage.TopEntry> list = eloTop(kitId);
                    if (rank < 1 || rank > list.size()) {
                        return "-";
                    }
                    var e = list.get(rank - 1);
                    return field.equals("name") ? e.name() : String.valueOf((long) e.value());
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
            return null;
        });
    }

    // ---- direct challenges ------------------------------------------------------------------------------

    @Override
    public boolean handleGameCommand(Player player, String[] args) {
        if (args.length == 0) {
            return false;
        }
        var m = plugin.getMessages();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "elo" -> {
                String kitId = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : (kits.isEmpty() ? "" : kits.keySet().iterator().next());
                m.send(player, "duels.elo", "kit", kitId, "elo", String.valueOf(eloOf(player, kitId)));
            }
            case "top" -> {
                String kitId = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : (kits.isEmpty() ? "" : kits.keySet().iterator().next());
                List<dev.midnight.minigames.core.storage.TopEntry> list = eloTop(kitId);
                m.send(player, "duels.top-header", "kit", kitId);
                if (list.isEmpty()) {
                    m.send(player, "duels.top-empty");
                }
                int rank = 1;
                for (var e : list) {
                    m.send(player, "duels.top-entry", "rank", String.valueOf(rank++), "player", e.name(), "elo", String.valueOf((long) e.value()));
                }
            }
            case "challenge" -> {
                if (args.length < 2) {
                    m.send(player, "duels.challenge-usage");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || target.equals(player)) {
                    m.send(player, "general.player-not-found", "player", args[1]);
                    return true;
                }
                String kitId = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : (kits.isEmpty() ? "" : kits.keySet().iterator().next());
                if (findMode(kitId) == null) {
                    m.send(player, "general.unknown-mode", "mode", kitId);
                    return true;
                }
                if (plugin.getPlayerManager().isInGame(player) || plugin.getPlayerManager().isInGame(target)) {
                    m.send(player, "duels.busy");
                    return true;
                }
                challenges.put(target.getUniqueId(), new Challenge(player.getUniqueId(), kitId, System.currentTimeMillis() + 60_000L));
                m.send(player, "duels.challenge-sent", "player", target.getName(), "kit", kitId);
                m.send(target, "duels.challenge-received", "player", player.getName(), "kit", kitId);
            }
            case "accept" -> {
                Challenge c = challenges.remove(player.getUniqueId());
                Player challenger = c == null ? null : Bukkit.getPlayer(c.challenger());
                if (c == null || c.expires() < System.currentTimeMillis() || challenger == null) {
                    m.send(player, "duels.no-challenge");
                    return true;
                }
                if (plugin.getPlayerManager().isInGame(player) || plugin.getPlayerManager().isInGame(challenger)) {
                    m.send(player, "duels.busy");
                    return true;
                }
                ModeDefinition mode = findMode(c.kit());
                if (mode == null) {
                    m.send(player, "general.unknown-mode", "mode", c.kit());
                    return true;
                }
                JoinResult r = joinNewInstance(List.of(challenger, player), mode);
                if (r != JoinResult.JOINED) {
                    m.send(player, "join.result." + r.name().toLowerCase(), "game", getDisplayName());
                    m.send(challenger, "join.result." + r.name().toLowerCase(), "game", getDisplayName());
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
