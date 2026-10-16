package io.yukkuric.bundle.mixin;

import io.yukkuric.bundle.YukkuriCBundleMod;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.InsertableLinkedOpenCustomHashSet;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuildCreativeModeTabContentsEvent.class)
public abstract class WhoFuckingCaresCreativeTabDupe {
    @Shadow
    static boolean isParentTab(CreativeModeTab.TabVisibility visibility) {
        return false;
    }
    @Shadow
    static boolean isSearchTab(CreativeModeTab.TabVisibility visibility) {
        return false;
    }
    @Shadow
    @Final
    private InsertableLinkedOpenCustomHashSet<ItemStack> parentEntries;
    @Shadow
    @Final
    private InsertableLinkedOpenCustomHashSet<ItemStack> searchEntries;
    @Inject(method = {"accept", "insertFirst"}, at = @At(value = "HEAD"), cancellable = true)
    void hookAllowDupe(ItemStack newEntry, CreativeModeTab.TabVisibility visibility, CallbackInfo ci) {
        if (isParentTab(visibility) && parentEntries.contains(newEntry)) {
            YukkuriCBundleMod.LOGGER.info("stack {} duped inside creative tab, so what?", newEntry);
            ci.cancel();
        }
        if (isSearchTab(visibility) && searchEntries.contains(newEntry)) {
            if (isParentTab(visibility) && parentEntries.contains(newEntry)) {
                YukkuriCBundleMod.LOGGER.info("stack {} duped inside creative tab, so what?", newEntry);
                ci.cancel();
            }
        }
    }
    @Inject(method = {"insertAfter", "insertBefore"}, at = @At(value = "HEAD"), cancellable = true)
    void hookAllowDupe2(ItemStack existingEntry, ItemStack newEntry, CreativeModeTab.TabVisibility visibility, CallbackInfo ci) {
        hookAllowDupe(newEntry, visibility, ci);
    }
}
