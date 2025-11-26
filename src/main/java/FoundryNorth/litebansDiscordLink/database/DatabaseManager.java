package FoundryNorth.litebansDiscordLink.database;

import FoundryNorth.litebansDiscordLink.LitebansDiscordLink;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages MySQL database connections and operations
 */
public class DatabaseManager {

    private final LitebansDiscordLink plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(LitebansDiscordLink plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize database connection pool and create tables
     */
    public void initialize() throws SQLException {
        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String database = plugin.getConfig().getString("database.database", "minecraft");
        String username = plugin.getConfig().getString("database.username", "root");
        String password = plugin.getConfig().getString("database.password", "");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);

        // Connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        // MySQL-specific settings
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        dataSource = new HikariDataSource(config);

        createTables();

        plugin.getLogger().info("Database connected successfully!");
    }

    /**
     * Create the punishments table if it doesn't exist
     */
    private void createTables() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS litebansdiscordlink_punishments (" +
                "discord_id VARCHAR(20) PRIMARY KEY," +
                "minecraft_uuid VARCHAR(36) NOT NULL," +
                "minecraft_name VARCHAR(16) NOT NULL," +
                "type VARCHAR(10) NOT NULL," +
                "reason TEXT," +
                "expiry_time BIGINT NOT NULL," +
                "issued_time BIGINT NOT NULL," +
                "INDEX idx_expiry (expiry_time)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createTable);
        }
    }

    /**
     * Get a connection from the pool
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not available");
        }
        return dataSource.getConnection();
    }

    /**
     * Check if the database is available
     */
    public boolean isAvailable() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Close the connection pool
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed");
        }
    }

    /**
     * Add or update a punishment
     */
    public void savePunishment(String discordId, PunishmentTracker.PunishmentInfo info) {
        if (!isAvailable()) {
            plugin.getLogger().warning("Cannot save punishment: Database connection is not available");
            return;
        }

        String sql = "INSERT INTO litebansdiscordlink_punishments " +
                "(discord_id, minecraft_uuid, minecraft_name, type, reason, expiry_time, issued_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "minecraft_uuid = VALUES(minecraft_uuid), " +
                "minecraft_name = VALUES(minecraft_name), " +
                "type = VALUES(type), " +
                "reason = VALUES(reason), " +
                "expiry_time = VALUES(expiry_time), " +
                "issued_time = VALUES(issued_time)";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, discordId);
            stmt.setString(2, info.getMinecraftUuid().toString());
            stmt.setString(3, info.getMinecraftName());
            stmt.setString(4, info.getType());
            stmt.setString(5, info.getReason());
            stmt.setLong(6, info.getExpiryTime());
            stmt.setLong(7, info.getIssuedTime());

            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save punishment: " + e.getMessage());
            if (plugin.isDebug()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Remove a punishment
     */
    public void removePunishment(String discordId) {
        String sql = "DELETE FROM litebansdiscordlink_punishments WHERE discord_id = ?";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, discordId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove punishment: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get a punishment by Discord ID
     */
    public PunishmentTracker.PunishmentInfo getPunishment(String discordId) {
        String sql = "SELECT * FROM litebansdiscordlink_punishments WHERE discord_id = ?";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, discordId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PunishmentTracker.PunishmentInfo(
                            UUID.fromString(rs.getString("minecraft_uuid")),
                            rs.getString("minecraft_name"),
                            rs.getString("type"),
                            rs.getString("reason"),
                            rs.getLong("expiry_time"));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get punishment: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get all active punishments
     */
    public Map<String, PunishmentTracker.PunishmentInfo> getAllPunishments() {
        Map<String, PunishmentTracker.PunishmentInfo> punishments = new HashMap<>();
        String sql = "SELECT * FROM litebansdiscordlink_punishments";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String discordId = rs.getString("discord_id");
                PunishmentTracker.PunishmentInfo info = new PunishmentTracker.PunishmentInfo(
                        UUID.fromString(rs.getString("minecraft_uuid")),
                        rs.getString("minecraft_name"),
                        rs.getString("type"),
                        rs.getString("reason"),
                        rs.getLong("expiry_time"));
                punishments.put(discordId, info);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get all punishments: " + e.getMessage());
            e.printStackTrace();
        }

        return punishments;
    }

    /**
     * Get all expired punishments (for cleanup with Discord role removal)
     */
    public Map<String, PunishmentTracker.PunishmentInfo> getExpiredPunishments() {
        Map<String, PunishmentTracker.PunishmentInfo> expired = new HashMap<>();
        String sql = "SELECT * FROM litebansdiscordlink_punishments WHERE expiry_time != -1 AND expiry_time < ?";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, System.currentTimeMillis());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String discordId = rs.getString("discord_id");
                    PunishmentTracker.PunishmentInfo info = new PunishmentTracker.PunishmentInfo(
                            UUID.fromString(rs.getString("minecraft_uuid")),
                            rs.getString("minecraft_name"),
                            rs.getString("type"),
                            rs.getString("reason"),
                            rs.getLong("expiry_time"));
                    expired.put(discordId, info);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get expired punishments: " + e.getMessage());
            if (plugin.isDebug()) {
                e.printStackTrace();
            }
        }

        return expired;
    }

    /**
     * Remove all expired punishments
     */
    public int cleanExpired() {
        String sql = "DELETE FROM litebansdiscordlink_punishments WHERE expiry_time != -1 AND expiry_time < ?";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, System.currentTimeMillis());
            return stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to clean expired punishments: " + e.getMessage());
            if (plugin.isDebug()) {
                e.printStackTrace();
            }
        }

        return 0;
    }

    /**
     * Check if a Discord ID has an active punishment
     */
    public boolean isPunished(String discordId) {
        PunishmentTracker.PunishmentInfo info = getPunishment(discordId);
        if (info == null) {
            return false;
        }

        // Check if expired
        if (info.isExpired()) {
            removePunishment(discordId);
            return false;
        }

        return true;
    }
}
