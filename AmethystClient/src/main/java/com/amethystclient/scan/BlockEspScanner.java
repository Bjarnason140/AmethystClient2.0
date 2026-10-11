package com.amethystclient.scan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.amethystclient.config.AmethystConfig;
import com.amethystclient.esp.EspType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Periodically collects outlined block-entity blocks around the player into an immutable snapshot. */
public final class BlockEspScanner {
	public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int rgb) {}

	private static volatile List<Box> boxes = List.of();
	private static int timer;

	private BlockEspScanner() {}

	public static List<Box> boxes() { return boxes; }

	/** Forces a rescan on the next tick (called after settings change). */
	public static void refresh() { timer = Integer.MAX_VALUE / 2; }

	public static void tick(Minecraft mc) {
		AmethystConfig cfg = AmethystConfig.INSTANCE;
		ClientLevel level = mc.level;
		if (!cfg.espEnabled || level == null || mc.player == null) {
			if (!boxes.isEmpty()) boxes = List.of();
			return;
		}
		if (++timer < cfg.espScanInterval) return;
		timer = 0;

		double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
		int range = cfg.espRange;
		double rangeSq = (double) range * range;
		int pcx = mc.player.blockPosition().getX() >> 4;
		int pcz = mc.player.blockPosition().getZ() >> 4;
		int cr = (range >> 4) + 1;

		List<Box> found = new ArrayList<>();
		for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
			for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) continue;
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					BlockState state = be.getBlockState();
					EspType type = EspType.of(state.getBlock());
					if (type == null || !cfg.espOn(type)) continue;
					BlockPos pos = be.getBlockPos();
					double dx = pos.getX() + 0.5 - px, dy = pos.getY() + 0.5 - py, dz = pos.getZ() + 0.5 - pz;
					if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
					VoxelShape shape = state.getShape(level, pos);
					AABB bb = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
					found.add(new Box(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, cfg.colorOf(type)));
				}
			}
		}
		found.sort(Comparator.comparingDouble(b -> {
			double dx = (b.minX() + b.maxX()) * 0.5 - px, dy = (b.minY() + b.maxY()) * 0.5 - py, dz = (b.minZ() + b.maxZ()) * 0.5 - pz;
			return dx * dx + dy * dy + dz * dz;
		}));
		if (found.size() > cfg.maxBoxes) found = new ArrayList<>(found.subList(0, cfg.maxBoxes));
		boxes = List.copyOf(found);
	}
}
