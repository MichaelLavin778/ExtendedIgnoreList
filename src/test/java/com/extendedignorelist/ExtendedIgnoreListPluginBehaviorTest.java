package com.extendedignorelist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Ignore;
import net.runelite.api.Menu;
import net.runelite.api.NameableContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.ui.ClientToolbar;
import org.junit.Before;
import org.junit.Test;

public class ExtendedIgnoreListPluginBehaviorTest
{
    private ExtendedIgnoreListPlugin plugin;
    private ConfigManager configManager;
    private Client client;
    private ClientToolbar clientToolbar;
    private MenuManager menuManager;
    private RenderCallbackManager renderCallbackManager;
    private Map<String, String> configValues;
    private Map<String, String> legacyValues;
    private String accountKey;

    @Before
    public void setUp() throws Exception
    {
        plugin = new ExtendedIgnoreListPlugin();
        configManager = mock(ConfigManager.class);
        client = mock(Client.class);
        clientToolbar = mock(ClientToolbar.class);
        menuManager = mock(MenuManager.class);
        renderCallbackManager = mock(RenderCallbackManager.class);
        configValues = new HashMap<>();
        legacyValues = new HashMap<>();
        accountKey = "123e4567-e89b-12d3-a456-426614174000";

        when(client.getIgnoreContainer()).thenReturn(null);
        when(configManager.getRSProfileKey()).thenReturn(accountKey);
        when(configManager.getConfig(ExtendedIgnoreListConfig.class)).thenReturn(new TestConfig(configValues));
        when(configManager.getConfiguration(eq("extendedignorelist"), eq("ignoredPlayers"))).thenAnswer(invocation -> legacyValues.get(key(invocation.getArgument(1), invocation.getArgument(2))));
        when(configManager.getConfiguration(eq("extendedignorelist"), eq(accountKey), eq("ignoredPlayers"))).thenAnswer(invocation -> configValues.get(key(invocation.getArgument(1), invocation.getArgument(2))));

        setField(plugin, "client", client);
        setField(plugin, "clientToolbar", clientToolbar);
        setField(plugin, "configManager", configManager);
        setField(plugin, "menuManager", menuManager);
        setField(plugin, "renderCallbackManager", renderCallbackManager);
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

        verify(configManager).setConfiguration(eq("extendedignorelist"), eq(accountKey), eq("ignoredPlayers"), eq("v3\tAlice\t\t\nv3\tAlice Prime\t\t"));
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
    }
}
