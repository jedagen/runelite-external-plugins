package com.playerrisk;

import com.playerrisk.PlayerRiskPlugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExamplePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PlayerRiskPlugin.class);
		RuneLite.main(args);
	}
}