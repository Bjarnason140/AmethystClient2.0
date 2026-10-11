package com.amethystclient.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.amethystclient.AmethystClient;
import com.amethystclient.config.AmethystConfig;
import com.amethystclient.esp.EspType;
import com.amethystclient.esp.GrowthType;
import com.amethystclient.scan.BlockEspScanner;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * AmethystClient menu: sidebar tabs, scrolling settings list, and a color picker panel.
 * Immediate-mode: every frame rebuilds the list of clickable regions while drawing.
 */
public final class AmethystScreen extends Screen {
	private enum Tab {
		ESP("Block ESP"), GROWTH("Growth Finder"), VISUALS("Visuals");
		final String label;
		Tab(String label) { this.label = label; }
	}

	private interface Click { void on(double mx, double my); }
	private interface Drag extends Click {}

	private record Region(int x, int y, int w, int h, boolean clipped, Click click, Drag drag) {
		boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
	}

	private static final int ROW_H = 20;
	private static final int[] PRESETS = {
		0xB388FF, 0x9D4EDD, 0xC77DFF, 0xE0AAFF, 0x7B2CBF, 0x5A189A,
		0xFF5C8A, 0xFF6B6B, 0xFFD166, 0x06D6A0, 0x4CC9F0, 0xFFFFFF
	};

	private final List<Region> regions = new ArrayList<>();
	private Drag activeDrag;
	private Tab tab = Tab.ESP;
	private int scroll, contentHeight;
	private int mouseX, mouseY;

	// layout (recomputed every frame)
	private int wx, wy, ww, wh;
	private int cx, cw, cy, clipTop, clipBottom;

	// color picker state
	private String pickLabel = "";
	private IntSupplier pickGet = () -> 0xFFFFFF;
	private IntConsumer pickSet = v -> {};
	private float hue, sat, val;

