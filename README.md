# Rogue Chests FC

A RuneLite plugin for members of the **Rogue Chests** Friends Chat.

## Features

- Displays each Friends Chat member's **Thieving level** next to their name
- Shows levels in green for players with 84 or higher Thieving
- Shows levels in red for players with less than 84 Thieving
- Caches Hiscore results to avoid repeated lookups while players change worlds
- Shows an optional chatbox warning when a player with less than 84 Thieving joins the Friends Chat
- Supports an ignore list that suppresses warnings, removes players from the under-84 panel, and displays their Thieving level in green
- Adds a right-click context menu item to add users to the under-84 ignore list
- Supports a configurable banned-player list that marks matching Friends Chat members in red with `BAN`, skips their Hiscore lookup, and shows a chat warning when they join

## Under-84 Panel

An optional overlay lists current Friends Chat members with less than 84 Thieving.

Panel options include:

- RuneScape default or Arial font
- Adjustable font size
- Custom font color
- Custom background color and transparency
- Automatic sizing based on the selected font
- An ignore list for excluding specific players from the panel

Players who leave the Friends Chat remain in the panel in italics for one minute. If they do not rejoin, they are removed automatically.

Ignored players are excluded only from the overlay. Their colored Thieving-level label still appears in the Friends Chat member list.