package net.runelite.client.plugins.customswap;

import com.google.inject.Inject;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.PostMenuSort;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "<html><font color=#b82584>[J] Custom Swaps",
	description = "Moves or removes menu options from text-based lists, with a separate list for shift",
	tags = {"entry", "swapper", "custom", "text", "menu", "hide"}
)
public class HotkeyableMenuSwapsPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "hotkeyablemenuswaps";

	@Inject
	private Client client;

	@Inject
	private HotkeyableMenuSwapsConfig config;

	final List<CustomSwap> customSwaps = new ArrayList<>();
	final List<CustomSwap> customShiftSwaps = new ArrayList<>();
	final List<CustomSwap> customHides = new ArrayList<>();

	@Provides
	HotkeyableMenuSwapsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HotkeyableMenuSwapsConfig.class);
	}

	@Override
	protected void startUp()
	{
		reloadCustomSwaps();
	}

	@Override
	protected void shutDown()
	{
		customSwaps.clear();
		customShiftSwaps.clear();
		customHides.clear();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (CONFIG_GROUP.equals(configChanged.getGroup()))
		{
			reloadCustomSwaps();
		}
	}

	@Subscribe(priority = -1) // Runs after the normal menu entry swapper, so it won't interfere with this plugin.
	public void onPostMenuSort(PostMenuSort e)
	{
		// Don't reorder a menu the player is currently looking at.
		if (client.isMenuOpen())
		{
			return;
		}

		customSwaps();
	}

	private void reloadCustomSwaps()
	{
		customSwaps.clear();
		customSwaps.addAll(loadCustomSwaps(config.customSwaps()));

		customShiftSwaps.clear();
		customShiftSwaps.addAll(loadCustomSwaps(config.customShiftSwaps()));

		customHides.clear();
		customHides.addAll(loadCustomSwaps(config.customHides()));
	}

	private static List<CustomSwap> loadCustomSwaps(String customSwaps)
	{
		List<CustomSwap> swaps = new ArrayList<>();
		for (String customSwap : customSwaps.split("\n"))
		{
			if (customSwap.trim().isEmpty())
			{
				continue;
			}
			swaps.add(CustomSwap.fromString(customSwap));
		}
		return swaps;
	}

	private void customSwaps()
	{
		// PostMenuSort runs every frame, so bail out before touching the menu when there's nothing to do.
		List<CustomSwap> swaps = client.isKeyPressed(KeyCode.KC_SHIFT) ? customShiftSwaps : customSwaps;
		if (swaps.isEmpty() && customHides.isEmpty())
		{
			return;
		}

		Menu menu = client.getMenu();
		MenuEntry[] menuEntries = menu.getMenuEntries();
		int count = menuEntries.length;
		if (count == 0)
		{
			return;
		}

		// Standardize each entry's text once and reuse it for both the hide and swap passes.
		String[] options = new String[count];
		String[] targets = new String[count];
		for (int i = 0; i < count; i++)
		{
			options[i] = Text.standardize(menuEntries[i].getOption());
			targets[i] = Text.standardize(menuEntries[i].getTarget());
		}

		boolean changed = false;

		if (!customHides.isEmpty())
		{
			String topOption = options[count - 1];
			String topTarget = targets[count - 1];
			int kept = 0;
			for (int i = 0; i < count; i++)
			{
				if (matches(options[i], targets[i], topOption, topTarget, customHides) == -1)
				{
					menuEntries[kept] = menuEntries[i];
					options[kept] = options[i];
					targets[kept] = targets[i];
					kept++;
				}
			}

			if (kept != count)
			{
				MenuEntry[] filtered = new MenuEntry[kept];
				System.arraycopy(menuEntries, 0, filtered, 0, kept);
				menuEntries = filtered;
				count = kept;
				changed = true;
			}

			if (count == 0)
			{
				// Every option was hidden; nothing left to swap.
				menu.setMenuEntries(menuEntries);
				return;
			}
		}

		if (!swaps.isEmpty())
		{
			int topEntryIndex = count - 1;
			int entryIndex = getEntryIndexToSwap(options, targets, count, swaps);
			if (entryIndex >= 0)
			{
				MenuEntry topEntry = menuEntries[topEntryIndex];
				MenuEntry entryToSwap = menuEntries[entryIndex];

				// The client opens the right-click menu on left-click if the top entry is CC_OP_LOW_PRIORITY.
				if (entryToSwap.getType() == MenuAction.CC_OP_LOW_PRIORITY)
				{
					entryToSwap.setType(MenuAction.CC_OP);
				}

				if (topEntryIndex > entryIndex)
				{
					menuEntries[topEntryIndex] = entryToSwap;
					menuEntries[entryIndex] = topEntry;
					changed = true;
				}
			}
		}

		if (changed)
		{
			menu.setMenuEntries(menuEntries);
		}
	}

	private static int getEntryIndexToSwap(String[] options, String[] targets, int count, List<CustomSwap> swaps)
	{
		int entryIndex = -1;
		int latestMatchingSwapIndex = -1;
		String topOption = options[count - 1];
		String topTarget = targets[count - 1];
		// Prefer to swap menu entries that are already at or near the top of the list.
		for (int i = count - 1; i >= 0; i--)
		{
			int swapIndex = matches(options[i], targets[i], topOption, topTarget, swaps);
			if (swapIndex > latestMatchingSwapIndex)
			{
				entryIndex = i;
				latestMatchingSwapIndex = swapIndex;
			}
		}
		return entryIndex;
	}

	private static int matches(String entryOption, String entryTarget, String topEntryOption, String topEntryTarget, List<CustomSwap> swaps)
	{
		for (int i = 0; i < swaps.size(); i++)
		{
			if (swaps.get(i).matches(entryOption, entryTarget, topEntryOption, topEntryTarget))
			{
				return i;
			}
		}
		return -1;
	}

	static class CustomSwap
	{
		// copied from runelite.
		private static final Pattern WILDCARD_PATTERN = Pattern.compile("(?i)[^*]+|(\\*)");

		private final String option;
		private final String target;
		private final String topOption;
		private final String topTarget;

		private final Type optionType;
		private final Type targetType;
		private final Type topOptionType;
		private final Type topTargetType;

		// Wildcard patterns are compiled once here instead of on every String.matches() call.
		private final Pattern optionPattern;
		private final Pattern targetPattern;
		private final Pattern topOptionPattern;
		private final Pattern topTargetPattern;

		private enum Type
		{
			EQUALS,
			STARTS_WITH,
			ENDS_WITH,
			CONTAINS,
			WILDCARD,
			IGNORE
		}

		static CustomSwap fromString(String s)
		{
			String[] split = s.split(",");
			return new CustomSwap(
				split[0].toLowerCase().trim(),
				split.length > 1 ? split[1].toLowerCase().trim() : "",
				split.length > 2 ? split[2].toLowerCase().trim() : null,
				split.length > 3 ? split[3].toLowerCase().trim() : null
			);
		}

		CustomSwap(String option, String target)
		{
			this(option, target, null, null);
		}

		CustomSwap(String option, String target, String topOption, String topTarget)
		{
			this.optionType = getType(option);
			this.option = removeStars(option);
			this.optionPattern = compileIfWildcard(option, optionType);

			this.targetType = getType(target);
			this.target = removeStars(target);
			this.targetPattern = compileIfWildcard(target, targetType);

			this.topOptionType = getType(topOption);
			this.topOption = removeStars(topOption);
			this.topOptionPattern = compileIfWildcard(topOption, topOptionType);

			this.topTargetType = getType(topTarget);
			this.topTarget = removeStars(topTarget);
			this.topTargetPattern = compileIfWildcard(topTarget, topTargetType);
		}

		private static Type getType(String s)
		{
			if (s == null)
			{
				return Type.IGNORE;
			}

			int star = s.indexOf('*');
			if (star == -1)
			{
				return Type.EQUALS;
			}
			if (star == 0)
			{
				if (s.length() == 1)
				{
					return Type.IGNORE;
				}
				star = s.indexOf('*', star + 1);
				if (star == -1)
				{
					return Type.ENDS_WITH;
				}
				if (star == s.length() - 1)
				{
					return Type.CONTAINS;
				}
			}
			else if (star == s.length() - 1)
			{
				return Type.STARTS_WITH;
			}

			return Type.WILDCARD;
		}

		private static Pattern compileIfWildcard(String s, Type type)
		{
			return type == Type.WILDCARD ? Pattern.compile(generateWildcardMatcher(s)) : null;
		}

		private static String generateWildcardMatcher(String pattern)
		{
			final Matcher matcher = WILDCARD_PATTERN.matcher(pattern);
			final StringBuilder buffer = new StringBuilder();

			buffer.append("(?i)");
			while (matcher.find())
			{
				if (matcher.group(1) != null)
				{
					matcher.appendReplacement(buffer, ".*");
				}
				else
				{
					matcher.appendReplacement(buffer, Matcher.quoteReplacement(Pattern.quote(matcher.group(0))));
				}
			}

			matcher.appendTail(buffer);
			return buffer.toString();
		}

		private static String removeStars(String s)
		{
			return s == null ? null : s.replace("*", "");
		}

		/** skip top option comparison, for testing */
		boolean matches(String option, String target)
		{
			return matches(option, target, "", "");
		}

		boolean matches(String option, String target, String topOption, String topTarget)
		{
			return matches(option, this.option, optionType, optionPattern)
				&& matches(target, this.target, targetType, targetPattern)
				&& matches(topOption, this.topOption, topOptionType, topOptionPattern)
				&& matches(topTarget, this.topTarget, topTargetType, topTargetPattern);
		}

		private static boolean matches(String menuText, String swapText, Type type, Pattern pattern)
		{
			switch (type)
			{
				case IGNORE:
					return true;
				case EQUALS:
					return menuText.equals(swapText);
				case STARTS_WITH:
					return menuText.startsWith(swapText);
				case ENDS_WITH:
					return menuText.endsWith(swapText);
				case CONTAINS:
					return menuText.contains(swapText);
				case WILDCARD:
					return pattern.matcher(menuText).matches();
				default:
					// shouldn't happen.
					throw new IllegalStateException();
			}
		}
	}
}
