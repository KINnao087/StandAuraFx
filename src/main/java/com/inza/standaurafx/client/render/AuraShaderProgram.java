package com.inza.standaurafx.client.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import com.inza.standaurafx.StandAuraFx;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.IResource;
import net.minecraft.util.ResourceLocation;

public final class AuraShaderProgram {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ResourceLocation VERTEX_SHADER = new ResourceLocation(StandAuraFx.MOD_ID, "shaders/aura_billboard.vsh");
    private static final ResourceLocation FRAGMENT_SHADER = new ResourceLocation(StandAuraFx.MOD_ID, "shaders/aura_billboard.fsh");

    private static final float DEFAULT_SHAPE_SCALE_X = 0.42F;
    private static final float DEFAULT_SHAPE_SCALE_Y = 0.68F;
    private static final float DEFAULT_SHAPE_OFFSET_X = 0.0F;
    private static final float DEFAULT_SHAPE_OFFSET_Y = 0.05F;
    private static final float DEFAULT_BASE_AURA_WIDTH = 0.050F;
    private static final float DEFAULT_AURA_WIDTH_CHAOS = 0.028F;
    private static final float DEFAULT_EDGE_WARP_STRENGTH = 0.010F;
    private static final float DEFAULT_NOISE_SCALE = 10.0F;
    private static final float DEFAULT_FILL_ALPHA_BASE = 0.16F;
    private static final float DEFAULT_FILL_ALPHA_FLOW = 0.20F;
    private static final float DEFAULT_CORE_ALPHA = 0.16F;
    private static final float DEFAULT_EDGE_ALPHA_BASE = 0.80F;
    private static final float DEFAULT_EDGE_ALPHA_FLOW = 0.25F;
    private static final float DEFAULT_RIM_ALPHA = 0.42F;
    private static final float DEFAULT_INNER_HIGHLIGHT_BASE = 0.35F;
    private static final float DEFAULT_INNER_HIGHLIGHT_FLOW = 0.20F;
    private static final float DEFAULT_OUTER_HIGHLIGHT_BASE = 0.70F;
    private static final float DEFAULT_OUTER_HIGHLIGHT_FLOW = 0.25F;
    private static final float DEFAULT_EDGE_HIGHLIGHT_STRENGTH = 0.55F;

    private static final float[] SILVER = {0.95F, 0.97F, 1.00F};
    private static final float[] PALE_BLUE = {0.74F, 0.82F, 0.97F};

    private static int programId = -1;

    private static int uMaskTex;
    private static int uTexelSize;
    private static int uMaskUvMin;
    private static int uMaskUvMax;
    private static int uTime;
    private static int uChaos;
    private static int uGlobalAlpha;
    private static int uShapeScale;
    private static int uShapeOffset;
    private static int uAspect;
    private static int uAntiAlias;
    private static int uBaseAuraWidth;
    private static int uAuraWidthChaos;
    private static int uEdgeWarpStrength;
    private static int uNoiseScale;
    private static int uFillAlphaBase;
    private static int uFillAlphaFlow;
    private static int uCoreAlpha;
    private static int uEdgeAlphaBase;
    private static int uEdgeAlphaFlow;
    private static int uRimAlpha;
    private static int uInnerColorA;
    private static int uInnerColorB;
    private static int uOuterColorA;
    private static int uOuterColorB;
    private static int uEdgeColor;
    private static int uRimColor;
    private static int uInnerHighlightBase;
    private static int uInnerHighlightFlow;
    private static int uOuterHighlightBase;
    private static int uOuterHighlightFlow;
    private static int uEdgeHighlightStrength;

    private AuraShaderProgram() {
    }

    public static void use(
        int maskTextureId,
        int framebufferWidth,
        int framebufferHeight,
        int maskTextureWidth,
        int maskTextureHeight,
        int standColor,
        float time,
        float chaos,
        float globalAlpha
    ) {
        ensureProgram();

        float rectWidth = Math.max(framebufferWidth, 1.0F);
        float rectHeight = Math.max(framebufferHeight, 1.0F);
        float antiAlias = Math.max(2.0F / rectWidth, 2.0F / rectHeight);
        float aspect = rectWidth / rectHeight;
        float[] baseColor = liftedBaseColor(standColor);
        float[] innerA = mix(baseColor, PALE_BLUE, 0.16F);
        float[] innerB = mix(baseColor, SILVER, 0.28F);
        float[] outerA = mix(baseColor, PALE_BLUE, 0.34F);
        float[] outerB = mix(baseColor, SILVER, 0.48F);
        float[] edge = mix(baseColor, SILVER, 0.62F);

        GL20.glUseProgram(programId);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTextureId);

