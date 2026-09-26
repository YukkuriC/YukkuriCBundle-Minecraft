package io.yukkuric.bundle.client;

import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// 内存 BakedModel 基类：收拢公用的占位方法与顶点打包工具，子类只提供几何
public abstract class CustomBakedModel implements BakedModel {
    private List<BakedQuad> quads;

    // 懒构建：在首次渲染（getQuads）时才生成几何，确保此时方块 atlas 已就绪、能取到正确 sprite
    protected abstract List<BakedQuad> buildQuads();

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        if (side != null) return List.of();
        if (quads == null) quads = buildQuads();
        return quads;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return false;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return null;
    }

    @Override
    public ItemTransforms getTransforms() {
        return ItemTransforms.NO_TRANSFORMS;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    // ===== 共享顶点打包工具 =====

    // 按 DefaultVertexFormat.BLOCK 打包顶点；法向朝向修正为背离几何中心，保证外面的面朝摄像机可见。
    // 整面统一取样 sprite 中心像素（纯色纹理），避免顶点 UV 直取整张 atlas 造成马赛克
    protected static BakedQuad bakeQuad(Vec3[] corners, TextureAtlasSprite sprite) {
        Vec3 n = corners[1].subtract(corners[0]).cross(corners[3].subtract(corners[0])).normalize();
        Vec3 sum = corners[0].add(corners[1]).add(corners[2]).add(corners[3]);
        if (n.dot(sum) < 0) {
            Vec3 tmp = corners[1];
            corners[1] = corners[3];
            corners[3] = tmp;
            n = new Vec3(-n.x, -n.y, -n.z);
        }
        int packedNormal = packedNormal(n.x, n.y, n.z);
        float cu = sprite.getU(0.5F);
        float cv = sprite.getV(0.5F);
        int[] data = new int[4 * 8];
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            Vec3 c = corners[i];
            data[o] = Float.floatToRawIntBits((float) c.x);
            data[o + 1] = Float.floatToRawIntBits((float) c.y);
            data[o + 2] = Float.floatToRawIntBits((float) c.z);
            data[o + 3] = 0xFFFFFFFF;
            data[o + 4] = Float.floatToRawIntBits(cu);
            data[o + 5] = Float.floatToRawIntBits(cv);
            data[o + 6] = 0;
            data[o + 7] = packedNormal;
        }
        return new BakedQuad(data, -1, Direction.UP, sprite, false);
    }

    private static int packedNormal(double x, double y, double z) {
        return ((int) (x * 127) & 0xFF) | (((int) (y * 127) & 0xFF) << 8) | (((int) (z * 127) & 0xFF) << 16);
    }
}