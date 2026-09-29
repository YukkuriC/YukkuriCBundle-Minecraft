package io.yukkuric.bundle.item;

import io.yukkuric.bundle.YukkuriCBundleMod;
import io.yukkuric.bundle.entity.MagicMissile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForgeMod;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiConsumer;

public class MissileEmitter extends ArmorItem {
    public static final String ID = "missile_emitter";
    public static final ResourceLocation IDLoc = YukkuriCBundleMod.modLoc(ID);
    public static final ItemAttributeModifiers EXTRA_ATTRS;
    public static final Properties PROPS = new Properties()
            .stacksTo(1).rarity(Rarity.EPIC);
    static {
        var attrBuilder = ItemAttributeModifiers.builder();
        BiConsumer<Holder<Attribute>, Float> addAttr = (attr, addVal) -> {
            attrBuilder.add(attr, new AttributeModifier(IDLoc, addVal, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY);
        };
        addAttr.accept(NeoForgeMod.CREATIVE_FLIGHT, 1f);
        addAttr.accept(Attributes.FLYING_SPEED, 2f);
        addAttr.accept(Attributes.MAX_ABSORPTION, 100f);
        EXTRA_ATTRS = attrBuilder.build();
    }
    public final ItemAttributeModifiers MERGED_ATTRS;

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
    /** 使用状态持续到玩家松开右键为止 */
    private static final int USE_DURATION = Integer.MAX_VALUE;
    /** 持续使用时刷新一批导弹的间隔 tick 数 */
    private static final int FIRE_INTERVAL = 5;

    public MissileEmitter(Properties properties) {
        super(ArmorMaterials.NETHERITE, Type.HELMET, properties);
        // merge attrs
        var list = new ArrayList<>(super.getDefaultAttributeModifiers().modifiers());
        list.addAll(EXTRA_ATTRS.modifiers());
        MERGED_ATTRS = new ItemAttributeModifiers(Collections.unmodifiableList(list), true);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return MERGED_ATTRS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            fireBatch(level, player);
        }
        player.startUsingItem(hand);
        // consume 而非 success：使用瞬间不触发挥手动画
        return InteractionResultHolder.consume(stack);
    }

    /** 持续使用中按间隔反复发射，逻辑与首次使用完全一致 */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        int elapsed = USE_DURATION - remainingUseDuration;
        if (level.isClientSide || elapsed <= 0 || elapsed % FIRE_INTERVAL != 0) return;
        if (entity instanceof Player player) fireBatch(level, player);
    }

    /** 发射一批导弹 */
    private void fireBatch(Level level, Player player) {
        Vec3 spawnPos = player.getBoundingBox().getCenter();
        // except 传 null：音效由服务端广播给附近所有人，含发射者本人
        level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z, SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
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
            for (int i = 0; i < RANDOM_TARGET_COUNT; i++) {
                fireAtEntity(level, spawnPos, targets.get(random.nextInt(targets.size())), look, player);
            }
            int pointCount = MISSILE_COUNT - CLOSE_TARGET_COUNT - RANDOM_TARGET_COUNT;
            for (int i = 0; i < pointCount; i++) {
                fireAtRandomPoint(level, spawnPos, anchor, look, player);
            }
        }
    }

    /** 使用中不播放抬起/挥动动画，物品保持在手中原姿态 */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    /** 打向指定目标实体发射一颗魔法弹 */
    private void fireAtEntity(Level level, Vec3 spawnPos, Entity target, Vec3 look, @Nullable Entity owner) {
        var missile = new MagicMissile(level, spawnPos, launchVelocity(level.random, look), target, MISSILE_DAMAGE, owner);
        missile.setMaxSpeed(3);
        missile.tickCount += (int) (Math.random() * MagicMissile.PREWARM_TICKS);
        missile.targetSelector = this::isTarget;
        level.addFreshEntity(missile);
    }

    /** 打向 anchor 周围 AABB_RADIUS 格立方体内随机点发射一颗魔法弹 */
    private void fireAtRandomPoint(Level level, Vec3 spawnPos, Vec3 anchor, Vec3 look, @Nullable Entity owner) {
        Vec3 target = randomPointInBox(level.random, anchor);
        var missile = new MagicMissile(level, spawnPos, launchVelocity(level.random, look), target, MISSILE_DAMAGE, owner);
        missile.tickCount += (int) (Math.random() * MagicMissile.PREWARM_TICKS);
        level.addFreshEntity(missile);
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