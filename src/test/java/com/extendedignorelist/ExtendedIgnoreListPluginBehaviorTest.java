package com.extendedignorelist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Ignore;
import net.runelite.api.Menu;
import net.runelite.api.NameableContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.SessionClose;
import net.runelite.client.events.SessionOpen;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.ui.ClientToolbar;
import org.junit.Before;
import org.junit.Test;

public class ExtendedIgnoreListPluginBehaviorTest
{
    private static final String SHARED_STORAGE_KEY = "rsprofile.extendedignorelist";
    private ExtendedIgnoreListPlugin plugin;
    private ConfigManager configManager;
    private ClientThread clientThread;
    private Client client;
    private ClientToolbar clientToolbar;
    private MenuManager menuManager;
    private RenderCallbackManager renderCallbackManager;
    private Notifier notifier;
    private Map<String, String> configValues;
    private Map<String, String> sharedValues;
    private Map<String, String> profileValues;
    private String accountKey;

    @Before
    public void setUp() throws Exception
    {
        plugin = new ExtendedIgnoreListPlugin();
        configManager = mock(ConfigManager.class);
        clientThread = mock(ClientThread.class);
        client = mock(Client.class);
        clientToolbar = mock(ClientToolbar.class);
        menuManager = mock(MenuManager.class);
        renderCallbackManager = mock(RenderCallbackManager.class);
        notifier = mock(Notifier.class);
        configValues = new HashMap<>();
        sharedValues = new HashMap<>();
        profileValues = new HashMap<>();
        accountKey = "123e4567-e89b-12d3-a456-426614174000";

        when(client.getIgnoreContainer()).thenReturn(null);
        when(configManager.getRSProfileKey()).thenReturn(accountKey);
        when(configManager.getConfig(ExtendedIgnoreListConfig.class)).thenReturn(new TestConfig(configValues));
        doAnswer(invocation ->
        {
            configValues.put("sortOrder", invocation.getArgument(2));
            ConfigChanged event = new ConfigChanged();
            event.setGroup("extendedignorelist");
            event.setKey("sortOrder");
            plugin.onConfigChanged(event);
            return null;
        }).when(configManager).setConfiguration(eq("extendedignorelist"), eq("sortOrder"), anyString());
        when(configManager.getConfiguration(eq("extendedignorelist"), eq("ignoredPlayers"))).thenAnswer(invocation -> profileValues.get("ignoredPlayers"));
        when(configManager.getConfiguration(eq("extendedignorelist"), anyString(), eq("ignoredPlayers"))).thenAnswer(invocation -> configValues.get(key(invocation.getArgument(1), invocation.getArgument(2))));
        when(configManager.getConfiguration(eq("extendedignorelist"), eq(SHARED_STORAGE_KEY), eq("ignoredPlayers"))).thenAnswer(invocation -> sharedValues.get("ignoredPlayers"));
        doAnswer(invocation ->
        {
            sharedValues.put("ignoredPlayers", invocation.getArgument(3));
            plugin.onConfigChanged(sharedListChanged());
            return null;
        }).when(configManager).setConfiguration(eq("extendedignorelist"), eq(SHARED_STORAGE_KEY), eq("ignoredPlayers"), anyString());
        doAnswer(invocation ->
        {
            profileValues.remove("ignoredPlayers");
            return null;
        }).when(configManager).unsetConfiguration(eq("extendedignorelist"), eq("ignoredPlayers"));
        doAnswer(invocation ->
        {
            configValues.remove(key(invocation.getArgument(1), invocation.getArgument(2)));
            return null;
        }).when(configManager).unsetConfiguration(eq("extendedignorelist"), anyString(), eq("ignoredPlayers"));
        doAnswer(invocation ->
        {
            sharedValues.remove("ignoredPlayers");
            plugin.onConfigChanged(sharedListChanged());
            return null;
        }).when(configManager).unsetConfiguration(eq("extendedignorelist"), eq(SHARED_STORAGE_KEY), eq("ignoredPlayers"));
        doAnswer(invocation ->
        {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(clientThread).invoke(org.mockito.ArgumentMatchers.any(Runnable.class));

        setField(plugin, "client", client);
        setField(plugin, "clientToolbar", clientToolbar);
        setField(plugin, "configManager", configManager);
        setField(plugin, "clientThread", clientThread);
        setField(plugin, "activeConfig", new TestConfig(configValues));
        setField(plugin, "menuManager", menuManager);
        setField(plugin, "renderCallbackManager", renderCallbackManager);
        setField(plugin, "notifier", notifier);
    }

    @Test
    public void addIgnoredPlayerPersistsAndTracksAliases() throws Exception
    {
        invoke(plugin, "addIgnoredPlayer", new Class<?>[] {String.class}, "Alice");
        invoke(plugin, "addIgnoredPlayer", new Class<?>[] {String.class}, "Alice Prime");

        List<IgnoredPlayer> players = plugin.getIgnoredPlayers();
        assertEquals(2, players.size());
        IgnoredPlayer alice = findPlayer(players, "Alice");
        IgnoredPlayer alicePrime = findPlayer(players, "Alice Prime");
        assertNotNull(alice);
        assertNotNull(alicePrime);
        assertTrue(alice.getAliases().isEmpty());
        assertTrue(alicePrime.getAliases().isEmpty());
        assertEquals("Alice Prime", players.get(0).getCurrentName());
        assertEquals("Alice", players.get(1).getCurrentName());

        String expected = serializedPlayer("Alice Prime", "", "") + "\n" + serializedPlayer("Alice", "", "");
        verify(configManager).setConfiguration(eq("extendedignorelist"), eq(SHARED_STORAGE_KEY),
            eq("ignoredPlayers"), eq(expected));
        verify(clientThread, never()).invoke(org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    public void addingExistingNameDoesNotCreateDuplicate() throws Exception
    {
        invoke(plugin, "addIgnoredPlayer", new Class<?>[] {String.class}, "Alice");
        invoke(plugin, "addIgnoredPlayer", new Class<?>[] {String.class}, "alice");

        assertEquals(1, plugin.getIgnoredPlayers().size());
        assertTrue(invokeBoolean(plugin, "isIgnoredPlayerName", new Class<?>[] {String.class}, "Alice"));
    }

    @Test
    public void panelDeletionRunsOnClientThreadAndStaysDeletedAcrossTicksAndReload() throws Exception
    {
        setConfigField("deleteConfirmation", false);
        plugin.addIgnoredPlayer("Alice");
        plugin.addIgnoredPlayer("Bob");
        Ignore alice = mock(Ignore.class);
        when(alice.getName()).thenReturn("Alice");
        when(alice.getPrevName()).thenReturn("Alicia");
        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> container = mock(NameableContainer.class);
        when(container.getMembers()).thenReturn(new Ignore[] {alice});
        when(client.getIgnoreContainer()).thenReturn(container);

        AtomicReference<Runnable> queued = new AtomicReference<>();
        doAnswer(invocation ->
        {
            queued.set(invocation.getArgument(0));
            return null;
        }).when(clientThread).invoke(org.mockito.ArgumentMatchers.any(Runnable.class));
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                invoke(plugin, "handlePanelRemovePlayer", new Class<?>[] {String.class}, "Alice");
            }
            catch (Exception ex)
            {
                throw new AssertionError(ex);
            }
        });

        assertNotNull(queued.get());
        assertPlayerNames("Bob", "Alice");
        plugin.onGameTick(new GameTick());
        queued.get().run();
        assertPlayerNames("Bob");
        assertFalse(sharedValues.get("ignoredPlayers").contains("Alice"));

        for (int tick = 0; tick < 35; tick++)
        {
            plugin.onGameTick(new GameTick());
        }
        assertPlayerNames("Bob");
        invokePrivateNoArgs(plugin, "loadIgnoredPlayersForCurrentSession");
        assertPlayerNames("Bob");
    }

    @Test
    public void pendingNativeAddDoesNotRestoreDeletedEntriesAndStillImportsNewNames() throws Exception
    {
        setConfigField("deleteConfirmation", false);
        plugin.addIgnoredPlayer("Alice");
        Ignore alice = mock(Ignore.class);
        when(alice.getName()).thenReturn("Alice");
        Ignore bob = mock(Ignore.class);
        when(bob.getName()).thenReturn("Bob");
        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> container = mock(NameableContainer.class);
        when(container.getMembers()).thenReturn(new Ignore[] {alice});
        when(client.getIgnoreContainer()).thenReturn(container);
        MenuEntry menuEntry = mock(MenuEntry.class);
        when(menuEntry.getOption()).thenReturn("Add Name");
        plugin.onMenuOptionClicked(new net.runelite.api.events.MenuOptionClicked(menuEntry));

        invoke(plugin, "handlePanelRemovePlayer", new Class<?>[] {String.class}, "Alice");
        when(container.getMembers()).thenReturn(new Ignore[] {alice, bob});
        plugin.onGameTick(new GameTick());
        assertPlayerNames("Bob");

        invoke(plugin, "handlePanelRemovePlayer", new Class<?>[] {String.class}, "Bob");
        for (int tick = 0; tick < 35; tick++)
        {
            plugin.onGameTick(new GameTick());
        }
        assertTrue(plugin.getIgnoredPlayers().isEmpty());
        assertFalse(sharedValues.containsKey("ignoredPlayers"));

        invokePrivateNoArgs(plugin, "handleImportIgnoreList");
        assertEquals(2, plugin.getIgnoredPlayers().size());
    }

    @Test
    public void rejectedNativeAddFallbackDoesNotUndoPanelDeletion() throws Exception
    {
        setConfigField("deleteConfirmation", false);
        plugin.addIgnoredPlayer("Bob");
        MenuEntry menuEntry = mock(MenuEntry.class);
        when(menuEntry.getOption()).thenReturn("Add Name");
        when(client.getVarcStrValue(VarClientID.CHATINPUT)).thenReturn(null, "Bob");
        plugin.onMenuOptionClicked(new net.runelite.api.events.MenuOptionClicked(menuEntry));
        plugin.onGameTick(new GameTick());

        invoke(plugin, "handlePanelRemovePlayer", new Class<?>[] {String.class}, "Bob");
        for (int tick = 0; tick < 35; tick++)
        {
            plugin.onGameTick(new GameTick());
        }
        assertTrue(plugin.getIgnoredPlayers().isEmpty());
        assertFalse(sharedValues.containsKey("ignoredPlayers"));
    }

    @Test
    public void queuedPanelDeletionDoesNotRunAfterShutdown() throws Exception
    {
        setConfigField("deleteConfirmation", false);
        plugin.addIgnoredPlayer("Alice");
        String snapshot = sharedValues.get("ignoredPlayers");
        AtomicReference<Runnable> queued = new AtomicReference<>();
        doAnswer(invocation ->
        {
            queued.set(invocation.getArgument(0));
            return null;
        }).when(clientThread).invoke(org.mockito.ArgumentMatchers.any(Runnable.class));

        invoke(plugin, "handlePanelRemovePlayer", new Class<?>[] {String.class}, "Alice");
        assertNotNull(queued.get());
        plugin.shutDown();
        queued.get().run();
        assertEquals(snapshot, sharedValues.get("ignoredPlayers"));
    }

    @Test
    public void additionsFollowAllFourSortOrders() throws Exception
    {
        plugin.addIgnoredPlayer("Charlie");
        plugin.addIgnoredPlayer("alice");
        plugin.addIgnoredPlayer("Bob");
        assertTrue(findPlayer(plugin.getIgnoredPlayers(), "Charlie").getAddedAt() > 0);
        assertTrue(findPlayer(plugin.getIgnoredPlayers(), "Bob").getAddedAt()
            > findPlayer(plugin.getIgnoredPlayers(), "alice").getAddedAt());

        invoke(plugin, "handleSortOrderChange", new Class<?>[] {IgnoreListSortOrder.class},
            IgnoreListSortOrder.NAME_ASCENDING);
        assertPlayerNames("alice", "Bob", "Charlie");
        plugin.addIgnoredPlayer("Aaron");
        assertPlayerNames("Aaron", "alice", "Bob", "Charlie");

        invoke(plugin, "handleSortOrderChange", new Class<?>[] {IgnoreListSortOrder.class},
            IgnoreListSortOrder.NAME_DESCENDING);
        assertPlayerNames("Charlie", "Bob", "alice", "Aaron");
        plugin.addIgnoredPlayer("Bravo");
        assertPlayerNames("Charlie", "Bravo", "Bob", "alice", "Aaron");

        invoke(plugin, "handleSortOrderChange", new Class<?>[] {IgnoreListSortOrder.class},
            IgnoreListSortOrder.OLDEST_FIRST);
        assertPlayerNames("Charlie", "alice", "Bob", "Aaron", "Bravo");
        plugin.addIgnoredPlayer("Delta");
        assertPlayerNames("Charlie", "alice", "Bob", "Aaron", "Bravo", "Delta");

        invoke(plugin, "handleSortOrderChange", new Class<?>[] {IgnoreListSortOrder.class},
            IgnoreListSortOrder.NEWEST_FIRST);
        plugin.addIgnoredPlayer("Echo");
        assertPlayerNames("Echo", "Delta", "Bravo", "Aaron", "Bob", "alice", "Charlie");
    }

    @Test
    public void bulkImportsFollowAllSortOrdersAndKeepExistingDates() throws Exception
    {
        plugin.addIgnoredPlayer("Zoe");
        plugin.addIgnoredPlayer("Liam");
        long zoeAddedAt = findPlayer(plugin.getIgnoredPlayers(), "Zoe").getAddedAt();
        Ignore zoe = mock(Ignore.class);
        when(zoe.getName()).thenReturn("Zoe");
        Ignore charlie = mock(Ignore.class);
        when(charlie.getName()).thenReturn("Charlie");
        Ignore bob = mock(Ignore.class);
        when(bob.getName()).thenReturn("Bob");
        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> container = mock(NameableContainer.class);
        when(container.getMembers()).thenReturn(new Ignore[] {zoe, charlie, bob});
        when(client.getIgnoreContainer()).thenReturn(container);

        for (IgnoreListSortOrder order : IgnoreListSortOrder.values())
        {
            configValues.put("sortOrder", order.name());
            invokePrivateNoArgs(plugin, "handleImportIgnoreList");
            switch (order)
            {
                case NAME_ASCENDING:
                    assertPlayerNames("Bob", "Charlie", "Liam", "Zoe");
                    break;
                case NAME_DESCENDING:
                    assertPlayerNames("Zoe", "Liam", "Charlie", "Bob");
                    break;
                case OLDEST_FIRST:
                    assertPlayerNames("Zoe", "Liam", "Bob", "Charlie");
                    break;
                case NEWEST_FIRST:
                    assertPlayerNames("Charlie", "Bob", "Liam", "Zoe");
                    break;
                default:
                    throw new AssertionError(order);
            }
            assertEquals(zoeAddedAt, findPlayer(plugin.getIgnoredPlayers(), "Zoe").getAddedAt());
            plugin.removeIgnoredPlayer("Bob");
            plugin.removeIgnoredPlayer("Charlie");
        }
    }

    @Test
    public void legacyDatesPreserveOrderAndArePersistedOnlyOnce()
    {
        sharedValues.put("ignoredPlayers", "v3\tZoe\tZoya\tnote\nv2\tAmy\tAmelia\nBob\tBobby");
        plugin.onSessionOpen(new SessionOpen());

        assertPlayerNames("Zoe", "Amy", "Bob");
        long zoeAddedAt = findPlayer(plugin.getIgnoredPlayers(), "Zoe").getAddedAt();
        long amyAddedAt = findPlayer(plugin.getIgnoredPlayers(), "Amy").getAddedAt();
        long bobAddedAt = findPlayer(plugin.getIgnoredPlayers(), "Bob").getAddedAt();
        assertTrue(zoeAddedAt > amyAddedAt);
        assertTrue(amyAddedAt > bobAddedAt);
        assertTrue(bobAddedAt > 0);
        assertEquals("note", findPlayer(plugin.getIgnoredPlayers(), "Zoe").getNote());
        assertTrue(findPlayer(plugin.getIgnoredPlayers(), "Amy").getAliases().contains("Amelia"));
        String migrated = sharedValues.get("ignoredPlayers");
        assertTrue(migrated.startsWith("v4\t"));

        plugin.onSessionOpen(new SessionOpen());
        assertEquals(migrated, sharedValues.get("ignoredPlayers"));
        assertEquals(zoeAddedAt, findPlayer(plugin.getIgnoredPlayers(), "Zoe").getAddedAt());
        verify(configManager, times(1)).setConfiguration(
            eq("extendedignorelist"), eq(SHARED_STORAGE_KEY), eq("ignoredPlayers"), anyString());

        configValues.put("sortOrder", IgnoreListSortOrder.OLDEST_FIRST.name());
        assertPlayerNames("Bob", "Amy", "Zoe");
        plugin.addIgnoredPlayer("Aaron");
        assertPlayerNames("Bob", "Amy", "Zoe", "Aaron");
        assertTrue(findPlayer(plugin.getIgnoredPlayers(), "Aaron").getAddedAt() > zoeAddedAt);
    }

    @Test
    public void restartRestoresDatesAndSortSelection() throws Exception
    {
        plugin.startUp();
        plugin.addIgnoredPlayer("Zoe");
        plugin.addIgnoredPlayer("Amy");
        long zoeAddedAt = findPlayer(plugin.getIgnoredPlayers(), "Zoe").getAddedAt();
        long amyAddedAt = findPlayer(plugin.getIgnoredPlayers(), "Amy").getAddedAt();
        invoke(plugin, "handleSortOrderChange", new Class<?>[] {IgnoreListSortOrder.class},
            IgnoreListSortOrder.NAME_ASCENDING);
        assertEquals(IgnoreListSortOrder.NAME_ASCENDING.name(), configValues.get("sortOrder"));
        String snapshot = sharedValues.get("ignoredPlayers");
        plugin.shutDown();
        plugin.startUp();
        try
        {
            assertPlayerNames("Amy", "Zoe");
            assertEquals(zoeAddedAt, findPlayer(plugin.getIgnoredPlayers(), "Zoe").getAddedAt());
            assertEquals(amyAddedAt, findPlayer(plugin.getIgnoredPlayers(), "Amy").getAddedAt());
            assertEquals(snapshot, sharedValues.get("ignoredPlayers"));
            plugin.addIgnoredPlayer("Bob");
            assertPlayerNames("Amy", "Bob", "Zoe");
        }
        finally
        {
            plugin.shutDown();
        }
    }

    @Test
    public void duplicatesNotesAndRenamesKeepOriginalDateWhileReadditionGetsNewDate() throws Exception
    {
        configValues.put("sortOrder", IgnoreListSortOrder.NAME_ASCENDING.name());
        plugin.addIgnoredPlayer("Zoe");
        long originalAddedAt = plugin.getIgnoredPlayers().get(0).getAddedAt();
        plugin.addIgnoredPlayer("Bob");
        plugin.addIgnoredPlayer("zoe");
        invoke(plugin, "updatePlayerNote", new Class<?>[] {String.class, String.class}, "zoe", "note");
        assertEquals(originalAddedAt, findPlayer(plugin.getIgnoredPlayers(), "zoe").getAddedAt());
        assertPlayerNames("Bob", "zoe");

        Ignore renamed = mock(Ignore.class);
        when(renamed.getName()).thenReturn("Amy");
        when(renamed.getPrevName()).thenReturn("zoe");
        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> container = mock(NameableContainer.class);
        when(container.getMembers()).thenReturn(new Ignore[] {renamed});
        when(client.getIgnoreContainer()).thenReturn(container);
        plugin.onGameTick(new GameTick());

        assertPlayerNames("Amy", "Bob");
        IgnoredPlayer amy = findPlayer(plugin.getIgnoredPlayers(), "Amy");
        assertEquals(originalAddedAt, amy.getAddedAt());
        assertEquals("note", amy.getNote());
        assertTrue(amy.getAliases().contains("zoe"));
        configValues.put("sortOrder", IgnoreListSortOrder.OLDEST_FIRST.name());
        assertPlayerNames("Amy", "Bob");

        plugin.removeIgnoredPlayer("Amy");
        plugin.addIgnoredPlayer("Amy");
        assertPlayerNames("Bob", "Amy");
        assertTrue(findPlayer(plugin.getIgnoredPlayers(), "Amy").getAddedAt() > originalAddedAt);
    }

    @Test
    public void matchingMigratedEntriesRetainEarliestKnownDate()
    {
        sharedValues.put("ignoredPlayers", "v4\tAlice\tAlicia\tshared note\t300");
        configValues.put(key(accountKey, "ignoredPlayers"), "v4\tAlicia\tOld Alice\taccount note\t100");
        plugin.onSessionOpen(new SessionOpen());

        assertEquals(1, plugin.getIgnoredPlayers().size());
        assertEquals(100, plugin.getIgnoredPlayers().get(0).getAddedAt());
        assertEquals("shared note / account note", plugin.getIgnoredPlayers().get(0).getNote());
        assertTrue(plugin.getIgnoredPlayers().get(0).getAliases().contains("Old Alice"));
    }

    @Test
    public void malformedDatesAreRepairedWithoutLosingNamesAliasesOrNotes()
    {
        sharedValues.put("ignoredPlayers",
            "v4\tZoe\tZoya\tnote\tnot-a-date\nv4\tAmy\t\t\t-1\nv4\tBob\t\t");
        plugin.onSessionOpen(new SessionOpen());

        assertPlayerNames("Zoe", "Amy", "Bob");
        assertTrue(plugin.getIgnoredPlayers().get(2).getAddedAt() > 0);
        assertEquals("note", plugin.getIgnoredPlayers().get(0).getNote());
        assertTrue(plugin.getIgnoredPlayers().get(0).getAliases().contains("Zoya"));
        assertFalse(sharedValues.get("ignoredPlayers").contains("not-a-date"));
    }

    @Test
    public void equalDatesUseDeterministicNameOrder()
    {
        sharedValues.put("ignoredPlayers", "v4\tZoe\t\t\t100\nv4\tamy\t\t\t100");
        plugin.onSessionOpen(new SessionOpen());
        assertPlayerNames("amy", "Zoe");
        configValues.put("sortOrder", IgnoreListSortOrder.OLDEST_FIRST.name());
        assertPlayerNames("amy", "Zoe");
    }

    @Test
    public void importMissingNativeIgnoresAddsOnlyMissingEntries() throws Exception
    {
        Ignore alice = mock(Ignore.class);
        when(alice.getName()).thenReturn("Alice");
        when(alice.getPrevName()).thenReturn("Alicia");

        Ignore bob = mock(Ignore.class);
        when(bob.getName()).thenReturn("Bob");
        when(bob.getPrevName()).thenReturn(null);

        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> ignoreContainer = mock(NameableContainer.class);
        when(ignoreContainer.getMembers()).thenReturn(new Ignore[] {alice, bob});
        when(client.getIgnoreContainer()).thenReturn(ignoreContainer);

        invoke(plugin, "addIgnoredPlayer", new Class<?>[] {String.class}, "Alice");
        invokePrivateNoArgs(plugin, "importMissingNativeIgnores");

        List<IgnoredPlayer> players = plugin.getIgnoredPlayers();
        assertEquals(2, players.size());
        assertNotNull(findPlayer(players, "Alice"));
        assertNotNull(findPlayer(players, "Bob"));
        assertTrue(findPlayer(players, "Bob").getAliases().isEmpty());
        assertTrue(findPlayer(players, "Alice").getAliases().contains("Alicia"));
        assertEquals("Bob", players.get(0).getCurrentName());
        assertEquals("Alice", players.get(1).getCurrentName());
    }

    @Test
    public void importButtonPrependsNewEntriesInNativeOrderAndPreservesExistingOrder() throws Exception
    {
        plugin.addIgnoredPlayer("Alice");
        plugin.addIgnoredPlayer("Zoe");
        Ignore alice = mock(Ignore.class);
        when(alice.getName()).thenReturn("Alice");
        Ignore bob = mock(Ignore.class);
        when(bob.getName()).thenReturn("Bob");
        Ignore charlie = mock(Ignore.class);
        when(charlie.getName()).thenReturn("Charlie");
        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> ignoreContainer = mock(NameableContainer.class);
        when(ignoreContainer.getMembers()).thenReturn(new Ignore[] {alice, bob, charlie});
        when(client.getIgnoreContainer()).thenReturn(ignoreContainer);

        invokePrivateNoArgs(plugin, "handleImportIgnoreList");

        String expected = serializedPlayer("Bob", "", "") + "\n" + serializedPlayer("Charlie", "", "")
            + "\n" + serializedPlayer("Zoe", "", "") + "\n" + serializedPlayer("Alice", "", "");
        assertEquals(expected, sharedValues.get("ignoredPlayers"));
        invokePrivateNoArgs(plugin, "importMissingNativeIgnores");
        assertEquals(expected, sharedValues.get("ignoredPlayers"));
        plugin.onSessionOpen(new SessionOpen());
        List<IgnoredPlayer> players = plugin.getIgnoredPlayers();
        assertEquals(4, players.size());
        assertEquals("Bob", players.get(0).getCurrentName());
        assertEquals("Charlie", players.get(1).getCurrentName());
        assertEquals("Zoe", players.get(2).getCurrentName());
        assertEquals("Alice", players.get(3).getCurrentName());
    }

    @Test
    public void importingDuplicateNativeEntriesCreatesOneRow() throws Exception
    {
        Ignore firstAlice = mock(Ignore.class);
        when(firstAlice.getName()).thenReturn("Alice");
        Ignore secondAlice = mock(Ignore.class);
        when(secondAlice.getName()).thenReturn("alice");

        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> ignoreContainer = mock(NameableContainer.class);
        when(ignoreContainer.getMembers()).thenReturn(new Ignore[] {firstAlice, secondAlice});
        when(client.getIgnoreContainer()).thenReturn(ignoreContainer);

        invokePrivateNoArgs(plugin, "importMissingNativeIgnores");

        assertEquals(1, plugin.getIgnoredPlayers().size());
    }

    @Test
    public void renameAwareMatchUsesCurrentOrPreviousNativeIgnoreNames() throws Exception
    {
        Ignore renamed = mock(Ignore.class);
        when(renamed.getName()).thenReturn("New Name");
        when(renamed.getPrevName()).thenReturn("Old Name");

        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> ignoreContainer = mock(NameableContainer.class);
        when(ignoreContainer.getMembers()).thenReturn(new Ignore[] {renamed});
        when(client.getIgnoreContainer()).thenReturn(ignoreContainer);

        invoke(plugin, "addIgnoredPlayer", new Class<?>[] {String.class}, "Old Name");
        invokePrivateNoArgs(plugin, "syncIgnoredPlayersWithNativeContainer");

        List<IgnoredPlayer> players = plugin.getIgnoredPlayers();
        assertEquals(1, players.size());
        assertEquals("New Name", players.get(0).getCurrentName());
        assertTrue(players.get(0).getAliases().contains("Old Name"));
        assertTrue(invokeBoolean(plugin, "isIgnoredPlayerName", new Class<?>[] {String.class}, "New Name"));
        assertTrue(invokeBoolean(plugin, "isIgnoredPlayerName", new Class<?>[] {String.class}, "Old Name"));
    }

    @Test
    public void shouldDrawHidesIgnoredPlayersWhenEnabled() throws Exception
    {
        setConfigField("hidePlayers", true);
        invoke(plugin, "addIgnoredPlayer", new Class<?>[] {String.class}, "Hidden Player");

        Player hidden = mock(Player.class);
        when(hidden.getName()).thenReturn("Hidden Player");

        Player visible = mock(Player.class);
        when(visible.getName()).thenReturn("Visible Player");

        assertFalse(invokeBoolean(plugin, "shouldDraw", new Class<?>[] {Renderable.class, boolean.class}, hidden, false));
        assertTrue(invokeBoolean(plugin, "shouldDraw", new Class<?>[] {Renderable.class, boolean.class}, visible, false));
    }

    @Test
    public void onMenuOptionClickedAddsIgnoredPlayerFromNativeAction() throws Exception
    {
        setConfigField("playerMenuOption", true);

        MenuEntry menuEntry = mock(MenuEntry.class);
        when(menuEntry.getOption()).thenReturn("Add ignore");
        when(menuEntry.getTarget()).thenReturn("<col=ffffff>Alice</col>  (level-3)");

        net.runelite.api.events.MenuOptionClicked event = new net.runelite.api.events.MenuOptionClicked(menuEntry);

        plugin.onMenuOptionClicked(event);

        assertNotNull(findPlayer(plugin.getIgnoredPlayers(), "Alice"));
    }

    @Test
    public void nativeIgnoreEntryAddsAddToExtendedOption()
    {
        Menu menu = mock(Menu.class);
        MenuEntry nativeEntry = mock(MenuEntry.class);
        MenuEntry extendedEntry = mock(MenuEntry.class);
        when(client.getMenu()).thenReturn(menu);
        when(menu.createMenuEntry(-1)).thenReturn(extendedEntry);
        when(nativeEntry.getOption()).thenReturn("Delete");
        when(nativeEntry.getTarget()).thenReturn("<col=ffffff>Alice</col>");
        when(nativeEntry.getParam1()).thenReturn(InterfaceID.IGNORE << 16);
        when(extendedEntry.setOption("Add to extended")).thenReturn(extendedEntry);
        when(extendedEntry.setTarget("<col=ffffff>Alice</col>")).thenReturn(extendedEntry);
        when(extendedEntry.setType(net.runelite.api.MenuAction.RUNELITE)).thenReturn(extendedEntry);
        when(extendedEntry.onClick(org.mockito.ArgumentMatchers.any())).thenReturn(extendedEntry);

        plugin.onMenuEntryAdded(new MenuEntryAdded(nativeEntry));

        verify(menu).createMenuEntry(-1);
        verify(extendedEntry).setOption("Add to extended");
    }

    @Test
    public void addToExtendedCopiesPreviousNameFriendNoteAndPreservesExistingNote()
    {
        Menu menu = mock(Menu.class);
        MenuEntry nativeEntry = mock(MenuEntry.class);
        MenuEntry extendedEntry = mock(MenuEntry.class);
        when(client.getMenu()).thenReturn(menu);
        when(menu.createMenuEntry(-1)).thenReturn(extendedEntry);
        when(nativeEntry.getOption()).thenReturn("Delete");
        when(nativeEntry.getTarget()).thenReturn("<col=ffffff>New Name</col>");
        when(nativeEntry.getParam1()).thenReturn(InterfaceID.IGNORE << 16);
        when(extendedEntry.getTarget()).thenReturn("<col=ffffff>New Name</col>");
        when(extendedEntry.setOption(anyString())).thenReturn(extendedEntry);
        when(extendedEntry.setTarget(anyString())).thenReturn(extendedEntry);
        when(extendedEntry.setType(net.runelite.api.MenuAction.RUNELITE)).thenReturn(extendedEntry);
        AtomicReference<Consumer<MenuEntry>> action = new AtomicReference<>();
        doAnswer(invocation ->
        {
            action.set(invocation.getArgument(0));
            return extendedEntry;
        }).when(extendedEntry).onClick(org.mockito.ArgumentMatchers.any());
        Ignore nativeIgnore = mock(Ignore.class);
        when(nativeIgnore.getName()).thenReturn("New Name");
        when(nativeIgnore.getPrevName()).thenReturn("Old Name");
        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> container = mock(NameableContainer.class);
        when(container.getMembers()).thenReturn(new Ignore[] {nativeIgnore});
        when(client.getIgnoreContainer()).thenReturn(container);
        when(configManager.getConfiguration("friendNotes", "note_" + net.runelite.client.util.Text.toJagexName("Old Name")))
            .thenReturn("original friend note");

        plugin.onMenuEntryAdded(new MenuEntryAdded(nativeEntry));
        action.get().accept(extendedEntry);
        assertEquals("original friend note", plugin.getIgnoredPlayers().get(0).getNote());
        long addedAt = plugin.getIgnoredPlayers().get(0).getAddedAt();

        when(configManager.getConfiguration("friendNotes", "note_" + net.runelite.client.util.Text.toJagexName("New Name")))
            .thenReturn("changed friend note");
        action.get().accept(extendedEntry);
        assertEquals("original friend note", plugin.getIgnoredPlayers().get(0).getNote());
        assertEquals(addedAt, plugin.getIgnoredPlayers().get(0).getAddedAt());
        verify(configManager, never()).setConfiguration(eq("friendNotes"), anyString(), anyString());
        verify(configManager, never()).unsetConfiguration(eq("friendNotes"), anyString());
    }

    @Test
    public void bulkImportCopiesCurrentNameNotesBeforePreviousNamesAndLeavesMissingNotesEmpty() throws Exception
    {
        Ignore alice = mock(Ignore.class);
        when(alice.getName()).thenReturn("Alice");
        when(alice.getPrevName()).thenReturn("Alicia");
        Ignore bob = mock(Ignore.class);
        when(bob.getName()).thenReturn("Bob");
        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> container = mock(NameableContainer.class);
        when(container.getMembers()).thenReturn(new Ignore[] {alice, bob});
        when(client.getIgnoreContainer()).thenReturn(container);
        when(configManager.getConfiguration("friendNotes", "note_" + net.runelite.client.util.Text.toJagexName("Alice")))
            .thenReturn("current note");
        when(configManager.getConfiguration("friendNotes", "note_" + net.runelite.client.util.Text.toJagexName("Alicia")))
            .thenReturn("previous note");

        invokePrivateNoArgs(plugin, "handleImportIgnoreList");
        assertEquals("current note", findPlayer(plugin.getIgnoredPlayers(), "Alice").getNote());
        assertEquals("", findPlayer(plugin.getIgnoredPlayers(), "Bob").getNote());
        assertTrue(sharedValues.get("ignoredPlayers").contains("current note"));
        plugin.onSessionOpen(new SessionOpen());
        assertEquals("current note", findPlayer(plugin.getIgnoredPlayers(), "Alice").getNote());
        verify(configManager, never()).setConfiguration(eq("friendNotes"), anyString(), anyString());
        verify(configManager, never()).unsetConfiguration(eq("friendNotes"), anyString());
    }

    @Test
    public void importCanFillExistingEmptyNotesUsingAliasesWithoutResettingDate() throws Exception
    {
        sharedValues.put("ignoredPlayers", "v4\tAlice\tOlder Alice\t\t100\nv4\tBob\t\tcustom note\t90");
        plugin.onSessionOpen(new SessionOpen());
        Ignore alice = mock(Ignore.class);
        when(alice.getName()).thenReturn("Alice");
        Ignore bob = mock(Ignore.class);
        when(bob.getName()).thenReturn("Bob");
        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> container = mock(NameableContainer.class);
        when(container.getMembers()).thenReturn(new Ignore[] {alice, bob});
        when(client.getIgnoreContainer()).thenReturn(container);
        when(configManager.getConfiguration("friendNotes", "note_" + net.runelite.client.util.Text.toJagexName("Older Alice")))
            .thenReturn("alias note");
        when(configManager.getConfiguration("friendNotes", "note_" + net.runelite.client.util.Text.toJagexName("Bob")))
            .thenReturn("do not overwrite");

        assertTrue(invokeBoolean(plugin, "canImportNativeIgnores", new Class<?>[0]));
        invokePrivateNoArgs(plugin, "handleImportIgnoreList");
        assertEquals("alias note", findPlayer(plugin.getIgnoredPlayers(), "Alice").getNote());
        assertEquals(100, findPlayer(plugin.getIgnoredPlayers(), "Alice").getAddedAt());
        assertEquals("custom note", findPlayer(plugin.getIgnoredPlayers(), "Bob").getNote());
        assertFalse(invokeBoolean(plugin, "canImportNativeIgnores", new Class<?>[0]));

        when(configManager.getConfiguration("friendNotes", "note_" + net.runelite.client.util.Text.toJagexName("Older Alice")))
            .thenReturn("updated elsewhere");
        plugin.onGameTick(new GameTick());
        assertEquals("alias note", findPlayer(plugin.getIgnoredPlayers(), "Alice").getNote());
    }

    @Test
    public void friendNoteChangesEnableImportWithoutAutomaticallyCopyingNote() throws Exception
    {
        plugin.addIgnoredPlayer("Alice");
        Ignore alice = mock(Ignore.class);
        when(alice.getName()).thenReturn("Alice");
        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> container = mock(NameableContainer.class);
        when(container.getMembers()).thenReturn(new Ignore[] {alice});
        when(client.getIgnoreContainer()).thenReturn(container);
        ExtendedIgnoreListPanel panel = mock(ExtendedIgnoreListPanel.class);
        setField(plugin, "panel", panel);
        assertFalse(invokeBoolean(plugin, "canImportNativeIgnores", new Class<?>[0]));
        String noteKey = "note_" + net.runelite.client.util.Text.toJagexName("Alice");
        when(configManager.getConfiguration("friendNotes", noteKey)).thenReturn("new note");
        ConfigChanged event = new ConfigChanged();
        event.setGroup("friendNotes");
        event.setKey(noteKey);

        plugin.onConfigChanged(event);

        verify(panel).setImportButtonState(true, null);
        assertEquals("", plugin.getIgnoredPlayers().get(0).getNote());
        invokePrivateNoArgs(plugin, "handleImportIgnoreList");
        assertEquals("new note", plugin.getIgnoredPlayers().get(0).getNote());
        verify(panel).setImportButtonState(false, "No new native ignore list entries or notes to import.");
    }

    @Test
    public void rejectedNativeAddStillAddsTypedNameToExtendedList() throws Exception
    {
        MenuEntry menuEntry = mock(MenuEntry.class);
        when(menuEntry.getOption()).thenReturn("Add Name");
        when(client.getVarcStrValue(VarClientID.CHATINPUT)).thenReturn(null, "Bob");

        plugin.onMenuOptionClicked(new net.runelite.api.events.MenuOptionClicked(menuEntry));
        for (int tick = 0; tick < 30; tick++)
        {
            invokePrivateNoArgs(plugin, "syncPendingNativeIgnoreActions");
        }

        assertNotNull(findPlayer(plugin.getIgnoredPlayers(), "Bob"));
    }

    @Test
    public void migratesLegacyPlayerMenuOptionKey() throws Exception
    {
        when(configManager.getConfiguration(eq("extendedignorelist"), eq("showMenuEntryOption"))).thenReturn("false");

        invokePrivateNoArgs(plugin, "migratePlayerMenuOptionKey");

        verify(configManager).setConfiguration(eq("extendedignorelist"), eq("playerMenuOption"), eq("false"));
        verify(configManager).unsetConfiguration(eq("extendedignorelist"), eq("showMenuEntryOption"));
    }

    @Test
    public void importButtonDisabledWhenAllNativeEntriesAlreadyTracked() throws Exception
    {
        Ignore alice = mock(Ignore.class);
        when(alice.getName()).thenReturn("Alice");

        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> ignoreContainer = mock(NameableContainer.class);
        when(ignoreContainer.getMembers()).thenReturn(new Ignore[] {alice});
        when(client.getIgnoreContainer()).thenReturn(ignoreContainer);

        invoke(plugin, "addIgnoredPlayer", new Class<?>[] {String.class}, "Alice");
        assertFalse(invokeBoolean(plugin, "canImportNativeIgnores", new Class<?>[0]));
    }

    @Test
    public void loadsV2RowsWithoutTreatingVersionAsPlayerName() throws Exception
    {
        configValues.put(key(accountKey, "ignoredPlayers"), "v2\tAlice\tAlicia");

        invokePrivateNoArgs(plugin, "loadIgnoredPlayersForCurrentSession");

        List<IgnoredPlayer> players = plugin.getIgnoredPlayers();
        assertEquals(1, players.size());
        assertEquals("Alice", players.get(0).getCurrentName());
        assertTrue(players.get(0).getAliases().contains("Alicia"));
        assertEquals("", players.get(0).getNote());
    }

    @Test
    public void importedListIsSharedWhenRuneScapeAccountChanges() throws Exception
    {
        Ignore alice = mock(Ignore.class);
        when(alice.getName()).thenReturn("Alice");
        @SuppressWarnings("unchecked")
        NameableContainer<Ignore> container = mock(NameableContainer.class);
        when(container.getMembers()).thenReturn(new Ignore[] {alice});
        when(client.getIgnoreContainer()).thenReturn(container);

        plugin.startUp();
        invokePrivateNoArgs(plugin, "importMissingNativeIgnores");
        when(configManager.getRSProfileKey()).thenReturn("another-account");
        when(client.getIgnoreContainer()).thenReturn(null);
        plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged(accountKey, "another-account"));

        assertEquals(1, plugin.getIgnoredPlayers().size());
        assertNotNull(findPlayer(plugin.getIgnoredPlayers(), "Alice"));
        assertEquals(serializedPlayer("Alice", "", ""), sharedValues.get("ignoredPlayers"));
        plugin.shutDown();
    }

