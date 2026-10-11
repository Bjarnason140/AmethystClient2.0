package com.amethystclient;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amethystclient.config.AmethystConfig;
import com.amethystclient.gui.AmethystHud;
import com.amethystclient.gui.AmethystScreen;
import com.amethystclient.render.EspRenderer;
import com.amethystclient.scan.BlockEspScanner;
import com.amethystclient.scan.GrowthScanner;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class AmethystClient implements ClientModInitializer {
	public static final String MOD_ID = "amethystclient";
	public static final Logger LOGGER = LoggerFactory.getLogger("AmethystClient");

	public static KeyMapping OPEN_GUI;
	public static KeyMapping TOGGLE_ESP;
	public static KeyMapping TOGGLE_GROWTH;

	@Override
	public void onInitializeClient() {
		AmethystConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		OPEN_GUI = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.amethystclient.open_gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		TOGGLE_ESP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.amethystclient.toggle_esp", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
		TOGGLE_GROWTH = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.amethystclient.toggle_growth", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		ClientTickEvents.END_CLIENT_TICK.register(AmethystClient::tick);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(EspRenderer::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "flagged_chunks"), AmethystHud::render);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			AmethystConfig.INSTANCE.save();
			EspRenderer.close();
		});

		LOGGER.info("AmethystClient loaded");
	}

	private static void tick(Minecraft mc) {
		AmethystConfig cfg = AmethystConfig.INSTANCE;

		while (OPEN_GUI.consumeClick()) {
			if (mc.gui.screen() == null) mc.gui.setScreen(new AmethystScreen());
		}
		while (TOGGLE_ESP.consumeClick()) {
			cfg.espEnabled = !cfg.espEnabled;
			cfg.save();
			BlockEspScanner.refresh();
			say(mc, "Block ESP " + (cfg.espEnabled ? "enabled" : "disabled"));
		}
		while (TOGGLE_GROWTH.consumeClick()) {
			cfg.growthEnabled = !cfg.growthEnabled;
			cfg.save();
			say(mc, "Growth Finder " + (cfg.growthEnabled ? "enabled" : "disabled"));
		}

		BlockEspScanner.tick(mc);
		GrowthScanner.tick(mc);
	}

	private static void say(Minecraft mc, String msg) {
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[AmethystClient] " + msg));
	}
}
