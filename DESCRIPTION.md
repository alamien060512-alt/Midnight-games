# Midnight Minigames

**Ten minigames in one plugin for Paper 26.3. Run a whole minigame network from a single jar.**

BedWars · SkyWars · Spleef · Duels · 1v1 · Parkour · Prison · Skyblock · OneBlock · SkyGen

Everything runs in **one world**: you place arenas wherever you like, and the plugin **resets them automatically** after every round. There is no world-copying and no extra world-management plugin needed.

---

## Short summary (for the listing tagline)

> All-in-one minigame framework for Paper 26.3. BedWars, SkyWars, Spleef, Duels, 1v1, Parkour, Prison, Skyblock, OneBlock and SkyGen with parties, stats, shops, scoreboards, NPC support and automatic arena reset. Single jar, no dependencies.

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
| **Prison** | Resettable mines, rank-ups, sell values and optional autosell. |
| **Skyblock** | Per-player islands, members, invites, island level and top list, optional visual border. |
| **OneBlock** | Phase-based progression on a single block. |
| **SkyGen** | **Shared mode:** one admin-built generator area where players mine ore, sell items and buy gear, and cannot place blocks. Or **island mode** with personal generators, upgrades and prestige. |

---

## Features

- **Automatic arena reset.** Every placed, broken, burned, exploded or liquid-changed block is remembered and restored in batches, so no lag spike and no map re-building. Several arenas can share one world with an optional `bounds` region.
- **Compass and menu GUIs.** A main games menu (`/games`) and a mode menu for each game, all configurable in `menus.yml` and `config.yml`.
- **Queues, teams and parties.** Automatic team balancing and a party system (`/party`).
- **Stats and economy.** Wins, losses, kills, deaths, coins and K/D stored asynchronously in **SQLite (default) or MySQL**. Built-in coins, or **Vault** if installed.
- **Scoreboards, tab list and effects.** Per-game scoreboards, tab prefixes and configurable sounds.
- **Per-game commands.** `/bw`, `/sw`, `/spleef`, `/duels`, `/1v1`, `/parkour`, `/prison`, `/skyblock`, `/oneblock`, `/skygen`. They work from **Citizens or any NPC plugin** and from the console.
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
| `/bw` `/sw` `/spleef` `/duels` `/1v1` `/parkour` `/prison` `/skyblock` `/oneblock` `/skygen` | Open a game's menu, or `join <mode>` |
| `/party` | Create and manage parties |
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

`%midnight_online%` · `%midnight_player_count%` · `%midnight_<game>_players%` · `%midnight_wins%` · `%midnight_kills%` · `%midnight_coins%` · `%midnight_kdr%` · `%midnight_top_wins_1_name%` · `%midnight_top_wins_1_value%` · `%midnight_duels_elo_<kit>%` · `%midnight_prison_rank%` · `%midnight_skyblock_level%`

---

## Notes

- This is an early release. It compiles against the Paper 26.3 API and passes load and simulation checks, but has had limited testing on live servers. Please report problems with the exact error or log line.
- MySQL storage and Vault support are implemented but least tested.

## Support

Open an issue on the GitHub repository with your server version, the plugin version and the relevant console output.
