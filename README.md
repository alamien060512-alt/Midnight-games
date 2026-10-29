# MidnightMinigames

> Step-by-step arena setup for every game is in [ARENAS.md](ARENAS.md)

Modular all-in-one minigame framework for **Paper 26.3** (Java 25).
One plugin, ten built-in minigames, and a small core you can extend with your own games.

| Round based (arenas, lobby → countdown → game → reset) | Persistent (enter a world, keep progressing) |
|---|---|
| BedWars, SkyWars, Spleef, TNT Run, The Bridge (solo/duo), Capture the Flag (solo/duo), Dropper, Duels, 1v1 | Parkour |

---------------------------------------------------------------------------------------------------

## 1. Build

```bash
# JDK 25 required (Paper 26.x requires Java 25)
mvn package
# -> target/MidnightMinigames-1.0.0.jar   (drop into plugins/)
```

* `pom.xml` depends on `io.papermc.paper:paper-api:[26.3.build,)` (latest 26.3 build, exactly as the
  Paper docs recommend for Maven) and `me.clip:placeholderapi:2.11.6` (both `provided`).
* Soft dependencies: PlaceholderAPI, Vault. Neither is required.
* SQLite and MySQL JDBC drivers are bundled with Paper itself, nothing is shaded.

### What was verified (and what was not)

The sandbox this was written in could not reach `repo.papermc.io`, so the Paper 26.3 API was **built from
Paper's own source** (`PaperMC/Paper`, `mcVersion=26.3`, `apiVersion=26.3`) and used as the compile classpath.

* The whole project compiles against that API with no warnings that matter, and `mvn package` produces the jar
  (verified with `-Djava.release=21` because only JDK 21 was available; the shipped `pom.xml` targets Java 25).
* All 123 classes load and pass bytecode verification (`-Xverify:all`).
* All bundled YAML files parse; every material / sound key in them exists in the 26.3 API; all 267 messages render;
  config validation reports no problems on the default files.
* A simulated server (fake Bukkit objects + manual scheduler + a real SQLite file) drove the SkyWars lifecycle end to
  end: queue → join → countdown → start → elimination → win → stats/rewards → reset, plus parties, forced stop,
  late joiners being queued instead of joining a running game, and stats + leaderboard persisted in SQLite.
* The same simulation (with a block-aware fake world) drove TNT Run (block removal, restore, elimination), The Bridge
  (teams, freeze countdown, goals, respawns, win), Capture the Flag (pick up, capture, drop, return, timeout, win) and
  Dropper (collision reset, level progression, win) end to end.
* **Not** verified: running on a live Paper server (no server jar was obtainable), MySQL, Vault, PlaceholderAPI
  itself (a stub was used to compile), and anything that creates `ItemStack`s/inventories (shops, kits, loot, menus).
  Treat the first run on a test server as the real acceptance test and read the console on startup – every config
  problem is reported with file and path.

---------------------------------------------------------------------------------------------------

## 2. Project structure

