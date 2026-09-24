package io.yukkuric.bundle.datagen;

import io.yukkuric.bundle.YukkuriCBundleMod;
import io.yukkuric.bundle.datagen.menger_sponge.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = YukkuriCBundleMod.MOD_ID)
public class YukkuricBundleDataGen {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        var gen = event.getGenerator();
        var output = gen.getPackOutput();
        var lookups = event.getLookupProvider();
        var efh = event.getExistingFileHelper();

        // 模型先于 blockstates 注册，blockstate 引用模型时可通过共享 ExistingFileHelper 校验
        gen.addProvider(event.includeClient(), new MengerSpongeModels(output, efh));
        gen.addProvider(event.includeClient(), new MengerSpongeBlockStates(output, efh));
        gen.addProvider(event.includeServer(), new MengerSpongeRecipes(output, lookups));

        var blockTags = new MengerSpongeBlockTags(output, lookups, efh);
        gen.addProvider(event.includeServer(), blockTags);
        gen.addProvider(event.includeServer(), new MengerSpongeItemTags(output, lookups, blockTags.contentsGetter(), efh));
    }
}
