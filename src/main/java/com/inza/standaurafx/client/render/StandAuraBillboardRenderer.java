package com.inza.standaurafx.client.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.entity.stand.StandEntityType;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.power.impl.stand.type.StandType;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public final class StandAuraBillboardRenderer {
    private static final Map<Integer, AuraRenderTarget> QUEUED_TARGETS = new LinkedHashMap<>();
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int FALLBACK_AURA_COLOR = 0x8B5CFF;
    private static final Method STAND_ENTITY_TYPE_GET_STAND_TYPE = findStandTypeMethod();
    private static final Field ENTITY_RENDER_MANAGER_RENDER_SHADOW = findRenderShadowField();
    private static final float TIME_SCALE = 0.05F;
    private static final float REFERENCE_AURA_DISTANCE = 8.0F;
    private static final float MIN_WORLD_THICKNESS_SCALE = 0.18F;
    private static final float MAX_WORLD_THICKNESS_SCALE = 2.6F;
    private static final float EXTRA_CLIP_PADDING_PIXELS = 300.0F;
    private static final RenderType MASK_RENDER_TYPE = MaskRenderType.create();
    private static final IVertexBuilder DISCARDING_VERTEX_BUILDER = new DiscardingVertexBuilder();

    private static Framebuffer maskFramebuffer;
    private static Framebuffer sceneDepthFramebuffer;
    private static Framebuffer auraFramebuffer;
    private static boolean auraPassActive;

    private StandAuraBillboardRenderer() {
    }

    public static boolean isAuraPassActive() {
        return auraPassActive;
    }

    public static void queueStand(StandEntity stand) {
        queueEntity(stand);
    }

    public static void queueEntity(Entity entity) {
        if (entity != null) {
            QUEUED_TARGETS.putIfAbsent(entity.getEntityId(), AuraRenderTarget.withDefaultColor(entity));
        }
    }

    public static void queueEntity(Entity entity, int auraColor) {
        if (entity != null) {
            QUEUED_TARGETS.put(entity.getEntityId(), AuraRenderTarget.withColor(entity, auraColor));
        }
    }

    public static void renderQueuedAuras(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.world == null || minecraft.player == null) {
            QUEUED_TARGETS.clear();
            return;
        }

        collectResolvedAuraTargets(minecraft);
        if (QUEUED_TARGETS.isEmpty()) {
            return;
        }

        Framebuffer mainFramebuffer = minecraft.getFramebuffer();
        ensureFramebuffers(mainFramebuffer.framebufferWidth, mainFramebuffer.framebufferHeight);
        minecraft.getRenderTypeBuffers().getBufferSource().finish();
        copySceneDepth(mainFramebuffer);
        clearAuraFramebuffer(mainFramebuffer);

        List<AuraRenderTarget> targets = new ArrayList<>(QUEUED_TARGETS.values());
        QUEUED_TARGETS.clear();

        Vector3d cameraPos = minecraft.gameRenderer.getActiveRenderInfo().getProjectedView();
        float partialTicks = event.getPartialTicks();
        Matrix4f modelViewMatrix = event.getMatrixStack().getLast().getMatrix();
        Matrix4f projectionMatrix = event.getProjectionMatrix();
        boolean renderedAnyAura = false;
        Map<Integer, AuraRenderGroup> groups = new LinkedHashMap<>();

        for (AuraRenderTarget target : targets) {
            Entity entity = target.entity;
            if (!canRenderEntity(minecraft, entity)) {
                continue;
            }

            ProjectedAuraBounds projectedBounds = projectEntityAuraBounds(
                entity,
                cameraPos,
                partialTicks,
                modelViewMatrix,
                projectionMatrix,
                mainFramebuffer.framebufferWidth,
                mainFramebuffer.framebufferHeight
            );
            if (projectedBounds.clipRect.isEmpty()) {
                continue;
            }

            int auraColor = target.resolveAuraColor();
            AuraRenderGroup group = groups.get(auraColor);
            if (group == null) {
                group = new AuraRenderGroup(auraColor);
                groups.put(auraColor, group);
            }
            group.add(target, projectedBounds);
        }

        for (AuraRenderGroup group : groups.values()) {
            renderGroupMask(minecraft, event.getMatrixStack(), group.targets, cameraPos, partialTicks);
            compositeAura(
                auraFramebuffer,
                mainFramebuffer.framebufferWidth,
                mainFramebuffer.framebufferHeight,
                group.auraColor,
                group.clipRect,
                group.getTime(partialTicks),
                group.getAuraThicknessScale(),
                partialTicks
            );
            renderedAnyAura = true;
        }

        if (renderedAnyAura) {
            blitAuraToMainFramebuffer(mainFramebuffer);
        }
        else {
            mainFramebuffer.bindFramebuffer(true);
        }
    }

    private static boolean canRenderEntity(Minecraft minecraft, Entity entity) {
        return entity != null && entity.isAlive() && entity.world == minecraft.world;
    }

    private static void collectResolvedAuraTargets(Minecraft minecraft) {
        for (Entity entity : minecraft.world.getAllEntities()) {
            if (shouldRenderAutomaticAura(entity)) {
                queueEntity(entity);
            }
        }
    }

    public static boolean shouldRenderAutomaticAura(Entity entity) {
        AuraRuntimeSettings.AuraMode mode = AuraRuntimeSettings.getAuraMode();
        if (mode == AuraRuntimeSettings.AuraMode.CLOSE) {
            return false;
        }

        if (entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;
            return mode == AuraRuntimeSettings.AuraMode.OPEN
                ? hasStandPower(player)
                : hasResolveEffect(player);
        }
        if (entity instanceof StandEntity) {
            LivingEntity user = ((StandEntity) entity).getUser();
            if (!(user instanceof PlayerEntity)) {
                return false;
            }

            PlayerEntity player = (PlayerEntity) user;
            return mode == AuraRuntimeSettings.AuraMode.OPEN
                ? hasStandPower(player)
                : hasResolveEffect(player);
        }
        return false;
    }

    private static boolean hasResolveEffect(LivingEntity entity) {
        return entity != null && entity.isPotionActive(ModStatusEffects.RESOLVE.get());
    }

    @SuppressWarnings("deprecation")
    private static boolean hasStandPower(PlayerEntity player) {
        IStandPower power = IStandPower.getPlayerStandPower(player);
        return power != null && power.hasPower();
    }

    private static void ensureFramebuffers(int width, int height) {
        maskFramebuffer = ensureFramebuffer(maskFramebuffer, width, height);
        sceneDepthFramebuffer = ensureFramebuffer(sceneDepthFramebuffer, width, height);
        auraFramebuffer = ensureFramebuffer(
            auraFramebuffer,
            scaledAuraFramebufferSize(width),
            scaledAuraFramebufferSize(height)
        );
    }

    private static int scaledAuraFramebufferSize(int value) {
        return Math.max(1, Math.round(value * AuraRuntimeSettings.framebufferScale()));
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

    private static void clearAuraFramebuffer(Framebuffer mainFramebuffer) {
        auraFramebuffer.bindFramebuffer(true);
        auraFramebuffer.framebufferClear(Minecraft.IS_RUNNING_ON_MAC);
        mainFramebuffer.bindFramebuffer(true);
    }

    private static void renderGroupMask(
        Minecraft minecraft,
        MatrixStack matrixStack,
        List<AuraRenderTarget> targets,
        Vector3d cameraPos,
        float partialTicks
    ) {
        Framebuffer mainFramebuffer = minecraft.getFramebuffer();
        EntityRendererManager renderManager = minecraft.getRenderManager();
        IRenderTypeBuffer.Impl delegateBuffer = IRenderTypeBuffer.getImpl(Tessellator.getInstance().getBuffer());
        IRenderTypeBuffer maskBuffer = new ForcedMaskBuffer(delegateBuffer);
        Boolean previousRenderShadow = getRenderShadow(renderManager);

        maskFramebuffer.bindFramebuffer(true);
        maskFramebuffer.framebufferClear(Minecraft.IS_RUNNING_ON_MAC);

        auraPassActive = true;

        try {
            renderManager.setRenderShadow(false);
            for (AuraRenderTarget target : targets) {
                Entity entity = target.entity;
                if (!canRenderEntity(minecraft, entity)) {
                    continue;
                }

                double renderX = MathHelper.lerp(partialTicks, entity.lastTickPosX, entity.getPosX()) - cameraPos.x;
                double renderY = MathHelper.lerp(partialTicks, entity.lastTickPosY, entity.getPosY()) - cameraPos.y;
                double renderZ = MathHelper.lerp(partialTicks, entity.lastTickPosZ, entity.getPosZ()) - cameraPos.z;
                float yaw = MathHelper.lerp(partialTicks, entity.prevRotationYaw, entity.rotationYaw);

                matrixStack.push();
                try {
                    renderManager.renderEntityStatic(
                        entity,
                        renderX,
                        renderY,
                        renderZ,
                        yaw,
                        partialTicks,
                        matrixStack,
                        maskBuffer,
                        FULL_BRIGHT
                    );
                }
                finally {
                    matrixStack.pop();
                }
            }
            delegateBuffer.finish(MASK_RENDER_TYPE);
        }
        finally {
            restoreRenderShadow(renderManager, previousRenderShadow);
            auraPassActive = false;
            mainFramebuffer.bindFramebuffer(true);
        }
    }

    private static void compositeAura(
        Framebuffer targetFramebuffer,
        int sourceFramebufferWidth,
        int sourceFramebufferHeight,
        int auraColor,
        ScreenRect clipRect,
        float time,
        float auraThicknessScale,
        float partialTicks
    ) {
        int framebufferWidth = targetFramebuffer.framebufferWidth;
        int framebufferHeight = targetFramebuffer.framebufferHeight;

        targetFramebuffer.bindFramebuffer(true);

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
                sourceFramebufferWidth,
                sourceFramebufferHeight,
                maskFramebuffer.framebufferTextureWidth,
                maskFramebuffer.framebufferTextureHeight,
                auraColor,
                time,
                auraThicknessScale,
                AuraRuntimeSettings.chaos(),
                AuraRuntimeSettings.globalAlpha()
            );
            drawScreenRectQuad(clipRect);
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

    private static void blitAuraToMainFramebuffer(Framebuffer mainFramebuffer) {
        mainFramebuffer.bindFramebuffer(true);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableTexture();
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        try {
            AuraShaderProgram.useTextureBlit(auraFramebuffer.getFrameBufferTexture());
            drawTexturedFullScreenQuad(
                Math.min((float) auraFramebuffer.framebufferWidth / Math.max(auraFramebuffer.framebufferTextureWidth, 1), 1.0F),
                Math.min((float) auraFramebuffer.framebufferHeight / Math.max(auraFramebuffer.framebufferTextureHeight, 1), 1.0F)
            );
        }
        finally {
            AuraShaderProgram.stop();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private static void drawFullScreenQuad() {
        drawScreenRectQuad(ScreenRect.full());
    }

    private static void drawScreenRectQuad(ScreenRect rect) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        float minX = rect.minU * 2.0F - 1.0F;
        float maxX = rect.maxU * 2.0F - 1.0F;
        float minY = rect.minV * 2.0F - 1.0F;
        float maxY = rect.maxV * 2.0F - 1.0F;

        bufferBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        bufferBuilder.pos(minX, minY, 0.0D).tex(rect.minU, rect.minV).endVertex();
        bufferBuilder.pos(maxX, minY, 0.0D).tex(rect.maxU, rect.minV).endVertex();
        bufferBuilder.pos(maxX, maxY, 0.0D).tex(rect.maxU, rect.maxV).endVertex();
        bufferBuilder.pos(minX, maxY, 0.0D).tex(rect.minU, rect.maxV).endVertex();
        tessellator.draw();
    }

    private static ProjectedAuraBounds projectEntityAuraBounds(
        Entity entity,
        Vector3d cameraPos,
        float partialTicks,
        Matrix4f modelViewMatrix,
        Matrix4f projectionMatrix,
        int sourceFramebufferWidth,
        int sourceFramebufferHeight
    ) {
        double interpolatedX = MathHelper.lerp(partialTicks, entity.lastTickPosX, entity.getPosX());
        double interpolatedY = MathHelper.lerp(partialTicks, entity.lastTickPosY, entity.getPosY());
        double interpolatedZ = MathHelper.lerp(partialTicks, entity.lastTickPosZ, entity.getPosZ());
        AxisAlignedBB box = entity.getBoundingBox()
            .offset(interpolatedX - entity.getPosX(), interpolatedY - entity.getPosY(), interpolatedZ - entity.getPosZ())
            .grow(0.25D);

        ProjectionBounds bounds = new ProjectionBounds();
        if (!projectBoxCorner(bounds, box.minX, box.minY, box.minZ, cameraPos, modelViewMatrix, projectionMatrix)) {
            return new ProjectedAuraBounds(ScreenRect.full(), estimateAuraThicknessScaleByDistance(entity, cameraPos, partialTicks));
        }
        if (!projectBoxCorner(bounds, box.minX, box.minY, box.maxZ, cameraPos, modelViewMatrix, projectionMatrix)) {
            return new ProjectedAuraBounds(ScreenRect.full(), estimateAuraThicknessScaleByDistance(entity, cameraPos, partialTicks));
        }
        if (!projectBoxCorner(bounds, box.minX, box.maxY, box.minZ, cameraPos, modelViewMatrix, projectionMatrix)) {
            return new ProjectedAuraBounds(ScreenRect.full(), estimateAuraThicknessScaleByDistance(entity, cameraPos, partialTicks));
        }
        if (!projectBoxCorner(bounds, box.minX, box.maxY, box.maxZ, cameraPos, modelViewMatrix, projectionMatrix)) {
            return new ProjectedAuraBounds(ScreenRect.full(), estimateAuraThicknessScaleByDistance(entity, cameraPos, partialTicks));
        }
        if (!projectBoxCorner(bounds, box.maxX, box.minY, box.minZ, cameraPos, modelViewMatrix, projectionMatrix)) {
            return new ProjectedAuraBounds(ScreenRect.full(), estimateAuraThicknessScaleByDistance(entity, cameraPos, partialTicks));
        }
        if (!projectBoxCorner(bounds, box.maxX, box.minY, box.maxZ, cameraPos, modelViewMatrix, projectionMatrix)) {
            return new ProjectedAuraBounds(ScreenRect.full(), estimateAuraThicknessScaleByDistance(entity, cameraPos, partialTicks));
        }
        if (!projectBoxCorner(bounds, box.maxX, box.maxY, box.minZ, cameraPos, modelViewMatrix, projectionMatrix)) {
            return new ProjectedAuraBounds(ScreenRect.full(), estimateAuraThicknessScaleByDistance(entity, cameraPos, partialTicks));
        }
        if (!projectBoxCorner(bounds, box.maxX, box.maxY, box.maxZ, cameraPos, modelViewMatrix, projectionMatrix)) {
            return new ProjectedAuraBounds(ScreenRect.full(), estimateAuraThicknessScaleByDistance(entity, cameraPos, partialTicks));
        }

        if (!bounds.hasProjectedPoint) {
            return new ProjectedAuraBounds(ScreenRect.empty(), 1.0F);
        }

        float sourceWidth = Math.max(sourceFramebufferWidth, 1.0F);
        float sourceHeight = Math.max(sourceFramebufferHeight, 1.0F);
        float aspect = sourceWidth / sourceHeight;
        float shapePadding = estimateAuraShapePadding();
        float paddingU = Math.max(shapePadding / Math.max(aspect * 2.0F, 0.0001F), 8.0F / sourceWidth)
            + EXTRA_CLIP_PADDING_PIXELS / sourceWidth;
        float paddingV = Math.max(shapePadding * 0.5F, 8.0F / sourceHeight)
            + EXTRA_CLIP_PADDING_PIXELS / sourceHeight;
        float projectedHeightInShapeSpace = Math.max((bounds.maxV - bounds.minV) * 2.0F, 0.0001F);
        float worldHeight = Math.max((float) box.getYSize(), 0.0001F);
        float projectedShapeUnitsPerBlock = projectedHeightInShapeSpace / worldHeight;
        float thicknessScale = MathHelper.clamp(
            projectedShapeUnitsPerBlock * REFERENCE_AURA_DISTANCE,
            MIN_WORLD_THICKNESS_SCALE,
            MAX_WORLD_THICKNESS_SCALE
        );
        return new ProjectedAuraBounds(
            ScreenRect.fromUnclamped(
                bounds.minU - paddingU,
                bounds.minV - paddingV,
                bounds.maxU + paddingU,
                bounds.maxV + paddingV
            ),
            thicknessScale
        );
    }

    private static boolean projectBoxCorner(
        ProjectionBounds bounds,
        double x,
        double y,
        double z,
        Vector3d cameraPos,
        Matrix4f modelViewMatrix,
        Matrix4f projectionMatrix
    ) {
        Vector4f corner = new Vector4f(
            (float) (x - cameraPos.x),
            (float) (y - cameraPos.y),
            (float) (z - cameraPos.z),
            1.0F
        );
        corner.transform(modelViewMatrix);
        corner.transform(projectionMatrix);
        if (corner.getW() <= 0.0001F) {
            return false;
        }

        corner.perspectiveDivide();
        float ndcX = corner.getX();
        float ndcY = corner.getY();
        float ndcZ = corner.getZ();
        if (Float.isNaN(ndcX) || Float.isNaN(ndcY) || Float.isNaN(ndcZ)) {
            return false;
        }
        if (ndcZ < -1.0F || ndcZ > 1.0F) {
            return true;
        }

        bounds.include(ndcX * 0.5F + 0.5F, ndcY * 0.5F + 0.5F);
        return true;
    }

    private static float estimateAuraShapePadding() {
        float thickness = AuraRuntimeSettings.auraThickness();
        float width = (AuraRuntimeSettings.baseAuraWidth()
            + AuraRuntimeSettings.auraWidthChaos() * 1.9F
            + AuraRuntimeSettings.edgeWarpStrength() * 2.0F
            + 0.07F) * thickness;
        width *= MAX_WORLD_THICKNESS_SCALE;
        return MathHelper.clamp(Math.max(width, 0.18F), 0.08F, 0.75F);
    }

    private static float estimateAuraThicknessScaleByDistance(Entity entity, Vector3d cameraPos, float partialTicks) {
        double interpolatedX = MathHelper.lerp(partialTicks, entity.lastTickPosX, entity.getPosX());
        double interpolatedY = MathHelper.lerp(partialTicks, entity.lastTickPosY, entity.getPosY());
        double interpolatedZ = MathHelper.lerp(partialTicks, entity.lastTickPosZ, entity.getPosZ());
        AxisAlignedBB box = entity.getBoundingBox()
            .offset(interpolatedX - entity.getPosX(), interpolatedY - entity.getPosY(), interpolatedZ - entity.getPosZ());
        float distance = MathHelper.sqrt((float) Math.max(box.getCenter().squareDistanceTo(cameraPos), 0.0001D));
        return MathHelper.clamp(
            REFERENCE_AURA_DISTANCE / Math.max(distance, 0.25F),
            MIN_WORLD_THICKNESS_SCALE,
            MAX_WORLD_THICKNESS_SCALE
        );
    }

    private static void drawTexturedFullScreenQuad(float maxU, float maxV) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        bufferBuilder.pos(-1.0D, -1.0D, 0.0D).tex(0.0F, 0.0F).endVertex();
        bufferBuilder.pos(1.0D, -1.0D, 0.0D).tex(maxU, 0.0F).endVertex();
        bufferBuilder.pos(1.0D, 1.0D, 0.0D).tex(maxU, maxV).endVertex();
        bufferBuilder.pos(-1.0D, 1.0D, 0.0D).tex(0.0F, maxV).endVertex();
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

    @SuppressWarnings("deprecation")
    private static int resolvePlayerStandColor(PlayerEntity player) {
        IStandPower power = IStandPower.getPlayerStandPower(player);
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

    private static Field findRenderShadowField() {
        for (String fieldName : new String[] {"renderShadow", "field_178638_s"}) {
            try {
                Field field = EntityRendererManager.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            }
            catch (ReflectiveOperationException exception) {
                // Try the next runtime name.
            }
        }
        return null;
    }

    private static Boolean getRenderShadow(EntityRendererManager renderManager) {
        if (ENTITY_RENDER_MANAGER_RENDER_SHADOW == null) {
            return null;
        }

        try {
            return ENTITY_RENDER_MANAGER_RENDER_SHADOW.getBoolean(renderManager);
        }
        catch (IllegalAccessException exception) {
            return null;
        }
    }

    private static void restoreRenderShadow(EntityRendererManager renderManager, Boolean previousRenderShadow) {
        renderManager.setRenderShadow(previousRenderShadow == null || previousRenderShadow.booleanValue());
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

    private static final class ProjectionBounds {
        private boolean hasProjectedPoint;
        private float minU = Float.POSITIVE_INFINITY;
        private float minV = Float.POSITIVE_INFINITY;
        private float maxU = Float.NEGATIVE_INFINITY;
        private float maxV = Float.NEGATIVE_INFINITY;

        private void include(float u, float v) {
            hasProjectedPoint = true;
            minU = Math.min(minU, u);
            minV = Math.min(minV, v);
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
        }
    }

    private static final class ProjectedAuraBounds {
        private final ScreenRect clipRect;
        private final float auraThicknessScale;

        private ProjectedAuraBounds(ScreenRect clipRect, float auraThicknessScale) {
            this.clipRect = clipRect;
            this.auraThicknessScale = auraThicknessScale;
        }
    }

    private static final class ScreenRect {
        private static final ScreenRect FULL = new ScreenRect(0.0F, 0.0F, 1.0F, 1.0F);
        private static final ScreenRect EMPTY = new ScreenRect(0.0F, 0.0F, 0.0F, 0.0F);

        private final float minU;
        private final float minV;
        private final float maxU;
        private final float maxV;

        private ScreenRect(float minU, float minV, float maxU, float maxV) {
            this.minU = minU;
            this.minV = minV;
            this.maxU = maxU;
            this.maxV = maxV;
        }

        private static ScreenRect full() {
            return FULL;
        }

        private static ScreenRect empty() {
            return EMPTY;
        }

        private static ScreenRect fromUnclamped(float minU, float minV, float maxU, float maxV) {
            if (maxU <= 0.0F || minU >= 1.0F || maxV <= 0.0F || minV >= 1.0F) {
                return EMPTY;
            }
            return new ScreenRect(
                MathHelper.clamp(minU, 0.0F, 1.0F),
                MathHelper.clamp(minV, 0.0F, 1.0F),
                MathHelper.clamp(maxU, 0.0F, 1.0F),
                MathHelper.clamp(maxV, 0.0F, 1.0F)
            );
        }

        private boolean isEmpty() {
            return maxU <= minU || maxV <= minV;
        }

        private ScreenRect union(ScreenRect other) {
            if (isEmpty()) {
                return other;
            }
            if (other.isEmpty()) {
                return this;
            }
            return new ScreenRect(
                Math.min(minU, other.minU),
                Math.min(minV, other.minV),
                Math.max(maxU, other.maxU),
                Math.max(maxV, other.maxV)
            );
        }
    }

    private static final class AuraRenderGroup {
        private final int auraColor;
        private final List<AuraRenderTarget> targets = new ArrayList<>();
        private ScreenRect clipRect = ScreenRect.empty();
        private float auraThicknessScale = MIN_WORLD_THICKNESS_SCALE;

        private AuraRenderGroup(int auraColor) {
            this.auraColor = auraColor;
        }

        private void add(AuraRenderTarget target, ProjectedAuraBounds projectedBounds) {
            targets.add(target);
            clipRect = clipRect.union(projectedBounds.clipRect);
            auraThicknessScale = Math.max(auraThicknessScale, projectedBounds.auraThicknessScale);
        }

        private float getTime(float partialTicks) {
            if (targets.isEmpty()) {
                return partialTicks * TIME_SCALE;
            }
            return (targets.get(0).entity.ticksExisted + partialTicks) * TIME_SCALE;
        }

        private float getAuraThicknessScale() {
            return auraThicknessScale;
        }
    }

    private static final class AuraRenderTarget {
        private final Entity entity;
        private final Integer auraColor;

        private AuraRenderTarget(Entity entity, Integer auraColor) {
            this.entity = entity;
            this.auraColor = auraColor;
        }

        private static AuraRenderTarget withDefaultColor(Entity entity) {
            return new AuraRenderTarget(entity, null);
        }

        private static AuraRenderTarget withColor(Entity entity, int auraColor) {
            return new AuraRenderTarget(entity, auraColor & 0xFFFFFF);
        }

        private int resolveAuraColor() {
            if (auraColor != null) {
                return auraColor.intValue();
            }
            if (entity instanceof StandEntity) {
                return resolveStandColor((StandEntity) entity);
            }
            if (entity instanceof PlayerEntity) {
                return resolvePlayerStandColor((PlayerEntity) entity);
            }
            return FALLBACK_AURA_COLOR;
        }
    }

    private static final class ForcedMaskBuffer implements IRenderTypeBuffer {
        private final IRenderTypeBuffer.Impl delegate;

        private ForcedMaskBuffer(IRenderTypeBuffer.Impl delegate) {
            this.delegate = delegate;
        }

        @Override
        public IVertexBuilder getBuffer(RenderType renderType) {
            if (renderType.getVertexFormat() != DefaultVertexFormats.ENTITY) {
                return DISCARDING_VERTEX_BUILDER;
            }
            return delegate.getBuffer(MASK_RENDER_TYPE);
        }
    }

    private static final class DiscardingVertexBuilder implements IVertexBuilder {
        @Override
        public IVertexBuilder pos(double x, double y, double z) {
            return this;
        }

        @Override
        public IVertexBuilder color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public IVertexBuilder tex(float u, float v) {
            return this;
        }

        @Override
        public IVertexBuilder overlay(int u, int v) {
            return this;
        }

        @Override
        public IVertexBuilder lightmap(int u, int v) {
            return this;
        }

        @Override
        public IVertexBuilder normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
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
