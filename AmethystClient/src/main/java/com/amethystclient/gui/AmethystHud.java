package com.amethystclient.gui;

import java.util.List;

import com.amethystclient.config.AmethystConfig;
import com.amethystclient.scan.GrowthScanner;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small flagged-chunk list in the top-left corner. */
public final class AmethystHud {
	private AmethystHud() {}

	public static void render(GuiGraphicsExtractor g, DeltaTracker tickCounter) {
		AmethystConfig cfg = AmethystConfig.INSTANCE;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !cfg.growthEnabled || !cfg.hudList) return;
		List<GrowthScanner.ChunkFlag> flags = GrowthScanner.flags();
		if (flags.isEmpty()) return;

		Font font = mc.font;
		int shown = Math.min(flags.size(), 6);
		String[] l1 = new String[shown];
		String[] l2 = new String[shown];
		int width = font.width("AMETHYST  flagged chunks (" + flags.size() + ")");
		for (int i = 0; i < shown; i++) {
			GrowthScanner.ChunkFlag f = flags.get(i);
			double dx = f.centerX() - mc.player.getX(), dz = f.centerZ() - mc.player.getZ();
			l1[i] = f.centerX() + ", " + f.centerZ() + "   " + (int) Math.sqrt(dx * dx + dz * dz) + "m";
			l2[i] = f.summary();
			width = Math.max(width, Math.max(font.width(l1[i]), font.width(l2[i])));
		}

		int x = 6, y = 6, pad = 6;
		int h = pad * 2 + 12 + shown * 22;
		Theme.rrect(g, x - 1, y - 1, width + pad * 2 + 2, h + 2, Theme.BORDER);
		Theme.rrect(g, x, y, width + pad * 2, h, 0xE6140A26);
		Theme.hGradient(g, x + 1, y, width + pad * 2 - 2, 2, Theme.ACCENT, Theme.ACCENT_LIGHT);
		Theme.gradientText(g, font, "AMETHYST", x + pad, y + pad, Theme.ACCENT_LIGHT, 0xFFE8D8FF);
		g.text(font, "  flagged chunks (" + flags.size() + ")", x + pad + font.width("AMETHYST"), y + pad, Theme.DIM, false);
		for (int i = 0; i < shown; i++) {
			int ly = y + pad + 14 + i * 22;
			g.text(font, l1[i], x + pad, ly, Theme.TEXT, false);
			g.text(font, l2[i], x + pad, ly + 10, Theme.DIM, false);
		}
	}
}
