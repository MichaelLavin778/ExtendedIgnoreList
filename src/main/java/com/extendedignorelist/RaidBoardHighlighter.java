package com.extendedignorelist;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.util.Text;

final class RaidBoardHighlighter
{
    private static final int[] PLAYER_LIST_COMPONENTS = {
        InterfaceID.RaidsLobbyPartylist.LIST,
        InterfaceID.RaidsLobbyPartydetails.LIST,
        InterfaceID.RaidsSidepanel.LIST,
        InterfaceID.TobPartylist.LIST,
        InterfaceID.TobPartydetails.CURRENT,
        InterfaceID.TobPartydetails.APPLICANTS,
        InterfaceID.TobHud.NAMES,
        InterfaceID.ToaPartylist.LIST,
        InterfaceID.ToaPartydetails.MEMBERS_LIST,
        InterfaceID.ToaPartydetails.APPLICANTS_LIST,
        InterfaceID.ToaLobby.NAMES,
        InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_NAME,
        InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_PLAYER_1_NAME,
        InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_PLAYER_2_NAME,
        InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_PLAYER_3_NAME,
        InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_PLAYER_4_NAME,
        InterfaceID.BarbassaultScrollPl1.BARBASSAULT_SCROLL_PL1_TN1,
        InterfaceID.BarbassaultScrollPl1.BARBASSAULT_SCROLL_PL1_TN2,
        InterfaceID.BarbassaultScrollPl1.BARBASSAULT_SCROLL_PL1_TN3,
        InterfaceID.BarbassaultScrollPl1.BARBASSAULT_SCROLL_PL1_TN4,
        InterfaceID.BarbassaultScrollPl1.BARBASSAULT_SCROLL_PL1_TN5,
        InterfaceID.BarbassaultScrollPl2.BARBASSAULT_SCROLL_PL2_TN1,
        InterfaceID.BarbassaultScrollPl2.BARBASSAULT_SCROLL_PL2_TN2,
        InterfaceID.BarbassaultScrollPl2.BARBASSAULT_SCROLL_PL2_TN3,
        InterfaceID.BarbassaultScrollPl2.BARBASSAULT_SCROLL_PL2_TN4,
        InterfaceID.BarbassaultScrollPl2.BARBASSAULT_SCROLL_PL2_TN5
    };
    private static final Pattern COLOR_TAGS = Pattern.compile("</?col(?:=[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final String RED_PREFIX = "<col=ff0000>";

    private final Client client;
    private final Predicate<String> isIgnoredName;
    private final Map<Widget, Highlight> highlights = new IdentityHashMap<>();

    RaidBoardHighlighter(Client client, Predicate<String> isIgnoredName)
    {
        this.client = client;
        this.isIgnoredName = isIgnoredName;
    }

    void refresh(boolean enabled)
    {
        if (!enabled)
        {
            restore();
            return;
        }

        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int componentId : PLAYER_LIST_COMPONENTS)
        {
            Widget widget = client.getWidget(componentId);
            if (componentId == InterfaceID.RaidsSidepanel.LIST)
            {
                highlightChambersNames(widget, visited);
            }
            highlight(widget, visited);
        }

        Iterator<Map.Entry<Widget, Highlight>> iterator = highlights.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<Widget, Highlight> entry = iterator.next();
            if (!visited.contains(entry.getKey()))
            {
                restore(entry.getKey(), entry.getValue());
                iterator.remove();
            }
        }
    }

    void restore()
    {
        highlights.forEach(this::restore);
        highlights.clear();
    }

    private void highlight(Widget widget, Set<Widget> visited)
    {
        if (widget == null || widget.isHidden() || !visited.add(widget))
        {
            return;
        }

        if (widget.getType() == WidgetType.TEXT)
        {
            highlightName(widget);
        }

        highlightChildren(widget.getStaticChildren(), visited);
        highlightChildren(widget.getDynamicChildren(), visited);
        highlightChildren(widget.getNestedChildren(), visited);
    }

    private void highlightChildren(Widget[] children, Set<Widget> visited)
    {
        if (children != null)
        {
            for (Widget child : children)
            {
                highlight(child, visited);
            }
        }
    }

    private void highlightName(Widget widget)
    {
        highlightName(widget, null);
    }

    private void highlightChambersNames(Widget list, Set<Widget> visited)
    {
        if (list == null || list.isHidden() || list.getDynamicChildren() == null)
        {
            return;
        }
        Widget[] children = list.getDynamicChildren();
        for (int row = 0; row + RaidPartyRoster.CHAMBERS_FULL_NAME_OFFSET < children.length;
            row += RaidPartyRoster.CHAMBERS_ROW_SIZE)
        {
            Widget visibleName = children[row + RaidPartyRoster.CHAMBERS_VISIBLE_NAME_OFFSET];
            Widget fullName = children[row + RaidPartyRoster.CHAMBERS_FULL_NAME_OFFSET];
            if (visibleName != null && fullName != null && fullName.getText() != null
                && !visibleName.isHidden() && visibleName.getType() == WidgetType.TEXT
                && visited.add(visibleName))
            {
                // The visible label may be truncated; the hidden sort field retains the full name.
                highlightName(visibleName, Text.removeTags(fullName.getText()));
            }
        }
    }

    private void highlightName(Widget widget, String fullName)
    {
        String displayedText = widget.getText();
        Highlight previous = highlights.get(widget);
        String originalText = displayedText;
        if (previous != null)
        {
            if (previous.coloredText.equals(displayedText))
            {
                originalText = previous.originalText;
            }
            else
            {
                // Game scripts can reuse the same row for a different player.
                highlights.remove(widget);
                previous = null;
            }
        }

        if (originalText == null || originalText.isEmpty())
        {
            if (previous != null)
            {
                restore(widget, previous);
                highlights.remove(widget);
            }
            return;
        }

        String coloredText = originalText;
        if (widget.getId() == InterfaceID.TobHud.NAMES
            || widget.getId() == InterfaceID.ToaLobby.NAMES)
        {
            String[] lines = originalText.split("(?i)(?<=<br>)|(?<=\\n)", -1);
            StringBuilder coloredLines = new StringBuilder();
            for (String line : lines)
            {
                String separator = "";
                String name = line;
                if (line.toLowerCase(java.util.Locale.ROOT).endsWith("<br>"))
                {
                    separator = line.substring(line.length() - 4);
                    name = line.substring(0, line.length() - 4);
                }
                else if (line.endsWith("\n"))
                {
                    separator = "\n";
                    name = line.substring(0, line.length() - 1);
                }
                coloredLines.append(isIgnoredName.test(Text.removeTags(name).trim())
                    ? RED_PREFIX + COLOR_TAGS.matcher(name).replaceAll("") + "</col>" : name);
                coloredLines.append(separator);
            }
            coloredText = coloredLines.toString();
        }
        else if (isIgnoredName.test(fullName == null ? Text.removeTags(originalText) : fullName))
        {
            coloredText = RED_PREFIX + COLOR_TAGS.matcher(originalText).replaceAll("") + "</col>";
        }
        if (coloredText.equals(originalText))
        {
            if (previous != null)
            {
                restore(widget, previous);
                highlights.remove(widget);
            }
            return;
        }
        if (!coloredText.equals(displayedText))
        {
            widget.setText(coloredText);
        }
        if (previous == null && !coloredText.equals(originalText))
        {
            highlights.put(widget, new Highlight(originalText, coloredText));
        }
    }

    private void restore(Widget widget, Highlight highlight)
    {
        // Do not overwrite text changed by the game or another plugin.
        if (highlight.coloredText.equals(widget.getText()))
        {
            widget.setText(highlight.originalText);
        }
    }

    private static final class Highlight
    {
        private final String originalText;
        private final String coloredText;

        private Highlight(String originalText, String coloredText)
        {
            this.originalText = originalText;
            this.coloredText = coloredText;
        }
    }
}
