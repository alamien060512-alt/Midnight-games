package dev.midnight.minigames.minigames.parkour;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractPersistentMinigame;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.core.placeholder.PlaceholderRegistry;
import dev.midnight.minigames.core.storage.TopEntry;
import dev.midnight.minigames.util.ConfigUtil;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.Keys;
import dev.midnight.minigames.util.LocationUtil;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Parkour: courses are arenas of the "parkour" game. Setup: create the arena, /minigame arena setlobby (start),
 * addcheckpoint (stand on each checkpoint), setfinish, enable. Best times are stored per player and ranked.
 */
public final class ParkourMinigame extends AbstractPersistentMinigame {

    private static final class Run {
        final Arena course;
        long startNanos;
        int checkpoint = -1;
        Location lastCheckpoint;

        Run(Arena course) {
            this.course = course;
        }
    }

    private static final class CachedTop {
        volatile long time;
        volatile List<TopEntry> entries = List.of();
        volatile boolean loading;
    }

    private final Map<UUID, Run> runs = new HashMap<>();
    private final Map<String, CachedTop> topCache = new HashMap<>();
    private BukkitTask actionBarTask;

    public ParkourMinigame(MidnightMinigames plugin) {
        super(plugin, "parkour", "<green>Parkour");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        runs.clear();
        stopActionBar();
    }

    // ---- modes = courses ---------------------------------------------------------------------------------

    @Override
    public List<ModeDefinition> getModes() {
        List<ModeDefinition> list = new ArrayList<>();
        YamlConfiguration cfg = plugin.getConfigurationManager().game("parkour");
        for (Arena a : plugin.getArenaManager().getArenas(getId())) {
            if (!a.isEnabled()) {
                continue;
            }
            ConfigurationSection s = new MemoryConfiguration();
            s.set("name", a.getYaml().getString("display-name", "<green>" + a.getName()));
            s.set("material", a.getYaml().getString("icon", cfg.getString("default-icon", "LEATHER_BOOTS")));
            List<String> lore = new ArrayList<>();
            for (String line : cfg.getStringList("course-lore")) {
                lore.add(TextUtil.replace(line, "checkpoints", String.valueOf(a.getYaml().getStringList("data.checkpoints").size()),
                        "difficulty", a.getYaml().getString("difficulty", "?"), "players", "{players}"));
            }
            s.set("lore", lore);
            list.add(ModeDefinition.fromConfig(a.getName().toLowerCase(Locale.ROOT), s));
        }
        return list;
    }

    // ---- joining -----------------------------------------------------------------------------------------------

    @Override
    protected Location resolveEntry(Player player, String modeId) {
        Arena course = plugin.getArenaManager().get(getId(), modeId == null ? "" : modeId);
        if (course == null || !course.isEnabled()) {
            return null;
        }
        Location l = course.getLobby();
        if (l == null) {
            plugin.getLogger().severe("Parkour course " + course.getName() + " has no start location (/minigame arena setlobby)");
        }
        return l;
    }

    @Override
    protected void onEnter(Player player, String modeId) {
        Arena course = plugin.getArenaManager().get(getId(), modeId);
        Run run = new Run(course);
        run.lastCheckpoint = course.getLobby();
        runs.put(player.getUniqueId(), run);
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        giveItems(player);
        startActionBar();
        plugin.getMessages().send(player, "parkour.started", "course", course.getName());
    }

    @Override
    protected void onExit(Player player) {
        runs.remove(player.getUniqueId());
        removeItems(player);
        if (runs.isEmpty()) {
            stopActionBar();
        }
    }

    private void giveItems(Player p) {
        YamlConfiguration cfg = plugin.getConfigurationManager().game("parkour");
        for (ConfigurationSection item : ConfigUtil.sections(cfg, "items")) {
            p.getInventory().setItem(item.getInt("slot", 0), ItemFactory.build(item));
        }
    }