```
MidnightMinigames/
├─ pom.xml
├─ README.md
└─ src/main
   ├─ java/dev/midnight/minigames
   │  ├─ MidnightMinigames.java            plugin entry point - only wires services together
   │  ├─ api/
   │  │  ├─ MidnightMinigamesAPI.java      static API for other plugins
   │  │  └─ events/                        MinigameJoin/Leave/Start/End, PlayerEliminate, ArenaReset events
   │  ├─ commands/                         /minigame, /party, /leave, /spectate, /bw /sw /spleef ... (GameCommand)
   │  ├─ core/
   │  │  ├─ game/       Minigame (interface), AbstractMinigame, AbstractArenaMinigame, AbstractPersistentMinigame,
   │  │  │              GameInstance (state machine), GameState, PlayerContext, MinigameManager, GameManager,
   │  │  │              ModeDefinition, JoinResult, PlayerSnapshot
   │  │  ├─ arena/      Arena (YAML backed), ArenaManager
   │  │  ├─ queue/      QueueManager        party aware FIFO queue, no polling
   │  │  ├─ party/      Party, PartyManager
   │  │  ├─ team/       GameTeam, TeamColor, TeamBalancer
   │  │  ├─ player/     PlayerManager, PlayerProfile, PlayerStats
   │  │  ├─ storage/    StorageProvider, SqlStorage, SqliteStorage, MysqlStorage (all async)
   │  │  ├─ config/     ConfigurationManager, ConfigFile, Messages, PermissionService
   │  │  ├─ world/      WorldService (load / void worlds / optional world-copy reset), BlockTracker (block restore)
   │  │  ├─ gui/        MenuHolder (InventoryHolder), MainMenu, ModeMenu, MenuService
   │  │  ├─ display/    ScoreboardService, TabService, EffectService, LobbyService
   │  │  ├─ placeholder/ PlaceholderService, PlaceholderRegistry, PlaceholderHook, MidnightExpansion
   │  │  ├─ economy/    EconomyService (internal coins or Vault via reflection)
   │  │  └─ listener/   CoreListener  - the ONLY Bukkit listener in the plugin
   │  ├─ minigames/
   │  │  ├─ bedwars/  skywars/  spleef/  tntrun/  dropper/  duels/  onevone/   round based games
   │  │  ├─ teamfight/  bridge/  ctf/      TeamFightGame base (red vs blue, kits, respawns) + The Bridge + Capture the Flag
   │  │  └─ parkour/                         persistent game
   │  └─ util/        ItemFactory, LootTable, WeightedTable, RegionFiller, Cuboid, LocationUtil, TextUtil ...
   └─ resources
      ├─ plugin.yml  config.yml  messages.yml  menus.yml  permissions.yml
      └─ games/  bedwars.yml skywars.yml spleef.yml tntrun.yml bridge.yml ctf.yml dropper.yml duels.yml parkour.yml
```

### Design in one paragraph

`Minigame` is the module interface (`start/stop/join/leave/createArena/deleteArena/handlePlayerDeath/handlePlayerQuit/
handlePlayerJoin/getState/getPlayers/getMaxPlayers`). Round based games extend `AbstractArenaMinigame`, which creates
one `GameInstance` per active arena; the instance owns the state machine
`WAITING → STARTING → IN_GAME → ENDING → RESETTING`, player snapshots/restore, spectators, teams, stats and rewards,
and the game only implements the rules (`onGameStart`, `onTick`, `onPlayerDeath`, `checkWin`, `onResetWorld`...).
Persistent games extend `AbstractPersistentMinigame`. Everything a player can be
"inside" implements `PlayerContext`; `CoreListener` looks the context up with one `HashMap` read and forwards the event,
so adding a game never adds a listener.

### Performance notes

* One repeating task drives all games (`GameManager`, 1 Hz); generators, countdowns, respawns, refills and timeline
  events are all counters inside that tick. Scoreboards use one task that only touches players with a board and only
  sends changed lines. Parkour's action-bar timer and TNT Run's block-removal task exist only while
  someone is playing them.
* `PlayerMoveEvent` work happens only for block changes of players whose context asked for it.
* All database access runs on a dedicated worker thread; profiles are loaded on join, saved on quit and every 5 minutes
  (dirty profiles only). Arena files are written asynchronously.
* Arena resets restore only the blocks that were changed (`BlockTracker`), 2000 blocks per tick by default.
  Worlds are never unloaded or deleted unless you explicitly enable world-copy resets (see below).

---------------------------------------------------------------------------------------------------

## 3. First-time setup

1. Drop the jar into `plugins/`, start the server once. `plugins/MidnightMinigames/` now contains
   `config.yml`, `messages.yml`, `menus.yml`, `permissions.yml`, `games/*.yml` and `arenas/`.
2. Stand in your lobby and run `/minigame setlobby`. Players get the compass on join (menus.yml → `lobby-items`).
3. Create arenas (sections 5 and 6), `/minigame arena enable <game> <arena>`.
4. Optional: PlaceholderAPI (`/papi reload` is not needed, the expansion registers itself), Vault
   (`economy.provider: vault`), MySQL (`database.type: mysql`).

