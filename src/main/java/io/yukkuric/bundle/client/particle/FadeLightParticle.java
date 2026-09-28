package io.yukkuric.bundle.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import io.yukkuric.bundle.particle.FadeLightParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.*;

/**
 * 以 fade_light 纹理渲染的圆形光点粒子。
 * - 大小在 lifetime 内从初始大小线性衰减到 0，到期消失
 * - additive 混合叠加，且不受环境光影响（恒定满亮度）
 */
public class FadeLightParticle extends TextureSheetParticle {
    private float initialSize;

    /**
     * 就是你丫搞的鬼
     */
    private static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }
    };

    public FadeLightParticle(ClientLevel level, double x, double y, double z, FadeLightParticleOptions options, SpriteSet sprites) {
        super(level, x, y, z);
        this.initialSize = options.getSize();
        this.quadSize = initialSize;
        this.lifetime = options.getLifetime();

        int color = options.getColor();
        setColor(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);

        TextureAtlasSprite sprite = sprites.get(level.getRandom());
        setSprite(sprite);
    }

    public void setSize(float size) {
        initialSize = size;
    }

    @Override
    public void tick() {
        super.tick();
        float progress = lifetime <= 0 ? 1.0F : (float) age / (float) lifetime;
        this.quadSize = initialSize * (1.0F - progress);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ADDITIVE;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
    }

    public static class Provider implements ParticleProvider<FadeLightParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(FadeLightParticleOptions type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new FadeLightParticle(level, x, y, z, type, sprites);
        }
    }
}