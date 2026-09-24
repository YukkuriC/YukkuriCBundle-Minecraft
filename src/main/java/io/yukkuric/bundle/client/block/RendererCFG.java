package io.yukkuric.bundle.client.block;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 各“花哨”方块实体渲染器的公共视觉参数。
 */
public interface RendererCFG {
    /**
     * 可渲染距离，线性淡出（FADE_START -> 100%，FADE_END -> 0%）
     */
    double FADE_START = 9;
    double FADE_END = 10;

    /**
     * 相机距离衰减系数：FADE_START 内恒为 1，FADE_START~FADE_END 间线性降至 0。
     */
    static float getFadeFactor(BlockEntity be) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distSqr = be.getBlockPos().distToCenterSqr(cam.x, cam.y, cam.z);
        return (float) Mth.clamp((FADE_END - Math.sqrt(distSqr)) / (FADE_END - FADE_START), 0.0, 1.0);
    }

    /**
     * 基于 FADE_END 的渲染剔除判断。
     */
    static boolean shouldRender(BlockEntity be, Vec3 cameraPos) {
        return be.getBlockPos().distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z) <= FADE_END * FADE_END;
    }
}
