package FoundryNorth.litebansDiscordLink.database;

import java.util.Map;
import java.util.UUID;

/**
 * Tracks active punishments by Discord user ID using MySQL database
 */
public class PunishmentTracker {

    private final DatabaseManager database;

    public PunishmentTracker(DatabaseManager database) {
        this.database = database;
    }

    /**
     * Add or update a punishment
     * 
     * @param discordId The Discord user ID
     * @param info      The punishment information
     */
    public void addPunishment(String discordId, PunishmentInfo info) {
        database.savePunishment(discordId, info);
    }

    /**
     * Remove a punishment
     * 
     * @param discordId The Discord user ID
     */
    public void removePunishment(String discordId) {
        database.removePunishment(discordId);
    }

    /**
     * Check if a Discord user is punished
     * 
     * @param discordId The Discord user ID
     * @return true if the user has an active punishment
     */
    public boolean isPunished(String discordId) {
        return database.isPunished(discordId);
    }

    /**
     * Get punishment info for a Discord user
     * 
     * @param discordId The Discord user ID
     * @return The punishment info, or null if not punished
     */
    public PunishmentInfo getPunishment(String discordId) {
        PunishmentInfo info = database.getPunishment(discordId);
        if (info != null && info.isExpired()) {
            database.removePunishment(discordId);
            return null;
        }
        return info;
    }

    /**
     * Get all expired punishments
     * 
     * @return Map of Discord ID to expired punishment info
     */
    public Map<String, PunishmentInfo> getExpiredPunishments() {
        return database.getExpiredPunishments();
    }

    /**
     * Remove all expired punishments
     * 
     * @return The number of punishments removed
     */
    public int cleanExpired() {
        return database.cleanExpired();
    }

    /**
     * Get all active punishments
     * 
     * @return Map of Discord ID to punishment info
     */
    public Map<String, PunishmentInfo> getAllPunishments() {
        return database.getAllPunishments();
    }

    /**
     * Information about a punishment
     */
    public static class PunishmentInfo {
        private final UUID minecraftUuid;
        private final String minecraftName;
        private final String type; // "BAN", "MUTE", "KICK"
        private final String reason;
        private final long expiryTime; // -1 for permanent
        private final long issuedTime;

        public PunishmentInfo(UUID minecraftUuid, String minecraftName, String type, String reason, long expiryTime) {
            this.minecraftUuid = minecraftUuid;
            this.minecraftName = minecraftName;
            this.type = type;
            this.reason = reason;
            this.expiryTime = expiryTime;
            this.issuedTime = System.currentTimeMillis();
        }

        public boolean isExpired() {
            if (expiryTime == -1) {
                return false; // Permanent
            }
            return System.currentTimeMillis() > expiryTime;
        }

        public boolean isPermanent() {
            return expiryTime == -1;
        }

        public long getTimeRemaining() {
            if (isPermanent()) {
                return -1;
            }
            return Math.max(0, expiryTime - System.currentTimeMillis());
        }

        public String getTimeRemainingFormatted() {
            if (isPermanent()) {
                return "Permanent";
            }

            long remaining = getTimeRemaining();
            if (remaining <= 0) {
                return "Expired";
            }

            long seconds = remaining / 1000;
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

        public UUID getMinecraftUuid() {
            return minecraftUuid;
        }

        public String getMinecraftName() {
            return minecraftName;
        }

        public String getType() {
            return type;
        }

        public String getReason() {
            return reason;
        }

        public long getExpiryTime() {
            return expiryTime;
        }

        public long getIssuedTime() {
            return issuedTime;
        }
    }
}
