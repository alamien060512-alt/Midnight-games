# Arena setup guide

How to create an arena for every minigame. All commands need `midnight.admin.*` (or OP). Replace `<arena>` with any name you like (letters, digits, `_`, `-`).

> Written from the plugin's source. It has not been tested on a live server, so if a step fails, send the exact message.

## 0. Basics that apply to every game

Everything can live in **one world** (your main world or a lobby world). Arenas are separate **regions** in it, and the plugin resets them itself.

| Step | Command |
|---|---|
| Create the arena (uses the world you stand in) | `/minigame arena create <game> <arena>` |
| Change the world | `/minigame arena setworld <game> <arena> <world>` |
| Lobby / waiting spawn (stand there) | `/minigame arena setlobby <game> <arena>` |
| Spectator spot (stand there) | `/minigame arena setspectator <game> <arena>` |
| Team spawn (stand there) | `/minigame arena setspawn <game> <arena> <team>` |
| Extra free-for-all spawn | `/minigame arena addspawn <game> <arena>` |
| Wipe spawns | `/minigame arena clearspawns <game> <arena>` |
| Mode (solo, doubles, ...) | `/minigame arena setmode <game> <arena> <mode>` |
| Min / max players | `/minigame arena setplayers <game> <arena> <min> <max>` |
| Region (after `/minigame pos1` and `pos2`) | `/minigame arena setregion <game> <arena> <key>` |
| Reset mode | `/minigame arena setreset <game> <arena> <blocks\|world-copy\|none>` |
| Void kill height | `/minigame arena setvoid <game> <arena> [y]` |
| Check for problems | `/minigame arena validate <game> <arena>` |
| Turn it on | `/minigame arena enable <game> <arena>` |
| Turn it off | `/minigame arena disable <game> <arena>` |
| Info / teleport / list / delete | `/minigame arena info\|tp\|list\|delete ...` |

Team names are colours: `red blue green yellow aqua white pink gray`.

**Several arenas in one world:** give every arena a `bounds` region so explosions and resets stay inside it:
`/minigame pos1`, `/minigame pos2`, then `/minigame arena setregion <game> <arena> bounds`.

**Reset:** the default `blocks` mode remembers every block that changes and restores it afterwards. You never need to rebuild the map.

`enable` refuses to turn the arena on and lists what is missing. Fix those and run it again.

---

## 1. BedWars

Modes: `solo`, `doubles`, `3v3`, `4v4`, `4v4v4v4`, `lucky`. One arena has one mode.

```
/minigame arena create bedwars speedway
/minigame arena setmode bedwars speedway doubles
/minigame arena setplayers bedwars speedway 4 16
/minigame arena setlobby bedwars speedway           (waiting area)
/minigame arena setspectator bedwars speedway
/minigame arena setspawn bedwars speedway red       (stand on each team's spawn)
/minigame arena setspawn bedwars speedway blue
/minigame arena setbed bedwars speedway red         (LOOK at the team's bed)
/minigame arena setbed bedwars speedway blue
/minigame arena addgen bedwars speedway iron        (stand on each generator spot)
/minigame arena addgen bedwars speedway gold
/minigame arena addgen bedwars speedway diamond
/minigame arena addgen bedwars speedway emerald
/minigame arena addshop bedwars speedway item       (stand where the item shop NPC goes)
/minigame arena addshop bedwars speedway upgrade
/minigame arena validate bedwars speedway
/minigame arena enable bedwars speedway
```
Needs at least 2 team spawns, a bed for each team, at least one generator, an item shop, an upgrade shop and a spectator spot. Remove generators or shops with `cleargens` and `clearshops`. Every team with a spawn gets a base, so make one spawn and bed per team (4v4v4v4 means 4 teams).

## 2. SkyWars

Modes: `solo`, `teams`, `lucky`.

```
/minigame arena create skywars castle
/minigame arena setmode skywars castle solo
/minigame arena setplayers skywars castle 2 12
/minigame arena setlobby skywars castle
/minigame arena setspectator skywars castle
/minigame arena addspawn skywars castle             (once per island; at least 2)
/minigame arena scanchests skywars castle           (finds every chest in bounds, see below)
/minigame arena enable skywars castle
```
- For `teams`, use `setspawn skywars castle red` and `blue` instead of `addspawn`.
- Chests: `scanchests` needs the `bounds` region first. Or stand next to a chest and run `addchest skywars castle island` (or `mid` for the better loot). `clearchests` wipes the list.
- `lucky` mode needs no chests.

## 3. Spleef

Modes: `classic`, `snowball`, `marathon`.

```
/minigame arena create spleef pit
/minigame arena setmode spleef pit classic
/minigame arena setplayers spleef pit 2 16
/minigame arena setlobby spleef pit
/minigame pos1   /minigame pos2                   (select the floor)
/minigame arena setregion spleef pit floor
/minigame arena addspawn spleef pit               (a few spots above the floor)
/minigame arena enable spleef pit
```
The `floor` region is rebuilt after each round. Players who fall below it are out.

## 4. Duels (kits)

Every kit in `games/duels.yml` is a mode. The arena mode `any` (default) hosts every kit.

