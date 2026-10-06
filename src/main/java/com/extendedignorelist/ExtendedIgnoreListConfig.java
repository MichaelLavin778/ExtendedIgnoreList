package com.extendedignorelist;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("extendedignorelist")
public interface ExtendedIgnoreListConfig extends Config
{
    @ConfigItem(
        keyName = "playerMenuOption",
        name = "Player Menu Option",
        description = "Show an Add ignore option on player menus",
        position = 0
    )
    default boolean playerMenuOption()
    {
        return true;
    }

    @ConfigItem(
        keyName = "ignoreListMenuOption",
        name = "Ignore List Menu Option",
        description = "Show an Add to extended option on native ignore-list entries",
        position = 1
    )
    default boolean ignoreListMenuOption()
    {
        return true;
    }

    @ConfigItem(
        keyName = "ignoreTrades",
        name = "Ignore Trades",
        description = "Hide incoming trade request messages from players on the extended ignore list",
        position = 2
    )
    default boolean ignoreTrades()
    {
        return true;
    }

    @ConfigItem(
        keyName = "deleteConfirmation",
        name = "Delete Confirmation",
        description = "Ask for confirmation before deleting a player from the extended ignore list",
        position = 3
    )
    default boolean deleteConfirmation()
    {
        return true;
    }

    @ConfigItem(
        keyName = "hidePlayers",
        name = "Hide Players",
        description = "Hide ignored players from the scene",
        position = 4
    )
    default boolean hidePlayers()
    {
        return false;
    }

    @ConfigItem(
        keyName = "syncRemoveIgnore",
        name = "Sync Remove Ignore",
        description = "Remove players from the extended ignore list when they are removed from RuneScape's native ignore list",
        position = 5
    )
    default boolean syncRemoveIgnore()
    {
        return false;
    }

    @ConfigItem(
        keyName = "highlightRaidsAndGroups",
        name = "Highlight Raids/Groups",
        description = "Show extended-ignored players in red on Chambers of Xeric, Theatre of Blood, and Tombs of Amascut party boards and party lists",
        position = 6
    )
    default boolean highlightRaidsAndGroups()
    {
        return true;
    }

    @ConfigItem(
        keyName = "notifyWhenInGroup",
        name = "Notify when in group",
        description = "Warn when an extended-ignored player is in your raid group, whether they join you or you join them",
        position = 7
    )
    default GroupNotificationMode notifyWhenInGroup()
    {
        return GroupNotificationMode.CHAT_ONLY;
    }

    @ConfigItem(
        keyName = "censorName",
        name = "Censor name",
        description = "Replace ignored names with Someone or Some people in raid group chat messages and notifications",
        position = 8
    )
    default boolean censorName()
    {
        return false;
    }
}