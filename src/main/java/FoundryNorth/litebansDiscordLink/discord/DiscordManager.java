package FoundryNorth.litebansDiscordLink.discord;

import FoundryNorth.litebansDiscordLink.LitebansDiscordLink;
import FoundryNorth.litebansDiscordLink.database.PunishmentTracker;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.AccountLinkedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.*;
import github.scarsz.discordsrv.dependencies.jda.api.events.guild.member.GuildMemberJoinEvent;
import github.scarsz.discordsrv.dependencies.jda.api.events.message.MessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import litebans.api.Database;
import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * Handles DiscordSRV events and Discord user management
 */
public class DiscordManager extends ListenerAdapter {

    private final LitebansDiscordLink plugin;
    private final PunishmentTracker tracker;

    public DiscordManager(LitebansDiscordLink plugin, PunishmentTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    /**
     * Initialize the Discord manager and register with DiscordSRV
     */
    public void initialize() {
        DiscordSRV discordSRV = DiscordSRV.getPlugin();
        if (discordSRV == null) {
            plugin.getLogger().severe("DiscordSRV not found! Cannot register Discord listeners.");
            return;
        }

        // Wait for DiscordSRV to be ready
        if (discordSRV.getJda() == null) {
            plugin.getLogger().info("Waiting for DiscordSRV to be ready...");
            Bukkit.getScheduler().runTaskLater(plugin, this::initialize, 20L); // Try again in 1 second
            return;
        }

        // Register our listener with JDA
        discordSRV.getJda().addEventListener(this);

        // Register DiscordSRV API listener for account linking events
        DiscordSRV.api.subscribe(this);

        plugin.getLogger().info("Discord manager initialized successfully!");
    }

    /**
     * Unregister Discord event listeners
     */
    public void unregister() {
        DiscordSRV discordSRV = DiscordSRV.getPlugin();
        if (discordSRV != null && discordSRV.getJda() != null) {
            discordSRV.getJda().removeEventListener(this);
        }

        // Unregister DiscordSRV API listener
        DiscordSRV.api.unsubscribe(this);

        plugin.getLogger().info("Discord manager unregistered");
    }

    /**
     * Handle a new punishment from Litebans
     */
    public void handlePunishment(String minecraftUuid, String minecraftName, String type, String reason,
            long duration) {
        UUID uuid;
        try {
            uuid = UUID.fromString(minecraftUuid);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid UUID: " + minecraftUuid);
            return;
        }

        // Get Discord ID from DiscordSRV linking
        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(uuid);

        if (discordId == null) {
            if (plugin.isDebug()) {
                plugin.getLogger()
                        .info("Player " + minecraftName + " is not linked to Discord, skipping punishment sync");
            }
            return;
        }

        // Calculate expiry time (-1 for permanent)
        long expiryTime = duration == -1 ? -1 : System.currentTimeMillis() + duration;

        // Add to tracker
        PunishmentTracker.PunishmentInfo info = new PunishmentTracker.PunishmentInfo(
                uuid, minecraftName, type, reason, expiryTime);
        tracker.addPunishment(discordId, info);

        // Log punishment
        plugin.getPunishmentLogger().logPunishment(minecraftName, minecraftUuid, discordId, type, reason, duration);

        if (plugin.isDebug()) {
            plugin.getLogger()
                    .info("Tracked punishment: " + minecraftName + " (Discord: " + discordId + ") | Type: " + type);
        }

        // Send DM notification
        sendPunishmentNotification(discordId, type, reason, expiryTime, minecraftName);

        // Apply Discord enforcement
        applyDiscordPunishment(discordId, info, minecraftName, reason, duration);
    }

    /**
     * Handle punishment removal from Litebans
     */
    public void handleUnpunishment(String minecraftUuid, String type) {
        UUID uuid;
        try {
            uuid = UUID.fromString(minecraftUuid);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid UUID: " + minecraftUuid);
            return;
        }

        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(uuid);

        if (discordId == null) {
            return;
        }

        PunishmentTracker.PunishmentInfo info = tracker.getPunishment(discordId);
        if (info != null && info.getType().equals(type)) {
            // Log removal
            plugin.getPunishmentLogger().logRemoval(info.getMinecraftName(), uuid.toString(), discordId, type);

            tracker.removePunishment(discordId);

            if (plugin.isDebug()) {
                plugin.getLogger().info("Removed punishment tracking for Discord ID: " + discordId);
            }

            // Remove Discord enforcement
            removeDiscordPunishment(discordId, info);
        }
    }

    /**
     * Apply Discord punishments (role + server mute)
     */
    private void applyDiscordPunishment(String discordId, PunishmentTracker.PunishmentInfo info,
            String minecraftName, String reason, long duration) {
        Guild guild = getMainGuild();
        if (guild == null)
            return;

        guild.retrieveMemberById(discordId).queue(member -> {
            // Apply muted role if configured
            String roleId = plugin.getConfig().getString("muted-role-id", "0");
            if (!roleId.equals("0") && !roleId.isEmpty()) {
                Role mutedRole = guild.getRoleById(roleId);
                if (mutedRole != null) {
                    guild.addRoleToMember(member, mutedRole).queue(
                            success -> {
                                plugin.getPunishmentLogger().logDiscordAction(discordId, "Role Applied",
                                        "Added muted role to " + member.getEffectiveName());
                                // Send Discord channel log
                                sendLogChannelMessage("role-applied", member, mutedRole, minecraftName,
                                        info.getType(), reason, duration);
                                if (plugin.isDebug()) {
                                    plugin.getLogger().info("Applied muted role to " + member.getEffectiveName());
                                }
                            },
                            error -> plugin.getLogger().warning("Failed to apply muted role: " + error.getMessage()));
                } else {
                    plugin.getLogger().warning("Muted role not found with ID: " + roleId);
                }
            }

            // Apply server mute if configured
            if (plugin.getConfig().getBoolean("apply-server-mute", true)) {
                if (!member.getVoiceState().isMuted()) {
                    member.mute(true).queue(
                            success -> {
                                plugin.getPunishmentLogger().logDiscordAction(discordId, "Server Mute Applied",
                                        "Applied server mute to " + member.getEffectiveName());
                                if (plugin.isDebug()) {
                                    plugin.getLogger().info("Applied server mute to " + member.getEffectiveName());
                                }
                            },
                            error -> plugin.getLogger().warning("Failed to apply server mute: " + error.getMessage()));
                }
            }
        }, error -> {
            if (plugin.isDebug()) {
                plugin.getLogger().info("User not in Discord server: " + discordId);
            }
        });
    }

    /**
     * Remove Discord punishments (role + server mute)
     */
    private void removeDiscordPunishment(String discordId, PunishmentTracker.PunishmentInfo info) {
        Guild guild = getMainGuild();
        if (guild == null)
            return;

        guild.retrieveMemberById(discordId).queue(member -> {
            // Remove muted role if configured
            String roleId = plugin.getConfig().getString("muted-role-id", "0");
            if (!roleId.equals("0") && !roleId.isEmpty()) {
                Role mutedRole = guild.getRoleById(roleId);
                if (mutedRole != null && member.getRoles().contains(mutedRole)) {
                    guild.removeRoleFromMember(member, mutedRole).queue(
                            success -> {
                                // Send Discord channel log
                                sendLogChannelMessage("role-removed", member, mutedRole, info.getMinecraftName(),
                                        info.getType(), null, -1);
                                if (plugin.isDebug()) {
                                    plugin.getLogger().info("Removed muted role from " + member.getEffectiveName());
                                }
                            },
                            error -> plugin.getLogger().warning("Failed to remove muted role: " + error.getMessage()));
                }
            }

            // Remove server mute if configured
            if (plugin.getConfig().getBoolean("apply-server-mute", true)) {
                if (member.getVoiceState().isMuted()) {
                    member.mute(false).queue(
                            success -> {
                                if (plugin.isDebug()) {
                                    plugin.getLogger().info("Removed server mute from " + member.getEffectiveName());
                                }
                            },
                            error -> plugin.getLogger().warning("Failed to remove server mute: " + error.getMessage()));
                }
            }
        }, error -> {
            if (plugin.isDebug()) {
                plugin.getLogger().info("User not in Discord server: " + discordId);
            }
        });
    }

    /**
     * Public method to remove Discord enforcement (for expired punishments)
     */
    public void removeDiscordEnforcement(String discordId, PunishmentTracker.PunishmentInfo info) {
        removeDiscordPunishmentExpired(discordId, info);
    }

    /**
     * Remove Discord punishments for expired punishments (with different log
     * message)
     */
    private void removeDiscordPunishmentExpired(String discordId, PunishmentTracker.PunishmentInfo info) {
        Guild guild = getMainGuild();
        if (guild == null)
            return;

        guild.retrieveMemberById(discordId).queue(member -> {
            // Remove muted role if configured
            String roleId = plugin.getConfig().getString("muted-role-id", "0");
            if (!roleId.equals("0") && !roleId.isEmpty()) {
                Role mutedRole = guild.getRoleById(roleId);
                if (mutedRole != null && member.getRoles().contains(mutedRole)) {
                    guild.removeRoleFromMember(member, mutedRole).queue(
                            success -> {
                                // Send Discord channel log for expiry
                                sendLogChannelMessage("punishment-expired", member, mutedRole, info.getMinecraftName(),
                                        info.getType(), null, -1);
                                if (plugin.isDebug()) {
                                    plugin.getLogger().info(
                                            "Removed muted role from " + member.getEffectiveName() + " (expired)");
                                }
                            },
                            error -> plugin.getLogger().warning("Failed to remove muted role: " + error.getMessage()));
                }
            }

            // Remove server mute if configured
            if (plugin.getConfig().getBoolean("apply-server-mute", true)) {
                if (member.getVoiceState().isMuted()) {
                    member.mute(false).queue(
                            success -> {
                                if (plugin.isDebug()) {
                                    plugin.getLogger().info(
                                            "Removed server mute from " + member.getEffectiveName() + " (expired)");
                                }
                            },
                            error -> plugin.getLogger().warning("Failed to remove server mute: " + error.getMessage()));
                }
            }
        }, error -> {
            if (plugin.isDebug()) {
                plugin.getLogger().info("User not in Discord server: " + discordId);
            }
        });
    }

    /**
     * Listen for messages and delete/warn if user is punished
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bots and DMs
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }

        String discordId = event.getAuthor().getId();
        PunishmentTracker.PunishmentInfo punishment = tracker.getPunishment(discordId);

        if (punishment == null) {
            return; // Not punished
        }

        // Delete the message
        event.getMessage().delete().queue(
                success -> {
                    if (plugin.isDebug()) {
                        plugin.getLogger().info("Deleted message from punished user: " + event.getAuthor().getAsTag());
                    }

                    // Send ephemeral warning
                    sendPunishmentWarning(event, punishment);
                },
                error -> {
                    plugin.getLogger().warning("Failed to delete message: " + error.getMessage());
                });
    }

    /**
     * Listen for members joining and reapply punishments
     */
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        String discordId = event.getUser().getId();
        PunishmentTracker.PunishmentInfo punishment = tracker.getPunishment(discordId);

        if (punishment != null) {
            if (plugin.isDebug()) {
                plugin.getLogger().info(
                        "Punished user rejoined Discord: " + event.getUser().getAsTag() + " - Reapplying punishment");
            }

            // Reapply punishment (without logging to channel since it's a rejoin)
            applyDiscordPunishment(discordId, punishment, punishment.getMinecraftName(),
                    punishment.getReason(), -1);
        }
    }

