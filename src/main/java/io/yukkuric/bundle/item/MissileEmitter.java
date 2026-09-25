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

    public MissileEmitter(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Vec3 spawnPos = player.getBoundingBox().getCenter();
            Vec3 anchor = raycastAnchor(level, player);
            RandomSource random = player.getRandom();
            List<Entity> targets = scanTargets(level, anchor);
            if (targets.isEmpty()) {
                // 无目标：全部打向 anchor 周围 AABB_RADIUS 内随机点
                for (int i = 0; i < MISSILE_COUNT; i++) {
                    fireAtRandomPoint(level, spawnPos, anchor, player);
                }
            } else {
                Entity nearest = nearest(targets, anchor);
                for (int i = 0; i < CLOSE_TARGET_COUNT; i++) {
                    fireAtEntity(level, spawnPos, nearest, player);
                }
                List<Entity> pool = new ArrayList<>(targets);
                for (int i = 0; i < RANDOM_TARGET_COUNT; i++) {
                    fireAtEntity(level, spawnPos, pool.remove(random.nextInt(pool.size())), player);
                }
                int pointCount = MISSILE_COUNT - CLOSE_TARGET_COUNT - RANDOM_TARGET_COUNT;
                for (int i = 0; i < pointCount; i++) {
                    fireAtRandomPoint(level, spawnPos, anchor, player);
                }
            }
        }
        return InteractionResultHolder.success(stack);
    }

    /** 打向指定目标实体发射一颗魔法弹 */
    private void fireAtEntity(Level level, Vec3 spawnPos, Entity target, @Nullable Entity owner) {
        Vec3 velocity = randomDirection(level.random).scale(level.random.nextDouble() * 2);
        level.addFreshEntity(new MagicMissile(level, spawnPos, velocity, target, MISSILE_DAMAGE, owner));
    }

    /** 打向 anchor 周围 AABB_RADIUS 格立方体内随机点发射一颗魔法弹 */
    private void fireAtRandomPoint(Level level, Vec3 spawnPos, Vec3 anchor, @Nullable Entity owner) {
        Vec3 velocity = randomDirection(level.random).scale(level.random.nextDouble() * 2);
        Vec3 target = randomPointInBox(level.random, anchor);
        level.addFreshEntity(new MagicMissile(level, spawnPos, velocity, target, MISSILE_DAMAGE, owner));
    }

    /** 在 anchor 周围 AABB_RADIUS 格的立方体内取一随机点 */
    private Vec3 randomPointInBox(RandomSource random, Vec3 anchor) {
        return anchor.add(
                (random.nextDouble() - 0.5) * 2 * AABB_RADIUS,
                (random.nextDouble() - 0.5) * 2 * AABB_RADIUS,
                (random.nextDouble() - 0.5) * 2 * AABB_RADIUS);
    }

    /** 视线向前 raycast 64 格作为起点，命中方块取方块中心，未命中取视线终点 */
    private Vec3 raycastAnchor(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().scale(RAYCAST_RANGE);
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) {
            return BlockPos.containing(hit.getLocation()).getCenter();
        }
        return eye.add(look);
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