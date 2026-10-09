package io.yukkuric.bundle.item;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import static io.yukkuric.bundle.YukkuriCBundleMod.MOD_ID;

public class YCItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredItem<MissileEmitter> MISSILE_EMITTER = ITEMS.register(MissileEmitter.ID, MissileEmitter::new);
    public static final DeferredItem<UniversalTool> UNIVERSAL_TOOL = ITEMS.register(UniversalTool.ID, UniversalTool::new);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
