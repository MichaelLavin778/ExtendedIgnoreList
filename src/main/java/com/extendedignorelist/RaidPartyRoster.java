package com.extendedignorelist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

final class RaidPartyRoster
{
    static final int CHAMBERS_ROW_SIZE = 7;
    static final int CHAMBERS_VISIBLE_NAME_OFFSET = 1;
    static final int CHAMBERS_FULL_NAME_OFFSET = 4;

    private RaidPartyRoster()
    {
    }

    static List<String> readNames(Widget widget)
    {
        if (widget == null || widget.isHidden())
        {
            return null;
        }
        List<String> names = new ArrayList<>();
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        readNames(widget, names, visited);
        return names.isEmpty() ? null : names;
    }

    private static void readNames(Widget widget, List<String> names, Set<Widget> visited)
    {
        if (widget == null || widget.isHidden() || !visited.add(widget))
        {
            return;
        }
        if (widget.getType() == WidgetType.TEXT && widget.getText() != null)
        {
            Collections.addAll(names, widget.getText().split("(?i)<br>|\\r?\\n", -1));
        }
        readChildren(widget.getStaticChildren(), names, visited);
        readChildren(widget.getDynamicChildren(), names, visited);
        readChildren(widget.getNestedChildren(), names, visited);
    }

    private static void readChildren(Widget[] children, List<String> names, Set<Widget> visited)
    {
        if (children != null)
        {
            for (Widget child : children)
            {
                readNames(child, names, visited);
            }
        }
    }
}
