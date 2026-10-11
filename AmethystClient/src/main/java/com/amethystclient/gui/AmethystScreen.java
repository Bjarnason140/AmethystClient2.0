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
