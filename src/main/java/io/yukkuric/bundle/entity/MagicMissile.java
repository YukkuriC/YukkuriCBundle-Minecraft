package io.yukkuric.bundle.entity;

import io.yukkuric.bundle.damage.YCDamageTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public class MagicMissile extends Projectile {
    public static final String ID = "magic_missile";
    public static final double MAX_TARGET_DISTANCE = 1000.0;
    /** 锁定目标实体时的颜色 */
    public static final Vec3 COLOR_LOCKED = Vec3.fromRGB24(0x87CEEB);
    /** 未锁定目标实体时的颜色 */
    public static final Vec3 COLOR_FREE = Vec3.fromRGB24(0xFFFFFF);

    private static final float DEF_TRACK_RATE = 0.05F;
    private static final float TRACK_RATE_INC = 0.01F;
    private static final float TRACK_RATE_MAX = 0.5F;
    private static final double DEF_MAX_SPEED = 3;

    /** 距目标点不足该距离（格）首次触发计时 */
    private static final double FUSE_RANGE = 1.0;
    /** 计时累计超过该 tick 数后引爆 */
    private static final int FUSE_ON = 10;
    /** stage=1 后累计该 tick 数，随后 discard */
    private static final int EXPLODE_TICKS = 20;
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

    /** 服务端私有计时累计变量（fuse 引爆计时与 explode 消失倒计时复用） */
    private int fusedTicks;
    /** 一次性触发标记（服务端 fuse 计时 / 客户端 explode 粒子各用一次） */
    private boolean markFlag;

    protected MagicMissile(EntityType<MagicMissile> type, Level level) {
        super(type, level);
        this.level = level;
        noPhysics = true;
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
        builder.define(DATA_STAGE, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetUuid != null) {
            tag.putUUID("TargetEntity", targetUuid);
        }
        Vector3f pos = getEntityData().get(DATA_TARGET_POS);
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
        getEntityData().set(DATA_TARGET_ENTITY_ID, 0);
        getEntityData().set(DATA_TARGET_POS, new Vector3f(tag.getFloat("TargetX"), tag.getFloat("TargetY"), tag.getFloat("TargetZ")));
        getEntityData().set(DATA_DAMAGE, tag.getFloat("Damage"));
        setTrackRate(tag.getFloat("TrackRate"));
        setMaxSpeed(tag.getFloat("MaxSpeed"));
        targetPos = getTargetPos();
        targetEntity = null;
        setStage(tag.getInt("Stage"));
    }

    @Override
    public void tick() {
        super.tick();
        int stage = getStage();
        var myPos = getEyePosition();

        switch (stage) {
            case 0 -> {
                if (level.isClientSide) {
                    // 每 tick 从同步数据刷新参数并喷射粒子
                    targetPos = getTargetPos();
                    trackRate = getTrackRate();
                    maxSpeed = getMaxSpeed();

                    Vec3 velDir = getDeltaMovement();
                    var centerBox = AABB.ofSize(myPos, 0.1, 0.1, 0.1);
                    Vec3 sparkColor = isLocked() ? COLOR_LOCKED : COLOR_FREE;
                    for (int i = 0; i < 5; i++) {
                        spawnParticle(ParticleTypes.ELECTRIC_SPARK, centerBox, velDir, sparkColor);
                    }
                } else {
                    if (targetEntity == null) {
                        targetEntity = resolveTargetByUuid();
                    }
                    if (targetEntity != null && (targetEntity.isRemoved() || targetEntity.level() != level)) {
                        explode();
                        return;
                    }
                    if (myPos.distanceTo(targetPos) > MAX_TARGET_DISTANCE) {
                        explode();
                        return;
                    }
                    if (targetEntity != null) {
                        getEntityData().set(DATA_TARGET_ENTITY_ID, targetEntity.getId());
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
                var blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (blockHit.getType() != HitResult.Type.MISS) {
                    var target = blockHit.getLocation();
                    setPos(target.x, target.y - getEyeHeight(), target.z);
                    explode();
                    return;
                }

                // move if no hit
                if (trackRate < TRACK_RATE_MAX) setTrackRate(trackRate + TRACK_RATE_INC);
                move(MoverType.SELF, vel);
            }
            case 1 -> {
                if (level.isClientSide) {
                    targetEntity = resolveTargetById();
                    // 客户端检测“刚进入 explode 状态”，喷发一次爆炸粒子
                    if (stage == 1 && !markFlag) {
                        markFlag = true;
                        spawnExplosionParticles();
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

    private ParticleEngine _cachedEngine = null;

    private Particle spawnParticle(ParticleOptions particle, AABB box, @Nullable Vec3 randDir, @Nullable Vec3 overrideColor) {
        var particleEngine = _cachedEngine == null ? (_cachedEngine = Minecraft.getInstance().particleEngine) : _cachedEngine;
        var random = level.random;
        double x = box.minX + box.getXsize() * random.nextDouble();
        double y = box.minY + box.getYsize() * random.nextDouble();
        double z = box.minZ + box.getZsize() * random.nextDouble();
        Vec3 offset = randDir == null ? Vec3.ZERO : randDir.scale(random.nextDouble() - 0.5);
        var spawned = particleEngine.createParticle(particle, x + offset.x, y + offset.y, z + offset.z, 0.0, 0.0, 0.0);
        if (overrideColor != null) {
            spawned.setColor((float) overrideColor.x, (float) overrideColor.y, (float) overrideColor.z);
        }
        return spawned;
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
        for (var e : level.getEntitiesOfClass(Entity.class, area, this::canHitEntity)) {
            e.hurt(source, getDamage());
        }
    }

    /** 爆炸影响范围：以碰撞盒为中心向外扩大到 EXPLODE_BOX 格边长 */
    private AABB explodeBox() {
        return getBoundingBox().inflate(
                (EXPLODE_BOX - getBbWidth()) / 2.0,
                (EXPLODE_BOX - getBbHeight()) / 2.0,
                (EXPLODE_BOX - getBbWidth()) / 2.0);
    }

    /** 客户端进入 explode 时，在爆炸范围内一次性喷发 30 个随机 electric spark 粒子 */
    private void spawnExplosionParticles() {
        AABB box = explodeBox();
        Vec3 color = isLocked() ? COLOR_LOCKED : COLOR_FREE;
        for (int i = 0; i < 30; i++) {
            spawnParticle(ParticleTypes.END_ROD, box, null, color);
        }
        spawnParticle(ParticleTypes.EXPLOSION, getBoundingBox(), null, null);
        spawnParticle(ParticleTypes.SONIC_BOOM, getBoundingBox(), null, null);
    }

    /** 是否已进入 explode 阶段（供渲染器判断不再渲染模型） */
    public boolean isExploding() {
        return getStage() == 1;
    }

    /** 是否锁定目标实体（客户端依据同步的实体 id 判断） */
    public boolean isLocked() {
        return targetEntity != null || getEntityData().get(DATA_TARGET_ENTITY_ID) != 0;
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
        if (target instanceof ItemEntity || target instanceof ExperienceOrb) return false;
        var owner = getOwner();
        if (target instanceof Projectile other) return !Objects.equals(owner, other.getOwner());
        return !Objects.equals(owner, target);
    }

    private void setStage(int s) {
        getEntityData().set(DATA_STAGE, s);
    }

    private int getStage() {
        return getEntityData().get(DATA_STAGE);
    }

    private void setTargetPos(Vec3 pos) {
        targetPos = pos;
        getEntityData().set(DATA_TARGET_POS, new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
    }

    private Vec3 getTargetPos() {
        Vector3f pos = getEntityData().get(DATA_TARGET_POS);
        return new Vec3(pos.x, pos.y, pos.z);
    }

    private void setTargetEntity(@Nullable Entity target) {
        targetEntity = target;
        targetUuid = target == null ? null : target.getUUID();
        getEntityData().set(DATA_TARGET_ENTITY_ID, target == null ? 0 : target.getId());
    }

    @Nullable
    private Entity resolveTargetByUuid() {
        if (targetUuid != null && level instanceof ServerLevel serverLevel) {
            return serverLevel.getEntity(targetUuid);
        }
        return null;
    }

    @Nullable
    private Entity resolveTargetById() {
        int id = getEntityData().get(DATA_TARGET_ENTITY_ID);
        return id == 0 ? null : level.getEntity(id);
    }

    private void setDamage(float damage) {
        getEntityData().set(DATA_DAMAGE, damage);
    }

    private float getDamage() {
        return getEntityData().get(DATA_DAMAGE);
    }

    private void setTrackRate(float rate) {
        trackRate = rate;
        getEntityData().set(DATA_TRACK_RATE, rate);
    }

    private void setMaxSpeed(double speed) {
        maxSpeed = speed;
        getEntityData().set(DATA_MAX_SPEED, (float) speed);
    }

    private float getTrackRate() {
        return getEntityData().get(DATA_TRACK_RATE);
    }

    private double getMaxSpeed() {
        return getEntityData().get(DATA_MAX_SPEED);
    }
}