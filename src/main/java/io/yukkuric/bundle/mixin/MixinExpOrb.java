package io.yukkuric.bundle.mixin;

import io.yukkuric.bundle.mixin_interface.IExpOrbEx;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ExperienceOrb.class)
public class MixinExpOrb implements IExpOrbEx {
    @Shadow
    private int count;
    @Shadow
    public int value;
    public int getTotalExp() {
        return count * value;
    }
}