```
/minigame arena create duels box
/minigame arena setplayers duels box 2 2
/minigame arena setlobby duels box
/minigame arena setspectator duels box
/minigame arena setspawn duels box red
/minigame arena setspawn duels box blue
/minigame arena enable duels box
```
Lock one arena to one kit: `/minigame arena setmode duels box sumo`.

## 5. 1v1

Same as Duels with game id `1v1`. Modes: `quick`, `classic`.

```
/minigame arena create 1v1 small
/minigame arena setplayers 1v1 small 2 2
/minigame arena setlobby 1v1 small
/minigame arena setspectator 1v1 small
/minigame arena setspawn 1v1 small red
/minigame arena setspawn 1v1 small blue
/minigame arena enable 1v1 small
```

## 6. Parkour

One arena is one course.

```
/minigame arena create parkour easy1
/minigame arena setlobby parkour easy1              (stand at the START)
/minigame arena addcheckpoint parkour easy1         (stand on each checkpoint, in order)
/minigame arena setfinish parkour easy1             (stand on the finish)
/minigame arena seticon parkour easy1 FEATHER
/minigame arena setdifficulty parkour easy1 <value>
/minigame arena setdisplayname parkour easy1 Easy Course 1
/minigame arena enable parkour easy1
```
`clearcheckpoints` starts the checkpoint list over.

## 7. Prison

One arena named `main`, plus any number of mines.

```
/minigame arena create prison main
/minigame arena setlobby prison main                (prison spawn)
/minigame pos1   /minigame pos2                     (select the mine area)
/minigame arena addmine prison main a               (mine "a", needs rank A)
/minigame arena addmine prison main b B             (mine "b", needs rank B)
/minigame arena enable prison main
```
Other commands: `delmine`, `setminespawn <mine>` (stand there), `resetmine <mine>`, `listmines`. Block tables, ranks and sell prices are in `games/prison.yml`. Players run `/prison sell`.

## 8. Skyblock

```
/minigame world create skyblock_world               (void world; or use your main world)
/minigame arena create skyblock main
/minigame arena setworld skyblock main skyblock_world
/minigame arena setlobby skyblock main              (hub spawn)
/minigame arena enable skyblock main
```
Islands are created per player with `/skyblock create`, spaced by `island.spacing` in `games/skyblock.yml`.

## 9. OneBlock

Identical to Skyblock with game id `oneblock`:

```
/minigame arena create oneblock main
/minigame arena setworld oneblock main <world>
/minigame arena setlobby oneblock main
/minigame arena enable oneblock main
```
Players run `/oneblock create`.

## 10. SkyGen (shared)

SkyGen now defaults to **shared mode** (`mode: shared` in `games/skygen.yml`):

- Everyone plays in **one shared generator area** that an admin builds.
- **Players cannot place blocks** (admins with `midnight.admin.build` can).
- Players **mine** the ore blocks the generators produce. They can mine nothing else.
- Players **sell** items with `/skygen sell` (or the button in the shop). They **buy** items with `/skygen buy`.

### Build the area
1. Build your platform. For each generator, place a normal block where the generator should be and leave the **block above it empty**. The ore appears there.
2. Create and configure the arena:
```
/minigame arena create skygen main
/minigame arena setworld skygen main <world>
/minigame arena setlobby skygen main                (stand at the spawn)
```
3. For every generator, **look at its block** (within 8 blocks) and run:
```
/minigame arena addgen skygen main <tier>           (tier 1 to 5, see games/skygen.yml -> tiers)
```
The block turns into that tier's generator block (cobble, iron, gold, diamond, emerald) and ore spawns above it every few seconds.
4. Enable it: `/minigame arena enable skygen main`

Other commands: `delgen` (look at a generator), `listgens`, `cleargens`.

### Player commands
| Command | What it does |
|---|---|
| `/skygen` | Enter SkyGen |
| `/skygen sell` | Sell everything sellable in your inventory |
| `/skygen buy` | Open the item shop (pickaxes, food, ...) with a sell button |
| `/skygen stats` | Balance, blocks mined, sell multiplier |
| `/skygen spawn` | Teleport back to spawn |
| `/skygen leave` | Leave the game |

### Configure it (`games/skygen.yml` -> `shared`)
- `shared.sell.values`: coins per dropped item (`COAL`, `RAW_IRON`, `DIAMOND`, ...).
- `shared.sell.autosell: true`: blocks pay instantly instead of dropping items.
- `shared.sell.multiplier` and `permission-multipliers`: for example `midnight.skygen.vip` earns x1.5.
- `shared.buy.items`: shop entries (`slot`, `material`, `name`, `price`, `amount`, `enchants`...).
- `shared.starter-items`: given once to a new player (a pickaxe by default).
- Generator tiers (`tiers:`) set which ore each generator makes and how fast.

To go back to the per-player island version, set `mode: island`.

---

## Troubleshooting

- `enable` lists a problem: read it, fix it, run `validate` again.
- Blocks placed during a match are restored at the end. If something is not, check that arena `reset-mode` is `blocks` and that the arena has a `bounds` region when several arenas share a world.
- Players cannot interact with an NPC: give them `midnight.play` and `midnight.play.<game>`.
- After editing a config file, run `/minigame reload`.
