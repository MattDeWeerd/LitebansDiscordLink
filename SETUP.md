# Quick Setup Guide

## 1. Create the Muted Role in Discord

1. Go to Server Settings → Roles
2. Create a new role named "Minecraft Muted" (or any name you prefer)
3. Choose a color (optional, e.g., dark gray)
4. Save the role

## 2. Configure Channel Permissions

For **EACH** channel where DiscordSRV is active:

### Text Channels:

1. Go to channel settings → Permissions
2. Add the "Minecraft Muted" role
3. **Deny** these permissions:
   - ❌ Send Messages
   - ❌ Send Messages in Threads
   - ❌ Create Public Threads
   - ❌ Create Private Threads
   - ❌ Add Reactions (optional)
4. Save

### Voice Channels:

1. Go to channel settings → Permissions
2. Add the "Minecraft Muted" role
3. **Deny** these permissions:
   - ❌ Speak
   - ❌ Use Voice Activity
   - ❌ Start Activities (optional)
4. Save

**Tip:** You can set these permissions on a **category** and they'll apply to all channels within it!

## 3. Get the Role ID

1. Enable Developer Mode:
   - User Settings → App Settings → Advanced → **Developer Mode** (toggle ON)
2. Go back to Server Settings → Roles
3. Right-click the "Minecraft Muted" role
4. Click **Copy ID**
5. Save this ID for the next step

## 4. Configure the Plugin

1. Start your server (the plugin will generate config.yml)
2. Edit `plugins/LitebansDiscordLink/config.yml`
3. Paste the role ID:
   ```yaml
   muted-role-id: "1234567890123456789" # Replace with your role ID
   ```
4. Save the file
5. Reload or restart your server

## 5. Configure Database (Optional)

The plugin uses MySQL to persist punishment tracking across restarts.

1. Edit `plugins/LitebansDiscordLink/config.yml`
2. Update database settings:
   ```yaml
   database:
     host: "localhost" # Your MySQL host
     port: 3306
     database: "minecraft" # Can use same database as Litebans
     username: "root"
     password: "password"
   ```
3. The plugin will automatically create the required table

## 6. Configure Discord Channel Logging (Optional)

Send punishment logs to a Discord channel:

1. Create a log channel (e.g., #punishment-logs)
2. Get the channel ID:
   - Right-click the channel > **Copy ID**
3. Edit config.yml:
   ```yaml
   discord-log-channel:
     enabled: true
     channel-id: "1234567890123456789" # Your channel ID
   ```
4. Customize which messages to send:
   - `role-applied.enabled` - When punishment role is added
   - `role-removed.enabled` - When punishment is manually removed
   - `punishment-expired.enabled` - When punishment expires
5. Set any to `false` to disable that message type

## 7. Verify Bot Permissions

Make sure your Discord bot has these permissions:

✅ **Manage Roles** - To assign the muted role  
✅ **Manage Messages** - To delete messages from punished users  
✅ **Mute Members** - To apply server mute in voice channels  
✅ **Read Messages** - To see messages  
✅ **Send Messages** - To send warning and log messages  
✅ **Send Direct Messages** - To DM players about punishments (optional)

## 8. Test It!

1. Link a test account: `/discord link` in Minecraft
2. Ban the test account: `/ban <player> Test`
3. Check that the player receives a DM about their ban
4. Try to send a message in Discord with that account
5. You should see:
   - ✅ Message is deleted immediately
   - ✅ Warning message appears (then deletes after 10s)
   - ✅ Muted role is applied
   - ✅ User cannot type in any channel
   - ✅ Log message appears in your Discord log channel (if enabled)
6. Check `plugins/LitebansDiscordLink/logs/punishments.log` for file logs

## Troubleshooting

**Role hierarchy error?**

- Your bot's role must be **higher** than the muted role
- Go to Server Settings → Roles
- Drag your bot's role **above** the "Minecraft Muted" role

**Messages not deleting?**

- Check bot has "Manage Messages" permission
- Check player is linked via `/discord link`
- Enable `debug: true` in config to see console logs

**Role not applying?**

- Check the role ID is correct (must match exactly)
- Check bot has "Manage Roles" permission
- Verify bot's role is higher than muted role

**DMs not sending?**

- Player must allow DMs from server members
- Check bot has "Send Direct Messages" permission
- Check debug logs for DM failures

**Log channel messages not appearing?**

- Verify `discord-log-channel.enabled` is `true`
- Check the channel ID is correct
- Ensure specific message types are enabled (e.g., `role-applied.enabled: true`)
- Bot needs "Send Messages" permission in the log channel

**Database connection failed?**

- Verify MySQL is running
- Check database credentials in config.yml
- Ensure database exists (plugin creates table automatically)
- Check MySQL allows connections from your server IP

## Done! 🎉

Your plugin is now set up. When players get banned/muted in Minecraft, they'll automatically be prevented from chatting in Discord too!