        GL20.glUniform1i(uMaskTex, 0);
        GL20.glUniform2f(uTexelSize, 1.0F / Math.max(maskTextureWidth, 1), 1.0F / Math.max(maskTextureHeight, 1));
        GL20.glUniform2f(uMaskUvMin, 0.0F, 0.0F);
        GL20.glUniform2f(
            uMaskUvMax,
            Math.min((float) framebufferWidth / Math.max(maskTextureWidth, 1), 1.0F),
            Math.min((float) framebufferHeight / Math.max(maskTextureHeight, 1), 1.0F)
        );
        GL20.glUniform1f(uTime, time);
        GL20.glUniform1f(uChaos, chaos);
        GL20.glUniform1f(uGlobalAlpha, globalAlpha);

        GL20.glUniform2f(uShapeScale, DEFAULT_SHAPE_SCALE_X, DEFAULT_SHAPE_SCALE_Y);
        GL20.glUniform2f(uShapeOffset, DEFAULT_SHAPE_OFFSET_X, DEFAULT_SHAPE_OFFSET_Y);
        GL20.glUniform1f(uAspect, aspect);
        GL20.glUniform1f(uAntiAlias, antiAlias);

        GL20.glUniform1f(uBaseAuraWidth, DEFAULT_BASE_AURA_WIDTH);
        GL20.glUniform1f(uAuraWidthChaos, DEFAULT_AURA_WIDTH_CHAOS);
        GL20.glUniform1f(uEdgeWarpStrength, DEFAULT_EDGE_WARP_STRENGTH);
        GL20.glUniform1f(uNoiseScale, DEFAULT_NOISE_SCALE);

        GL20.glUniform1f(uFillAlphaBase, DEFAULT_FILL_ALPHA_BASE);
        GL20.glUniform1f(uFillAlphaFlow, DEFAULT_FILL_ALPHA_FLOW);
        GL20.glUniform1f(uCoreAlpha, DEFAULT_CORE_ALPHA);
        GL20.glUniform1f(uEdgeAlphaBase, DEFAULT_EDGE_ALPHA_BASE);
        GL20.glUniform1f(uEdgeAlphaFlow, DEFAULT_EDGE_ALPHA_FLOW);
        GL20.glUniform1f(uRimAlpha, DEFAULT_RIM_ALPHA);

        GL20.glUniform3f(uInnerColorA, innerA[0], innerA[1], innerA[2]);
        GL20.glUniform3f(uInnerColorB, innerB[0], innerB[1], innerB[2]);
        GL20.glUniform3f(uOuterColorA, outerA[0], outerA[1], outerA[2]);
        GL20.glUniform3f(uOuterColorB, outerB[0], outerB[1], outerB[2]);
        GL20.glUniform3f(uEdgeColor, edge[0], edge[1], edge[2]);
        GL20.glUniform3f(uRimColor, SILVER[0], SILVER[1], SILVER[2]);