    @Test
    public void restartedClientLoadsSyncedSnapshotForDifferentRuneScapeAccount()
    {
        plugin.startUp();
        plugin.addIgnoredPlayer("Alice");
        plugin.shutDown();

        // Model the shared config snapshot delivered by RuneLite cloud sync.
        when(configManager.getRSProfileKey()).thenReturn("another-account");
        sharedValues.put("ignoredPlayers", "v3\tAlice\tAlicia\tshared note");
        plugin.startUp();

        assertEquals(1, plugin.getIgnoredPlayers().size());
        assertEquals("shared note", plugin.getIgnoredPlayers().get(0).getNote());
        assertTrue(plugin.getIgnoredPlayers().get(0).getAliases().contains("Alicia"));
        plugin.shutDown();
    }

    @Test
    public void editsAndDeletionPersistInSharedConfig() throws Exception
    {
        plugin.addIgnoredPlayer("Alice");

        invoke(plugin, "updatePlayerNote", new Class<?>[] {String.class, String.class}, "Alice", "updated note");

        assertEquals(serializedPlayer("Alice", "", "updated note"), sharedValues.get("ignoredPlayers"));
        plugin.removeIgnoredPlayer("Alice");
        verify(configManager).unsetConfiguration("extendedignorelist", SHARED_STORAGE_KEY, "ignoredPlayers");
        assertTrue(plugin.getIgnoredPlayers().isEmpty());
    }

