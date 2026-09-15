package io.yukkuric.bundle.client.blocks;

/**
 * 各“花哨”方块实体渲染器的公共视觉参数。
 */
public interface RendererCFG {
    /**
     * 可渲染距离，线性淡出（FADE_START -> 100%，FADE_END -> 0%）
     */
    double FADE_START = 9;
    double FADE_END = 10;
}
