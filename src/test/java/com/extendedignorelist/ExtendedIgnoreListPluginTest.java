package com.extendedignorelist;

import net.runelite.client.RuneLite;

public class ExtendedIgnoreListPluginTest
{
    public static void main(String[] args) throws Exception
    {
        PluginLauncher.loadBuiltin(ExtendedIgnoreListPlugin.class);
        RuneLite.main(args);
    }
}