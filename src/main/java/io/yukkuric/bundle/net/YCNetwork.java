package io.yukkuric.bundle.net;

import io.yukkuric.bundle.item.MissileEmitter;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class YCNetwork {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar("1");
            registrar.playToServer(
                    MissileEmitter.FirePayload.TYPE, MissileEmitter.FirePayload.STREAM_CODEC,
                    (payload, context) -> MissileEmitter.fireIfEquipped(context.player()));
            registrar.playToServer(
                    ScrollSwitch.Payload.TYPE, ScrollSwitch.Payload.STREAM_CODEC,
                    (payload, context) -> ScrollSwitch.handleServer(context.player(), payload.offHand(), payload.delta()));
        });
    }
}
