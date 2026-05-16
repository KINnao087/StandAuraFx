package com.inza.standaurafx.client.render;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.entity.stand.StandEntityType;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.power.impl.stand.type.StandType;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public final class StandAuraBillboardRenderer {
    private static final Map<Integer, StandEntity> QUEUED_STANDS = new LinkedHashMap<>();
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int FALLBACK_AURA_COLOR = 0x8B5CFF;
    private static final Method STAND_ENTITY_TYPE_GET_STAND_TYPE = findStandTypeMethod();
    private static final float TIME_SCALE = 0.05F;
    private static final float DEFAULT_CHAOS = 0.2F;
    private static final float DEFAULT_ALPHA = 0.9F;
    private static final RenderType MASK_RENDER_TYPE = MaskRenderType.create();

    private static Framebuffer maskFramebuffer;
    private static Framebuffer sceneDepthFramebuffer;
    private static boolean auraPassActive;

    private StandAuraBillboardRenderer() {
    }

    public static boolean isAuraPassActive() {
        return auraPassActive;
    }

    public static void queueStand(StandEntity stand) {
        if (stand != null) {
            QUEUED_STANDS.put(stand.getEntityId(), stand);
        }
    }

    public static void renderQueuedAuras(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.world == null || minecraft.player == null) {
            QUEUED_STANDS.clear();
            return;
        }

        collectLoadedStands(minecraft);
        if (QUEUED_STANDS.isEmpty()) {
            return;
        }

        Framebuffer mainFramebuffer = minecraft.getFramebuffer();
        ensureFramebuffers(mainFramebuffer.framebufferWidth, mainFramebuffer.framebufferHeight);
        minecraft.getRenderTypeBuffers().getBufferSource().finish();
        copySceneDepth(mainFramebuffer);

        List<StandEntity> stands = new ArrayList<>(QUEUED_STANDS.values());
        QUEUED_STANDS.clear();

        Vector3d cameraPos = minecraft.gameRenderer.getActiveRenderInfo().getProjectedView();
        float partialTicks = event.getPartialTicks();

        for (StandEntity stand : stands) {
            if (!canRenderStand(minecraft, stand)) {
                continue;
            }

            renderStandMask(minecraft, event.getMatrixStack(), stand, cameraPos, partialTicks);
            compositeAura(mainFramebuffer, stand, partialTicks);
        }
    }

    private static boolean canRenderStand(Minecraft minecraft, StandEntity stand) {
        return stand != null && stand.isAlive() && stand.world == minecraft.world;
    }

    private static void collectLoadedStands(Minecraft minecraft) {
        for (Entity entity : minecraft.world.getAllEntities()) {
            if (entity instanceof StandEntity) {
                queueStand((StandEntity) entity);
            }
        }
    }

    private static void ensureFramebuffers(int width, int height) {
        maskFramebuffer = ensureFramebuffer(maskFramebuffer, width, height);
        sceneDepthFramebuffer = ensureFramebuffer(sceneDepthFramebuffer, width, height);
    }

    private static Framebuffer ensureFramebuffer(Framebuffer framebuffer, int width, int height) {
        if (framebuffer == null) {
            framebuffer = new Framebuffer(width, height, true, Minecraft.IS_RUNNING_ON_MAC);
            framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
            framebuffer.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
            return framebuffer;
        }

        if (framebuffer.framebufferWidth != width || framebuffer.framebufferHeight != height) {
            framebuffer.resize(width, height, Minecraft.IS_RUNNING_ON_MAC);
            framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
            framebuffer.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
        }

        return framebuffer;
    }

    private static void copySceneDepth(Framebuffer mainFramebuffer) {
        sceneDepthFramebuffer.bindFramebuffer(true);
        sceneDepthFramebuffer.framebufferClear(Minecraft.IS_RUNNING_ON_MAC);
        sceneDepthFramebuffer.func_237506_a_(mainFramebuffer);
        mainFramebuffer.bindFramebuffer(true);
    }

    private static void renderStandMask(
        Minecraft minecraft,
        MatrixStack matrixStack,
        StandEntity stand,
        Vector3d cameraPos,
        float partialTicks
    ) {
        Framebuffer mainFramebuffer = minecraft.getFramebuffer();
        IRenderTypeBuffer.Impl delegateBuffer = IRenderTypeBuffer.getImpl(Tessellator.getInstance().getBuffer());
        IRenderTypeBuffer maskBuffer = new ForcedMaskBuffer(delegateBuffer);
        double renderX = MathHelper.lerp(partialTicks, stand.lastTickPosX, stand.getPosX()) - cameraPos.x;
        double renderY = MathHelper.lerp(partialTicks, stand.lastTickPosY, stand.getPosY()) - cameraPos.y;
        double renderZ = MathHelper.lerp(partialTicks, stand.lastTickPosZ, stand.getPosZ()) - cameraPos.z;
        float yaw = MathHelper.lerp(partialTicks, stand.prevRotationYaw, stand.rotationYaw);

        maskFramebuffer.bindFramebuffer(true);
        maskFramebuffer.framebufferClear(Minecraft.IS_RUNNING_ON_MAC);

        auraPassActive = true;
        matrixStack.push();

        try {
            minecraft.getRenderManager().renderEntityStatic(
                stand,
                renderX,
                renderY,
                renderZ,
                yaw,
                partialTicks,
                matrixStack,
                maskBuffer,
                FULL_BRIGHT
            );
            delegateBuffer.finish(MASK_RENDER_TYPE);
        }
        finally {
            matrixStack.pop();
            auraPassActive = false;
            mainFramebuffer.bindFramebuffer(true);
        }
    }

    private static void compositeAura(Framebuffer mainFramebuffer, StandEntity stand, float partialTicks) {
        float time = (stand.ticksExisted + partialTicks) * TIME_SCALE;
        int framebufferWidth = mainFramebuffer.framebufferWidth;
        int framebufferHeight = mainFramebuffer.framebufferHeight;

        mainFramebuffer.bindFramebuffer(true);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableTexture();

        try {
            AuraShaderProgram.use(
                maskFramebuffer.getFrameBufferTexture(),
                maskFramebuffer.getDepthBuffer(),
                sceneDepthFramebuffer.getDepthBuffer(),
                framebufferWidth,
                framebufferHeight,
                maskFramebuffer.framebufferTextureWidth,
                maskFramebuffer.framebufferTextureHeight,
                resolveStandColor(stand),
                time,
                DEFAULT_CHAOS,
                DEFAULT_ALPHA
            );
            drawFullScreenQuad();
        }
        finally {
            AuraShaderProgram.stop();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private static void drawFullScreenQuad() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        bufferBuilder.pos(-1.0D, -1.0D, 0.0D).tex(0.0F, 0.0F).endVertex();
        bufferBuilder.pos(1.0D, -1.0D, 0.0D).tex(1.0F, 0.0F).endVertex();
        bufferBuilder.pos(1.0D, 1.0D, 0.0D).tex(1.0F, 1.0F).endVertex();
        bufferBuilder.pos(-1.0D, 1.0D, 0.0D).tex(0.0F, 1.0F).endVertex();
        tessellator.draw();
    }

    @SuppressWarnings("deprecation")
    private static int resolveStandColor(StandEntity stand) {
        StandType<?> registeredStandType = getRegisteredStandType(stand);
        if (registeredStandType != null) {
            return registeredStandType.getColor();
        }

        IStandPower power = stand.getUserPower();
        if (power != null && power.hasPower() && power.getType() != null) {
            return power.getType().getColor();
        }
        return FALLBACK_AURA_COLOR;
    }

    private static Method findStandTypeMethod() {
        try {
            Method method = StandEntityType.class.getDeclaredMethod("getStandType");
            method.setAccessible(true);
            return method;
        }
        catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static StandType<?> getRegisteredStandType(StandEntity stand) {
        if (!(stand.getType() instanceof StandEntityType) || STAND_ENTITY_TYPE_GET_STAND_TYPE == null) {
            return null;
        }

        try {
            Object standType = STAND_ENTITY_TYPE_GET_STAND_TYPE.invoke(stand.getType());
            return standType instanceof StandType ? (StandType<?>) standType : null;
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static final class ForcedMaskBuffer implements IRenderTypeBuffer {
        private final IRenderTypeBuffer.Impl delegate;

        private ForcedMaskBuffer(IRenderTypeBuffer.Impl delegate) {
            this.delegate = delegate;
        }

        @Override
        public com.mojang.blaze3d.vertex.IVertexBuilder getBuffer(RenderType renderType) {
            return delegate.getBuffer(MASK_RENDER_TYPE);
        }
    }

    private static final class MaskRenderType extends RenderType {
        private MaskRenderType(
            String name,
            VertexFormat vertexFormat,
            int drawMode,
            int bufferSize,
            boolean useDelegate,
            boolean needsSorting,
            Runnable setupTask,
            Runnable clearTask
        ) {
            super(name, vertexFormat, drawMode, bufferSize, useDelegate, needsSorting, setupTask, clearTask);
        }

        private static RenderType create() {
            RenderState.TargetState maskTarget = new RenderState.TargetState(
                "standaurafx_mask_target",
                () -> {
                    if (maskFramebuffer != null) {
                        maskFramebuffer.bindFramebuffer(false);
                    }
                },
                () -> Minecraft.getInstance().getFramebuffer().bindFramebuffer(false)
            );
            State state = State.getBuilder()
                .texture(NO_TEXTURE)
                .transparency(NO_TRANSPARENCY)
                .diffuseLighting(DIFFUSE_LIGHTING_DISABLED)
                .alpha(DEFAULT_ALPHA)
                .depthTest(DEPTH_LEQUAL)
                .cull(CULL_DISABLED)
                .lightmap(LIGHTMAP_DISABLED)
                .overlay(OVERLAY_DISABLED)
                .fog(NO_FOG)
                .layer(NO_LAYERING)
                .target(maskTarget)
                .writeMask(COLOR_DEPTH_WRITE)
                .build(false);
            return makeType("standaurafx_mask", DefaultVertexFormats.ENTITY, GL11.GL_QUADS, 256, true, false, state);
        }
    }
}