    @Test
    public void loadsSharedListWithoutRuneScapeLogin()
    {
        when(configManager.getRSProfileKey()).thenReturn(null);
        sharedValues.put("ignoredPlayers", "v3\tAlice\tAlicia\tshared note");

        plugin.onSessionOpen(new SessionOpen());

        assertEquals(1, plugin.getIgnoredPlayers().size());
        assertEquals("shared note", plugin.getIgnoredPlayers().get(0).getNote());
    }

    @Test
    public void mergesAllAccountListsAndPreservesAliasesAndConflictingNotes()
    {
        RuneScapeProfile otherProfile = mock(RuneScapeProfile.class);
        when(otherProfile.getKey()).thenReturn("another-account");
        when(configManager.getRSProfiles()).thenReturn(Arrays.asList(otherProfile));
        sharedValues.put("ignoredPlayers", "v3\tAlice\tAlicia\tshared note");
        configValues.put(key(accountKey, "ignoredPlayers"), "v3\tAlicia\tOld Alice\taccount note");
        configValues.put(key("another-account", "ignoredPlayers"), "v3\tBob\tBobby\tother note");

        plugin.onSessionOpen(new SessionOpen());

        assertEquals(2, plugin.getIgnoredPlayers().size());
        IgnoredPlayer alice = findPlayer(plugin.getIgnoredPlayers(), "Alice");
        assertTrue(alice.getAliases().contains("Old Alice"));
        assertEquals("shared note / account note", alice.getNote());
        assertEquals("other note", findPlayer(plugin.getIgnoredPlayers(), "Bob").getNote());
        verify(configManager).unsetConfiguration("extendedignorelist", accountKey, "ignoredPlayers");
        verify(configManager).unsetConfiguration("extendedignorelist", "another-account", "ignoredPlayers");

        plugin.removeIgnoredPlayer("Alice");
        plugin.removeIgnoredPlayer("Bob");
        plugin.onSessionOpen(new SessionOpen());
        assertTrue(plugin.getIgnoredPlayers().isEmpty());
        assertFalse(sharedValues.containsKey("ignoredPlayers"));
    }

