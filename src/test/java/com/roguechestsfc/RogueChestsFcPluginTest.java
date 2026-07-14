package com.roguechestsfc;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RogueChestsFcPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(RogueChestsFcPlugin.class);
        RuneLite.main(args);
    }
}