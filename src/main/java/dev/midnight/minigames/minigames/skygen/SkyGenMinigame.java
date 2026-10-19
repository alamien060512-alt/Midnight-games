package dev.midnight.minigames.minigames.skygen;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.island.AbstractIslandMinigame;
import dev.midnight.minigames.core.game.JoinResult;
import dev.midnight.minigames.core.island.Island;
import dev.midnight.minigames.core.placeholder.PlaceholderRegistry;
import dev.midnight.minigames.util.ConfigUtil;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.Keys;
import dev.midnight.minigames.util.TextUtil;
import dev.midnight.minigames.util.WeightedTable;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import dev.midnight.minigames.core.player.PlayerProfile;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import java.util.Collection;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * SkyGen: buy generator blocks, place them on your island and mine the blocks they produce for coins.
 * Progression: generator tiers (better block tables), island upgrades (speed, multiplier) and prestige (ranks).
 * Everything is configured in games/skygen.yml. A single 1 Hz task runs only while players are inside.
 */
public final class SkyGenMinigame extends AbstractIslandMinigame {

    /** Runtime state of a placed generator. */
    static final class Gen {
        final int x;
        final int y;
        final int z;
        final int tier;
        double progress;

        Gen(int x, int y, int z, int tier) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.tier = tier;
        }
    }

    static final class Tier {
        int id;
        String name;
        Material material;
        double price;
        double interval;
        WeightedTable<Material> table = new WeightedTable<>();
        ConfigurationSection section;
    }

    private final Map<Integer, Tier> tiers = new HashMap<>();
    private final Map<UUID, List<Gen>> gens = new HashMap<>();
    private final Random random = new Random();
    private final Map<UUID, Long> prestigeConfirm = new HashMap<>();
    private BukkitTask task;
    /** Generators of the shared mode, loaded lazily from the primary arena (data.sharedgens). */
    private List<Gen> sharedGens;

    public SkyGenMinigame(MidnightMinigames plugin) {
        super(plugin, "skygen", "<light_purple>SkyGen");
    }

    @Override
    protected void onIslandsLoaded() {
        tiers.clear();
        gens.clear();
        sharedGens = null;
        for (ConfigurationSection s : ConfigUtil.sections(plugin.getConfigurationManager().game("skygen"), "tiers")) {
            Tier t = new Tier();
            t.id = s.getInt("id", tiers.size() + 1);
            t.name = s.getString("name", "Generator " + t.id);
            t.material = ItemFactory.material(s.getString("material", "COBBLESTONE"), Material.COBBLESTONE);
            t.price = s.getDouble("price", 0);
            t.interval = Math.max(1.0, s.getDouble("interval", 10));
            ConfigurationSection blocks = s.getConfigurationSection("blocks");
            if (blocks != null) {
                for (String k : blocks.getKeys(false)) {
                    t.table.add(ItemFactory.material(k, Material.COBBLESTONE), blocks.getDouble(k, 1));
                }
            }
            if (t.table.isEmpty()) {
                plugin.getLogger().warning("[config] games/skygen.yml -> tiers: tier " + t.id + " has no blocks; using COBBLESTONE");
                t.table.add(Material.COBBLESTONE, 1);
            }
            t.section = s;
            tiers.put(t.id, t);
        }
        if (tiers.isEmpty()) {
            plugin.getLogger().severe("[config] games/skygen.yml -> tiers: no generator tiers defined");
        }
    }

    @Override
    protected void onDisable() {
        stopTask();
        for (Map.Entry<UUID, List<Gen>> e : gens.entrySet()) {
            Island is = islands.getByOwner(e.getKey());
            if (is != null) {
                storeGens(is, e.getValue());
            }
        }
        super.onDisable();
    }

    Map<Integer, Tier> getTiers() {
        return tiers;
    }

    // ---- generator storage ----------------------------------------------------------------------------------

    private List<Gen> gensOf(Island is) {
        return gens.computeIfAbsent(is.getOwner(), k -> {
            List<Gen> list = new ArrayList<>();
            String raw = is.getData().get("gens");
            if (raw != null && !raw.isEmpty()) {
                for (String part : raw.split(";")) {
                    String[] p = part.split(",");
                    if (p.length == 4) {
                        try {
                            list.add(new Gen(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
                        } catch (NumberFormatException ignored) {
                            plugin.getLogger().warning("SkyGen: corrupt generator entry '" + part + "' ignored");
                        }
                    }
                }
            }
            return list;
        });
    }

    private void storeGens(Island is, List<Gen> list) {
        StringBuilder sb = new StringBuilder();
        for (Gen g : list) {
            sb.append(sb.length() == 0 ? "" : ";").append(g.x).append(',').append(g.y).append(',').append(g.z).append(',').append(g.tier);
        }
        is.getData().put("gens", sb.toString());
        islands.markDirty();
    }

    private Gen genAt(Island is, int x, int y, int z) {
        for (Gen g : gensOf(is)) {
            if (g.x == x && g.y == y && g.z == z) {
                return g;
            }
        }
        return null;
    }

    // ---- progression numbers ---------------------------------------------------------------------------------------

    private YamlConfiguration cfg() {
        return plugin.getConfigurationManager().game("skygen");
    }

    int speedLevel(Island is) {
        return is.getUpgrade("speed");
    }

    int multiplierLevel(Island is) {
        return is.getUpgrade("multiplier");
    }

    int prestige(Island is) {
        return is.getUpgrade("prestige");
    }

    double speedFactor(Island is) {
        return Math.max(0.1, 1.0 - speedLevel(is) * cfg().getDouble("upgrades.speed.per-level", 0.08));
    }

    double multiplier(Island is) {
        double base = 1.0 + multiplierLevel(is) * cfg().getDouble("upgrades.multiplier.per-level", 0.25);
        return base * (1.0 + prestige(is) * cfg().getDouble("prestige.multiplier-per-prestige", 0.5));
    }

    int maxGenerators(Island is) {
        return cfg().getInt("island.max-generators", 12) + prestige(is) * cfg().getInt("prestige.extra-generators", 2);
    }

    String rankName(Island is) {
        List<String> ranks = cfg().getStringList("ranks");
        if (ranks.isEmpty()) {
            return "Prestige " + prestige(is);
        }
        return ranks.get(Math.min(prestige(is), ranks.size() - 1));
    }

    double upgradeCost(String id, int level) {
        ConfigurationSection u = cfg().getConfigurationSection("upgrades." + id);
        return u == null ? Double.MAX_VALUE : u.getDouble("base-cost", 500) * Math.pow(u.getDouble("cost-growth", 1.6), level);
    }

    int upgradeMax(String id) {
        return cfg().getInt("upgrades." + id + ".max", 10);
    }

    double prestigeCost(Island is) {
        return cfg().getDouble("prestige.base-cost", 100000) * Math.pow(cfg().getDouble("prestige.cost-growth", 2.5), prestige(is));
    }

    // ---- shop actions (used by the menus) ----------------------------------------------------------------------------

    boolean buyGenerator(Player p, Tier t) {
        if (!plugin.getEconomy().withdraw(p, t.price)) {
            plugin.getMessages().send(p, "skygen.not-enough", "cost", TextUtil.formatNumber(t.price));
            plugin.getEffects().play(p, "shop-fail");
            return false;
        }
        for (ItemStack rest : p.getInventory().addItem(generatorItem(t)).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        plugin.getMessages().send(p, "skygen.bought-generator", "name", TextUtil.plain(TextUtil.parse(t.name)));
        plugin.getEffects().play(p, "shop-buy");
        return true;
    }

    boolean buyUpgrade(Player p, Island is, String id) {
        var m = plugin.getMessages();
        if (!is.getOwner().equals(p.getUniqueId())) {
            m.send(p, "island.owner-only");
            return false;
        }
        int level = is.getUpgrade(id);
        if (level >= upgradeMax(id)) {
            m.send(p, "island.upgrade-maxed");
            return false;
        }
        double cost = upgradeCost(id, level);
        if (!plugin.getEconomy().withdraw(p, cost)) {
            m.send(p, "skygen.not-enough", "cost", TextUtil.formatNumber(cost));
            plugin.getEffects().play(p, "shop-fail");
            return false;
        }
        is.getUpgrades().put(id, level + 1);
        islands.markDirty();
        m.send(p, "island.upgraded", "upgrade", id, "level", String.valueOf(level + 1));
        plugin.getEffects().play(p, "shop-buy");
        return true;
    }

    ItemStack generatorItem(Tier t) {
        ItemStack item = new ItemStack(t.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.item(t.name));
        meta.lore(TextUtil.items(plugin.getMessages().raw("skygen.generator-lore").isEmpty() ? List.of()
                : List.of(TextUtil.replace(plugin.getMessages().raw("skygen.generator-lore"), "interval", TextUtil.formatNumber(t.interval)))));
        item.setItemMeta(meta);
        return ItemFactory.tag(item, Keys.GENERATOR, String.valueOf(t.id));
    }


    // ---- shared mode ("mode: shared" in games/skygen.yml) -------------------------------------------------------------
    // One server-wide generator area built by an admin. Players cannot place blocks; they mine what the generators
    // produce, sell the drops (/skygen sell) and buy items (/skygen buy).

    boolean isShared() {
        return cfg().getString("mode", "shared").equalsIgnoreCase("shared");
    }

    private List<Gen> shared() {
        if (sharedGens == null) {
            List<Gen> list = new ArrayList<>();
            Arena a = primaryArena();
            if (a != null) {
                for (String raw : a.getYaml().getStringList("data.sharedgens")) {
                    String[] p = raw.split(",");
                    if (p.length == 4) {
                        try {
                            list.add(new Gen(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
                        } catch (NumberFormatException ignored) {
                            plugin.getLogger().warning("SkyGen: corrupt shared generator entry '" + raw + "' ignored");
                        }
                    }
                }
            }
            sharedGens = list;
        }
        return sharedGens;
    }

    private void saveShared(Arena arena) {
        List<String> out = new ArrayList<>();
        for (Gen g : shared()) {
            out.add(g.x + "," + g.y + "," + g.z + "," + g.tier);
        }
        arena.getYaml().set("data.sharedgens", out);
        plugin.getArenaManager().save(arena);
    }

    private Gen sharedAt(int x, int y, int z) {
        for (Gen g : shared()) {
            if (g.x == x && g.y == y && g.z == z) {
                return g;
            }
        }
        return null;
    }

    double sellValue(Material m) {
        return cfg().getDouble("shared.sell.values." + m.name(), 0);
    }

    private double sellMultiplier(Player p) {
        double base = cfg().getDouble("shared.sell.multiplier", 1.0);
        for (ConfigurationSection s : ConfigUtil.sections(cfg(), "shared.sell.permission-multipliers")) {
            String perm = s.getString("permission", "");
            if (!perm.isEmpty() && p.hasPermission(perm)) {
                base = Math.max(base, s.getDouble("multiplier", 1.0));
            }
        }
        return base;
    }

    private void sellAll(Player p) {
        double total = 0;
        int items = 0;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.hasItemMeta()) {
                continue;
            }
            double v = sellValue(it.getType());
            if (v > 0) {
                total += v * it.getAmount();
                items += it.getAmount();
                p.getInventory().setItem(i, null);
            }
        }
        if (items == 0) {
            plugin.getMessages().send(p, "skygen.nothing-to-sell");
            return;
        }
        total *= sellMultiplier(p);
        plugin.getEconomy().deposit(p, total);
        plugin.getPlayerManager().getProfile(p).increment("skygen", "sold", items);
        plugin.getMessages().send(p, "skygen.sold", "items", String.valueOf(items), "amount", TextUtil.formatNumber(total));
        plugin.getEffects().play(p, "shop-buy");
    }

    /** Buys one entry of shared.buy.items; used by the buy menu. */
    boolean buyItem(Player p, ConfigurationSection entry) {
        double price = entry.getDouble("price", 0);
        if (!plugin.getEconomy().withdraw(p, price)) {
            plugin.getMessages().send(p, "skygen.not-enough", "cost", TextUtil.formatNumber(price));
            plugin.getEffects().play(p, "shop-fail");
            return false;
        }
        ItemStack item = ItemFactory.build(entry);
        item.setAmount(Math.max(1, entry.getInt("amount", 1)));
        for (ItemStack rest : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        plugin.getMessages().send(p, "skygen.bought-item", "name", TextUtil.plain(TextUtil.parse(entry.getString("name", entry.getName()))),
                "price", TextUtil.formatNumber(price));
        plugin.getEffects().play(p, "shop-buy");
        return true;
    }

    private void sharedMine(Player player, BlockBreakEvent e) {
        Block b = e.getBlock();
        Gen below = sharedAt(b.getX(), b.getY() - 1, b.getZ());
        boolean admin = plugin.getPermissions().has(player, "admin-build");
        if (below == null) {
            if (!admin) {
                e.setCancelled(true);
                plugin.getMessages().actionBar(player, "skygen.shared-no-break");
            }
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
        boolean autosell = cfg().getBoolean("shared.sell.autosell", false);
        double mult = sellMultiplier(player);
        double earned = 0;
        Collection<ItemStack> drops = b.getDrops(player.getInventory().getItemInMainHand(), player);
        for (ItemStack drop : drops) {
            if (autosell && sellValue(drop.getType()) > 0) {
                earned += sellValue(drop.getType()) * drop.getAmount() * mult;
            } else {
                for (ItemStack rest : player.getInventory().addItem(drop).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
        }
        if (earned > 0) {
            plugin.getEconomy().deposit(player, earned);
            plugin.getMessages().actionBar(player, "skygen.earned", "amount", TextUtil.formatNumber(earned));
        }
        plugin.getPlayerManager().getProfile(player).increment("skygen", "mined", 1);
    }

    private void produceShared() {
        for (Gen g : shared()) {
            Tier t = tiers.get(g.tier);
            Arena a = primaryArena();
            World w = a == null ? null : a.getWorld();
            if (t == null || w == null || !w.isChunkLoaded(g.x >> 4, g.z >> 4)) {
                continue;
            }
            g.progress += 1.0;
            if (g.progress >= t.interval) {
                g.progress = 0;
                Block above = w.getBlockAt(g.x, g.y + 1, g.z);
                if (above.getType() == Material.AIR && w.getBlockAt(g.x, g.y, g.z).getType() == t.material) {
                    above.setType(t.table.pick(random), false);
                }
            }
        }
    }

    // ---- shared mode: entry, protection, commands -----------------------------------------------------------------

    @Override
    protected Location resolveEntry(Player player, String modeId) {
        if (!isShared()) {
            return super.resolveEntry(player, modeId);
        }
        Arena a = primaryArena();
        if (a == null || a.getLobby() == null) {
            plugin.getLogger().severe("skygen has no enabled arena with a spawn. /minigame arena create skygen main, setlobby, enable.");
            return null;
        }
        return a.getLobby();
    }

    @Override
    protected boolean canBuild(Player p, Location l) {
        return isShared() ? plugin.getPermissions().has(p, "admin-build") : super.canBuild(p, l);
    }

    @Override
    public void onFoodChange(Player player, FoodLevelChangeEvent e) {
        if (isShared()) {
            e.setCancelled(true);
        }
    }

    @Override
    public boolean handleGameCommand(Player player, String[] args) {
        if (!isShared()) {
            return super.handleGameCommand(player, args);
        }
        if (args.length == 0) {
            return false;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("sell", "buy", "shop", "stats", "spawn", "home", "leave").contains(sub)) {
            return false;
        }
        if (!inside.contains(player.getUniqueId()) && join(player, "play") != JoinResult.JOINED) {
            return true;
        }
        switch (sub) {
            case "sell" -> sellAll(player);
            case "buy", "shop" -> new SkyGenBuyMenu(plugin, this).open(player);
            case "spawn", "home" -> {
                Location l = resolveEntry(player, "");
                if (l != null) {
                    player.teleport(l);
                }
            }
            case "leave" -> leave(player);
            default -> plugin.getMessages().send(player, "skygen.shared-stats",
                    "balance", TextUtil.formatNumber(plugin.getEconomy().getBalance(player)),
                    "mined", String.valueOf(plugin.getPlayerManager().getProfile(player).getLong("skygen", "mined", 0)),
                    "multiplier", String.format("%.2f", sellMultiplier(player)));
        }
        return true;
    }

    @Override
    public List<String> getArenaSubcommands() {
        return List.of("addgen", "delgen", "cleargens", "listgens");
    }

    @Override
    public List<String> tabCompleteArena(String sub, Arena arena, String[] args) {
        List<String> out = new ArrayList<>();
        if (sub.equals("addgen") && args.length == 1) {
            for (Tier t : tiers.values()) {
                out.add(String.valueOf(t.id));
            }
        }
        return out;
    }

    @Override
    public boolean handleArenaCommand(CommandSender sender, String sub, Arena arena, String[] args) {
        var m = plugin.getMessages();
        switch (sub) {
            case "addgen" -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "general.players-only");
                    return true;
                }
                Block target = p.getTargetBlockExact(8);
                Tier t = null;
                if (args.length > 0) {
                    try {
                        t = tiers.get(Integer.parseInt(args[0]));
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (target == null || t == null) {
                    m.send(sender, "admin.usage", "usage", "/minigame arena addgen skygen <arena> <tier>  (look at the block that becomes the generator)");
                    return true;
                }
                if (arena.getWorldName().isEmpty()) {
                    arena.setWorldName(target.getWorld().getName());
                }
                Gen existing = sharedAt(target.getX(), target.getY(), target.getZ());
                if (existing != null) {
                    shared().remove(existing);
                }
                target.setType(t.material, false);
                shared().add(new Gen(target.getX(), target.getY(), target.getZ(), t.id));
                saveShared(arena);
                m.send(sender, "skygen.gen-added", "tier", String.valueOf(t.id), "count", String.valueOf(shared().size()));
                startTask();
            }
            case "delgen" -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "general.players-only");
                    return true;
                }
                Block target = p.getTargetBlockExact(8);
                Gen g = target == null ? null : sharedAt(target.getX(), target.getY(), target.getZ());
                if (g == null) {
                    m.send(sender, "skygen.gen-none-there");
                    return true;
                }
                shared().remove(g);
                saveShared(arena);
                m.send(sender, "skygen.gen-removed", "count", String.valueOf(shared().size()));
            }
            case "cleargens" -> {
                shared().clear();
                saveShared(arena);
                m.send(sender, "skygen.gens-cleared");
            }
            case "listgens" -> {
                m.send(sender, "skygen.gens-list", "count", String.valueOf(shared().size()));
                for (Gen g : shared()) {
                    sender.sendMessage("  tier " + g.tier + " at " + g.x + " " + g.y + " " + g.z);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---- block events -----------------------------------------------------------------------------------------------------

    @Override
    public void onBlockPlace(Player player, BlockPlaceEvent e) {
        if (isShared()) {
            if (!plugin.getPermissions().has(player, "admin-build")) {
                e.setCancelled(true);
                plugin.getMessages().actionBar(player, "skygen.shared-no-build");
            }
            return;
        }
        String tag = ItemFactory.readTag(e.getItemInHand(), Keys.GENERATOR);
        if (tag == null) {
            super.onBlockPlace(player, e);
            return;
        }
        Island is = islands.of(player.getUniqueId());
        if (!canBuild(player, e.getBlock().getLocation()) || is == null) {
            e.setCancelled(true);
            plugin.getMessages().actionBar(player, "island.cannot-build");
            return;
        }
        List<Gen> list = gensOf(is);
        if (list.size() >= maxGenerators(is)) {
            e.setCancelled(true);
            plugin.getMessages().send(player, "skygen.generator-limit", "limit", String.valueOf(maxGenerators(is)));
            return;
        }
        int tierId;
        try {
            tierId = Integer.parseInt(tag);
        } catch (NumberFormatException ex) {
            e.setCancelled(true);
            return;
        }
        if (!tiers.containsKey(tierId)) {
            e.setCancelled(true);
            plugin.getMessages().send(player, "skygen.unknown-tier");
            return;
        }
        Block b = e.getBlockPlaced();
        list.add(new Gen(b.getX(), b.getY(), b.getZ(), tierId));
        storeGens(is, list);
        plugin.getMessages().actionBar(player, "skygen.generator-placed", "count", String.valueOf(list.size()), "limit", String.valueOf(maxGenerators(is)));
        startTask();
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent e) {
        if (isShared()) {
            sharedMine(player, e);
            return;
        }
        Island is = islands.at(e.getBlock().getLocation());
        Block b = e.getBlock();
        if (is != null && is.isMember(player.getUniqueId()) && canBuild(player, b.getLocation())) {
            Gen own = genAt(is, b.getX(), b.getY(), b.getZ());
            if (own != null) { // picking a generator back up
                e.setDropItems(false);
                List<Gen> list = gensOf(is);
                list.remove(own);
                storeGens(is, list);
                Tier t = tiers.get(own.tier);
                if (t != null) {
                    for (ItemStack rest : player.getInventory().addItem(generatorItem(t)).values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), rest);
                    }
                }
                return;
            }
            Gen below = genAt(is, b.getX(), b.getY() - 1, b.getZ());
            if (below != null) { // mining a produced block
                e.setDropItems(false);
                e.setExpToDrop(0);
                double value = cfg().getDouble("values." + b.getType().name(), 0) * multiplier(is);
                if (value > 0) {
                    plugin.getEconomy().deposit(player, value);
                    plugin.getMessages().actionBar(player, "skygen.earned", "amount", TextUtil.formatNumber(value));
                }
                plugin.getPlayerManager().getProfile(player).increment("skygen", "mined", 1);
                return;
            }
        }
        super.onBlockBreak(player, e);
    }

    // ---- production task ------------------------------------------------------------------------------------------------------

    @Override
    protected void onEnter(Player player, String modeId) {
        if (isShared()) {
            player.setGameMode(GameMode.SURVIVAL);
            PlayerProfile profile = plugin.getPlayerManager().getProfile(player);
            if (profile.getLong("skygen", "starter", 0) == 0) {
                profile.setLong("skygen", "starter", 1);
                for (ConfigurationSection item : ConfigUtil.sections(cfg(), "shared.starter-items")) {
                    player.getInventory().addItem(ItemFactory.build(item));
                }
            }
        } else {
            super.onEnter(player, modeId);
        }
        startTask();
    }

    @Override
    protected void onExit(Player player) {
        super.onExit(player);
        if (inside.size() <= 1) {
            stopTask();
        }
    }

    private void startTask() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::produce, 20L, 20L);
        }
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void produce() {
        if (isShared()) {
            produceShared();
            return;
        }
        Set<UUID> done = new HashSet<>();
        for (UUID id : inside) {
            Island is = islands.of(id);
            if (is == null || !done.add(is.getOwner())) {
                continue;
            }
            World w = is.getWorld();
            if (w == null) {
                continue;
            }
            double factor = speedFactor(is);
            for (Gen g : gensOf(is)) {
                Tier t = tiers.get(g.tier);
                if (t == null || !w.isChunkLoaded(g.x >> 4, g.z >> 4)) {
                    continue;
                }
                g.progress += 1.0;
                if (g.progress >= t.interval * factor) {
                    g.progress = 0;
                    Block above = w.getBlockAt(g.x, g.y + 1, g.z);
                    if (above.getType() == Material.AIR && w.getBlockAt(g.x, g.y, g.z).getType() == t.material) {
                        above.setType(t.table.pick(random), false);
                    }
                }
            }
        }
    }

    // ---- commands ------------------------------------------------------------------------------------------------------------------

    @Override
    protected boolean handleExtraCommand(Player player, String sub, String[] args) {
        var m = plugin.getMessages();
        Island is = islands.of(player.getUniqueId());
        switch (sub) {
            case "shop", "generators" -> new SkyGenMenu(plugin, this, is, SkyGenMenu.Page.SHOP).open(player);
            case "upgrade", "upgrades" -> {
                if (is == null) {
                    m.send(player, "island.no-island", "command", "/skygen create");
                } else {
                    new SkyGenMenu(plugin, this, is, SkyGenMenu.Page.UPGRADES).open(player);
                }
            }
            case "prestige" -> {
                if (is == null || !is.getOwner().equals(player.getUniqueId())) {
                    m.send(player, "island.owner-only");
                    return true;
                }
                double cost = prestigeCost(is);
                int max = cfg().getInt("prestige.max", 50);
                if (prestige(is) >= max) {
                    m.send(player, "skygen.max-prestige");
                    return true;
                }
                long now = System.currentTimeMillis();
                Long until = prestigeConfirm.get(player.getUniqueId());
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm") || until == null || until < now) {
                    prestigeConfirm.put(player.getUniqueId(), now + 30_000L);
                    m.send(player, "skygen.prestige-confirm", "cost", TextUtil.formatNumber(cost));
                    return true;
                }
                prestigeConfirm.remove(player.getUniqueId());
                if (!plugin.getEconomy().withdraw(player, cost)) {
                    m.send(player, "skygen.not-enough", "cost", TextUtil.formatNumber(cost));
                    return true;
                }
                doPrestige(is);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    m.send(p, "skygen.prestiged-broadcast", "player", player.getName(), "rank", TextUtil.plain(TextUtil.parse(rankName(is))));
                }
                plugin.getEffects().play(player, "win");
                plugin.getTabService().update(player);
            }
            case "stats" -> {
                if (is == null) {
                    m.send(player, "island.no-island", "command", "/skygen create");
                } else {
                    m.send(player, "skygen.stats", "rank", TextUtil.plain(TextUtil.parse(rankName(is))),
                            "multiplier", String.format("%.2f", multiplier(is)), "speed", String.valueOf(speedLevel(is)),
                            "generators", gensOf(is).size() + "/" + maxGenerators(is),
                            "next", TextUtil.formatNumber(prestigeCost(is)));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean isExtraNeedingNoJoin(String sub) {
        return sub.equals("shop") || sub.equals("upgrades") || sub.equals("upgrade") || sub.equals("prestige") || sub.equals("stats");
    }

    /** Prestige: removes generators and upgrade levels, grants a permanent multiplier and rank. */
    private void doPrestige(Island is) {
        World w = is.getWorld();
        List<Gen> list = gensOf(is);
        if (w != null) {
            for (Gen g : list) {
                w.getBlockAt(g.x, g.y, g.z).setType(Material.AIR, false);
                w.getBlockAt(g.x, g.y + 1, g.z).setType(Material.AIR, false);
            }
        }
        list.clear();
        storeGens(is, list);
        is.getUpgrades().put("speed", 0);
        is.getUpgrades().put("multiplier", 0);
        is.getUpgrades().put("prestige", prestige(is) + 1);
        islands.markDirty();
    }

    @Override
    public String getTabPrefix(Player player) {
        Island is = islands.of(player.getUniqueId());
        return is == null || !cfg().getBoolean("tab-rank-prefix", true) ? null : "<gray>[" + rankName(is) + "<gray>] ";
    }

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = super.getScoreboardVariables(player);
        Island is = islands.of(player.getUniqueId());
        if (isShared()) {
            v.put("rank", "-");
            v.put("prestige", "0");
            v.put("multiplier", String.format("%.2f", sellMultiplier(player)));
            v.put("generators", String.valueOf(shared().size()));
            v.put("speed", "-");
        } else if (is != null) {
            v.put("rank", TextUtil.plain(TextUtil.parse(rankName(is))));
            v.put("prestige", String.valueOf(prestige(is)));
            v.put("multiplier", String.format("%.2f", multiplier(is)));
            v.put("generators", gensOf(is).size() + "/" + maxGenerators(is));
            v.put("speed", String.valueOf(speedLevel(is)));
        } else {
            v.put("rank", "-");
            v.put("prestige", "0");
            v.put("multiplier", "1.00");
            v.put("generators", "0/0");
            v.put("speed", "0");
        }
        return v;
    }

    @Override
    public void registerPlaceholders(PlaceholderRegistry registry) {
        // %midnight_skygen_rank% %midnight_skygen_prestige% %midnight_skygen_multiplier% %midnight_skygen_generators%
        registry.register(getId(), (player, key) -> {
            if (player == null) {
                return "";
            }
            return switch (key) {
                case "rank", "prestige", "multiplier", "generators", "speed" -> getScoreboardVariables(player).get(key);
                default -> null;
            };
        });
    }

    @Override
    public List<String> validateArena(Arena arena) {
        List<String> problems = new ArrayList<>();
        if (arena.getWorld() == null) {
            problems.add("Island world '" + arena.getWorldName() + "' is not loaded (/minigame world create <name>, /minigame arena setworld)");
        }
        if (arena.getLobby() == null) {
            problems.add("Hub spawn missing: /minigame arena setlobby skygen " + arena.getName());
        }
        return problems;
    }
}