**Arena worlds (no world plugin needed).** Put the map folder in the server directory with a **lowercase name
without spaces** (e.g. `bw_speedway` - Paper 26.x identifies worlds by `minecraft:<name>`), then run
`/minigame world load bw_speedway`. Paper migrates a classic top-level folder into `<level>/dimensions/minecraft/`
itself; a world already in that folder loads too. A failed load prints the exact reason in chat and console. Enabled
arenas load their world automatically when needed (`worlds.auto-load: true`). `/minigame world create <name>` makes an
empty void world. Multiverse is not required, but any world it loaded is usable as well.

**Everything in one world.** You can build every arena in a single world (even the main world) and just create several
arenas that point to it. Nothing is created automatically: arenas exist only when you create them with
`/minigame arena create`, and a game only starts on arenas you enabled. Resets are automatic: after every game the
default `blocks` mode restores every block the game changed, and no world is unloaded. For BedWars/SkyWars arenas
that sit close together, also mark each arena's area so explosions and cleanup stay inside it:
`/minigame pos1`, `/minigame pos2`, `/minigame arena setregion <game> <arena> bounds`.

**Reset strategies** (`/minigame arena setreset <game> <arena> <mode>`):

| mode | what happens |
|---|---|
| `blocks` (default) | every block placed/broken during the game is restored; chest contents, signs etc. are *not* |
| `world-copy` | after the game the world is unloaded and its folder replaced by `world-templates/<world>/`. **Deletes the live world folder.** Requires `worlds.world-copy-reset.enabled: true` AND the template folder. |
| `none` | nothing is reset |

---------------------------------------------------------------------------------------------------

## 4. Commands

### Player commands
| Command | Description |
|---|---|
| `/games` | open the main menu (same as the compass) |
| `/bw` `/sw` `/spleef` `/tntrun` `/bridge` `/ctf` `/dropper` `/duels` `/1v1` `/parkour` | open that game's mode menu (NPC friendly) |
| `/<game> join <mode>` | queue directly, no GUI (`/bw join doubles`, `/parkour join course1`, `/duels join sumo`) |
| `/<game> leave` , `/leave` (`/lobby`, `/hub`) | leave the game / queue / go to lobby |
| `/party create`, `invite <player>`, `accept`, `leave`, `disband`, `kick <player>`, `list` | parties (only the leader queues, the party joins together and stays on one team when the team size allows) |
| `/cosmetic` (`/cosmetics`, `/cos`) | cosmetic shop: trails, kill effects, win effects and hats, bought with coins |
| `/ranks` (`/rank`), `/ranks info` | rank shop: VIP / MVP / LEGEND bought with coins (upgrades pay only the difference) |
| `/minigamemenu` (alias of `/games`) | open the main menu. |
| `/spectate <player>` | spectate a running game that supports spectators |
| `/duels challenge <player> [kit]`, `/duels accept` | direct duel on a free arena |
| `/duels elo [kit]`, `/duels top [kit]` (also `/1v1`) | ranked rating and leaderboard |
| `/parkour reset`, `checkpoint`, `top [course]`, `best [course]`, `list` | parkour helpers |

### Admin commands (`/minigame`, aliases `/mg`, `/minigames`, `/midnight`)
```
/minigame reload
/minigame setlobby | lobby                              main lobby
/minigame pos1 | pos2                                   region selection (setregion, addlevel, scanchests)
/minigame world list|create <n>|load <n>|tp <n>
/minigame arena create <game> <name>
/minigame arena delete <game> <name>
/minigame arena list [game]  |  info <game> <arena>  |  validate <game> <arena>
/minigame arena setspawn <game> <arena> <team>          team spawn (red blue green yellow aqua white pink gray orange purple cyan lime black)
/minigame arena addspawn | clearspawns <game> <arena>   free spawn points (SkyWars solo, Spleef)
/minigame arena setlobby | setspectator <game> <arena>
/minigame arena setworld <game> <arena> [world]
/minigame arena setmode <game> <arena> <mode>
/minigame arena setplayers <game> <arena> <min> <max>
/minigame arena setcountdown | setgametime | setvoid | setreset <game> <arena> <value>
/minigame arena setregion <game> <arena> <key>          uses pos1/pos2
/minigame arena enable | disable <game> <arena>         enable runs validation and prints every problem
/minigame arena tp <game> <arena>
/minigame forcestart <game> <arena>
/minigame stop <game> <arena>                           (no stats are recorded for admin-stopped games)
/minigame stats [player]   /minigame addcoins <player> <amount>
/cosmetic give|take <player> <category.id>              admin: hand out / remove a cosmetic
/ranks set <player> <rank|none>                         admin: set a rank without paying
```
Game specific arena sub commands (tab-completed):
`bedwars: setbed, addgen, cleargens, addshop, clearshops` ·
`skywars: addchest, scanchests, clearchests` ·
`parkour: addcheckpoint, clearcheckpoints, setfinish, seticon, setdifficulty, setdisplayname` ·
`ctf: setflag` · `dropper: addlevel, dellevel, clearlevels, listlevels`.

