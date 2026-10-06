package com.extendedignorelist;

import java.util.Comparator;
import net.runelite.client.util.Text;

public enum IgnoreListSortOrder
{
    NAME_ASCENDING("Name A-Z"),
    NAME_DESCENDING("Name Z-A"),
    OLDEST_FIRST("Oldest first"),
    NEWEST_FIRST("Newest first");

    private final String label;

    IgnoreListSortOrder(String label)
    {
        this.label = label;
    }

    public Comparator<IgnoredPlayer> comparator()
    {
        Comparator<IgnoredPlayer> byName = Comparator.comparing(
            player -> Text.standardize(player.getCurrentName()));
        Comparator<IgnoredPlayer> byDate = Comparator.comparingLong(IgnoredPlayer::getAddedAt);
        switch (this)
        {
            case NAME_ASCENDING:
                return byName.thenComparing(byDate);
            case NAME_DESCENDING:
                return byName.reversed().thenComparing(byDate);
            case OLDEST_FIRST:
                return byDate.thenComparing(byName);
            case NEWEST_FIRST:
                return byDate.reversed().thenComparing(byName);
            default:
                throw new IllegalStateException("Unsupported ignore list sort order: " + this);
        }
    }

    @Override
    public String toString()
    {
        return label;
    }
}