        GL20.glUniform1f(uInnerHighlightBase, DEFAULT_INNER_HIGHLIGHT_BASE);
        GL20.glUniform1f(uInnerHighlightFlow, DEFAULT_INNER_HIGHLIGHT_FLOW);
        GL20.glUniform1f(uOuterHighlightBase, DEFAULT_OUTER_HIGHLIGHT_BASE);
        GL20.glUniform1f(uOuterHighlightFlow, DEFAULT_OUTER_HIGHLIGHT_FLOW);
        GL20.glUniform1f(uEdgeHighlightStrength, DEFAULT_EDGE_HIGHLIGHT_STRENGTH);
    }

    public static void stop() {
        GL20.glUseProgram(0);
    }

    private static float[] liftedBaseColor(int color) {
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float luminance = 0.299F * r + 0.587F * g + 0.114F * b;
        float lift = luminance < 0.35F ? (0.35F - luminance) * 1.15F : 0.0F;
        return new float[] {
            clamp(r + (1.0F - r) * lift),
            clamp(g + (1.0F - g) * lift),
            clamp(b + (1.0F - b) * lift)
        };
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[] {
            clamp(a[0] + (b[0] - a[0]) * t),
            clamp(a[1] + (b[1] - a[1]) * t),
            clamp(a[2] + (b[2] - a[2]) * t)
        };
    }

    private static float clamp(float value) {
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    private static void ensureProgram() {
        if (programId != -1) {
            return;
        }

        int vertexShaderId = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER, "vertex");
        int fragmentShaderId = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER, "fragment");
        int linkedProgramId = GL20.glCreateProgram();

        GL20.glAttachShader(linkedProgramId, vertexShaderId);
        GL20.glAttachShader(linkedProgramId, fragmentShaderId);
        GL20.glLinkProgram(linkedProgramId);

        if (GL20.glGetProgrami(linkedProgramId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String infoLog = GL20.glGetProgramInfoLog(linkedProgramId, 8192);
            LOGGER.error("Failed to link aura shader program:\n{}", infoLog);
            GL20.glDeleteProgram(linkedProgramId);
            GL20.glDeleteShader(vertexShaderId);
            GL20.glDeleteShader(fragmentShaderId);
            throw new IllegalStateException("Failed to link aura shader program:\n" + infoLog);
        }

        GL20.glDetachShader(linkedProgramId, vertexShaderId);
        GL20.glDetachShader(linkedProgramId, fragmentShaderId);
        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);

        programId = linkedProgramId;
        uMaskTex = GL20.glGetUniformLocation(programId, "uMaskTex");
        uTexelSize = GL20.glGetUniformLocation(programId, "uTexelSize");
        uMaskUvMin = GL20.glGetUniformLocation(programId, "uMaskUvMin");
        uMaskUvMax = GL20.glGetUniformLocation(programId, "uMaskUvMax");
        uTime = GL20.glGetUniformLocation(programId, "uTime");
        uChaos = GL20.glGetUniformLocation(programId, "uChaos");
        uGlobalAlpha = GL20.glGetUniformLocation(programId, "uGlobalAlpha");
        uShapeScale = GL20.glGetUniformLocation(programId, "uShapeScale");
        uShapeOffset = GL20.glGetUniformLocation(programId, "uShapeOffset");
        uAspect = GL20.glGetUniformLocation(programId, "uAspect");
        uAntiAlias = GL20.glGetUniformLocation(programId, "uAntiAlias");
        uBaseAuraWidth = GL20.glGetUniformLocation(programId, "uBaseAuraWidth");
        uAuraWidthChaos = GL20.glGetUniformLocation(programId, "uAuraWidthChaos");
        uEdgeWarpStrength = GL20.glGetUniformLocation(programId, "uEdgeWarpStrength");
        uNoiseScale = GL20.glGetUniformLocation(programId, "uNoiseScale");
        uFillAlphaBase = GL20.glGetUniformLocation(programId, "uFillAlphaBase");
        uFillAlphaFlow = GL20.glGetUniformLocation(programId, "uFillAlphaFlow");
        uCoreAlpha = GL20.glGetUniformLocation(programId, "uCoreAlpha");
        uEdgeAlphaBase = GL20.glGetUniformLocation(programId, "uEdgeAlphaBase");
        uEdgeAlphaFlow = GL20.glGetUniformLocation(programId, "uEdgeAlphaFlow");
        uRimAlpha = GL20.glGetUniformLocation(programId, "uRimAlpha");
        uInnerColorA = GL20.glGetUniformLocation(programId, "uInnerColorA");
        uInnerColorB = GL20.glGetUniformLocation(programId, "uInnerColorB");
        uOuterColorA = GL20.glGetUniformLocation(programId, "uOuterColorA");
        uOuterColorB = GL20.glGetUniformLocation(programId, "uOuterColorB");
        uEdgeColor = GL20.glGetUniformLocation(programId, "uEdgeColor");
        uRimColor = GL20.glGetUniformLocation(programId, "uRimColor");
        uInnerHighlightBase = GL20.glGetUniformLocation(programId, "uInnerHighlightBase");
        uInnerHighlightFlow = GL20.glGetUniformLocation(programId, "uInnerHighlightFlow");
        uOuterHighlightBase = GL20.glGetUniformLocation(programId, "uOuterHighlightBase");
        uOuterHighlightFlow = GL20.glGetUniformLocation(programId, "uOuterHighlightFlow");
        uEdgeHighlightStrength = GL20.glGetUniformLocation(programId, "uEdgeHighlightStrength");
    }

    private static int compileShader(int type, ResourceLocation shaderLocation, String shaderKind) {
        int shaderId = GL20.glCreateShader(type);
        String shaderSource = readShaderSource(shaderLocation);

        GL20.glShaderSource(shaderId, shaderSource);
        GL20.glCompileShader(shaderId);

        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String infoLog = GL20.glGetShaderInfoLog(shaderId, 8192);
            LOGGER.error("Failed to compile {} shader {}:\n{}", shaderKind, shaderLocation, infoLog);
            GL20.glDeleteShader(shaderId);
            throw new IllegalStateException("Failed to compile " + shaderKind + " shader " + shaderLocation + ":\n" + infoLog);
        }

        return shaderId;
    }

    private static String readShaderSource(ResourceLocation shaderLocation) {
        try (
            IResource resource = Minecraft.getInstance().getResourceManager().getResource(shaderLocation);
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))
        ) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to read shader resource " + shaderLocation, exception);
        }
    }
}
