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
Needs at least 2 team spawns, a bed for each team, at least one generator, an item shop, an upgrade shop and a spectator spot. Remove generators or shops with `cleargens` and `clearshops`.

**Build limit:** select the whole map with `/minigame pos1` and `pos2`, then `/minigame arena setregion bedwars speedway bounds`. Players (and bridge eggs, towers and buckets) can then only build inside it. Add a height limit with `build-max-y` in `games/bedwars.yml`. Every team with a spawn gets a base, so make one spawn and bed per team (4v4v4v4 means 4 teams).

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

## 4. TNT Run

Mode: `classic`. The floor disappears under the players' feet; last one standing wins.

```
/minigame arena create tntrun runway
/minigame arena setplayers tntrun runway 2 16
/minigame arena setlobby tntrun runway
/minigame pos1   /minigame pos2                     (select EVERY floor layer, top to bottom)
/minigame arena setregion tntrun runway floor
/minigame arena addspawn tntrun runway              (optional; without spawns players start at random spots on the top layer)
/minigame arena setspectator tntrun runway          (optional)
/minigame arena enable tntrun runway
```
- Build the floor yourself. The classic look is a layer of sand on top of a layer of TNT, repeated a few times, with a gap
  between the layers so players fall onto the next one.
- A block vanishes about half a second after a player steps on it, together with the block below it. Everything is restored
  after the game, so you never rebuild the map.
- Players are out when they fall below the lowest layer. Players get 3 double jumps (press space twice).
- Delay, grace period and jumps: `games/tntrun.yml`.

## 5. The Bridge (solo, duo)

Modes: `solo` (1v1) and `duo` (2v2). Two teams, **red** and **blue**. Jump into the enemy goal to score, first to 5 wins.

```
/minigame arena create bridge classic
/minigame arena setmode bridge classic solo          (or duo)
/minigame arena setplayers bridge classic 2 2        (duo: 4 4)
/minigame arena setlobby bridge classic
/minigame arena setspectator bridge classic          (optional)
/minigame arena setspawn bridge classic red          (stand on the red spawn cage)
/minigame arena setspawn bridge classic blue
/minigame pos1   /minigame pos2                      (select the RED goal hole)
/minigame arena setregion bridge classic goal_red    (the goal that RED defends)
/minigame pos1   /minigame pos2                      (select the BLUE goal hole)
/minigame arena setregion bridge classic goal_blue
/minigame arena enable bridge classic
```
- A team scores by entering the **other** team's goal region. Make each region a few blocks deep so players cannot miss it.
- Optional `bounds` region (`setregion bridge classic bounds`) limits where players can build.
- After a goal everyone goes back to their spawn with a fresh kit and a 5 second countdown. Placed blocks stay, so the
  bridge keeps growing. Players can only break blocks that players placed.
- Kit, goals needed, countdown and respawn time: `games/bridge.yml`.

## 6. Capture the Flag (solo, duo)

Modes: `solo` (1v1) and `duo` (2v2). Two teams, **red** and **blue**. Capture the enemy flag 3 times to win.

```
/minigame arena create ctf castle
/minigame arena setmode ctf castle solo              (or duo)
/minigame arena setplayers ctf castle 2 2            (duo: 4 4)
/minigame arena setlobby ctf castle
/minigame arena setspectator ctf castle              (optional)
/minigame arena setspawn ctf castle red
/minigame arena setspawn ctf castle blue
/minigame arena setflag ctf castle red               (stand ON the spot where the red flag should be)
/minigame arena setflag ctf castle blue
/minigame arena enable ctf castle
```
- Flags are banner blocks placed at the flag spots when the game starts (the spot must be free air with solid ground below).
- Walk up to the enemy flag to pick it up (you glow and wear the banner). Bring it to your own flag to score, as long as your
  own flag is at home.
- A killed carrier drops the flag. Teammates return it by touching it, otherwise it returns by itself after 20 seconds.
- Building is off by default. Kit, captures needed, respawn time and flag rules: `games/ctf.yml`.

## 7. Dropper

Mode: `race`. Fall through each level without touching anything and land in the finish zone. First to finish all levels wins.

```
/minigame arena create dropper maps
/minigame arena setplayers dropper maps 1 8
/minigame arena setlobby dropper maps
```
Then for **each level, in order**:
```
(stand at the top of the level, where players should start)
/minigame pos1   /minigame pos2                      (select the landing zone at the bottom, usually the water pool)
/minigame arena addlevel dropper maps
```
and finally `/minigame arena enable dropper maps`.
- Touching any block while falling sends the player back to the start of the level.
- Collisions only count after the player has dropped about 1.5 blocks, so the start platform is safe.
- The landing zone must contain the place where players end up (the water), so the finish is detected before they hit the floor.
- Other commands: `dellevel <n>`, `clearlevels`, `listlevels`. Tuning: `games/dropper.yml`.

## 8. Duels (kits)

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

## 9. 1v1

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

## 10. Parkour

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

## Troubleshooting

- `enable` lists a problem: read it, fix it, run `validate` again.
- Blocks placed during a match are restored at the end. If something is not, check that arena `reset-mode` is `blocks` and that the arena has a `bounds` region when several arenas share a world.
- Players cannot interact with an NPC: give them `midnight.play` and `midnight.play.<game>`.
- After editing a config file, run `/minigame reload`.
