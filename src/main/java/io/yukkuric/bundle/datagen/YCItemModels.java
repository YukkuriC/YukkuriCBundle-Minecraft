package io.yukkuric.bundle.datagen;

import io.yukkuric.bundle.YukkuriCBundleMod;
import io.yukkuric.bundle.datagen.menger_sponge.MengerSpongeModels;
import io.yukkuric.bundle.item.YCItems;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class YCItemModels extends ItemModelProvider {
    /** 子 provider 工厂，run() 时实例化并分发执行，后续新增直接往列表里加 */
    private static final List<BiFunction<PackOutput, ExistingFileHelper, ItemModelProvider>> SUB_PROVIDERS = List.of(
            MengerSpongeModels::new
    );

    public YCItemModels(PackOutput output, ExistingFileHelper efh) {
        super(output, YukkuriCBundleMod.MOD_ID, efh);
    }

    @Override
    protected void registerModels() {
        // 手工编写过模型的物品跳过，仅在缺失时生成占位模型
        for (var entry : YCItems.ITEMS.getEntries()) {
            var id = entry.getId();
            if (existingFileHelper.exists(id, PackType.CLIENT_RESOURCES, ".json", "models/item")) continue;
            // 占位模型允许引用尚未制作的贴图，先登记为已生成以跳过存在性校验
            existingFileHelper.trackGenerated(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "item/" + id.getPath()), TEXTURE);
            basicItem(id);
        }
    }

    // DataGenerator 不允许重复 provider 名，子 provider 不单独注册，在此统一分发执行
    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        var futures = new ArrayList<CompletableFuture<?>>();
        futures.add(super.run(cache));
        SUB_PROVIDERS.forEach(factory -> futures.add(factory.apply(output, existingFileHelper).run(cache)));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    }
}
