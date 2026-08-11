# Rogue Chests FC

Utilities to assist with managing the Rogue Chests Friends Chat.

## Features

- Displays each Friends Chat member's Thieving level directly in the member list.
- Highlights members below **84 Thieving**.
- Under-84 side panel with customizable font, colors, and background.
- One-click ignore list for trusted members.
- Persistent banned player list with **BAN** indicator in the Friends Chat.
- Automatically captures nearby non-FC players at Rogue's Castle for quick review.
- Copy, clear, and promote captured players directly to the ban list.
- Automatically sorts the Friends Chat with banned players first, followed by members below **84 Thieving**.
- Tracks how long Friends Chat members remain within render distance at Rogue's Castle.
- Includes an overtime tracker with configurable time limits, notifications, and an overtime whitelist for excluding regular campers.
- Detects unranked free-to-play FC members with configurable warnings and highlights.
- Warns when nearby Friends Chat members are missing a configurable number of visible equipment slots, including support for two-handed weapons and an ignore list.

## Staff Access

Certain staff-only features are protected behind a one-time passcode.

- Authorized users unlock the plugin once per RuneLite installation.
- Authorization is stored locally and persists between sessions.
- Plugin functionality remains disabled until the correct passcode has been entered.
- Future passcode rotations can invalidate previous authorizations when required.

### Party Plugin Integration (Staff Only)

Once the plugin has been authorized with the staff passcode, it provides quick access to the configured RuneLite Party.

- Prompts authorized users on login to join the Party.
- Joining the Party always requires a manual **Join** button press.
- Users can select **Not now** to dismiss the login prompt.
- A top-center warning reminds users when the Party prompt is awaiting a response.
- **Join Party** and **Leave Party** controls are available from the plugin sidebar.
- The Party passphrase is handled internally, so authorized users do not need to manually enter it.

> **Important:** Party integration is only available after the plugin has been unlocked with the staff passcode. The plugin does **not** automatically join or leave a Party; all Party membership changes require explicit user interaction.

## Under-84 Panel

An optional overlay displays current Friends Chat members with less than **84 Thieving**.

Panel customization includes:

- RuneScape default or Arial font
- Adjustable font size
- Custom font color
- Custom background color and transparency
- Automatic sizing based on the selected font

Players who leave the Friends Chat remain in the panel in italics for one minute before being removed automatically.

## Sidebar

The plugin sidebar provides quick management tools for Friends Chat moderation:

- **Under-84 Ignore List** for players who should be excluded from the under-84 overlay and join notifications.
- **Banned Players** list for players marked with a red **BAN** label and excluded from Hiscore lookups.
- **Nearby Outsiders** list that automatically captures nearby non-Friends Chat players while at Rogue's Castle.
- One-click copy of player names to the clipboard.
- Add captured players directly to the banned players list.
- Remove individual players or clear captured lists as needed.
- **Clear All** and **Search** tools for managing large banned player lists.