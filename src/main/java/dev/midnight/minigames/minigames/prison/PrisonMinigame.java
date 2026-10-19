package dev.midnight.minigames.minigames.prison;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractPersistentMinigame;
import dev.midnight.minigames.core.placeholder.PlaceholderRegistry;
import dev.midnight.minigames.core.player.PlayerProfile;
import dev.midnight.minigames.util.ConfigUtil;
import dev.midnight.minigames.util.Cuboid;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.LocationUtil;
import dev.midnight.minigames.util.RegionFiller;
import dev.midnight.minigames.util.TextUtil;
import dev.midnight.minigames.util.WeightedTable;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Prison: mine blocks in resettable mines, sell them, buy rank-ups. Setup: create an arena (usually "main"), set its
 * lobby as the prison spawn, select a mine region with /minigame pos1 &amp; pos2, then
 * {@code /minigame arena addmine prison main <mine> [requiredRank]}.
 * Ranks, sell values and mine block tables live in games/prison.yml. Economy: config.yml -&gt; economy.provider.
 */
public final class PrisonMinigame extends AbstractPersistentMinigame {

    private final Map<String, Mine> mines = new LinkedHashMap<>();
    private final Random random = new Random();
    private List<ConfigurationSection> ranks = new ArrayList<>();
    private BukkitTask resetTask;

    public PrisonMinigame(MidnightMinigames plugin) {
        super(plugin, "prison", "<gold>Prison");
    }

    @Override
    protected void onEnable() {
        reloadConfig();
    }

    @Override
    protected void onReload() {
        reloadConfig();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        stopResetTask();
        mines.clear();
    }

