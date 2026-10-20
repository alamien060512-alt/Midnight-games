package dev.midnight.minigames;

import dev.midnight.minigames.commands.GameCommand;
import dev.midnight.minigames.commands.LeaveCommand;
import dev.midnight.minigames.commands.MenuCommand;
import dev.midnight.minigames.commands.MinigameCommand;
import dev.midnight.minigames.commands.PartyCommand;
import dev.midnight.minigames.commands.SpectateCommand;
import dev.midnight.minigames.core.arena.ArenaManager;
import dev.midnight.minigames.core.config.ConfigurationManager;
import dev.midnight.minigames.core.config.Messages;
import dev.midnight.minigames.core.config.PermissionService;
import dev.midnight.minigames.core.display.EffectService;
import dev.midnight.minigames.core.display.LobbyService;
import dev.midnight.minigames.core.display.ScoreboardService;
import dev.midnight.minigames.core.display.TabService;
import dev.midnight.minigames.core.economy.EconomyService;
import dev.midnight.minigames.core.game.GameManager;
import dev.midnight.minigames.core.game.MinigameManager;
import dev.midnight.minigames.core.gui.MenuService;
import dev.midnight.minigames.core.listener.CoreListener;
import dev.midnight.minigames.core.party.PartyManager;
import dev.midnight.minigames.core.placeholder.PlaceholderHook;
import dev.midnight.minigames.core.placeholder.PlaceholderRegistry;
import dev.midnight.minigames.core.placeholder.PlaceholderService;
import dev.midnight.minigames.core.player.PlayerManager;
import dev.midnight.minigames.core.queue.QueueManager;
import dev.midnight.minigames.core.storage.MysqlStorage;
import dev.midnight.minigames.core.storage.SqliteStorage;
import dev.midnight.minigames.core.storage.StorageProvider;
import dev.midnight.minigames.core.world.WorldService;
import dev.midnight.minigames.minigames.bedwars.BedWarsMinigame;
import dev.midnight.minigames.minigames.bridge.BridgeMinigame;
import dev.midnight.minigames.minigames.ctf.CtfMinigame;
import dev.midnight.minigames.minigames.dropper.DropperMinigame;
import dev.midnight.minigames.minigames.tntrun.TntRunMinigame;
import dev.midnight.minigames.minigames.duels.DuelsMinigame;
import dev.midnight.minigames.minigames.onevone.OneVOneMinigame;
import dev.midnight.minigames.minigames.parkour.ParkourMinigame;
import dev.midnight.minigames.minigames.skywars.SkyWarsMinigame;
import dev.midnight.minigames.minigames.spleef.SpleefMinigame;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/**
 * MidnightMinigames - modular all-in-one minigame framework.
 * The main class only wires services together; all logic lives in the managers and minigame modules.
 */
public final class MidnightMinigames extends JavaPlugin {

    private static MidnightMinigames instance;

    private ConfigurationManager configurationManager;
    private Messages messages;
    private PermissionService permissions;
    private StorageProvider storage;
    private PlayerManager playerManager;
    private ArenaManager arenaManager;
    private WorldService worldService;
    private GameManager gameManager;
    private MinigameManager minigameManager;
    private QueueManager queueManager;
    private PartyManager partyManager;
    private MenuService menus;
    private EffectService effects;
    private LobbyService lobbyService;
    private ScoreboardService scoreboardService;
    private TabService tabService;
    private EconomyService economy;
    private PlaceholderRegistry placeholderRegistry;
    private PlaceholderService placeholderService;
    private PlaceholderHook placeholderHook;

    public static MidnightMinigames getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Keys.init(this);
        ItemFactory.setLogger(getLogger());

        configurationManager = new ConfigurationManager(this);
        configurationManager.load();
        messages = new Messages(this);
        permissions = new PermissionService(this);

        if (!initStorage()) {
            getLogger().severe("Could not initialise the database - disabling MidnightMinigames.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        worldService = new WorldService(this);
        playerManager = new PlayerManager(this);
        arenaManager = new ArenaManager(this);
        effects = new EffectService(this);
        lobbyService = new LobbyService(this);
        tabService = new TabService(this);
        scoreboardService = new ScoreboardService(this);
        economy = new EconomyService(this);
        gameManager = new GameManager(this);
        minigameManager = new MinigameManager(this);
        queueManager = new QueueManager(this);
        partyManager = new PartyManager(this);
        menus = new MenuService(this);
        placeholderRegistry = new PlaceholderRegistry();
        placeholderService = new PlaceholderService(this);
        placeholderHook = new PlaceholderHook(this);

        effects.reload();
        economy.reload();
        arenaManager.loadAll();
        registerBuiltInMinigames();

        getServer().getPluginManager().registerEvents(new CoreListener(this), this);
        registerCommands();

        playerManager.start();
        gameManager.start();
        scoreboardService.start();
        placeholderHook.register();

        // Players already online (after /reload or late enable)
        for (Player p : Bukkit.getOnlinePlayers()) {
            playerManager.handleJoin(p);
            scoreboardService.refresh(p);
        }
        getLogger().info("MidnightMinigames enabled with " + minigameManager.getAll().size() + " minigame module(s).");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stop();
            gameManager.stopAll();
        }
        if (minigameManager != null) {
            minigameManager.disableAll();
        }
        if (scoreboardService != null) {
            scoreboardService.shutdown();
        }
        if (placeholderHook != null) {
            placeholderHook.unregister();
        }
        if (arenaManager != null) {
            arenaManager.saveAllBlocking();
        }
        if (playerManager != null) {
            playerManager.stop();
            playerManager.saveAllOnShutdown();
        }
        if (storage != null) {
            storage.close();
        }
        instance = null;
    }

