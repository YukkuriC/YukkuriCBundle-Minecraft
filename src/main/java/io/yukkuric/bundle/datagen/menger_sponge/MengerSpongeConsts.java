package io.yukkuric.bundle.datagen.menger_sponge;

import io.yukkuric.bundle.YukkuriCBundleMod;
import io.yukkuric.bundle.blocks.MengerSpongeDuper;
import io.yukkuric.bundle.blocks.YCBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;

/**
 * 门格海绵家族的 datagen 公共常量。
 * 新增门格海绵时：将方块加入 {@link #SPONGES}，并在配方 provider 中补一行调用。
 */
public class MengerSpongeConsts {
    public static final List<DeferredBlock<? extends Block>> SPONGES = List.of(
            YCBlocks.MENGER_SPONGE_DUPER,
            YCBlocks.MENGER_SPONGE_MINER,
            YCBlocks.MENGER_SPONGE_VOID);

    public static final ResourceLocation TAG_ID = YukkuriCBundleMod.modLoc("menger_sponges");
    public static final TagKey<Block> BLOCK_TAG = TagKey.create(Registries.BLOCK, TAG_ID);
    public static final TagKey<Item> ITEM_TAG = TagKey.create(Registries.ITEM, TAG_ID);

    /** 基础模型所属方块的注册路径，其余门格海绵的 block model 以其为父模型 */
    public static final String BASE_BLOCK_ID = MengerSpongeDuper.ID;
}