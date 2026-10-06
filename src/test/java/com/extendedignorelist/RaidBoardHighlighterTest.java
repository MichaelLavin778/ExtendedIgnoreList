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
            InterfaceID.ToaPartylist.LIST,
            InterfaceID.ToaPartydetails.MEMBERS_LIST,
            InterfaceID.ToaPartydetails.APPLICANTS_LIST,
            InterfaceID.ToaLobby.NAMES
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
