package io.yukkuric.bundle.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static io.yukkuric.bundle.YukkuriCBundleMod.MOD_ID;

public class YCParticleTypes {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, MOD_ID);

    public static final DeferredHolder<ParticleType<?>, FadeLightParticleType> FADE_LIGHT =
            PARTICLE_TYPES.register("fade_light", () -> new FadeLightParticleType(false));

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}