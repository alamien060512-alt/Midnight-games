# Midnight Minigames

**Ten minigames in one plugin for Paper 26.3. Run a whole minigame network from a single jar.**

BedWars · SkyWars · Spleef · TNT Run · The Bridge · Capture the Flag · Dropper · Duels · 1v1 · Parkour

Everything runs in **one world**: you place arenas wherever you like, and the plugin **resets them automatically** after every round. There is no world-copying and no extra world-management plugin needed.

---

## Short summary (for the listing tagline)

> All-in-one minigame framework for Paper 26.3. BedWars, SkyWars, Spleef, TNT Run, The Bridge, Capture the Flag, Dropper, Duels, 1v1 and Parkour with parties, stats, shops, scoreboards, NPC support and automatic arena reset. Single jar, no dependencies.

---

## Games

| Game | Highlights |
|---|---|
| **BedWars** | Solo, doubles, 3v3, 4v4, 4v4v4v4 and Lucky modes. Item shop with quick-buy, team upgrades and traps, tool tiers, generators with floating timers, bridge egg, pop-up tower, magic milk, dream defender, blast-proof glass. |
| **SkyWars** | Solo, teams and Lucky modes. Cages at start, configurable loot tables, shrinking border, chests filled on first open. |
| **Spleef** | Classic, snowball and marathon modes. |
| **Duels** | Any number of kits defined in config, with a **ranked ELO ladder** per kit (`/duels elo`, `/duels top`). |
| **1v1** | Quick and classic queues using the same engine. |
| **Parkour** | Courses with checkpoints, personal bests and per-course leaderboards. |
| **TNT Run** | The floor vanishes under your feet. Three double jumps, last one standing wins. |
| **The Bridge** | Solo (1v1) and duo (2v2). Cross the bridge, jump into the enemy goal, first to 5 wins. Build to defend and attack. |
| **Capture the Flag** | Solo (1v1) and duo (2v2). Steal the enemy flag, return it to your base, dropped flags return on a timer. |
| **Dropper** | Fall through each map without touching a block. Race to finish every level first. |

---

## Features

- **Automatic arena reset.** Every placed, broken, burned, exploded or liquid-changed block is remembered and restored in batches, so no lag spike and no map re-building. Several arenas can share one world with an optional `bounds` region.
- **Compass and menu GUIs.** A main games menu (`/games`) and a mode menu for each game, all configurable in `menus.yml` and `config.yml`.
- **Queues, teams and parties.** Automatic team balancing and a party system (`/party`).
- **Stats and economy.** Wins, losses, kills, deaths, coins and K/D stored asynchronously in **SQLite (default) or MySQL**. Built-in coins, or **Vault** if installed.
- **Scoreboards, tab list and effects.** Per-game scoreboards, tab prefixes and configurable sounds.
- **Cosmetics shop.** `/cosmetic`: trails, kill effects, win effects and hats, bought with coins and saved per player.
- **Ranks.** `/ranks`: VIP / MVP / LEGEND bought with coins. They give a tag, a coin multiplier and optional permissions or LuckPerms commands.
- **Per-game commands.** `/bw`, `/sw`, `/spleef`, `/tntrun`, `/bridge`, `/ctf`, `/dropper`, `/duels`, `/1v1`, `/parkour`. They work from **Citizens or any NPC plugin** and from the console.
- **PlaceholderAPI** (optional) with the `midnight` identifier, ready for holograms and scoreboards.
- **Developer API and events.** Join, leave, start and end events, plus a public API.
- **Everything is configurable.** `config.yml`, `messages.yml`, `menus.yml`, `permissions.yml` and one file per game under `games/`.

---

## Requirements

- **Server:** Paper **26.3** or newer
- **Java:** 25
- **Optional:** PlaceholderAPI, Vault, Citizens or any NPC plugin, a hologram plugin such as DecentHolograms or FancyHolograms

---

## Quick start

1. Drop `MidnightMinigames.jar` into `plugins/` and start the server.
2. Create an arena for a game, for example BedWars:
   ```
   /minigame arena create bedwars speedway
   /minigame arena setmode bedwars speedway doubles
   /minigame arena setlobby bedwars speedway
   /minigame arena setspawn bedwars speedway red
   /minigame arena setbed bedwars speedway red
   /minigame arena addgen bedwars speedway iron
   /minigame arena addshop bedwars speedway item
   /minigame arena enable bedwars speedway
   ```
   `enable` tells you exactly what is still missing.
3. Set your main lobby with `/minigame setlobby`.
4. Give players `midnight.play` and `midnight.menu`.
5. Players run `/games`, or click an NPC that runs `/bw`.

The full setup for every game is in **ARENAS.md**.

---

## Commands

| Command | Use |
|---|---|
| `/games` | Open the main menu |
| `/bw` `/sw` `/spleef` `/tntrun` `/bridge` `/ctf` `/dropper` `/duels` `/1v1` `/parkour` | Open a game's menu, or `join <mode>` |
| `/party` | Create and manage parties |
| `/cosmetic` | Cosmetic shop |
| `/ranks` | Rank shop |
| `/leave` | Leave the current game |
| `/spectate` | Spectate a game |
| `/minigame` | Admin: arenas, worlds, reload, force-start, stop, stats |

## Permissions

| Node | Allows |
|---|---|
| `midnight.play` and `midnight.play.<game>` | Play (all or one game) |
| `midnight.menu` | Open the menus |
| `midnight.admin.arena` | Create and edit arenas |
| `midnight.admin.build` | Build in protected areas |
| `midnight.admin.world` | World commands |

All nodes are renamable in `permissions.yml`.

## PlaceholderAPI

Identifier: `midnight`. Examples:

`%midnight_online%` · `%midnight_player_count%` · `%midnight_<game>_players%` · `%midnight_wins%` · `%midnight_kills%` · `%midnight_coins%` · `%midnight_kdr%` · `%midnight_top_wins_1_name%` · `%midnight_top_wins_1_value%` · `%midnight_duels_elo_<kit>%` · `%midnight_var_<name>%`

---

## Notes

- This is an early release. It compiles against the Paper 26.3 API and passes load and simulation checks, but has had limited testing on live servers. Please report problems with the exact error or log line.
- MySQL storage and Vault support are implemented but least tested.

## Support

Open an issue on the GitHub repository with your server version, the plugin version and the relevant console output.
