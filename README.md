# LitebansDiscordLink

A Minecraft plugin that synchronizes Litebans punishments to Discord, preventing banned/muted players from chatting on your Discord server.

## Features

✅ **Multi-layered Enforcement**

- Discord role assignment for muted/banned players
- Message deletion for punished users
- Server mute to prevent voice chat
- Persistent tracking by Discord ID (survives Discord leaves/rejoins)

✅ **Automatic Synchronization**

- Bans, mutes, and warns are automatically synced to Discord
- Punishments are automatically removed when lifted
- Expired punishments are cleaned up automatically (configurable interval)

✅ **Player Notifications**

- Automatic DM notifications when players receive punishments
- Separate notifications for bans, mutes, and warnings
- Fully customizable multiline messages

✅ **Comprehensive Logging**

- File logging for all punishment actions
- Optional Discord channel logging with configurable messages
- Individual enable/disable for each log message type
- Detailed tracking of role applications, removals, and expirations

✅ **Highly Configurable**

- Customizable warning messages for each punishment type
- Adjustable expiry check intervals
- Configurable DiscordSRV initialization delay
- Messages auto-delete after 10 seconds
- Placeholders for reason, time remaining, player name, and more

✅ **Bypass-Proof**

- Tracks punished users by Discord ID
- Automatically reapplies role if they leave and rejoin Discord
- Secondary enforcement via message deletion
- MySQL persistence across server restarts

## Requirements

- Paper/Spigot 1.21+ (or compatible)
- [Litebans](https://www.spigotmc.org/resources/litebans.3715/)
- [DiscordSRV](https://www.spigotmc.org/resources/discordsrv.18494/)
- Java 21+

## Installation

1. Install Litebans and DiscordSRV on your server
2. Download `LitebansDiscordLink.jar`
3. Place in your `plugins/` folder
4. Restart your server
5. Configure the plugin (see Configuration below)

## Configuration

After first run, edit `plugins/LitebansDiscordLink/config.yml`:

### Database Settings

```yaml
database:
  host: "localhost"
  port: 3306
  database: "minecraft"
  username: "root"
  password: "password"
```

### Discord Enforcement

```yaml
# The ID of the Discord role to apply to muted/banned players
muted-role-id: "0"

# Whether to apply Discord server mute to punished players (prevents voice chat)
apply-server-mute: true
```

### Timing Settings

```yaml
# How often to check for expired punishments (in minutes)
expiry-check-interval: 5

# Delay before initializing DiscordSRV integration (in seconds)
discordsrv-init-delay: 2
```

### Message Settings

```yaml
# Messages sent to punished players when they try to chat (multiline format)
messages:
  banned:
    - "⛔ You cannot send messages in this Discord server because you are banned on the Minecraft server."
    - "**Reason:** {reason}"
    - "**Time Remaining:** {time}"
  muted:
    - "🔇 You cannot send messages in this Discord server because you are muted on the Minecraft server."
    - "**Reason:** {reason}"
    - "**Time Remaining:** {time}"

# Private messages sent to players when they receive a punishment
notification-messages:
  ban:
    - "⛔ **You have been banned from the Minecraft server**"
    - "**Reason:** {reason}"
    - "**Duration:** {time}"
    - ""
    - "You will also be unable to chat in the Discord server while banned."
  mute:
    - "🔇 **You have been muted on the Minecraft server**"
    - "**Reason:** {reason}"
    - "**Duration:** {time}"
  warn:
    - "⚠️ **You have been warned on the Minecraft server**"
    - "**Reason:** {reason}"
```

### Logging Settings

```yaml
# File logging
logging:
  enabled: true
  filename: "punishments.log"

# Discord channel logging
discord-log-channel:
  enabled: false
  channel-id: "0"
  messages:
    role-applied:
      enabled: true
      message:
        - "🔒 Applied the {punished-role} to {discord-user}"
        - "**Player:** {player}"
        - "**Type:** {type}"
        - "**Reason:** {reason}"
        - "**Duration:** {duration}"
    role-removed:
      enabled: true
      message:
        - "🔓 Removed the {punished-role} from {discord-user}"
        - "**Player:** {player}"
        - "**Type:** {type}"
    punishment-expired:
      enabled: true
      message:
        - "⏰ Punishment expired for {discord-user}"
        - "**Player:** {player}"
        - "**Type:** {type}"
```

### Setting Up the Muted Role

1. Create a Discord role (e.g., "Minecraft Muted")
2. Configure role permissions in each channel:
   - Deny "Send Messages", "Speak", and thread permissions
3. Get the role ID:
   - Enable Developer Mode in Discord settings
   - Right-click the role > Copy ID
4. Paste the ID into `muted-role-id` in config.yml
5. Restart the server

**Important:** Your bot's role must be higher than the muted role in Discord's role hierarchy!

## How It Works

1. **Player gets banned/muted** → Plugin tracks their Discord ID
2. **Player tries to chat on Discord** → Message deleted + warning sent
3. **Player leaves and rejoins** → Role and mute reapplied automatically
4. **Punishment lifted** → All restrictions removed

## Enforcement Layers

- **Muted Role** - Denies permissions in channels
- **Message Deletion** - Removes messages instantly
- **Server Mute** - Prevents voice chat
- **Persistent Tracking** - Survives Discord leaves/rejoins

## Troubleshooting

- **Role not assigned:** Bot role must be higher than muted role
- **Messages not deleted:** Bot needs "Manage Messages" permission
- **Server mute not working:** Bot needs "Mute Members" permission
- **Not enforcing:** Player must link with `/discord link`

## Building

```bash
mvn clean package
```

Output: `target/litebansdiscordlink-1.0.jar`

---

Created by Matt546
