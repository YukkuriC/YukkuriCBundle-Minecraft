package io.yukkuric.bundle.entity;

import io.yukkuric.bundle.damage.YCDamageTypes;
import io.yukkuric.bundle.utils.MathUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
    private static final double DEF_MAX_SPEED = 2;
    /** 初始飞出阶段的每 tick 速度衰减系数 */
    private static final double FLY_DECAY = 0.99;
    /** 初始飞出阶段持续的 tick 数，之后进入追踪阶段 */
    private static final int FLY_TICKS = 20;
    private static final float MAX_TRAIL_DIST = 20;
    private static final float TRAIL_STEP = 0.1f;

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

    private @Nullable Vec3 lastPos = null;
    private @Nullable Vec3 lastVel = null;

    /** 上一 tick 采样出的内外状态，仅用于判断本 tick 的补点是否需要截出分界 */
    private boolean wasInside;

    /** 尾迹珠：世界坐标 + 生成时的 tick 计数 + 生成时是否埋在方块或流体里，渲染器按 age 收缩并据此分趟绘制 */
    public record TrailBead(Vec3 pos, long bornAt, boolean inside) {}

    /** 客户端尾迹采样，仅 stage 0 追加，进入爆炸后停止追加以便自然淡出 */
    private final Deque<TrailBead> trail = new ArrayDeque<>();

    public Deque<TrailBead> getTrail() {
        return trail;
    }

    /** 爆炸光点：随机落点 + 初始半宽 + 收缩时长（正比于大小）+ 生成时是否埋在方块或流体里 + 生成时的 tick 计数 */
    public record ExplodeBead(Vec3 pos, float size, float life, boolean inside, long bornAt) {}

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
            trail.add(new TrailBead(pos, bornAt, (boundary < 0 || i >= boundary) ? inside : !inside));
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
                if (!level.isClientSide && tickCount >= FLY_TICKS) {
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
        var random = level.random;
        for (int i = 0; i < 30; i++) {
            var pos = new Vec3(
                    box.minX + box.getXsize() * random.nextDouble(),
                    box.minY + box.getYsize() * random.nextDouble(),
                    box.minZ + box.getZsize() * random.nextDouble());
            var size = EXPLODE_SIZE_MIN + (float) Math.random() * (EXPLODE_SIZE_MAX - EXPLODE_SIZE_MIN);
            explosion.add(new ExplodeBead(pos, size, EXPLODE_LIFETIME * size / EXPLODE_SIZE_MAX, isInside(pos), bornAt));
        }
        var eye = getEyePosition();
        // 爆心再压一个大光点
        explosion.add(new ExplodeBead(eye, EXPLODE_FLASH_SIZE, EXPLODE_FLASH_LIFETIME, isInside(eye), bornAt));
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

    public void setStage(int s) {
        getEntityData().set(DATA_STAGE, s);
    }

    public int getStage() {
        return getEntityData().get(DATA_STAGE);
    }

    public void setTargetPos(Vec3 pos) {
        targetPos = pos;
        getEntityData().set(DATA_TARGET_POS, new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
    }

    public Vec3 getTargetPos() {
        Vector3f pos = getEntityData().get(DATA_TARGET_POS);
        return new Vec3(pos.x, pos.y, pos.z);
    }

    public void setTargetEntity(@Nullable Entity target) {
        targetEntity = target;
        targetUuid = target == null ? null : target.getUUID();
        getEntityData().set(DATA_TARGET_ENTITY_ID, target == null ? 0 : target.getId());
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
        int id = getEntityData().get(DATA_TARGET_ENTITY_ID);
        return id == 0 ? null : level.getEntity(id);
    }

    public void setDamage(float damage) {
        getEntityData().set(DATA_DAMAGE, damage);
    }

    public float getDamage() {
        return getEntityData().get(DATA_DAMAGE);
    }

    public void setTrackRate(float rate) {
        trackRate = rate;
        getEntityData().set(DATA_TRACK_RATE, rate);
    }

    public void setMaxSpeed(double speed) {
        maxSpeed = speed;
        getEntityData().set(DATA_MAX_SPEED, (float) speed);
    }

    public float getTrackRate() {
        return getEntityData().get(DATA_TRACK_RATE);
    }

    public double getMaxSpeed() {
        return getEntityData().get(DATA_MAX_SPEED);
    }
}