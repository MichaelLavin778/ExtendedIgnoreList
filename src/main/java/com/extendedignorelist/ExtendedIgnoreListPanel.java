package com.extendedignorelist;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class ExtendedIgnoreListPanel extends PluginPanel
{
    private static final Color PANEL_BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
    private static final Color HEADER_BACKGROUND = ColorScheme.MEDIUM_GRAY_COLOR;
    private static final Color HEADER_TEXT = ColorScheme.BRAND_ORANGE;
    private static final Color LIST_FRAME = ColorScheme.BORDER_COLOR;
    private static final Color LIST_BACKGROUND = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color PRIMARY_TEXT = ColorScheme.TEXT_COLOR;
    private static final Color SECONDARY_TEXT = ColorScheme.LIGHT_GRAY_COLOR;
    private static final String NOTE_ICON_TEXT = "\u25A4";
    private static final String PREVIOUS_NAME_ICON_TEXT = "\u21C4";
    private static final int ROW_HEIGHT = 24;

    private final JPanel playersListPanel = new JPanel();
    private final Consumer<String> removePlayerAction;
    private final BiConsumer<String, String> editNoteAction;
    private final JButton importButton;
    private final JComboBox<IgnoreListSortOrder> sortSelector;
    private boolean updatingSortOrder;

    public ExtendedIgnoreListPanel(Runnable importAction, Consumer<String> removePlayerAction,
        BiConsumer<String, String> editNoteAction, IgnoreListSortOrder sortOrder,
        Consumer<IgnoreListSortOrder> sortAction)
    {
        super(false);
        this.removePlayerAction = removePlayerAction;
        this.editNoteAction = editNoteAction;
        setLayout(new BorderLayout());
        setBackground(PANEL_BACKGROUND);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.setBackground(PANEL_BACKGROUND);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(HEADER_BACKGROUND);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LIST_FRAME),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));

        JLabel titleLabel = new JLabel("Extended Ignores");
        titleLabel.setForeground(HEADER_TEXT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JPanel listContainer = new JPanel(new BorderLayout());
        listContainer.setBorder(BorderFactory.createLineBorder(LIST_FRAME));
        listContainer.setBackground(LIST_BACKGROUND);

        importButton = new JButton("Import ignore list");
        importButton.setFocusPainted(false);
        importButton.setBackground(HEADER_BACKGROUND);
        importButton.setForeground(PRIMARY_TEXT);
        importButton.setBorder(BorderFactory.createLineBorder(LIST_FRAME));
        importButton.addActionListener(event -> importAction.run());
        importButton.setAlignmentX(LEFT_ALIGNMENT);
        importButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, importButton.getPreferredSize().height));

        playersListPanel.setBackground(LIST_BACKGROUND);
        playersListPanel.setLayout(new BoxLayout(playersListPanel, BoxLayout.Y_AXIS));

        JScrollPane listScrollPane = new JScrollPane(playersListPanel);
        listScrollPane.setBorder(BorderFactory.createEmptyBorder());
        listScrollPane.getViewport().setBackground(LIST_BACKGROUND);
        listScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        listContainer.add(listScrollPane, BorderLayout.CENTER);

        topPanel.add(importButton);
        topPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        headerPanel.setAlignmentX(LEFT_ALIGNMENT);
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, headerPanel.getPreferredSize().height));
        topPanel.add(headerPanel);
        JPanel sortPanel = new JPanel(new BorderLayout(8, 0));
        sortPanel.setOpaque(false);
        sortPanel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel sortLabel = createHelpLabel("Sort:");
        sortSelector = new JComboBox<>(IgnoreListSortOrder.values());
        sortSelector.setSelectedItem(sortOrder);
        sortSelector.setToolTipText("Sort by name or date added");
        sortSelector.addActionListener(event ->
        {
            if (!updatingSortOrder)
            {
                sortAction.accept(sortSelector.getItemAt(sortSelector.getSelectedIndex()));
            }
        });
        sortPanel.add(sortLabel, BorderLayout.WEST);
        sortPanel.add(sortSelector, BorderLayout.CENTER);
        sortPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, sortPanel.getPreferredSize().height));
        JPanel helpPanel = new JPanel();
        helpPanel.setLayout(new BoxLayout(helpPanel, BoxLayout.Y_AXIS));
        helpPanel.setOpaque(false);
        helpPanel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel deleteHelpLabel = createHelpLabel("Left-click: delete");
        JLabel noteHelpLabel = createHelpLabel("Right-click: edit note");
        helpPanel.add(deleteHelpLabel);
        helpPanel.add(noteHelpLabel);
        topPanel.add(helpPanel);
        topPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        topPanel.add(sortPanel);
        topPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        content.add(topPanel, BorderLayout.NORTH);
        content.add(listContainer, BorderLayout.CENTER);

        add(content, BorderLayout.CENTER);
        // Match Notes-style behavior so this panel fills available sidebar height.
        SwingUtilities.invokeLater(() ->
        {
            if (getParent() != null)
            {
                getParent().setLayout(new BorderLayout());
                getParent().add(this, BorderLayout.CENTER);
                getParent().revalidate();
            }
        });

        renderPlayers(List.of());
        setImportButtonState(false, "Log in to import your ignore list.");
    }

    public void setPlayers(List<IgnoredPlayer> players)
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            renderPlayers(players);
            return;
        }

        SwingUtilities.invokeLater(() -> renderPlayers(players));
    }

    public void addDeveloperControls(Runnable clearAction)
    {
        JButton clearButton = new JButton("Clear extended list (dev)");
        clearButton.setFocusPainted(false);
        clearButton.setBackground(HEADER_BACKGROUND);
        clearButton.setForeground(PRIMARY_TEXT);
        clearButton.setBorder(BorderFactory.createLineBorder(LIST_FRAME));
        clearButton.setToolTipText("Clear all shared extended entries and notes after confirmation");
        clearButton.addActionListener(event -> clearAction.run());
        clearButton.setAlignmentX(LEFT_ALIGNMENT);
        clearButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, clearButton.getPreferredSize().height));
        Container topPanel = importButton.getParent();
        int importIndex = topPanel.getComponentZOrder(importButton);
        topPanel.add(Box.createRigidArea(new Dimension(0, 8)), importIndex + 1);
        topPanel.add(clearButton, importIndex + 2);
        topPanel.revalidate();
        topPanel.repaint();
    }

    public void setSortOrder(IgnoreListSortOrder sortOrder)
    {
        Runnable update = () ->
        {
            if (sortSelector.getSelectedItem() != sortOrder)
            {
                updatingSortOrder = true;
                try
                {
                    sortSelector.setSelectedItem(sortOrder);
                }
                finally
                {
                    updatingSortOrder = false;
                }
            }
        };

        if (SwingUtilities.isEventDispatchThread())
        {
            update.run();
            return;
        }

        SwingUtilities.invokeLater(update);
    }

    public void setImportButtonState(boolean enabled, String disabledReason)
    {
        Runnable update = () ->
        {
            importButton.setEnabled(enabled);
            importButton.setToolTipText(enabled ? null : disabledReason);
        };

        if (SwingUtilities.isEventDispatchThread())
        {
            update.run();
            return;
        }

        SwingUtilities.invokeLater(update);
    }

    private JLabel createHelpLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(SECONDARY_TEXT);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private void renderPlayers(List<IgnoredPlayer> players)
    {
        playersListPanel.removeAll();

        if (players.isEmpty())
        {
            JLabel emptyLabel = new JLabel("No players added yet.");
            emptyLabel.setForeground(SECONDARY_TEXT);
            emptyLabel.setAlignmentX(LEFT_ALIGNMENT);
            playersListPanel.add(emptyLabel);
        }
        else
        {
            for (IgnoredPlayer player : players)
            {
                playersListPanel.add(createPlayerRow(player));
            }
        }

        playersListPanel.revalidate();
        playersListPanel.repaint();
    }

    private JPanel createPlayerRow(IgnoredPlayer player)
    {
        JPanel rowPanel = new JPanel(new BorderLayout(8, 0));
        rowPanel.setOpaque(false);
        rowPanel.setAlignmentX(LEFT_ALIGNMENT);
        rowPanel.setMinimumSize(new Dimension(0, ROW_HEIGHT));
        rowPanel.setPreferredSize(new Dimension(0, ROW_HEIGHT));
        rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        rowPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, LIST_FRAME),
            BorderFactory.createEmptyBorder(1, 6, 1, 6)
        ));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(Box.createVerticalGlue());

        JLabel playerLabel = new JLabel(player.getCurrentName());
        playerLabel.setForeground(PRIMARY_TEXT);
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        namePanel.setOpaque(false);
        namePanel.add(playerLabel);

        if (!player.getNote().isEmpty())
        {
            JLabel noteIcon = new JLabel(NOTE_ICON_TEXT);
            noteIcon.setForeground(HEADER_TEXT);
            noteIcon.setToolTipText(player.getNote());
            namePanel.add(noteIcon);
        }

        textPanel.add(namePanel);

        List<String> aliases = player.getAliases();
        if (!aliases.isEmpty())
        {
            JLabel previousNameIcon = new JLabel(PREVIOUS_NAME_ICON_TEXT);
            previousNameIcon.setForeground(SECONDARY_TEXT);
            previousNameIcon.setToolTipText(String.join(", ", aliases));
            namePanel.add(previousNameIcon);
        }

        textPanel.add(Box.createVerticalGlue());

        MouseAdapter deleteClickListener = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent event)
            {
                if (event.isPopupTrigger() || event.getButton() == MouseEvent.BUTTON3)
                {
                    promptAndEditNote(player);
                    return;
                }

                if (event.getButton() == MouseEvent.BUTTON1)
                {
                    removePlayerAction.accept(player.getCurrentName());
                }
            }
        };

        rowPanel.add(textPanel, BorderLayout.CENTER);
        applyDeleteRowBehavior(rowPanel, deleteClickListener);
        return rowPanel;
    }

    private void promptAndEditNote(IgnoredPlayer player)
    {
        String note = (String) JOptionPane.showInputDialog(
            this,
            "Enter note for " + player.getCurrentName() + ":",
            "Edit note",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            player.getNote()
        );
        if (note != null)
        {
            editNoteAction.accept(player.getCurrentName(), note.trim());
        }
    }

    private void applyDeleteRowBehavior(Component component, MouseAdapter deleteClickListener)
    {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.addMouseListener(deleteClickListener);

        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                applyDeleteRowBehavior(child, deleteClickListener);
            }
        }
    }

}