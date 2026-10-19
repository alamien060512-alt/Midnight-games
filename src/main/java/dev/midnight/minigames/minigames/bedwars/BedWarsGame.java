package dev.midnight.minigames.minigames.bedwars;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.api.events.PlayerEliminateEvent;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.GameState;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.core.team.GameTeam;
import dev.midnight.minigames.core.team.TeamBalancer;
import dev.midnight.minigames.core.team.TeamColor;
import dev.midnight.minigames.util.ConfigUtil;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.Keys;
import dev.midnight.minigames.util.LocationUtil;
import dev.midnight.minigames.util.LootTable;
import dev.midnight.minigames.util.LuckyBlocks;
import dev.midnight.minigames.util.PlayerUtil;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** One BedWars match. */
final class BedWarsGame extends GameInstance {

    private final Random random = new Random();
    private final YamlConfiguration cfg;
    private final boolean lucky;

    private final Map<String, TeamState> states = new LinkedHashMap<>();
    private final List<Generator> generators = new ArrayList<>();
    private final Map<Long, GameTeam> bedBlocks = new HashMap<>();
    private final Map<UUID, String> shopEntities = new HashMap<>();
    private final Map<UUID, Integer> respawning = new HashMap<>();
    private final Map<UUID, Integer> invulnerableUntil = new HashMap<>();
    private final Map<UUID, Integer> armorTier = new HashMap<>();
    private final Set<Long> luckyBlocks = new HashSet<>();
    private final List<ConfigurationSection> events;
    private final List<String> armorMaterials;
    private LootTable luckyTable;
    private Material luckyMaterial;

    private int diamondTier = 1;
    private int emeraldTier = 1;
    private int nextEvent = 0;
    private int luckyTimer = 0;

    BedWarsGame(MidnightMinigames plugin, AbstractArenaMinigame minigame, Arena arena, ModeDefinition mode) {
        super(plugin, minigame, arena, mode);
        this.cfg = plugin.getConfigurationManager().game("bedwars");
        this.lucky = mode.isLucky();
        this.events = ConfigUtil.sections(cfg, "events");
        this.events.sort((a, b) -> Integer.compare(a.getInt("time"), b.getInt("time")));
        List<String> tiers = cfg.getStringList("armor-tiers");
        this.armorMaterials = tiers.isEmpty() ? List.of("LEATHER", "CHAINMAIL", "IRON", "DIAMOND") : tiers;
    }

    // ------------------------------------------------------------------------------------------------
    // Start
    // ------------------------------------------------------------------------------------------------

    @Override
    protected void onGameStart() {
        luckyTable = LuckyBlocks.table(cfg);
        luckyMaterial = LuckyBlocks.material(cfg);

        // Teams that exist on the map
        Map<String, GameTeam> allTeams = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : arena.getRawTeamSpawns().entrySet()) {
            TeamColor color = TeamColor.fromId(e.getKey());
            Location spawn = LocationUtil.deserialize(e.getValue());
            if (color == null || spawn == null) {
                plugin.getLogger().warning("BedWars arena " + arena.getName() + ": ignoring team '" + e.getKey() + "' (bad colour or spawn)");
                continue;
            }
            allTeams.put(e.getKey(), new GameTeam(e.getKey(), color, spawn));
        }
        if (allTeams.size() < 2) {
            throw new IllegalStateException("arena needs at least two valid teams");
        }
        List<UUID> ids = new ArrayList<>();
        for (Player p : getOnlinePlayers()) {
            ids.add(p.getUniqueId());
        }
        TeamBalancer.assign(ids, new ArrayList<>(allTeams.values()), mode.getTeamSize(), plugin.getPartyManager()::getPartyId);
        for (GameTeam t : allTeams.values()) {
            if (!t.getMembers().isEmpty()) {
                teams.put(t.getId(), t);
                states.put(t.getId(), new TeamState());
                for (UUID id : t.getMembers()) {
                    teamByPlayer.put(id, t);
                }
            }
        }

        // Beds
        for (GameTeam t : teams.values()) {
            Location bed = LocationUtil.deserialize(arena.getYaml().getString("data.beds." + t.getId()));
            if (bed == null) {
                throw new IllegalStateException("team " + t.getId() + " has no valid bed location");
            }
            registerBed(bed.getBlock(), t);
        }