    @Test
    public void receivedSharedConfigChangesReloadAliasesNotesAndDeletions() throws Exception
    {
        plugin.addIgnoredPlayer("Local Entry");
        sharedValues.put("ignoredPlayers", "v3\tRemote Entry\tPrevious Name\tremote note");

        plugin.onConfigChanged(sharedListChanged());

        assertEquals(1, plugin.getIgnoredPlayers().size());
        assertEquals("remote note", plugin.getIgnoredPlayers().get(0).getNote());
        assertTrue(invokeBoolean(plugin, "isIgnoredPlayerName", new Class<?>[] {String.class}, "Previous Name"));
        assertFalse(invokeBoolean(plugin, "isIgnoredPlayerName", new Class<?>[] {String.class}, "Local Entry"));

        sharedValues.remove("ignoredPlayers");
        plugin.onConfigChanged(sharedListChanged());
        assertTrue(plugin.getIgnoredPlayers().isEmpty());
        assertFalse(invokeBoolean(plugin, "isIgnoredPlayerName", new Class<?>[] {String.class}, "Previous Name"));
    }

    @Test
    public void profileSwitchKeepsSharedListAndMigratesTheSelectedProfilesOldList()
    {
        plugin.addIgnoredPlayer("Shared Entry");
        profileValues.put("ignoredPlayers", "v3\tSecond Profile\t\t");

        plugin.onProfileChanged(new ProfileChanged());

        assertEquals(2, plugin.getIgnoredPlayers().size());
        assertNotNull(findPlayer(plugin.getIgnoredPlayers(), "Shared Entry"));
        assertNotNull(findPlayer(plugin.getIgnoredPlayers(), "Second Profile"));
        assertFalse(profileValues.containsKey("ignoredPlayers"));

        profileValues.clear();
        plugin.onProfileChanged(new ProfileChanged());
        assertEquals(2, plugin.getIgnoredPlayers().size());
    }

