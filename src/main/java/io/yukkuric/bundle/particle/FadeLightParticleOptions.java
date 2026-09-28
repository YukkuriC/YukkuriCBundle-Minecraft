package io.yukkuric.bundle.particle;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/**
 * fade_light 粒子的生成参数：初始大小、初始颜色（0xRRGGBB）、存在时长（tick）。
 * 注意：该粒子走 ADDITIVE render type，与原版地形不在同一批次，刷出时序不定会导致闪烁，因此尾迹与爆炸光点已改由实体渲染器直接绘制；
 * 此处实现保留备用。
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