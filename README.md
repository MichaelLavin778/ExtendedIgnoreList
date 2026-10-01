# Extended Ignore List

Extended Ignore List expands RuneScape's native ignore system by maintaining an additional plugin-managed ignore list, so you can track and filter more players than the in-game limit.

![Extended Ignore List panel](assets/panel.png)

## What it does

- Extends ignore capacity beyond the native cap by storing extra ignored names in plugin config.
- Imports your current native ignore list into the extended list with one click.
- Mirrors native ignore actions:
	- `Add ignore` player menu action syncs into extended list.
	- Native `Add Name` attempts are captured and synced, including fallback when native add fails (for example, native list is full).
	- Native `Del Name` / remove-ignore sync can remove from extended list when `Sync Remove ignore` is enabled.
- Supports adding names from the native ignore-list context menu with `Add to extended`.
- Supports left-click row deletion and right-click note editing in the extended list.
- Stores notes independently in the extended list, with note indicators and saved-note tooltips.

## Identity and syncing behavior

- Tracks rename history using aliases and native ignore current/previous names, so ignored players stay matched after name changes.
- Keeps data per logged-in account session (account-scoped persistence), and reloads automatically when sessions open/close.

## Optional filtering features

- Hide ignored players from rendering in-scene.
- Suppress chat visibility checks for ignored names.
- Hide incoming trade request messages from ignored players.