    private void removeItems(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null) {
                String action = ItemFactory.readTag(it, Keys.ACTION);
                if (action != null && (action.startsWith("parkour_") || action.equals("leave"))) {
                    p.getInventory().setItem(i, null);
                }
            }
        }
    }

    // ---- run logic ---------------------------------------------------------------------------------------------

    @Override
    public boolean wantsMoveEvents() {
        return true;
    }

    @Override
    public void onMove(Player player, PlayerMoveEvent e) {
        Run run = runs.get(player.getUniqueId());
        if (run == null) {
            return;
        }
        Location to = e.getTo();
        if (run.startNanos == 0) {
            run.startNanos = System.nanoTime();
        }
        if (to.getY() < run.course.getVoidY()) {
            backToCheckpoint(player, run);
            return;
        }
        List<String> cps = run.course.getYaml().getStringList("data.checkpoints");
        if (run.checkpoint + 1 < cps.size() && at(to, cps.get(run.checkpoint + 1))) {
            run.checkpoint++;
            run.lastCheckpoint = LocationUtil.deserialize(cps.get(run.checkpoint));
            if (run.lastCheckpoint != null) {
                run.lastCheckpoint = run.lastCheckpoint.clone().add(0.5, 0, 0.5);
                run.lastCheckpoint.setYaw(player.getLocation().getYaw());
            }
            plugin.getMessages().send(player, "parkour.checkpoint", "number", String.valueOf(run.checkpoint + 1),
                    "total", String.valueOf(cps.size()), "time", TextUtil.formatMillis(elapsedMs(run)));
            plugin.getEffects().play(player, "checkpoint");
            return;
        }
        String fin = run.course.getYaml().getString("data.finish");
        if (fin != null && at(to, fin)) {
            boolean all = plugin.getConfigurationManager().game("parkour").getBoolean("require-all-checkpoints", true);
            if (all && run.checkpoint + 1 < cps.size()) {
                plugin.getMessages().actionBar(player, "parkour.missing-checkpoints");
                return;
            }
            finish(player, run);
        }
    }

    private boolean at(Location to, String raw) {
        Location t = LocationUtil.deserialize(raw);
        return t != null && t.getWorld() == to.getWorld() && t.getBlockX() == to.getBlockX()
                && t.getBlockZ() == to.getBlockZ() && Math.abs(t.getBlockY() - to.getBlockY()) <= 1;
    }

    private long elapsedMs(Run run) {
        return run.startNanos == 0 ? 0 : (System.nanoTime() - run.startNanos) / 1_000_000L;
    }

    private void backToCheckpoint(Player p, Run run) {
        Location l = run.lastCheckpoint != null ? run.lastCheckpoint : run.course.getLobby();
        if (l != null) {
            p.teleport(l);
            p.setFallDistance(0f);
            plugin.getEffects().play(p, "parkour-fall");
        }
    }

    private void finish(Player p, Run run) {
        long ms = elapsedMs(run);
        String course = run.course.getName().toLowerCase(Locale.ROOT);
        String key = "best." + course;
        var profile = plugin.getPlayerManager().getProfile(p);
        long best = profile.getLong("parkour", key, Long.MAX_VALUE);
        boolean record = ms < best;
        if (record) {
            profile.setLong("parkour", key, ms);
            CachedTop c = topCache.get(course);
            if (c != null) {
                c.time = 0; // force refresh of the leaderboard
            }
        }
        plugin.getPlayerManager().addGamePlayed(p, getId());
        plugin.getPlayerManager().addWin(p, getId());
        double reward = plugin.getConfigurationManager().config().getDouble("games.parkour.rewards.finish", 0);
        if (reward > 0) {
            plugin.getPlayerManager().addCoins(p, reward);
        }
        plugin.getMessages().send(p, record ? "parkour.finished-record" : "parkour.finished", "time", TextUtil.formatMillis(ms),
                "best", best == Long.MAX_VALUE ? TextUtil.formatMillis(ms) : TextUtil.formatMillis(Math.min(best, ms)), "course", run.course.getName());
        plugin.getMessages().title(p, "parkour-finish", "time", TextUtil.formatMillis(ms));
        plugin.getEffects().play(p, "win");
        plugin.getEffects().firework(p.getLocation());
        resetRun(p, run);
    }

    private void resetRun(Player p, Run run) {
        run.startNanos = 0;
        run.checkpoint = -1;
        run.lastCheckpoint = run.course.getLobby();
        if (run.lastCheckpoint != null) {
            p.teleport(run.lastCheckpoint);
        }
        p.setFallDistance(0f);
        plugin.getScoreboardService().refresh(p);
    }

    @Override
    protected void onDeathRespawn(Player victim) {
        Run run = runs.get(victim.getUniqueId());
        victim.setHealth(dev.midnight.minigames.util.PlayerUtil.maxHealth(victim));
        if (run != null) {
            backToCheckpoint(victim, run);
        }
    }

    @Override
    public boolean onItemAction(Player player, String action) {
        Run run = runs.get(player.getUniqueId());
        if (run != null) {
            switch (action) {
                case "parkour_reset" -> {
                    resetRun(player, run);
                    plugin.getMessages().send(player, "parkour.reset");
                    return true;
                }
                case "parkour_checkpoint" -> {
                    backToCheckpoint(player, run);
                    return true;
                }
                default -> {
                }
            }
        }
        return super.onItemAction(player, action);
    }

    // ---- protections ------------------------------------------------------------------------------------------

    @Override
    public void onDamage(Player victim, EntityDamageEvent e) {
        e.setCancelled(true);
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent e) {
        if (!plugin.getPermissions().has(player, "admin-build")) {
            e.setCancelled(true);
        }
    }

    @Override
    public void onBlockPlace(Player player, BlockPlaceEvent e) {
        if (!plugin.getPermissions().has(player, "admin-build")) {
            e.setCancelled(true);
        }
    }

    @Override
    public void onDrop(Player player, PlayerDropItemEvent e) {
        e.setCancelled(true);
    }

    @Override
    public void onFoodChange(Player player, FoodLevelChangeEvent e) {
        e.setCancelled(true);
    }

    // ---- action bar timer (task only exists while someone is running) ------------------------------------------

    private void startActionBar() {
        if (actionBarTask != null || !plugin.getConfigurationManager().game("parkour").getBoolean("actionbar-timer", true)) {
            return;
        }
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Run> e : runs.entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                Run r = e.getValue();
                if (p != null && r.startNanos != 0) {
                    plugin.getMessages().actionBar(p, "parkour.actionbar", "time", TextUtil.formatMillis(elapsedMs(r)),
                            "checkpoint", String.valueOf(r.checkpoint + 1),
                            "total", String.valueOf(r.course.getYaml().getStringList("data.checkpoints").size()));
                }
            }
        }, 2L, 2L);
    }

    private void stopActionBar() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
    }

    // ---- commands ------------------------------------------------------------------------------------------------

    @Override
    public boolean handleGameCommand(Player player, String[] args) {
        if (args.length == 0) {
            return false;
        }
        var m = plugin.getMessages();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reset", "restart" -> {
                Run run = runs.get(player.getUniqueId());
                if (run == null) {
                    m.send(player, "general.not-in-game");
                } else {
                    resetRun(player, run);
                    m.send(player, "parkour.reset");
                }
            }
            case "checkpoint", "cp" -> {
                Run run = runs.get(player.getUniqueId());
                if (run == null) {
                    m.send(player, "general.not-in-game");
                } else {
                    backToCheckpoint(player, run);
                }
            }
            case "list" -> {
                StringBuilder sb = new StringBuilder();
                for (ModeDefinition d : getModes()) {
                    sb.append(sb.length() == 0 ? "" : ", ").append(d.getId());
                }
                m.send(player, "parkour.list", "courses", sb.length() == 0 ? "-" : sb.toString());
            }
            case "top" -> {
                String course = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : courseOf(player);
                if (course == null || plugin.getArenaManager().get(getId(), course) == null) {
                    m.send(player, "parkour.top-usage");
                    return true;
                }
                CachedTop c = top(course);
                final String courseName = course;
                Bukkit.getScheduler().runTaskLater(plugin, () -> showTop(player, courseName), c.time == 0 ? 10L : 0L);
            }
            case "best" -> {
                String course = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : courseOf(player);
                if (course == null) {
                    m.send(player, "parkour.top-usage");
                    return true;
                }
                long best = plugin.getPlayerManager().getProfile(player).getLong("parkour", "best." + course, -1);
                m.send(player, "parkour.best", "course", course, "time", best < 0 ? "-" : TextUtil.formatMillis(best));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private String courseOf(Player p) {
        Run r = runs.get(p.getUniqueId());
        return r == null ? null : r.course.getName().toLowerCase(Locale.ROOT);
    }

    private CachedTop top(String course) {
        CachedTop c = topCache.computeIfAbsent(course, k -> new CachedTop());
        long now = System.currentTimeMillis();
        if (!c.loading && now - c.time > 60_000L) {
            c.loading = true;
            plugin.getStorage().topData("parkour", "best." + course, 10, true).whenComplete((list, ex) -> {
                if (ex == null) {
                    c.entries = list;
                }
                c.time = System.currentTimeMillis();
                c.loading = false;
            });
        }
        return c;
    }

    private void showTop(Player p, String course) {
        var m = plugin.getMessages();
        List<TopEntry> list = top(course).entries;
        m.send(p, "parkour.top-header", "course", course);
        if (list.isEmpty()) {
            m.send(p, "parkour.top-empty");
            return;
        }
        int rank = 1;
        for (TopEntry e : list) {
            m.send(p, "parkour.top-entry", "rank", String.valueOf(rank++), "player", e.name(), "time", TextUtil.formatMillis((long) e.value()));
        }
    }

    // ---- scoreboard / placeholders ---------------------------------------------------------------------------------

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = new HashMap<>();
        Run run = runs.get(player.getUniqueId());
        v.put("game", getDisplayPlain());
        v.put("players", String.valueOf(inside.size()));
        if (run != null) {
            v.put("course", run.course.getName());
            v.put("time", run.startNanos == 0 ? "00:00.000" : TextUtil.formatMillis(elapsedMs(run)));
            v.put("checkpoint", String.valueOf(run.checkpoint + 1));
            v.put("checkpoints", String.valueOf(run.course.getYaml().getStringList("data.checkpoints").size()));
            long best = plugin.getPlayerManager().getProfile(player).getLong("parkour", "best." + run.course.getName().toLowerCase(Locale.ROOT), -1);
            v.put("best", best < 0 ? "-" : TextUtil.formatMillis(best));
        }
        return v;
    }

    @Override
    public void registerPlaceholders(PlaceholderRegistry registry) {
        // %midnight_parkour_time% %midnight_parkour_course% %midnight_parkour_best_<course>% %midnight_parkour_top_<course>_<n>_name|time%
        registry.register(getId(), (player, key) -> {
            if (player != null && (key.equals("time") || key.equals("course") || key.equals("checkpoint"))) {
                String v = getScoreboardVariables(player).get(key);
                return v == null ? "-" : v;
            }
            if (key.startsWith("best_") && player != null) {
                long best = plugin.getPlayerManager().getProfile(player).getLong("parkour", "best." + key.substring(5), -1);
                return best < 0 ? "-" : TextUtil.formatMillis(best);
            }
            if (key.startsWith("top_")) {
                String[] parts = key.substring(4).split("_");
                if (parts.length < 3) {
                    return null;
                }
                try {
                    int rank = Integer.parseInt(parts[parts.length - 2]);
                    String field = parts[parts.length - 1];
                    String course = String.join("_", java.util.Arrays.copyOfRange(parts, 0, parts.length - 2));
                    List<TopEntry> list = top(course).entries;
                    if (rank < 1 || rank > list.size()) {
                        return "-";
                    }
                    TopEntry e = list.get(rank - 1);
                    return field.equals("name") ? e.name() : TextUtil.formatMillis((long) e.value());
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
            return null;
        });
    }

    // ---- admin setup ---------------------------------------------------------------------------------------------

    @Override
    public List<String> getArenaSubcommands() {
        return List.of("addcheckpoint", "clearcheckpoints", "setfinish", "seticon", "setdifficulty", "setdisplayname");
    }

    @Override
    public boolean handleArenaCommand(CommandSender sender, String sub, Arena arena, String[] args) {
        var m = plugin.getMessages();
        switch (sub) {
            case "addcheckpoint" -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "general.players-only");
                    return true;
                }
                List<String> list = new ArrayList<>(arena.getYaml().getStringList("data.checkpoints"));
                list.add(LocationUtil.serializeBlock(p.getLocation().getBlock()));
                arena.getYaml().set("data.checkpoints", list);
                m.send(sender, "parkour.checkpoint-added", "number", String.valueOf(list.size()));
            }
            case "clearcheckpoints" -> {
                arena.getYaml().set("data.checkpoints", null);
                m.send(sender, "parkour.checkpoints-cleared");
            }
            case "setfinish" -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "general.players-only");
                    return true;
                }
                arena.getYaml().set("data.finish", LocationUtil.serializeBlock(p.getLocation().getBlock()));
                m.send(sender, "parkour.finish-set");
            }
            case "seticon" -> {
                if (args.length < 1) {
                    return false;
                }
                arena.getYaml().set("icon", args[0].toUpperCase(Locale.ROOT));
                m.send(sender, "admin.value-set", "key", "icon", "value", args[0]);
            }
            case "setdifficulty" -> {
                if (args.length < 1) {
                    return false;
                }
                arena.getYaml().set("difficulty", args[0]);
                m.send(sender, "admin.value-set", "key", "difficulty", "value", args[0]);
            }
            case "setdisplayname" -> {
                if (args.length < 1) {
                    return false;
                }
                arena.getYaml().set("display-name", String.join(" ", args));
                m.send(sender, "admin.value-set", "key", "display-name", "value", String.join(" ", args));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> validateArena(Arena arena) {
        List<String> problems = new ArrayList<>();
        if (arena.getWorldName().isEmpty() || arena.getWorld() == null) {
            problems.add("World '" + arena.getWorldName() + "' is not loaded");
        }
        if (arena.getLobby() == null) {
            problems.add("Start location missing: stand at the start and run /minigame arena setlobby parkour " + arena.getName());
        }
        if (arena.getYaml().getString("data.finish") == null) {
            problems.add("Finish missing: /minigame arena setfinish parkour " + arena.getName());
        }
        return problems;
    }
}
