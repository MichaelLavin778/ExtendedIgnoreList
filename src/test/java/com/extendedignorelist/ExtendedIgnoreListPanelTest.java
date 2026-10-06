package com.extendedignorelist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.awt.Container;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class ExtendedIgnoreListPanelTest
{
    @Test
    public void rendersPlayerRowsAndAliases() throws Exception
    {
        AtomicInteger importClicks = new AtomicInteger();
        AtomicReference<String> removedPlayer = new AtomicReference<>();
        ExtendedIgnoreListPanel panel = new ExtendedIgnoreListPanel(importClicks::incrementAndGet,
            removedPlayer::set, (ignored, note) -> { }, IgnoreListSortOrder.NEWEST_FIRST, ignored -> { });

        List<IgnoredPlayer> players = Arrays.asList(
            new IgnoredPlayer("Alice", Collections.singletonList("Alicia")),
            new IgnoredPlayer("Bob")
        );

        SwingUtilities.invokeAndWait(() -> panel.setPlayers(players));

        assertTrue(findLabelText(panel, "Alice"));
        assertTrue(findLabelText(panel, "\u21C4"));
        assertTrue(findLabelText(panel, "Bob"));
        assertFalse(findLabelText(panel, "No players added yet."));
    }

    @Test
    public void rowClickInvokesCallbackWithCurrentName() throws Exception
    {
        AtomicReference<String> removedPlayer = new AtomicReference<>();
        ExtendedIgnoreListPanel panel = new ExtendedIgnoreListPanel(() -> { }, removedPlayer::set,
            (ignored, note) -> { }, IgnoreListSortOrder.NEWEST_FIRST, ignored -> { });
        SwingUtilities.invokeAndWait(() -> panel.setPlayers(Collections.singletonList(new IgnoredPlayer("Charlie"))));

        JLabel nameLabel = findLabel(panel, "Charlie");
        JPanel rowPanel = (JPanel) nameLabel.getParent().getParent();
        assertEquals(null, rowPanel.getToolTipText());

        SwingUtilities.invokeAndWait(() -> rowPanel.dispatchEvent(
            new MouseEvent(
                rowPanel,
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                4,
                4,
                1,
                false,
                MouseEvent.BUTTON1
            )
        ));
        assertEquals("Charlie", removedPlayer.get());
    }

    @Test
    public void importButtonStateReflectsAvailability() throws Exception
    {
        AtomicReference<ExtendedIgnoreListPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new ExtendedIgnoreListPanel(() -> { }, ignored -> { },
            (ignored, note) -> { }, IgnoreListSortOrder.NEWEST_FIRST, ignored -> { })));
        ExtendedIgnoreListPanel panel = panelRef.get();
        JButton importButton = findButton(panel, "Import ignore list");

        AtomicBoolean disabledByDefault = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> disabledByDefault.set(!importButton.isEnabled()));
        assertTrue(disabledByDefault.get());

        SwingUtilities.invokeAndWait(() -> panel.setImportButtonState(true, null));
        assertTrue(importButton.isEnabled());
        assertEquals(null, importButton.getToolTipText());

        SwingUtilities.invokeAndWait(() -> panel.setImportButtonState(false, "No new native ignore list entries to import."));
        assertFalse(importButton.isEnabled());
        assertEquals("No new native ignore list entries to import.", importButton.getToolTipText());
    }

    @Test
    public void notedRowsShowNoteIndicatorAndInteractionHelp() throws Exception
    {
        ExtendedIgnoreListPanel panel = new ExtendedIgnoreListPanel(() -> { }, ignored -> { },
            (ignored, note) -> { }, IgnoreListSortOrder.NEWEST_FIRST, ignored -> { });
        IgnoredPlayer player = new IgnoredPlayer("Dana");
        player.setNote("Follow up later");

        SwingUtilities.invokeAndWait(() -> panel.setPlayers(Collections.singletonList(player)));

        assertTrue(findLabelText(panel, "\u25A4"));
        assertTrue(findLabelText(panel, "Left-click: delete"));
        assertTrue(findLabelText(panel, "Right-click: edit note"));
        Container helpPanel = findLabel(panel, "Right-click: edit note").getParent();
        Container sortPanel = findSortSelector(panel).getParent();
        Container topPanel = helpPanel.getParent();
        assertEquals(topPanel, sortPanel.getParent());
        assertTrue(topPanel.getComponentZOrder(helpPanel) < topPanel.getComponentZOrder(sortPanel));
    }

    @Test
    public void sortSelectorRestoresSelectionAndOnlyNotifiesForUserChanges() throws Exception
    {
        AtomicReference<IgnoreListSortOrder> selected = new AtomicReference<>();
        AtomicReference<ExtendedIgnoreListPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new ExtendedIgnoreListPanel(
            () -> { }, ignored -> { }, (ignored, note) -> { },
            IgnoreListSortOrder.NAME_DESCENDING, selected::set)));
        ExtendedIgnoreListPanel panel = panelRef.get();
        JComboBox<?> selector = findSortSelector(panel);
        assertEquals(4, selector.getItemCount());
        assertEquals(IgnoreListSortOrder.NAME_DESCENDING, selector.getSelectedItem());
        assertEquals(null, selected.get());

        SwingUtilities.invokeAndWait(() -> selector.setSelectedItem(IgnoreListSortOrder.OLDEST_FIRST));
        assertEquals(IgnoreListSortOrder.OLDEST_FIRST, selected.get());

        selected.set(null);
        panel.setSortOrder(IgnoreListSortOrder.NEWEST_FIRST);
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(IgnoreListSortOrder.NEWEST_FIRST, selector.getSelectedItem());
        assertEquals(null, selected.get());
    }

    private JComboBox<?> findSortSelector(Container container)
    {
        for (Component child : container.getComponents())
        {
            if (child instanceof JComboBox)
            {
                return (JComboBox<?>) child;
            }
            if (child instanceof Container)
            {
                JComboBox<?> selector = findSortSelector((Container) child);
                if (selector != null)
                {
                    return selector;
                }
            }
        }
        return null;
    }

    @Test
    public void developerControlsAreAbsentByDefaultAndRequestClearWhenAdded() throws Exception
    {
        AtomicInteger clearRequests = new AtomicInteger();
        AtomicReference<ExtendedIgnoreListPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new ExtendedIgnoreListPanel(
            () -> { }, ignored -> { }, (ignored, note) -> { },
            IgnoreListSortOrder.NEWEST_FIRST, ignored -> { })));
        ExtendedIgnoreListPanel panel = panelRef.get();
        assertEquals(java.awt.BorderLayout.class, panel.getLayout().getClass());
        assertEquals(null, ((java.awt.BorderLayout) panel.getLayout()).getLayoutComponent(
            java.awt.BorderLayout.SOUTH));
        SwingUtilities.invokeAndWait(() ->
        {
            panel.addDeveloperControls(clearRequests::incrementAndGet);
            JButton importButton = findButton(panel, "Import ignore list");
            JButton clearButton = findButton(panel, "Clear extended list (dev)");
            Container topPanel = importButton.getParent();
            assertEquals(topPanel, clearButton.getParent());
            assertEquals(topPanel.getComponentZOrder(importButton) + 2,
                topPanel.getComponentZOrder(clearButton));
            clearButton.doClick();
        });
        assertEquals(1, clearRequests.get());
    }

    private boolean findLabelText(Component component, String text)
    {
        if (component instanceof JLabel && text.equals(((JLabel) component).getText()))
        {
            return true;
        }

        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                if (findLabelText(child, text))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private JLabel findLabel(Component component, String text)
    {
        Deque<Component> queue = new ArrayDeque<>();
        Set<Component> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(component);

        while (!queue.isEmpty())
        {
            Component current = queue.removeFirst();
            if (!visited.add(current))
            {
                continue;
            }

            if (current instanceof JLabel && text.equals(((JLabel) current).getText()))
            {
                return (JLabel) current;
            }

            if (current instanceof Container)
            {
                for (Component child : ((Container) current).getComponents())
                {
                    queue.addLast(child);
                }
            }
        }

        throw new AssertionError("Label not found: " + text);
    }

    private JButton findButton(Component component, String text)
    {
        Deque<Component> queue = new ArrayDeque<>();
        Set<Component> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(component);

        while (!queue.isEmpty())
        {
            Component current = queue.removeFirst();
            if (!visited.add(current))
            {
                continue;
            }

            if (current instanceof JButton && text.equals(((JButton) current).getText()))
            {
                return (JButton) current;
            }

            if (current instanceof Container)
            {
                for (Component child : ((Container) current).getComponents())
                {
                    queue.addLast(child);
                }
            }
        }

        throw new AssertionError("Button not found: " + text);
    }

    private JButton findButton(ExtendedIgnoreListPanel panel, String text)
    {
        return findButton((Component) panel, text);
    }
}
