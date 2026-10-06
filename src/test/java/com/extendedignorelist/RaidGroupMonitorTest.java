package com.extendedignorelist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.util.Text;
import org.junit.Before;
import org.junit.Test;

public class RaidGroupMonitorTest
{
    private Client client;
    private RaidGroupMonitor monitor;
    private Set<String> ignoredNames;
    private List<String> messages;
    private List<GroupNotificationMode> modes;
    private List<String> singleIgnoredNames;
    private boolean censorName;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        Player localPlayer = mock(Player.class);
        when(localPlayer.getName()).thenReturn("Me");
        when(localPlayer.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        when(client.getLocalPlayer()).thenReturn(localPlayer);
        ignoredNames = new HashSet<>();
        ignoredNames.add("alice");
        messages = new ArrayList<>();
        modes = new ArrayList<>();
        singleIgnoredNames = new ArrayList<>();
        monitor = new RaidGroupMonitor(client, name -> ignoredNames.contains(Text.standardize(name)),
            () -> censorName,
            (mode, message, singleIgnoredName) ->
            {
                modes.add(mode);
                messages.add(message);
                singleIgnoredNames.add(singleIgnoredName);
            });
    }

    @Test
    public void alertsWhenIgnoredPlayerJoinsTheatreGroupOnlyOnce()
    {
        tobParty(null);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        tobParty("Alice");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);

