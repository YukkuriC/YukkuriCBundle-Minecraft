package io.yukkuric.bundle.damage;

import io.yukkuric.bundle.YukkuriCBundleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class YCDamageTypes {
    public static final DeferredRegister<DamageType> DAMAGE_TYPES = DeferredRegister.create(Registries.DAMAGE_TYPE, YukkuriCBundleMod.MOD_ID);

    /**
     * 魔法飞弹伤害：运行时由数据包 {@code damage_type/magic_missile.json} 及
     * {@code minecraft:bypasses_armor} / {@code minecraft:bypasses_cooldown} 标签驱动（无视护甲、无视无敌帧）。
     */
    public static final DeferredHolder<DamageType, DamageType> MAGIC_MISSILE = DAMAGE_TYPES.register("magic_missile",
            () -> new DamageType("magic_missile", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1f));
}