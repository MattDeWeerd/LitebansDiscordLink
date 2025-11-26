package FoundryNorth.litebansDiscordLink.listener;

import FoundryNorth.litebansDiscordLink.LitebansDiscordLink;
import litebans.api.Database;
import litebans.api.Entry;
import litebans.api.Events;
import java.util.UUID;

/**
 * Listens for Litebans punishment events using the Events API
 */
public class LitebansListener {

    private final LitebansDiscordLink plugin;
    private Events.Listener listener;

    public LitebansListener(LitebansDiscordLink plugin) {
        this.plugin = plugin;
    }

    /**
     * Register Litebans event listeners
     */
    public void register() {
        listener = new Events.Listener() {
            @Override
            public void entryAdded(Entry entry) {
                String uuid = entry.getUuid();
                if (uuid == null) {
                    return;
                }

                String type = entry.getType();
                UUID playerUuid = UUID.fromString(entry.getUuid());
                String playerName = Database.get().getPlayerName(playerUuid); // Name of the punished player
                String reason = entry.getReason();
                long durationEnd = entry.getDateEnd();

                switch (type) {
                    case "ban":
                        handleBan(uuid, playerName, reason, durationEnd);
                        break;
                    case "mute":
                        handleMute(uuid, playerName, reason, durationEnd);
                        break;
                    case "warn":
                        handleWarn(uuid, playerName, reason);
                        break;
                }
            }

            @Override
            public void entryRemoved(Entry entry) {
                String uuid = entry.getUuid();
                if (uuid == null) {
                    return;
                }

                String type = entry.getType();
                UUID playerUuid = UUID.fromString(entry.getUuid());
                String playerName = Database.get().getPlayerName(playerUuid); // Name of the punished player

                switch (type) {
                    case "ban":
                        handleUnban(uuid, playerName);
                        break;
                    case "mute":
                        handleUnmute(uuid, playerName);
                        break;
                }
            }
        };

        Events.get().register(listener);
        plugin.getLogger().info("Litebans event listener registered");
    }

    /**
     * Unregister Litebans event listeners
     */
    public void unregister() {
        if (listener != null) {
            Events.get().unregister(listener);
            plugin.getLogger().info("Litebans event listener unregistered");
        }
    }

    private void handleBan(String uuid, String playerName, String reason, long durationEnd) {
        // Calculate duration in milliseconds from now until end time
        // durationEnd is 0 for permanent bans, otherwise it's the timestamp when ban
        // expires
        long duration = (durationEnd == 0) ? -1 : (durationEnd - System.currentTimeMillis());

        plugin.getDiscordManager().handlePunishment(uuid, playerName, "BAN", reason, duration);

        if (plugin.isDebug()) {
            plugin.getLogger().info("Ban event: " + playerName + " | Duration: " + duration + " | Reason: " + reason);
        }
    }

    private void handleMute(String uuid, String playerName, String reason, long durationEnd) {
        // Calculate duration in milliseconds from now until end time
        long duration = (durationEnd == 0) ? -1 : (durationEnd - System.currentTimeMillis());

        plugin.getDiscordManager().handlePunishment(uuid, playerName, "MUTE", reason, duration);

        if (plugin.isDebug()) {
            plugin.getLogger().info("Mute event: " + playerName + " | Duration: " + duration + " | Reason: " + reason);
        }
    }

    private void handleUnban(String uuid, String playerName) {
        plugin.getDiscordManager().handleUnpunishment(uuid, "BAN");

        if (plugin.isDebug()) {
            plugin.getLogger().info("Unban event: " + playerName);
        }
    }

    private void handleUnmute(String uuid, String playerName) {
        plugin.getDiscordManager().handleUnpunishment(uuid, "MUTE");

        if (plugin.isDebug()) {
            plugin.getLogger().info("Unmute event: " + playerName);
        }
    }

    private void handleWarn(String uuid, String playerName, String reason) {
        // Warns don't restrict Discord access, just notify
        plugin.getDiscordManager().handleWarn(uuid, playerName, reason);

        if (plugin.isDebug()) {
            plugin.getLogger().info("Warn event: " + playerName + " | Reason: " + reason);
        }
    }
}
