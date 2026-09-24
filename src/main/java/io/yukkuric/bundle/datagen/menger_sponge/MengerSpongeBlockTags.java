package io.yukkuric.bundle.datagen.menger_sponge;

import io.yukkuric.bundle.YukkuriCBundleMod;
import io.yukkuric.bundle.tag.YCTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class MengerSpongeBlockTags extends BlockTagsProvider {
    public MengerSpongeBlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookups, ExistingFileHelper efh) {
        super(output, lookups, YukkuriCBundleMod.MOD_ID, efh);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        var appender = tag(YCTags.Blocks.MengerSponges);
        MengerSpongeConsts.getAllMengerSponges().forEach(sponge -> addBlockTag(appender, sponge.get()));
    }

    /** 将方块加入 block tag {@code yukkuric_bundle:menger_sponges} */
    private void addBlockTag(IntrinsicHolderTagsProvider.IntrinsicTagAppender<Block> appender, Block block) {
        appender.add(block);
    }
}