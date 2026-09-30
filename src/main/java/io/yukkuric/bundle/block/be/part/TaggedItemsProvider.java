package io.yukkuric.bundle.block.be.part;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

import java.util.*;
import java.util.function.Supplier;

public class TaggedItemsProvider implements Supplier<List<ItemStack>> {
    public static int DEFAULT_COUNT = 1_000_000_000;

    List<ItemStack> cachedList;
    public final TagKey<Block> tagBlock;
    public final TagKey<Item> tagItem;
    public final int counts;

    public TaggedItemsProvider(TagKey<Block> tagBlock, TagKey<Item> tagItem, int counts) {
        this.tagBlock = tagBlock;
        this.tagItem = tagItem;
        this.counts = counts;
    }
    public TaggedItemsProvider(TagKey<Block> tagBlock, TagKey<Item> tagItem) {
        this(tagBlock, tagItem, DEFAULT_COUNT);
    }

    public List<ItemStack> get() {
        if (cachedList == null) {
            var mergedSet = new HashSet<Item>();
            if (tagItem != null) {
                var itemSet = BuiltInRegistries.ITEM.getOrCreateTag(tagItem);
                mergedSet.addAll(itemSet.stream().map(Holder::value).toList());
            }
            if (tagBlock != null) {
                var blockSet = BuiltInRegistries.BLOCK.getOrCreateTag(tagBlock);
                mergedSet.addAll(blockSet.stream().map(b -> b.value().asItem()).toList());
            }
            var ret = new ArrayList<ItemStack>();
            for (var b : mergedSet) {
                var stack = b.getDefaultInstance();
                stack = stack.copyWithCount(counts);
                ret.add(stack);
            }
            cachedList = ImmutableList.copyOf(ret);
        }
        return cachedList;
    }

    public boolean contains(ItemStack stack) {
        if (tagItem != null && stack.is(tagItem)) return true;
        return tagBlock != null && stack.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().is(tagBlock);
    }
}