---------------------------------------------------------------------------------------------------

## 5. Your first BedWars arena

Prepare a map world (e.g. folder `bw_castle`) with: one bed per team, the item/upgrade shop spots, generator spots.

```text
# load the world and stand in it
/minigame world load bw_castle
/minigame world tp bw_castle

# 1. create the arena - the world is taken from where you stand; the mode defaults to the first mode (solo)
/minigame arena create bedwars castle_solo
/minigame arena setmode bedwars castle_solo solo          # solo | doubles | 3v3 | 4v4 | 4v4v4v4 | lucky
/minigame arena setplayers bedwars castle_solo 2 8        # min 2, max = teams x team size

# 2. for every team: stand on the spawn, run setspawn; look at the bed (within 6 blocks), run setbed
/minigame arena setspawn bedwars castle_solo red
/minigame arena setbed   bedwars castle_solo red
/minigame arena setspawn bedwars castle_solo blue
/minigame arena setbed   bedwars castle_solo blue
...                                                       # up to 8 teams: red blue green yellow aqua white pink gray

# 3. generators: stand on each generator block (iron + gold next to every base, diamond/emerald in the open)
/minigame arena addgen bedwars castle_solo iron           # within 20 blocks of a team spawn => that team's forge
/minigame arena addgen bedwars castle_solo gold
/minigame arena addgen bedwars castle_solo diamond
/minigame arena addgen bedwars castle_solo emerald

# 4. shops: stand where the shop villager should be (one per team is usual)
/minigame arena addshop bedwars castle_solo item
/minigame arena addshop bedwars castle_solo upgrade

# 5. waiting lobby (can be any world, e.g. a void lobby) and spectator spot
/minigame arena setlobby     bedwars castle_solo
/minigame arena setspectator bedwars castle_solo
/minigame arena setvoid      bedwars castle_solo 0        # optional: Y below which players die (default = world min height)

# 6. check and enable
/minigame arena validate bedwars castle_solo
/minigame arena enable   bedwars castle_solo
/minigame forcestart bedwars castle_solo                  # test with a single player
```
For doubles create another arena (`castle_doubles`), `setmode ... doubles`, `setplayers ... 4 16` and copy the setup.
`lucky` mode adds Lucky Blocks from diamond/emerald generators. Shop, upgrades, generator speed, the game timeline
(diamond/emerald tiers, bed removal, game end) are all in `games/bedwars.yml`.

## 6. Your first SkyWars arena

```text
/minigame world load sw_islands
/minigame world tp sw_islands
/minigame arena create skywars islands_solo
/minigame arena setmode skywars islands_solo solo         # solo | teams | lucky
/minigame arena setplayers skywars islands_solo 2 12

# solo: stand on every island spawn
/minigame arena addspawn skywars islands_solo             # repeat for every spawn (>= max players)
# teams mode: /minigame arena setspawn skywars <arena> red   (one per team, standard colours)

# chests: look at a chest and register it, tier "island" (default) or "mid" ...
/minigame arena addchest skywars islands_solo mid
# ... or select the whole map with /minigame pos1 + pos2 and scan (containers within 14 blocks of the
# selection centre become "mid", the rest "island"):
/minigame arena scanchests skywars islands_solo 14

/minigame arena setlobby     skywars islands_solo
/minigame arena setspectator skywars islands_solo
/minigame arena enable       skywars islands_solo
```
Chests are filled lazily when first opened (loot tables in `games/skywars.yml`, refill times configurable).
Lucky mode replaces every registered chest with a Lucky Block.

