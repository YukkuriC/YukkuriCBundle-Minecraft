package io.yukkuric.bundle.blocks;

import io.yukkuric.bundle.blocks.be.MengerSpongeBE;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import static io.yukkuric.bundle.YukkuriCBundleMod.MOD_ID;

@EventBusSubscriber()
public class YCBlockEvents {
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        var type = YCBlocks.BE_MENGER_SPONGE.get();
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> new MengerSpongeBE.ItemCap((MengerSpongeBE) be));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, side) -> new MengerSpongeBE.FluidCap((MengerSpongeBE) be));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> new MengerSpongeBE.EnergyCap((MengerSpongeBE) be));
    }
}