        // Generators
        for (String raw : arena.getYaml().getStringList("data.generators")) {
            int sep = raw.indexOf(';');
            if (sep < 0) {
                continue;
            }
            String type = raw.substring(0, sep);
            Location l = LocationUtil.deserialize(raw.substring(sep + 1));
            if (l == null) {
                plugin.getLogger().warning("BedWars arena " + arena.getName() + ": generator with unloaded world ignored: " + raw);
                continue;
            }
            createGenerator(type, l, allTeams);
        }

        // Shops
        spawnShops("item");
        spawnShops("upgrade");

        // Players
        for (Player p : getOnlinePlayers()) {
            GameTeam t = teamByPlayer.get(p.getUniqueId());
            if (t == null) {
                continue;
            }
            p.teleport(t.getSpawn());
            giveLoadout(p);
            plugin.getMessages().send(p, "game.team-assigned", "team", t.getColoredName());
            plugin.getTabService().update(p);
        }
        invulnerableUntil.clear();
    }

    private void registerBed(Block b, GameTeam team) {
        bedBlocks.put(LocationUtil.pack(b), team);
        Block other = otherHalf(b);
        if (other != null) {
            bedBlocks.put(LocationUtil.pack(other), team);
        }
    }

    @Nullable
    private Block otherHalf(Block b) {
        if (b.getBlockData() instanceof Bed bed) {
            return bed.getPart() == Bed.Part.FOOT ? b.getRelative(bed.getFacing()) : b.getRelative(bed.getFacing().getOppositeFace());
        }
        return null;
    }

    private void createGenerator(String type, Location l, Map<String, GameTeam> allTeams) {
        ConfigurationSection g = cfg.getConfigurationSection("generators." + type);
        if (g == null) {
            plugin.getLogger().warning("[config] games/bedwars.yml -> generators." + type + " is missing; generator ignored");
            return;
        }
        GameTeam owner = null;
        if (type.equals("iron") || type.equals("gold")) {
            double radius = cfg.getDouble("base-radius", 20.0);
            GameTeam nearest = null;
            double best = Double.MAX_VALUE;
            for (GameTeam t : allTeams.values()) {
                double d = t.getSpawn().distanceSquared(l);
                if (d < best) {
                    best = d;
                    nearest = t;
                }
            }
            if (nearest != null && best <= radius * radius) {
                if (nearest.getMembers().isEmpty()) {
                    return; // base of a team that is not playing: no generators
                }
                owner = nearest;
            }
        }
        List<Double> intervals = new ArrayList<>();
        for (Object o : g.getList("intervals", List.of(g.getDouble("interval", 5.0)))) {
            try {
                intervals.add(Double.parseDouble(String.valueOf(o)));
            } catch (NumberFormatException ignored) {
                intervals.add(5.0);
            }
        }
        if (intervals.isEmpty()) {
            intervals.add(5.0);
        }
        Material mat = ItemFactory.material(g.getString("material", "IRON_INGOT"), Material.IRON_INGOT);
        generators.add(new Generator(type, l, owner, mat, intervals, g.getInt("max-stack", 32), g.getInt("amount", 1)));
    }

    private void spawnShops(String type) {
        String nameKey = type.equals("item") ? "bedwars.shop-name.item" : "bedwars.shop-name.upgrade";
        for (String raw : arena.getYaml().getStringList("data.shops." + type)) {
            Location l = LocationUtil.deserialize(raw);
            if (l == null || l.getWorld() == null) {
                continue;
            }
            l.getChunk().load();
            Villager v = l.getWorld().spawn(l, Villager.class, vil -> {
                vil.setAI(false);
                vil.setInvulnerable(true);
                vil.setSilent(true);
                vil.setCollidable(false);
                vil.setPersistent(false);
                vil.setRemoveWhenFarAway(false);
                vil.customName(plugin.getMessages().get(nameKey));
                vil.setCustomNameVisible(true);
            });
            shopEntities.put(v.getUniqueId(), type);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Loadout & upgrades
    // ------------------------------------------------------------------------------------------------

    void giveLoadout(Player p) {
        GameTeam team = teamByPlayer.get(p.getUniqueId());
        if (team == null) {
            return;
        }
        p.getInventory().clear();
        equipArmor(p, team);
        for (ConfigurationSection item : ConfigUtil.sections(cfg, "start-items")) {
            ItemStack stack = ItemFactory.build(item, "color", team.getColor().getDye());
            p.getInventory().addItem(applyTeamEnchants(stack, team));
        }
        applyTeamEffects(p, team);
    }

    private void equipArmor(Player p, GameTeam team) {
        int tier = armorTier.getOrDefault(p.getUniqueId(), 0);
        p.getInventory().setHelmet(colored(Material.LEATHER_HELMET, team));
        p.getInventory().setChestplate(colored(Material.LEATHER_CHESTPLATE, team));
        String set = armorMaterials.get(Math.min(tier, armorMaterials.size() - 1)).toUpperCase(Locale.ROOT);
        if (set.equals("LEATHER")) {
            p.getInventory().setLeggings(colored(Material.LEATHER_LEGGINGS, team));
            p.getInventory().setBoots(colored(Material.LEATHER_BOOTS, team));
        } else {
            p.getInventory().setLeggings(applyTeamEnchants(unbreakable(new ItemStack(ItemFactory.material(set + "_LEGGINGS", Material.IRON_LEGGINGS))), team));
            p.getInventory().setBoots(applyTeamEnchants(unbreakable(new ItemStack(ItemFactory.material(set + "_BOOTS", Material.IRON_BOOTS))), team));
        }
        TeamState st = states.get(team.getId());
        if (st != null && st.protection > 0) {
            for (ItemStack a : p.getInventory().getArmorContents()) {
                if (a != null) {
                    applyTeamEnchants(a, team);
                }
            }
            p.getInventory().setArmorContents(p.getInventory().getArmorContents());
        }
    }

    private ItemStack colored(Material m, GameTeam team) {
        ItemStack item = unbreakable(new ItemStack(m));
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(team.getColor().getArmorColor());
        item.setItemMeta(meta);
        return applyTeamEnchants(item, team);
    }

    private ItemStack unbreakable(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Applies sharpness / protection of the team to an item (no-op for other items). */
    private ItemStack applyTeamEnchants(ItemStack item, GameTeam team) {
        TeamState st = states.get(team.getId());
        if (st == null || item == null) {
            return item;
        }
        if (st.sharpness > 0 && Tag.ITEMS_SWORDS.isTagged(item.getType())) {
            item.addUnsafeEnchantment(Enchantment.SHARPNESS, st.sharpness);
        }
        if (st.protection > 0 && isArmor(item.getType())) {
            item.addUnsafeEnchantment(Enchantment.PROTECTION, st.protection);
        }
        return item;
    }

    private boolean isArmor(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    private void applyTeamEffects(Player p, GameTeam team) {
        TeamState st = states.get(team.getId());
        p.removePotionEffect(PotionEffectType.HASTE);
        if (st != null && st.haste > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, st.haste - 1, true, false));
        }
    }

    private void refreshTeam(GameTeam team) {
        for (Player p : online(team.getMembers())) {
            if (!players.contains(p.getUniqueId()) || respawning.containsKey(p.getUniqueId())) {
                continue;
            }
            for (ItemStack it : p.getInventory().getContents()) {
                applyTeamEnchants(it, team);
            }
            equipArmor(p, team);
            applyTeamEffects(p, team);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Shopping
    // ------------------------------------------------------------------------------------------------

    String resourceName(String resource) {
        return plugin.getMessages().text("bedwars.resource." + resource.toLowerCase(Locale.ROOT));
    }

    private Material resourceMaterial(String resource) {
        ConfigurationSection g = cfg.getConfigurationSection("generators." + resource.toLowerCase(Locale.ROOT));
        return g == null ? null : ItemFactory.material(g.getString("material", "IRON_INGOT"), Material.IRON_INGOT);
    }

    @Nullable
    TeamState stateOf(Player p) {
        GameTeam t = teamByPlayer.get(p.getUniqueId());
        return t == null ? null : states.get(t.getId());
    }

    private int count(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == m) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private void take(Player p, Material m, int amount) {
        int left = amount;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it != null && it.getType() == m) {
                int remove = Math.min(left, it.getAmount());
                left -= remove;
                if (remove >= it.getAmount()) {
                    p.getInventory().setItem(i, null);
                } else {
                    it.setAmount(it.getAmount() - remove);
                }
            }
        }
    }

    /** Buys a shop entry. Returns true on success. */
    boolean purchase(Player p, ConfigurationSection entry) {
        GameTeam team = teamByPlayer.get(p.getUniqueId());
        if (team == null || state != GameState.IN_GAME || !players.contains(p.getUniqueId())) {
            return false;
        }
        ConfigurationSection costSec = entry.getConfigurationSection("cost");
        String resource = "iron";
        int amount = 0;
        if (costSec != null && !costSec.getKeys(false).isEmpty()) {
            resource = costSec.getKeys(false).iterator().next();
            amount = costSec.getInt(resource);
        }
        Material resMat = resourceMaterial(resource);
        if (resMat == null) {
            plugin.getLogger().warning("[config] games/bedwars.yml -> shop item '" + entry.getString("id", "?") + "': unknown resource '" + resource + "'");
            return false;
        }
        if (count(p, resMat) < amount) {
            plugin.getMessages().send(p, "bedwars.not-enough", "resource", resourceName(resource));
            plugin.getEffects().play(p, "shop-fail");
            return false;
        }
        String type = entry.getString("type", "item");
        if (type.equals("armor")) {
            int tier = entry.getInt("tier", 1);
            if (armorTier.getOrDefault(p.getUniqueId(), 0) >= tier) {
                plugin.getMessages().send(p, "bedwars.already-owned");
                plugin.getEffects().play(p, "shop-fail");
                return false;
            }
            take(p, resMat, amount);
            armorTier.put(p.getUniqueId(), tier);
            equipArmor(p, team);
        } else {
            ConfigurationSection plain = new MemoryConfiguration();
            for (String k : entry.getKeys(false)) {
                if (!List.of("name", "lore", "cost", "type", "slot", "id", "tier").contains(k)) {
                    plain.set(k, entry.get(k));
                }
            }
            ItemStack stack = ItemFactory.build(plain, "color", team.getColor().getDye());
            if (entry.getBoolean("lucky-block", false)) {
                ItemFactory.tag(stack, Keys.LUCKY, "1");
            }
            take(p, resMat, amount);
            applyTeamEnchants(stack, team);
            var left = p.getInventory().addItem(stack);
            for (ItemStack rest : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
        }
        plugin.getEffects().play(p, "shop-buy");
        plugin.getMessages().send(p, "bedwars.purchased", "item", TextUtil.plain(TextUtil.parse(entry.getString("name", entry.getString("id", "item")))));
        return true;
    }

    boolean buyUpgrade(Player p, ConfigurationSection def) {
        GameTeam team = teamByPlayer.get(p.getUniqueId());
        TeamState st = team == null ? null : states.get(team.getId());
        if (st == null || state != GameState.IN_GAME) {
            return false;
        }
        String id = def.getString("id", "");
        List<ConfigurationSection> tiers = ConfigUtil.sections(def, "tiers");
        int level = st.level(id);
        if (level >= tiers.size()) {
            plugin.getMessages().send(p, "bedwars.already-owned");
            return false;
        }
        String currency = def.getString("currency", "diamond");
        Material cm = resourceMaterial(currency);
        int cost = tiers.get(level).getInt("cost", 1);
        if (cm == null || count(p, cm) < cost) {
            plugin.getMessages().send(p, "bedwars.not-enough", "resource", resourceName(currency));
            plugin.getEffects().play(p, "shop-fail");
            return false;
        }
        take(p, cm, cost);
        st.setLevel(id, level + 1);
        plugin.getEffects().play(p, "shop-buy");
        broadcastTeam(team, "bedwars.upgrade-bought", "player", p.getName(),
                "upgrade", TextUtil.plain(TextUtil.parse(def.getString("name", id))), "level", String.valueOf(level + 1));
        refreshTeam(team);
        return true;
    }

    // ------------------------------------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------------------------------------

    @Override
    protected void onTick() {
        while (nextEvent < events.size() && events.get(nextEvent).getInt("time") <= elapsed) {
            runEvent(events.get(nextEvent++));
            if (state != GameState.IN_GAME) {
                return;
            }
        }
        List<Double> forge = cfg.getDoubleList("upgrades.forge-multipliers");
        for (Generator g : generators) {
            int tier = g.type.equals("diamond") ? diamondTier : g.type.equals("emerald") ? emeraldTier : 1;
            double mult = 1.0;
            if (g.team != null) {
                TeamState st = states.get(g.team.getId());
                if (st != null && st.forge > 0 && !forge.isEmpty()) {
                    mult = forge.get(Math.min(st.forge, forge.size() - 1));
                }
            }
            g.tick(tier, mult);
        }
        if (lucky) {
            int every = Math.max(5, cfg.getInt("lucky.interval", 30));
            if (++luckyTimer >= every) {
                luckyTimer = 0;
                for (Generator g : generators) {
                    if (g.type.equals("diamond") || g.type.equals("emerald")) {
                        ItemStack lb = ItemFactory.tag(new ItemStack(luckyMaterial), Keys.LUCKY, "1");
                        ItemMeta meta = lb.getItemMeta();
                        meta.displayName(plugin.getMessages().get("bedwars.lucky-block-name"));
                        lb.setItemMeta(meta);
                        g.location.getWorld().dropItem(g.location.clone().add(0.5, 0.6, 0.5), lb);
                    }
                }
            }
        }
        // respawn countdowns
        if (!respawning.isEmpty()) {
            for (UUID id : new ArrayList<>(respawning.keySet())) {
                Player p = Bukkit.getPlayer(id);
                int left = respawning.get(id) - 1;
                if (p == null) {
                    respawning.remove(id);
                } else if (left <= 0) {
                    respawning.remove(id);
                    respawn(p);
                } else {
                    respawning.put(id, left);
                    plugin.getMessages().title(p, "respawning", "seconds", String.valueOf(left));
                }
            }
        }
        // heal pools
        double radius = cfg.getDouble("upgrades.heal-pool-radius", 12.0);
        for (GameTeam t : teams.values()) {
            TeamState st = states.get(t.getId());
            if (st != null && st.healPool) {
                for (Player p : online(t.getMembers())) {
                    if (players.contains(p.getUniqueId()) && !respawning.containsKey(p.getUniqueId())
                            && p.getWorld() == t.getSpawn().getWorld() && p.getLocation().distanceSquared(t.getSpawn()) <= radius * radius) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false));
                    }
                }
            }
        }
    }

    private void runEvent(ConfigurationSection ev) {
        String action = ev.getString("action", "");
        int value = ev.getInt("value", 1);
        switch (action) {
            case "diamond-tier" -> diamondTier = Math.max(diamondTier, value);
            case "emerald-tier" -> emeraldTier = Math.max(emeraldTier, value);
            case "destroy-beds" -> {
                for (GameTeam t : new ArrayList<>(teams.values())) {
                    if (states.get(t.getId()).bedAlive) {
                        removeBed(t, null);
                    }
                }
            }
            case "end-game" -> {
                endGame(List.of());
                return;
            }
            default -> plugin.getLogger().warning("[config] games/bedwars.yml -> events: unknown action '" + action + "'");
        }
        String message = ev.getString("message");
        if (message != null && !message.isEmpty()) {
            for (Player p : getOnlineOccupants()) {
                p.sendMessage(TextUtil.parse(plugin.getMessages().text("prefix") + message));
            }
            sound("event");
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Beds
    // ------------------------------------------------------------------------------------------------

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent e) {
        super.onBlockBreak(player, e);
        if (e.isCancelled()) {
            return;
        }
        Block b = e.getBlock();
        GameTeam breakerTeam = teamByPlayer.get(player.getUniqueId());
        GameTeam owner = bedBlocks.get(LocationUtil.pack(b));
        if (owner != null) {
            if (owner == breakerTeam) {
                e.setCancelled(true);
                plugin.getMessages().send(player, "bedwars.own-bed");
                return;
            }
            Block other = otherHalf(b);
            blocks.record(b);
            if (other != null) {
                blocks.record(other);
            }
            e.setDropItems(false);
            destroyBed(owner, player);
            return;
        }
        if (Tag.BEDS.isTagged(b.getType()) || !blocks.isPlaced(b)) {
            e.setCancelled(true);
            plugin.getMessages().send(player, "bedwars.cannot-break");
            return;
        }
        long key = LocationUtil.pack(b);
        if (luckyBlocks.remove(key)) {
            e.setDropItems(false);
            LuckyBlocks.dropLoot(b.getLocation(), luckyTable, random);
        }
        blocks.unmarkPlaced(b);
    }

    private void destroyBed(GameTeam owner, @Nullable Player breaker) {
        TeamState st = states.get(owner.getId());
        if (st == null || !st.bedAlive) {
            return;
        }
        st.bedAlive = false;
        if (breaker != null) {
            plugin.getPlayerManager().getProfile(breaker).increment("stats", "bedwars.beds", 1);
        }
        broadcast("bedwars.bed-destroyed", "team", owner.getColoredName(),
                "player", breaker == null ? "-" : coloredName(breaker));
        plugin.getMessages().titleAll(online(owner.getMembers()), "bed-destroyed");
        sound("bed-destroyed");
        eliminateRespawning(owner, breaker);
        for (Player p : getOnlineOccupants()) {
            plugin.getScoreboardService().refresh(p);
        }
        checkWin();
    }

    /** Removes the bed blocks (used by the destroy-beds timeline event). */
    private void removeBed(GameTeam owner, @Nullable Player breaker) {
        for (Map.Entry<Long, GameTeam> e : new ArrayList<>(bedBlocks.entrySet())) {
            if (e.getValue() == owner) {
                Block b = world().getBlockAt(LocationUtil.unpackX(e.getKey()), LocationUtil.unpackY(e.getKey()), LocationUtil.unpackZ(e.getKey()));
                blocks.record(b);
                b.setType(Material.AIR, false);
            }
        }
        destroyBed(owner, breaker);
    }

    private void eliminateRespawning(GameTeam team, @Nullable Player killer) {
        for (UUID id : new ArrayList<>(team.getMembers())) {
            if (respawning.remove(id) != null) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && players.contains(id)) {
                    eliminate(p, killer, PlayerEliminateEvent.Reason.KILLED, true);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Death & respawn
    // ------------------------------------------------------------------------------------------------

    @Override
    protected void onPlayerDeath(Player victim, @Nullable Player killer, EntityDamageEvent.DamageCause cause) {
        GameTeam team = teamByPlayer.get(victim.getUniqueId());
        TeamState st = team == null ? null : states.get(team.getId());
        if (respawning.containsKey(victim.getUniqueId())) {
            return;
        }
        if (killer != null && killer != victim) {
            transferResources(victim, killer);
        }
        victim.getInventory().clear();
        boolean voidDeath = cause == EntityDamageEvent.DamageCause.VOID;
        if (st != null && st.bedAlive) {
            plugin.getPlayerManager().addDeath(victim, minigame.getId());
            if (killer != null && killer != victim) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                plugin.getPlayerManager().addKill(killer, minigame.getId());
                reward(killer, "kill");
                plugin.getEffects().play(killer, "kill");
            }
            String key = voidDeath ? (killer != null ? "game.death.void-killed" : "game.death.void")
                    : (killer != null ? "game.death.killed" : "game.death.generic");
            broadcast(key, "player", coloredName(victim), "killer", killer == null ? "" : coloredName(killer));
            startRespawn(victim);
        } else {
            eliminate(victim, killer, voidDeath ? PlayerEliminateEvent.Reason.VOID
                    : killer != null ? PlayerEliminateEvent.Reason.KILLED : PlayerEliminateEvent.Reason.OTHER, true);
        }
    }

    private void transferResources(Player from, Player to) {
        Map<Material, Integer> totals = new HashMap<>();
        for (String res : List.of("iron", "gold", "diamond", "emerald")) {
            Material m = resourceMaterial(res);
            if (m != null) {
                int n = count(from, m);
                if (n > 0) {
                    totals.put(m, n);
                    to.getInventory().addItem(new ItemStack(m, n)).values()
                            .forEach(rest -> to.getWorld().dropItemNaturally(to.getLocation(), rest));
                    plugin.getMessages().send(to, "bedwars.loot", "amount", String.valueOf(n), "resource", resourceName(res));
                }
            }
        }
    }

    private void startRespawn(Player p) {
        PlayerUtil.reset(p, GameMode.SPECTATOR);
        Location spec = arena.getSpectator();
        if (spec != null) {
            p.teleport(spec);
        }
        int delay = Math.max(1, cfg.getInt("respawn-delay", 5));
        respawning.put(p.getUniqueId(), delay);
        plugin.getMessages().title(p, "respawning", "seconds", String.valueOf(delay));
        plugin.getEffects().play(p, "eliminated");
    }

    private void respawn(Player p) {
        GameTeam team = teamByPlayer.get(p.getUniqueId());
        if (team == null || !players.contains(p.getUniqueId())) {
            return;
        }
        PlayerUtil.reset(p, GameMode.SURVIVAL);
        p.teleport(team.getSpawn());
        giveLoadout(p);
        invulnerableUntil.put(p.getUniqueId(), elapsed + Math.max(0, cfg.getInt("invulnerable-seconds", 3)));
        plugin.getMessages().send(p, "bedwars.respawned");
        plugin.getEffects().play(p, "respawn");
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent e) {
        super.onDamage(victim, e);
        if (e.isCancelled()) {
            return;
        }
        Integer until = invulnerableUntil.get(victim.getUniqueId());
        if (until != null && elapsed < until) {
            e.setCancelled(true);
        }
    }

    @Override
    protected void onPlayerRemoved(Player player) {
        respawning.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.HASTE);
    }

    @Override
    protected void onPlayerLeftDuringGame(Player player) {
        respawning.remove(player.getUniqueId());
        player.getInventory().clear();
    }

    @Override
    protected void onEliminated(Player player, @Nullable Player killer, PlayerEliminateEvent.Reason reason) {
        respawning.remove(player.getUniqueId());
    }

    @Override
    public boolean wantsMoveEvents() {
        return state == GameState.IN_GAME;
    }

    @Override
    public void onMove(Player player, PlayerMoveEvent e) {
        if (!players.contains(player.getUniqueId()) || respawning.containsKey(player.getUniqueId())) {
            return;
        }
        if (e.getTo().getY() < arena.getVoidY()) {
            handleDeath(player, null, EntityDamageEvent.DamageCause.VOID);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Building, TNT, fireballs, shops
    // ------------------------------------------------------------------------------------------------

    @Override
    public void onBlockPlace(Player player, BlockPlaceEvent e) {
        super.onBlockPlace(player, e);
        if (e.isCancelled()) {
            return;
        }
        Block placed = e.getBlockPlaced();
        double radius = cfg.getDouble("no-build-radius", 2.0);
        if (radius > 0) {
            Location pl = placed.getLocation().add(0.5, 0.5, 0.5);
            for (GameTeam t : teams.values()) {
                if (t.getSpawn().getWorld() == pl.getWorld() && t.getSpawn().distanceSquared(pl) <= radius * radius) {
                    e.setCancelled(true);
                    plugin.getMessages().send(player, "bedwars.cannot-build-here");
                    return;
                }
            }
            for (Generator g : generators) {
                if (g.location.getWorld() == pl.getWorld() && g.location.distanceSquared(pl) <= radius * radius) {
                    e.setCancelled(true);
                    plugin.getMessages().send(player, "bedwars.cannot-build-here");
                    return;
                }
            }
        }
        blocks.record(e.getBlockReplacedState().getBlock());
        blocks.markPlaced(placed);
        ItemStack inHand = e.getItemInHand();
        if (ItemFactory.readTag(inHand, Keys.LUCKY) != null) {
            luckyBlocks.add(LocationUtil.pack(placed));
        }
        if (placed.getType() == Material.TNT) {
            placed.setType(Material.AIR, false);
            blocks.unmarkPlaced(placed);
            Location at = placed.getLocation().add(0.5, 0.0, 0.5);
            TNTPrimed tnt = placed.getWorld().spawn(at, TNTPrimed.class);
            tnt.setFuseTicks(cfg.getInt("tnt-fuse-ticks", 40));
            tnt.setSource(player);
        }
    }

    @Override
    public void onExplode(EntityExplodeEvent e) {
        if (state != GameState.IN_GAME || !isNear(e.getLocation())) {
            return;
        }
        e.blockList().removeIf(b -> !blocks.isPlaced(b) || luckyBlocks.contains(LocationUtil.pack(b)));
        for (Block b : e.blockList()) {
            blocks.unmarkPlaced(b);
        }
    }

    @Override
    public void onInteract(Player player, PlayerInteractEvent e) {
        super.onInteract(player, e);
        if (e.isCancelled() || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && e.getItem() != null && e.getItem().getType() == Material.FIRE_CHARGE) {
            e.setCancelled(true);
            ItemStack hand = e.getItem();
            hand.setAmount(hand.getAmount() - 1);
            Fireball fb = player.launchProjectile(Fireball.class);
            fb.setYield((float) cfg.getDouble("fireball-yield", 2.0));
            fb.setIsIncendiary(false);
        }
    }

    @Override
    public void onInteractEntity(Player player, PlayerInteractEntityEvent e) {
        String type = shopEntities.get(e.getRightClicked().getUniqueId());
        if (type == null) {
            return;
        }
        e.setCancelled(true);
        if (state != GameState.IN_GAME || !players.contains(player.getUniqueId()) || respawning.containsKey(player.getUniqueId())) {
            return;
        }
        if (type.equals("item")) {
            new ShopMenu(plugin, this, player).open(player);
        } else {
            new UpgradeMenu(plugin, this, player).open(player);
        }
        plugin.getEffects().play(player, "menu-open");
    }

    @Override
    protected void onCleanup() {
        for (UUID id : shopEntities.keySet()) {
            Entity en = Bukkit.getEntity(id);
            if (en != null) {
                en.remove();
            }
        }
        shopEntities.clear();
        World w = world();
        if (w != null) {
            // Remove dropped resources left by generators so they never leak into the next game.
            for (Generator g : generators) {
                for (Entity en : w.getNearbyEntities(g.location, 3, 3, 3)) {
                    if (en instanceof org.bukkit.entity.Item) {
                        en.remove();
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Win handling & scoreboard
    // ------------------------------------------------------------------------------------------------

    @Override
    protected void onTeamEliminated(GameTeam team) {
        super.onTeamEliminated(team);
        TeamState st = states.get(team.getId());
        if (st != null) {
            st.bedAlive = false;
        }
    }

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = super.getScoreboardVariables(player);
        for (TeamColor c : TeamColor.values()) {
            v.put("team_" + c.name().toLowerCase(Locale.ROOT), "");
        }
        GameTeam own = teamByPlayer.get(player.getUniqueId());
        String line = cfg.getString("scoreboard.team-line", "{color}{letter} <white>{name}<gray>: {status}{you}");
        for (GameTeam t : teams.values()) {
            TeamState st = states.get(t.getId());
            String status;
            if (st != null && st.bedAlive) {
                status = cfg.getString("scoreboard.status-bed", "<green>✔");
            } else if (aliveCount(t) > 0) {
                status = TextUtil.replace(cfg.getString("scoreboard.status-count", "<yellow>{count}"), "count", String.valueOf(aliveCount(t)));
            } else {
                status = cfg.getString("scoreboard.status-dead", "<red>✘");
            }
            v.put("team_" + t.getId(), TextUtil.replace(line, "color", t.getColor().tag(), "letter", t.getName().substring(0, 1),
                    "name", t.getName(), "status", status, "you", t == own ? cfg.getString("scoreboard.you", " <gray>YOU") : ""));
        }
        TeamState mine = own == null ? null : states.get(own.getId());
        v.put("bed", mine == null ? "-" : mine.bedAlive ? plugin.getMessages().text("bedwars.bed-alive") : plugin.getMessages().text("bedwars.bed-destroyed-short"));
        if (nextEvent < events.size()) {
            ConfigurationSection ev = events.get(nextEvent);
            v.put("next_event", ev.getString("name", ev.getString("action", "-")));
            v.put("next_event_in", TextUtil.formatTime(ev.getInt("time") - elapsed));
        } else {
            v.put("next_event", "-");
            v.put("next_event_in", "-");
        }
        return v;
    }
}
