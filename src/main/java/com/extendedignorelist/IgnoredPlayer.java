package com.extendedignorelist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class IgnoredPlayer
{
    private String currentName;
    private String note = "";
    private final LinkedHashSet<String> aliases = new LinkedHashSet<>();

    public IgnoredPlayer(String currentName)
    {
        this.currentName = currentName;
    }

    public IgnoredPlayer(String currentName, Collection<String> aliases)
    {
        this.currentName = currentName;
        addAliases(aliases);
    }

    public IgnoredPlayer(String currentName, Collection<String> aliases, String note)
    {
        this(currentName, aliases);
        setNote(note);
    }

    public String getCurrentName()
    {
        return currentName;
    }

    public void setCurrentName(String currentName)
    {
        this.currentName = currentName;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        this.note = note == null ? "" : note;
    }

    public List<String> getAliases()
    {
        return new ArrayList<>(aliases);
    }

    public void addAlias(String alias)
    {
        if (alias == null || alias.isEmpty() || alias.equals(currentName))
        {
            return;
        }

        aliases.add(alias);
    }

    public void addAliases(Collection<String> aliases)
    {
        if (aliases == null)
        {
            return;
        }

        for (String alias : aliases)
        {
            addAlias(alias);
        }
    }
}