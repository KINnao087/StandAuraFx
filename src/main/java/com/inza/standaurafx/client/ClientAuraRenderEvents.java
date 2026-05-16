package com.inza.standaurafx.client;

import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.inza.standaurafx.StandAuraFx;
import com.inza.standaurafx.client.render.StandAuraBillboardRenderer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StandAuraFx.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientAuraRenderEvents {
    private ClientAuraRenderEvents() {
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (StandAuraBillboardRenderer.isAuraPassActive()) {
            return;
        }

        if (!(event.getEntity() instanceof StandEntity)) {
            return;
        }

        StandEntity stand = (StandEntity) event.getEntity();
        if (!shouldRenderAura(stand)) {
            return;
        }

        StandAuraBillboardRenderer.queueStand(stand);
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        StandAuraBillboardRenderer.renderQueuedAuras(event);
    }

    private static boolean shouldRenderAura(StandEntity stand) {
        return true;
    }
}
