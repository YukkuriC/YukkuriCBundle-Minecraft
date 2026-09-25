package io.yukkuric.bundle.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static io.yukkuric.bundle.YukkuriCBundleMod.MOD_ID;
public class YCEntityTypes {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MOD_ID);

    public static final Supplier<EntityType<MagicMissile>> MAGIC_MISSILE = build(MagicMissile.ID,
            EntityType.Builder.<MagicMissile>of(MagicMissile::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F)
                    .eyeHeight(0.1F)
                    .clientTrackingRange(4)
                    .updateInterval(20));

    private static <B extends Entity> Supplier<EntityType<B>> build(String name, EntityType.Builder<B> builder) {
        return TYPES.register(name, () -> builder.build(name));
    }
}
