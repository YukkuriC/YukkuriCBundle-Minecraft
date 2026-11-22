package io.yukkuric.bundle.mixin;

import io.yukkuric.bundle.block.YCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockAndTintGetter.class)
public interface MixinBlockAndTintGetter {
    @Inject(method = "canSeeSky", at = @At("HEAD"), cancellable = true)
    default void artificialSky(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!(this instanceof Level level)) return;
        if (level.getBlockState(pos.above()).is(YCBlocks.MENGER_SPONGE_ARTIFICIAL_SKY.get())) cir.setReturnValue(true);
    }
}