    private boolean initStorage() {
        ConfigurationSection db = configurationManager.config().getConfigurationSection("database");
        String type = db == null ? "sqlite" : db.getString("type", "sqlite").toLowerCase();
        String prefix = db == null ? "midnight_" : db.getString("table-prefix", "midnight_");
        try {
            if (type.equals("mysql") || type.equals("mariadb")) {
                storage = new MysqlStorage(getLogger(), db.getString("host", "localhost"), db.getInt("port", 3306),
                        db.getString("database", "midnight"), db.getString("username", "root"),
                        db.getString("password", ""), db.getString("parameters", "useSSL=false&autoReconnect=true"), prefix);
            } else {
                if (!type.equals("sqlite")) {
                    getLogger().warning("[config] config.yml -> database.type: unknown type '" + type + "', falling back to sqlite");
                }
                storage = new SqliteStorage(getLogger(), new File(getDataFolder(), db == null ? "data/stats.db"
                        : db.getString("sqlite-file", "data/stats.db")), prefix);
            }
            storage.init();
            getLogger().info("Using " + storage.getType() + " storage.");
            return true;
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Database initialisation failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    private void registerBuiltInMinigames() {
        minigameManager.register(new BedWarsMinigame(this));
        minigameManager.register(new SkyWarsMinigame(this));
        minigameManager.register(new SpleefMinigame(this));
        minigameManager.register(new TntRunMinigame(this));
        minigameManager.register(new BridgeMinigame(this));
        minigameManager.register(new CtfMinigame(this));
        minigameManager.register(new DropperMinigame(this));
        minigameManager.register(new DuelsMinigame(this));
        minigameManager.register(new OneVOneMinigame(this));
        minigameManager.register(new ParkourMinigame(this));
    }

    private void registerCommands() {
        MinigameCommand admin = new MinigameCommand(this);
        bind("minigame", admin, admin);
        PartyCommand party = new PartyCommand(this);
        bind("party", party, party);
        bind("games", new MenuCommand(this), null);
        LeaveCommand leave = new LeaveCommand(this);
        bind("leave", leave, null);
        SpectateCommand spec = new SpectateCommand(this);
        bind("spectate", spec, spec);
        String[][] gameCommands = {{"bw", "bedwars"}, {"sw", "skywars"}, {"tntrun", "tntrun"},
                {"bridge", "bridge"}, {"ctf", "ctf"}, {"dropper", "dropper"}, {"spleef", "spleef"},
                {"duels", "duels"}, {"1v1", "1v1"}, {"parkour", "parkour"}};
        for (String[] c : gameCommands) {
            GameCommand gc = new GameCommand(this, c[1]);
            bind(c[0], gc, gc);
        }
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter completer) {
        org.bukkit.command.PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml");
            return;
        }
        cmd.setExecutor(executor);
        if (completer != null) {
            cmd.setTabCompleter(completer);
        }
    }

    /** Re-reads every configuration file and re-initialises dependent services. */
    public void reloadAll() {
        configurationManager.reload();
        effects.reload();
        economy.reload();
        arenaManager.loadAll();
        minigameManager.reloadAll();
        scoreboardService.start();
        for (Player p : Bukkit.getOnlinePlayers()) {
            scoreboardService.refresh(p);
            tabService.update(p);
        }
    }

    // ---- accessors ------------------------------------------------------------------------------------

    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public PermissionService getPermissions() {
        return permissions;
    }

    public StorageProvider getStorage() {
        return storage;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public WorldService getWorldService() {
        return worldService;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public MinigameManager getMinigameManager() {
        return minigameManager;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public MenuService getMenus() {
        return menus;
    }

    public EffectService getEffects() {
        return effects;
    }

    public LobbyService getLobbyService() {
        return lobbyService;
    }

    public ScoreboardService getScoreboardService() {
        return scoreboardService;
    }

    public TabService getTabService() {
        return tabService;
    }

    public EconomyService getEconomy() {
        return economy;
    }

    public PlaceholderRegistry getPlaceholderRegistry() {
        return placeholderRegistry;
    }

    public PlaceholderService getPlaceholderService() {
        return placeholderService;
    }

    public boolean isPlaceholderApiPresent() {
        return placeholderHook != null && placeholderHook.isAvailable();
    }

    public String parsePlaceholderApi(Player player, String text) {
        return placeholderHook == null ? text : placeholderHook.parse(player, text);
    }
}
