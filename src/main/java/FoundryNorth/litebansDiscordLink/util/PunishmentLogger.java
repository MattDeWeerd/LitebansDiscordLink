package FoundryNorth.litebansDiscordLink.util;

import FoundryNorth.litebansDiscordLink.LitebansDiscordLink;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Handles logging of punishments to a file
 */
public class PunishmentLogger {

    private final LitebansDiscordLink plugin;
    private final File logFile;
    private final SimpleDateFormat dateFormat;
    private final boolean enabled;

    public PunishmentLogger(LitebansDiscordLink plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("logging.enabled", true);
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        if (enabled) {
            // Create logs directory if it doesn't exist
            File logsDir = new File(plugin.getDataFolder(), "logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            String fileName = plugin.getConfig().getString("logging.filename", "punishments.log");
            this.logFile = new File(logsDir, fileName);

            // Create log file if it doesn't exist
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                    writeToFile("=".repeat(80));
                    writeToFile("LitebansDiscordLink Punishment Log");
                    writeToFile("Log started at: " + dateFormat.format(new Date()));
                    writeToFile("=".repeat(80));
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to create punishment log file: " + e.getMessage());
                }
            }
        } else {
            this.logFile = null;
        }
    }

    /**
     * Log a punishment being applied
     */
    public void logPunishment(String minecraftName, String minecraftUuid, String discordId,
            String type, String reason, long duration) {
        if (!enabled)
            return;

        String timestamp = dateFormat.format(new Date());
        String durationStr = formatDuration(duration);

        StringBuilder log = new StringBuilder();
        log.append("\n").append(timestamp).append(" | PUNISHMENT APPLIED\n");
        log.append("  Type: ").append(type).append("\n");
        log.append("  Player: ").append(minecraftName).append(" (").append(minecraftUuid).append(")\n");
        log.append("  Discord ID: ").append(discordId != null ? discordId : "Not linked").append("\n");
        log.append("  Reason: ").append(reason != null ? reason : "No reason provided").append("\n");
        log.append("  Duration: ").append(durationStr).append("\n");

        writeToFile(log.toString());
    }

    /**
     * Log a punishment being removed
     */
    public void logRemoval(String minecraftName, String minecraftUuid, String discordId, String type) {
        if (!enabled)
            return;

        String timestamp = dateFormat.format(new Date());

        StringBuilder log = new StringBuilder();
        log.append("\n").append(timestamp).append(" | PUNISHMENT REMOVED\n");
        log.append("  Type: ").append(type).append("\n");
        log.append("  Player: ").append(minecraftName).append(" (").append(minecraftUuid).append(")\n");
        log.append("  Discord ID: ").append(discordId != null ? discordId : "Not linked").append("\n");

        writeToFile(log.toString());
    }

    /**
     * Log a punishment expiring automatically
     */
    public void logExpiry(String discordId, String type, String minecraftName) {
        if (!enabled)
            return;

        String timestamp = dateFormat.format(new Date());

        StringBuilder log = new StringBuilder();
        log.append("\n").append(timestamp).append(" | PUNISHMENT EXPIRED\n");
        log.append("  Type: ").append(type).append("\n");
        log.append("  Player: ").append(minecraftName != null ? minecraftName : "Unknown").append("\n");
        log.append("  Discord ID: ").append(discordId).append("\n");

        writeToFile(log.toString());
    }

    /**
     * Log a Discord enforcement action
     */
    public void logDiscordAction(String discordId, String action, String reason) {
        if (!enabled)
            return;

        String timestamp = dateFormat.format(new Date());

        StringBuilder log = new StringBuilder();
        log.append("\n").append(timestamp).append(" | DISCORD ACTION\n");
        log.append("  Action: ").append(action).append("\n");
        log.append("  Discord ID: ").append(discordId).append("\n");
        log.append("  Reason: ").append(reason).append("\n");

        writeToFile(log.toString());
    }

    /**
     * Format duration in milliseconds to human readable string
     */
    private String formatDuration(long duration) {
        if (duration == -1) {
            return "Permanent";
        }

        long seconds = duration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "");
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "");
        }
    }

    /**
     * Write content to the log file
     */
    private void writeToFile(String content) {
        if (logFile == null)
            return;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(content);
            if (!content.endsWith("\n")) {
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write to punishment log: " + e.getMessage());
        }
    }
}
