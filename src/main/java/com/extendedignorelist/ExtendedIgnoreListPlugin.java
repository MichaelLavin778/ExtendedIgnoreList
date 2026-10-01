package com.extendedignorelist;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Ignore;
import net.runelite.api.MenuAction;
import net.runelite.api.MessageNode;
import net.runelite.api.NameableContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.api.Renderable;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.account.SessionManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.events.SessionClose;
import net.runelite.client.events.SessionOpen;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
    name = "Extended Ignore List",
    description = "Extend your ignore list limit",
    tags = {"ignore", "list", "social", "chat"}
)
public class ExtendedIgnoreListPlugin extends Plugin
{
    private static final String ADD_IGNORE_MENU_OPTION = "Add ignore";
    private static final String ADD_TO_EXTENDED_MENU_OPTION = "Add to extended";
    private static final String ADD_NAME_MENU_OPTION = "Add Name";
    private static final String REMOVE_IGNORE_MENU_OPTION = "Delete";
    private static final String REMOVE_GIM_IGNORE_MENU_OPTION = "Remove ignore";
    private static final String DEL_NAME_MENU_OPTION = "Del Name";
    private static final String CONFIG_GROUP = "extendedignorelist";
    private static final String PLAYER_MENU_OPTION_KEY = "playerMenuOption";
    private static final String LEGACY_PLAYER_MENU_OPTION_KEY = "showMenuEntryOption";
    private static final String IGNORED_PLAYERS_CONFIG_KEY = "ignoredPlayers";
    private static final String LEGACY_SERIALIZED_ROW_VERSION = "v2";
    private static final String SERIALIZED_ROW_VERSION = "v3";
    private static final String CHAT_FILTER_CHECK_EVENT = "chatFilterCheck";
    private static final int NATIVE_ACTION_SYNC_TICKS = 30;

    private static final Set<ChatMessageType> FILTERED_CHAT_TYPES = EnumSet.of(
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.MODCHAT,
        ChatMessageType.PRIVATECHAT,
        ChatMessageType.MODPRIVATECHAT,
        ChatMessageType.FRIENDSCHAT,
        ChatMessageType.CLAN_CHAT,
        ChatMessageType.CLAN_GUEST_CHAT,
        ChatMessageType.CLAN_GIM_CHAT
    );

    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ConfigManager configManager;

    @Inject
    private RenderCallbackManager renderCallbackManager;

    @Inject
    private MenuManager menuManager;

    @Inject
    private SessionManager sessionManager;

    private final Map<String, IgnoredPlayer> ignoredPlayers = new LinkedHashMap<>();
    private final Set<String> ignoredNameIndex = new HashSet<>();

    private NavigationButton navigationButton;
    private ExtendedIgnoreListPanel panel;
    private ExtendedIgnoreListConfig activeConfig;
    private boolean addIgnoreMenuRegistered;
    private boolean drawListenerRegistered;
    private int pendingNativeIgnoreImportTicks;
    private int pendingNativeIgnoreRemovalTicks;
    private final Set<String> previousNativeIgnoreNames = new HashSet<>();
    private String nativeIgnoreFingerprint;
    private final Set<String> nativeIgnoreNamesBeforeAddAttempt = new HashSet<>();
    private boolean nativeAddObserved;
    private String pendingNativeAddFallbackName;
    private String nativeAddDialogBaseline;
    private String nativeAddMesLayerBaseline;
    private String nativeAddChatInputBaseline;
    private final RenderCallback drawListener = new RenderCallback()
    {
        @Override
        public boolean addEntity(Renderable renderable, boolean ui)
        {
            return shouldDraw(renderable, ui);
        }
    };

    @Provides
    ExtendedIgnoreListConfig provideConfig()
    {
        return configManager.getConfig(ExtendedIgnoreListConfig.class);
    }

