package com.playerrisk;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import java.awt.Color;

@ConfigGroup("PlayerRisk")
public interface PlayerRiskConfig extends Config
{
	@ConfigSection(
		name = "Display Settings",
		description = "Configure how player risks are displayed",
		position = 1
	)
	String displaySection = "displaySection";

	@ConfigItem(
		keyName = "renderMode",
		name = "Render Mode",
		description = "Choose how to display player risks: Overhead (above heads), UI (overlay window), or Both",
		section = displaySection,
		position = 1
	)
	default RenderMode renderMode()
	{
		return RenderMode.UI;
	}

	enum RenderMode
	{
		NONE("None"),
		OVERHEAD("Above Head"),
		UI("UI Overlay"),
		BOTH("Both");

		private final String displayName;

		RenderMode(String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	@ConfigItem(
		keyName = "enableOutsidePVP",
		name = "Enable Outside PVP",
		description = "Allow the plugin to run and display player risks even when not in PVP-enabled zones",
		section = displaySection,
		position = 2
	)
	default boolean enableOutsidePVP()
	{
		return false;
	}

	@ConfigItem(
		keyName = "aboveHeadTextSize",
		name = "Above Head Text Size",
		description = "Size of the text displayed above player heads",
		section = displaySection,
		position = 3
	)
	default int aboveHeadTextSize()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "aboveHeadDistance",
		name = "Above Head Distance",
		description = "Distance above player heads to display the value text",
		section = displaySection,
		position = 4
	)
	default int aboveHeadDistance()
	{
		return 40;
	}

	@ConfigItem(
		keyName = "outlinePlayer",
		name = "Outline Players",
		description = "Draw outlines around players based on their value (uses low/medium/high value colors)",
		section = displaySection,
		position = 5
	)
	default boolean outlinePlayer()
	{
		return false;
	}

	@ConfigItem(
		keyName = "outlineWidth",
		name = "Outline Width",
		description = "Width of the player outline",
		section = displaySection,
		position = 6
	)
	default int outlineWidth()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "outlineFeather",
		name = "Outline Feather",
		description = "Feather amount for the player outline (0 = no feathering)",
		section = displaySection,
		position = 7
	)
	default int outlineFeather()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "enableCheckRiskMenu",
		name = "Enable Check Risk Menu",
		description = "Add 'Check Risk' option to player right-click menus",
		section = displaySection,
		position = 8
	)
	default boolean enableCheckRiskMenu()
	{
		return true;
	}

	@ConfigSection(
		name = "Value Calculation",
		description = "Configure how player risks are calculated",
		position = 2
	)
	String calculationSection = "calculationSection";

	@ConfigItem(
		keyName = "alwaysShowTotalEquipmentValue",
		name = "Always Show Total Equipment Value",
		description = "When enabled, shows total value of all equipment. When disabled, shows value of equipment EXCEPT top items (items kept on death).",
		section = calculationSection,
		position = 1
	)
	default boolean alwaysShowTotalEquipmentValue()
	{
		return false;
	}

	@ConfigItem(
		keyName = "assumeProtectItemIsOn",
		name = "Assume Protect Item is on",
		description = "When 'Always Show Total Equipment Value' is disabled: ignore 4 items instead of 3 (simulates Protect Item prayer being active). When enabled: this setting has no effect.",
		section = calculationSection,
		position = 2
	)
	default boolean assumeProtectItemIsOn()
	{
		return false;
	}

	@ConfigItem(
		keyName = "detectSkullStatus",
		name = "Detect Skull Status",
		description = "Automatically detect if players are skulled and adjust gear value calculations accordingly. Skulled players keep fewer items on death.",
		section = calculationSection,
		position = 3
	)
	default boolean detectSkullStatus()
	{
		return true;
	}

	@ConfigSection(
		name = "Value Thresholds",
		description = "Configure value thresholds for color coding",
		position = 3
	)
	String thresholdSection = "thresholdSection";

	@ConfigItem(
		keyName = "mediumThreshold",
		name = "Medium Threshold",
		description = "Value threshold for medium value players (in GP)",
		section = thresholdSection,
		position = 1
	)
	default int mediumThreshold()
	{
		return 100000;
	}

	@ConfigItem(
		keyName = "highThreshold",
		name = "High Threshold",
		description = "Value threshold for high value players (in GP). Must be greater than medium threshold.",
		section = thresholdSection,
		position = 2
	)
	default int highThreshold()
	{
		return 500000;
	}

	@ConfigSection(
		name = "Color Scheme",
		description = "Configure colors for different value ranges",
		position = 4
	)
	String colorSection = "colorSection";

	@ConfigItem(
		keyName = "lowValueColor",
		name = "Low Value Color",
		description = "Color for low value players (below medium threshold)",
		section = colorSection,
		position = 1
	)
	default Color lowValueColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
		keyName = "mediumValueColor",
		name = "Medium Value Color",
		description = "Color for medium value players (between medium and high thresholds)",
		section = colorSection,
		position = 2
	)
	default Color mediumValueColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
		keyName = "highValueColor",
		name = "High Value Color",
		description = "Color for high value players (above high threshold)",
		section = colorSection,
		position = 3
	)
	default Color highValueColor()
	{
		return Color.RED;
	}
}
