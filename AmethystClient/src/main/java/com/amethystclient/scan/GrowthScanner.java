package com.amethystclient.scan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amethystclient.config.AmethystConfig;
import com.amethystclient.esp.GrowthType;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Budgeted, round-robin chunk scanner that flags chunks containing fully grown blocks
 * (things that only finish growing while a player keeps the chunk loaded).
 */
public final class GrowthScanner {
	public record ChunkFlag(int chunkX, int chunkZ, int minY, int maxY, Map<GrowthType, Integer> counts) {
		public int centerX() { return chunkX * 16 + 8; }
		public int centerZ() { return chunkZ * 16 + 8; }

		public String summary() {
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<GrowthType, Integer> e : counts.entrySet()) {
				if (e.getValue() <= 0) continue;
				if (!sb.isEmpty()) sb.append(", ");
				sb.append(e.getKey().shortName).append(" x").append(e.getValue());
			}
			return sb.toString();
		}
	}

	private static final Map<Long, ChunkFlag> FLAGS = new HashMap<>();
	private static final Set<Long> ANNOUNCED = new HashSet<>();
	private static final List<int[]> ORDER = new ArrayList<>();
	private static volatile List<ChunkFlag> snapshot = List.of();

	private static ClientLevel lastLevel;
	private static int cursor, rebuildTimer, publishTimer, announceTimer, announceBudget;
	private static int lastPcx = Integer.MIN_VALUE, lastPcz = Integer.MIN_VALUE, lastRadius = -1;

	private GrowthScanner() {}

	public static List<ChunkFlag> flags() { return snapshot; }

	private static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

	private static void reset() {
		FLAGS.clear();
		ANNOUNCED.clear();
		ORDER.clear();
		snapshot = List.of();
		cursor = 0;
		lastPcx = lastPcz = Integer.MIN_VALUE;
		lastRadius = -1;
		lastLevel = null;
	}

	public static void tick(Minecraft mc) {
		AmethystConfig cfg = AmethystConfig.INSTANCE;
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || !cfg.growthEnabled) {
			if (lastLevel != null || !FLAGS.isEmpty()) reset();
			return;
		}
		if (level != lastLevel) {
			reset();
			lastLevel = level;
		}

		int pcx = mc.player.blockPosition().getX() >> 4;
		int pcz = mc.player.blockPosition().getZ() >> 4;
		int radius = cfg.growthRadius;
		boolean moved = pcx != lastPcx || pcz != lastPcz || radius != lastRadius;
		if (moved || rebuildTimer-- <= 0) {
			rebuild(pcx, pcz, radius);
			rebuildTimer = 40;
		}
		if (announceTimer-- <= 0) {
			announceTimer = 20;
			announceBudget = 3;
		}

		boolean changed = false;
		for (int i = 0; i < cfg.growthChunksPerTick && !ORDER.isEmpty(); i++) {
			if (cursor >= ORDER.size()) cursor = 0;
			int[] c = ORDER.get(cursor++);
			long k = key(c[0], c[1]);
			LevelChunk chunk = level.getChunkSource().getChunkNow(c[0], c[1]);
			ChunkFlag now = chunk == null ? null : scan(level, chunk, c[0], c[1], cfg);
			ChunkFlag old = now == null ? FLAGS.remove(k) : FLAGS.put(k, now);
			if (now == null) {
				ANNOUNCED.remove(k);
			} else if (old == null) {
				announce(mc, now, k, cfg);
			}
			if (now == null ? old != null : !now.equals(old)) changed = true;
		}

		if (changed || moved || publishTimer-- <= 0) {
			publishTimer = 20;
			publish(mc);
		}
	}

	private static void rebuild(int pcx, int pcz, int radius) {
		ORDER.clear();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				ORDER.add(new int[] { pcx + dx, pcz + dz });
			}
		}
		ORDER.sort(Comparator.comparingInt(c -> {
			int dx = c[0] - pcx, dz = c[1] - pcz;
			return dx * dx + dz * dz;
		}));
		FLAGS.entrySet().removeIf(e -> {
			int cx = (int) (e.getKey() >> 32), cz = (int) (long) e.getKey();
			boolean out = Math.abs(cx - pcx) > radius || Math.abs(cz - pcz) > radius;
			if (out) ANNOUNCED.remove(e.getKey());
			return out;
		});
		lastPcx = pcx;
		lastPcz = pcz;
		lastRadius = radius;
	}

	private static void publish(Minecraft mc) {
		double px = mc.player.getX(), pz = mc.player.getZ();
		List<ChunkFlag> list = new ArrayList<>(FLAGS.values());
		list.sort(Comparator.comparingDouble(f -> {
			double dx = f.centerX() - px, dz = f.centerZ() - pz;
			return dx * dx + dz * dz;
		}));
		snapshot = List.copyOf(list);
	}

	private static void announce(Minecraft mc, ChunkFlag f, long k, AmethystConfig cfg) {
		ANNOUNCED.add(k);
		if (!cfg.notifyChat || announceBudget <= 0) return;
		announceBudget--;
		mc.player.sendSystemMessage(
			Component.literal("[AmethystClient] ").withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(Component.literal("Grown blocks near " + f.centerX() + ", " + f.centerZ() + ": " + f.summary())
					.withStyle(ChatFormatting.WHITE)));
	}

	// ------------------------------------------------------------------ scanning

	/** Cheap, context-free classification. Returns null when the state is irrelevant. */
	private static GrowthType classify(BlockState s) {
		Block b = s.getBlock();
		if (b instanceof GrowingPlantHeadBlock) { // kelp, weeping/twisting vines, cave vines
			return s.hasProperty(BlockStateProperties.AGE_25) && s.getValue(BlockStateProperties.AGE_25) >= 25
				? GrowthType.KELP_VINES : null;
		}
		if (b == Blocks.POINTED_DRIPSTONE) {
			return s.getValue(BlockStateProperties.DRIPSTONE_THICKNESS) == DripstoneThickness.TIP
				? GrowthType.DRIPSTONE : null;
		}
		if (b == Blocks.AMETHYST_CLUSTER) return GrowthType.AMETHYST;
		if (b instanceof CropBlock crop) return crop.isMaxAge(s) ? GrowthType.CROPS : null;
		if (b == Blocks.NETHER_WART) return s.getValue(BlockStateProperties.AGE_3) >= 3 ? GrowthType.CROPS : null;
		if (b == Blocks.SWEET_BERRY_BUSH) return s.getValue(BlockStateProperties.AGE_3) >= 3 ? GrowthType.CROPS : null;
		if (b == Blocks.COCOA) return s.getValue(BlockStateProperties.AGE_2) >= 2 ? GrowthType.CROPS : null;
		if (b instanceof StemBlock) return s.getValue(BlockStateProperties.AGE_7) >= 7 ? GrowthType.CROPS : null;
		return null;
	}

	private static ChunkFlag scan(ClientLevel level, LevelChunk chunk, int cx, int cz, AmethystConfig cfg) {
		GrowthType[] types = GrowthType.values();
		boolean[] on = new boolean[types.length];
		boolean any = false;
		for (GrowthType t : types) {
			on[t.ordinal()] = cfg.growthOn(t);
			any |= on[t.ordinal()];
		}
		if (!any) return null;

		int[] counts = new int[types.length];
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		int levelMinY = level.getMinY();
		LevelChunkSection[] sections = chunk.getSections();

		for (int si = 0; si < sections.length; si++) {
			LevelChunkSection section = sections[si];
			if (section == null || section.hasOnlyAir()) continue;
			if (!section.maybeHas(s -> classify(s) != null)) continue;
			int baseY = levelMinY + si * 16;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState s = section.getBlockState(x, y, z);
						GrowthType t = classify(s);
						if (t == null || !on[t.ordinal()]) continue;
						int wy = baseY + y;
						if (t == GrowthType.DRIPSTONE
							&& !dripstoneQualifies(level, new BlockPos(cx * 16 + x, wy, cz * 16 + z), s, cfg)) {
							continue;
						}
						counts[t.ordinal()]++;
						if (wy < minY) minY = wy;
						if (wy > maxY) maxY = wy;
					}
				}
			}
		}

		Map<GrowthType, Integer> result = new EnumMap<>(GrowthType.class);
		boolean flagged = false;
		for (GrowthType t : types) {
			int n = counts[t.ordinal()];
			if (n >= cfg.minCount(t)) {
				result.put(t, n);
				flagged = true;
			}
		}
		return flagged ? new ChunkFlag(cx, cz, minY, maxY, result) : null;
	}

	/** A dripstone tip counts when its column is long enough and (optionally) outside natural dripstone caves. */
	private static boolean dripstoneQualifies(ClientLevel level, BlockPos tip, BlockState state, AmethystConfig cfg) {
		if (cfg.dripstoneOutsideCavesOnly && level.getBiome(tip).is(Biomes.DRIPSTONE_CAVES)) return false;
		Direction tipDir = state.getValue(BlockStateProperties.VERTICAL_DIRECTION);
		Direction body = tipDir.getOpposite();
		BlockPos.MutableBlockPos m = tip.mutable();
		int length = 1;
		for (int i = 0; i < 16; i++) {
			m.move(body);
			if (level.getBlockState(m).is(Blocks.POINTED_DRIPSTONE)) length++;
			else break;
		}
		return length >= cfg.dripstoneMinLength;
	}
}
