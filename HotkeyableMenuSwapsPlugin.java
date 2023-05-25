package net.runelite.client.plugins.customswap;

import static com.google.common.base.Predicates.alwaysTrue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import static net.runelite.api.MenuAction.*;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.PostMenuSort;
import static net.runelite.api.widgets.WidgetID.SPELLBOOK_GROUP_ID;
import static net.runelite.api.widgets.WidgetInfo.TO_GROUP;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.menuentryswapper.MenuEntrySwapperConfig;
import net.runelite.client.plugins.menuentryswapper.MenuEntrySwapperPlugin;
import net.runelite.client.util.Text;


@PluginDescriptor(
	name = "<html><font color=#b82584>[J] Custom Swaps",
	tags = {"entry", "swapper", "custom", "text"}
)
@PluginDependency(MenuEntrySwapperPlugin.class)
public class HotkeyableMenuSwapsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private HotkeyableMenuSwapsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private MenuEntrySwapperConfig menuEntrySwapperConfig;
	final List<CustomSwap> customSwaps = new ArrayList<>();
	final List<CustomSwap> customShiftSwaps = new ArrayList<>();
	final List<CustomSwap> customHides = new ArrayList<>();

	@Provides
	HotkeyableMenuSwapsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HotkeyableMenuSwapsConfig.class);
	}

	@Override
	protected void startUp() {
		reloadCustomSwaps();
	}
	@Override
	protected void shutDown() {}
	private final Multimap<String, Swap> swaps = LinkedHashMultimap.create();
	private final ArrayListMultimap<String, Integer> optionIndexes = ArrayListMultimap.create();

	@Subscribe(priority = -1) // This will run after the normal menu entry swapper, so it won't interfere with this plugin.
	public void onPostMenuSort(PostMenuSort e)
	{
		customSwaps();

		MenuEntry[] menuEntries = client.getMenuEntries();

		// Build option map for quick lookup in findIndex
		int idx = 0;
		optionIndexes.clear();
		for (MenuEntry entry : menuEntries)
		{
			String option = Text.removeTags(entry.getOption()).toLowerCase();
			optionIndexes.put(option, idx++);
		}

		// Perform swaps
		for (int i = 0; i < menuEntries.length; i++)
		{
			swapMenuEntry(menuEntries, i);
		}
	}

	@Value
	class Swap
	{
		private Predicate<String> optionPredicate;
		private Predicate<String> targetPredicate;
		private String swappedOption;
		private Supplier<Boolean> enabled;
		private boolean strict;
	}

	private void swapMenuEntry(MenuEntry[] menuEntries, int index)
	{
		MenuEntry menuEntry = menuEntries[index];
		String option = Text.removeTags(menuEntry.getOption()).toLowerCase();
		String target = Text.removeTags(menuEntry.getTarget()).toLowerCase();
		MenuAction menuAction = menuEntry.getType();

		Collection<Swap> swaps = this.swaps.get(option);
		for (Swap swap : swaps)
		{
			if (swap.getTargetPredicate().test(target) && swap.getEnabled().get())
			{
				if (swap(swap.getSwappedOption(), target, index, swap.isStrict()))
				{
					break;
				}
			}
		}
	}

	private void swap(String option, Predicate<String> targetPredicate, String swappedOption, Supplier<Boolean> enabled)
	{
		swaps.put(option, new Swap(alwaysTrue(), targetPredicate, swappedOption, enabled, true));
	}

	private boolean swap(String option, String target, int index, boolean strict)
	{
		MenuEntry[] menuEntries = client.getMenuEntries();

		int optionIdx = findIndex(menuEntries, index, option, target, strict);

		if (optionIdx >= 0)
		{
			swap(optionIndexes, menuEntries, optionIdx, index);
			return true;
		}

		return false;
	}

	private boolean swap(MenuAction menuAction, String target, int index)
	{
		MenuEntry[] menuEntries = client.getMenuEntries();

		int optionIdx = findIndex(menuEntries, index, menuAction, target);

		if (optionIdx >= 0)
		{
			swap(optionIndexes, menuEntries, optionIdx, index);
			return true;
		}

		return false;
	}

	private int findIndex(MenuEntry[] entries, int limit, MenuAction menuAction, String target)
	{
		for (int i = limit - 1; i >= 0; --i)
		{
			MenuEntry entry = entries[i];

			if (entry.getType() != menuAction) continue;

			String entryTarget = Text.removeTags(entry.getTarget()).toLowerCase();
			if (entryTarget.equals(target))
			{
				return i;
			}
		}

		return -1;
	}

	private int findIndex(MenuEntry[] entries, int limit, String option, String target, boolean strict)
	{
		if (strict)
		{
			List<Integer> indexes = optionIndexes.get(option.toLowerCase());

			// We want the last index which matches the target, as that is what is top-most
			// on the menu
			for (int i = indexes.size() - 1; i >= 0; --i)
			{
				int idx = indexes.get(i);
				MenuEntry entry = entries[idx];
				String entryTarget = Text.removeTags(entry.getTarget()).toLowerCase();

				// Limit to the last index which is prior to the current entry
				if (idx < limit && entryTarget.equals(target))
				{
					return idx;
				}
			}
		}
		else
		{
			// Without strict matching we have to iterate all entries up to the current limit...
			for (int i = limit - 1; i >= 0; i--)
			{
				MenuEntry entry = entries[i];
				String entryOption = Text.removeTags(entry.getOption()).toLowerCase();
				String entryTarget = Text.removeTags(entry.getTarget()).toLowerCase();

				if (entryOption.contains(option.toLowerCase()) && entryTarget.equals(target))
				{
					return i;
				}
			}

		}

		return -1;
	}

	private void swap(ArrayListMultimap<String, Integer> optionIndexes, MenuEntry[] entries, int index1, int index2)
	{
		MenuEntry entry1 = entries[index1],
				entry2 = entries[index2];

		entries[index1] = entry2;
		entries[index2] = entry1;

		client.setMenuEntries(entries);

		// Update optionIndexes
		String option1 = Text.removeTags(entry1.getOption()).toLowerCase(),
				option2 = Text.removeTags(entry2.getOption()).toLowerCase();

		List<Integer> list1 = optionIndexes.get(option1),
				list2 = optionIndexes.get(option2);

		// call remove(Object) instead of remove(int)
		list1.remove((Integer) index1);
		list2.remove((Integer) index2);

		sortedInsert(list1, index2);
		sortedInsert(list2, index1);
	}

	private static <T extends Comparable<? super T>> void sortedInsert(List<T> list, T value) // NOPMD: UnusedPrivateMethod: false positive
	{
		int idx = Collections.binarySearch(list, value);
		list.add(idx < 0 ? -idx - 1 : idx, value);
	}

	private void swapContains(String option, Predicate<String> targetPredicate, String swappedOption, Supplier<Boolean> enabled)
	{
		swaps.put(option, new Swap(alwaysTrue(), targetPredicate, swappedOption, enabled, false));
	}

	@Getter
	@ToString
	@RequiredArgsConstructor
	@EqualsAndHashCode
	static class CustomSwap
	{
		private final String option;
		private final String target;
		private final String topOption;
		private final String topTarget;

		private final Type optionType;
		private final Type targetType;
		private final Type topOptionType;
		private final Type topTargetType;

		private enum Type {
			EQUALS,
			STARTS_WITH,
			ENDS_WITH,
			CONTAINS,
			WILDCARD,
			IGNORE
		}

		public static CustomSwap fromString(String s)
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
			this.option = prepareMatch(option, optionType);
			this.targetType = getType(target);
			this.target = prepareMatch(target, targetType);
			this.topOptionType = getType(topOption);
			this.topOption = prepareMatch(topOption, topOptionType);
			this.topTargetType = getType(topTarget);
			this.topTarget = prepareMatch(topTarget, topTargetType);
		}

		private static Type getType(String s)
		{
			if (s == null) return Type.IGNORE;

			int star = s.indexOf('*');
			if (star == -1) return Type.EQUALS;
			if (star == 0) {
				if (s.length() == 1) return Type.IGNORE;
				star = s.indexOf('*', star + 1);
				if (star == -1) return Type.ENDS_WITH;
				if (star == s.length() - 1) return Type.CONTAINS;
			} else if (star == s.length() - 1) {
				return Type.STARTS_WITH;
			}

			return Type.WILDCARD;
		}

		private String prepareMatch(String option, Type optionType)
		{
			return optionType == Type.WILDCARD ? generateWildcardMatcher(option) : removeStars(option);
		}

		// copied from runelite.
		private static final Pattern WILDCARD_PATTERN = Pattern.compile("(?i)[^*]+|(\\*)");
		private static String generateWildcardMatcher(String pattern)
		{
			final Matcher matcher = WILDCARD_PATTERN.matcher(pattern);
			final StringBuffer buffer = new StringBuffer();

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

		private String removeStars(String s)
		{
			return s == null ? s : s.replaceAll("\\*", "");
		}

		/** skip top option comparison, for testing */
		boolean matches(String option, String target)
		{
			return matches(option, target, "", "");
		}

		public boolean matches(String option, String target, String topOption, String topTarget)
		{
			return matches(option, this.option, optionType) &&
				matches(target, this.target, targetType) &&
				matches(topOption, this.topOption, topOptionType) &&
				matches(topTarget, this.topTarget, topTargetType);
		}

		private static boolean matches(String menuText, String swapText, Type type)
		{
			switch (type) {
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
					return menuText.matches(swapText);
				default:
					// shouldn't happen.
					throw new IllegalStateException();
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged) {
		if (configChanged.getGroup().equals("hotkeyablemenuswaps")) {
			reloadCustomSwaps();
		}
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

	private Collection<? extends CustomSwap> loadCustomSwaps(String customSwaps)
	{
		List<CustomSwap> swaps = new ArrayList<>();
		for (String customSwap : customSwaps.split("\n"))
		{
			if (customSwap.trim().equals("")) continue;
			swaps.add(CustomSwap.fromString(customSwap));
		}
		return swaps;
	}

	private boolean shiftModifier()
	{
		return client.isKeyPressed(KeyCode.KC_SHIFT);
	}

	public void customSwaps()
	{
		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries.length == 0) return;

		menuEntries = filterEntries(menuEntries);
		int topEntryIndex = menuEntries.length - 1;
		if (topEntryIndex == -1) { // The filtering removed all the menu options. No swaps can happen, so return early.
			client.setMenuEntries(menuEntries);
			return;
		}
		MenuEntry topEntry = menuEntries[topEntryIndex];
		if (mayNotBeLeftClick(topEntry)) {
			return;
		}

		int entryIndex = getEntryIndexToSwap(menuEntries, shiftModifier() ? customShiftSwaps : customSwaps);
		if (entryIndex >= 0)
		{
			MenuEntry entryToSwap = menuEntries[entryIndex];
			if (isProtected(topEntry) || isProtected(entryToSwap)) {
				return;
			}

			// the client will open the right-click menu on left-click if the entry at the top is a CC_OP_LOW_PRIORITY.
			if (entryToSwap.getType() == MenuAction.CC_OP_LOW_PRIORITY)
			{
				entryToSwap.setType(MenuAction.CC_OP);
			}

			if (topEntryIndex > entryIndex)
			{
				menuEntries[topEntryIndex] = entryToSwap;
				menuEntries[entryIndex] = topEntry;
			}
		}

		client.setMenuEntries(menuEntries);
	}

	private int getEntryIndexToSwap(MenuEntry[] menuEntries, List<CustomSwap> swaps)
	{
		int entryIndex = -1;
		int latestMatchingSwapIndex = -1;
		MenuEntry topEntry = menuEntries[menuEntries.length - 1];
		String topEntryOption = Text.standardize(topEntry.getOption());
		String topEntryTarget = Text.standardize(topEntry.getTarget());
		// prefer to swap menu entries that are already at or near the top of the list.
		for (int i = menuEntries.length - 1; i >= 0; i--)
		{
			MenuEntry entry = menuEntries[i];

			String option = Text.standardize(entry.getOption());
			String target = Text.standardize(entry.getTarget());
			int swapIndex = matches(option, target, topEntryOption, topEntryTarget, swaps);
			if (swapIndex > latestMatchingSwapIndex)
			{
				entryIndex = i;
				latestMatchingSwapIndex = swapIndex;
			}
		}
		return entryIndex;
	}

	private boolean isProtected(MenuEntry entry)
	{
		return false;
	}

	private boolean mayNotBeLeftClick(MenuEntry entry)
	{
		return false;
	}

	private MenuEntry[] filterEntries(MenuEntry[] menuEntries)
	{
		List<MenuEntry> filtered = new ArrayList<>();
		MenuEntry topEntry = menuEntries[menuEntries.length - 1];
		String topEntryOption = Text.standardize(topEntry.getOption());
		String topEntryTarget = Text.standardize(topEntry.getTarget());
		for (MenuEntry entry : menuEntries)
		{
			String option = Text.standardize(Text.removeTags(entry.getOption()));
			String target = Text.standardize(Text.removeTags(entry.getTarget()));
			if (matches(option, target, topEntryOption, topEntryTarget, customHides) == -1 || isProtected(entry))
			{
				filtered.add(entry);
			}
		}
		return filtered.toArray(new MenuEntry[0]);
	}

	private static int matches(String entryOption, String entryTarget, String topEntryOption, String topEntryTarget, List<CustomSwap> swaps)
	{
		for (int i = 0; i < swaps.size(); i++)
		{
			CustomSwap swap = swaps.get(i);
			if (swap.matches(entryOption, entryTarget, topEntryOption, topEntryTarget)) {
				return i;
			}
		}
		return -1;
	}
}
