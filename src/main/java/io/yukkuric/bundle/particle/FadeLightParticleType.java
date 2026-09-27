package io.yukkuric.bundle.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

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