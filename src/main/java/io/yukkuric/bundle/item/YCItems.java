package io.yukkuric.bundle.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import static io.yukkuric.bundle.YukkuriCBundleMod.MOD_ID;

public class YCItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredItem<MissileEmitter> MISSILE_EMITTER = ITEMS.registerItem(MissileEmitter.ID, MissileEmitter::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
    );

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
