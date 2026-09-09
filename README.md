# Rogue Chests FC

Utilities to assist with managing the Rogue Chests Friends Chat.

## Features

- Displays Friends Chat members' Thieving levels directly in the Friends Chat list.
- Highlights members below 84 Thieving and provides an optional Under 84 overlay.
- Identifies F2P members and provides configurable join notifications.
- Tracks nearby non-FC players around the Rogue's Castle.
- Provides overtime tracking and whitelist tools.
- Includes equipment checks and nearby player/FC counters.
- Syncs staff-managed Under 84 and banned-player lists.
- Provides separate Staff and Thiever modes based on Rogue Chests Friends Chat access.
- Includes a lightweight tool that flags potentially suspicious Friends Chat activity for staff review.

## Access

The plugin is intended for members of the **Rogue Chests** Friends Chat.

**Thiever Mode** provides the general tools used by FC members while thieving.

**Staff Mode** is automatically available to ranked Rogue Chests members and provides additional moderation and coordination tools.

No password or separate plugin login is required.

## Staff and Thiever Modes

### Staff Mode

Staff Mode provides the full set of moderation and Friends Chat tools available to ranked staff.

- Friends Chat moderation and player intelligence tools.
- Equipment warnings and nearby player information.
- Staff-managed list synchronization.
- RuneLite Party controls and Party credential synchronization.
- Suspicious Friends Chat activity tracking.

### Thiever Mode

Thiever Mode provides a limited set of tools useful to regular thievers.

- **Nearby Outsiders** tracking.
- **Overtime Tracking** and the **Overtime Whitelist**.
- Party tile ping beams.
- Party membership reminder with instructions to request the Party passphrase from a staff member.

Staff moderation tools and access to the internally configured Party passphrase remain unavailable in Thiever Mode.

## Party Plugin Integration

### Staff Mode

Staff Mode users receive quick access to the configured RuneLite Party.

- Prompts staff on login to join the Party.
- Joining the Party always requires a manual **Join** button press.
- Users can select **Not now** to dismiss the login prompt.
- **Join Party** and **Leave Party** controls are available from the plugin sidebar.
- The Party passphrase is handled internally, so staff do not need to manually enter it.

### Thiever Mode

Thiever Mode does not provide access to the stored Party passphrase or Staff Party controls.

When not currently in a Party, an optional on-screen reminder directs the user to request the Party passphrase from a staff member in public chat.

The Party reminder can be disabled from the plugin configuration.

### Party Ping Beams

Party tile pings can optionally display a tall red beam above the pinged tile, making pings easier to locate quickly.

- Available in both Staff and Thiever modes.
- Appears alongside the normal RuneLite Party tile ping.
- Automatically disappears with the ping.
- Can be enabled or disabled from the plugin configuration.

> **Important:** The plugin does **not** automatically join or leave a Party. All Party membership changes require explicit user interaction. Access to the internally configured Party passphrase remains restricted to Staff Mode users.

## Under-84 Panel

An optional overlay displays current Friends Chat members with less than **84 Thieving** while using Staff Mode.

Panel customization includes:

- RuneScape default or Arial font
- Adjustable font size
- Custom font color
- Custom background color and transparency
- Automatic sizing based on the selected font

Players who leave the Friends Chat remain in the panel in italics for one minute before being removed automatically.

## Sidebar

When first opening the sidebar, users can choose between **Staff Mode** and **Thiever Mode** and can switch modes later.

### Staff Mode Sidebar

The full Staff sidebar provides access to moderation, player intelligence, Party coordination, synchronized staff data, and other staff tools.

### Thiever Mode Sidebar

The simplified Thiever sidebar provides:

- **Nearby Outsiders** tracking and management.
- **Overtime Whitelist** management.
- Party ping support.
- No access to Staff moderation lists or credentials.