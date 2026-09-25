package io.yukkuric.bundle.client.entity;

import io.yukkuric.bundle.entity.YCEntityTypes;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class YCEntityRenderers {
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(YCEntityTypes.MAGIC_MISSILE.get(), MagicMissileRenderer::new);
    }
}