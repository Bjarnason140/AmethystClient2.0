package com.amethystclient.esp;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** Block types the Block ESP can outline. defaultColor is 0xRRGGBB. */
public enum EspType {
	CHEST("Chests", 0xB388FF),
	TRAPPED_CHEST("Trapped Chests", 0xFF6E8A),
	ENDER_CHEST("Ender Chests", 0xC77DFF),
	BARREL("Barrels", 0xD9A066),
	SHULKER("Shulker Boxes", 0xE0AAFF),
	HOPPER("Hoppers", 0x9BA3FF),
	SPAWNER("Spawners", 0xFF5C8A),
	DROPPER("Droppers", 0x7DE3D0),
	DISPENSER("Dispensers", 0x7DB8FF);

	public final String label;
	public final int defaultColor;

	EspType(String label, int defaultColor) {
		this.label = label;
		this.defaultColor = defaultColor;
	}

	public static EspType of(Block b) {
		if (b == Blocks.CHEST) return CHEST;
		if (b == Blocks.TRAPPED_CHEST) return TRAPPED_CHEST;
		if (b == Blocks.ENDER_CHEST) return ENDER_CHEST;
		if (b == Blocks.BARREL) return BARREL;
		if (b == Blocks.HOPPER) return HOPPER;
		if (b == Blocks.SPAWNER) return SPAWNER;
		if (b == Blocks.DROPPER) return DROPPER;
		if (b == Blocks.DISPENSER) return DISPENSER;
		if (b instanceof ShulkerBoxBlock) return SHULKER;
		return null;
	}
}
