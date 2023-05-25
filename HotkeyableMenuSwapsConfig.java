package net.runelite.client.plugins.customswap;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("hotkeyablemenuswaps")
public interface HotkeyableMenuSwapsConfig extends Config
{
	@ConfigSection(name = "Custom Swaps", description = "Text-based custom swaps", position = -10)
	String customSwapsSection = "Custom Swaps";

	@ConfigItem(
		keyName = "customSwaps",
		name = "Custom swaps",
		description = "Options to swap to the top.",
		section = customSwapsSection,
		position = 0
	)
	default String customSwaps() {
		return "";
	}

	@ConfigSection(name = "Custom Swaps (shift)", description = "Text-based custom swaps", position = -10)
	String customShiftSwapsSection = "Custom Swaps (shift)";

	@ConfigItem(
		keyName = "customShiftSwaps",
		name = "Custom swaps (shift)",
		description = "Options to swap to the top when shift is held.",
		section = customShiftSwapsSection,
		position = 0
	)
	default String customShiftSwaps() {
		return "";
	}

	@ConfigSection(name = "Custom Hides", description = "Text-based custom menu entry hides", position = -9)
	String customHidesSection = "Custom Hides";

	@ConfigItem(
		keyName = "customHides",
		name = "Custom hides",
		description = "Options to swap to the top.",
		section = customHidesSection,
		position = 1
	)
	default String customHides() {
		return "";
	}

}