## 7. Quick setup of the other games

* **Spleef** – `arena create spleef a1`, select the floor with `pos1`/`pos2`, `arena setregion spleef a1 floor`,
  `addspawn` (optional), `setlobby`, `setspectator`, `enable`. Floor blocks / rounds / tool in `games/spleef.yml`.
* **Duels / 1v1** – `arena create duels d1` (`1v1` uses its own arenas: `arena create 1v1 q1`), then
  `setspawn duels d1 red` and `blue`, `setplayers duels d1 2 2`, `setlobby`, `setspectator`, `enable`.
  An arena accepts every kit; `setmode duels d1 sumo` restricts it to one kit. Kits: `games/duels.yml`.
* **Parkour** – `arena create parkour course1`, stand at the start → `setlobby`, stand on every checkpoint in order →
  `addcheckpoint`, on the finish → `setfinish`, `enable`. Each enabled arena is a course in the menu.
* **TNT Run** – `arena create tntrun t1`, select every floor layer with `pos1`/`pos2`, `arena setregion tntrun t1 floor`,
  `setlobby`, optional `addspawn`/`setspectator`, `enable`. Timing and double jumps: `games/tntrun.yml`.
* **The Bridge** (solo / duo) – `setspawn bridge b1 red` and `blue`, then `setregion bridge b1 goal_red` and `goal_blue`
  (the goal each team defends), `setlobby`, `enable`. Duo: `setmode bridge b1 duo` + `setplayers bridge b1 4 4`.
  Kit, goals needed and building rules: `games/bridge.yml`.
* **Capture the Flag** (solo / duo) – `setspawn ctf c1 red` and `blue`, stand on each flag spot and run `setflag ctf c1 red`
  / `blue`, `setlobby`, `enable`. Duo: `setmode ctf c1 duo` + `setplayers ctf c1 4 4`. Rules and kit: `games/ctf.yml`.
* **Dropper** – per level (in order) stand at the start, select the landing zone with `pos1`/`pos2`, run
  `arena addlevel dropper d1`; `setlobby`, `enable`. Details: `games/dropper.yml`.

Full step-by-step setup for every game: [ARENAS.md](ARENAS.md).

---------------------------------------------------------------------------------------------------

### Cosmetics and ranks

* **Cosmetics** (`cosmetics.yml`): `/cosmetic` opens a shop with four categories. **Trails** (particles while you walk, lobby only
  by default), **kill effects** (played where your victim died), **win effects** (played on the winners) and **hats** (a block
  on your head in the lobby). Click an item to buy it (click again to confirm), click an owned item to equip or unequip it.
  Ownership is stored with the player's stats. Prices, particles, sounds and the whole catalogue are in `cosmetics.yml`.
