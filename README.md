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
- Shares the extended list, aliases, and notes across RuneScape accounts AND RuneLite configuration profiles. Feature settings (such as hiding players) remain configuration-profile-specific.
- To share across computers, use the updated plugin and sign into the same RuneLite account on every computer. Entries use a fixed plugin namespace in RuneLite's automatically synced shared store; the selected configuration profiles do not need to match.
- Uses RuneLite's normal cloud sync timing, not instant live synchronization. Allow the source client to sync (or close it normally), then restart the other client to receive changes. Avoid editing the list on both computers at once: the list is stored as one setting, so competing edits can overwrite each other.
- Automatically merges existing account-specific extended lists and the active configuration profile's old list into the shared store. Switch through other configuration profiles once to migrate their old lists too. Matching names/aliases are combined; distinct notes are joined with ` / `. Migrated entries are removed from their old storage so deleted shared entries are not re-imported later.
- Reloads when RuneLite sessions, configuration profiles, or the shared list setting change. Import still reads only the currently logged-in RuneScape account's native ignore list; it does not change another account's native list.

## Optional filtering features

- Hide ignored players from rendering in-scene.
- Suppress chat visibility checks for ignored names.
- Hide incoming trade request messages from ignored players.
