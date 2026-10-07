package io.yukkuric.bundle.utils;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MathUtils {
    /** 三次 Hermite 插值：线段两端切线分别为 m0、m1，t 取 [0,1] */
    public static Vec3 hermite(Vec3 p0, Vec3 m0, Vec3 p1, Vec3 m1, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return p0.scale(2 * t3 - 3 * t2 + 1)
                .add(m0.scale(t3 - 2 * t2 + t))
                .add(p1.scale(-2 * t3 + 3 * t2))
                .add(m1.scale(t3 - t2));
    }

    /** 速度按法线做镜面反射：v - 2(v·n)n，normal 为单位向量 */
    public static Vec3 reflect(Vec3 vel, Vec3 normal) {
        return vel.subtract(normal.scale(2 * vel.dot(normal)));
    }

    /** 在 AABB 内取一随机点 */
    public static Vec3 randomPointIn(AABB box, RandomSource random) {
        return new Vec3(
                box.minX + box.getXsize() * random.nextDouble(),
                box.minY + box.getYsize() * random.nextDouble(),
                box.minZ + box.getZsize() * random.nextDouble());
    }

    /** 随机单位向量 */
    public static Vec3 randomDirection(RandomSource random) {
        return new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize();
    }
}