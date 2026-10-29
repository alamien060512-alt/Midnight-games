package dev.midnight.minigames.core.cosmetic;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.player.PlayerProfile;
import dev.midnight.minigames.util.ConfigUtil;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Coin-priced cosmetics (cosmetics.yml): walking trails, kill effects, win effects and hats. Ownership and the equipped
 * item per category live in the player profile (namespace "cosmetics"), so they are saved with the stats.
 */
public final class CosmeticService {

    public static final String TRAILS = "trails";
    public static final String KILL_EFFECTS = "kill-effects";
    public static final String WIN_EFFECTS = "win-effects";
    public static final String HATS = "hats";
    private static final String NS = "cosmetics";

    public static final class Category {
        public final String id;
        public final String name;
        public final Material icon;
        public final int slot;
        public final List<String> lore;
        public final List<Item> items = new ArrayList<>();

        Category(String id, String name, Material icon, int slot, List<String> lore) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.slot = slot;
            this.lore = lore;
        }
    }

    public static final class Item {
        public final Category category;
        public final String id;
        public final String name;
        public final Material icon;
        public final double price;
        public final List<String> lore;
        final ConfigurationSection cfg;

        Item(Category category, ConfigurationSection cfg) {
            this.category = category;
            this.cfg = cfg;
            this.id = cfg.getString("id", "").toLowerCase(Locale.ROOT);
            this.name = cfg.getString("name", id);
            this.icon = ItemFactory.material(cfg.getString("material", "NAME_TAG"), Material.NAME_TAG);
            this.price = Math.max(0, cfg.getDouble("price", 0));
            this.lore = cfg.getStringList("lore");
        }

        public String key() {
            return category.id + "." + id;
        }
    }

    public enum Result { OK, ALREADY_OWNED, NOT_ENOUGH, LOADING }

    private final MidnightMinigames plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<UUID, Item> trails = new HashMap<>();
    private final Map<UUID, Location> lastTrailLocation = new HashMap<>();
    private BukkitTask trailTask;

    public CosmeticService(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    // ---- loading ---------------------------------------------------------------------------------------------------

    public void reload() {
        stopTrailTask();
        categories.clear();
        YamlConfiguration y = plugin.getConfigurationManager().cosmetics();
        ConfigurationSection cats = y.getConfigurationSection("categories");
        if (cats != null) {
            for (String cid : cats.getKeys(false)) {
                ConfigurationSection s = cats.getConfigurationSection(cid);
                if (s == null) {
                    continue;
                }
                Category c = new Category(cid, s.getString("name", cid), ItemFactory.material(s.getString("icon", "CHEST"), Material.CHEST),
                        s.getInt("slot", 10 + categories.size() * 2), s.getStringList("lore"));
                for (ConfigurationSection is : ConfigUtil.sections(s, "items")) {
                    Item item = new Item(c, is);
                    if (item.id.isEmpty()) {
                        plugin.getLogger().warning("[config] cosmetics.yml -> " + cid + ": an item without id was ignored");
                        continue;
                    }
                    checkEffect(item);
                    c.items.add(item);
                }
                categories.put(cid, c);
            }
        }
        trails.clear();
        lastTrailLocation.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshTrail(p);
        }
        Category t = categories.get(TRAILS);
        if (t != null && !t.items.isEmpty()) {
            long interval = Math.max(1, y.getInt("trails.interval-ticks", 4));
            trailTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTrails, interval, interval);
        }
    }

    public void shutdown() {
        stopTrailTask();
    }

    private void stopTrailTask() {
        if (trailTask != null) {
            trailTask.cancel();
            trailTask = null;
        }
    }

    /** Warns about particle names / data types the effect code cannot use. */
    private void checkEffect(Item item) {
        String effect = item.cfg.getString("effect", item.category.id.equals(HATS) ? "" : "PARTICLES").toUpperCase(Locale.ROOT);
        if (!effect.equals("PARTICLES")) {
            return;
        }
        Particle p = parseParticle(item.cfg.getString("particle", ""));
        if (p == null) {
            plugin.getLogger().warning("[config] cosmetics.yml -> " + item.key() + ": unknown particle '" + item.cfg.getString("particle") + "'");
        } else if (!supportedData(p)) {
            plugin.getLogger().warning("[config] cosmetics.yml -> " + item.key() + ": particle " + p.name() + " needs data this plugin cannot supply");
        }
    }

    private static Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean supportedData(Particle p) {
        Class<?> t = p.getDataType();
        return t == Void.class || t == Particle.DustOptions.class || t == org.bukkit.block.data.BlockData.class
                || t == ItemStack.class || t == Color.class || t == Particle.DustTransition.class;
    }

    public List<Category> getCategories() {
        return new ArrayList<>(categories.values());
    }

    public Category getCategory(String id) {
        return categories.get(id);
    }

    public Item find(String key) {
        int dot = key == null ? -1 : key.indexOf('.');
        if (dot < 0) {
            return null;
        }
        Category c = categories.get(key.substring(0, dot));
        if (c == null) {
            return null;
        }
        String id = key.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (Item i : c.items) {
            if (i.id.equals(id)) {
                return i;
            }
        }
        return null;
    }

    // ---- ownership ---------------------------------------------------------------------------------------------------

    private PlayerProfile profile(Player p) {
        return plugin.getPlayerManager().getProfile(p);
    }

    private Set<String> ownedSet(PlayerProfile pr) {
        Set<String> out = new LinkedHashSet<>();
        for (String part : pr.getString(NS, "owned", "").split(";")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }

    public boolean owns(Player p, Item item) {
        return item.price <= 0 || ownedSet(profile(p)).contains(item.key());
    }

    public int ownedCount(Player p, Category c) {
        int n = 0;
        for (Item i : c.items) {
            if (owns(p, i)) {
                n++;
            }
        }
        return n;
    }

    public void grant(Player p, Item item) {
        PlayerProfile pr = profile(p);
        Set<String> set = ownedSet(pr);
        if (set.add(item.key())) {
            pr.setString(NS, "owned", String.join(";", set));
        }
    }

    public void revoke(Player p, Item item) {
        PlayerProfile pr = profile(p);
        Set<String> set = ownedSet(pr);
        if (set.remove(item.key())) {
            pr.setString(NS, "owned", String.join(";", set));
        }
        Item active = getActive(p, item.category.id);
        if (active == item) {
            unequip(p, item.category.id);
        }
    }

    @Nullable
    public Item getActive(Player p, String categoryId) {
        Category c = categories.get(categoryId);
        if (c == null) {
            return null;
        }
        String id = profile(p).getString(NS, "active." + categoryId, "");
        for (Item i : c.items) {
            if (i.id.equals(id) && owns(p, i)) {
                return i;
            }
        }
        return null;
    }

    public Result purchase(Player p, Item item) {
        PlayerProfile pr = profile(p);
        if (!pr.isLoaded()) {
            return Result.LOADING;
        }
        if (owns(p, item)) {
            return Result.ALREADY_OWNED;
        }
        if (!plugin.getEconomy().withdraw(p, item.price)) {
            return Result.NOT_ENOUGH;
        }
        grant(p, item);
        return Result.OK;
    }

    public boolean equip(Player p, Item item) {
        if (!owns(p, item)) {
            return false;
        }
        profile(p).setString(NS, "active." + item.category.id, item.id);
        afterChange(p, item.category.id);
        return true;
    }

    public void unequip(Player p, String categoryId) {
        profile(p).setString(NS, "active." + categoryId, "");
        afterChange(p, categoryId);
    }

    private void afterChange(Player p, String categoryId) {
        if (categoryId.equals(TRAILS)) {
            refreshTrail(p);
        } else if (categoryId.equals(HATS)) {
            applyHat(p);
        }
    }

    /** Called once the player's profile finished loading (join). */
    public void onLoaded(Player p) {
        refreshTrail(p);
        applyHat(p);
    }

    public void onQuit(Player p) {
        trails.remove(p.getUniqueId());
        lastTrailLocation.remove(p.getUniqueId());
    }

    // ---- trails --------------------------------------------------------------------------------------------------------

    private void refreshTrail(Player p) {
        Item it = getActive(p, TRAILS);
        if (it == null) {
            trails.remove(p.getUniqueId());
        } else {
            trails.put(p.getUniqueId(), it);
        }
    }

    private void tickTrails() {
        if (trails.isEmpty()) {
            return;
        }
        boolean inGame = plugin.getConfigurationManager().cosmetics().getBoolean("trails.in-game", false);
        for (Map.Entry<UUID, Item> e : new ArrayList<>(trails.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline() || p.getGameMode() == GameMode.SPECTATOR || p.isDead()) {
                continue;
            }
            if (!inGame && plugin.getPlayerManager().isInGame(p)) {
                continue;
            }
            Location now = p.getLocation();
            Location last = lastTrailLocation.put(p.getUniqueId(), now);
            if (last == null || last.getWorld() != now.getWorld() || last.distanceSquared(now) < 0.01) {
                continue; // only while moving
            }
            particles(e.getValue().cfg, now);
        }
    }

    // ---- kill and win effects ------------------------------------------------------------------------------------------

    public void playKillEffect(Player killer, Location at) {
        Item it = getActive(killer, KILL_EFFECTS);
        if (it != null && at.getWorld() != null) {
            burst(it.cfg, at);
        }
    }

    public void playWinEffect(Player winner) {
        Item it = getActive(winner, WIN_EFFECTS);
        if (it == null) {
            return;
        }
        final int repeat = Math.max(1, it.cfg.getInt("repeat", 1));
        final int interval = Math.max(1, it.cfg.getInt("interval-ticks", 8));
        final ConfigurationSection c = it.cfg;
        new BukkitRunnable() {
            int n = 0;

            @Override
            public void run() {
                if (!winner.isOnline() || n++ >= repeat) {
                    cancel();
                    return;
                }
                burst(c, winner.getLocation());
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    private void burst(ConfigurationSection c, Location at) {
        World w = at.getWorld();
        if (w == null) {
            return;
        }
        String effect = c.getString("effect", "PARTICLES").toUpperCase(Locale.ROOT);
        switch (effect) {
            case "LIGHTNING" -> w.strikeLightningEffect(at);
            case "FIREWORK" -> plugin.getEffects().firework(at);
            default -> particles(c, at);
        }
        String sound = c.getString("sound", "");
        if (!sound.isEmpty()) {
            w.playSound(at, sound, 1.0f, (float) c.getDouble("pitch", 1.0));
        }
    }

    private void particles(ConfigurationSection c, Location at) {
        World w = at.getWorld();
        Particle particle = parseParticle(c.getString("particle", "CLOUD"));
        if (w == null || particle == null || !supportedData(particle)) {
            return;
        }
        int count = Math.max(1, c.getInt("count", 10));
        double spread = c.getDouble("spread", 0.3);
        double speed = c.getDouble("speed", 0.02);
        Location loc = at.clone().add(0, c.getDouble("height", 0.1), 0);
        try {
            w.spawnParticle(particle, loc, count, spread, spread, spread, speed, particleData(particle, c));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.FINE, "Particle " + particle + " rejected: " + ex.getMessage());
        }
    }

    private Object particleData(Particle particle, ConfigurationSection c) {
        Class<?> t = particle.getDataType();
        if (t == Particle.DustOptions.class) {
            return new Particle.DustOptions(color(c.getString("color", "#ff3030")), (float) c.getDouble("size", 1.0));
        }
        if (t == Particle.DustTransition.class) {
            return new Particle.DustTransition(color(c.getString("color", "#ff3030")), color(c.getString("color-to", "#3030ff")),
                    (float) c.getDouble("size", 1.0));
        }
        if (t == Color.class) {
            return color(c.getString("color", "#ffffff"));
        }
        if (t == org.bukkit.block.data.BlockData.class) {
            return ItemFactory.material(c.getString("block", "REDSTONE_BLOCK"), Material.REDSTONE_BLOCK).createBlockData();
        }
        if (t == ItemStack.class) {
            return new ItemStack(ItemFactory.material(c.getString("block", "SNOWBALL"), Material.SNOWBALL));
        }
        return null;
    }

    private static Color color(String raw) {
        Color c = ItemFactory.parseColor(raw);
        return c == null ? Color.WHITE : c;
    }

    // ---- hats --------------------------------------------------------------------------------------------------------------

    /** Puts the equipped hat on the player's head (lobby only; games manage armour themselves). */
    public void applyHat(Player p) {
        if (!p.isOnline() || plugin.getPlayerManager().isInGame(p)) {
            return;
        }
        Item it = getActive(p, HATS);
        ItemStack current = p.getInventory().getHelmet();
        boolean ours = current != null && "hat".equals(ItemFactory.readTag(current, Keys.ACTION));
        if (it == null) {
            if (ours) {
                p.getInventory().setHelmet(null);
            }
            return;
        }
        if (current != null && !current.getType().isAir() && !ours) {
            return; // never replace a real helmet
        }
        ItemStack hat = ItemFactory.simple(it.icon, it.name, List.of());
        p.getInventory().setHelmet(ItemFactory.tag(hat, Keys.ACTION, "hat"));
    }
}