    @Test
    public void migratesConfigurationProfileListWithoutReimportingDeletedEntries()
    {
        sharedValues.put("ignoredPlayers", "v3\tAlice\tAlicia\tshared note");
        profileValues.put("ignoredPlayers", "v3\tAlicia\tOld Alice\tprofile note\nv2\tBob\tBobby");

        plugin.onProfileChanged(new ProfileChanged());

        assertEquals(2, plugin.getIgnoredPlayers().size());
        IgnoredPlayer alice = findPlayer(plugin.getIgnoredPlayers(), "Alice");
        assertTrue(alice.getAliases().contains("Old Alice"));
        assertEquals("shared note / profile note", alice.getNote());
        assertTrue(findPlayer(plugin.getIgnoredPlayers(), "Bob").getAliases().contains("Bobby"));
        verify(configManager).unsetConfiguration("extendedignorelist", "ignoredPlayers");

        plugin.removeIgnoredPlayer("Alice");
        plugin.removeIgnoredPlayer("Bob");
        plugin.onProfileChanged(new ProfileChanged());
        plugin.onSessionOpen(new SessionOpen());
        assertTrue(plugin.getIgnoredPlayers().isEmpty());
        assertFalse(sharedValues.containsKey("ignoredPlayers"));
    }

    @Test
    public void profileSpecificSettingChangesDoNotAffectSharedEntries()
    {
        plugin.addIgnoredPlayer("Alice");
        setConfigField("hidePlayers", true);
        ConfigChanged event = new ConfigChanged();
        event.setGroup("extendedignorelist");
        event.setKey("hidePlayers");
        plugin.onConfigChanged(event);
        verify(renderCallbackManager).register(org.mockito.ArgumentMatchers.any());

        setConfigField("hidePlayers", false);
        plugin.onConfigChanged(event);
        verify(renderCallbackManager).unregister(org.mockito.ArgumentMatchers.any());

        plugin.onProfileChanged(new ProfileChanged());
        assertNotNull(findPlayer(plugin.getIgnoredPlayers(), "Alice"));
        assertEquals(serializedPlayer("Alice", "", ""), sharedValues.get("ignoredPlayers"));
        verify(configManager, never()).setConfiguration(eq("extendedignorelist"), eq("ignoredPlayers"), anyString());
    }

