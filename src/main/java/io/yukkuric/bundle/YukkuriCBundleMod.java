package io.yukkuric.bundle;

import com.mojang.logging.LogUtils;
import io.yukkuric.bundle.blocks.YCBlocks;
import io.yukkuric.bundle.client.blocks.MengerSpongeRenderer;
import io.yukkuric.bundle.client.blocks.MengerSpongeVoidRenderer;
import io.yukkuric.bundle.items.YCItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import static io.yukkuric.bundle.blocks.YCBlocks.MENGER_SPONGE;

@Mod(YukkuriCBundleMod.MOD_ID)
public class YukkuriCBundleMod {
    public static final String MOD_ID = "yukkuric_bundle";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.yukkuric_bundle")).withTabsBefore(CreativeModeTabs.COMBAT).icon(() -> MENGER_SPONGE.get().asItem().getDefaultInstance()).displayItems((parameters, output) -> {
        for (var e : YCItems.ITEMS.getEntries()) {
            output.accept(e.get());
        }
        for (var e : YCBlocks.BLOCKS.getEntries()) {
            output.accept(e.get());
        }
    }).build());

    public YukkuriCBundleMod(IEventBus modEventBus, ModContainer modContainer) {
        YCBlocks.register(modEventBus);
        YCItems.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(YCBlocks.BE_MENGER_SPONGE.get(), MengerSpongeRenderer::new);
            event.registerBlockEntityRenderer(YCBlocks.BE_MENGER_SPONGE_VOID.get(), MengerSpongeVoidRenderer::new);
        }
    }
}
