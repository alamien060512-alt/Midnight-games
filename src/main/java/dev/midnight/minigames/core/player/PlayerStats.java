package dev.midnight.minigames.core.player;

/** Global per-player statistics shared by every minigame. */
public final class PlayerStats {

    private int wins;
    private int losses;
    private int kills;
    private int deaths;
    private int gamesPlayed;
    private double coins;

    public PlayerStats() {
    }

    public PlayerStats(int wins, int losses, int kills, int deaths, int gamesPlayed, double coins) {
        this.wins = wins;
        this.losses = losses;
        this.kills = kills;
        this.deaths = deaths;
        this.gamesPlayed = gamesPlayed;
        this.coins = coins;
    }

    public PlayerStats copy() {
        return new PlayerStats(wins, losses, kills, deaths, gamesPlayed, coins);
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public double getCoins() {
        return coins;
    }

    public void setWins(int v) {
        wins = v;
    }

    public void setLosses(int v) {
        losses = v;
    }

    public void setKills(int v) {
        kills = v;
    }

    public void setDeaths(int v) {
        deaths = v;
    }

    public void setGamesPlayed(int v) {
        gamesPlayed = v;
    }

    public void setCoins(double v) {
        coins = Math.max(0, v);
    }

    public double getKdr() {
        return deaths == 0 ? kills : Math.round((kills / (double) deaths) * 100.0) / 100.0;
    }
}
