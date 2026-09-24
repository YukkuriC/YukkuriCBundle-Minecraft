package io.yukkuric.bundle.datagen.menger_sponge;

import io.yukkuric.bundle.YukkuriCBundleMod;
import io.yukkuric.bundle.blocks.YCBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.*;

/**
 * 门格海绵家族的 datagen 公共常量。
 */
public class MengerSpongeConsts {
    private static List<DeferredHolder<Block, ? extends Block>> _cachedMengerSponges = null;
    @SuppressWarnings("unchecked")
    public static List<DeferredHolder<Block, ? extends Block>> getAllMengerSponges() {
        if (_cachedMengerSponges == null) {
            _cachedMengerSponges = Arrays.asList(
                    YCBlocks.BLOCKS.getEntries().stream()
                            .filter(e -> e.getId().getPath().contains("menger_sponge"))
                            .toArray(DeferredHolder[]::new)
            );
            _cachedMengerSponges.sort(Comparator.comparing(
                    e -> e.getId().getPath(),
                    String::compareTo
            ));
        }
        return _cachedMengerSponges;
    }

    public static final ResourceLocation TAG_ID = YukkuriCBundleMod.modLoc("menger_sponges");
    public static final TagKey<Block> BLOCK_TAG = TagKey.create(Registries.BLOCK, TAG_ID);
    public static final TagKey<Item> ITEM_TAG = TagKey.create(Registries.ITEM, TAG_ID);

    /** 基础模型所属方块的注册路径，其余门格海绵的 block model 以其为父模型 */
    public static final String BASE_BLOCK_ID = "menger_sponge/base";
}