package io.yukkuric.bundle.tag;

import io.yukkuric.bundle.YukkuriCBundleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class YCTags {
    public static class ID {
        public static final ResourceLocation MengerSponges = YukkuriCBundleMod.modLoc("menger_sponges");
        public static final ResourceLocation OreTargets = YukkuriCBundleMod.modLoc("ore_targets");
    }

    public static class Blocks {
        public static final TagKey<Block> MengerSponges = TagKey.create(Registries.BLOCK, ID.MengerSponges);
        public static final TagKey<Block> OreTargets = TagKey.create(Registries.BLOCK, ID.OreTargets);
    }

    public static class Items {
        public static final TagKey<Item> MengerSponges = TagKey.create(Registries.ITEM, ID.MengerSponges);
        public static final TagKey<Item> OreTargets = TagKey.create(Registries.ITEM, ID.OreTargets);
    }
}
