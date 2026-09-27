package io.yukkuric.bundle.item;

import io.yukkuric.bundle.entity.MagicMissile;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class MissileEmitter extends Item {
    public static final String ID = "missile_emitter";

    private static final int MISSILE_COUNT = 5;
    private static final int CLOSE_TARGET_COUNT = 2;
    private static final int RANDOM_TARGET_COUNT = 2;
    private static final int RAYCAST_RANGE = 128;
    private static final float SCAN_RANGE = 16;
    private static final float AABB_RADIUS = 5;
    private static final float MISSILE_DAMAGE = 20;
    /** 起点判定时扫描目标实体的范围半径 */
    private static final float ANCHOR_SCAN_RANGE = 64;
    /** 目标中心点与视线夹角小于该值时视为落在视锥内 */
    private static final double CONE_DEGREES = 30;
    /** 抛出速度沿视线的分量范围 */
    private static final double LAUNCH_SPEED_MIN = 0.2;
    private static final double LAUNCH_SPEED_MAX = 0.5;
    /** 抛出速度沿随机球面方向的分量上限 */
    private static final double LAUNCH_SCATTER_MAX = 0.4;

    public MissileEmitter(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Vec3 spawnPos = player.getBoundingBox().getCenter();
            Vec3 look = player.getLookAngle();
            Vec3 anchor = resolveAnchor(level, player, look);
            RandomSource random = player.getRandom();
            List<Entity> targets = scanTargets(level, anchor);
            if (targets.isEmpty()) {
                // 无目标：全部打向 anchor 周围 AABB_RADIUS 内随机点
                for (int i = 0; i < MISSILE_COUNT; i++) {
                    fireAtRandomPoint(level, spawnPos, anchor, look, player);
                }
            } else {
                Entity nearest = nearest(targets, anchor);
                for (int i = 0; i < CLOSE_TARGET_COUNT; i++) {
                    fireAtEntity(level, spawnPos, nearest, look, player);
                }
                List<Entity> pool = new ArrayList<>(targets);
                for (int i = 0; i < RANDOM_TARGET_COUNT; i++) {
                    fireAtEntity(level, spawnPos, pool.get(random.nextInt(pool.size())), look, player);
                }
                int pointCount = MISSILE_COUNT - CLOSE_TARGET_COUNT - RANDOM_TARGET_COUNT;
                for (int i = 0; i < pointCount; i++) {
                    fireAtRandomPoint(level, spawnPos, anchor, look, player);
                }
            }
        }
        return InteractionResultHolder.success(stack);
    }

    /** 打向指定目标实体发射一颗魔法弹 */
    private void fireAtEntity(Level level, Vec3 spawnPos, Entity target, Vec3 look, @Nullable Entity owner) {
        var missile = new MagicMissile(level, spawnPos, launchVelocity(level.random, look), target, MISSILE_DAMAGE, owner);
        missile.setMaxSpeed(3);
        level.addFreshEntity(missile);
    }

    /** 打向 anchor 周围 AABB_RADIUS 格立方体内随机点发射一颗魔法弹 */
    private void fireAtRandomPoint(Level level, Vec3 spawnPos, Vec3 anchor, Vec3 look, @Nullable Entity owner) {
        Vec3 target = randomPointInBox(level.random, anchor);
        level.addFreshEntity(new MagicMissile(level, spawnPos, launchVelocity(level.random, look), target, MISSILE_DAMAGE, owner));
    }

    /** 向前抛出：视线方向分量叠加一行随机球面方向的偏移 */
    private Vec3 launchVelocity(RandomSource random, Vec3 look) {
        double speed = LAUNCH_SPEED_MIN + random.nextDouble() * (LAUNCH_SPEED_MAX - LAUNCH_SPEED_MIN);
        Vec3 scatter = randomDirection(random).scale(random.nextDouble() * LAUNCH_SCATTER_MAX);
        return look.scale(speed).add(scatter);
    }

    /** 在 anchor 周围 AABB_RADIUS 格的立方体内取一随机点 */
    private Vec3 randomPointInBox(RandomSource random, Vec3 anchor) {
        return anchor.add(
                (random.nextDouble() - 0.5) * 2 * AABB_RADIUS,
                (random.nextDouble() - 0.5) * 2 * AABB_RADIUS,
                (random.nextDouble() - 0.5) * 2 * AABB_RADIUS);
    }

    /** 起点判定：视线 raycast 方块交点与视锥内最近目标中心点取更近的一个 */
    private Vec3 resolveAnchor(Level level, Player player, Vec3 look) {
        Vec3 eye = player.getEyePosition();
        Vec3 rayEnd = eye.add(look.scale(RAYCAST_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 point = hit.getType() != HitResult.Type.MISS
                ? BlockPos.containing(hit.getLocation()).getCenter()
                : rayEnd;
        Vec3 targetPoint = nearestTargetInCone(level, eye, look);
        return targetPoint != null && targetPoint.distanceToSqr(eye) < point.distanceToSqr(eye) ? targetPoint : point;
    }

    /** 扫描 eye 周围 ANCHOR_SCAN_RANGE 格内的目标实体，返回中心点落在视线 CONE_DEGREES 视锥内且离 eye 最近者 */
    private Vec3 nearestTargetInCone(Level level, Vec3 eye, Vec3 look) {
        double cosLimit = Math.cos(Math.toRadians(CONE_DEGREES));
        AABB area = AABB.ofSize(eye, ANCHOR_SCAN_RANGE * 2, ANCHOR_SCAN_RANGE * 2, ANCHOR_SCAN_RANGE * 2);
        Vec3 nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entity e : level.getEntitiesOfClass(Entity.class, area, this::isTarget)) {
            Vec3 dir = e.getBoundingBox().getCenter().subtract(eye);
            double dist = dir.length();
            if (dist < 1.0E-4 || dir.scale(1.0 / dist).dot(look) < cosLimit) continue;
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = e.getBoundingBox().getCenter();
            }
        }
        return nearest;
    }

    /** 收集 anchor 周围 SCAN_RANGE 格立方体内所有符合条件的目标 */
    private List<Entity> scanTargets(Level level, Vec3 anchor) {
        return level.getEntitiesOfClass(Entity.class, AABB.ofSize(anchor, SCAN_RANGE * 2, SCAN_RANGE * 2, SCAN_RANGE * 2), this::isTarget);
    }

    /** 从给定目标列表中选离 anchor 最近的一个 */
    private Entity nearest(List<Entity> targets, Vec3 anchor) {
        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entity e : targets) {
            double d = e.distanceToSqr(anchor);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = e;
            }
        }
        return nearest;
    }

    /** 暂定：属于 Enemy 的存活生物即视为目标 */
    private boolean isTarget(Entity e) {
        return e instanceof Enemy && e.isAlive();
    }

    private Vec3 randomDirection(RandomSource random) {
        return new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize();
    }
}