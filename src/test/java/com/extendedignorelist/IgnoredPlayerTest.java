package com.extendedignorelist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class IgnoredPlayerTest
{
    @Test
    public void aliasesAreTrackedWithoutDuplicates()
    {
        IgnoredPlayer player = new IgnoredPlayer("Old Name", Arrays.asList("Old Name", "Older Name"));
        player.addAlias("New Name");
        player.addAlias("New Name");

        assertEquals("Old Name", player.getCurrentName());
        assertEquals(2, player.getAliases().size());
        assertTrue(player.getAliases().contains("Older Name"));
        assertTrue(player.getAliases().contains("New Name"));
        assertFalse(player.getAliases().contains("Old Name"));
    }

    @Test
    public void setCurrentNameDoesNotAddDuplicateAlias()
    {
        IgnoredPlayer player = new IgnoredPlayer("Alpha");
        player.addAlias("Beta");
        player.setCurrentName("Gamma");
        player.addAlias("Gamma");

        assertEquals("Gamma", player.getCurrentName());
        assertEquals(1, player.getAliases().size());
        assertTrue(player.getAliases().contains("Beta"));
    }

    @Test
    public void notesCanBeUpdatedAndNullBecomesEmpty()
    {
        IgnoredPlayer player = new IgnoredPlayer("Alpha");
        player.setNote("Keep an eye on this player");

        assertEquals("Keep an eye on this player", player.getNote());

        player.setNote(null);
        assertEquals("", player.getNote());
    }
}