	public AmethystScreen() {
		super(Component.literal("AmethystClient"));
		selectColor("Chests", () -> AmethystConfig.INSTANCE.colorOf(EspType.CHEST),
			v -> AmethystConfig.INSTANCE.setColor(EspType.CHEST, v));
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void removed() {
		AmethystConfig.INSTANCE.save();
		BlockEspScanner.refresh();
		super.removed();
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			double mx = event.x(), my = event.y();
			for (int i = regions.size() - 1; i >= 0; i--) {
				Region r = regions.get(i);
				if (!r.contains(mx, my)) continue;
				if (r.clipped() && (my < clipTop || my >= clipBottom)) continue;
				if (r.click() != null) r.click().on(mx, my);
				activeDrag = r.drag();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (activeDrag != null) {
			activeDrag.on(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (activeDrag != null) {
			activeDrag = null;
			AmethystConfig.INSTANCE.save();
			BlockEspScanner.refresh();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		scroll -= (int) (scrollY * 14);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (AmethystClient.OPEN_GUI.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
		super.extractRenderState(g, mx, my, partial);
		mouseX = mx;
		mouseY = my;
		regions.clear();

		ww = Math.min(500, width - 10);
		wh = Math.min(320, height - 10);
		wx = (width - ww) / 2;
		wy = (height - wh) / 2;
		int side = 96, pad = 8;
		int pick = ww >= 440 ? 138 : 0;

		// window
		Theme.rrect(g, wx - 1, wy - 1, ww + 2, wh + 2, Theme.BORDER);
		Theme.rrect(g, wx, wy, ww, wh, Theme.WINDOW);
		Theme.hGradient(g, wx + 1, wy, ww - 2, 2, Theme.ACCENT, Theme.ACCENT_LIGHT);

		drawSidebar(g, side);

		cx = wx + side + pad;
		cw = wx + ww - pad - (pick > 0 ? pick + pad : 0) - cx;
		clipTop = wy + 30;
		clipBottom = wy + wh - 8;

		int maxScroll = Math.max(0, contentHeight - (clipBottom - clipTop));
		scroll = Mth.clamp(scroll, 0, maxScroll);
		cy = clipTop - scroll;
		buildContent(g);
		contentHeight = cy - (clipTop - scroll);

		// mask rows that overflow the viewport, then draw the title on top
		g.fill(cx - 1, wy + 3, cx + cw + 5, clipTop, Theme.WINDOW);
		g.fill(cx - 1, clipBottom, cx + cw + 5, wy + wh - 3, Theme.WINDOW);
		g.text(font, tab.label, cx, wy + 11, Theme.TEXT, false);
		g.fill(cx, wy + 22, cx + 26, wy + 24, Theme.ACCENT);

		if (maxScroll > 0) { // scrollbar
			int trackH = clipBottom - clipTop;
			int barH = Math.max(16, trackH * trackH / contentHeight);
			int barY = clipTop + (int) ((trackH - barH) * (scroll / (float) maxScroll));
			g.fill(cx + cw + 2, clipTop, cx + cw + 4, clipBottom, Theme.OFF);
			g.fill(cx + cw + 2, barY, cx + cw + 4, barY + barH, Theme.ACCENT_LIGHT);
		}

		if (pick > 0) drawPicker(g, wx + ww - pad - pick, wy + 8, pick, wh - 16);
	}

	private void drawSidebar(GuiGraphicsExtractor g, int side) {
		Theme.rrect(g, wx + 6, wy + 6, side - 6, wh - 12, Theme.SIDEBAR);
		Theme.gem(g, wx + 12, wy + 14);
		Theme.gradientText(g, font, "AMETHYST", wx + 12, wy + 32, Theme.ACCENT_LIGHT, 0xFFE8D8FF);
		g.text(font, "CLIENT", wx + 12, wy + 42, Theme.DIM, false);

		int ty = wy + 62;
		for (Tab t : Tab.values()) {
			int x = wx + 10, w = side - 14, h = 22;
			boolean sel = t == tab;
			boolean hov = mouseX >= x && mouseX < x + w && mouseY >= ty && mouseY < ty + h;
			Theme.rrect(g, x, ty, w, h, sel ? Theme.ACCENT : hov ? Theme.ROW_HOVER : Theme.SIDEBAR);
			if (sel) g.fill(x, ty + 3, x + 2, ty + h - 3, Theme.ACCENT_LIGHT);
			g.text(font, t.label, x + 9, ty + (h - font.lineHeight) / 2 + 1, sel ? 0xFFFFFFFF : Theme.TEXT, false);
			final Tab target = t;
			regions.add(new Region(x, ty, w, h, false, (a, b) -> { tab = target; scroll = 0; }, null));
			ty += h + 4;
		}
		g.text(font, "RShift to close", wx + 12, wy + wh - 20, Theme.DIM, false);
	}

	// ------------------------------------------------------------------ tab content

	private void buildContent(GuiGraphicsExtractor g) {
		AmethystConfig c = AmethystConfig.INSTANCE;
		switch (tab) {
			case ESP -> {
				toggleRow(g, "Block ESP", () -> c.espEnabled, v -> c.espEnabled = v);
				header(g, "TARGETS  -  click a swatch to recolor");
				for (EspType t : EspType.values()) typeRow(g, t);
			}
			case GROWTH -> {
				toggleRow(g, "Growth Finder", () -> c.growthEnabled, v -> c.growthEnabled = v);
				toggleRow(g, "Chat notifications", () -> c.notifyChat, v -> c.notifyChat = v);
				header(g, "DETECT  -  min blocks per chunk");
				for (GrowthType t : GrowthType.values()) growthRow(g, t);
				header(g, "TUNING");
				sliderRow(g, "Scan radius", 2, 16, 1, () -> c.growthRadius, v -> c.growthRadius = (int) v, "%.0f chunks");
				sliderRow(g, "Scan speed", 1, 8, 1, () -> c.growthChunksPerTick, v -> c.growthChunksPerTick = (int) v, "%.0f / tick");
				toggleRow(g, "Dripstone: outside caves only", () -> c.dripstoneOutsideCavesOnly, v -> c.dripstoneOutsideCavesOnly = v);
				sliderRow(g, "Dripstone min length", 2, 8, 1, () -> c.dripstoneMinLength, v -> c.dripstoneMinLength = (int) v, "%.0f");
				header(g, "FLAG STYLE");
				colorRow(g, "Flagged chunk outline", () -> c.chunkColor, v -> c.chunkColor = v & 0xFFFFFF);
				toggleRow(g, "Full-height outline", () -> c.chunkFullHeight, v -> c.chunkFullHeight = v);
				sliderRow(g, "Chunk outline thickness", 0.04, 0.3, 0.01, () -> c.chunkThickness, v -> c.chunkThickness = (float) v, "%.2f");
				toggleRow(g, "Flagged-chunk list on HUD", () -> c.hudList, v -> c.hudList = v);
			}
			case VISUALS -> {
				header(g, "OUTLINES");
				sliderRow(g, "Outline thickness", 0.01, 0.12, 0.005, () -> c.outlineThickness, v -> c.outlineThickness = (float) v, "%.3f");
				sliderRow(g, "Outline opacity", 0.2, 1.0, 0.05, () -> c.outlineAlpha, v -> c.outlineAlpha = (float) v, "%.2f");
				sliderRow(g, "Fill opacity", 0.0, 0.5, 0.02, () -> c.fillAlpha, v -> c.fillAlpha = (float) v, "%.2f");
				header(g, "PERFORMANCE");
				sliderRow(g, "ESP range", 16, 256, 16, () -> c.espRange, v -> c.espRange = (int) v, "%.0f blocks");
				sliderRow(g, "Max outlined blocks", 50, 800, 50, () -> c.maxBoxes, v -> c.maxBoxes = (int) v, "%.0f");
			}
		}
	}

	private boolean visible(int h) { return cy + h > clipTop && cy < clipBottom; }

	private boolean hoverRow(int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && mouseY >= clipTop && mouseY < clipBottom;
	}

	private void header(GuiGraphicsExtractor g, String text) {
		int h = 18;
		if (visible(h)) g.text(font, text, cx + 2, cy + 8, Theme.ACCENT_LIGHT, false);
		cy += h;
	}

	private void switchWidget(GuiGraphicsExtractor g, int x, int y, boolean on) {
		Theme.rrect(g, x, y, 22, 10, on ? Theme.ACCENT : Theme.OFF);
		g.fill(on ? x + 13 : x + 1, y + 1, on ? x + 21 : x + 9, y + 9, on ? 0xFFFFFFFF : 0xFFB6A6DD);
	}

	private void toggleRow(GuiGraphicsExtractor g, String label, BooleanSupplier get, Consumer<Boolean> set) {
		if (visible(ROW_H)) {
			int h = ROW_H - 2;
			Theme.rrect(g, cx, cy, cw, h, hoverRow(cx, cy, cw, h) ? Theme.ROW_HOVER : Theme.ROW);
			g.text(font, label, cx + 8, cy + (h - font.lineHeight) / 2 + 1, Theme.TEXT, false);
			switchWidget(g, cx + cw - 30, cy + (h - 10) / 2, get.getAsBoolean());
			regions.add(new Region(cx, cy, cw, h, true, (a, b) -> set.accept(!get.getAsBoolean()), null));
		}
		cy += ROW_H;
	}

	private void swatch(GuiGraphicsExtractor g, int x, int y, int rgb, boolean selected) {
		Theme.rrect(g, x - 1, y - 1, 18, 12, selected ? Theme.ACCENT_LIGHT : Theme.BORDER);
		g.fill(x, y, x + 16, y + 10, 0xFF000000 | rgb);
	}

	private void typeRow(GuiGraphicsExtractor g, EspType t) {
		AmethystConfig c = AmethystConfig.INSTANCE;
		if (visible(ROW_H)) {
			int h = ROW_H - 2;
			Theme.rrect(g, cx, cy, cw, h, hoverRow(cx, cy, cw, h) ? Theme.ROW_HOVER : Theme.ROW);
			switchWidget(g, cx + 8, cy + (h - 10) / 2, c.espOn(t));
			g.text(font, t.label, cx + 38, cy + (h - font.lineHeight) / 2 + 1, c.espOn(t) ? Theme.TEXT : Theme.DIM, false);
			regions.add(new Region(cx, cy, cw, h, true, (a, b) -> { c.setEspOn(t, !c.espOn(t)); BlockEspScanner.refresh(); }, null));

			int sx = cx + cw - 26, sy = cy + (h - 10) / 2;
			boolean sel = pickLabel.equals(t.label);
			swatch(g, sx, sy, c.colorOf(t), sel);
			regions.add(new Region(sx - 2, cy, 22, h, true, (a, b) -> selectColor(t.label, () -> c.colorOf(t), v -> c.setColor(t, v)), null));
		}
		cy += ROW_H;
	}

	private void colorRow(GuiGraphicsExtractor g, String label, IntSupplier get, IntConsumer set) {
		if (visible(ROW_H)) {
			int h = ROW_H - 2;
			Theme.rrect(g, cx, cy, cw, h, hoverRow(cx, cy, cw, h) ? Theme.ROW_HOVER : Theme.ROW);
			g.text(font, label, cx + 8, cy + (h - font.lineHeight) / 2 + 1, Theme.TEXT, false);
			int sx = cx + cw - 26, sy = cy + (h - 10) / 2;
			swatch(g, sx, sy, get.getAsInt(), pickLabel.equals(label));
			regions.add(new Region(cx, cy, cw, h, true, (a, b) -> selectColor(label, get, set), null));
		}
		cy += ROW_H;
	}

	private void growthRow(GuiGraphicsExtractor g, GrowthType t) {
		AmethystConfig c = AmethystConfig.INSTANCE;
		if (visible(ROW_H)) {
			int h = ROW_H - 2;
			Theme.rrect(g, cx, cy, cw, h, hoverRow(cx, cy, cw, h) ? Theme.ROW_HOVER : Theme.ROW);
			switchWidget(g, cx + 8, cy + (h - 10) / 2, c.growthOn(t));
			g.text(font, t.label, cx + 38, cy + (h - font.lineHeight) / 2 + 1, c.growthOn(t) ? Theme.TEXT : Theme.DIM, false);
			regions.add(new Region(cx, cy, cw, h, true, (a, b) -> c.setGrowthOn(t, !c.growthOn(t)), null));

			// [-] n [+]
			int bx = cx + cw - 64, by = cy + 3, bs = 12;
			String n = String.valueOf(c.minCount(t));
			stepButton(g, bx, by, bs, "-");
			g.text(font, n, bx + bs + 3 + (20 - font.width(n)) / 2, cy + (h - font.lineHeight) / 2 + 1, Theme.TEXT, false);
			stepButton(g, bx + bs + 26, by, bs, "+");
			regions.add(new Region(bx, by, bs, bs, true, (a, b) -> c.setMinCount(t, c.minCount(t) - (step())), null));
			regions.add(new Region(bx + bs + 26, by, bs, bs, true, (a, b) -> c.setMinCount(t, c.minCount(t) + (step())), null));
		}
		cy += ROW_H;
	}

	private int step() { return 1; }

	private void stepButton(GuiGraphicsExtractor g, int x, int y, int s, String label) {
		boolean hov = mouseX >= x && mouseX < x + s && mouseY >= y && mouseY < y + s;
		Theme.rrect(g, x, y, s, s, hov ? Theme.ACCENT : Theme.OFF);
		g.text(font, label, x + (s - font.width(label)) / 2 + 1, y + 2, 0xFFFFFFFF, false);
	}

	private void sliderRow(GuiGraphicsExtractor g, String label, double min, double max, double step,
			DoubleSupplier get, DoubleConsumer set, String fmt) {
		int h = ROW_H + 8;
		if (visible(h)) {
			int rh = h - 2;
			Theme.rrect(g, cx, cy, cw, rh, Theme.ROW);
			double v = get.getAsDouble();
			g.text(font, label, cx + 8, cy + 5, Theme.TEXT, false);
			String vs = String.format(Locale.ROOT, fmt, v);
			g.text(font, vs, cx + cw - 8 - font.width(vs), cy + 5, Theme.ACCENT_LIGHT, false);

			int tx = cx + 8, ty = cy + rh - 9, tw = cw - 16;
			float frac = (float) Mth.clamp((v - min) / (max - min), 0.0, 1.0);
			Theme.rrect(g, tx, ty, tw, 4, Theme.OFF);
			Theme.rrect(g, tx, ty, Math.max(2, (int) (tw * frac)), 4, Theme.ACCENT);
			int kx = tx + (int) (tw * frac);
			g.fill(kx - 2, ty - 2, kx + 2, ty + 6, 0xFFFFFFFF);

			Drag drag = (mx, my) -> {
				double t = Mth.clamp((mx - tx) / tw, 0.0, 1.0);
				double nv = min + t * (max - min);
				if (step > 0) nv = Math.round(nv / step) * step;
				set.accept(Mth.clamp(nv, min, max));
			};
			regions.add(new Region(tx - 4, ty - 7, tw + 8, 18, true, drag, drag));
		}
		cy += h;
	}

	// ------------------------------------------------------------------ color picker

	private void selectColor(String label, IntSupplier get, IntConsumer set) {
		pickLabel = label;
		pickGet = get;
		pickSet = set;
		float[] hsv = toHsv(get.getAsInt());
		hue = hsv[0];
		sat = hsv[1];
		val = hsv[2];
	}

	private void applyPick() {
		pickSet.accept(fromHsv(hue, sat, val));
	}

	private void drawPicker(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		Theme.rrect(g, x, y, w, h, Theme.PANEL);
		int ix = x + 8, iw = w - 16, iy = y + 8;
		g.text(font, "COLOR", ix, iy, Theme.ACCENT_LIGHT, false);
		g.text(font, pickLabel, ix, iy + 11, Theme.TEXT, false);

		int rgb = pickGet.getAsInt() & 0xFFFFFF;
		Theme.rrect(g, ix - 1, iy + 24, iw + 2, 26, Theme.BORDER);
		g.fill(ix, iy + 25, ix + iw, iy + 49, 0xFF000000 | rgb);
		String hex = String.format("#%06X", rgb);
		g.text(font, hex, ix, iy + 54, Theme.DIM, false);

		int sy = iy + 70;
		pickSlider(g, "Hue", ix, sy, iw, hue, t -> fromHsv(t, 1f, 1f), t -> { hue = t; applyPick(); });
		pickSlider(g, "Saturation", ix, sy + 28, iw, sat, t -> fromHsv(hue, t, Math.max(val, 0.35f)), t -> { sat = t; applyPick(); });
		pickSlider(g, "Brightness", ix, sy + 56, iw, val, t -> fromHsv(hue, sat, t), t -> { val = t; applyPick(); });

		int py = sy + 88;
		g.text(font, "Presets", ix, py, Theme.DIM, false);
		int sw = 16, gap = 5, perRow = Math.max(1, (iw + gap) / (sw + gap));
		for (int i = 0; i < PRESETS.length; i++) {
			int px = ix + (i % perRow) * (sw + gap);
			int pyy = py + 12 + (i / perRow) * 16;
			boolean hov = mouseX >= px && mouseX < px + sw && mouseY >= pyy && mouseY < pyy + 12;
			Theme.rrect(g, px - 1, pyy - 1, sw + 2, 14, hov ? Theme.ACCENT_LIGHT : Theme.BORDER);
			g.fill(px, pyy, px + sw, pyy + 12, 0xFF000000 | PRESETS[i]);
			final int preset = PRESETS[i];
			regions.add(new Region(px, pyy, sw, 12, false, (a, b) -> {
				pickSet.accept(preset);
				float[] hsv = toHsv(preset);
				hue = hsv[0];
				sat = hsv[1];
				val = hsv[2];
				AmethystConfig.INSTANCE.save();
				BlockEspScanner.refresh();
			}, null));
		}
	}

	private interface ColorAt { int at(float t); }
	private interface FloatSet { void set(float t); }

	private void pickSlider(GuiGraphicsExtractor g, String label, int x, int y, int w, float value, ColorAt colorAt, FloatSet set) {
		g.text(font, label, x, y, Theme.DIM, false);
		int ty = y + 11, th = 8;
		for (int i = 0; i < w; i += 2) {
			g.fill(x + i, ty, Math.min(x + i + 2, x + w), ty + th, 0xFF000000 | colorAt.at(i / (float) Math.max(1, w - 1)));
		}
		int kx = x + (int) (w * value);
		g.fill(kx - 2, ty - 2, kx + 2, ty + th + 2, 0xFF000000);
		g.fill(kx - 1, ty - 1, kx + 1, ty + th + 1, 0xFFFFFFFF);
		Drag drag = (mx, my) -> set.set((float) Mth.clamp((mx - x) / w, 0.0, 1.0));
		regions.add(new Region(x - 3, ty - 4, w + 6, th + 8, false, drag, drag));
	}

	// ------------------------------------------------------------------ HSV helpers

	private static int fromHsv(float h, float s, float v) {
		return Mth.hsvToRgb(h, s, v) & 0xFFFFFF;
	}

	private static float[] toHsv(int rgb) {
		float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h;
		if (d == 0f) h = 0f;
		else if (max == r) h = ((g - b) / d % 6f) / 6f;
		else if (max == g) h = ((b - r) / d + 2f) / 6f;
		else h = ((r - g) / d + 4f) / 6f;
		if (h < 0f) h += 1f;
		return new float[] { h, max == 0f ? 0f : d / max, max };
	}
}
