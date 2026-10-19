package dev.midnight.minigames.core.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

/** MySQL / MariaDB backend using the connector bundled with Paper (MySQL protocol). */
public final class MysqlStorage extends SqlStorage {

    private final String url;
    private final String user;
    private final String password;

    public MysqlStorage(Logger log, String host, int port, String database, String user, String password,
                        String params, String prefix) {
        super(log, prefix);
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + (params == null || params.isBlank() ? "" : "?" + params);
        this.user = user;
        this.password = password;
    }

    @Override
    protected Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    protected String upsertPlayerSql() {
        return "INSERT INTO " + playersTable() + " (uuid, name, wins, losses, kills, deaths, coins, games_played) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), wins = VALUES(wins), "
                + "losses = VALUES(losses), kills = VALUES(kills), deaths = VALUES(deaths), coins = VALUES(coins), "
                + "games_played = VALUES(games_played)";
    }

    @Override
    protected String upsertDataSql() {
        return "INSERT INTO " + dataTable() + " (uuid, namespace, data_key, data_value) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE data_value = VALUES(data_value)";
    }

    @Override
    public String getType() {
        return "mysql";
    }
}
