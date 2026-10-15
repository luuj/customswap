package net.runelite.client.plugins.customswap;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("hotkeyablemenuswaps")
public interface HotkeyableMenuSwapsConfig extends Config
{
	@ConfigSection(
		name = "Swaps",
		description = "Menu options to move to the top",
		position = 0
	)
	String customSwapsSection = "Custom Swaps";

	@ConfigSection(
		name = "Shift swaps",
		description = "Menu options to move to the top while shift is held",
		position = 1
	)
	String customShiftSwapsSection = "Custom Swaps (shift)";

	@ConfigSection(
		name = "Hides",
		description = "Menu options to remove",
		position = 2
	)
	String customHidesSection = "Custom Hides";

	@ConfigItem(
		keyName = "customSwaps",
		name = "Swap list",
		description = "One entry per line: option, target, then optionally the top option and target it should replace. "
			+ "Use * as a wildcard. Example: wield, dragon dagger",
		position = 0,
		section = customSwapsSection
	)
	default String customSwaps()
	{
		return "";
	}

	@ConfigItem(
		keyName = "customShiftSwaps",
		name = "Shift swap list",
		description = "Same format as the swap list; only used while shift is held",
		position = 0,
		section = customShiftSwapsSection
	)
	default String customShiftSwaps()
	{
		return "";
	}

	@ConfigItem(
		keyName = "customHides",
		name = "Hide list",
		description = "One entry per line: option, target. Matching menu options are removed. Use * as a wildcard. Example: walk here, *",
		position = 0,
		section = customHidesSection
	)
	default String customHides()
	{
		return "";
	}
}