    @Override
    protected void startUp()
    {
        migratePlayerMenuOptionKey();
        activeConfig = provideConfig();
        loadIgnoredPlayersForCurrentSession();
        panel = new ExtendedIgnoreListPanel(this::handleImportIgnoreList, this::handlePanelRemovePlayer, this::updatePlayerNote);
        refreshPanelPlayers();
        syncAddIgnoreMenuItem();
        syncDrawListener();

        navigationButton = NavigationButton.builder()
            .tooltip("Extended Ignore List")
            .icon(createSidebarIcon())
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navigationButton);
    }

    @Override
    protected void shutDown()
    {
        if (drawListenerRegistered)
        {
            renderCallbackManager.unregister(drawListener);
            drawListenerRegistered = false;
        }
        removeAddIgnoreMenuItem();

        if (navigationButton != null)
        {
            clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
        }

        panel = null;
        activeConfig = null;
        ignoredPlayers.clear();
        ignoredNameIndex.clear();
    }

    public void addIgnoredPlayer(String playerName)
    {
        syncIgnoredPlayersWithNativeContainer();

        String normalizedName = normalizeName(playerName);
        if (normalizedName == null)
        {
            return;
        }

        IgnoredPlayer ignoredPlayer = findIgnoredPlayerByName(playerName);
        if (ignoredPlayer == null)
        {
            ignoredPlayer = new IgnoredPlayer(playerName);
        }

        IgnoredPlayer trackedPlayer = ignoredPlayer;
        ignoredPlayers.entrySet().removeIf(entry -> entry.getValue() == trackedPlayer);
        trackedPlayer.setCurrentName(playerName);
        trackedPlayer.addAlias(playerName);
        ignoredPlayers.put(normalizedName, trackedPlayer);
        nativeIgnoreFingerprint = null;
        persistIgnoredPlayers();
        refreshPanelPlayers();
    }

    public void removeIgnoredPlayer(String playerName)
    {
        syncIgnoredPlayersWithNativeContainer();

        IgnoredPlayer ignoredPlayer = findIgnoredPlayerByName(playerName);
        if (ignoredPlayer != null)
        {
            ignoredPlayers.entrySet().removeIf(entry -> entry.getValue() == ignoredPlayer);
            nativeIgnoreFingerprint = null;
            persistIgnoredPlayers();
            refreshPanelPlayers();
        }
    }

    private void updatePlayerNote(String playerName, String note)
    {
        IgnoredPlayer ignoredPlayer = findIgnoredPlayerByName(playerName);
        if (ignoredPlayer == null)
        {
            return;
        }

        ignoredPlayer.setNote(note);
        persistIgnoredPlayers();
        updatePanelPlayers();
    }

    private void handlePanelRemovePlayer(String playerName)
    {
        if (!provideConfig().deleteConfirmation()
            || JOptionPane.showConfirmDialog(
                panel,
                "Delete " + playerName + "?",
                "Delete Confirmation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            ) == JOptionPane.YES_OPTION)
        {
            removeIgnoredPlayer(playerName);
        }
    }

    public List<IgnoredPlayer> getIgnoredPlayers()
    {
        return new ArrayList<>(ignoredPlayers.values());
    }

    private boolean shouldDraw(net.runelite.api.Renderable renderable, boolean drawingUI)
    {
        if (!(renderable instanceof net.runelite.api.Player))
        {
            return true;
        }

        net.runelite.api.Player player = (net.runelite.api.Player) renderable;
        String playerName = player.getName();
        if (playerName == null || playerName.isEmpty())
        {
            return true;
        }

        return !isIgnoredPlayerNameFast(playerName);
    }

    private void syncDrawListener()
    {
        ExtendedIgnoreListConfig config = activeConfig == null ? provideConfig() : activeConfig;
        boolean shouldRegister = config.hidePlayers();
        if (shouldRegister && !drawListenerRegistered)
        {
            renderCallbackManager.register(drawListener);
            drawListenerRegistered = true;
        }
        else if (!shouldRegister && drawListenerRegistered)
        {
            renderCallbackManager.unregister(drawListener);
            drawListenerRegistered = false;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!CONFIG_GROUP.equals(event.getGroup()))
        {
            return;
        }

        if (PLAYER_MENU_OPTION_KEY.equals(event.getKey()))
        {
            syncAddIgnoreMenuItem();
        }

        if ("hidePlayers".equals(event.getKey()))
        {
            syncDrawListener();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (isNativeAddNameAction(event))
        {
            nativeIgnoreNamesBeforeAddAttempt.clear();
            nativeIgnoreNamesBeforeAddAttempt.addAll(getNativeIgnoreCurrentNames());
            nativeAddObserved = false;
            pendingNativeAddFallbackName = null;
            nativeAddDialogBaseline = sanitizeCandidateName(client.getVarcStrValue(VarClientID.LAST_NAMEDIALOG));
            nativeAddMesLayerBaseline = sanitizeCandidateName(client.getVarcStrValue(VarClientID.MESLAYERINPUT));
            nativeAddChatInputBaseline = sanitizeCandidateName(client.getVarcStrValue(VarClientID.CHATINPUT));
            pendingNativeIgnoreImportTicks = NATIVE_ACTION_SYNC_TICKS;
        }

        if (isNativeDelNameAction(event) && provideConfig().syncRemoveIgnore())
        {
            pendingNativeIgnoreRemovalTicks = NATIVE_ACTION_SYNC_TICKS;
        }

        if (ADD_IGNORE_MENU_OPTION.equals(event.getMenuOption()))
        {
            String playerName = getClickedPlayerName(event);
            if (playerName == null || playerName.isEmpty())
            {
                return;
            }

            addIgnoredPlayer(playerName);
            return;
        }

        if (!provideConfig().syncRemoveIgnore())
        {
            return;
        }

        if (!isNativeRemoveIgnoreAction(event))
        {
            return;
        }

        String playerName = getClickedPlayerName(event);
        if (playerName == null || playerName.isEmpty())
        {
            return;
        }

        removeIgnoredPlayerByName(playerName);
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (!provideConfig().ignoreListMenuOption())
        {
            return;
        }

        int groupId = WidgetUtil.componentToInterface(event.getActionParam1());
        if ((groupId != InterfaceID.IGNORE && groupId != InterfaceID.GIM_SIDEPANEL)
            || (!REMOVE_IGNORE_MENU_OPTION.equals(event.getOption()) && !REMOVE_GIM_IGNORE_MENU_OPTION.equals(event.getOption())))
        {
            return;
        }

        client.getMenu().createMenuEntry(-1)
            .setOption(ADD_TO_EXTENDED_MENU_OPTION)
            .setTarget(event.getTarget())
            .setType(MenuAction.RUNELITE)
            .onClick(entry -> addIgnoredPlayer(extractPlayerName(entry.getTarget())));
    }

    @Subscribe
    public void onSessionOpen(SessionOpen sessionOpen)
    {
        loadIgnoredPlayersForCurrentSession();
        refreshPanelPlayers();
    }

    @Subscribe
    public void onSessionClose(SessionClose sessionClose)
    {
        ignoredPlayers.clear();
        ignoredNameIndex.clear();
        nativeIgnoreFingerprint = null;
        previousNativeIgnoreNames.clear();
        refreshPanelPlayers();
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        boolean nativeChanged = syncIgnoredPlayersWithNativeContainer();
        boolean pendingActionActive = pendingNativeIgnoreImportTicks > 0 || pendingNativeIgnoreRemovalTicks > 0;
        boolean pendingChanged = syncPendingNativeIgnoreActions();
        if (nativeChanged || pendingActionActive)
        {
            updateNativeIgnoreSnapshot();
        }
        if (nativeChanged || pendingChanged)
        {
            refreshPanelPlayers();
        }
    }

    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        if (!CHAT_FILTER_CHECK_EVENT.equals(event.getEventName()))
        {
            return;
        }

        int[] intStack = client.getIntStack();
        int intStackSize = client.getIntStackSize();
        if (intStackSize < 3)
        {
            return;
        }

        ChatMessageType chatMessageType = ChatMessageType.of(intStack[intStackSize - 2]);
        if (!FILTERED_CHAT_TYPES.contains(chatMessageType))
        {
            return;
        }

        MessageNode messageNode = client.getMessages().get(intStack[intStackSize - 1]);
        if (messageNode == null || !isIgnoredPlayerName(messageNode.getName()))
        {
            return;
        }

        intStack[intStackSize - 3] = 0;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!provideConfig().ignoreTrades() || event.getType() != ChatMessageType.TRADEREQ)
        {
            return;
        }

        String message = event.getMessage();
        if (message == null || !message.contains("wishes to trade with you."))
        {
            return;
        }

        String senderName = event.getName() == null ? null : Text.removeTags(event.getName());
        if (senderName == null || senderName.isEmpty() || !isIgnoredPlayerName(senderName))
        {
            return;
        }

        MessageNode messageNode = event.getMessageNode();
        if (messageNode != null)
        {
            messageNode.setRuneLiteFormatMessage("");
            messageNode.setValue("");
        }
    }

    private void handleImportIgnoreList()
    {
        if (!canImportNativeIgnores())
        {
            refreshImportButtonState();
            return;
        }

        log.debug("Import ignore list requested.");
        importMissingNativeIgnores();
        refreshPanelPlayers();
    }

    private boolean importMissingNativeIgnores()
    {
        NameableContainer<Ignore> ignoreContainer = client.getIgnoreContainer();
        if (ignoreContainer == null)
        {
            return false;
        }

        Ignore[] members = ignoreContainer.getMembers();
        if (members == null || members.length == 0)
        {
            return false;
        }

        boolean changed = false;
        for (Ignore member : members)
        {
            if (member == null)
            {
                continue;
            }

            String currentName = member.getName();
            if (currentName == null || currentName.isEmpty())
            {
                continue;
            }

            IgnoredPlayer ignoredPlayer = findIgnoredPlayerByName(currentName);
            if (ignoredPlayer == null)
            {
                ignoredPlayer = new IgnoredPlayer(currentName);
                ignoredPlayers.put(normalizeName(currentName), ignoredPlayer);
                changed = true;
            }

            String previousName = member.getPrevName();
            if (previousName != null && !previousName.isEmpty())
            {
                int aliasesBefore = ignoredPlayer.getAliases().size();
                ignoredPlayer.addAlias(previousName);
                changed |= aliasesBefore != ignoredPlayer.getAliases().size();
            }
        }

        if (changed)
        {
            nativeIgnoreFingerprint = null;
            persistIgnoredPlayers();
        }

        return changed;
    }

    private void refreshPanelPlayers()
    {
        syncIgnoredPlayersWithNativeContainer();
        updatePanelPlayers();
    }

    private void updatePanelPlayers()
    {
        rebuildIgnoredNameIndex();
        if (panel != null)
        {
            panel.setPlayers(getIgnoredPlayers());
        }

        refreshImportButtonState();
    }

    private void refreshImportButtonState()
    {
        if (panel == null)
        {
            return;
        }

        String accountStorageKey = getAccountStorageKey();
        if (accountStorageKey == null)
        {
            panel.setImportButtonState(false, "Log in to import your ignore list.");
            return;
        }

        int missingEntries = countMissingNativeIgnoreEntries();
        if (missingEntries < 0)
        {
            return;
        }

        if (missingEntries <= 0)
        {
            panel.setImportButtonState(false, "No new native ignore list entries to import.");
            return;
        }

        panel.setImportButtonState(true, null);
    }

    private boolean canImportNativeIgnores()
    {
        return getAccountStorageKey() != null && countMissingNativeIgnoreEntries() > 0;
    }

    private int countMissingNativeIgnoreEntries()
    {
        NameableContainer<Ignore> ignoreContainer = client.getIgnoreContainer();
        if (ignoreContainer == null)
        {
            return -1;
        }

        Ignore[] members = ignoreContainer.getMembers();
        if (members == null)
        {
            return -1;
        }

        if (members.length == 0)
        {
            return 0;
        }

        int missingEntries = 0;
        for (Ignore member : members)
        {
            if (member == null)
            {
                continue;
            }

            String currentName = member.getName();
            if (currentName == null || currentName.isEmpty())
            {
                continue;
            }

            IgnoredPlayer ignoredPlayer = findIgnoredPlayerByName(currentName);
            if (ignoredPlayer == null)
            {
                missingEntries++;
            }
        }

        return missingEntries;
    }

    private void loadIgnoredPlayersForCurrentSession()
    {
        ignoredPlayers.clear();
        ignoredNameIndex.clear();
        nativeIgnoreFingerprint = null;
        previousNativeIgnoreNames.clear();

        String accountStorageKey = getAccountStorageKey();
        if (accountStorageKey == null)
        {
            return;
        }

        String serializedPlayers = configManager.getConfiguration(CONFIG_GROUP, accountStorageKey, IGNORED_PLAYERS_CONFIG_KEY);
        if (serializedPlayers == null || serializedPlayers.isEmpty())
        {
            serializedPlayers = configManager.getConfiguration(CONFIG_GROUP, IGNORED_PLAYERS_CONFIG_KEY);
            if (serializedPlayers == null || serializedPlayers.isEmpty())
            {
                return;
            }
        }

        List<IgnoredPlayer> loadedPlayers = new ArrayList<>();
        String[] rows = serializedPlayers.split("\\n");
        for (String row : rows)
        {
            if (row.isEmpty())
            {
                continue;
            }

            String[] parts = row.split("\\t", -1);
            if (parts.length == 0)
            {
                log.debug("Skipping malformed ignored player row: {}", row);
                continue;
            }

            if (SERIALIZED_ROW_VERSION.equalsIgnoreCase(parts[0])
                || LEGACY_SERIALIZED_ROW_VERSION.equalsIgnoreCase(parts[0]))
            {
                if (parts.length < 2)
                {
                    log.debug("Skipping malformed versioned ignored player row: {}", row);
                    continue;
                }

                IgnoredPlayer ignoredPlayer = new IgnoredPlayer(parts[1]);
                if (parts.length > 2 && !parts[2].isEmpty())
                {
                    String[] aliases = parts[2].split("\\|", -1);
                    for (String alias : aliases)
                    {
                        ignoredPlayer.addAlias(alias);
                    }
                }

                if (SERIALIZED_ROW_VERSION.equalsIgnoreCase(parts[0]) && parts.length > 3)
                {
                    ignoredPlayer.setNote(parts[3]);
                }

                loadedPlayers.add(ignoredPlayer);
                continue;
            }

            if (isLegacySerializedId(parts[0]))
            {
                if (parts.length < 2)
                {
                    log.debug("Skipping malformed legacy ignored player row: {}", row);
                    continue;
                }

                loadedPlayers.add(new IgnoredPlayer(parts[1]));
                continue;
            }

            IgnoredPlayer ignoredPlayer = new IgnoredPlayer(parts[0]);
            if (parts.length > 1 && !parts[1].isEmpty())
            {
                String[] aliases = parts[1].split("\\|", -1);
                for (String alias : aliases)
                {
                    ignoredPlayer.addAlias(alias);
                }
            }

            loadedPlayers.add(ignoredPlayer);
        }

        for (IgnoredPlayer player : loadedPlayers)
        {
            ignoredPlayers.put(normalizeName(player.getCurrentName()), player);
        }

        rebuildIgnoredNameIndex();

        persistIgnoredPlayers();
        configManager.unsetConfiguration(CONFIG_GROUP, IGNORED_PLAYERS_CONFIG_KEY);
    }

    private void persistIgnoredPlayers()
    {
        String accountStorageKey = getAccountStorageKey();
        if (accountStorageKey == null)
        {
            return;
        }

        if (ignoredPlayers.isEmpty())
        {
            configManager.unsetConfiguration(CONFIG_GROUP, accountStorageKey, IGNORED_PLAYERS_CONFIG_KEY);
            return;
        }

        StringBuilder serializedPlayers = new StringBuilder();
        for (IgnoredPlayer player : ignoredPlayers.values())
        {
            if (serializedPlayers.length() > 0)
            {
                serializedPlayers.append('\n');
            }

            serializedPlayers.append(SERIALIZED_ROW_VERSION)
                .append('\t')
                .append(player.getCurrentName())
                .append('\t');

            List<String> aliases = player.getAliases();
            if (!aliases.isEmpty())
            {
                serializedPlayers.append(String.join("|", aliases));
            }

            serializedPlayers.append('\t').append(sanitizeNote(player.getNote()));
        }

        configManager.setConfiguration(CONFIG_GROUP, accountStorageKey, IGNORED_PLAYERS_CONFIG_KEY, serializedPlayers.toString());
    }

    private String getAccountStorageKey()
    {
        if (sessionManager.getAccountSession() == null)
        {
            return null;
        }

        if (sessionManager.getAccountSession().getUuid() == null)
        {
            return null;
        }

        return sessionManager.getAccountSession().getUuid().toString();
    }

    private void syncAddIgnoreMenuItem()
    {
        if (provideConfig().playerMenuOption())
        {
            registerAddIgnoreMenuItem();
            return;
        }

        removeAddIgnoreMenuItem();
    }

    private void migratePlayerMenuOptionKey()
    {
        String currentValue = configManager.getConfiguration(CONFIG_GROUP, PLAYER_MENU_OPTION_KEY);
        String legacyValue = configManager.getConfiguration(CONFIG_GROUP, LEGACY_PLAYER_MENU_OPTION_KEY);
        if (currentValue == null && legacyValue != null)
        {
            configManager.setConfiguration(CONFIG_GROUP, PLAYER_MENU_OPTION_KEY, legacyValue);
        }

        if (legacyValue != null)
        {
            configManager.unsetConfiguration(CONFIG_GROUP, LEGACY_PLAYER_MENU_OPTION_KEY);
        }
    }

    private void registerAddIgnoreMenuItem()
    {
        if (addIgnoreMenuRegistered)
        {
            return;
        }

        menuManager.addPlayerMenuItem(ADD_IGNORE_MENU_OPTION);
        addIgnoreMenuRegistered = true;
    }

    private void removeAddIgnoreMenuItem()
    {
        if (!addIgnoreMenuRegistered)
        {
            return;
        }

        menuManager.removePlayerMenuItem(ADD_IGNORE_MENU_OPTION);
        addIgnoreMenuRegistered = false;
    }

    private String extractPlayerName(String menuTarget)
    {
        String withoutTags = menuTarget.replaceAll("<[^>]+>", "").trim();
        int levelSuffix = withoutTags.indexOf("  (");
        if (levelSuffix >= 0)
        {
            return withoutTags.substring(0, levelSuffix).trim();
        }

        return withoutTags;
    }

    private String getClickedPlayerName(MenuOptionClicked event)
    {
        if (event.getMenuTarget() != null)
        {
            return extractPlayerName(event.getMenuTarget());
        }

        return null;
    }

    private void removeIgnoredPlayerByName(String playerName)
    {
        syncIgnoredPlayersWithNativeContainer();

        IgnoredPlayer ignoredPlayer = findIgnoredPlayerByName(playerName);
        if (ignoredPlayer != null)
        {
            ignoredPlayers.entrySet().removeIf(entry -> entry.getValue() == ignoredPlayer);
            persistIgnoredPlayers();
            refreshPanelPlayers();
        }
    }

    private boolean isNativeRemoveIgnoreAction(MenuOptionClicked event)
    {
        String option = event.getMenuOption();
        if (!REMOVE_IGNORE_MENU_OPTION.equals(option) && !REMOVE_GIM_IGNORE_MENU_OPTION.equals(option))
        {
            return false;
        }

        if (event.getParam1() <= 0)
        {
            return false;
        }

        int groupId = WidgetUtil.componentToInterface(event.getParam1());
        return groupId == InterfaceID.IGNORE || groupId == InterfaceID.GIM_SIDEPANEL;
    }

    private boolean isNativeAddNameAction(MenuOptionClicked event)
    {
        return ADD_NAME_MENU_OPTION.equalsIgnoreCase(event.getMenuOption());
    }

    private boolean isNativeDelNameAction(MenuOptionClicked event)
    {
        return DEL_NAME_MENU_OPTION.equalsIgnoreCase(event.getMenuOption()) && isNativeIgnoreListAction(event);
    }

    private boolean isNativeIgnoreListAction(MenuOptionClicked event)
    {
        if (event.getParam1() <= 0)
        {
            return false;
        }

        int groupId = WidgetUtil.componentToInterface(event.getParam1());
        return groupId == InterfaceID.IGNORE || groupId == InterfaceID.GIM_SIDEPANEL;
    }

    private boolean syncPendingNativeIgnoreActions()
    {
        boolean changed = false;
        if (pendingNativeIgnoreImportTicks > 0)
        {
            capturePendingNativeAddNameCandidate();
            changed |= importMissingNativeIgnores();
            if (nativeIgnoreAddDetected())
            {
                nativeAddObserved = true;
            }
            pendingNativeIgnoreImportTicks--;

            if (pendingNativeIgnoreImportTicks == 0 && !nativeAddObserved)
            {
                addPendingFallbackNameToExtendedList();
                changed = true;
            }
        }

        if (pendingNativeIgnoreRemovalTicks > 0 && provideConfig().syncRemoveIgnore())
        {
            changed |= removeNamesNoLongerInNativeList();
            pendingNativeIgnoreRemovalTicks--;
        }

        return changed;
    }

    private void updateNativeIgnoreSnapshot()
    {
        previousNativeIgnoreNames.clear();
        previousNativeIgnoreNames.addAll(getNativeIgnoreCurrentNames());
    }

    private boolean removeNamesNoLongerInNativeList()
    {
        if (previousNativeIgnoreNames.isEmpty())
        {
            return false;
        }

        Set<String> currentNativeIgnoreNames = getNativeIgnoreCurrentNames();
        boolean changed = false;
        for (String previousNativeIgnoreName : previousNativeIgnoreNames)
        {
            if (currentNativeIgnoreNames.contains(previousNativeIgnoreName))
            {
                continue;
            }

            IgnoredPlayer ignoredPlayer = findIgnoredPlayerByName(previousNativeIgnoreName);
            if (ignoredPlayer != null)
            {
                ignoredPlayers.entrySet().removeIf(entry -> entry.getValue() == ignoredPlayer);
                changed = true;
            }
        }

        if (changed)
        {
            persistIgnoredPlayers();
        }

        return changed;
    }

    private Set<String> getNativeIgnoreCurrentNames()
    {
        Set<String> names = new HashSet<>();
        NameableContainer<Ignore> ignoreContainer = client.getIgnoreContainer();
        if (ignoreContainer == null)
        {
            return names;
        }

        Ignore[] members = ignoreContainer.getMembers();
        if (members == null || members.length == 0)
        {
            return names;
        }

        for (Ignore member : members)
        {
            if (member == null)
            {
                continue;
            }

            String normalizedName = normalizeName(member.getName());
            if (normalizedName != null)
            {
                names.add(normalizedName);
            }
        }

        return names;
    }

    private boolean nativeIgnoreAddDetected()
    {
        Set<String> currentNativeIgnoreNames = getNativeIgnoreCurrentNames();
        for (String name : currentNativeIgnoreNames)
        {
            if (!nativeIgnoreNamesBeforeAddAttempt.contains(name))
            {
                return true;
            }
        }

        return false;
    }

    private void capturePendingNativeAddNameCandidate()
    {
        String mesLayerInput = sanitizeCandidateName(client.getVarcStrValue(VarClientID.MESLAYERINPUT));
        if (mesLayerInput != null && !mesLayerInput.equals(nativeAddMesLayerBaseline))
        {
            pendingNativeAddFallbackName = mesLayerInput;
        }

        String lastNameDialogInput = sanitizeCandidateName(client.getVarcStrValue(VarClientID.LAST_NAMEDIALOG));
        if (lastNameDialogInput != null && !lastNameDialogInput.equals(nativeAddDialogBaseline))
        {
            pendingNativeAddFallbackName = lastNameDialogInput;
        }

        String chatInput = sanitizeCandidateName(client.getVarcStrValue(VarClientID.CHATINPUT));
        if (chatInput != null && !chatInput.equals(nativeAddChatInputBaseline))
        {
            pendingNativeAddFallbackName = chatInput;
        }
    }

    private void addPendingFallbackNameToExtendedList()
    {
        if (pendingNativeAddFallbackName == null)
        {
            return;
        }

        if (findIgnoredPlayerByName(pendingNativeAddFallbackName) == null)
        {
            addIgnoredPlayer(pendingNativeAddFallbackName);
        }
    }

    private String sanitizeCandidateName(String input)
    {
        if (input == null)
        {
            return null;
        }

        String cleanedInput = Text.removeTags(input).trim();
        return cleanedInput.isEmpty() ? null : cleanedInput;
    }

    private boolean isIgnoredPlayerName(String playerName)
    {
        if (playerName == null || playerName.isEmpty())
        {
            return false;
        }

        syncIgnoredPlayersWithNativeContainer();
        return isIgnoredPlayerNameFast(playerName);
    }

    private boolean isIgnoredPlayerNameFast(String playerName)
    {
        String normalizedName = normalizeName(playerName);
        return normalizedName != null && ignoredNameIndex.contains(normalizedName);
    }

    private void rebuildIgnoredNameIndex()
    {
        ignoredNameIndex.clear();
        for (IgnoredPlayer ignoredPlayer : ignoredPlayers.values())
        {
            String currentName = normalizeName(ignoredPlayer.getCurrentName());
            if (currentName != null)
            {
                ignoredNameIndex.add(currentName);
            }

            for (String alias : ignoredPlayer.getAliases())
            {
                String normalizedAlias = normalizeName(alias);
                if (normalizedAlias != null)
                {
                    ignoredNameIndex.add(normalizedAlias);
                }
            }
        }
    }

    private boolean syncIgnoredPlayersWithNativeContainer()
    {
        NameableContainer<Ignore> ignoreContainer = client.getIgnoreContainer();
        if (ignoreContainer == null)
        {
            return false;
        }

        Ignore[] members = ignoreContainer.getMembers();
        if (members == null)
        {
            return false;
        }

        String fingerprint = createNativeIgnoreFingerprint(members);
        if (fingerprint.equals(nativeIgnoreFingerprint))
        {
            return false;
        }
        nativeIgnoreFingerprint = fingerprint;

        if (ignoredPlayers.isEmpty() || members.length == 0)
        {
            return true;
        }

        boolean changed = false;
        Map<String, IgnoredPlayer> refreshedPlayers = new LinkedHashMap<>();
        for (IgnoredPlayer ignoredPlayer : ignoredPlayers.values())
        {
            String originalName = ignoredPlayer.getCurrentName();
            String matchedCurrentName = null;
            String matchedPreviousName = null;

            for (Ignore member : members)
            {
                if (member == null)
                {
                    continue;
                }

                if (matchesAnyTrackedName(ignoredPlayer, member.getName(), member.getPrevName()))
                {
                    matchedCurrentName = member.getName();
                    matchedPreviousName = member.getPrevName();
                    break;
                }
            }

            if (matchedCurrentName != null && !matchedCurrentName.isEmpty())
            {
                if (!matchedCurrentName.equals(originalName))
                {
                    ignoredPlayer.addAlias(originalName);
                    ignoredPlayer.setCurrentName(matchedCurrentName);
                    changed = true;
                }

                if (matchedPreviousName != null && !matchedPreviousName.isEmpty())
                {
                    int aliasCountBefore = ignoredPlayer.getAliases().size();
                    ignoredPlayer.addAlias(matchedPreviousName);
                    if (ignoredPlayer.getAliases().size() != aliasCountBefore)
                    {
                        changed = true;
                    }
                }
            }

            refreshedPlayers.put(normalizeName(ignoredPlayer.getCurrentName()), ignoredPlayer);
        }

        if (changed)
        {
            ignoredPlayers.clear();
            ignoredPlayers.putAll(refreshedPlayers);
            rebuildIgnoredNameIndex();
            persistIgnoredPlayers();
        }

        return true;
    }

    private String createNativeIgnoreFingerprint(Ignore[] members)
    {
        StringBuilder fingerprint = new StringBuilder();
        for (Ignore member : members)
        {
            if (member == null)
            {
                continue;
            }

            fingerprint.append(normalizeName(member.getName()))
                .append('|')
                .append(normalizeName(member.getPrevName()))
                .append(';');
        }

        return fingerprint.toString();
    }

    private boolean matchesAnyTrackedName(IgnoredPlayer ignoredPlayer, String... names)
    {
        for (String name : names)
        {
            if (matchesTrackedName(ignoredPlayer, name))
            {
                return true;
            }
        }

        return false;
    }

    private boolean matchesTrackedName(IgnoredPlayer ignoredPlayer, String playerName)
    {
        String normalizedName = normalizeName(playerName);
        if (normalizedName == null)
        {
            return false;
        }

        if (normalizedName.equals(normalizeName(ignoredPlayer.getCurrentName())))
        {
            return true;
        }

        for (String alias : ignoredPlayer.getAliases())
        {
            if (normalizedName.equals(normalizeName(alias)))
            {
                return true;
            }
        }

        return false;
    }

    private IgnoredPlayer findIgnoredPlayerByName(String playerName)
    {
        for (IgnoredPlayer ignoredPlayer : ignoredPlayers.values())
        {
            if (matchesTrackedName(ignoredPlayer, playerName))
            {
                return ignoredPlayer;
            }
        }

        return null;
    }

    private String normalizeName(String playerName)
    {
        if (playerName == null || playerName.isEmpty())
        {
            return null;
        }

        return Text.standardize(playerName);
    }

    private String sanitizeNote(String note)
    {
        if (note == null)
        {
            return "";
        }

        return note.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    private boolean isLegacySerializedId(String value)
    {
        if (value == null || value.isEmpty())
        {
            return false;
        }

        for (int i = 0; i < value.length(); i++)
        {
            if (!Character.isDigit(value.charAt(i)))
            {
                return false;
            }
        }

        return true;
    }

    private BufferedImage createSidebarIcon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        try
        {
            Color tileShadow = new Color(0x12, 0x0F, 0x0D);
            Color tileEdge = new Color(0x4B, 0x3B, 0x2B);
            Color faceOutline = new Color(0x16, 0x0B, 0x0A);
            Color faceShadow = new Color(0x8B, 0x13, 0x16);
            Color faceRed = new Color(0xD9, 0x2B, 0x2F);
            Color faceHighlight = new Color(0xF0, 0x4A, 0x42);
            Color plusShadow = new Color(0x5A, 0x3C, 0x12);
            Color plusYellow = new Color(0xF2, 0xC4, 0x4C);

            graphics.setColor(tileShadow);
            graphics.fillRect(0, 0, 16, 16);
            graphics.setColor(tileEdge);
            graphics.drawRect(1, 1, 13, 13);

            int[] faceX = {2, 3, 3, 4, 4, 11, 11, 12, 12, 13, 13, 12, 12, 11, 11, 4, 4, 3, 3, 2};
            int[] faceY = {5, 5, 4, 4, 3, 3, 4, 4, 5, 5, 11, 11, 12, 12, 13, 13, 12, 12, 11, 11};

            graphics.setColor(faceOutline);
            graphics.fillPolygon(faceX, faceY, faceX.length);

            graphics.setColor(faceShadow);
            graphics.fillRect(4, 5, 8, 7);
            graphics.fillRect(5, 4, 6, 9);
            graphics.setColor(faceRed);
            graphics.fillRect(4, 6, 8, 5);
            graphics.fillRect(5, 5, 6, 7);
            graphics.setColor(faceHighlight);
            graphics.fillRect(5, 5, 3, 1);

            // White pixel eyes with dark pupils and an unmistakable frown.
            graphics.setColor(new Color(0xF4, 0xE8, 0xD2));
            graphics.fillRect(5, 7, 2, 2);
            graphics.fillRect(9, 7, 2, 2);
            graphics.setColor(faceOutline);
            graphics.fillRect(6, 8, 1, 1);
            graphics.fillRect(9, 8, 1, 1);
            graphics.fillRect(5, 10, 1, 1);
            graphics.fillRect(6, 11, 4, 1);
            graphics.fillRect(10, 10, 1, 1);

            // Floating superscript plus with a dark pixel shadow.
            graphics.setColor(plusShadow);
            graphics.fillRect(12, 1, 3, 2);
            graphics.fillRect(13, 0, 2, 4);
            graphics.setColor(plusYellow);
            graphics.fillRect(11, 1, 3, 1);
            graphics.fillRect(12, 0, 1, 3);
        }
        finally
        {
            graphics.dispose();
        }

        return image;
    }
}