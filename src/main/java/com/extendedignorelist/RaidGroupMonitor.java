package com.extendedignorelist;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

final class RaidGroupMonitor
{
    private static final int[] TOB_NAMES = {
        VarClientID.TOB_CLIENT_NAME0, VarClientID.TOB_CLIENT_NAME1,
        VarClientID.TOB_CLIENT_NAME2, VarClientID.TOB_CLIENT_NAME3, VarClientID.TOB_CLIENT_NAME4
    };
    private static final int[] TOB_MEMBERS = {
        VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1,
        VarbitID.TOB_CLIENT_P2, VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4
    };
    private static final int[] TOA_NAMES = {
        VarClientID.TOA_CLIENT_NAME0, VarClientID.TOA_CLIENT_NAME1,
        VarClientID.TOA_CLIENT_NAME2, VarClientID.TOA_CLIENT_NAME3,
        VarClientID.TOA_CLIENT_NAME4, VarClientID.TOA_CLIENT_NAME5,
        VarClientID.TOA_CLIENT_NAME6, VarClientID.TOA_CLIENT_NAME7
    };
    private static final int[] TOA_MEMBERS = {
        VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1,
        VarbitID.TOA_CLIENT_P2, VarbitID.TOA_CLIENT_P3,
        VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5,
        VarbitID.TOA_CLIENT_P6, VarbitID.TOA_CLIENT_P7
    };
    // raids_sidepanel_addline stores the untruncated name at child row * 7 + 4.
    private static final int COX_ROW_SIZE = 7;
    private static final int COX_FULL_NAME_OFFSET = 4;

    private final Client client;
    private final Predicate<String> isIgnoredName;
    private final BooleanSupplier censorName;
    private final GroupAlertConsumer notify;
    private final Map<Raid, Set<String>> previousMatches = new EnumMap<>(Raid.class);

    RaidGroupMonitor(Client client, Predicate<String> isIgnoredName,
        BooleanSupplier censorName,
        GroupAlertConsumer notify)
    {
        this.client = client;
        this.isIgnoredName = isIgnoredName;
        this.censorName = censorName;
        this.notify = notify;
    }

    void reset()
    {
        previousMatches.clear();
    }

    void refresh(GroupNotificationMode mode)
    {
        if (mode == GroupNotificationMode.NONE || client.getGameState() != GameState.LOGGED_IN)
        {
            reset();
            return;
        }

        check(Raid.TOB, readParty(VarbitID.TOB_CLIENT_PARTYSTATUS, VarbitID.TOB_CLIENT_PARTYSLOT,
            TOB_NAMES, TOB_MEMBERS), mode);
        check(Raid.TOA, readParty(VarbitID.TOA_CLIENT_PARTYSTATUS, VarbitID.TOA_CLIENT_PARTYSLOT,
            TOA_NAMES, TOA_MEMBERS), mode);
        check(Raid.COX, readChambersParty(), mode);
    }

    private List<String> readParty(int statusVarbit, int slotVarbit, int[] names, int[] members)
    {
        List<String> party = new ArrayList<>();
        int ownSlot = client.getVarbitValue(slotVarbit);
        if (client.getVarbitValue(statusVarbit) == 0 || ownSlot == 0)
        {
            return party;
        }

        for (int i = 0; i < names.length; i++)
        {
            if (i != ownSlot - 1 && client.getVarbitValue(members[i]) != 0)
            {
                party.add(client.getVarcStrValue(names[i]));
            }
        }
        return party;
    }

    private List<String> readChambersParty()
    {
        if (client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 0)
        {
            return new ArrayList<>();
        }

        Widget list = client.getWidget(InterfaceID.RaidsSidepanel.LIST);
        if (list == null || list.getDynamicChildren() == null)
        {
            // An unavailable sidepanel is not evidence that a player left the party.
            return null;
        }

        List<String> party = new ArrayList<>();
        Widget[] children = list.getDynamicChildren();
        for (int i = COX_FULL_NAME_OFFSET; i < children.length; i += COX_ROW_SIZE)
        {
            if (children[i] != null)
            {
                party.add(children[i].getText());
            }
        }
        return party;
    }

    private void check(Raid raid, List<String> party, GroupNotificationMode mode)
    {
        if (party == null)
        {
            return;
        }

        Set<String> currentMatches = new HashSet<>();
        Set<String> previous = previousMatches.getOrDefault(raid, Set.of());
        List<String> newNames = new ArrayList<>();
        String localName = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
        String normalizedLocalName = localName == null ? null : Text.standardize(localName);
        for (String name : party)
        {
            if (name == null)
            {
                continue;
            }
            String cleanName = Text.removeTags(name).replace('\u00a0', ' ').trim();
            String normalizedName = Text.standardize(cleanName);
            if (cleanName.isEmpty() || "-".equals(cleanName) || "null".equalsIgnoreCase(cleanName)
                || normalizedName.equals(normalizedLocalName) || !isIgnoredName.test(cleanName))
            {
                continue;
            }
            if (currentMatches.add(normalizedName) && !previous.contains(normalizedName))
            {
                newNames.add(cleanName);
            }
        }
        previousMatches.put(raid, currentMatches);
        if (!newNames.isEmpty())
        {
            String subject = censorName.getAsBoolean()
                ? (newNames.size() == 1 ? "Someone" : "Some people")
                : String.join(", ", newNames);
            notify.accept(mode, subject
                + (newNames.size() == 1 ? " is" : " are") + " on your extended ignore list.",
                currentMatches.size() == 1 ? newNames.get(0) : null);
        }
    }

    private enum Raid
    {
        TOB,
        TOA,
        COX
    }

    @FunctionalInterface
    interface GroupAlertConsumer
    {
        void accept(GroupNotificationMode mode, String message, String singleIgnoredName);
    }
}
