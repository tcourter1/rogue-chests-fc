package com.roguechestsfc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class RogueChestsFcPanel extends PluginPanel
{
    private static final int INPUT_HEIGHT = 55;
    private static final int STANDARD_LIST_HEIGHT = 145;
    private static final int CAPTURED_LIST_HEIGHT = 160;
    private static final int PLAYER_ROW_HEIGHT = 30;
    private static final int BUTTON_HEIGHT = 30;
    private static final int SCROLLBAR_SIZE = 7;

    private final RogueChestsFcPlugin plugin;

    private final JTextArea ignoredNamesInput = new JTextArea();
    private final JTextArea bannedNamesInput = new JTextArea();

    private final JPanel ignoredNamesList = new JPanel();
    private final JPanel bannedNamesList = new JPanel();
    private final JPanel capturedNearbyNamesList = new JPanel();

    @Inject
    public RogueChestsFcPanel(RogueChestsFcPlugin plugin)
    {
        super();

        this.plugin = plugin;

        styleScrollPane(getScrollPane());

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 4, 10, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(createHeader());
        add(Box.createRigidArea(new Dimension(0, 12)));

        add(createIgnoredNamesSection());
        add(Box.createRigidArea(new Dimension(0, 14)));

        add(createBannedNamesSection());
        add(Box.createRigidArea(new Dimension(0, 14)));

        add(createCapturedNearbyNamesSection());
        add(Box.createRigidArea(new Dimension(0, 10)));

        refresh();
    }

    private JPanel createHeader()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 30)
        );

        JLabel title = new JLabel(
                "Rogue Chests FC",
                SwingConstants.CENTER
        );

        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setFont(
                title.getFont().deriveFont(
                        Font.BOLD,
                        15.0f
                )
        );
        title.setHorizontalAlignment(SwingConstants.CENTER);

        panel.add(title, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createIgnoredNamesSection()
    {
        configureInputArea(ignoredNamesInput);
        configureListPanel(ignoredNamesList);

        return createEditableListSection(
                "Under-84 Ignore List",
                "Ignored players are excluded from the under-84 panel and join warnings.",
                ignoredNamesInput,
                ignoredNamesList,
                this::addIgnoredNames,
                plugin::copyIgnoredNames
        );
    }

    private JPanel createBannedNamesSection()
    {
        configureInputArea(bannedNamesInput);
        configureListPanel(bannedNamesList);

        return createEditableListSection(
                "Banned Players",
                "Banned players are marked red with BAN and do not receive Hiscore lookups.",
                bannedNamesInput,
                bannedNamesList,
                this::addBannedNames,
                plugin::copyBannedNames
        );
    }

    private JPanel createCapturedNearbyNamesSection()
    {
        configureListPanel(capturedNearbyNamesList);

        JPanel section = createSectionPanel();

        JLabel title = createSectionTitle(
                "Nearby Outsiders"
        );

        JLabel description = createSectionDescription(
                "Automatically records players who enter render distance while you are in an FC, excluding current FC members."
        );

        JScrollPane listScrollPane = createListScrollPane(
                capturedNearbyNamesList,
                CAPTURED_LIST_HEIGHT
        );

        JButton copyButton = createButton(
                "Copy All",
                plugin::copyCapturedNearbyNames
        );

        JButton clearButton = createButton(
                "Clear",
                plugin::clearCapturedNearbyNames
        );

        JPanel actionRow = createButtonRow(
                copyButton,
                clearButton
        );

        JButton addToBanButton = createButton(
                "Add All to Ban List",
                plugin::addCapturedNearbyNamesToBanList
        );

        configureFullWidthButton(addToBanButton);

        section.add(title);
        section.add(description);
        section.add(listScrollPane);
        section.add(Box.createRigidArea(new Dimension(0, 5)));
        section.add(actionRow);
        section.add(Box.createRigidArea(new Dimension(0, 5)));
        section.add(addToBanButton);

        return section;
    }

    private JPanel createEditableListSection(
            String titleText,
            String descriptionText,
            JTextArea input,
            JPanel listPanel,
            Runnable addAction,
            Runnable copyAction)
    {
        JPanel section = createSectionPanel();

        JLabel title = createSectionTitle(titleText);

        JLabel description = createSectionDescription(
                descriptionText
        );

        JScrollPane inputScrollPane =
                createInputScrollPane(input);

        JButton addButton = createButton(
                "Add Players",
                addAction
        );

        JButton copyButton = createButton(
                "Copy All",
                copyAction
        );

        JPanel actionRow = createButtonRow(
                addButton,
                copyButton
        );

        JScrollPane listScrollPane = createListScrollPane(
                listPanel,
                STANDARD_LIST_HEIGHT
        );

        section.add(title);
        section.add(description);
        section.add(inputScrollPane);
        section.add(Box.createRigidArea(new Dimension(0, 5)));
        section.add(actionRow);
        section.add(Box.createRigidArea(new Dimension(0, 7)));
        section.add(listScrollPane);

        return section;
    }

    private JPanel createSectionPanel()
    {
        JPanel section = new JPanel();

        section.setLayout(
                new BoxLayout(section, BoxLayout.Y_AXIS)
        );
        section.setBackground(ColorScheme.DARK_GRAY_COLOR);
        section.setBorder(null);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE
                )
        );

        return section;
    }

    private JLabel createSectionTitle(String titleText)
    {
        JLabel title = new JLabel(titleText);

        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setFont(
                title.getFont().deriveFont(Font.BOLD)
        );
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        return title;
    }

    private JLabel createSectionDescription(
            String descriptionText)
    {
        JLabel description = new JLabel(
                "<html><div style='width:178px;'>"
                        + descriptionText
                        + "</div></html>"
        );

        description.setForeground(
                ColorScheme.LIGHT_GRAY_COLOR
        );
        description.setBorder(
                new EmptyBorder(4, 0, 7, 0)
        );
        description.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        return description;
    }

    private JScrollPane createInputScrollPane(
            JTextArea input)
    {
        JScrollPane inputScrollPane =
                new JScrollPane(input);

        inputScrollPane.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );
        inputScrollPane.setPreferredSize(
                new Dimension(0, INPUT_HEIGHT)
        );
        inputScrollPane.setMinimumSize(
                new Dimension(0, INPUT_HEIGHT)
        );
        inputScrollPane.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        INPUT_HEIGHT
                )
        );
        inputScrollPane.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        inputScrollPane.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        );
        inputScrollPane.setBorder(
                BorderFactory.createLineBorder(
                        ColorScheme.MEDIUM_GRAY_COLOR
                )
        );

        styleScrollPane(inputScrollPane);

        return inputScrollPane;
    }

    private JScrollPane createListScrollPane(
            JPanel listPanel,
            int height)
    {
        JScrollPane listScrollPane =
                new JScrollPane(listPanel);

        listScrollPane.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );
        listScrollPane.setPreferredSize(
                new Dimension(0, height)
        );
        listScrollPane.setMinimumSize(
                new Dimension(0, height)
        );
        listScrollPane.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        height
                )
        );
        listScrollPane.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        listScrollPane.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        );
        listScrollPane.getViewport().setBackground(
                ColorScheme.DARKER_GRAY_COLOR
        );
        listScrollPane.setBorder(
                BorderFactory.createLineBorder(
                        ColorScheme.MEDIUM_GRAY_COLOR
                )
        );

        styleScrollPane(listScrollPane);

        return listScrollPane;
    }

    private void styleScrollPane(JScrollPane scrollPane)
    {
        if (scrollPane == null)
        {
            return;
        }

        styleScrollBar(scrollPane.getVerticalScrollBar());
        styleScrollBar(scrollPane.getHorizontalScrollBar());

        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
    }

    private void styleScrollBar(JScrollBar scrollBar)
    {
        scrollBar.setUI(new ThinScrollBarUI());
        scrollBar.setUnitIncrement(16);

        if (scrollBar.getOrientation() == JScrollBar.VERTICAL)
        {
            scrollBar.setPreferredSize(
                    new Dimension(SCROLLBAR_SIZE, 0)
            );
        }
        else
        {
            scrollBar.setPreferredSize(
                    new Dimension(0, SCROLLBAR_SIZE)
            );
        }
    }

    private JPanel createButtonRow(
            JButton leftButton,
            JButton rightButton)
    {
        JPanel buttonRow = new JPanel(
                new GridLayout(1, 2, 5, 0)
        );

        buttonRow.setBackground(
                ColorScheme.DARK_GRAY_COLOR
        );
        buttonRow.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );
        buttonRow.setPreferredSize(
                new Dimension(0, BUTTON_HEIGHT)
        );
        buttonRow.setMinimumSize(
                new Dimension(0, BUTTON_HEIGHT)
        );
        buttonRow.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        BUTTON_HEIGHT
                )
        );

        buttonRow.add(leftButton);
        buttonRow.add(rightButton);

        return buttonRow;
    }

    private JButton createButton(
            String text,
            Runnable action)
    {
        JButton button = new JButton(text);

        button.setFocusable(false);
        button.addActionListener(
                event -> action.run()
        );

        return button;
    }

    private void configureFullWidthButton(
            JButton button)
    {
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setPreferredSize(
                new Dimension(0, BUTTON_HEIGHT)
        );
        button.setMinimumSize(
                new Dimension(0, BUTTON_HEIGHT)
        );
        button.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        BUTTON_HEIGHT
                )
        );
    }

    private void configureInputArea(JTextArea input)
    {
        input.setRows(3);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setToolTipText(
                "Enter one or more names separated by commas or new lines"
        );
        input.setBorder(
                new EmptyBorder(4, 4, 4, 4)
        );
    }

    private void configureListPanel(JPanel listPanel)
    {
        listPanel.setLayout(
                new BoxLayout(listPanel, BoxLayout.Y_AXIS)
        );
        listPanel.setBackground(
                ColorScheme.DARKER_GRAY_COLOR
        );
        listPanel.setBorder(
                new EmptyBorder(4, 4, 4, 4)
        );
    }

    private void addIgnoredNames()
    {
        String input = ignoredNamesInput.getText();

        if (input == null || input.trim().isEmpty())
        {
            return;
        }

        plugin.addIgnoredNames(input);
        ignoredNamesInput.setText("");
        refresh();
    }

    private void addBannedNames()
    {
        String input = bannedNamesInput.getText();

        if (input == null || input.trim().isEmpty())
        {
            return;
        }

        plugin.addBannedNames(input);
        bannedNamesInput.setText("");
        refresh();
    }

    public void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        rebuildList(
                ignoredNamesList,
                plugin.getIgnoredPlayerNames(),
                plugin::removeIgnoredName
        );

        rebuildList(
                bannedNamesList,
                plugin.getBannedPlayerNames(),
                plugin::removeBannedName
        );

        rebuildList(
                capturedNearbyNamesList,
                plugin.getCapturedNearbyPlayerNames(),
                plugin::removeCapturedNearbyName
        );

        revalidate();
        repaint();
    }

    private void rebuildList(
            JPanel listPanel,
            List<String> names,
            NameRemovalAction removalAction)
    {
        listPanel.removeAll();

        if (names.isEmpty())
        {
            JLabel emptyLabel = new JLabel(
                    "No players added",
                    SwingConstants.CENTER
            );

            emptyLabel.setForeground(
                    ColorScheme.LIGHT_GRAY_COLOR
            );
            emptyLabel.setBorder(
                    new EmptyBorder(8, 0, 8, 0)
            );
            emptyLabel.setAlignmentX(
                    Component.CENTER_ALIGNMENT
            );
            emptyLabel.setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            34
                    )
            );
            emptyLabel.setHorizontalAlignment(
                    SwingConstants.CENTER
            );

            listPanel.add(emptyLabel);
        }
        else
        {
            for (String name : names)
            {
                listPanel.add(
                        createPlayerRow(
                                name,
                                removalAction
                        )
                );

                listPanel.add(
                        Box.createRigidArea(
                                new Dimension(0, 3)
                        )
                );
            }
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel createPlayerRow(
            String playerName,
            NameRemovalAction removalAction)
    {
        JPanel row = new JPanel(
                new BorderLayout(5, 0)
        );

        row.setBackground(
                ColorScheme.MEDIUM_GRAY_COLOR
        );
        row.setBorder(
                new EmptyBorder(3, 6, 3, 3)
        );
        row.setPreferredSize(
                new Dimension(0, PLAYER_ROW_HEIGHT)
        );
        row.setMinimumSize(
                new Dimension(0, PLAYER_ROW_HEIGHT)
        );
        row.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        PLAYER_ROW_HEIGHT
                )
        );
        row.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        JLabel nameLabel = new JLabel(playerName);

        nameLabel.setForeground(
                ColorScheme.LIGHT_GRAY_COLOR
        );

        JButton removeButton = new JButton("\u2715");

        removeButton.setFocusable(false);
        removeButton.setToolTipText(
                "Remove " + playerName
        );
        removeButton.setPreferredSize(
                new Dimension(30, 24)
        );
        removeButton.setMargin(
                new java.awt.Insets(1, 5, 1, 5)
        );

        removeButton.addActionListener(event ->
        {
            removalAction.remove(playerName);
            refresh();
        });

        row.add(nameLabel, BorderLayout.CENTER);
        row.add(removeButton, BorderLayout.EAST);

        return row;
    }

    private static class ThinScrollBarUI extends BasicScrollBarUI
    {
        private static final Dimension ZERO_SIZE =
                new Dimension(0, 0);

        @Override
        protected void paintTrack(
                Graphics graphics,
                JComponent component,
                Rectangle trackBounds)
        {
            graphics.setColor(
                    ColorScheme.SCROLL_TRACK_COLOR
            );

            graphics.fillRect(
                    trackBounds.x,
                    trackBounds.y,
                    trackBounds.width,
                    trackBounds.height
            );
        }

        @Override
        protected void paintThumb(
                Graphics graphics,
                JComponent component,
                Rectangle thumbBounds)
        {
            if (!component.isEnabled()
                    || thumbBounds.width <= 0
                    || thumbBounds.height <= 0)
            {
                return;
            }

            graphics.setColor(ColorScheme.MEDIUM_GRAY_COLOR);

            graphics.fillRect(
                    thumbBounds.x,
                    thumbBounds.y,
                    thumbBounds.width,
                    thumbBounds.height
            );
        }

        @Override
        protected JButton createDecreaseButton(
                int orientation)
        {
            return createEmptyButton();
        }

        @Override
        protected JButton createIncreaseButton(
                int orientation)
        {
            return createEmptyButton();
        }

        private JButton createEmptyButton()
        {
            JButton button = new JButton();

            button.setPreferredSize(ZERO_SIZE);
            button.setMinimumSize(ZERO_SIZE);
            button.setMaximumSize(ZERO_SIZE);

            return button;
        }
    }

    @FunctionalInterface
    private interface NameRemovalAction
    {
        void remove(String playerName);
    }
}