    @Test
    public void profileScopedListChangeDoesNotReplaceSharedEntries()
    {
        plugin.addIgnoredPlayer("Alice");
        profileValues.put("ignoredPlayers", "v3\tBob\t\t");
        ConfigChanged event = sharedListChanged();
        event.setProfile(null);

        plugin.onConfigChanged(event);

        assertEquals(1, plugin.getIgnoredPlayers().size());
        assertNotNull(findPlayer(plugin.getIgnoredPlayers(), "Alice"));
        verify(clientThread, never()).invoke(org.mockito.ArgumentMatchers.any(Runnable.class));

        plugin.onProfileChanged(new ProfileChanged());
        assertEquals(2, plugin.getIgnoredPlayers().size());
    }

    @Test
    public void runeLiteLogoutDoesNotDiscardSharedList()
    {
        plugin.addIgnoredPlayer("Alice");

        plugin.onSessionClose(new SessionClose());

        assertNotNull(findPlayer(plugin.getIgnoredPlayers(), "Alice"));
    }

    @Test
    public void accountScopedConfigChangesDoNotReplaceSharedList()
    {
        plugin.addIgnoredPlayer("Alice");
        ConfigChanged event = sharedListChanged();
        event.setProfile(accountKey);

        plugin.onConfigChanged(event);

        assertNotNull(findPlayer(plugin.getIgnoredPlayers(), "Alice"));
        verify(clientThread, never()).invoke(org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    public void loadingSharedDataDoesNotWriteItBack()
    {
        sharedValues.put("ignoredPlayers", "v4\tAlice\t\t\t100");

        plugin.onSessionOpen(new SessionOpen());

        verify(configManager, never()).setConfiguration(eq("extendedignorelist"), eq(SHARED_STORAGE_KEY), eq("ignoredPlayers"), anyString());
        verify(configManager, never()).unsetConfiguration("extendedignorelist", SHARED_STORAGE_KEY, "ignoredPlayers");
    }

    @Test
    public void raidBoardHighlightsMatchSharedAliasesAndRestoreOnRemoval()
    {
        sharedValues.put("ignoredPlayers", "v3\tAlice\tAlicia\t");
        Widget name = mock(Widget.class);
        when(name.getType()).thenReturn(WidgetType.TEXT);
        when(name.getText()).thenReturn("<img=1>ALICIA");
        when(client.getWidget(InterfaceID.ToaLobby.NAMES)).thenReturn(name);
        plugin.startUp();
        try
        {
            plugin.onGameTick(new GameTick());
            verify(name).setText("<col=ff0000><img=1>ALICIA</col>");

            when(name.getText()).thenReturn("<col=ff0000><img=1>ALICIA</col>");
            plugin.removeIgnoredPlayer("Alice");
            plugin.onGameTick(new GameTick());
            verify(name).setText("<img=1>ALICIA");
        }
        finally
        {
            plugin.shutDown();
        }
    }

    @Test
    public void raidBoardConfigChangesAndShutdownRestoreHighlights()
    {
        sharedValues.put("ignoredPlayers", "v3\tAlice\t\t");
        Widget name = mock(Widget.class);
        when(name.getType()).thenReturn(WidgetType.TEXT);
        when(name.getText()).thenReturn("Alice");
        when(client.getWidget(InterfaceID.TobPartydetails.CURRENT)).thenReturn(name);
        plugin.startUp();
        try
        {
            plugin.onGameTick(new GameTick());
            verify(name).setText("<col=ff0000>Alice</col>");
            when(name.getText()).thenReturn("<col=ff0000>Alice</col>");

            ConfigChanged event = new ConfigChanged();
            event.setGroup("extendedignorelist");
            event.setKey("highlightRaidsAndGroups");
            setConfigField("highlightRaidsAndGroups", false);
            plugin.onConfigChanged(event);
            verify(name).setText("Alice");

            when(name.getText()).thenReturn("Alice");
            setConfigField("highlightRaidsAndGroups", true);
            plugin.onConfigChanged(event);
            verify(name, times(2)).setText("<col=ff0000>Alice</col>");
            when(name.getText()).thenReturn("<col=ff0000>Alice</col>");
        }
        finally
        {
            plugin.shutDown();
        }
        verify(name, times(2)).setText("Alice");
    }

    @Test
    public void groupNotificationModesRouteToChatAndNotifierIndependentlyOfHighlights()
    {
        sharedValues.put("ignoredPlayers", "v3\tAlice\tAlicia\t");
        setConfigField("highlightRaidsAndGroups", false);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSLOT)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_P1)).thenReturn(1);
        when(client.getVarcStrValue(VarClientID.TOA_CLIENT_NAME1)).thenReturn("Alicia");
        String message = "Alicia is on your extended ignore list.";
        plugin.startUp();
        try
        {
            plugin.onGameTick(new GameTick());
            verify(client, never()).addChatMessage(eq(ChatMessageType.CONSOLE), anyString(), anyString(), anyString());
            verify(notifier, never()).notify(anyString());

            configValues.put("notifyWhenInGroup", GroupNotificationMode.CHAT_ONLY.name());
            plugin.onGameTick(new GameTick());
            verify(client).addChatMessage(ChatMessageType.CONSOLE, "", message, "");
            verify(notifier, never()).notify(anyString());

            configValues.put("notifyWhenInGroup", GroupNotificationMode.NONE.name());
            ConfigChanged event = new ConfigChanged();
            event.setGroup("extendedignorelist");
            event.setKey("notifyWhenInGroup");
            plugin.onConfigChanged(event);
            configValues.put("notifyWhenInGroup", GroupNotificationMode.NOTIFICATION_AND_CHAT.name());
            plugin.onGameTick(new GameTick());
            plugin.onGameTick(new GameTick());
            verify(notifier).notify(message);
            verify(client, times(2)).addChatMessage(ChatMessageType.CONSOLE, "", message, "");

            setConfigField("censorName", true);
            when(client.getVarbitValue(VarbitID.TOA_CLIENT_P1)).thenReturn(0);
            plugin.onGameTick(new GameTick());
            when(client.getVarbitValue(VarbitID.TOA_CLIENT_P1)).thenReturn(1);
            plugin.onGameTick(new GameTick());
            verify(notifier).notify("Someone is on your extended ignore list.");
            verify(client).addChatMessage(ChatMessageType.CONSOLE, "",
                "Someone is on your extended ignore list.", "");
        }
        finally
        {
            plugin.shutDown();
        }
    }

    @Test
    public void singleIgnoredPlayerNoteAppearsOnlyInChatIncludingWhenCensored() throws Exception
    {
        sharedValues.put("ignoredPlayers", "v3\tAlice\tAlicia\t  rude <br> player  ");
        configValues.put("notifyWhenInGroup", GroupNotificationMode.NOTIFICATION_AND_CHAT.name());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSLOT)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_P1)).thenReturn(1);
        when(client.getVarcStrValue(VarClientID.TOA_CLIENT_NAME1)).thenReturn("Alicia");
        plugin.startUp();
        try
        {
            plugin.onGameTick(new GameTick());
            verify(notifier).notify("Alicia is on your extended ignore list.");
            verify(client).addChatMessage(ChatMessageType.CONSOLE, "",
                "Alicia is on your extended ignore list for rude <lt>br<gt> player.", "");

            setConfigField("censorName", true);
            when(client.getVarbitValue(VarbitID.TOA_CLIENT_P1)).thenReturn(0);
            plugin.onGameTick(new GameTick());
            when(client.getVarbitValue(VarbitID.TOA_CLIENT_P1)).thenReturn(1);
            plugin.onGameTick(new GameTick());
            verify(notifier).notify("Someone is on your extended ignore list.");
            verify(client).addChatMessage(ChatMessageType.CONSOLE, "",
                "Someone is on your extended ignore list for rude <lt>br<gt> player.", "");

            invoke(plugin, "updatePlayerNote", new Class<?>[] {String.class, String.class}, "Alice", "   ");
            when(client.getVarbitValue(VarbitID.TOA_CLIENT_P1)).thenReturn(0);
            plugin.onGameTick(new GameTick());
            when(client.getVarbitValue(VarbitID.TOA_CLIENT_P1)).thenReturn(1);
            plugin.onGameTick(new GameTick());
            verify(client).addChatMessage(ChatMessageType.CONSOLE, "",
                "Someone is on your extended ignore list.", "");
        }
        finally
        {
            plugin.shutDown();
        }
    }

    @Test
    public void barbarianAssaultAlertsUseCensoringAndNotesWithoutRaidHighlighting()
    {
        sharedValues.put("ignoredPlayers", "v3\tAlice\tAlicia\tleft the team");
        configValues.put("notifyWhenInGroup", GroupNotificationMode.NOTIFICATION_AND_CHAT.name());
        setConfigField("highlightRaidsAndGroups", false);
        setConfigField("censorName", true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        Widget member = mock(Widget.class);
        when(member.getText()).thenReturn("Alicia");
        when(client.getWidget(InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_PLAYER_1_NAME))
            .thenReturn(member);
        plugin.startUp();
        try
        {
            plugin.onGameTick(new GameTick());
            plugin.onGameTick(new GameTick());
            verify(notifier).notify("Someone is on your extended ignore list.");
            verify(client).addChatMessage(ChatMessageType.CONSOLE, "",
                "Someone is on your extended ignore list for left the team.", "");
        }
        finally
        {
            plugin.shutDown();
        }
    }

    private static ConfigChanged sharedListChanged()
    {
        ConfigChanged event = new ConfigChanged();
        event.setGroup("extendedignorelist");
        event.setKey("ignoredPlayers");
        event.setProfile(SHARED_STORAGE_KEY);
        return event;
    }

    private IgnoredPlayer findPlayer(List<IgnoredPlayer> players, String name)
    {
        for (IgnoredPlayer player : players)
        {
            if (name.equals(player.getCurrentName()))
            {
                return player;
            }
        }

        return null;
    }

    private String serializedPlayer(String name, String aliases, String note)
    {
        return "v4\t" + name + "\t" + aliases + "\t" + note + "\t"
            + findPlayer(plugin.getIgnoredPlayers(), name).getAddedAt();
    }

    private void assertPlayerNames(String... names)
    {
        assertEquals(Arrays.asList(names), plugin.getIgnoredPlayers().stream()
            .map(IgnoredPlayer::getCurrentName).collect(Collectors.toList()));
    }

    private void setConfigField(String key, boolean value)
    {
        configValues.put(key, Boolean.toString(value));
    }

    private static String key(Object... parts)
    {
        return Arrays.toString(parts);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invokePrivateNoArgs(Object target, String methodName) throws Exception
    {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static boolean invokeBoolean(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception
    {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (Boolean) method.invoke(target, args);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception
    {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static class TestConfig implements ExtendedIgnoreListConfig
    {
        private final Map<String, String> values;

        private TestConfig(Map<String, String> values)
        {
            this.values = values;
        }

        private boolean readBoolean(String key, boolean defaultValue)
        {
            String configuredValue = values.get(key);
            return configuredValue == null ? defaultValue : Boolean.parseBoolean(configuredValue);
        }

        @Override
        public boolean hidePlayers()
        {
            return readBoolean("hidePlayers", false);
        }

        @Override
        public boolean playerMenuOption()
        {
            return readBoolean("playerMenuOption", true);
        }

        @Override
        public boolean syncRemoveIgnore()
        {
            return readBoolean("syncRemoveIgnore", false);
        }

        @Override
        public boolean deleteConfirmation()
        {
            return readBoolean("deleteConfirmation", true);
        }

        @Override
        public boolean ignoreTrades()
        {
            return readBoolean("ignoreTrades", true);
        }

        @Override
        public boolean highlightRaidsAndGroups()
        {
            return readBoolean("highlightRaidsAndGroups", true);
        }

        @Override
        public GroupNotificationMode notifyWhenInGroup()
        {
            String value = values.get("notifyWhenInGroup");
            return value == null ? GroupNotificationMode.NONE : GroupNotificationMode.valueOf(value);
        }

        @Override
        public boolean censorName()
        {
            return readBoolean("censorName", false);
        }

        @Override
        public IgnoreListSortOrder sortOrder()
        {
            String value = values.get("sortOrder");
            return value == null ? IgnoreListSortOrder.NEWEST_FIRST : IgnoreListSortOrder.valueOf(value);
        }
    }
}
