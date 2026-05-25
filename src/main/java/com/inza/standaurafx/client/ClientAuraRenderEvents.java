package com.inza.standaurafx.client;

import com.inza.standaurafx.StandAuraFx;
import com.inza.standaurafx.client.render.StandAuraBillboardRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.TickEvent;
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

        Entity entity = event.getEntity();
        if (!StandAuraBillboardRenderer.shouldRenderAutomaticAura(entity)) {
            return;
        }

        StandAuraBillboardRenderer.queueEntity(entity);
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        StandAuraBillboardRenderer.renderQueuedAuras(event);
    }

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        AuraClientCommand.handle(event);
    }

    @SubscribeEvent
    public static void onClientLoggedIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
        registerClientCommands();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            registerClientCommands();
        }
    }

    private static void registerClientCommands() {
        if (Minecraft.getInstance().getConnection() != null) {
            AuraClientCommand.register(Minecraft.getInstance().getConnection().getCommandDispatcher());
        }
    }

}
