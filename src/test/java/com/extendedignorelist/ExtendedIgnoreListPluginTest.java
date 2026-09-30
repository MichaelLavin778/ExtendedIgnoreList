package com.extendedignorelist;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExtendedIgnoreListPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ExtendedIgnoreListPlugin.class);
        RuneLite.main(args);
    }
}