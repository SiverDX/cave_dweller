package de.cadentem.cave_dweller.entities;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.goals.*;
import de.cadentem.cave_dweller.network.CaveSound;
import de.cadentem.cave_dweller.network.NetworkHandler;
import de.cadentem.cave_dweller.registry.ModSounds;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation.LoopType;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.Random;

public class CaveDwellerEntity extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final RawAnimation CHASE = RawAnimation.begin().then("animation.cave_dweller.new_run", LoopType.LOOP);
    private final RawAnimation CHASE_IDLE = RawAnimation.begin().then("animation.cave_dweller.run_idle", LoopType.LOOP);
    private final RawAnimation CROUCH_RUN = RawAnimation.begin().then("animation.cave_dweller.crouch_run_new", LoopType.LOOP);
    private final RawAnimation CROUCH_IDLE = RawAnimation.begin().then("animation.cave_dweller.crouch_idle", LoopType.LOOP);
    private final RawAnimation CALM_RUN = RawAnimation.begin().then("animation.cave_dweller.calm_move", LoopType.LOOP);
    private final RawAnimation CALM_STILL = RawAnimation.begin().then("animation.cave_dweller.calm_idle", LoopType.LOOP);
    private final RawAnimation IS_SPOTTED = RawAnimation.begin().then("animation.cave_dweller.spotted", LoopType.HOLD_ON_LAST_FRAME);
    private final RawAnimation CRAWL = RawAnimation.begin().then("animation.cave_dweller.crawl", LoopType.LOOP);
    private final RawAnimation CRAWL_END = RawAnimation.begin().then("animation.cave_dweller.crawl_end", LoopType.HOLD_ON_LAST_FRAME);
    private final RawAnimation FLEE = RawAnimation.begin().then("animation.cave_dweller.flee", LoopType.LOOP);

    public static final EntityDataAccessor<Boolean> FLEEING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CROUCHING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CRAWLING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> SPOTTED_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CLIMBING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);

    private final float twoBlockSpaceCooldown;

    public Roll currentRoll = Roll.STROLL;
    public boolean isFleeing;
    public boolean pleaseStopMoving;
    public boolean targetIsLookingAtMe;

    private float twoBlockSpaceTimer;
    private int ticksTillRemove;
    private int chaseSoundClock;
    private boolean inTwoBlockSpace;
    private boolean alreadyPlayedFleeSound;
    private boolean alreadyPlayedSpottedSound;
    private boolean startedPlayingChaseSound;
    private boolean alreadyPlayedDeathSound;

    public CaveDwellerEntity(final EntityType<? extends CaveDwellerEntity> entityType, final Level level) {
        super(entityType, level);
        this.refreshDimensions();
        this.twoBlockSpaceCooldown = 5.0F;
        this.ticksTillRemove = Utils.secondsToTicks(ServerConfig.TIME_UNTIL_LEAVE.get());
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NotNull final ServerLevelAccessor level, @NotNull final DifficultyInstance difficulty, @NotNull final MobSpawnType reason, @Nullable final SpawnGroupData spawnData, @Nullable final CompoundTag tagData) {
        setAttribute(getAttribute(Attributes.MAX_HEALTH), ServerConfig.MAX_HEALTH.get());
        setAttribute(getAttribute(Attributes.ATTACK_DAMAGE), ServerConfig.ATTACK_DAMAGE.get());
        setAttribute(getAttribute(Attributes.ATTACK_SPEED), ServerConfig.ATTACK_SPEED.get());
        setAttribute(getAttribute(Attributes.MOVEMENT_SPEED), ServerConfig.MOVEMENT_SPEED.get());
        setAttribute(getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get()), 0.4); // LivingEntity default is 0.6

        return super.finalizeSpawn(level, difficulty, reason, spawnData, tagData);
    }

    private void setAttribute(final AttributeInstance attribute, double value) {
        if (attribute != null) {
            attribute.setBaseValue(value);

            if (attribute.getAttribute() == Attributes.MAX_HEALTH) {
                setHealth((float) value);
            } else if (attribute.getAttribute() == Attributes.MOVEMENT_SPEED) {
                setSpeed((float) value);
            }
        }
    }

    public static AttributeSupplier getAttributeBuilder() {
        double maxHealth = 60.0;
        double attackDamage = 6.0;
        double attackSpeed = 0.35;
        double movementSpeed = 0.5;
        double followRange = 100.0;

        return CaveDwellerEntity.createMobAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ATTACK_SPEED, attackSpeed)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.FOLLOW_RANGE, followRange)
                .build();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FLEEING_ACCESSOR, false);
        entityData.define(CROUCHING_ACCESSOR, false);
        entityData.define(CRAWLING_ACCESSOR, false);
        entityData.define(SPOTTED_ACCESSOR, false);
        entityData.define(CLIMBING_ACCESSOR, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new CaveDwellerChaseGoal(this, true));
        goalSelector.addGoal(1, new CaveDwellerFleeGoal(this, 20.0F, 1.0));
        goalSelector.addGoal(2, new CaveDwellerBreakInvisGoal(this));
        goalSelector.addGoal(2, new CaveDwellerStareGoal(this));
        if (ServerConfig.CAN_BREAK_DOOR.get()) { // TODO :: Remove for already spawned entities on config change?
            goalSelector.addGoal(2, new CaveDwellerBreakDoorGoal(this, difficulty -> true));
        }
        goalSelector.addGoal(3, new CaveDwellerStrollGoal(this, 0.7));
        targetSelector.addGoal(1, new CaveDwellerTargetTooCloseGoal(this, 12.0F));
        targetSelector.addGoal(2, new CaveDwellerTargetSeesMeGoal(this));
    }

    public Vec3 generatePos(final Entity player) {
        Vec3 playerPos = player.position();
        Random rand = new Random();
        double randX = rand.nextInt(70) - 35;
        double randZ = rand.nextInt(70) - 35;
        int posX = (int) (playerPos.x + randX);
        int posY = (int) (playerPos.y + 10.0);
        int posZ = (int) (playerPos.z + randZ);

        for (int runFor = 100; runFor >= 0; --posY) {
            BlockPos blockPosition = new BlockPos(posX, posY, posZ);
            BlockPos blockPosition2 = new BlockPos(posX, posY + 1, posZ);
            BlockPos blockPosition3 = new BlockPos(posX, posY + 2, posZ);
            BlockPos blockPosition4 = new BlockPos(posX, posY - 1, posZ);
            --runFor;

            if (!level().getBlockState(blockPosition).blocksMotion()
                    && !level().getBlockState(blockPosition2).blocksMotion()
                    && !level().getBlockState(blockPosition3).blocksMotion()
                    && level().getBlockState(blockPosition4).blocksMotion()) {
                break;
            }
        }

        return new Vec3(posX, posY, posZ);
    }

    public void disappear() {
        playDisappearSound();
        discard();
    }

    @Override
    protected boolean canRide(@NotNull final Entity vehicle) {
        if (ServerConfig.ALLOW_RIDING.get()) {
            return super.canRide(vehicle);
        }

        return false;
    }

    @Override
    public boolean startRiding(@NotNull final Entity vehicle, boolean force) {
        if (ServerConfig.ALLOW_RIDING.get()) {
            return super.startRiding(vehicle, force);
        }

        return false;
    }

    @Override
    public void tick() {
        --ticksTillRemove;

        if (ticksTillRemove <= 0) {
            disappear();
        }

        if (goalSelector.getAvailableGoals().isEmpty() || targetSelector.getAvailableGoals().isEmpty()) {
            registerGoals();
            goalSelector.tick();
            targetSelector.tick();
        }

        // The calls in the goal seem to only update the server side, this updates the rendered hitbox e.g.
        refreshDimensions();

        if (getTarget() != null) {
            targetIsLookingAtMe = isLookingAtMe(getTarget());
        }

        boolean isTwoAboveSolid = false;
        boolean isFacingTwoAboveSolid = false;
        boolean isFacingNonSolid = false;
        boolean isFacingAboveNonSolid = false;

        if (!getEntityData().get(CRAWLING_ACCESSOR)) {
            isTwoAboveSolid = level().getBlockState(blockPosition().above().above()).isSolid();
            isFacingTwoAboveSolid = level().getBlockState(blockPosition().relative(getDirection()).above().above()).isSolid();
            isFacingNonSolid = !level().getBlockState(blockPosition().relative(getDirection())).isSolid();
            isFacingAboveNonSolid = !level().getBlockState(blockPosition().relative(getDirection()).above()).isSolid();
        }

        if (isTwoAboveSolid || (isFacingTwoAboveSolid && isFacingNonSolid && isFacingAboveNonSolid)) {
            twoBlockSpaceTimer = twoBlockSpaceCooldown;
            inTwoBlockSpace = true;
        } else {
            // Don't immediately stop crouching
            --twoBlockSpaceTimer;

            if (twoBlockSpaceTimer <= 0.0F) {
                inTwoBlockSpace = false;
            }
        }

        if (level() instanceof ServerLevel) {
            if (isAggressive() || isFleeing) {
                entityData.set(SPOTTED_ACCESSOR, false);
            }

            setClimbing(horizontalCollision);
            entityData.set(CROUCHING_ACCESSOR, inTwoBlockSpace);
        }

        if (entityData.get(SPOTTED_ACCESSOR)) {
            playSpottedSound();
        }

        super.tick();
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull final Pose pose) {
        if (entityData.get(CRAWLING_ACCESSOR)) { // TODO :: Allow config (for crawling through half-block space)?
            return new EntityDimensions(0.5F, 0.5F, true);
        } else if (entityData.get(CROUCHING_ACCESSOR)) {
            return new EntityDimensions(0.5F, 2.0F, true);
        }

        return super.getDimensions(pose);
    }

    private boolean isMoving() {
        Vec3 velocity = getDeltaMovement();
        float avgVelocity = (float) (Math.abs(velocity.x) + Math.abs(velocity.z)) / 2.0F;

        return avgVelocity > 0.03F;
    }

    public void reRoll() {
        /*
        Rolling STROLL (3) here causes it to just stand in place and play the stare animation
        (And playing the stare animation when it stops moving)
        */
        currentRoll = Roll.fromValue(new Random().nextInt(3));
    }

    public void pickRoll(@NotNull final List<Roll> rolls) {
        currentRoll = rolls.get(new Random().nextInt(rolls.size()));
    }

    @Override
    public boolean onClimbable() {
        return isClimbing();
    }

    public boolean isClimbing() {
        if (!ServerConfig.CAN_CLIMB.get()) {
            return false;
        }

        if (getTarget() != null /*&& getTarget().getPosition(1).y > getY()*/) {
            return entityData.get(CLIMBING_ACCESSOR);
        }

        return false;
    }

    public void setClimbing(boolean isClimbing) {
        entityData.set(CLIMBING_ACCESSOR, isClimbing);
    }

    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull final Level level) {
        return new WallClimberNavigation(this, level);
    }

    private int crawlingTicks;

    private PlayState predicate(final AnimationState<CaveDwellerEntity> state) {
        if (entityData.get(CRAWLING_ACCESSOR)) {
            // Crawling
            crawlingTicks = Utils.secondsToTicks(1);
            return state.setAndContinue(CRAWL);
        } else if (crawlingTicks > 0) {
            // Crawling end
            // TODO :: set a flag in the chase logic when the dweller is at the last node to crawl and then play this
            crawlingTicks--;
            return state.setAndContinue(CRAWL_END);
        } else if (entityData.get(CROUCHING_ACCESSOR)) {
            // Crouching
            if (state.isMoving()) {
                return state.setAndContinue(CROUCH_RUN);
            } else {
                return state.setAndContinue(CROUCH_IDLE);
            }
        } else if (isAggressive()) {
            // Chase
            if (state.isMoving()) {
                // FIXME :: With the ramp up the cave dweller slides for 1 second or so -> add a flag to determine when this should be played?
                return state.setAndContinue(CHASE);
            } else {
                return state.setAndContinue(CHASE_IDLE);
            }
        } else if (entityData.get(FLEEING_ACCESSOR)) {
            // Fleeing
            if (state.isMoving()) {
                return state.setAndContinue(FLEE);
            } else {
                return state.setAndContinue(CHASE_IDLE);
            }
        } else if (pleaseStopMoving || entityData.get(SPOTTED_ACCESSOR) && !state.isMoving()) {
            // Spotted
            return state.setAndContinue(IS_SPOTTED);
        } else {
            // Normal
            if (state.isMoving()) {
                return state.setAndContinue(CALM_RUN);
            } else {
                return state.setAndContinue(CALM_STILL);
            }
        }
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<CaveDwellerEntity>(this, "controller", 3, this::predicate));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pPos, @NotNull BlockState pState) {
        super.playStepSound(pPos, pState);
        playEntitySound(chooseStep());
    }

    private void playEntitySound(SoundEvent soundEvent) {
        playEntitySound(soundEvent, 1.0F, 1.0F);
    }

    private void playEntitySound(SoundEvent soundEvent, float volume, float pitch) {
        level().playSound(null, this, soundEvent, SoundSource.HOSTILE, volume, pitch);
    }

    // TODO :: Is this needed? Why not just playEntitySound
    private void playBlockPosSound(final ResourceLocation soundResource, float volume, float pitch) {
        if (level() instanceof ServerLevel serverLevel) {
            int radius = 60; // blocks
            serverLevel.getPlayers(player -> player.distanceToSqr(this) <= radius * radius).forEach(player -> NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CaveSound(soundResource, player.blockPosition(), volume, pitch)));
        }
    }

    public void playChaseSound() {
        if (startedPlayingChaseSound || isMoving()) {
            if (chaseSoundClock <= 0) {
                Random rand = new Random();

                switch (rand.nextInt(4)) {
                    case 0 -> playEntitySound(ModSounds.CHASE_1.get(), 3.0F, 1.0F);
                    case 1 -> playEntitySound(ModSounds.CHASE_2.get(), 3.0F, 1.0F);
                    case 2 -> playEntitySound(ModSounds.CHASE_3.get(), 3.0F, 1.0F);
                    case 3 -> playEntitySound(ModSounds.CHASE_4.get(), 3.0F, 1.0F);
                }

                startedPlayingChaseSound = true;
                resetChaseSoundClock();
            }

            --chaseSoundClock;
        }
    }

    public void playDisappearSound() {
        playBlockPosSound(ModSounds.DISAPPEAR.get().getLocation(), 3.0F, 1.0F);
    }

    public void playFleeSound() {
        if (!alreadyPlayedFleeSound) {
            Random rand = new Random();

            switch (rand.nextInt(2)) {
                case 0 -> playEntitySound(ModSounds.FLEE_1.get(), 3.0F, 1.0F);
                case 1 -> playEntitySound(ModSounds.FLEE_2.get(), 3.0F, 1.0F);
            }

            alreadyPlayedFleeSound = true;
        }
    }

    private void playSpottedSound() {
        if (!alreadyPlayedSpottedSound) {
            playEntitySound(ModSounds.SPOTTED.get(), 3.0F, 1.0F);
            alreadyPlayedSpottedSound = true;
        }
    }

    private void resetChaseSoundClock() {
        chaseSoundClock = Utils.secondsToTicks(5);
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
        SoundEvent soundevent = chooseHurtSound();
        playEntitySound(soundevent, 2.0F, 1.0F);
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();

        if (!alreadyPlayedDeathSound) {
            playBlockPosSound(ModSounds.DWELLER_DEATH.get().getLocation(), 2.0F, 1.0F);
            alreadyPlayedDeathSound = true;
        }
    }

    public boolean isLookingAtMe(final Entity target) {
        if (!Utils.isValidPlayer(target)) {
            return false;
        }

        if (target.getEyePosition(1).distanceTo(getPosition(1)) > ServerConfig.SPOTTING_RANGE.get()) {
            return false;
        }

        return isLooking(target);
    }

    private boolean isLooking(final Entity target) {
        Vec3 viewVector = target.getViewVector(1.0F).normalize();
        Vec3 difference = new Vec3(getX() - target.getX(), getEyeY() - target.getEyeY(), getZ() - target.getZ());
        difference = difference.normalize();
        double dot = viewVector.dot(difference);

        // FIXME :: The line of sight method is very unreliable
        return dot > 0 /*&& target.hasLineOfSight(this)*/;
    }

    public boolean teleportToTarget() {
        LivingEntity target = getTarget();

        if (target == null) {
            return false;
        }

        Vec3 targetPosition = new Vec3(getX() - target.getX(), getY(0.5D) - target.getEyeY(), getZ() - target.getZ());
        targetPosition = targetPosition.normalize();

        double radius = 16;

        double d1 = getX() + (getRandom().nextDouble() - 0.5D) * (radius / 2) - targetPosition.x * radius;
        double d2 = getY() + (getRandom().nextInt((int) radius) - (radius / 2)) - targetPosition.y * radius;
        double d3 = getZ() + (getRandom().nextDouble() - 0.5D) * (radius / 2) - targetPosition.z * radius;

        BlockPos.MutableBlockPos validPosition = new BlockPos.MutableBlockPos(d1, d2, d3);

        // Don't teleport up into the air
        while (validPosition.getY() > level().getMinBuildHeight() && !level().getBlockState(validPosition).blocksMotion()) {
            validPosition.move(Direction.DOWN);
        }

        teleportTo(validPosition.getX(), validPosition.getY(), validPosition.getZ());

        return true;
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull final DamageSource damageSourceIn) {
        return chooseHurtSound();
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