        assertEquals(List.of("Alice is on your extended ignore list."), messages);
        assertEquals(List.of(GroupNotificationMode.CHAT_ONLY), modes);
        assertEquals("Alice", singleIgnoredNames.get(0));
    }

    @Test
    public void theatreLobbyHudNotifiesWithoutCombatSlotOrMemberVarbits()
    {
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(1);
        Widget names = tobHud("Me<br>-<br>-<br>-<br>-");
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        when(names.getText()).thenReturn("Me<br><col=ff0000>Alice</col><br>-<br>-<br>-");
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        assertEquals(List.of("Alice is on your extended ignore list."), messages);
        assertEquals(List.of(GroupNotificationMode.NOTIFICATION_AND_CHAT), modes);
        assertEquals("Alice", singleIgnoredNames.get(0));

        when(names.getText()).thenReturn("Me<br>-<br>-<br>-<br>-");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(names.getText()).thenReturn("Me<br>Alice<br>-<br>-<br>-");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(2, messages.size());
    }

    @Test
    public void theatreLobbyHudMatchesAliasesExcludesSelfAndRespectsCensoring()
    {
        ignoredNames.add("old alice");
        ignoredNames.add("me");
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(1);
        tobHud("Me<br>OLD\u00a0Alice<br>-<br>-<br>-");
        censorName = true;
        monitor.refresh(GroupNotificationMode.NONE);
        assertTrue(messages.isEmpty());
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(List.of("Someone is on your extended ignore list."), messages);
        assertEquals("old alice", Text.standardize(singleIgnoredNames.get(0)));
    }

    @Test
    public void theatreLobbyStateSurvivesMissingHudAndRaidTransitionButResetsOnLeaving()
    {
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(1);
        Widget names = tobHud("Me<br>Alice<br>-<br>-<br>-");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getWidget(InterfaceID.TobHud.NAMES)).thenReturn(null);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getWidget(InterfaceID.TobHud.NAMES)).thenReturn(names);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(1, messages.size());

        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(2);
        when(client.getWidget(InterfaceID.TobHud.NAMES)).thenReturn(null);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSLOT)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_P1)).thenReturn(1);
        when(client.getVarcStrValue(VarClientID.TOB_CLIENT_NAME1)).thenReturn("Alice");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(1, messages.size());

        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(0);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(1);
        when(client.getWidget(InterfaceID.TobHud.NAMES)).thenReturn(names);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(2, messages.size());
    }

    @Test
    public void theatreHudDoesNotNotifyForNoPartySpectatorHiddenOrBrowsedParties()
    {
        Widget names = tobHud("Alice<br>-<br>-<br>-<br>-");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(3);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(2);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(1);
        when(names.isHidden()).thenReturn(true);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getWidget(InterfaceID.TobHud.NAMES)).thenReturn(null);
        Widget browsedParty = text("Alice");
        when(client.getWidget(InterfaceID.TobPartydetails.CURRENT)).thenReturn(browsedParty);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void amascutLobbyAlertsWhenIgnoredMemberJoinsAndWhenJoiningTheirTeam()
    {
        Widget names = toaLobby("Me<br>-<br>-<br>-<br>-<br>-<br>-<br>-");
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(1);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        when(names.getText()).thenReturn("Me<br><col=ff0000>Alice</col><br>-<br>-<br>-<br>-<br>-<br>-");
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        assertEquals(List.of("Alice is on your extended ignore list."), messages);
        assertEquals(List.of(GroupNotificationMode.NOTIFICATION_AND_CHAT), modes);

        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(0);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(1);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(2, messages.size());
        assertEquals("Alice", singleIgnoredNames.get(1));
    }

    @Test
    public void amascutLobbySurvivesUnavailableWidgetAndReadyTransitionAndRealertsOnRejoin()
    {
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(1);
        Widget names = toaLobby("Me<br>Alice<br>-");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getWidget(InterfaceID.ToaLobby.NAMES)).thenReturn(null);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(2);
        when(client.getWidget(InterfaceID.ToaLobby.NAMES)).thenReturn(names);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(1, messages.size());
        when(client.getWidget(InterfaceID.ToaLobby.NAMES)).thenReturn(null);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSLOT)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_P7)).thenReturn(1);
        when(client.getVarcStrValue(VarClientID.TOA_CLIENT_NAME7)).thenReturn("Alice");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(1, messages.size());
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_P7)).thenReturn(0);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_P7)).thenReturn(1);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(2, messages.size());
    }

    @Test
    public void amascutLobbyRespectsCensoringAliasesSelfAndDisabledNotifications()
    {
        ignoredNames.add("old alice");
        ignoredNames.add("me");
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(1);
        toaLobby("Me<br>OLD\u00a0Alice<br>Alice Prime<br>-");
        censorName = true;
        monitor.refresh(GroupNotificationMode.NONE);
        assertTrue(messages.isEmpty());
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(List.of("Someone is on your extended ignore list."), messages);
        assertEquals("old alice", Text.standardize(singleIgnoredNames.get(0)));
    }

    @Test
    public void amascutLobbyIgnoresNoPartySpectatorsHiddenRosterAndBrowsedDetails()
    {
        Widget names = toaLobby("Alice<br>-");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(3);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(1);
        when(names.isHidden()).thenReturn(true);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getWidget(InterfaceID.ToaLobby.NAMES)).thenReturn(null);
        Widget browsed = text("Alice");
        when(client.getWidget(InterfaceID.ToaPartydetails.MEMBERS_LIST)).thenReturn(browsed);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertTrue(messages.isEmpty());
    }

    private Widget toaLobby(String names)
    {
        Widget widget = text(names);
        when(widget.getType()).thenReturn(WidgetType.TEXT);
        when(client.getWidget(InterfaceID.ToaLobby.NAMES)).thenReturn(widget);
        return widget;
    }

    private Widget tobHud(String names)
    {
        Widget widget = text(names);
        when(widget.getType()).thenReturn(WidgetType.TEXT);
        when(client.getWidget(InterfaceID.TobHud.NAMES)).thenReturn(widget);
        return widget;
    }

    @Test
    public void alertsWhenJoiningExistingAmascutGroupAndMatchesAlias()
    {
        ignoredNames.add("old alice");
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSLOT)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_P7)).thenReturn(1);
        when(client.getVarcStrValue(VarClientID.TOA_CLIENT_NAME7)).thenReturn("OLD\u00a0Alice");

        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(2);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);

        assertEquals(List.of("OLD Alice is on your extended ignore list."), messages);
        assertEquals(List.of(GroupNotificationMode.NOTIFICATION_AND_CHAT), modes);
    }

    @Test
    public void leavingAndRejoiningOrAnotherPlayerRejoiningAlertsAgain()
    {
        tobParty("Alice");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(0);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        tobParty("Alice");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_P1)).thenReturn(0);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        tobParty("Alice");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);

        assertEquals(3, messages.size());
    }

    @Test
    public void batchesMultipleIgnoredMembersAndIgnoresOtherNames()
    {
        tobParty("Alice");
        ignoredNames.add("bob");
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_P2)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_P3)).thenReturn(1);
        when(client.getVarcStrValue(VarClientID.TOB_CLIENT_NAME2)).thenReturn("Bob");
        when(client.getVarcStrValue(VarClientID.TOB_CLIENT_NAME3)).thenReturn("Alice Prime");

        monitor.refresh(GroupNotificationMode.CHAT_ONLY);

        assertEquals(List.of("Alice, Bob are on your extended ignore list."), messages);
        assertEquals(null, singleIgnoredNames.get(0));

        ignoredNames.add("alice prime");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(null, singleIgnoredNames.get(1));
    }

    @Test
    public void noAlertsForStaleNamesEmptySlotsSpectatorsOrBrowsedBoards()
    {
        when(client.getVarcStrValue(VarClientID.TOB_CLIENT_NAME1)).thenReturn("Alice");
        when(client.getVarcStrValue(VarClientID.TOA_CLIENT_NAME1)).thenReturn("Alice");
        when(client.getWidget(InterfaceID.ToaPartydetails.MEMBERS_LIST)).thenReturn(mock(Widget.class));
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);

        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_P1)).thenReturn(1);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);

        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSLOT)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_P1)).thenReturn(0);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);

        assertTrue(messages.isEmpty());
    }

    @Test
    public void doesNotAlertForLocalPlayerOrInvalidNames()
    {
        ignoredNames.add("me");
        ignoredNames.add("null");
        ignoredNames.add("-");
        tobParty("null");
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_P0)).thenReturn(1);
        when(client.getVarcStrValue(VarClientID.TOB_CLIENT_NAME0)).thenReturn("Me");
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_P2)).thenReturn(1);
        when(client.getVarcStrValue(VarClientID.TOB_CLIENT_NAME2)).thenReturn("-");
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_P3)).thenReturn(1);
        when(client.getVarcStrValue(VarClientID.TOB_CLIENT_NAME3)).thenReturn("");
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_P4)).thenReturn(1);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);

        assertTrue(messages.isEmpty());
    }

    @Test
    public void noneIsSilentAndEnablingChecksCurrentGroup()
    {
        tobParty("Alice");
        monitor.refresh(GroupNotificationMode.NONE);
        assertTrue(messages.isEmpty());
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        assertEquals(1, messages.size());
        monitor.refresh(GroupNotificationMode.NONE);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        assertEquals(2, messages.size());
    }

    @Test
    public void resetAndLogoutAllowFreshGroupWarnings()
    {
        tobParty("Alice");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        monitor.reset();
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(2, messages.size());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(3, messages.size());
    }

    @Test
    public void extendedListChangesApplyToCurrentGroup()
    {
        tobParty("Bob");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertTrue(messages.isEmpty());
        ignoredNames.add("bob");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(1, messages.size());
        ignoredNames.remove("bob");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        ignoredNames.add("bob");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(2, messages.size());
    }

    @Test
    public void chambersUsesFullOwnRosterNamesNotVisibleTruncationOrOtherColumns()
    {
        when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON)).thenReturn(1);
        ignoredNames.add("long alice");
        ignoredNames.add("me");
        Widget list = mock(Widget.class);
        Widget[] rows = new Widget[21];
        rows[1] = text("Long Ali...");
        rows[2] = text("Alice");
        rows[4] = text("<col=ffffff>Long\u00a0Alice</col>");
        rows[11] = text("Me");
        rows[18] = text("Alice Prime");
        when(list.getDynamicChildren()).thenReturn(rows);
        when(client.getWidget(InterfaceID.RaidsSidepanel.LIST)).thenReturn(list);

        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(List.of("Long Alice is on your extended ignore list."), messages);

        when(client.getWidget(InterfaceID.RaidsSidepanel.LIST)).thenReturn(null);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getWidget(InterfaceID.RaidsSidepanel.LIST)).thenReturn(list);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(1, messages.size());
        when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON)).thenReturn(0);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON)).thenReturn(1);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(2, messages.size());
    }

    @Test
    public void chambersAlertsWhenIgnoredMemberJoinsOrWhenEnteringTheirExistingRaid()
    {
        Widget list = mock(Widget.class);
        Widget[] rows = new Widget[14];
        rows[4] = text("Me");
        rows[11] = text("-");
        when(list.getDynamicChildren()).thenReturn(rows);
        when(client.getWidget(InterfaceID.RaidsSidepanel.LIST)).thenReturn(list);
        when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON)).thenReturn(1);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        rows[11] = text("Alice");
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        assertEquals(List.of("Alice is on your extended ignore list."), messages);

        when(list.isHidden()).thenReturn(true);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        when(list.isHidden()).thenReturn(false);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        assertEquals(1, messages.size());

        rows[11] = text("-");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        rows[11] = text("Alice");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(2, messages.size());
        when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON)).thenReturn(0);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(2, messages.size());
        when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON)).thenReturn(1);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(3, messages.size());
    }

    @Test
    public void chambersEntranceFriendsChatDetectsBothJoinDirectionsAndRejoining()
    {
        when(client.getLocalPlayer().getWorldLocation()).thenReturn(new WorldPoint(1233, 3560, 0));
        FriendsChatManager chat = mock(FriendsChatManager.class);
        FriendsChatMember alice = mock(FriendsChatMember.class);
        when(alice.getName()).thenReturn("Alice");
        when(client.getFriendsChatManager()).thenReturn(chat);
        when(chat.getMembers()).thenReturn(new FriendsChatMember[0]);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        when(chat.getMembers()).thenReturn(new FriendsChatMember[] {alice});
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        assertEquals(List.of("Alice is on your extended ignore list."), messages);
        when(chat.getMembers()).thenReturn(new FriendsChatMember[0]);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(chat.getMembers()).thenReturn(new FriendsChatMember[] {alice});
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(2, messages.size());
        when(client.getFriendsChatManager()).thenReturn(null);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getFriendsChatManager()).thenReturn(chat);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(3, messages.size());
    }

    @Test
    public void chambersEntranceFriendsChatIgnoresUnrelatedLocationsAndPreservesRaidTransition()
    {
        FriendsChatManager chat = mock(FriendsChatManager.class);
        FriendsChatMember alice = mock(FriendsChatMember.class);
        when(alice.getName()).thenReturn("Alice");
        when(client.getFriendsChatManager()).thenReturn(chat);
        when(chat.getMembers()).thenReturn(new FriendsChatMember[] {alice});
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertTrue(messages.isEmpty());
        when(client.getLocalPlayer().getWorldLocation()).thenReturn(new WorldPoint(1233, 3560, 0));
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(1, messages.size());
        when(chat.getMembers()).thenReturn(null);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(chat.getMembers()).thenReturn(new FriendsChatMember[] {alice});
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(1, messages.size());
        when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON)).thenReturn(1);
        Widget list = mock(Widget.class);
        Widget[] rows = new Widget[7];
        rows[4] = text("Alice");
        when(list.getDynamicChildren()).thenReturn(rows);
        when(client.getWidget(InterfaceID.RaidsSidepanel.LIST)).thenReturn(list);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(1, messages.size());
    }

    @Test
    public void chambersEntranceFriendsChatRespectsAliasesSelfCensoringAndDisabledMode()
    {
        ignoredNames.add("old alice");
        ignoredNames.add("me");
        when(client.getLocalPlayer().getWorldLocation()).thenReturn(new WorldPoint(1233, 3560, 0));
        FriendsChatManager chat = mock(FriendsChatManager.class);
        FriendsChatMember alice = mock(FriendsChatMember.class);
        when(alice.getName()).thenReturn("OLD\u00a0Alice");
        FriendsChatMember me = mock(FriendsChatMember.class);
        when(me.getName()).thenReturn("Me");
        when(chat.getMembers()).thenReturn(new FriendsChatMember[] {null, me, alice});
        when(client.getFriendsChatManager()).thenReturn(chat);
        censorName = true;
        monitor.refresh(GroupNotificationMode.NONE);
        assertTrue(messages.isEmpty());
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        assertEquals(List.of("Someone is on your extended ignore list."), messages);
        assertEquals("old alice", Text.standardize(singleIgnoredNames.get(0)));
    }

    @Test
    public void chambersBrowsedLobbyPartyDoesNotTriggerAlerts()
    {
        Widget lobby = text("Alice");
        when(client.getWidget(InterfaceID.RaidsLobbyPartydetails.LIST)).thenReturn(lobby);
        when(client.getWidget(InterfaceID.RaidsLobbyPartylist.LIST)).thenReturn(lobby);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void censoringUsesSomeoneForOnePersonAndSomePeopleForMultiple()
    {
        censorName = true;
        tobParty("Alice");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(List.of("Someone is on your extended ignore list."), messages);

        monitor.reset();
        ignoredNames.add("bob");
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_P2)).thenReturn(1);
        when(client.getVarcStrValue(VarClientID.TOB_CLIENT_NAME2)).thenReturn("Bob");
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        assertEquals("Some people are on your extended ignore list.", messages.get(1));

        censorName = false;
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        assertEquals(2, messages.size());
        monitor.reset();
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        assertEquals("Alice, Bob are on your extended ignore list.", messages.get(2));
    }

    @Test
    public void barbarianAssaultOwnTeamMatchesAliasesAndIgnoresSelfAndEmptySlots()
    {
        ignoredNames.add("old alice");
        ignoredNames.add("me");
        ignoredNames.add("-----");
        baName(InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_NAME, "Me");
        baName(InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_PLAYER_1_NAME,
            "<col=ffa81f>OLD\u00a0Alice</col>");
        baName(InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_PLAYER_2_NAME, "-----");
        baName(InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_PLAYER_3_NAME, "Alice Prime");
        baName(InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_PLAYER_4_NAME, "");

        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);
        monitor.refresh(GroupNotificationMode.NOTIFICATION_AND_CHAT);

        assertEquals(List.of("OLD Alice is on your extended ignore list."), messages);
        assertEquals("OLD Alice", singleIgnoredNames.get(0));
    }

    @Test
    public void barbarianAssaultUnacceptedScrollAndHiddenRosterDoNotNotify()
    {
        baName(InterfaceID.BarbassaultScrollPl2.BARBASSAULT_SCROLL_PL2_TN1, "Alice");
        Widget hidden = baName(InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_NAME, "Alice");
        when(hidden.isHidden()).thenReturn(true);

        monitor.refresh(GroupNotificationMode.CHAT_ONLY);

        assertTrue(messages.isEmpty());
    }

    @Test
    public void barbarianAssaultStateSurvivesWavesAndResetsOnTeamChangesOrLeaving()
    {
        int component = InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_NAME;
        Widget name = baName(component, "Alice");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getWidget(component)).thenReturn(null);
        when(client.getVarbitValue(VarbitID.BARBASSAULT_AREAEXIT_PENDING)).thenReturn(1);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getVarbitValue(VarbitID.BARBASSAULT_AREAEXIT_PENDING)).thenReturn(0);
        when(client.getLocalPlayer().getWorldLocation()).thenReturn(new WorldPoint(2576, 5264, 0));
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getWidget(component)).thenReturn(name);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(1, messages.size());

        when(name.getText()).thenReturn("-----");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(name.getText()).thenReturn("Alice");
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(2, messages.size());

        when(client.getWidget(component)).thenReturn(null);
        when(client.getLocalPlayer().getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        when(client.getWidget(component)).thenReturn(name);
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(3, messages.size());
    }

    @Test
    public void barbarianAssaultRespectsModesCensoringAndMultipleMatches()
    {
        censorName = true;
        ignoredNames.add("bob");
        baName(InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_NAME, "Alice");
        baName(InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_PLAYER_1_NAME, "Bob");
        monitor.refresh(GroupNotificationMode.NONE);
        assertTrue(messages.isEmpty());
        monitor.refresh(GroupNotificationMode.CHAT_ONLY);
        assertEquals(List.of("Some people are on your extended ignore list."), messages);
        assertEquals(null, singleIgnoredNames.get(0));
    }

    private Widget baName(int component, String name)
    {
        Widget widget = text(name);
        when(client.getWidget(component)).thenReturn(widget);
        return widget;
    }

    private void tobParty(String name)
    {
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSLOT)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.TOB_CLIENT_P1)).thenReturn(name == null ? 0 : 1);
        when(client.getVarcStrValue(VarClientID.TOB_CLIENT_NAME1)).thenReturn(name);
    }

    private Widget text(String name)
    {
        Widget widget = mock(Widget.class);
        when(widget.getText()).thenReturn(name);
        return widget;
    }
}
