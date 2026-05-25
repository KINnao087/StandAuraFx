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

    private static final float[] SILVER = {0.95F, 0.97F, 1.00F};
    private static final float[] PALE_BLUE = {0.74F, 0.82F, 0.97F};
    private static final int MASK_TEXTURE_UNIT = 0;
    private static final int ENTITY_DEPTH_TEXTURE_UNIT = 4;
    private static final int SCENE_DEPTH_TEXTURE_UNIT = 5;
    private static final String BLIT_FRAGMENT_SHADER_SOURCE =
        "#version 120\n" +
        "uniform sampler2D uTexture;\n" +
        "varying vec2 vUv;\n" +
        "void main() {\n" +
        "    gl_FragColor = texture2D(uTexture, vUv);\n" +
        "}\n";

    private static int programId = -1;
    private static int blitProgramId = -1;

    private static int uMaskTex;
    private static int uEntityDepthTex;
    private static int uSceneDepthTex;
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
    private static int uAuraThickness;
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
    private static int uBlitTexture;

    private AuraShaderProgram() {
    }

    public static void use(
        int maskTextureId,
        int entityDepthTextureId,
        int sceneDepthTextureId,
        int outputFramebufferWidth,
        int outputFramebufferHeight,
        int sourceFramebufferWidth,
        int sourceFramebufferHeight,
        int maskTextureWidth,
        int maskTextureHeight,
        int standColor,
        float time,
        float auraThicknessScale,
        float chaos,
        float globalAlpha
    ) {
        ensureProgram();

        float outputWidth = Math.max(outputFramebufferWidth, 1.0F);
        float outputHeight = Math.max(outputFramebufferHeight, 1.0F);
        float sourceWidth = Math.max(sourceFramebufferWidth, 1.0F);
        float sourceHeight = Math.max(sourceFramebufferHeight, 1.0F);
        float antiAlias = Math.max(2.0F / outputWidth, 2.0F / outputHeight);
        float aspect = sourceWidth / sourceHeight;
        float auraThickness = auraThicknessScale * AuraRuntimeSettings.auraThickness();
        float[] baseColor = liftedBaseColor(standColor);
        float[] innerA = mix(baseColor, PALE_BLUE, 0.16F);
        float[] innerB = mix(baseColor, SILVER, 0.28F);
        float[] outerA = mix(baseColor, PALE_BLUE, 0.34F);
        float[] outerB = mix(baseColor, SILVER, 0.48F);
        float[] edge = mix(baseColor, SILVER, 0.62F);

        GL20.glUseProgram(programId);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTextureId);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + ENTITY_DEPTH_TEXTURE_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, entityDepthTextureId);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + SCENE_DEPTH_TEXTURE_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneDepthTextureId);

        GL20.glUniform1i(uMaskTex, MASK_TEXTURE_UNIT);
        GL20.glUniform1i(uEntityDepthTex, ENTITY_DEPTH_TEXTURE_UNIT);
        GL20.glUniform1i(uSceneDepthTex, SCENE_DEPTH_TEXTURE_UNIT);
        GL20.glUniform2f(uTexelSize, 1.0F / Math.max(maskTextureWidth, 1), 1.0F / Math.max(maskTextureHeight, 1));
        GL20.glUniform2f(uMaskUvMin, 0.0F, 0.0F);
        GL20.glUniform2f(
            uMaskUvMax,
            Math.min((float) sourceFramebufferWidth / Math.max(maskTextureWidth, 1), 1.0F),
            Math.min((float) sourceFramebufferHeight / Math.max(maskTextureHeight, 1), 1.0F)
        );
        GL20.glUniform1f(uTime, time);
        GL20.glUniform1f(uChaos, chaos);
        GL20.glUniform1f(uGlobalAlpha, globalAlpha);

        GL20.glUniform2f(uShapeScale, AuraRuntimeSettings.shapeScaleX(), AuraRuntimeSettings.shapeScaleY());
        GL20.glUniform2f(uShapeOffset, AuraRuntimeSettings.shapeOffsetX(), AuraRuntimeSettings.shapeOffsetY());
        GL20.glUniform1f(uAspect, aspect);
        GL20.glUniform1f(uAntiAlias, antiAlias);
        GL20.glUniform1f(uAuraThickness, auraThickness);

        GL20.glUniform1f(uBaseAuraWidth, AuraRuntimeSettings.baseAuraWidth() * auraThickness);
        GL20.glUniform1f(uAuraWidthChaos, AuraRuntimeSettings.auraWidthChaos() * auraThickness);
        GL20.glUniform1f(uEdgeWarpStrength, AuraRuntimeSettings.edgeWarpStrength() * auraThickness);
        GL20.glUniform1f(uNoiseScale, AuraRuntimeSettings.noiseScale());

        GL20.glUniform1f(uFillAlphaBase, AuraRuntimeSettings.fillAlphaBase());
        GL20.glUniform1f(uFillAlphaFlow, AuraRuntimeSettings.fillAlphaFlow());
        GL20.glUniform1f(uCoreAlpha, AuraRuntimeSettings.coreAlpha());
        GL20.glUniform1f(uEdgeAlphaBase, AuraRuntimeSettings.edgeAlphaBase());
        GL20.glUniform1f(uEdgeAlphaFlow, AuraRuntimeSettings.edgeAlphaFlow());
        GL20.glUniform1f(uRimAlpha, AuraRuntimeSettings.rimAlpha());

        GL20.glUniform3f(uInnerColorA, innerA[0], innerA[1], innerA[2]);
        GL20.glUniform3f(uInnerColorB, innerB[0], innerB[1], innerB[2]);
        GL20.glUniform3f(uOuterColorA, outerA[0], outerA[1], outerA[2]);
        GL20.glUniform3f(uOuterColorB, outerB[0], outerB[1], outerB[2]);
        GL20.glUniform3f(uEdgeColor, edge[0], edge[1], edge[2]);
        GL20.glUniform3f(uRimColor, SILVER[0], SILVER[1], SILVER[2]);

        GL20.glUniform1f(uInnerHighlightBase, AuraRuntimeSettings.innerHighlightBase());
        GL20.glUniform1f(uInnerHighlightFlow, AuraRuntimeSettings.innerHighlightFlow());
        GL20.glUniform1f(uOuterHighlightBase, AuraRuntimeSettings.outerHighlightBase());
        GL20.glUniform1f(uOuterHighlightFlow, AuraRuntimeSettings.outerHighlightFlow());
        GL20.glUniform1f(uEdgeHighlightStrength, AuraRuntimeSettings.edgeHighlightStrength());
    }

    public static void stop() {
        GL20.glUseProgram(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + SCENE_DEPTH_TEXTURE_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + ENTITY_DEPTH_TEXTURE_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public static void useTextureBlit(int textureId) {
        ensureBlitProgram();

        GL20.glUseProgram(blitProgramId);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL20.glUniform1i(uBlitTexture, 0);
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
        uEntityDepthTex = GL20.glGetUniformLocation(programId, "uEntityDepthTex");
        uSceneDepthTex = GL20.glGetUniformLocation(programId, "uSceneDepthTex");
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
        uAuraThickness = GL20.glGetUniformLocation(programId, "uAuraThickness");
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

        compileShaderSource(shaderId, shaderSource, shaderKind + " shader " + shaderLocation);

        return shaderId;
    }

    private static int compileShader(int type, String shaderSource, String shaderName) {
        int shaderId = GL20.glCreateShader(type);
        compileShaderSource(shaderId, shaderSource, shaderName);
        return shaderId;
    }

    private static void compileShaderSource(int shaderId, String shaderSource, String shaderDescription) {

        GL20.glShaderSource(shaderId, shaderSource);
        GL20.glCompileShader(shaderId);

        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String infoLog = GL20.glGetShaderInfoLog(shaderId, 8192);
            LOGGER.error("Failed to compile {}:\n{}", shaderDescription, infoLog);
            GL20.glDeleteShader(shaderId);
            throw new IllegalStateException("Failed to compile " + shaderDescription + ":\n" + infoLog);
        }
    }

    private static void ensureBlitProgram() {
        if (blitProgramId != -1) {
            return;
        }

        int vertexShaderId = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER, "blit vertex");
        int fragmentShaderId = compileShader(GL20.GL_FRAGMENT_SHADER, BLIT_FRAGMENT_SHADER_SOURCE, "blit fragment");
        int linkedProgramId = GL20.glCreateProgram();

        GL20.glAttachShader(linkedProgramId, vertexShaderId);
        GL20.glAttachShader(linkedProgramId, fragmentShaderId);
        GL20.glLinkProgram(linkedProgramId);

        if (GL20.glGetProgrami(linkedProgramId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String infoLog = GL20.glGetProgramInfoLog(linkedProgramId, 8192);
            LOGGER.error("Failed to link aura blit shader program:\n{}", infoLog);
            GL20.glDeleteProgram(linkedProgramId);
            GL20.glDeleteShader(vertexShaderId);
            GL20.glDeleteShader(fragmentShaderId);
            throw new IllegalStateException("Failed to link aura blit shader program:\n" + infoLog);
        }

        GL20.glDetachShader(linkedProgramId, vertexShaderId);
        GL20.glDetachShader(linkedProgramId, fragmentShaderId);
        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);

        blitProgramId = linkedProgramId;
        uBlitTexture = GL20.glGetUniformLocation(blitProgramId, "uTexture");
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
