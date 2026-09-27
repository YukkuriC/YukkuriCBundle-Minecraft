package io.yukkuric.bundle.particle;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/**
 * fade_light 粒子的生成参数：初始大小、初始颜色（0xRRGGBB）、存在时长（tick）。
 */
public class FadeLightParticleOptions implements ParticleOptions {
    private final float size;
    private final int color;
    private final int lifetime;

    public FadeLightParticleOptions(float size, int color, int lifetime) {
        this.size = size;
        this.color = color;
        this.lifetime = lifetime;
    }
    public FadeLightParticleOptions(float size, int lifetime) {
        this(size, 0xFFFFFF, lifetime);
    }

    public float getSize() {
        return size;
    }

    public int getColor() {
        return color;
    }

    public int getLifetime() {
        return lifetime;
    }

    @Override
    public ParticleType<?> getType() {
        return YCParticleTypes.FADE_LIGHT.get();
    }
}