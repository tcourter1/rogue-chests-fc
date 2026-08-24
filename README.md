# Rogue Chests FC

Utilities to assist with managing the Rogue Chests Friends Chat.

## Features

- Displays each Friends Chat member's Thieving level directly in the member list.
- Highlights members below **84 Thieving**.
- Under-84 side panel with customizable font, colors, and background.
- One-click ignore list for trusted members.
- Persistent banned player list with **BAN** indicator in the Friends Chat.
- Automatically captures nearby non-FC players throughout the supported Rogue Chests tracking area.
- Copy, clear, and promote captured players directly to the ban list.
- Automatically sorts the Friends Chat with banned players first, followed by members below **84 Thieving**.
- Tracks how long Friends Chat members remain within render distance throughout the supported tracking area.
- Includes an overtime tracker with configurable time limits, notifications, and an overtime whitelist for excluding regular campers.
- Detects unranked free-to-play FC members with configurable warnings and highlights.
- Warns when nearby Friends Chat members are missing a configurable number of visible equipment slots, including support for two-handed weapons and an ignore list.
- Adds an optional red loot-style beam to Party plugin tile pings for improved visibility.

## Staff and Thiever Modes

The sidebar can operate in two different modes.

### Staff Mode

Staff Mode provides access to the full set of moderation and Friends Chat tools and is protected by a one-time passcode.

- Authorized users unlock Staff Mode once per RuneLite installation.
- Authorization is stored locally and persists between sessions.
- Staff-only functionality remains unavailable until the correct passcode has been entered.
- Future passcode rotations can invalidate previous authorizations when required.
- Includes Friends Chat moderation tools, player intelligence, equipment warnings, and Party controls.

### Thiever Mode

Thiever Mode does not require the staff passcode and provides a limited set of tools useful to regular thievers.

- **Nearby Outsiders** tracking.
- **Overtime Tracking** and the **Overtime Whitelist**.
- Party tile ping beams.
- Party membership reminder with instructions to request the Party passphrase from a staff member.

Staff moderation tools and automated access to the configured Party passphrase remain unavailable in Thiever Mode.

## Party Plugin Integration

### Staff Mode

Authorized Staff Mode users receive quick access to the configured RuneLite Party.

- Prompts authorized users on login to join the Party.
- Joining the Party always requires a manual **Join** button press.
- Users can select **Not now** to dismiss the login prompt.
- **Join Party** and **Leave Party** controls are available from the plugin sidebar.
- The Party passphrase is handled internally, so authorized users do not need to manually enter it.

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

> **Important:** The plugin does **not** automatically join or leave a Party. All Party membership changes require explicit user interaction. Access to the internally configured Party passphrase remains restricted to authorized Staff Mode users.

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

The full Staff sidebar provides:

- Staff-only features.

### Thiever Mode Sidebar

The simplified Thiever sidebar provides:

- **Nearby Outsiders** tracking and management.
- **Overtime Whitelist** management.
- No access to Staff moderation lists
