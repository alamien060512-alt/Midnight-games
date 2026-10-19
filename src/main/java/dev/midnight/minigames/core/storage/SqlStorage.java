package dev.midnight.minigames.core.storage;

import dev.midnight.minigames.core.player.PlayerStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared JDBC implementation; subclasses provide the connection and the dialect specific upserts. */
public abstract class SqlStorage implements StorageProvider {

    private static final Set<String> STAT_COLUMNS = Set.of("wins", "losses", "kills", "deaths", "coins", "games_played");

    protected final Logger log;
    protected final String prefix;
    private final ExecutorService executor;
    private Connection connection;

    protected SqlStorage(Logger log, String tablePrefix) {
        this.log = log;
        this.prefix = tablePrefix == null ? "" : tablePrefix.replaceAll("[^A-Za-z0-9_]", "");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MidnightMinigames-Storage");
            t.setDaemon(false);
            return t;
        });
    }

    protected abstract Connection openConnection() throws SQLException;

    /** SQL with 8 parameters: uuid, name, wins, losses, kills, deaths, coins, games_played. */
    protected abstract String upsertPlayerSql();

    /** SQL with 4 parameters: uuid, namespace, key, value. */
    protected abstract String upsertDataSql();

    protected String playersTable() {
        return prefix + "players";
    }

    protected String dataTable() {
        return prefix + "player_data";
    }

    private Connection conn() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            connection = openConnection();
        }
        return connection;
    }

    @Override
    public void init() throws Exception {
        executor.submit(() -> {
            try (Statement st = conn().createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + playersTable() + " ("
                        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + "name VARCHAR(32),"
                        + "wins INT NOT NULL DEFAULT 0,"
                        + "losses INT NOT NULL DEFAULT 0,"
                        + "kills INT NOT NULL DEFAULT 0,"
                        + "deaths INT NOT NULL DEFAULT 0,"
                        + "coins DOUBLE NOT NULL DEFAULT 0,"
                        + "games_played INT NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + dataTable() + " ("
                        + "uuid VARCHAR(36) NOT NULL,"
                        + "namespace VARCHAR(48) NOT NULL,"
                        + "data_key VARCHAR(96) NOT NULL,"
                        + "data_value VARCHAR(255) NOT NULL,"
                        + "PRIMARY KEY (uuid, namespace, data_key))");
                return null;
            }
        }).get(30, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warning("Storage queue did not drain in 30s - some data may be lost.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }

    private <T> CompletableFuture<T> async(SqlCall<T> call) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(call.run());
                } catch (Exception ex) {
                    log.log(Level.SEVERE, "Database error: " + ex.getMessage(), ex);
                    future.completeExceptionally(ex);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            future.completeExceptionally(ex);
        }
        return future;
    }

    @FunctionalInterface
    private interface SqlCall<T> {
        T run() throws Exception;
    }

    @Override
    public CompletableFuture<StoredProfile> loadProfile(UUID uuid, String name) {
        return async(() -> {
            PlayerStats stats = new PlayerStats();
            Map<String, Map<String, String>> data = new HashMap<>();
            Connection c = conn();
            try (PreparedStatement ps = c.prepareStatement("SELECT wins, losses, kills, deaths, coins, games_played FROM "
                    + playersTable() + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats = new PlayerStats(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(6), rs.getDouble(5));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT namespace, data_key, data_value FROM "
                    + dataTable() + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        data.computeIfAbsent(rs.getString(1), k -> new HashMap<>()).put(rs.getString(2), rs.getString(3));
                    }
                }
            }
            return new StoredProfile(stats, data);
        });
    }

    @Override
    public CompletableFuture<Void> saveProfile(UUID uuid, String name, PlayerStats s, Map<String, Map<String, String>> data) {
        return async(() -> {
            Connection c = conn();
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(upsertPlayerSql())) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, name);
                    ps.setInt(3, s.getWins());
                    ps.setInt(4, s.getLosses());
                    ps.setInt(5, s.getKills());
                    ps.setInt(6, s.getDeaths());
                    ps.setDouble(7, s.getCoins());
                    ps.setInt(8, s.getGamesPlayed());
                    ps.executeUpdate();
                }
                try (PreparedStatement up = c.prepareStatement(upsertDataSql());
                     PreparedStatement del = c.prepareStatement("DELETE FROM " + dataTable()
                             + " WHERE uuid = ? AND namespace = ? AND data_key = ?")) {
                    for (Map.Entry<String, Map<String, String>> ns : data.entrySet()) {
                        for (Map.Entry<String, String> kv : ns.getValue().entrySet()) {
                            if (kv.getValue() == null || kv.getValue().isEmpty()) {
                                del.setString(1, uuid.toString());
                                del.setString(2, ns.getKey());
                                del.setString(3, kv.getKey());
                                del.addBatch();
                            } else {
                                up.setString(1, uuid.toString());
                                up.setString(2, ns.getKey());
                                up.setString(3, kv.getKey());
                                up.setString(4, kv.getValue().length() > 255 ? kv.getValue().substring(0, 255) : kv.getValue());
                                up.addBatch();
                            }
                        }
                    }
                    up.executeBatch();
                    del.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(auto);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<List<TopEntry>> topData(String namespace, String key, int limit, boolean ascending) {
        return async(() -> {
            List<TopEntry> out = new ArrayList<>();
            String sql = "SELECT d.uuid, p.name, d.data_value FROM " + dataTable() + " d LEFT JOIN " + playersTable()
                    + " p ON p.uuid = d.uuid WHERE d.namespace = ? AND d.data_key = ? ORDER BY (d.data_value + 0) "
                    + (ascending ? "ASC" : "DESC") + " LIMIT ?";
            try (PreparedStatement ps = conn().prepareStatement(sql)) {
                ps.setString(1, namespace);
                ps.setString(2, key);
                ps.setInt(3, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString(2);
                        double value;
                        try {
                            value = Double.parseDouble(rs.getString(3));
                        } catch (NumberFormatException ex) {
                            continue;
                        }
                        out.add(new TopEntry(UUID.fromString(rs.getString(1)), name == null ? "Unknown" : name, value));
                    }
                }
            }
            return out;
        });
    }

    @Override
    public CompletableFuture<List<TopEntry>> topStat(String column, int limit) {
        if (!STAT_COLUMNS.contains(column)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown stat column " + column));
        }
        return async(() -> {
            List<TopEntry> out = new ArrayList<>();
            String sql = "SELECT uuid, name, " + column + " FROM " + playersTable() + " ORDER BY " + column + " DESC LIMIT ?";
            try (PreparedStatement ps = conn().prepareStatement(sql)) {
                ps.setInt(1, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString(2);
                        out.add(new TopEntry(UUID.fromString(rs.getString(1)), name == null ? "Unknown" : name, rs.getDouble(3)));
                    }
                }
            }
            return out;
        });
    }
}