    /**
     * Send an ephemeral warning message to the user
     */
    private void sendPunishmentWarning(MessageReceivedEvent event, PunishmentTracker.PunishmentInfo punishment) {
        String messageKey = punishment.getType().toLowerCase();

        // Get message from config (could be String or List)
        String message;
        if (plugin.getConfig().isList("messages." + messageKey)) {
            message = String.join("\n", plugin.getConfig().getStringList("messages." + messageKey));
        } else {
            message = plugin.getConfig().getString("messages." + messageKey,
                    "⛔ You cannot send messages because you are punished on the Minecraft server.");
        }

        // Replace placeholders
        message = message.replace("{reason}",
                punishment.getReason() != null ? punishment.getReason() : "No reason provided");
        message = message.replace("{time}", punishment.getTimeRemainingFormatted());
        message = message.replace("{player}", punishment.getMinecraftName());

        // Send as a reply that mentions the user (since we can't do true ephemeral in
        // regular channels)
        event.getChannel().sendMessage(event.getAuthor().getAsMention() + " " + message)
                .queue(
                        sentMessage -> {
                            // Auto-delete after 10 seconds
                            sentMessage.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS,
                                    null,
                                    error -> {
                                    } // Ignore errors on auto-delete
                            );
                        },
                        error -> plugin.getLogger().warning("Failed to send warning message: " + error.getMessage()));
    }

    /**
     * Handle a warn notification (doesn't restrict Discord access)
     */
    public void handleWarn(String minecraftUuid, String minecraftName, String reason) {
        UUID uuid;
        try {
            uuid = UUID.fromString(minecraftUuid);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid UUID: " + minecraftUuid);
            return;
        }

        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(uuid);

        if (discordId == null) {
            if (plugin.isDebug()) {
                plugin.getLogger()
                        .info("Player " + minecraftName + " is not linked to Discord, skipping warn notification");
            }
            return;
        }

        // Send DM notification (warns don't expire, so we use -1)
        sendPunishmentNotification(discordId, "WARN", reason, -1, minecraftName);
    }

    /**
     * Send a private message to a user about their punishment
     */
    private void sendPunishmentNotification(String discordId, String type, String reason, long expiryTime,
            String minecraftName) {
        if (DiscordSRV.getPlugin().getJda() == null) {
            return;
        }

        DiscordSRV.getPlugin().getJda().retrieveUserById(discordId).queue(user -> {
            String messageKey = type.toLowerCase();

            // Get message from config (could be String or List)
            String messageTemplate;
            if (plugin.getConfig().isList("notification-messages." + messageKey)) {
                messageTemplate = String.join("\n",
                        plugin.getConfig().getStringList("notification-messages." + messageKey));
            } else {
                messageTemplate = plugin.getConfig().getString("notification-messages." + messageKey,
                        "You have received a punishment on the Minecraft server.");
            }

            // Replace placeholders
            String finalMessage = messageTemplate
                    .replace("{reason}", reason != null ? reason : "No reason provided")
                    .replace("{player}", minecraftName);

            if (expiryTime != -1) {
                long remaining = expiryTime - System.currentTimeMillis();
                finalMessage = finalMessage.replace("{time}", formatTime(remaining));
            } else {
                finalMessage = finalMessage.replace("{time}", type.equals("WARN") ? "" : "Permanent");
            }

            String messageToSend = finalMessage;

            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage(messageToSend).queue(
                        success -> {
                            if (plugin.isDebug()) {
                                plugin.getLogger().info("Sent " + type + " notification DM to " + user.getAsTag());
                            }
                        },
                        error -> {
                            if (plugin.isDebug()) {
                                plugin.getLogger()
                                        .warning("Failed to send DM to " + user.getAsTag() + ": " + error.getMessage());
                            }
                        });
            }, error -> {
                if (plugin.isDebug()) {
                    plugin.getLogger()
                            .warning("Failed to open DM channel with " + discordId + ": " + error.getMessage());
                }
            });
        }, error -> {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("Failed to retrieve user " + discordId + ": " + error.getMessage());
            }
        });
    }

    /**
     * Format time in milliseconds to human readable string
     */
    private String formatTime(long millis) {
        if (millis <= 0) {
            return "Expired";
        }

        long seconds = millis / 1000;
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
     * Send a log message to the configured Discord log channel
     */
    private void sendLogChannelMessage(String messageType, Member member, Role punishedRole,
            String minecraftName, String punishmentType, String reason, long duration) {
        // Check if Discord channel logging is enabled
        if (!plugin.getConfig().getBoolean("discord-log-channel.enabled", false)) {
            return;
        }

        // Check if this specific message type is enabled
        String enabledPath = "discord-log-channel.messages." + messageType + ".enabled";
        if (!plugin.getConfig().getBoolean(enabledPath, true)) {
            return;
        }

        String channelId = plugin.getConfig().getString("discord-log-channel.channel-id", "0");
        if (channelId.equals("0") || channelId.isEmpty()) {
            return;
        }

        Guild guild = getMainGuild();
        if (guild == null)
            return;

        // Get the log channel
        var channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("Discord log channel not found with ID: " + channelId);
            }
            return;
        }

        // Get message from config
        String configPath = "discord-log-channel.messages." + messageType + ".message";
        String message;
        if (plugin.getConfig().isList(configPath)) {
            message = String.join("\n", plugin.getConfig().getStringList(configPath));
        } else {
            message = plugin.getConfig().getString(configPath, "Punishment action occurred");
        }

        // Replace placeholders
        message = message.replace("{discord-user}", member.getAsMention())
                .replace("{discord-id}", member.getId())
                .replace("{player}", minecraftName)
                .replace("{punished-role}", punishedRole.getAsMention())
                .replace("{type}", punishmentType)
                .replace("{reason}", reason != null ? reason : "No reason provided");

        if (duration != -1) {
            message = message.replace("{duration}", formatTime(duration));
        } else {
            message = message.replace("{duration}", "Permanent");
        }

        // Send the message
        channel.sendMessage(message).queue(
                success -> {
                    if (plugin.isDebug()) {
                        plugin.getLogger().info("Sent log message to Discord channel");
                    }
                },
                error -> plugin.getLogger().warning("Failed to send log message to Discord: " + error.getMessage()));
    }

    /**
     * Listen for account linking events and check for existing punishments
     */
    @Subscribe
    public void onAccountLinked(AccountLinkedEvent event) {
        UUID minecraftUuid = event.getPlayer().getUniqueId();
        String discordId = event.getUser().getId();
        String playerName = event.getPlayer().getName();

        if (plugin.isDebug()) {
            plugin.getLogger()
                    .info("Account linked: " + playerName + " (" + minecraftUuid + ") -> Discord ID: " + discordId);
        }

        // Check for active punishments in LiteBans
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PunishmentInfo activePunishment = checkLiteBansForActivePunishment(minecraftUuid);

            if (activePunishment != null) {
                // Found an active punishment - sync it to Discord
                if (plugin.isDebug()) {
                    plugin.getLogger()
                            .info("Found active " + activePunishment.type + " for newly linked account: " + playerName);
                }

                // Calculate expiry time
                long expiryTime = activePunishment.expiryTimestamp == 0 ? -1 : activePunishment.expiryTimestamp;
                long duration = activePunishment.expiryTimestamp == 0 ? -1
                        : (activePunishment.expiryTimestamp - System.currentTimeMillis());

                // Add to tracker
                PunishmentTracker.PunishmentInfo info = new PunishmentTracker.PunishmentInfo(
                        minecraftUuid, playerName, activePunishment.type, activePunishment.reason, expiryTime);
                tracker.addPunishment(discordId, info);

                // Log retroactive application
                plugin.getPunishmentLogger().logPunishment(playerName, minecraftUuid.toString(), discordId,
                        activePunishment.type, activePunishment.reason, duration);
                plugin.getPunishmentLogger().logDiscordAction(discordId, "Retroactive Punishment",
                        "Applied existing " + activePunishment.type + " from LiteBans after account link");

                if (plugin.isDebug()) {
                    plugin.getLogger().info("Applied retroactive punishment for " + playerName);
                }

                // Send DM notification
                sendPunishmentNotification(discordId, activePunishment.type, activePunishment.reason, expiryTime,
                        playerName);

                // Apply Discord enforcement on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    applyDiscordPunishment(discordId, info, playerName, activePunishment.reason, duration);
                });
            } else if (plugin.isDebug()) {
                plugin.getLogger().info("No active punishments found for newly linked account: " + playerName);
            }
        });
    }

    /**
     * Check LiteBans database for any active ban or mute for a player
     */
    private PunishmentInfo checkLiteBansForActivePunishment(UUID uuid) {
        // Check for active ban (pass null for IP and server to check all)
        litebans.api.Entry ban = Database.get().getBan(uuid, null, null);
        if (ban != null) {
            return new PunishmentInfo(
                    "BAN",
                    ban.getReason(),
                    ban.getDateEnd() // 0 for permanent
            );
        }

        // Check for active mute (pass null for IP and server to check all)
        litebans.api.Entry mute = Database.get().getMute(uuid, null, null);
        if (mute != null) {
            return new PunishmentInfo(
                    "MUTE",
                    mute.getReason(),
                    mute.getDateEnd() // 0 for permanent
            );
        }

        return null;
    }

    /**
     * Helper class to hold punishment information from LiteBans
     */
    private static class PunishmentInfo {
        final String type;
        final String reason;
        final long expiryTimestamp; // 0 for permanent

        PunishmentInfo(String type, String reason, long expiryTimestamp) {
            this.type = type;
            this.reason = reason;
            this.expiryTimestamp = expiryTimestamp;
        }
    }

    /**
     * Get the main Discord guild
     */
    private Guild getMainGuild() {
        if (DiscordSRV.getPlugin().getMainGuild() == null) {
            plugin.getLogger().warning("Main guild not found in DiscordSRV!");
            return null;
        }
        return DiscordSRV.getPlugin().getMainGuild();
    }
}
