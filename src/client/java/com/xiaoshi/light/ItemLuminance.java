package com.xiaoshi.light;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Luminance rule for handheld light sources: a held item emits light at the default luminance of
 * the block it places, whenever that block is light-emitting (torch 14, lantern 15, soul torch 10,
 * glowstone 15…). OVERRIDES lets individual items be overridden (or disabled) explicitly.
 */
public final class ItemLuminance {
	private static final Map<Item, Integer> OVERRIDES = new HashMap<>();

	private ItemLuminance() {
	}

	/** Registers an explicit item → luminance mapping (0 disables the item). */
	public static void override(Item item, int luminance) {
		OVERRIDES.put(item, luminance);
	}

	/** Returns the luminance of the held stack as a light source; 0 means it emits no light. */
	public static int of(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		Item item = stack.getItem();
		Integer override = OVERRIDES.get(item);
		if (override != null) {
			return override;
		}
		if (item instanceof BlockItem blockItem) {
			return blockItem.getBlock().getDefaultState().getLuminance();
		}
		return 0;
	}

	public static Map<Item, Integer> overrides() {
		return Collections.unmodifiableMap(OVERRIDES);
	}
}
