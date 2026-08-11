package com.roguechestsfc;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import net.runelite.client.config.ConfigManager;
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

    private static final String CONFIG_GROUP = "roguechestsfc";
    private static final String COLLAPSED_KEY_PREFIX =
            "panelCollapsed.";

    private static final String IGNORED_SECTION_KEY =
            "under84Ignore";
    private static final String BANNED_SECTION_KEY =
            "bannedPlayers";
    private static final String CAPTURED_SECTION_KEY =
            "nearbyOutsiders";
    private static final String OVERTIME_SECTION_KEY =
            "overtimeWhitelist";

    private final RogueChestsFcPlugin plugin;
    private final ConfigManager configManager;

    private String bannedSearchQuery = "";
    private boolean authorized;

    private final JPanel contentContainer = new JPanel();
    private final JPanel partyControlsContainer = new JPanel();
    private final JPasswordField passcodeField = new JPasswordField();
    private final JLabel unlockStatusLabel = new JLabel();

    private final JTextArea ignoredNamesInput = new JTextArea();
    private final JTextArea bannedNamesInput = new JTextArea();
    private final JTextArea overtimeWhitelistInput = new JTextArea();

    private final JPanel ignoredNamesList = new JPanel();
    private final JPanel bannedNamesList = new JPanel();
    private final JPanel capturedNearbyNamesList = new JPanel();
    private final JPanel overtimeWhitelistList = new JPanel();

    @Inject
    public RogueChestsFcPanel(
            RogueChestsFcPlugin plugin,
            ConfigManager configManager)
    {
        super();

        this.plugin = plugin;
        this.configManager = configManager;

        styleScrollPane(getScrollPane());

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 4, 10, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        contentContainer.setLayout(
                new BoxLayout(contentContainer, BoxLayout.Y_AXIS)
        );
        contentContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        contentContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentContainer.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE
                )
        );

        add(contentContainer);

        rebuildPanelContents();
    }

    void setAuthorized(boolean authorized)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(
                    () -> setAuthorized(authorized)
            );
            return;
        }

        if (this.authorized == authorized)
        {
            return;
        }

        this.authorized = authorized;
        rebuildPanelContents();
    }

    private void rebuildPanelContents()
    {
        contentContainer.removeAll();

        contentContainer.add(createHeader());
        contentContainer.add(
                Box.createRigidArea(
                        new Dimension(0, 12)
                )
        );

        if (!authorized)
        {
            contentContainer.add(createLockedPanel());
        }
        else
        {
            contentContainer.add(createPartyControlsSection());
            contentContainer.add(
                    Box.createRigidArea(
                            new Dimension(0, 14)
                    )
            );

            contentContainer.add(createIgnoredNamesSection());
            contentContainer.add(
                    Box.createRigidArea(
                            new Dimension(0, 14)
                    )
            );

            contentContainer.add(createBannedNamesSection());
            contentContainer.add(
                    Box.createRigidArea(
                            new Dimension(0, 14)
                    )
            );

            contentContainer.add(createCapturedNearbyNamesSection());
            contentContainer.add(
                    Box.createRigidArea(
                            new Dimension(0, 14)
                    )
            );

            contentContainer.add(createOvertimeWhitelistSection());
            contentContainer.add(
                    Box.createRigidArea(
                            new Dimension(0, 10)
                    )
            );

            refresh();
        }

        contentContainer.revalidate();
        contentContainer.repaint();

        revalidate();
        repaint();
    }

    private JPanel createLockedPanel()
    {
        JPanel panel = createSectionPanel();

        panel.setBorder(
                new EmptyBorder(0, 8, 0, 8)
        );

        JLabel title = new JLabel(
                "Plugin Locked",
                SwingConstants.CENTER
        );

        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setFont(
                title.getFont().deriveFont(
                        Font.BOLD,
                        14.0f
                )
        );
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        28
                )
        );

        JLabel line1 = new JLabel(
                "Enter your passcode",
                SwingConstants.CENTER
        );

        line1.setForeground(
                ColorScheme.LIGHT_GRAY_COLOR
        );
        line1.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );
        line1.setHorizontalAlignment(
                SwingConstants.CENTER
        );
        line1.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        20
                )
        );

        JLabel line2 = new JLabel(
                "to unlock the plugin.",
                SwingConstants.CENTER
        );

        line2.setForeground(
                ColorScheme.LIGHT_GRAY_COLOR
        );
        line2.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );
        line2.setHorizontalAlignment(
                SwingConstants.CENTER
        );
        line2.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        20
                )
        );

        passcodeField.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );
        passcodeField.setHorizontalAlignment(
                SwingConstants.CENTER
        );
        passcodeField.setPreferredSize(
                new Dimension(0, BUTTON_HEIGHT)
        );
        passcodeField.setMinimumSize(
                new Dimension(0, BUTTON_HEIGHT)
        );
        passcodeField.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        BUTTON_HEIGHT
                )
        );
        passcodeField.setToolTipText(
                "Enter plugin passcode"
        );

        JButton unlockButton = createButton(
                "Unlock",
                this::attemptUnlock
        );

        configureFullWidthButton(unlockButton);
        unlockButton.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        unlockStatusLabel.setForeground(Color.RED);
        unlockStatusLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );
        unlockStatusLabel.setHorizontalAlignment(
                SwingConstants.CENTER
        );
        unlockStatusLabel.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        22
                )
        );
        unlockStatusLabel.setText(" ");

        passcodeField.addActionListener(
                ignored -> attemptUnlock()
        );

        panel.add(title);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(line1);
        panel.add(line2);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(passcodeField);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
        panel.add(unlockButton);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
        panel.add(unlockStatusLabel);

        return panel;
    }

    private void attemptUnlock()
    {
        char[] password = passcodeField.getPassword();

        if (password == null || password.length == 0)
        {
            unlockStatusLabel.setText(
                    "Enter a passcode."
            );
            return;
        }

        String passcode = new String(password);

        try
        {
            if (!plugin.authorize(passcode))
            {
                unlockStatusLabel.setText(
                        "Incorrect passcode."
                );
                passcodeField.selectAll();
                return;
            }

            passcodeField.setText("");
            unlockStatusLabel.setText(" ");
            setAuthorized(true);
        }
        finally
        {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private JPanel createPartyControlsSection()
    {
        partyControlsContainer.removeAll();
        partyControlsContainer.setLayout(
                new BoxLayout(
                        partyControlsContainer,
                        BoxLayout.Y_AXIS
                )
        );
        partyControlsContainer.setBackground(
                ColorScheme.DARK_GRAY_COLOR
        );
        partyControlsContainer.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );
        partyControlsContainer.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE
                )
        );

        if (plugin.shouldShowPartyJoinBanner())
        {
            JPanel banner = createSectionPanel();

            banner.setBackground(
                    ColorScheme.DARKER_GRAY_COLOR
            );
            banner.setBorder(
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(
                                    ColorScheme.BRAND_ORANGE,
                                    1
                            ),
                            new EmptyBorder(8, 8, 8, 8)
                    )
            );

            JLabel heading = new JLabel(
                    "PARTY PLUGIN",
                    SwingConstants.CENTER
            );

            heading.setForeground(
                    ColorScheme.BRAND_ORANGE
            );
            heading.setFont(
                    heading.getFont().deriveFont(
                            Font.BOLD,
                            13.0f
                    )
            );
            heading.setAlignmentX(
                    Component.CENTER_ALIGNMENT
            );
            heading.setHorizontalAlignment(
                    SwingConstants.CENTER
            );
            heading.setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            22
                    )
            );

            JLabel line1 = new JLabel(
                    "Join the Rogue Chests FC",
                    SwingConstants.CENTER
            );

            line1.setForeground(
                    ColorScheme.LIGHT_GRAY_COLOR
            );
            line1.setAlignmentX(
                    Component.CENTER_ALIGNMENT
            );
            line1.setHorizontalAlignment(
                    SwingConstants.CENTER
            );
            line1.setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            20
                    )
            );

            JLabel line2 = new JLabel(
                    "Party?",
                    SwingConstants.CENTER
            );

            line2.setForeground(
                    ColorScheme.LIGHT_GRAY_COLOR
            );
            line2.setAlignmentX(
                    Component.CENTER_ALIGNMENT
            );
            line2.setHorizontalAlignment(
                    SwingConstants.CENTER
            );
            line2.setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            20
                    )
            );

            JButton joinPromptButton = createButton(
                    "Join Party",
                    this::joinParty
            );

            configurePromptButton(
                    joinPromptButton
            );

            JButton notNowButton = createButton(
                    "Not now",
                    this::dismissPartyJoinBanner
            );

            configurePromptButton(
                    notNowButton
            );

            banner.add(heading);
            banner.add(
                    Box.createRigidArea(
                            new Dimension(0, 6)
                    )
            );
            banner.add(line1);
            banner.add(line2);
            banner.add(
                    Box.createRigidArea(
                            new Dimension(0, 8)
                    )
            );
            banner.add(joinPromptButton);
            banner.add(
                    Box.createRigidArea(
                            new Dimension(0, 5)
                    )
            );
            banner.add(notNowButton);

            partyControlsContainer.add(banner);
            partyControlsContainer.add(
                    Box.createRigidArea(
                            new Dimension(0, 8)
                    )
            );
        }

        boolean inParty = plugin.isInParty();

        JButton partyButton = createButton(
                inParty ? "Leave Party" : "Join Party",
                inParty
                        ? this::leaveParty
                        : this::joinParty
        );

        configureFullWidthButton(partyButton);

        partyControlsContainer.add(partyButton);

        return partyControlsContainer;
    }

    private void joinParty()
    {
        plugin.joinStaffParty();
        refreshPartyControls();
    }

    private void leaveParty()
    {
        plugin.leaveStaffParty();
        refreshPartyControls();
    }

    private void dismissPartyJoinBanner()
    {
        plugin.dismissPartyJoinBanner();
        refreshPartyControls();
    }

    private void refreshPartyControls()
    {
        if (!authorized)
        {
            return;
        }

        createPartyControlsSection();

        partyControlsContainer.revalidate();
        partyControlsContainer.repaint();

        contentContainer.revalidate();
        contentContainer.repaint();
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

        JPanel content = createEditableListSection(
                "Ignored players are excluded from the under-84 panel and join warnings.",
                ignoredNamesInput,
                ignoredNamesList,
                this::addIgnoredNames,
                plugin::copyIgnoredNames
        );

        return createCollapsibleSection(
                IGNORED_SECTION_KEY,
                "Under-84 Ignore List",
                content
        );
    }

    private JPanel createBannedNamesSection()
    {
        configureInputArea(bannedNamesInput);
        configureListPanel(bannedNamesList);

        JPanel content = createSectionPanel();

        JLabel description = createSectionDescription(
                "Banned players are marked red with BAN and do not receive Hiscore lookups."
        );

        JScrollPane inputScrollPane =
                createInputScrollPane(bannedNamesInput);

        JButton addButton = createButton(
                "Add Players",
                this::addBannedNames
        );

        JButton searchButton = createButton(
                "Search",
                this::searchBannedNames
        );

        JButton copyButton = createButton(
                "Copy All",
                plugin::copyBannedNames
        );

        JButton clearAllButton = createButton(
                "Clear All",
                this::confirmClearBannedNames
        );

        JPanel firstActionRow = createButtonRow(
                addButton,
                searchButton
        );

        JPanel secondActionRow = createButtonRow(
                copyButton,
                clearAllButton
        );

        JScrollPane listScrollPane = createListScrollPane(
                bannedNamesList,
                STANDARD_LIST_HEIGHT
        );

        content.add(description);
        content.add(inputScrollPane);
        content.add(Box.createRigidArea(new Dimension(0, 5)));
        content.add(firstActionRow);
        content.add(Box.createRigidArea(new Dimension(0, 5)));
        content.add(secondActionRow);
        content.add(Box.createRigidArea(new Dimension(0, 7)));
        content.add(listScrollPane);

        return createCollapsibleSection(
                BANNED_SECTION_KEY,
                "Banned Players",
                content
        );
    }

    private JPanel createCapturedNearbyNamesSection()
    {
        configureListPanel(capturedNearbyNamesList);

        JPanel content = createSectionPanel();

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

        content.add(description);
        content.add(listScrollPane);
        content.add(Box.createRigidArea(new Dimension(0, 5)));
        content.add(actionRow);
        content.add(Box.createRigidArea(new Dimension(0, 5)));
        content.add(addToBanButton);

        return createCollapsibleSection(
                CAPTURED_SECTION_KEY,
                "Nearby Outsiders",
                content
        );
    }

    private JPanel createOvertimeWhitelistSection()
    {
        configureInputArea(overtimeWhitelistInput);
        configureListPanel(overtimeWhitelistList);

        JPanel content = createEditableListSection(
                "Whitelisted players are excluded from overtime tracking, the overtime panel, and overtime notifications.",
                overtimeWhitelistInput,
                overtimeWhitelistList,
                this::addOvertimeWhitelistNames,
                plugin::copyOvertimeWhitelistNames
        );

        return createCollapsibleSection(
                OVERTIME_SECTION_KEY,
                "Overtime Whitelist",
                content
        );
    }

    private JPanel createEditableListSection(
            String descriptionText,
            JTextArea input,
            JPanel listPanel,
            Runnable addAction,
            Runnable copyAction)
    {
        JPanel section = createSectionPanel();

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

    private JPanel createCollapsibleSection(
            String sectionKey,
            String titleText,
            JPanel content)
    {
        JPanel container = new JPanel();

        container.setLayout(
                new BoxLayout(container, BoxLayout.Y_AXIS)
        );
        container.setBackground(ColorScheme.DARK_GRAY_COLOR);
        container.setBorder(null);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE
                )
        );

        JPanel header = new JPanel(new BorderLayout(6, 0));

        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(new EmptyBorder(6, 7, 6, 7));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );
        header.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        30
                )
        );

        boolean collapsed =
                isSectionCollapsed(sectionKey);

        JLabel arrow = new JLabel(
                collapsed ? "▶" : "▼"
        );
        arrow.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JLabel title = new JLabel(titleText);
        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setFont(
                title.getFont().deriveFont(Font.BOLD)
        );

        header.add(arrow, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);

        Component spacer =
                Box.createRigidArea(new Dimension(0, 6));

        content.setVisible(!collapsed);
        spacer.setVisible(!collapsed);

        MouseAdapter toggleListener = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent ignored)
            {
                boolean expanded = content.isVisible();
                boolean collapsedNow = expanded;

                content.setVisible(!expanded);
                spacer.setVisible(!expanded);
                arrow.setText(expanded ? "▶" : "▼");

                saveSectionCollapsed(
                        sectionKey,
                        collapsedNow
                );

                container.revalidate();
                container.repaint();

                RogueChestsFcPanel.this.revalidate();
                RogueChestsFcPanel.this.repaint();
            }
        };

        header.addMouseListener(toggleListener);
        arrow.addMouseListener(toggleListener);
        title.addMouseListener(toggleListener);

        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        container.add(header);
        container.add(spacer);
        container.add(content);

        return container;
    }

    private boolean isSectionCollapsed(
            String sectionKey)
    {
        String value = configManager.getConfiguration(
                CONFIG_GROUP,
                COLLAPSED_KEY_PREFIX + sectionKey
        );

        return Boolean.parseBoolean(value);
    }

    private void saveSectionCollapsed(
            String sectionKey,
            boolean collapsed)
    {
        configManager.setConfiguration(
                CONFIG_GROUP,
                COLLAPSED_KEY_PREFIX + sectionKey,
                collapsed
        );
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
                ignored -> action.run()
        );

        return button;
    }

    private void configurePromptButton(
            JButton button)
    {
        button.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );
        button.setPreferredSize(
                new Dimension(110, BUTTON_HEIGHT)
        );
        button.setMinimumSize(
                new Dimension(110, BUTTON_HEIGHT)
        );
        button.setMaximumSize(
                new Dimension(110, BUTTON_HEIGHT)
        );
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
        bannedSearchQuery = "";
        refresh();
    }

    private void searchBannedNames()
    {
        String input = bannedNamesInput.getText();

        bannedSearchQuery =
                input == null
                        ? ""
                        : input.trim();

        refreshBannedList();
    }

    private void confirmClearBannedNames()
    {
        if (plugin.getBannedPlayerNames().isEmpty())
        {
            return;
        }

        int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to clear the entire banned players list?",
                "Clear Banned Players",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (result != JOptionPane.YES_OPTION)
        {
            return;
        }

        bannedSearchQuery = "";
        bannedNamesInput.setText("");
        plugin.clearBannedNames();
    }

    private void addOvertimeWhitelistNames()
    {
        String input = overtimeWhitelistInput.getText();

        if (input == null || input.trim().isEmpty())
        {
            return;
        }

        plugin.addOvertimeWhitelistNames(input);
        overtimeWhitelistInput.setText("");
        refresh();
    }

    public void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        if (!authorized)
        {
            return;
        }

        refreshPartyControls();

        rebuildList(
                ignoredNamesList,
                plugin.getIgnoredPlayerNames(),
                plugin::removeIgnoredName
        );

        refreshBannedList();

        rebuildList(
                capturedNearbyNamesList,
                plugin.getCapturedNearbyPlayerNames(),
                plugin::removeCapturedNearbyName
        );

        rebuildList(
                overtimeWhitelistList,
                plugin.getOvertimeWhitelistPlayerNames(),
                plugin::removeOvertimeWhitelistName
        );

        revalidate();
        repaint();
    }

    private void refreshBannedList()
    {
        List<String> bannedNames =
                plugin.getBannedPlayerNames();

        String normalizedQuery =
                bannedSearchQuery.trim()
                        .toLowerCase(Locale.ROOT);

        if (!normalizedQuery.isEmpty())
        {
            List<String> filteredNames =
                    new ArrayList<>();

            for (String name : bannedNames)
            {
                if (name.toLowerCase(Locale.ROOT)
                        .contains(normalizedQuery))
                {
                    filteredNames.add(name);
                }
            }

            bannedNames = filteredNames;
        }

        rebuildList(
                bannedNamesList,
                bannedNames,
                plugin::removeBannedName
        );
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

        removeButton.addActionListener(ignored ->
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