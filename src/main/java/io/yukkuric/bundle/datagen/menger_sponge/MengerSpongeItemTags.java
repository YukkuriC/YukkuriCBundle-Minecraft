package io.yukkuric.bundle.datagen.menger_sponge;

import io.yukkuric.bundle.YukkuriCBundleMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class MengerSpongeItemTags extends ItemTagsProvider {
    public MengerSpongeItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookups, CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, ExistingFileHelper efh) {
        super(output, lookups, blockTags, YukkuriCBundleMod.MOD_ID, efh);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        var appender = tag(MengerSpongeConsts.ITEM_TAG);
        MengerSpongeConsts.SPONGES.forEach(sponge -> addItemTag(appender, sponge.get()));
    }

    /** 将方块对应物品加入 item tag {@code yukkuric_bundle:menger_sponges} */
    private void addItemTag(IntrinsicHolderTagsProvider.IntrinsicTagAppender<Item> appender, Block block) {
        appender.add(block.asItem());
    }
}