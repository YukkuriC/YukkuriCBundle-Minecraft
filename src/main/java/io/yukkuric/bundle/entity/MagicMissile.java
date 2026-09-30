package io.yukkuric.bundle.entity;

import io.yukkuric.bundle.damage.YCDamageTypes;
import io.yukkuric.bundle.utils.MathUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

public class MagicMissile extends Projectile {
    public static final String ID = "magic_missile";
    public static final double MAX_TARGET_DISTANCE = 1000.0;
    /** 锁定目标实体时的颜色 */
    public static final Vec3 COLOR_LOCKED = Vec3.fromRGB24(0x87CEEB);
    /** 未锁定目标实体时的颜色 */
    public static final Vec3 COLOR_FREE = Vec3.fromRGB24(0xFF77EE);

    /** 尾迹珠的初始半宽 */
    public static final float TRAIL_SIZE = 0.3F;
    /** 尾迹珠寿命（tick），到期后由渲染器淡出 */
    public static final int TRAIL_LIFETIME = 30;

    /** 爆炸光点初始半宽的取值范围，落点与大小均在爆炸瞬间一次定死 */
    private static final float EXPLODE_SIZE_MIN = 0.2F;
    private static final float EXPLODE_SIZE_MAX = 1.0F;
    /** 最大初始半宽对应的光点收缩时长（tick），更小的光点按大小正比缩短 */
    private static final int EXPLODE_LIFETIME = 30;
    /** 压在爆心的闪光：比其余光点更大、消散更快 */
    private static final float EXPLODE_FLASH_SIZE = 2.0F;
    private static final int EXPLODE_FLASH_LIFETIME = 10;

    private static final float DEF_TRACK_RATE = 0.05F;
    private static final float TRACK_RATE_INC = 0.01F;
    private static final float TRACK_RATE_MAX = 0.5F;
    public static final double DEF_MAX_SPEED = 2;
    public static final double DEF_MAX_SPEED_LOCKED = 3;
    /** 锁定档相对未锁定档的最大速度倍率，用于目标失效时的速度档切换 */
    private static final double LOCK_SPEED_RATIO = DEF_MAX_SPEED_LOCKED / DEF_MAX_SPEED;
    /** 击杀连锁弹初速占未锁定档默认速度的比例范围 */
    private static final double KILL_LAUNCH_SPEED_MIN = 0.2;
    private static final double KILL_LAUNCH_SPEED_MAX = 0.5;
    /** 击杀连锁弹初速上叠加的随机散射比例上限（相对未锁定档默认速度） */
    private static final double KILL_SCATTER_MAX = 0.2;
    /** 初始飞出阶段的每 tick 速度衰减系数 */
    private static final double FLY_DECAY = 0.99;
    /** stage 0 起步阶段跳过方块碰撞检测的 tick 数 */
    private static final int STAGE0_SKIP_BLOCK_TICKS = 3;
    /** 初始飞出阶段持续的 tick 数，之后进入追踪阶段 */
    public static final int PREWARM_TICKS = 20;
    private static final float MAX_TRAIL_DIST = 200;
    private static final float TRAIL_STEP = 0.1f;

    /** 目标失效后按当前速度外推的 tick 数范围，所得位置作为新的目标点 */
    private static final int TARGET_LOST_LOOKAHEAD_MIN = 20;
    private static final int TARGET_LOST_LOOKAHEAD_MAX = 40;
    /** 重新搜索可锁定目标的范围 */
    private static final double RELOCK_RANGE = 32;

    /** 距目标点不足该距离（格）首次触发计时 */
    private static final double FUSE_RANGE = 1.0;
    /** 计时累计超过该 tick 数后引爆 */
    private static final int FUSE_ON = 10;
    /** stage=1 后累计该 tick 数，随后 discard；留出余量覆盖 stage 同步延迟，避免尾迹与爆炸光点被实体移除时硬切 */
    private static final int EXPLODE_TICKS = EXPLODE_LIFETIME + 2;
    /** 爆炸框选 AABB 边长（格） */
    private static final double EXPLODE_BOX = 2.0;

