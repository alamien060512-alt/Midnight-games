package dev.midnight.minigames.minigames.bedwars;

/** BedWars specific state of a team. */
final class TeamState {

    boolean bedAlive = true;
    int sharpness;
    int protection;
    int forge;
    int haste;
    boolean healPool;
    /** Queued traps (ids from games/bedwars.yml -> upgrades.traps), triggered first-in-first-out. */
    final java.util.List<String> traps = new java.util.ArrayList<>();

    int level(String upgradeId) {
        return switch (upgradeId) {
            case "sharpness" -> sharpness;
            case "protection" -> protection;
            case "forge" -> forge;
            case "haste" -> haste;
            case "heal-pool" -> healPool ? 1 : 0;
            default -> 0;
        };
    }

    void setLevel(String upgradeId, int level) {
        switch (upgradeId) {
            case "sharpness" -> sharpness = level;
            case "protection" -> protection = level;
            case "forge" -> forge = level;
            case "haste" -> haste = level;
            case "heal-pool" -> healPool = level > 0;
            default -> {
            }
        }
    }
}
