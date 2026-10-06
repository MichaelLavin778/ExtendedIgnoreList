package com.extendedignorelist;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.util.Text;
import org.junit.Before;
import org.junit.Test;

public class RaidBoardHighlighterTest
{
    private Client client;
    private RaidBoardHighlighter highlighter;
    private Set<String> ignoredNames;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        ignoredNames = new HashSet<>();
        ignoredNames.add("alice");
        highlighter = new RaidBoardHighlighter(client, name -> ignoredNames.contains(Text.standardize(name)));
    }

    @Test
    public void highlightsAllSupportedPartyLists()
    {
        int[] components = {
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
        for (int component : components)
        {
            Widget root = mock(Widget.class);
            Widget name = textWidget("Alice");
            when(root.getDynamicChildren()).thenReturn(new Widget[] {null, name});
            when(client.getWidget(component)).thenReturn(root);
        }

        highlighter.refresh(true);

        for (int component : components)
        {
            assertEquals("<col=ff0000>Alice</col>", client.getWidget(component).getDynamicChildren()[1].getText());
        }
    }

    @Test
    public void theatreHudHighlightsOnlyIgnoredLinesAndRestoresOriginalText()
    {
        Widget names = textWidget("Me<br><col=ffff00>Alice</col><br>Alice Prime<br>-<br>-");
        when(names.getId()).thenReturn(InterfaceID.TobHud.NAMES);
        when(client.getWidget(InterfaceID.TobHud.NAMES)).thenReturn(names);
        highlighter.refresh(true);
        assertEquals("Me<br><col=ff0000>Alice</col><br>Alice Prime<br>-<br>-", names.getText());
        highlighter.refresh(true);
        ignoredNames.clear();
        highlighter.refresh(true);
        assertEquals("Me<br><col=ffff00>Alice</col><br>Alice Prime<br>-<br>-", names.getText());
        ignoredNames.add("alice");
        highlighter.refresh(true);
        highlighter.refresh(false);
        assertEquals("Me<br><col=ffff00>Alice</col><br>Alice Prime<br>-<br>-", names.getText());
    }

    @Test
    public void amascutLobbyHighlightsOnlyIgnoredNamesAndRestoresOnDisableOrRemoval()
    {
        ignoredNames.add("old alice");
        String original = "Me<br><img=1><col=ffff00>OLD\u00a0Alice</col><br>Alice Prime<br>-<br>-<br>-<br>-<br>-";
        Widget names = textWidget(original);
        when(names.getId()).thenReturn(InterfaceID.ToaLobby.NAMES);
        when(client.getWidget(InterfaceID.ToaLobby.NAMES)).thenReturn(names);
        highlighter.refresh(true);
        assertEquals("Me<br><col=ff0000><img=1>OLD\u00a0Alice</col><br>Alice Prime<br>-<br>-<br>-<br>-<br>-",
            names.getText());
        highlighter.refresh(false);
        assertEquals(original, names.getText());
        highlighter.refresh(true);
        ignoredNames.clear();
        highlighter.refresh(true);
        assertEquals(original, names.getText());
    }

    @Test
    public void chambersHighlightsTruncatedNamesUsingFullNameAndRestoresOnRowReuse()
    {
        ignoredNames.add("long alice");
        Widget list = mock(Widget.class);
        Widget visible = textWidget("<img=1>Long Ali...");
        Widget full = textWidget("Long Alice");
        when(full.isHidden()).thenReturn(true);
        Widget otherColumn = textWidget("Alice");
        Widget[] children = new Widget[7];
        children[1] = visible;
        children[2] = otherColumn;
        children[4] = full;
        when(list.getDynamicChildren()).thenReturn(children);
        when(client.getWidget(InterfaceID.RaidsSidepanel.LIST)).thenReturn(list);

        highlighter.refresh(true);
        assertEquals("<col=ff0000><img=1>Long Ali...</col>", visible.getText());
        assertEquals("Long Alice", full.getText());
        highlighter.refresh(false);
        assertEquals("<img=1>Long Ali...", visible.getText());
        highlighter.refresh(true);
        when(full.getText()).thenReturn("Long Alison");
        highlighter.refresh(true);
        assertEquals("<img=1>Long Ali...", visible.getText());
        when(full.getText()).thenReturn("Long Alice");
        highlighter.refresh(true);
        ignoredNames.clear();
        highlighter.refresh(true);
        assertEquals("<img=1>Long Ali...", visible.getText());
    }

    @Test
    public void matchesWholeNamesAndPreservesIconsAndSpacing()
    {
        ignoredNames.add("old alice");
        Widget exact = textWidget("<img=1><col=ffff00>oLd\u00a0Alice</col>");
        Widget longerName = textWidget("Alice Prime");
        Widget shorterName = textWidget("Ali");
        Widget sentence = textWidget("Alice's party");
        Widget button = textWidget("Alice");
        when(button.getType()).thenReturn(WidgetType.GRAPHIC);
        show(exact, longerName, shorterName, sentence, button);

        highlighter.refresh(true);

        assertEquals("<col=ff0000><img=1>oLd\u00a0Alice</col>", exact.getText());
        verify(longerName, never()).setText(anyString());
        verify(shorterName, never()).setText(anyString());
        verify(sentence, never()).setText(anyString());
        verify(button, never()).setText(anyString());
    }

    @Test
    public void traversesStaticDynamicAndNestedChildrenWithoutDuplicateWrites()
    {
        Widget root = mock(Widget.class);
        Widget staticName = textWidget("Alice");
        Widget dynamicName = textWidget("ALICE");
        Widget nestedName = textWidget("alice");
        when(root.getStaticChildren()).thenReturn(new Widget[] {staticName});
        when(root.getDynamicChildren()).thenReturn(new Widget[] {dynamicName, staticName});
        when(root.getNestedChildren()).thenReturn(new Widget[] {nestedName});
        when(client.getWidget(InterfaceID.ToaPartylist.LIST)).thenReturn(root);

        highlighter.refresh(true);
        highlighter.refresh(true);

        verify(staticName, times(1)).setText("<col=ff0000>Alice</col>");
        verify(dynamicName, times(1)).setText("<col=ff0000>ALICE</col>");
        verify(nestedName, times(1)).setText("<col=ff0000>alice</col>");
    }

    @Test
    public void skipsHiddenListsAndNames()
    {
        Widget root = mock(Widget.class);
        Widget child = textWidget("Alice");
        when(root.isHidden()).thenReturn(true);
        when(root.getDynamicChildren()).thenReturn(new Widget[] {child});
        when(client.getWidget(InterfaceID.ToaPartylist.LIST)).thenReturn(root);
        Widget hiddenName = textWidget("Alice");
        when(hiddenName.isHidden()).thenReturn(true);
        when(client.getWidget(InterfaceID.ToaLobby.NAMES)).thenReturn(hiddenName);

        highlighter.refresh(true);

        verify(child, never()).setText(anyString());
        verify(hiddenName, never()).setText(anyString());
    }

    @Test
    public void ignoresEmptyAndNullText()
    {
        Widget empty = textWidget("");
        Widget nullText = textWidget(null);
        show(empty, nullText);

        highlighter.refresh(true);

        verify(empty, never()).setText(anyString());
        verify(nullText, never()).setText(anyString());
    }

    @Test
    public void disablingRestoresOriginalFormattingAndAllowsReenabling()
    {
        String original = "<img=2><col=ffff00>Alice</col>";
        Widget name = textWidget(original);
        show(name);
        highlighter.refresh(true);

        highlighter.refresh(false);
        assertEquals(original, name.getText());
        highlighter.refresh(true);
        assertEquals("<col=ff0000><img=2>Alice</col>", name.getText());
        highlighter.restore();
        assertEquals(original, name.getText());
    }

    @Test
    public void removingIgnoredNameRestoresOriginalText()
    {
        Widget name = textWidget("Alice");
        show(name);
        highlighter.refresh(true);

        ignoredNames.clear();
        highlighter.refresh(true);

        assertEquals("Alice", name.getText());
    }

    @Test
    public void closingBoardReleasesOldWidgets()
    {
        Widget name = textWidget("Alice");
        show(name);
        highlighter.refresh(true);
        when(client.getWidget(InterfaceID.ToaPartylist.LIST)).thenReturn(null);

        highlighter.refresh(true);
        assertEquals("Alice", name.getText());
        name.setText("<col=ff0000>Alice</col>");
        highlighter.restore();
        assertEquals("<col=ff0000>Alice</col>", name.getText());
    }

    @Test
    public void handlesReusedRowsAndGameScriptRedraws()
    {
        Widget name = textWidget("Alice");
        show(name);
        highlighter.refresh(true);

        name.setText("Bob");
        highlighter.refresh(true);
        assertEquals("Bob", name.getText());
        name.setText("<col=00ff00>Alice</col>");
        highlighter.refresh(true);
        assertEquals("<col=ff0000>Alice</col>", name.getText());
        highlighter.restore();
        assertEquals("<col=00ff00>Alice</col>", name.getText());
    }

    @Test
    public void restorationDoesNotOverwriteOtherChanges()
    {
        Widget name = textWidget("Alice");
        show(name);
        highlighter.refresh(true);

        name.setText("Charlie");
        highlighter.restore();

        assertEquals("Charlie", name.getText());
    }

    @Test
    public void leavesAlreadyRedNamesUnchanged()
    {
        Widget name = textWidget("<col=ff0000>Alice</col>");
        show(name);

        highlighter.refresh(true);
        highlighter.refresh(true);
        highlighter.restore();

        verify(name, never()).setText(anyString());
    }

    @Test
    public void barbarianAssaultNameHighlightRestoresAndDoesNotMatchPartialNames()
    {
        Widget name = textWidget("<col=ffa81f>Alice</col>");
        Widget partialName = textWidget("Alice Prime");
        when(client.getWidget(InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_NAME))
            .thenReturn(name);
        when(client.getWidget(InterfaceID.BarbassaultScrollPl2.BARBASSAULT_SCROLL_PL2_TN1))
            .thenReturn(partialName);

        highlighter.refresh(true);
        assertEquals("<col=ff0000>Alice</col>", name.getText());
        verify(partialName, never()).setText(anyString());
        highlighter.refresh(false);
        assertEquals("<col=ffa81f>Alice</col>", name.getText());
    }

    private void show(Widget... names)
    {
        Widget root = mock(Widget.class);
        when(root.getDynamicChildren()).thenReturn(names);
        when(client.getWidget(InterfaceID.ToaPartylist.LIST)).thenReturn(root);
    }

    private Widget textWidget(String text)
    {
        Widget widget = mock(Widget.class);
        AtomicReference<String> currentText = new AtomicReference<>(text);
        when(widget.getType()).thenReturn(WidgetType.TEXT);
        when(widget.getText()).thenAnswer(invocation -> currentText.get());
        doAnswer(invocation ->
        {
            currentText.set(invocation.getArgument(0));
            return null;
        }).when(widget).setText(anyString());
        return widget;
    }
}
