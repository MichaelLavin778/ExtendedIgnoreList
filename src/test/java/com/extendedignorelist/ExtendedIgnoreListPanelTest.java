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
        ExtendedIgnoreListPanel panel = new ExtendedIgnoreListPanel(importClicks::incrementAndGet, removedPlayer::set, (ignored, note) -> { });

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
        ExtendedIgnoreListPanel panel = new ExtendedIgnoreListPanel(() -> { }, removedPlayer::set, (ignored, note) -> { });
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
        SwingUtilities.invokeAndWait(() -> panelRef.set(new ExtendedIgnoreListPanel(() -> { }, ignored -> { }, (ignored, note) -> { })));
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
        ExtendedIgnoreListPanel panel = new ExtendedIgnoreListPanel(() -> { }, ignored -> { }, (ignored, note) -> { });
        IgnoredPlayer player = new IgnoredPlayer("Dana");
        player.setNote("Follow up later");

        SwingUtilities.invokeAndWait(() -> panel.setPlayers(Collections.singletonList(player)));

        assertTrue(findLabelText(panel, "\u25A4"));
        assertTrue(findLabelText(panel, "Left-click: delete"));
        assertTrue(findLabelText(panel, "Right-click: edit note"));
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
