package com.amethystclient.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Deep-purple palette + tiny drawing helpers shared by the menu and the HUD. All colors are 0xAARRGGBB. */
public final class Theme {
	public static final int WINDOW = 0xFF140A26;       // deepest purple
	public static final int SIDEBAR = 0xFF1B0E33;
	public static final int PANEL = 0xFF1E1038;
	public static final int ROW = 0xFF2A1750;
	public static final int ROW_HOVER = 0xFF37206A;
	public static final int BORDER = 0xFF4B3485;
	public static final int ACCENT = 0xFF7C4DFF;        // lighter deep purple
	public static final int ACCENT_LIGHT = 0xFFB794FF;  // light violet
	public static final int TEXT = 0xFFF1E9FF;
	public static final int DIM = 0xFFA796CC;
	public static final int OFF = 0xFF3B2A63;

	private Theme() {}

	public static int lerp(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
		int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
		return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	/** Rectangle with 1px-clipped corners (cheap rounded look). */
	public static void rrect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		if (w < 3 || h < 3) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x + 1, y, x + w - 1, y + h, color);
		g.fill(x, y + 1, x + w, y + h - 1, color);
	}

	public static void hGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int left, int right) {
		for (int i = 0; i < w; i += 2) {
			g.fill(x + i, y, Math.min(x + i + 2, x + w), y + h, lerp(left, right, i / (float) Math.max(1, w - 1)));
		}
	}

	/** Text with a per-letter purple gradient. */
	public static void gradientText(GuiGraphicsExtractor g, Font font, String s, int x, int y, int from, int to) {
		int px = x;
		for (int i = 0; i < s.length(); i++) {
			String c = String.valueOf(s.charAt(i));
			g.text(font, c, px, y, lerp(from, to, i / (float) Math.max(1, s.length() - 1)), false);
			px += font.width(c);
		}
	}

	/** Little amethyst gem, 16x14. */
	public static void gem(GuiGraphicsExtractor g, int x, int y) {
		for (int r = 0; r < 14; r++) {
			int w = r <= 5 ? 4 + 2 * r : Math.max(2, 14 - (r - 5) * 2);
			g.fill(x + 8 - w / 2, y + r, x + 8 + w / 2, y + r + 1, lerp(ACCENT_LIGHT, ACCENT, r / 13f));
		}
		g.fill(x + 5, y + 4, x + 7, y + 6, 0x66FFFFFF); // facet highlight
	}
}
