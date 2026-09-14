package io.yukkuric.bundle.blocks;

import io.yukkuric.bundle.blocks.be.MengerSpongeBE;
import io.yukkuric.bundle.blocks.be.MengerSpongeVoidBE;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@EventBusSubscriber()
public class YCBlockEvents {
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        {
            var type = YCBlocks.BE_MENGER_SPONGE.get();
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> new MengerSpongeBE.ItemCap(be));
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, side) -> new MengerSpongeBE.FluidCap(be));
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> new MengerSpongeBE.EnergyCap(be));
        }
        {
            var type = YCBlocks.BE_MENGER_SPONGE_VOID.get();
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> new MengerSpongeVoidBE.ItemCap(be));
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, side) -> new MengerSpongeVoidBE.FluidCap(be));
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> new MengerSpongeVoidBE.EnergyCap(be));
        }
    }
}