* **Ranks** (`ranks.yml`): `/ranks` sells VIP / MVP / LEGEND for coins. A rank gives a prefix (chat, tab list, above the player's head and in the display name; see `ranks.yml`), a multiplier for coins earned in games, optional permission nodes and optional
  console commands (e.g. LuckPerms) on grant/removal. Buying a higher rank only costs the difference.

---------------------------------------------------------------------------------------------------

## 8. Permissions

Defaults are declared in `plugin.yml`; **every node can be remapped** in `permissions.yml` (key → node).

| Node | Key in permissions.yml | Default | Purpose |
|---|---|---|---|
| `midnight.play` | `play` | everyone | play any minigame |
| `midnight.play.<game>` (`bedwars`, `skywars`, `spleef`, `tntrun`, `bridge`, `ctf`, `dropper`, `duels`, `1v1`, `parkour`) | `play-game` | everyone | negate to block one game |
| `midnight.menu` | `menu` | everyone | compass menu and `/bw` style commands |
| `midnight.cosmetic` | - | everyone | `/cosmetic` |
| `midnight.ranks` | - | everyone | `/ranks` |
| `midnight.party` | `party` | everyone | `/party` |
| `midnight.spectate` | `spectate` | everyone | `/spectate` |
| `midnight.admin` | `admin` | op | everything below |
| `midnight.admin.reload` | `admin-reload` | op | `/minigame reload` |
| `midnight.admin.arena` | `admin-arena` | op | arena setup, `pos1/pos2`, `setlobby`; also lets `/bw ... <player>` act on others |
| `midnight.admin.forcestart` | `admin-forcestart` | op | `/minigame forcestart` |
| `midnight.admin.stop` | `admin-stop` | op | `/minigame stop` |
| `midnight.admin.world` | `admin-world` | op | `/minigame world ...` |
| `midnight.admin.build` | `admin-build` | op | build in the lobby and protected areas |
| `midnight.admin.stats` | `admin-stats` | op | `/minigame stats`, `addcoins` |

---------------------------------------------------------------------------------------------------

## 9. PlaceholderAPI

The expansion registers automatically if PlaceholderAPI is installed; otherwise the plugin logs one line and carries on
(scoreboards and everything else still work, they use the same resolver internally).

| Placeholder | Value |
|---|---|
| `%midnight_player_count%` | players currently inside any minigame |
| `%midnight_online%` | players online on the server |
| `%midnight_current_game%` | display name of the player's game (or "None") |
| `%midnight_game_state%` | WAITING / STARTING / IN_GAME / ENDING / RESETTING (text from messages.yml) |
| `%midnight_game_players%` · `%midnight_game_max_players%` | players / capacity of the player's current game |
| `%midnight_arena%` · `%midnight_mode%` | arena name / mode of the current game |
| `%midnight_wins%` `losses` `kills` `deaths` `coins` `games_played` `kdr` | global stats |
| `%midnight_<game>_players%` | players in a game, e.g. `%midnight_bedwars_players%` |
| `%midnight_<game>_players_<mode>%` | players in one mode of a round game |
| `%midnight_<game>_state%` | aggregate state of a game |
| `%midnight_<game>_wins%` `_losses` `_kills` `_deaths` `_played` | per-game stats |
| `%midnight_var_<name>%` | any scoreboard variable of the current game (`kills`, `team`, `time`, `alive`, `phase`, ...) |
| `%midnight_top_<stat>_<rank>_name%` / `_value%` | global leaderboard, e.g. `%midnight_top_wins_1_name%` (stat: wins, losses, kills, deaths, coins, games_played) |
| `%midnight_bedwars_beds%` | beds destroyed |
| `%midnight_rank%` `_rank_prefix` `_rank_multiplier` | the player's purchased rank |
| `%midnight_cosmetic_<category>%` | equipped cosmetic, category with `_` for `-`: `trails`, `kill_effects`, `win_effects`, `hats` |
| `%midnight_parkour_time%` `_course` `_checkpoint` `_best_<course>` `_top_<course>_<n>_name` / `_time` | parkour |
| `%midnight_duels_elo_<kit>%` `%midnight_duels_top_<kit>_<n>_name%` `_elo` (also `1v1`) | ranked ladder |

**Adding your own:** in a minigame, `registry.register("mygame", (player, key) -> ...)` inside
`registerPlaceholders(PlaceholderRegistry)` exposes `%midnight_mygame_<key>%` (also usable in scoreboards through
`getScoreboardVariables`).

---------------------------------------------------------------------------------------------------

## 10. NPC integration

NPC plugins only need to run a command. Nothing in MidnightMinigames depends on a specific NPC plugin.

* **Run as the player** (works everywhere): `bw`, `sw`, `spleef`, `tntrun`, `bridge`, `ctf`, `dropper`, `duels`, `1v1`,
  `parkour`  → opens that game's menu. `bw join solo` skips the menu and queues.
* **Run as console** (for plugins that only do console commands): append the player's name – the last argument may be
  the target player: `bw join solo <playername>`, `bw open <playername>`, `parkour join course1 <playername>`.
  Every NPC plugin has its own placeholder for the clicker's name; use it as the last argument.

Examples (check your NPC plugin's documentation for the exact syntax of its version):

* **Citizens** – `/npc select`, then `/npc command add bw -p` (run as player), or
  `/npc command add bw join solo <p>` (console with the clicker substituted).
* **FancyNpcs / ZNPCsPlus / others** – add a *player command* action with `bw`, or a *console command* action with
  `bw open {player}` (the placeholder token differs per plugin).
* `/games` (or `/games <player>` from the console) opens the main menu with all ten games – the same GUI as the compass.

---------------------------------------------------------------------------------------------------

## 11. Public API

```java
Minigame bedwars = MidnightMinigamesAPI.getMinigame("bedwars");
Arena arena      = MidnightMinigamesAPI.getArena("bedwars", "solo_1");
PlayerStats s    = MidnightMinigamesAPI.getPlayerStats(player);   // wins, losses, kills, deaths, coins, games played
MidnightMinigamesAPI.queue(player, "skywars", "solo");
MidnightMinigamesAPI.registerMinigame(myGame);                   // plug in your own game
```
Events (Bukkit events, main thread): `MinigameJoinEvent` (cancellable), `MinigameLeaveEvent`, `MinigameStartEvent`,
`MinigameEndEvent`, `PlayerEliminateEvent`, `ArenaResetEvent`. Add `compileOnly files("MidnightMinigames.jar")` and
`depend: [MidnightMinigames]` (or `softdepend`).

### Adding a new minigame

1. Extend `AbstractArenaMinigame` (rounds) or `AbstractPersistentMinigame` (progression). Two-team games can extend `TeamFightGame`.
2. For rounds also write a `GameInstance` subclass: implement `onGameStart()` (teleport, teams, kits); optionally
   `onTick()`, `onPlayerDeath()`, `checkWin()`, `onResetWorld()` and the event hooks inherited from `PlayerContext`
   (`onBlockBreak`, `onDamage`, `onMove` ...). Lifecycle, spectators, stats, rewards, resets are inherited.
3. Add `games.<id>` (modes) to `config.yml`, an entry in `menus.yml → main-menu.items`, and register it:
   `minigameManager.register(new MyGame(plugin))` in `MidnightMinigames#registerBuiltInMinigames`
   (or call `MidnightMinigamesAPI.registerMinigame` from another plugin). Add the command to `plugin.yml`
   and `registerCommands()` if you want a `/mygame` shortcut.

---------------------------------------------------------------------------------------------------

## 12. Newer features and known limitations

Prison, Skyblock, OneBlock and SkyGen were removed; TNT Run, The Bridge, Capture the Flag and Dropper were added.

Added after the first version (all untested on a live server): BedWars **traps** (It's a Trap, Counter-Offensive, Alarm,
Miner Fatigue; queue of 3, costs in `games/bedwars.yml`), **bridge egg**, **pop-up tower**, **magic milk**, **dream
defender** golems, **quick-buy** (shift-click any shop item to favourite it), **pickaxe/axe tiers** that drop one tier on
death, and **floating generator timers**. SkyWars **glass cages** and a **shrinking border** (per-player border, so
arenas can share a world). **Snowball spleef** mode. A **ranked ELO ladder** for Duels and 1v1 (`/duels elo`,
`/duels top`).

What is still not implemented:

* **BedWars**: block-placement limits by height, tool *selling*, spectator compass, team chat.
* **SkyWars**: no deathmatch phase besides the shrinking border.
* **TNT Run / Bridge / CTF / Dropper**: new, only checked with a simulated server (state machine, scoring, flag handling,
  block removal and restore). Real-player behaviour (hit detection in Dropper, double-jump feel, kit items) is untested.
* **Capture the Flag**: flags are banner blocks; there is no flag compass or per-team chat.
* **Block restore** (default reset mode) does not restore tile-entity contents; liquids, fire, TNT and falling blocks
  are tracked. Use `world-copy` for maps where that matters.
* Player snapshots (inventory/location restored when leaving round games) are in memory only: a server crash while
  players are inside a round game loses their pre-game inventory. Persistent games never touch inventories.
* MySQL support is implemented with plain JDBC (one reconnecting connection on the storage thread, no pool) and is
  untested; SQLite is the default and the tested path.
* Nothing here has run on a live Paper server yet. Report problems with the exact console output.

---------------------------------------------------------------------------------------------------

## 13. Pre-built jar

`dist/MidnightMinigames-1.0.0-dev-build.jar` was built in the sandbox against the Paper 26.3 API compiled from source
(see section 1) and is meant for a quick test. For production, run `mvn package` with JDK 25 so the jar is built
against the official `paper-api` artifact.
