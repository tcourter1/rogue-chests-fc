# Rogue Chests FC

A RuneLite plugin for members of the **Rogue Chests** Friends Chat.

## Features

- Displays each Friends Chat member's **Thieving level** next to their name
- Shows levels in green for players with **84+ Thieving**
- Shows levels in red for players with **less than 84 Thieving**
- Caches Hiscore lookups to minimize repeated requests while players hop worlds
- Optional chat notifications when a player with less than 84 Thieving joins the Friends Chat
- Optional chat notifications when a banned player joins the Friends Chat
- Adds a right-click **Plugin Ignore** option directly to Friends Chat members
- Persistent **Under-84 Ignore List** managed from the plugin sidebar
- Persistent **Banned Players** list managed from the plugin sidebar
- Automatically marks banned Friends Chat members with a red **BAN** label and skips their Hiscore lookup
- Automatically records nearby non-Friends Chat players while inside the Rogue's Castle region, making it easy to copy or add them to the ban list

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

- **Under-84 Ignore List** for players who should be excluded from the under-84 overlay and join notifications
- **Banned Players** list for players who are marked with a red **BAN** label and excluded from Hiscore lookups
- **Nearby Outsiders** list that automatically captures nearby non-Friends Chat players while at Rogue's Castle
- Copy names to your clipboard with a single click
- Add captured players directly to the ban list
- Remove individual players or clear captured lists as needed