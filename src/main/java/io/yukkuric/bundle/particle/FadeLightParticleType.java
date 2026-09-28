package io.yukkuric.bundle.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * fade_light 粒子类型注册。
 * 注意：该粒子走 ADDITIVE render type，与原版地形不在同一批次，刷出时序不定会导致闪烁，因此尾迹与爆炸光点已改由实体渲染器直接绘制；
 * 此处实现保留备用。
 */
public class FadeLightParticleType extends ParticleType<FadeLightParticleOptions> {
    private static final MapCodec<FadeLightParticleOptions> CODEC = RecordCodecBuilder.mapCodec(inst ->
            inst.group(
                    Codec.FLOAT.fieldOf("size").forGetter(FadeLightParticleOptions::getSize),
                    Codec.INT.fieldOf("color").forGetter(FadeLightParticleOptions::getColor),
                    Codec.INT.fieldOf("lifetime").forGetter(FadeLightParticleOptions::getLifetime)
            ).apply(inst, FadeLightParticleOptions::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, FadeLightParticleOptions> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, options) -> {
                        buffer.writeFloat(options.getSize());
                        buffer.writeInt(options.getColor());
                        buffer.writeInt(options.getLifetime());
                    },
                    buffer -> new FadeLightParticleOptions(buffer.readFloat(), buffer.readInt(), buffer.readInt()));

    public FadeLightParticleType(boolean overrideLimiter) {
        super(overrideLimiter);
    }

    @Override
    public MapCodec<FadeLightParticleOptions> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, FadeLightParticleOptions> streamCodec() {
        return STREAM_CODEC;
    }
}