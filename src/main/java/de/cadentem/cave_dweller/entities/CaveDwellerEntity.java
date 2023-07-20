package de.cadentem.cave_dweller.entities;

import de.cadentem.cave_dweller.entities.goals.*;
import de.cadentem.cave_dweller.network.CaveSound;
import de.cadentem.cave_dweller.network.NetworkHandler;
import de.cadentem.cave_dweller.registry.ModSounds;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType;
import software.bernie.geckolib3.core.builder.RawAnimation;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.util.GeckoLibUtil;

import java.util.Random;

public class CaveDwellerEntity extends Monster implements IAnimatable {
    private final AnimationFactory factory = GeckoLibUtil.createFactory(this);

    public int reRollResult = 3;
    public boolean isAggro;
    public boolean fakeSize = false;
    private boolean inTwoBlockSpace = false;
    public boolean spottedByPlayer = false;
    public boolean squeezeCrawling = false;
    public boolean isFleeing;
    public boolean startedMovingChase = false;
    private int ticksTillRemove;

    private final RawAnimation OLD_RUN = new RawAnimation("animation.cave_dweller.run", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation IDLE = new RawAnimation("animation.cave_dweller.idle", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation CHASE = new RawAnimation("animation.cave_dweller.new_run", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation CHASE_IDLE = new RawAnimation("animation.cave_dweller.run_idle", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation CROUCH_RUN = new RawAnimation("animation.cave_dweller.crouch_run_new", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation CROUCH_IDLE = new RawAnimation("animation.cave_dweller.crouch_idle", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation CALM_RUN = new RawAnimation("animation.cave_dweller.calm_move", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation CALM_STILL = new RawAnimation("animation.cave_dweller.calm_idle", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation IS_SPOTTED = new RawAnimation("animation.cave_dweller.spotted", ILoopType.EDefaultLoopTypes.HOLD_ON_LAST_FRAME);
    private final RawAnimation CRAWL = new RawAnimation("animation.cave_dweller.crawl", ILoopType.EDefaultLoopTypes.HOLD_ON_LAST_FRAME);
    private final RawAnimation FLEE = new RawAnimation("animation.cave_dweller.flee", ILoopType.EDefaultLoopTypes.LOOP);

    public static final EntityDataAccessor<Boolean> FLEEING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CROUCHING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> AGGRO_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> SQUEEZING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> SPOTTED_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CLIMBING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);

    private final float twoBlockSpaceCooldown;
    private float twoBlockSpaceTimer = 0.0F;
    private int chaseSoundClock = 0;
    private boolean alreadyPlayedFleeSound = false;
    private boolean alreadyPlayedSpottedSound = false;
    private boolean startedPlayingChaseSound = false;
    private boolean alreadyPlayedDeathSound = false;

    public CaveDwellerEntity(final EntityType<? extends CaveDwellerEntity> entityType, final Level level) {
        super(entityType, level);
        this.maxUpStep = 1.0F;
        this.refreshDimensions();
        this.twoBlockSpaceCooldown = 5.0F;
        this.ticksTillRemove = 6000; // TODO :: Config option
    }

    public static AttributeSupplier.Builder getAttributeBuilder() {
        return CaveDwellerEntity.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ATTACK_SPEED, 0.35)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 100.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(FLEEING_ACCESSOR, false);
        this.entityData.define(CROUCHING_ACCESSOR, false);
        this.entityData.define(AGGRO_ACCESSOR, false);
        this.entityData.define(SQUEEZING_ACCESSOR, false);
        this.entityData.define(SPOTTED_ACCESSOR, false);
        this.entityData.define(CLIMBING_ACCESSOR, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new CaveDwellerStareGoal(this, 100.0F));
        this.goalSelector.addGoal(1, new CaveDwellerChaseGoal(this,  0.85F, true, 20.0F));
        this.goalSelector.addGoal(1, new CaveDwellerFleeGoal(this, 20.0F, 1.0));
        this.goalSelector.addGoal(1, new CaveDwellerStrollGoal(this, 0.7));
        this.goalSelector.addGoal(1, new CaveDwellerBreakInvisGoal(this));
        this.targetSelector.addGoal(1, new CaveDwellerTargetTooCloseGoal(this, 12.0F));
        this.targetSelector.addGoal(2, new CaveDwellerTargetSeesMeGoal(this));
    }

    // TODO :: getStandingEyeHeight

    public Vec3 generatePos(final Entity player) {
        Vec3 playerPos = player.position();
        Random rand = new Random();
        double randX = rand.nextInt(70) - 35;
        double randZ = rand.nextInt(70) - 35;
        double posX = playerPos.x + randX;
        double posY = playerPos.y + 10.0;
        double posZ = playerPos.z + randZ;

        for (int runFor = 100; runFor >= 0; --posY) {
            BlockPos blockPosition = new BlockPos(posX, posY, posZ);
            BlockPos blockPosition2 = new BlockPos(posX, posY + 1.0, posZ);
            BlockPos blockPosition3 = new BlockPos(posX, posY + 2.0, posZ);
            BlockPos blockPosition4 = new BlockPos(posX, posY - 1.0, posZ);
            --runFor;

            if (!this.level.getBlockState(blockPosition).getMaterial().blocksMotion()
                    && !this.level.getBlockState(blockPosition2).getMaterial().blocksMotion()
                    && !this.level.getBlockState(blockPosition3).getMaterial().blocksMotion()
                    && this.level.getBlockState(blockPosition4).getMaterial().blocksMotion()) {
                break;
            }
        }

        return new Vec3(posX, posY, posZ);
    }

    @Override
    public void tick() {
        --this.ticksTillRemove;

        if (this.ticksTillRemove <= 0) {
            this.discard();
        }

        if (this.goalSelector.getAvailableGoals().isEmpty()) {
            registerGoals();
            this.goalSelector.tick();
        }

        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(this.position().x, this.position().y + 2.0, this.position().z);
        BlockState above = this.level.getBlockState(blockpos$mutableblockpos);
        boolean blocksMotion = above.getMaterial().blocksMotion();

        if (blocksMotion) {
            this.twoBlockSpaceTimer = this.twoBlockSpaceCooldown;
            this.inTwoBlockSpace = true;
        } else {
            --this.twoBlockSpaceTimer;

            if (this.twoBlockSpaceTimer <= 0.0F) {
                this.inTwoBlockSpace = false;
            }
        }

        if (this.isAggro || this.isFleeing) {
            this.spottedByPlayer = false;
            this.entityData.set(SPOTTED_ACCESSOR, false);
        }

        super.tick();

        this.entityData.set(CROUCHING_ACCESSOR, this.inTwoBlockSpace);

        if (this.entityData.get(SPOTTED_ACCESSOR)) {
            this.playSpottedSound();
        }

        if (!this.level.isClientSide) {
            this.setClimbing(this.horizontalCollision);
        }
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull final Pose pose) {
        if (this.isAggro) {
            return this.fakeSize ? new EntityDimensions(0.5F, 0.9F, true) : new EntityDimensions(0.5F, 1.9F, true);
        } else {
            return new EntityDimensions(0.5F, 1.9F, true);
        }
    }

    private boolean isMoving() {
        Vec3 velocity = this.getDeltaMovement();
        float avgVelocity = (float) (Math.abs(velocity.x) + Math.abs(velocity.z)) / 2.0F;

        return avgVelocity > 0.03F;
    }

    public void reRoll() {
        this.reRollResult = this.random.nextInt(3);
    }

    public Path createShortPath(final LivingEntity target) {
        this.fakeSize = true;
        this.refreshDimensions();
        Path shortPath = this.getNavigation().createPath(target, 0);
        this.fakeSize = false;
        this.refreshDimensions();
        return shortPath;
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbing();
    }

    public boolean isClimbing() {
        return this.entityData.get(CLIMBING_ACCESSOR);
    }

    public void setClimbing(boolean isClimbing) {
        this.entityData.set(CLIMBING_ACCESSOR, isClimbing);
    }

    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull final Level level) {
        return new WallClimberNavigation(this, level);
    }

    private PlayState predicate(final AnimationEvent<CaveDwellerEntity> event) {
        AnimationBuilder builder = new AnimationBuilder();
        AnimationController<CaveDwellerEntity> controller = event.getController();

        if (this.entityData.get(AGGRO_ACCESSOR)) {
            if (this.entityData.get(SQUEEZING_ACCESSOR)) {
                // Squeezing
                builder.addAnimation(CRAWL.animationName, CRAWL.loopType);
            } else if (this.entityData.get(CROUCHING_ACCESSOR)) {
                // Crouching
                if (event.isMoving()) {
                    builder.addAnimation(CROUCH_RUN.animationName, CROUCH_RUN.loopType);
                } else {
                    builder.addAnimation(CROUCH_IDLE.animationName, CROUCH_IDLE.loopType);
                }
            } else {
                // Chase
                if (event.isMoving()) {
                    builder.addAnimation(CHASE.animationName, CHASE.loopType);
                } else {
                    builder.addAnimation(CHASE_IDLE.animationName, CHASE_IDLE.loopType);
                }
            }
        } else if (this.entityData.get(FLEEING_ACCESSOR)) {
            // Fleeing
            if (event.isMoving()) {
                builder.addAnimation(FLEE.animationName, FLEE.loopType);
            } else {
                builder.addAnimation(CHASE_IDLE.animationName, CHASE_IDLE.loopType);
            }
        } else if (this.entityData.get(SPOTTED_ACCESSOR)) {
            // Spotted
            builder.addAnimation(IS_SPOTTED.animationName, IS_SPOTTED.loopType);
        } else {
            // Normal
            if (event.isMoving()) {
                builder.addAnimation(CALM_RUN.animationName, CALM_RUN.loopType);
            } else {
                builder.addAnimation(CALM_STILL.animationName, CALM_STILL.loopType);
            }
        }

        controller.setAnimation(builder);
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(final AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 3, this::predicate));
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pPos, @NotNull BlockState pState) {
        super.playStepSound(pPos, pState);
        this.playEntitySound(this.chooseStep());
    }

    private void playEntitySound(SoundEvent soundEvent) {
        this.playEntitySound(soundEvent, 1.0F, 1.0F);
    }

    private void playEntitySound(SoundEvent soundEvent, float volume, float pitch) {
        this.level.playSound(null, this, soundEvent, SoundSource.HOSTILE, volume, pitch);
    }

    // TODO :: Is this needed? Why not just playEntitySound
    private void playBlockPosSound(final ResourceLocation soundResource, float volume, float pitch) {
        if (this.level instanceof ServerLevel serverLevel) {
            int radius = 10; // blocks
            serverLevel.getPlayers(player -> player.distanceToSqr(this) <= radius * radius).forEach(player -> NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CaveSound(soundResource, player.blockPosition(), volume, pitch)));
        }
    }

    public void playChaseSound() {
        if (this.startedPlayingChaseSound || this.isMoving()) {
            if (this.chaseSoundClock <= 0) {
                Random rand = new Random();

                switch (rand.nextInt(4)) {
                    case 0 -> this.playEntitySound(ModSounds.CHASE_1.get(), 3.0F, 1.0F);
                    case 1 -> this.playEntitySound(ModSounds.CHASE_2.get(), 3.0F, 1.0F);
                    case 2 -> this.playEntitySound(ModSounds.CHASE_3.get(), 3.0F, 1.0F);
                    case 3 -> this.playEntitySound(ModSounds.CHASE_4.get(), 3.0F, 1.0F);
                }

                this.startedPlayingChaseSound = true;
                this.resetChaseSoundClock();
            }

            --this.chaseSoundClock;
        }
    }

    public void playDisappearSound() {
        this.playBlockPosSound(ModSounds.DISAPPEAR.get().getLocation(), 3.0F, 1.0F);
    }

    public void playFleeSound() {
        if (!this.alreadyPlayedFleeSound) {
            Random rand = new Random();

            switch (rand.nextInt(2)) {
                case 0 -> this.playEntitySound(ModSounds.FLEE_1.get(), 3.0F, 1.0F);
                case 1 -> this.playEntitySound(ModSounds.FLEE_2.get(), 3.0F, 1.0F);
            }

            this.alreadyPlayedFleeSound = true;
        }
    }

    private void playSpottedSound() {
        if (!this.alreadyPlayedSpottedSound) {
            this.playEntitySound(ModSounds.SPOTTED.get(), 3.0F, 1.0F);
            this.alreadyPlayedSpottedSound = true;
        }
    }

    private void resetChaseSoundClock() {
        this.chaseSoundClock = Utils.secondsToTicks(5);
    }

    private SoundEvent chooseStep() {
        Random rand = new Random();

        return switch (rand.nextInt(4)) {
            case 1 -> ModSounds.CHASE_STEP_2.get();
            case 2 -> ModSounds.CHASE_STEP_3.get();
            case 3 -> ModSounds.CHASE_STEP_4.get();
            default -> ModSounds.CHASE_STEP_1.get();
        };
    }

    private SoundEvent chooseHurtSound() {
        Random rand = new Random();

        return switch (rand.nextInt(4)) {
            case 1 -> ModSounds.DWELLER_HURT_2.get();
            case 2 -> ModSounds.DWELLER_HURT_3.get();
            case 3 -> ModSounds.DWELLER_HURT_4.get();
            default -> ModSounds.DWELLER_HURT_1.get();
        };
    }

    @Override
    protected void playHurtSound(@NotNull final DamageSource pSource) {
        SoundEvent soundevent = this.chooseHurtSound();
        this.playEntitySound(soundevent, 2.0F, 1.0F);
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();

        if (!this.alreadyPlayedDeathSound) {
            this.playBlockPosSound(ModSounds.DWELLER_DEATH.get().getLocation(), 2.0F, 1.0F);
            this.alreadyPlayedDeathSound = true;
        }
    }

    public boolean isLookingAtMe(final Entity pendingTarget) {
        if (pendingTarget == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();

        boolean yawPlayerLookingTowards = false;

        float fov = (float) minecraft.options.fov().get();
        float yFovMod = 0.65F;
        float fovMod = (35.0F / fov - 1.0F) * 0.4F + 1.0F;
        fov *= fovMod;

        Vec3 targetPosition = pendingTarget.position();
        Vec3 caveDwellerPosition = this.position();
        Vec2 distance = new Vec2((float) caveDwellerPosition.x - (float) targetPosition.x, (float) caveDwellerPosition.z - (float) targetPosition.z);
        distance = distance.normalized();

        double newAngle = Math.toDegrees(Math.atan2(distance.x, distance.y));
        float lookX = (float) pendingTarget.getViewVector(1.0F).x;
        float lookZ = (float) pendingTarget.getViewVector(1.0F).z;
        double newLookAngle = Math.toDegrees(Math.atan2(lookX, lookZ));
        double newNewAngle = this.loopAngle(newAngle - newLookAngle) + (double) fov;
        newNewAngle = this.loopAngle(newNewAngle);

        if (newNewAngle > 0.0 && newNewAngle < (double) (fov * 2.0F)) {
            yawPlayerLookingTowards = true;
        }

        boolean pitchPlayerLookingTowards = false;
        boolean shouldOnlyUsePitch = false;
        float yFov = fov * yFovMod;
        Vec2 yDistance = new Vec2(
                (float) Math.sqrt((caveDwellerPosition.x - targetPosition.x) * (caveDwellerPosition.x - targetPosition.x) + (caveDwellerPosition.z - targetPosition.z) * (caveDwellerPosition.z - targetPosition.z)),
                (float) (caveDwellerPosition.y - targetPosition.y)
        );
        yDistance = yDistance.normalized();

        double yAngle = Math.toDegrees(Math.atan2(yDistance.x, yDistance.y));
        float lookY = (float) pendingTarget.getViewVector(1.0F).y;
        Vec2 lookDist = new Vec2((float) Math.sqrt(lookX * lookX + lookZ * lookZ), lookY);
        lookDist = lookDist.normalized();

        double yLookAngle = Math.toDegrees(Math.atan2(lookDist.x, lookDist.y));
        double newYAngle = this.loopAngle(yAngle - yLookAngle) + (double) yFov;
        newYAngle = this.loopAngle(newYAngle);

        if (newYAngle > 0.0 && newYAngle < (double) (yFov * 2.0F)) {
            pitchPlayerLookingTowards = true;
        }

        if (!(yLookAngle < (double) (180.0F - yFov)) || !(yLookAngle > (double) yFov)) {
            shouldOnlyUsePitch = true;
        }

        return (yawPlayerLookingTowards || shouldOnlyUsePitch) && pitchPlayerLookingTowards;
    }

    private double loopAngle(double angle) {
        if (angle > 360.0) {
            return angle - 360.0;
        } else {
            return angle < 0.0 ? angle + 360.0 : angle;
        }
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull final DamageSource damageSourceIn) {
        return this.chooseHurtSound();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DWELLER_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }
}