package io.yukkuric.bundle.datagen.menger_sponge;

import io.yukkuric.bundle.blocks.YCBlocks;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import java.util.concurrent.CompletableFuture;

public class MengerSpongeRecipes extends RecipeProvider {
    public MengerSpongeRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookups) {
        super(output, lookups);
    }

    /**
     * 生成无序配方：#minecraft:sponges + 若干附加材料 -> 1 个门格海绵
     */
    private void addRecipe(RecipeOutput out, Block block, ItemLike... extras) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, block)
                .requires(MengerSpongeConsts.ITEM_TAG)
                .requires(Ingredient.of(extras))
                .unlockedBy("slept_in_bed", PlayerTrigger.TriggerInstance.sleptInBed())
                .save(out, BuiltInRegistries.BLOCK.getKey(block));
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        addRecipe(output, YCBlocks.MENGER_SPONGE_DUPER.get(), Items.CHEST);
        addRecipe(output, YCBlocks.MENGER_SPONGE_MINER.get(), Items.IRON_PICKAXE);
        addRecipe(output, YCBlocks.MENGER_SPONGE_VOID.get(), Items.HOPPER);
    }
}