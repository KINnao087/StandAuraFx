package com.inza.standaurafx.api;

import com.inza.standaurafx.client.render.StandAuraBillboardRenderer;

import net.minecraft.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public final class StandAuraFxApi {
    private StandAuraFxApi() {
    }

    /**
     * Queues an entity for the next aura render pass.
     *
     * The entity is rendered with its normal renderer/model into the aura mask,
     * then the built-in aura shader is composited around that mask. ROTP stand
     * entities use their stand color; other entities use the default aura color.
     */
    @OnlyIn(Dist.CLIENT)
    public static void renderAura(Entity entity) {
        StandAuraBillboardRenderer.queueEntity(entity);
    }

    /**
     * Queues an entity for the next aura render pass with a custom RGB color.
     *
     * @param auraColor RGB color in {@code 0xRRGGBB} format
     */
    @OnlyIn(Dist.CLIENT)
    public static void renderAura(Entity entity, int auraColor) {
        StandAuraBillboardRenderer.queueEntity(entity, auraColor);
    }
}
