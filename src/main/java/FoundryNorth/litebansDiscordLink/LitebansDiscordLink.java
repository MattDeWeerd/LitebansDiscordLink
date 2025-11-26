package FoundryNorth.litebansDiscordLink;

import FoundryNorth.litebansDiscordLink.database.DatabaseManager;
import FoundryNorth.litebansDiscordLink.database.PunishmentTracker;
import FoundryNorth.litebansDiscordLink.discord.DiscordManager;
import FoundryNorth.litebansDiscordLink.listener.LitebansListener;
import FoundryNorth.litebansDiscordLink.util.PunishmentLogger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;

public final class LitebansDiscordLink extends JavaPlugin {

    private DatabaseManager database;
    private PunishmentTracker tracker;
    private DiscordManager discordManager;
    private LitebansListener litebansListener;
    private PunishmentLogger punishmentLogger;
    private boolean debug;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        debug = getConfig().getBoolean("debug", false);

        // Initialize database
        database = new DatabaseManager(this);
        try {
            database.initialize();
        } catch (SQLException e) {
            getLogger().severe("Failed to connect to database! Plugin will be disabled.");
            getLogger().severe("Error: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize punishment tracker
        tracker = new PunishmentTracker(database);
        getLogger().info("Punishment tracker initialized");

        // Initialize punishment logger
        punishmentLogger = new PunishmentLogger(this);
        if (getConfig().getBoolean("logging.enabled", true)) {
            getLogger().info(
                    "Punishment logging enabled: logs/" + getConfig().getString("logging.filename", "punishments.log"));
        }

        // Initialize Discord manager
        discordManager = new DiscordManager(this, tracker);

        // Wait a bit for DiscordSRV to fully load, then initialize
        long initDelay = getConfig().getLong("discordsrv-init-delay", 2) * 20L; // Convert seconds to ticks
        Bukkit.getScheduler().runTaskLater(this, () -> {
            discordManager.initialize();
        }, initDelay);

        // Register Litebans listener
        litebansListener = new LitebansListener(this);
        litebansListener.register();

        // Start punishment expiry checker
        long expiryCheckInterval = getConfig().getLong("expiry-check-interval", 5) * 60 * 20L; // Convert minutes to
                                                                                               // ticks
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            // Get expired punishments before removing them
            Map<String, PunishmentTracker.PunishmentInfo> expired = tracker.getExpiredPunishments();

            // Remove Discord roles and mutes for expired punishments
            if (!expired.isEmpty()) {
                for (Map.Entry<String, PunishmentTracker.PunishmentInfo> entry : expired.entrySet()) {
                    String discordId = entry.getKey();
                    PunishmentTracker.PunishmentInfo info = entry.getValue();

                    // Log expiry
                    punishmentLogger.logExpiry(discordId, info.getType(), info.getMinecraftName());

                    // Remove Discord enforcement
                    Bukkit.getScheduler().runTask(this, () -> {
                        discordManager.removeDiscordEnforcement(discordId, info);
                    });
                }
            }

            // Clean from database
            int removed = tracker.cleanExpired();
            if (removed > 0 && debug) {
                getLogger().info("Cleaned " + removed + " expired punishment(s)");
            }
        }, expiryCheckInterval, expiryCheckInterval);

        getLogger().info("LitebansDiscordLink enabled successfully!");
        getLogger().info("Expiry check interval: " + getConfig().getLong("expiry-check-interval", 5) + " minutes");

        // Log configuration
        String roleId = getConfig().getString("muted-role-id", "0");
        if (roleId.equals("0") || roleId.isEmpty()) {
            getLogger().warning("No muted role configured! Only message deletion will be used.");
            getLogger().warning("Set 'muted-role-id' in config.yml to enable role-based enforcement.");
        } else {
            getLogger().info("Muted role ID: " + roleId);
        }

        if (getConfig().getBoolean("apply-server-mute", true)) {
            getLogger().info("Server mute enforcement: ENABLED");
        } else {
            getLogger().info("Server mute enforcement: DISABLED");
        }
    }

    @Override
    public void onDisable() {
        // Unregister Litebans listener
        if (litebansListener != null) {
            litebansListener.unregister();
        }

        // Unregister Discord listener
        if (discordManager != null) {
            discordManager.unregister();
        }

        // Cancel all tasks
        Bukkit.getScheduler().cancelTasks(this);

        // Close database connection
        if (database != null) {
            database.close();
        }

        getLogger().info("LitebansDiscordLink disabled");
    }

    public PunishmentTracker getTracker() {
        return tracker;
    }

    public DiscordManager getDiscordManager() {
        return discordManager;
    }

    public PunishmentLogger getPunishmentLogger() {
        return punishmentLogger;
    }

    public boolean isDebug() {
        return debug;
    }
}
