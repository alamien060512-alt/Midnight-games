package dev.midnight.minigames.core.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/** SQLite backend (default). The driver is bundled with Paper. */
public final class SqliteStorage extends SqlStorage {

    private final File file;

    public SqliteStorage(Logger log, File file, String prefix) {
        super(log, prefix);
        this.file = file;
    }

    @Override
    protected Connection openConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("SQLite JDBC driver not found on this server", ex);
        }
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
        }
        return c;
    }

    @Override
    protected String upsertPlayerSql() {
        return "INSERT INTO " + playersTable() + " (uuid, name, wins, losses, kills, deaths, coins, games_played) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, "
                + "wins = excluded.wins, losses = excluded.losses, kills = excluded.kills, deaths = excluded.deaths, "
                + "coins = excluded.coins, games_played = excluded.games_played";
    }

    @Override
    protected String upsertDataSql() {
        return "INSERT INTO " + dataTable() + " (uuid, namespace, data_key, data_value) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(uuid, namespace, data_key) DO UPDATE SET data_value = excluded.data_value";
    }

    @Override
    public String getType() {
        return "sqlite";
    }
}
