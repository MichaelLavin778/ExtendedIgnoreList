package com.extendedignorelist;

import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

final class PluginLauncher
{
    private PluginLauncher()
    {
    }

    @SafeVarargs
    static void loadBuiltin(Class<? extends Plugin>... plugins)
    {
        ExternalPluginManager.loadBuiltin(plugins);
    }
}