    private void reloadConfig() {
        YamlConfiguration cfg = plugin.getConfigurationManager().game("prison");
        ranks = ConfigUtil.sections(cfg, "ranks");
        if (ranks.isEmpty()) {
            plugin.getLogger().warning("[config] games/prison.yml -> ranks: no ranks defined");
        }
        mines.clear();
        for (Arena arena : plugin.getArenaManager().getArenas(getId())) {
            ConfigurationSection ms = arena.getYaml().getConfigurationSection("data.mines");
            if (ms == null) {
                continue;
            }
            for (String name : ms.getKeys(false)) {
                try {
                    Mine mine = buildMine(arena, name, ms.getConfigurationSection(name), cfg);
                    if (mine != null) {
                        mines.put(name.toLowerCase(Locale.ROOT), mine);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().severe("[config] Prison mine '" + name + "' in arena " + arena.getName() + " is invalid: " + ex.getMessage());
                }
            }
        }
    }

    private Mine buildMine(Arena arena, String name, ConfigurationSection s, YamlConfiguration cfg) {
        Cuboid region = Cuboid.deserialize(s.getString("region"));
        if (region == null) {
            throw new IllegalArgumentException("region is missing or malformed");
        }
        ConfigurationSection mc = cfg.getConfigurationSection("mines." + name);
        ConfigurationSection blocks = mc != null && mc.isConfigurationSection("blocks") ? mc.getConfigurationSection("blocks")
                : cfg.getConfigurationSection("default-blocks");
        WeightedTable<Material> table = new WeightedTable<>();
        if (blocks != null) {
            for (String key : blocks.getKeys(false)) {
                table.add(ItemFactory.material(key, Material.STONE), blocks.getDouble(key, 1));
            }
        }
        if (table.isEmpty()) {
            table.add(Material.STONE, 1);
        }
        int rank = rankIndex(s.getString("rank", ranks.isEmpty() ? "" : ranks.get(0).getString("id", "")));
        Location spawn = LocationUtil.deserialize(s.getString("spawn"));
        if (spawn == null && Bukkit.getWorld(region.world) != null) {
            spawn = region.center(Bukkit.getWorld(region.world)).add(0, 1, 0);
        }
        Material icon = ItemFactory.material(mc != null ? mc.getString("icon", "STONE_PICKAXE") : "STONE_PICKAXE", Material.STONE_PICKAXE);
        long resetSeconds = mc != null ? mc.getLong("reset-seconds", cfg.getLong("reset-seconds", 600)) : cfg.getLong("reset-seconds", 600);
        double percent = mc != null ? mc.getDouble("reset-percent", cfg.getDouble("reset-percent", 0.25)) : cfg.getDouble("reset-percent", 0.25);
        return new Mine(name, region, table, rank, spawn, icon, resetSeconds, percent);
    }

    // ---- ranks -----------------------------------------------------------------------------------------------

    int rankIndex(String id) {
        for (int i = 0; i < ranks.size(); i++) {
            if (ranks.get(i).getString("id", "").equalsIgnoreCase(id)) {
                return i;
            }
        }
        return 0;
    }

    int rankOf(Player p) {
        return (int) Math.min(plugin.getPlayerManager().getProfile(p).getLong("prison", "rank", 0), Math.max(0, ranks.size() - 1));
    }

    String rankName(int index) {
        if (ranks.isEmpty()) {
            return "-";
        }
        ConfigurationSection r = ranks.get(Math.min(index, ranks.size() - 1));
        return TextUtil.plain(TextUtil.parse(r.getString("name", r.getString("id", "?"))));
    }

    private long mined(Player p) {
        return plugin.getPlayerManager().getProfile(p).getLong("prison", "mined", 0);
    }

    private void rankUp(Player p) {
        var m = plugin.getMessages();
        int cur = rankOf(p);
        if (cur + 1 >= ranks.size()) {
            m.send(p, "prison.max-rank");
            return;
        }
        ConfigurationSection next = ranks.get(cur + 1);
        double cost = next.getDouble("cost", 0);
        long needBlocks = next.getLong("blocks", 0);
        if (mined(p) < needBlocks) {
            m.send(p, "prison.need-blocks", "blocks", String.valueOf(needBlocks - mined(p)));
            return;
        }
        if (!plugin.getEconomy().withdraw(p, cost)) {
            m.send(p, "prison.need-money", "cost", TextUtil.formatNumber(cost));
            return;
        }
        PlayerProfile profile = plugin.getPlayerManager().getProfile(p);
        profile.setLong("prison", "rank", cur + 1);
        m.send(p, "prison.ranked-up", "rank", rankName(cur + 1));
        plugin.getEffects().play(p, "win");
        plugin.getTabService().update(p);
        plugin.getScoreboardService().refresh(p);
    }

    // ---- joining ---------------------------------------------------------------------------------------------------

    @Override
    protected Location resolveEntry(Player player, String modeId) {
        Arena a = primaryArena();
        if (a == null) {
            plugin.getLogger().severe("Prison has no enabled arena. Create one: /minigame arena create prison main, setlobby, enable.");
            return null;
        }
        Location l = a.getLobby();
        if (l == null) {
            plugin.getLogger().severe("Prison arena " + a.getName() + " has no spawn (/minigame arena setlobby).");
        }
        return l;
    }

    @Override
    protected void onEnter(Player player, String modeId) {
        player.setGameMode(GameMode.SURVIVAL);
        PlayerProfile profile = plugin.getPlayerManager().getProfile(player);
        if (profile.getLong("prison", "starter", 0) == 0) {
            profile.setLong("prison", "starter", 1);
            for (ConfigurationSection item : ConfigUtil.sections(plugin.getConfigurationManager().game("prison"), "starter-items")) {
                player.getInventory().addItem(ItemFactory.build(item));
            }
        }
        startResetTask();
    }

    @Override
    protected void onExit(Player player) {
        if (inside.size() <= 1) {
            stopResetTask();
        }
    }

    // ---- mining ----------------------------------------------------------------------------------------------------

    private Mine mineAt(Location l) {
        for (Mine m : mines.values()) {
            if (m.region.contains(l)) {
                return m;
            }
        }
        return null;
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent e) {
        Mine mine = mineAt(e.getBlock().getLocation());
        if (mine == null) {
            if (!plugin.getPermissions().has(player, "admin-build")) {
                e.setCancelled(true);
            }
            return;
        }
        if (mine.resetting) {
            e.setCancelled(true);
            return;
        }
        if (rankOf(player) < mine.requiredRank) {
            e.setCancelled(true);
            plugin.getMessages().actionBar(player, "prison.mine-locked-action", "rank", rankName(mine.requiredRank));
            return;
        }
        YamlConfiguration cfg = plugin.getConfigurationManager().game("prison");
        e.setDropItems(false);
        e.setExpToDrop(0);
        Collection<ItemStack> drops = e.getBlock().getDrops(player.getInventory().getItemInMainHand(), player);
        boolean autosell = cfg.getBoolean("sell.autosell", false);
        double earned = 0;
        for (ItemStack drop : drops) {
            if (autosell && valueOf(drop.getType()) > 0) {
                earned += valueOf(drop.getType()) * drop.getAmount();
            } else {
                for (ItemStack rest : player.getInventory().addItem(drop).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
        }
        if (earned > 0) {
            double mult = multiplier(player);
            plugin.getEconomy().deposit(player, earned * mult);
            plugin.getMessages().actionBar(player, "prison.autosold", "amount", TextUtil.formatNumber(earned * mult));
        }
        plugin.getPlayerManager().getProfile(player).increment("prison", "mined", 1);
        mine.remaining = Math.max(0, mine.remaining - 1);
        if (mine.resetPercent > 0 && mine.remainingFraction() <= mine.resetPercent) {
            resetMine(mine);
        }
    }

    @Override
    public void onBlockPlace(Player player, BlockPlaceEvent e) {
        if (!plugin.getPermissions().has(player, "admin-build")) {
            e.setCancelled(true);
        }
    }

    @Override
    public void onFoodChange(Player player, FoodLevelChangeEvent e) {
        e.setCancelled(true);
    }

    private double valueOf(Material m) {
        return plugin.getConfigurationManager().game("prison").getDouble("sell.values." + m.name(), 0);
    }

    private double multiplier(Player p) {
        double perRank = plugin.getConfigurationManager().game("prison").getDouble("sell.multiplier-per-rank", 0.0);
        return 1.0 + perRank * rankOf(p);
    }

    private void sellAll(Player p) {
        double total = 0;
        int items = 0;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null) {
                continue;
            }
            double v = valueOf(it.getType());
            if (v > 0 && !it.hasItemMeta()) {
                total += v * it.getAmount();
                items += it.getAmount();
                p.getInventory().setItem(i, null);
            }
        }
        if (items == 0) {
            plugin.getMessages().send(p, "prison.nothing-to-sell");
            return;
        }
        total *= multiplier(p);
        plugin.getEconomy().deposit(p, total);
        plugin.getMessages().send(p, "prison.sold", "items", String.valueOf(items), "amount", TextUtil.formatNumber(total));
        plugin.getEffects().play(p, "shop-buy");
    }

    // ---- mine resetting -------------------------------------------------------------------------------------------------

    void resetMine(Mine mine) {
        if (mine.resetting) {
            return;
        }
        World w = Bukkit.getWorld(mine.region.world);
        if (w == null) {
            plugin.getLogger().severe("Cannot reset prison mine '" + mine.name + "': world '" + mine.region.world + "' is not loaded.");
            return;
        }
        mine.resetting = true;
        for (Player p : w.getPlayers()) {
            if (inside.contains(p.getUniqueId()) && mine.region.contains(p.getLocation())) {
                Location up = mine.spawn != null ? mine.spawn : mine.region.center(w);
                p.teleport(up);
            }
        }
        RegionFiller.fill(plugin, w, mine.region, () -> mine.table.pick(random),
                plugin.getConfigurationManager().config().getInt("worlds.restore-blocks-per-tick", 2000), () -> {
                    mine.remaining = mine.total;
                    mine.resetting = false;
                    mine.nextReset = mine.resetSeconds > 0 ? System.currentTimeMillis() + mine.resetSeconds * 1000L : 0;
                    for (Player p : getPlayers()) {
                        plugin.getMessages().actionBar(p, "prison.mine-reset", "mine", mine.name);
                    }
                });
    }

    private void startResetTask() {
        if (resetTask != null) {
            return;
        }
        resetTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Mine m : mines.values()) {
                if (!m.resetting && m.nextReset > 0 && now >= m.nextReset) {
                    resetMine(m);
                }
            }
        }, 100L, 100L);
    }

    private void stopResetTask() {
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
    }

    void teleportToMine(Player p, Mine m) {
        if (rankOf(p) < m.requiredRank) {
            plugin.getMessages().send(p, "prison.mine-locked-action", "rank", rankName(m.requiredRank));
            return;
        }
        World w = Bukkit.getWorld(m.region.world);
        Location l = m.spawn != null ? m.spawn : (w == null ? null : m.region.center(w));
        if (l == null) {
            plugin.getMessages().send(p, "admin.world-missing", "world", m.region.world);
            return;
        }
        p.teleport(l);
    }

    // ---- commands ---------------------------------------------------------------------------------------------------------

    @Override
    public boolean handleGameCommand(Player player, String[] args) {
        if (args.length == 0) {
            return false;
        }
        var m = plugin.getMessages();
        if (!inside.contains(player.getUniqueId())) {
            m.send(player, "general.not-in-game");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "rankup" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("max")) {
                    int guard = 0;
                    int before = rankOf(player);
                    while (guard++ < ranks.size() && rankAttempt(player)) {
                        // keep ranking up while affordable
                    }
                    if (rankOf(player) == before) {
                        rankUp(player);
                    }
                } else {
                    rankUp(player);
                }
            }
            case "sell" -> sellAll(player);
            case "mines" -> new PrisonMinesMenu(plugin, this, player, new ArrayList<>(mines.values())).open(player);
            case "rank" -> {
                int r = rankOf(player);
                String next = r + 1 < ranks.size() ? rankName(r + 1) : "-";
                double cost = r + 1 < ranks.size() ? ranks.get(r + 1).getDouble("cost", 0) : 0;
                m.send(player, "prison.rank-info", "rank", rankName(r), "next", next, "cost", TextUtil.formatNumber(cost),
                        "mined", String.valueOf(mined(player)), "balance", TextUtil.formatNumber(plugin.getEconomy().getBalance(player)));
            }
            case "ranks" -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < ranks.size(); i++) {
                    sb.append(sb.length() == 0 ? "" : ", ").append(rankName(i));
                }
                m.send(player, "prison.ranks", "ranks", sb.toString());
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean rankAttempt(Player p) {
        int cur = rankOf(p);
        if (cur + 1 >= ranks.size()) {
            return false;
        }
        ConfigurationSection next = ranks.get(cur + 1);
        if (mined(p) < next.getLong("blocks", 0) || !plugin.getEconomy().has(p, next.getDouble("cost", 0))) {
            return false;
        }
        rankUp(p);
        return rankOf(p) > cur;
    }

    // ---- display ------------------------------------------------------------------------------------------------------------

    @Override
    public String getTabPrefix(Player player) {
        return plugin.getConfigurationManager().game("prison").getBoolean("tab-rank-prefix", true)
                ? "<gray>[<white>" + rankName(rankOf(player)) + "<gray>] " : null;
    }

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = new HashMap<>();
        int r = rankOf(player);
        v.put("game", getDisplayPlain());
        v.put("rank", rankName(r));
        v.put("next_rank", r + 1 < ranks.size() ? rankName(r + 1) : "-");
        v.put("next_cost", r + 1 < ranks.size() ? TextUtil.formatNumber(ranks.get(r + 1).getDouble("cost", 0)) : "-");
        v.put("mined", String.valueOf(mined(player)));
        v.put("balance", TextUtil.formatNumber(plugin.getEconomy().getBalance(player)));
        v.put("players", String.valueOf(inside.size()));
        return v;
    }

    @Override
    public void registerPlaceholders(PlaceholderRegistry registry) {
        // %midnight_prison_rank% %midnight_prison_mined% %midnight_prison_balance% %midnight_prison_next_cost%
        registry.register(getId(), (player, key) -> {
            if (player == null) {
                return "";
            }
            return switch (key) {
                case "rank", "mined", "balance", "next_cost", "next_rank" -> getScoreboardVariables(player).get(key);
                default -> null;
            };
        });
    }

    // ---- admin setup --------------------------------------------------------------------------------------------------------------

    @Override
    public List<String> getArenaSubcommands() {
        return List.of("addmine", "delmine", "setminespawn", "resetmine", "listmines");
    }

    @Override
    public List<String> tabCompleteArena(String sub, Arena arena, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && (sub.equals("delmine") || sub.equals("setminespawn") || sub.equals("resetmine"))) {
            ConfigurationSection ms = arena.getYaml().getConfigurationSection("data.mines");
            if (ms != null) {
                out.addAll(ms.getKeys(false));
            }
        } else if (args.length == 2 && sub.equals("addmine")) {
            for (ConfigurationSection r : ranks) {
                out.add(r.getString("id", ""));
            }
        }
        return out;
    }

    @Override
    public boolean handleArenaCommand(CommandSender sender, String sub, Arena arena, String[] args) {
        var m = plugin.getMessages();
        switch (sub) {
            case "addmine" -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "general.players-only");
                    return true;
                }
                if (args.length < 1 || !args[0].matches("[A-Za-z0-9_\\-]{1,24}")) {
                    m.send(sender, "admin.usage", "usage", "/minigame arena addmine prison <arena> <mine> [requiredRank]");
                    return true;
                }
                Cuboid sel = plugin.getArenaManager().getSelection(p);
                if (sel == null) {
                    m.send(sender, "admin.no-selection");
                    return true;
                }
                String path = "data.mines." + args[0].toLowerCase(Locale.ROOT);
                arena.getYaml().set(path + ".region", sel.serialize());
                arena.getYaml().set(path + ".rank", args.length > 1 ? args[1] : (ranks.isEmpty() ? "A" : ranks.get(0).getString("id", "A")));
                arena.getYaml().set(path + ".spawn", LocationUtil.serialize(p.getLocation()));
                if (arena.getWorldName().isEmpty()) {
                    arena.setWorldName(sel.world);
                }
                reloadConfig();
                Mine mine = mines.get(args[0].toLowerCase(Locale.ROOT));
                m.send(sender, "prison.mine-added", "mine", args[0], "volume", String.valueOf(sel.volume()));
                if (mine != null) {
                    resetMine(mine);
                }
            }
            case "delmine" -> {
                if (args.length < 1 || arena.getYaml().get("data.mines." + args[0].toLowerCase(Locale.ROOT)) == null) {
                    m.send(sender, "prison.unknown-mine");
                    return true;
                }
                arena.getYaml().set("data.mines." + args[0].toLowerCase(Locale.ROOT), null);
                reloadConfig();
                m.send(sender, "prison.mine-removed", "mine", args[0]);
            }
            case "setminespawn" -> {
                if (!(sender instanceof Player p) || args.length < 1
                        || arena.getYaml().get("data.mines." + args[0].toLowerCase(Locale.ROOT)) == null) {
                    m.send(sender, "prison.unknown-mine");
                    return true;
                }
                arena.getYaml().set("data.mines." + args[0].toLowerCase(Locale.ROOT) + ".spawn", LocationUtil.serialize(p.getLocation()));
                reloadConfig();
                m.send(sender, "admin.location-set", "what", "mine spawn");
            }
            case "resetmine" -> {
                Mine mine = args.length < 1 ? null : mines.get(args[0].toLowerCase(Locale.ROOT));
                if (mine == null) {
                    m.send(sender, "prison.unknown-mine");
                    return true;
                }
                resetMine(mine);
                m.send(sender, "prison.mine-resetting", "mine", mine.name);
            }
            case "listmines" -> {
                for (Mine mine : mines.values()) {
                    m.send(sender, "prison.mine-line", "mine", mine.name, "rank", rankName(mine.requiredRank),
                            "volume", String.valueOf(mine.total), "percent", String.valueOf(Math.round(mine.remainingFraction() * 100)));
                }
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
        if (arena.getLobby() == null) {
            problems.add("Prison spawn missing: /minigame arena setlobby prison " + arena.getName());
        }
        return problems;
    }
}