    private static final EntityDataAccessor<Vector3f> DATA_TARGET_POS = SynchedEntityData.defineId(MagicMissile.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_TARGET_ENTITY_ID = SynchedEntityData.defineId(MagicMissile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE = SynchedEntityData.defineId(MagicMissile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TRACK_RATE = SynchedEntityData.defineId(MagicMissile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MAX_SPEED = SynchedEntityData.defineId(MagicMissile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(MagicMissile.class, EntityDataSerializers.INT);

    private float trackRate;
    private double maxSpeed;

    @Nullable
    private Entity targetEntity;
    private UUID targetUuid;
    private Vec3 targetPos = Vec3.ZERO;
    protected Level level;

    @Nullable
    public Predicate<Entity> targetSelector;

    /** 服务端私有计时累计变量（fuse 引爆计时与 explode 消失倒计时复用） */
    private int fusedTicks;
    /** 一次性触发标记（服务端 fuse 计时 / 客户端爆炸光点各用一次） */
    private boolean markFlag;

    protected MagicMissile(EntityType<MagicMissile> type, Level level) {
        super(type, level);
        this.level = level;
        noPhysics = true;
        // 尾迹铺在身后，不参与视锥剔除，避免本体出画面时整条尾迹瞬间消失
        noCulling = true;
    }

    private MagicMissile(Level level, Vec3 startPos, Vec3 startVelocity, Vec3 targetPos, float damage, @Nullable Entity targetEntity, @Nullable Entity owner) {
        this(YCEntityTypes.MAGIC_MISSILE.get(), level);
        setPos(startPos);
        setDeltaMovement(startVelocity);
        setTrackRate(DEF_TRACK_RATE);
        setMaxSpeed(DEF_MAX_SPEED);
        setTargetPos(targetPos);
        setTargetEntity(targetEntity);
        setDamage(damage);
        setOwner(owner);
    }

    public MagicMissile(Level level, Vec3 startPos, Vec3 startVelocity, Vec3 targetPos, float damage, @Nullable Entity owner) {
        this(level, startPos, startVelocity, targetPos, damage, null, owner);
    }

    public MagicMissile(Level level, Vec3 startPos, Vec3 startVelocity, Entity targetEntity, float damage, @Nullable Entity owner) {
        this(level, startPos, startVelocity, targetEntity.getBoundingBox().getCenter(), damage, targetEntity, owner);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TARGET_POS, new Vector3f());
        builder.define(DATA_TARGET_ENTITY_ID, 0);
        builder.define(DATA_DAMAGE, 0.0F);
        builder.define(DATA_TRACK_RATE, DEF_TRACK_RATE);
        builder.define(DATA_MAX_SPEED, (float) DEF_MAX_SPEED);
        builder.define(DATA_STAGE, -1);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetUuid != null) {
            tag.putUUID("TargetEntity", targetUuid);
        }
        Vector3f pos = entityData.get(DATA_TARGET_POS);
        tag.putFloat("TargetX", pos.x);
        tag.putFloat("TargetY", pos.y);
        tag.putFloat("TargetZ", pos.z);
        tag.putFloat("Damage", getDamage());
        tag.putFloat("TrackRate", trackRate);
        tag.putFloat("MaxSpeed", (float) maxSpeed);
        tag.putInt("Stage", getStage());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        targetUuid = tag.hasUUID("TargetEntity") ? tag.getUUID("TargetEntity") : null;
        entityData.set(DATA_TARGET_ENTITY_ID, 0);
        entityData.set(DATA_TARGET_POS, new Vector3f(tag.getFloat("TargetX"), tag.getFloat("TargetY"), tag.getFloat("TargetZ")));
        entityData.set(DATA_DAMAGE, tag.getFloat("Damage"));
        setTrackRate(tag.getFloat("TrackRate"));
        setMaxSpeed(tag.getFloat("MaxSpeed"));
        targetPos = getTargetPos();
        targetEntity = null;
        setStage(tag.getInt("Stage"));
    }

    private @Nullable Vec3 lastPos = null;
    private @Nullable Vec3 lastVel = null;

    /** 上一 tick 采样出的内外状态，仅用于判断本 tick 的补点是否需要截出分界 */
    private boolean wasInside;

    /** 尾迹珠：世界坐标 + 生成时的 tick 计数 + 生成时是否埋在方块或流体里 + 生成时的颜色，渲染器按 age 收缩并据此分趟绘制 */
    public record TrailBead(Vec3 pos, long bornAt, boolean inside, Vec3 color) {
    }

    /** 客户端尾迹采样，仅 stage 0 追加，进入爆炸后停止追加以便自然淡出 */
    private final Deque<TrailBead> trail = new ArrayDeque<>();

    public Deque<TrailBead> getTrail() {
        return trail;
    }

    /** 爆炸光点：随机落点 + 初始半宽 + 收缩时长（正比于大小）+ 生成时是否埋在方块或流体里 + 生成时的颜色 + 生成时的 tick 计数 */
    public record ExplodeBead(Vec3 pos, float size, float life, boolean inside, Vec3 color, long bornAt) {
    }

    /** 客户端爆炸光点，爆炸瞬间一次性铺开，之后只按 age 收缩淡出 */
    private final List<ExplodeBead> explosion = new ArrayList<>();

    public List<ExplodeBead> getExplosion() {
        return explosion;
    }

    /** 该点是否埋在方块或流体的实际形状里。vanilla 的 clip 只认“线段真的穿过某个面”，自己原地不动那种射线一律判不中，故这里直接按块内的形状盒子判定 */
    private boolean isInside(Vec3 pos) {
        var bp = BlockPos.containing(pos);
        return inShape(level.getBlockState(bp).getCollisionShape(level, bp), bp, pos)
                || inShape(level.getFluidState(bp).getShape(level, bp), bp, pos);
    }

    /** 形状坐标相对块原点，平移到世界坐标后逐盒判断该点是否落在里面 */
    private static boolean inShape(VoxelShape shape, BlockPos bp, Vec3 pos) {
        if (shape.isEmpty()) return false;
        for (var box : shape.toAabbs()) {
            if (box.move(bp.getX(), bp.getY(), bp.getZ()).contains(pos)) return true;
        }
        return false;
    }

    /**
     * 沿 hermite 曲线补点，并把每颗珠子的内外状态就地钉死，渲染时无需再做任何射线检测。
     * 与上一 tick 状态相同则整段沿用；翻转的这段从外侧那端朝内侧打一条射线取遮挡体表面，离它最近的珠子成为新旧状态的分界。
     */
    private void sampleTrail(Vec3 from, Vec3 fromVel, Vec3 to, Vec3 toVel, boolean inside) {
        // 起点切线取上段速度方向、终点切线取当前速度方向
        var steps = (int) Math.ceil(to.distanceTo(from) / TRAIL_STEP);
        var bornAt = level.getGameTime();
        // 颜色此刻定死，锁定状态之后翻转也不会回头改动已生成的这段尾迹
        var color = isLocked() ? COLOR_LOCKED : COLOR_FREE;
        int boundary = -1;
        if (inside != wasInside) {
            // 从外侧那端朝内侧打射线，命中点即遮挡体表面
            var outsideEnd = inside ? from : to;
            var insideEnd = inside ? to : from;
            var surface = level.clip(new ClipContext(outsideEnd, insideEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (surface.getType() != HitResult.Type.MISS) {
                var hit = surface.getLocation();
                var best = Double.MAX_VALUE;
                for (var i = 0; i < steps; i++) {
                    var dist = MathUtils.hermite(from, fromVel, to, toVel, (float) i / steps).distanceToSqr(hit);
                    if (dist < best) {
                        best = dist;
                        boundary = i;
                    }
                }
            }
        }
        for (var i = 0; i < steps; i++) {
            var pos = MathUtils.hermite(from, fromVel, to, toVel, (float) i / steps);
            trail.add(new TrailBead(pos, bornAt, (boundary < 0 || i >= boundary) ? inside : !inside, color));
        }
    }

    @Override
    public void tick() {
        super.tick();
        int stage = getStage();
        var myPos = getEyePosition();

        switch (stage) {
            case -1 -> {
                // 向初始速度方向飞出并逐 tick 衰减，撞到方块表面时按法线镜面反弹，撞到可碰撞实体则提前爆炸
                Vec3 vel = getDeltaMovement().scale(FLY_DECAY);
                Vec3 start = myPos.subtract(vel.scale(0.5));
                Vec3 end = myPos.add(vel.scale(0.5));
                var entityHit = ProjectileUtil.getEntityHitResult(level, this, start, end, getBoundingBox().expandTowards(vel).inflate(1.0), this::canHitEntity);
                if (entityHit != null) {
                    var target = entityHit.getLocation();
                    setPos(target.x, target.y - getEyeHeight(), target.z);
                    explode();
                    return;
                }
                var blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (blockHit.getType() != HitResult.Type.MISS) {
                    vel = MathUtils.reflect(vel, Vec3.atLowerCornerOf(blockHit.getDirection().getNormal()));
                    var target = blockHit.getLocation();
                    setPos(target.x, target.y - getEyeHeight(), target.z);
                }
                setDeltaMovement(vel);
                move(MoverType.SELF, vel);
                if (!level.isClientSide && tickCount >= PREWARM_TICKS) {
                    setStage(0);
                }
            }
            case 0 -> {
                if (level.isClientSide) {
                    // 每 tick 从同步数据刷新参数，并沿 hermite 曲线补点作为尾迹珠交给渲染器
                    targetPos = getTargetPos();
                    trackRate = getTrackRate();
                    maxSpeed = getMaxSpeed();

                    var newPos = getEyePosition();
                    var newVel = getDeltaMovement();
                    boolean inside = isInside(newPos);
                    if (lastPos != null && newPos.distanceToSqr(lastPos) < MAX_TRAIL_DIST) {
                        var totalDist = newPos.distanceTo(lastPos);
                        if (totalDist < MAX_TRAIL_DIST || totalDist < maxSpeed) {
                            sampleTrail(lastPos, lastVel, newPos, newVel, inside);
                        }
                    }
                    lastPos = newPos;
                    lastVel = newVel;
                    wasInside = inside;
                    trail.removeIf(bead -> level.getGameTime() - bead.bornAt() > TRAIL_LIFETIME);
                } else {
                    if (targetEntity == null) {
                        targetEntity = resolveTargetByUuid();
                    }
                    if (targetEntity != null && (targetEntity.isRemoved() || targetEntity.level() != level
                            || (targetSelector != null && !targetSelector.test(targetEntity)))) {
                        // 先在周围就近改锁到另一个符合 targetSelector 的目标，实在没有才脱锁
                        var replacement = findRelockTarget();
                        if (replacement != null) {
                            setTargetEntity(replacement);
                        } else {
                            // 脱锁：不就地爆炸，改为沿当前速度方向外推一段作为新的目标点，继续飞行
                            setTargetEntity(null);
                            setTargetPos(flybyTarget(level, myPos, getDeltaMovement()));
                            // 按锁定档与未锁定档的比值切换最大速度
                            setMaxSpeed(maxSpeed / LOCK_SPEED_RATIO);
                        }
                    }
                    if (myPos.distanceTo(targetPos) > MAX_TARGET_DISTANCE) {
                        explode();
                        return;
                    }
                    if (targetEntity != null) {
                        entityData.set(DATA_TARGET_ENTITY_ID, targetEntity.getId());
                    }
                }

                if (targetEntity != null) {
                    targetPos = targetEntity.getBoundingBox().getCenter();
                    if (!level.isClientSide) {
                        setTargetPos(targetPos);
                    }
                }
                var targetDist = targetPos.distanceTo(myPos);
                Vec3 toTarget = targetPos.subtract(myPos).normalize().scale(
                        Math.min(maxSpeed, targetDist)
                );
                setDeltaMovement(getDeltaMovement().scale(1.0F - trackRate).add(toTarget.scale(trackRate)));

                // 距目标点首次不足 FUSE_RANGE 开始计时，累计超过 FUSE_ON 则引爆（均为服务端逻辑）
                // 锁定坐标且目标方块无碰撞时跳过等待计时，接近后直接空爆
                if (!level.isClientSide) {
                    if (targetEntity == null && level.getBlockState(BlockPos.containing(targetPos)).getCollisionShape(level, BlockPos.containing(targetPos)).isEmpty()) {
                        if (targetDist < FUSE_RANGE) {
                            explode();
                            return;
                        }
                    } else {
                        if (!markFlag) markFlag = targetDist < FUSE_RANGE;
                        if (markFlag) fusedTicks++;
                        if (fusedTicks > FUSE_ON) {
                            explode();
                            return;
                        }
                    }
                }

                // rayCasts
                Vec3 vel = getDeltaMovement();
                Vec3 start = myPos.subtract(vel.scale(0.5));
                Vec3 end = myPos.add(vel.scale(0.5));
                var entityHit = ProjectileUtil.getEntityHitResult(level, this, start, end, getBoundingBox().expandTowards(vel).inflate(1.0), this::canHitEntity);
                if (entityHit != null) {
                    var target = entityHit.getLocation();
                    setPos(target.x, target.y - getEyeHeight(), target.z);
                    explode();
                    return;
                }
                // 起步阶段不检查方块碰撞，避免刚生成的弹贴脸自爆
                if (tickCount >= STAGE0_SKIP_BLOCK_TICKS) {
                    var blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                    if (blockHit.getType() != HitResult.Type.MISS) {
                        var target = blockHit.getLocation();
                        setPos(target.x, target.y - getEyeHeight(), target.z);
                        explode();
                        return;
                    }
                }

                // move if no hit
                if (trackRate < TRACK_RATE_MAX) setTrackRate(trackRate + TRACK_RATE_INC);
                move(MoverType.SELF, vel);
            }
            case 1 -> {
                if (level.isClientSide) {
                    targetEntity = resolveTargetById();
                    // 客户端检测“刚进入 explode 状态”：先补上最后一 tick 到爆心的这段尾迹，再铺开爆炸光点
                    if (stage == 1 && !markFlag) {
                        markFlag = true;
                        var head = getEyePosition();
                        if (lastPos != null) {
                            sampleTrail(lastPos, lastVel, head, getDeltaMovement(), isInside(head));
                        }
                        spawnExplosionBeads();
                    }
                }

                // stage=1 累计计时，EXPLODE_TICKS tick 后消失（服务端）
                if (!level.isClientSide) {
                    fusedTicks++;
                    if (fusedTicks >= EXPLODE_TICKS) {
                        discard();
                    }
                }
            }
            default -> discard();
        }
    }

    /** 客户端进入 explode 时，在爆炸范围内一次性铺开 30 个随机光点；光点不走路径，内外只能逐点判定 */
    private void spawnExplosionBeads() {
        AABB box = explodeBox();
        var bornAt = level.getGameTime();
        var color = isLocked() ? COLOR_LOCKED : COLOR_FREE;
        var random = level.random;
        for (int i = 0; i < 30; i++) {
            var pos = new Vec3(
                    box.minX + box.getXsize() * random.nextDouble(),
                    box.minY + box.getYsize() * random.nextDouble(),
                    box.minZ + box.getZsize() * random.nextDouble());
            var size = EXPLODE_SIZE_MIN + (float) Math.random() * (EXPLODE_SIZE_MAX - EXPLODE_SIZE_MIN);
            explosion.add(new ExplodeBead(pos, size, EXPLODE_LIFETIME * size / EXPLODE_SIZE_MAX, isInside(pos), color, bornAt));
        }
        var eye = getEyePosition();
        // 爆心再压一个大光点
        explosion.add(new ExplodeBead(eye, EXPLODE_FLASH_SIZE, EXPLODE_FLASH_LIFETIME, isInside(eye), color, bornAt));
        Minecraft.getInstance().particleEngine.createParticle(ParticleTypes.EXPLOSION, eye.x, eye.y, eye.z, 0.0, 0.0, 0.0);
    }

    /**
     * 进入爆炸阶段。设 stage=1，框选周围 EXPLODE_BOX 格边长 AABB 内的所有生物统一造成一次伤害（回避 owner），随后开始消失倒计时。
     */
    private void explode() {
        if (level.isClientSide) {
            return;
        }
        setStage(1);
        fusedTicks = 0;
        var pos = getEyePosition();
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 4.0F, (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);
        AABB area = explodeBox();
        var source = level.damageSources().source(YCDamageTypes.MAGIC_MISSILE.getKey(), this, getOwner());
        float totalHurt = 0;
        for (var e : level.getEntitiesOfClass(Entity.class, area, this::canHitEntity)) {
            if (e instanceof LivingEntity living) {
                var oldHealth = living.getHealth();
                e.hurt(source, getDamage());
                var newHealth = living.getHealth();
                var delta = oldHealth - newHealth;
                totalHurt += delta;
                if (oldHealth > 0 && newHealth <= 0) spawnKillMissile(e.getBoundingBox().getCenter());
            } else e.hurt(source, getDamage());
        }
        if (getOwner() instanceof ServerPlayer player) {
            // looting
            var center = player.getBoundingBox().getCenter();
            for (var loot : level.getEntitiesOfClass(Entity.class, area, MagicMissile::isLoot)) {
                loot.teleportTo(center.x, center.y, center.z);
                loot.addDeltaMovement(player.getDeltaMovement());
            }
            // absorption
            player.setAbsorptionAmount(player.getAbsorptionAmount() + totalHurt);
        }
    }

    /** 击杀目标瞬间，从当前位置向上发射一颗继承属性的新弹：伤害翻倍，最大速度取对应锁定状态的默认值 */
    private void spawnKillMissile(Vec3 pos) {
        var random = level.random;
        // 初速统一按未锁定档默认速度折算：竖直向上占一部分，再叠加一层随机方向的散量
        var upward = DEF_MAX_SPEED * (KILL_LAUNCH_SPEED_MIN + random.nextDouble() * (KILL_LAUNCH_SPEED_MAX - KILL_LAUNCH_SPEED_MIN));
        var scatter = DEF_MAX_SPEED * KILL_SCATTER_MAX * random.nextDouble();
        var dir = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize();
        var vel = new Vec3(0, upward, 0).add(dir.scale(scatter));
        Entity newTarget = targetSelector == null ? null
                : findRandomTargetWithin(level, pos, AABB.ofSize(pos, RELOCK_RANGE * 2, RELOCK_RANGE * 2, RELOCK_RANGE * 2), targetSelector);
        var newTargetPos = newTarget == null ? flybyTarget(level, pos, vel) : newTarget.getBoundingBox().getCenter();
        var missile = new MagicMissile(level, pos, vel, newTargetPos, getDamage() * 2, newTarget, getOwner());
        missile.setMaxSpeed(newTarget == null ? DEF_MAX_SPEED : DEF_MAX_SPEED_LOCKED);
        missile.targetSelector = targetSelector;
        missile.setStage(0);
        level.addFreshEntity(missile);
    }

    /** 是否属于不计伤害、只受传送处理的东西：掉落物与经验球 */
    private static boolean isLoot(Entity e) {
        return e instanceof ItemEntity || e instanceof ExperienceOrb;
    }

    /** 爆炸影响范围：碰撞盒向外扩 EXPLODE_BOX */
    private AABB explodeBox() {
        return getBoundingBox().inflate(EXPLODE_BOX);
    }

    /** 是否已进入 explode 阶段（供渲染器判断不再渲染模型） */
    public boolean isExploding() {
        return getStage() == 1;
    }

    /** 是否处于初始飞出阶段（供渲染器判断只绘制内层） */
    public boolean isLaunching() {
        return getStage() == -1;
    }

    /** 是否锁定目标实体（客户端依据同步的实体 id 判断） */
    public boolean isLocked() {
        return targetEntity != null || entityData.get(DATA_TARGET_ENTITY_ID) != 0;
    }

    /**
     * 默认渲染距离按碰撞盒大小缩放（0.5 边长方块远比怪物近），
     * 改为始终渲染，保证远距离可见。
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (isLoot(target)) return false;
        if (target instanceof LivingEntity living) {
            if (living.isDeadOrDying()) return false;
        }
        var owner = getOwner();
        if (target instanceof Projectile other) return !Objects.equals(owner, other.getOwner());
        return !Objects.equals(owner, target);
    }

    public void setStage(int s) {
        entityData.set(DATA_STAGE, s);
    }

    public int getStage() {
        return entityData.get(DATA_STAGE);
    }

    public void setTargetPos(Vec3 pos) {
        targetPos = pos;
        entityData.set(DATA_TARGET_POS, new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
    }

    public Vec3 getTargetPos() {
        Vector3f pos = entityData.get(DATA_TARGET_POS);
        return new Vec3(pos.x, pos.y, pos.z);
    }

    public void setTargetEntity(@Nullable Entity target) {
        targetEntity = target;
        targetUuid = target == null ? null : target.getUUID();
        entityData.set(DATA_TARGET_ENTITY_ID, target == null ? 0 : target.getId());
    }

    @Nullable
    public Entity resolveTargetByUuid() {
        if (targetUuid != null && level instanceof ServerLevel serverLevel) {
            return serverLevel.getEntity(targetUuid);
        }
        return null;
    }

    @Nullable
    public Entity resolveTargetById() {
        int id = entityData.get(DATA_TARGET_ENTITY_ID);
        return id == 0 ? null : level.getEntity(id);
    }

    /** 在自身周围 RELOCK_RANGE 格内搜离得最近的一个符合 targetSelector 的实体；无选择器或无候选时返回 null */
    @Nullable
    private Entity findRelockTarget() {
        if (targetSelector == null) return null;
        return findNearestTarget(level, getEyePosition(), getBoundingBox().inflate(RELOCK_RANGE), targetSelector);
    }

    /** 目标失效时的外推落点：当前位置沿当前速度外推一段随机 tick 数 */
    private static Vec3 flybyTarget(Level level, Vec3 pos, Vec3 vel) {
        var ticks = TARGET_LOST_LOOKAHEAD_MIN + level.random.nextInt(TARGET_LOST_LOOKAHEAD_MAX - TARGET_LOST_LOOKAHEAD_MIN + 1);
        return pos.add(vel.scale(ticks));
    }

    /** 在给定范围内搜离 from 最近的一个符合 selector 的实体；无候选时返回 null */
    @Nullable
    private static Entity findNearestTarget(Level level, Vec3 from, AABB area, Predicate<Entity> selector) {
        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (var e : level.getEntitiesOfClass(Entity.class, area, selector)) {
            var dist = e.getBoundingBox().getCenter().distanceToSqr(from);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = e;
            }
        }
        return nearest;
    }
    @Nullable
    private static Entity findRandomTargetWithin(Level level, Vec3 from, AABB area, Predicate<Entity> selector) {
        var pool = level.getEntitiesOfClass(Entity.class, area, selector);
        if (pool.isEmpty()) return null;
        return pool.get((int) (Math.random() * pool.size()));
    }

    public void setDamage(float damage) {
        entityData.set(DATA_DAMAGE, damage);
    }

    public float getDamage() {
        return entityData.get(DATA_DAMAGE);
    }

    public void setTrackRate(float rate) {
        trackRate = rate;
        entityData.set(DATA_TRACK_RATE, rate);
    }

    public void setMaxSpeed(double speed) {
        maxSpeed = speed;
        entityData.set(DATA_MAX_SPEED, (float) speed);
    }

    public float getTrackRate() {
        return entityData.get(DATA_TRACK_RATE);
    }

    public double getMaxSpeed() {
        return entityData.get(DATA_MAX_SPEED);
    }
}