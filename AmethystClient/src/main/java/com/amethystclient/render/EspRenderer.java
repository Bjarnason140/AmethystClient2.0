package com.amethystclient.render;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.amethystclient.AmethystClient;
import com.amethystclient.config.AmethystConfig;
import com.amethystclient.scan.BlockEspScanner;
import com.amethystclient.scan.GrowthScanner;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Draws ESP outlines through walls. Based on the official Fabric 26.2 "custom render pipeline" example:
 * a copy of the debug-filled-box pipeline with the depth test removed. Outlines are built from thin
 * quad-boxes (12 edges per outline), so only the QUADS pipeline is needed.
 */
public final class EspRenderer {
	private static final RenderPipeline PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(AmethystClient.MOD_ID, "pipeline/esp_through_walls"))
			.withDepthStencilState(Optional.empty())
			.build());

	private static final int BUFFER_BYTES = 4 * 1024 * 1024;
	private static final int BYTES_PER_BOX = 24 * 16; // 24 vertices * (position 12 + color 4)
	private static final StagedVertexBuffer STAGED = new StagedVertexBuffer(() -> "AmethystClient ESP", BUFFER_BYTES);

	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	private static int boxesLeft;

	private EspRenderer() {}

	public static void render(LevelRenderContext context) {
		AmethystConfig cfg = AmethystConfig.INSTANCE;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;

		List<BlockEspScanner.Box> esp = cfg.espEnabled ? BlockEspScanner.boxes() : List.of();
		List<GrowthScanner.ChunkFlag> flags = cfg.growthEnabled ? GrowthScanner.flags() : List.of();
		if (esp.isEmpty() && flags.isEmpty()) return;

		RenderPipeline pipeline = PIPELINE;
		VertexFormat format = pipeline.getVertexFormatBinding(0);
		assert format != null;
		PrimitiveTopology topology = pipeline.getPrimitiveTopology();

		StagedVertexBuffer.Draw draw = STAGED.appendDraw(format, topology,
			topology == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);
		VertexConsumer vb = STAGED.getVertexBuilder(draw);

		Vec3 cam = context.levelState().cameraRenderState.pos;
		Matrix4fc pose = context.poseStack().last().pose();
		boxesLeft = BUFFER_BYTES / BYTES_PER_BOX - 16;

		// --- Block ESP ---
		double rangeSq = (double) cfg.espRange * cfg.espRange;
		for (BlockEspScanner.Box b : esp) {
			if (boxesLeft < 14) break;
			double cx = (b.minX() + b.maxX()) * 0.5, cy = (b.minY() + b.maxY()) * 0.5, cz = (b.minZ() + b.maxZ()) * 0.5;
			double dx = cx - cam.x, dy = cy - cam.y, dz = cz - cam.z;
			double distSq = dx * dx + dy * dy + dz * dz;
			if (distSq > rangeSq * 1.2) continue;
			float dist = (float) Math.sqrt(distSq);
			float r = ((b.rgb() >> 16) & 255) / 255f, g = ((b.rgb() >> 8) & 255) / 255f, bl = (b.rgb() & 255) / 255f;
			float t = cfg.outlineThickness + dist * 0.0025f; // keeps outlines visible at range
			float x1 = (float) (b.minX() - cam.x), y1 = (float) (b.minY() - cam.y), z1 = (float) (b.minZ() - cam.z);
			float x2 = (float) (b.maxX() - cam.x), y2 = (float) (b.maxY() - cam.y), z2 = (float) (b.maxZ() - cam.z);
			if (cfg.fillAlpha > 0f) {
				box(vb, pose, x1, y1, z1, x2, y2, z2, r, g, bl, cfg.fillAlpha);
			}
			outline(vb, pose, x1, y1, z1, x2, y2, z2, t, r, g, bl, cfg.outlineAlpha);
		}

		// --- Flagged chunks (growth finder) ---
		int cc = cfg.chunkColor;
		float cr = ((cc >> 16) & 255) / 255f, cg = ((cc >> 8) & 255) / 255f, cb = (cc & 255) / 255f;
		int levelMinY = mc.level.getMinY();
		int levelMaxY = levelMinY + mc.level.getHeight();
		for (GrowthScanner.ChunkFlag f : flags) {
			if (boxesLeft < 14) break;
			double x0 = f.chunkX() * 16.0, z0 = f.chunkZ() * 16.0;
			double yLo = cfg.chunkFullHeight ? levelMinY : f.minY();
			double yHi = cfg.chunkFullHeight ? levelMaxY : Math.max(f.maxY() + 1, f.minY() + 4);
			double dx = x0 + 8 - cam.x, dz = z0 + 8 - cam.z;
			float dist = (float) Math.sqrt(dx * dx + dz * dz);
			float t = cfg.chunkThickness + dist * 0.002f;
			outline(vb, pose,
				(float) (x0 - cam.x), (float) (yLo - cam.y), (float) (z0 - cam.z),
				(float) (x0 + 16 - cam.x), (float) (yHi - cam.y), (float) (z0 + 16 - cam.z),
				t, cr, cg, cb, 0.95f);
		}

		STAGED.upload();
		StagedVertexBuffer.ExecuteInfo info = STAGED.getExecuteInfo(draw);
		if (info != null) {
			draw(mc, info, pipeline);
		}
		STAGED.endFrame();
	}

	/** 12 thin boxes forming the edges of the given cuboid (coordinates already camera-relative). */
	private static void outline(VertexConsumer vb, Matrix4fc m,
			float x1, float y1, float z1, float x2, float y2, float z2,
			float t, float r, float g, float b, float a) {
		float h = t * 0.5f;
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				float y = i == 0 ? y1 : y2;
				float z = j == 0 ? z1 : z2;
				box(vb, m, x1 - h, y - h, z - h, x2 + h, y + h, z + h, r, g, b, a); // edges along X
				float x = i == 0 ? x1 : x2;
				box(vb, m, x - h, y1 - h, z - h, x + h, y2 + h, z + h, r, g, b, a); // edges along Y
				float y3 = j == 0 ? y1 : y2;
				box(vb, m, x - h, y3 - h, z1 - h, x + h, y3 + h, z2 + h, r, g, b, a); // edges along Z
			}
		}
	}

	private static void box(VertexConsumer b, Matrix4fc m,
			float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
			float r, float g, float bl, float a) {
		boxesLeft--;
		// Front (+Z)
		b.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
		// Back (-Z)
		b.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
		// Left (-X)
		b.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
		// Right (+X)
		b.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
		// Top (+Y)
		b.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
		// Bottom (-Y)
		b.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
	}

	private static void draw(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
			.writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
		GpuTextureView colorTexture = mainTarget.getColorTextureView();
		assert colorTexture != null;

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> AmethystClient.MOD_ID + " esp", colorTexture, Optional.empty(),
			mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", dynamicTransforms);
			pass.setVertexBuffer(0, info.vertexBuffer().slice());
			pass.setIndexBuffer(info.indexBuffer(), info.indexType());
			pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
		}
	}

	public static void close() {
		STAGED.close();
	}
}
