package de.cadentem.cave_dweller.entities;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.goals.*;
import de.cadentem.cave_dweller.network.CaveSound;
import de.cadentem.cave_dweller.network.NetworkHandler;
import de.cadentem.cave_dweller.registry.ModSounds;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

import java.util.List;
import java.util.Random;

public class CaveDwellerEntity extends Monster implements IAnimatable {
    private final AnimationFactory factory = GeckoLibUtil.createFactory(this);

    private final RawAnimation CHASE = new RawAnimation("animation.cave_dweller.new_run", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation CHASE_IDLE = new RawAnimation("animation.cave_dweller.run_idle", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation CROUCH_RUN = new RawAnimation("animation.cave_dweller.crouch_run_new", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation CROUCH_IDLE = new RawAnimation("animation.cave_dweller.crouch_idle", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation CALM_RUN = new RawAnimation("animation.cave_dweller.calm_move", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation CALM_STILL = new RawAnimation("animation.cave_dweller.calm_idle", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation IS_SPOTTED = new RawAnimation("animation.cave_dweller.spotted", ILoopType.EDefaultLoopTypes.HOLD_ON_LAST_FRAME);
    private final RawAnimation CRAWL = new RawAnimation("animation.cave_dweller.crawl", ILoopType.EDefaultLoopTypes.LOOP);
    private final RawAnimation FLEE = new RawAnimation("animation.cave_dweller.flee", ILoopType.EDefaultLoopTypes.LOOP);

    public static final EntityDataAccessor<Boolean> FLEEING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CROUCHING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CRAWLING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> SPOTTED_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CLIMBING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);

    public Roll currentRoll = Roll.STROLL;
    public boolean isFleeing;
    /** To be able to create a path while spawning */
    public boolean hasSpawned;
    public boolean pleaseStopMoving;
    public boolean targetIsFacingMe;

    private int ticksTillRemove;
    private int chaseSoundClock;
    private boolean alreadyPlayedFleeSound;
    private boolean alreadyPlayedSpottedSound;
    private boolean startedPlayingChaseSound;
    private boolean alreadyPlayedDeathSound;

    public CaveDwellerEntity(final EntityType<? extends CaveDwellerEntity> entityType, final Level level) {
        super(entityType, level);
        this.refreshDimensions();
        this.ticksTillRemove = Utils.secondsToTicks(ServerConfig.TIME_UNTIL_LEAVE.get());
        this.setPathfindingMalus(BlockPathTypes.UNPASSABLE_RAIL, 0.0f);
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
        double movementSpeed = 0.3;
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
        goalSelector.addGoal(1, new CaveDwellerFleeGoal(this, 20, 1));
        goalSelector.addGoal(2, new CaveDwellerBreakInvisGoal(this));
        goalSelector.addGoal(2, new CaveDwellerStareGoal(this));
        if (ServerConfig.CAN_BREAK_DOOR.get()) { // TODO :: Remove for already spawned entities on config change?
            goalSelector.addGoal(2, new CaveDwellerBreakDoorGoal(this, difficulty -> true));
        }
        goalSelector.addGoal(3, new CaveDwellerStrollGoal(this, 0.35));
        targetSelector.addGoal(1, new CaveDwellerTargetTooCloseGoal(this, 12));
        targetSelector.addGoal(2, new CaveDwellerTargetSeesMeGoal(this));
    }

    public void disappear() {
        playDisappearSound();
        discard();
    }

    public boolean hasSpawned() {
        return hasSpawned;
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

        if (getTarget() != null) {
            targetIsFacingMe = isLookingAtMe(getTarget(), false);
        }

        if (level instanceof ServerLevel) {
            boolean isAboveSolid = level.getBlockState(blockPosition().above()).getMaterial().isSolid();
            boolean isTwoAboveSolid = level.getBlockState(blockPosition().above(2)).getMaterial().isSolid();
            boolean isThreeAboveSolid = level.getBlockState(blockPosition().above(3)).getMaterial().isSolid();

            Vec3i offset = getDirectionVector();
            boolean isFacingSolid = level.getBlockState(blockPosition().relative(getDirection())).getMaterial().isSolid();

            /* Offset is set to the block above the block position (which is at feet level) (since direction is used it's the block in front for both cases)
                -----o                  -----o
                     o                       o <- offset
                -----o <- current       -----o
            */
            if (isFacingSolid) { // TODO :: Clean up, the offset with the check is kinda useless at this point since both positions are needed for correct checks
                offset = offset.offset(0, 1, 0);
            }

            boolean isOffsetFacingSolid = level.getBlockState(blockPosition().offset(offset)).getMaterial().isSolid();
            boolean isOffsetFacingAboveSolid = level.getBlockState(blockPosition().offset(offset).above()).getMaterial().isSolid();
            boolean isOffsetFacingTwoAboveSolid = level.getBlockState(blockPosition().offset(offset).above(2)).getMaterial().isSolid();

            /* [- : blocks | o : cave dweller]
                To handle these variants among other things:
                    o       -----
                ----o           o
                    o           o
                -----       ----o
            */
            boolean shouldCrouch = isTwoAboveSolid || (!isOffsetFacingSolid && !isOffsetFacingAboveSolid && (isOffsetFacingTwoAboveSolid || isFacingSolid && isThreeAboveSolid)) ;

            /* [- : blocks | o : cave dweller | + : cave dweller in solid block]
                To handle these variants among other things:
                    o       ----o
                ----+          -o       ----o
                    o           o           o
                -----       -----       ----o
            */
            boolean shouldCrawl = isAboveSolid || !isOffsetFacingSolid && isOffsetFacingAboveSolid || isFacingSolid && isTwoAboveSolid;

            if (isAggressive() || isFleeing) {
                entityData.set(SPOTTED_ACCESSOR, false);
            }

            setClimbing(horizontalCollision);
            entityData.set(CROUCHING_ACCESSOR, shouldCrouch);
            setCrawling(shouldCrawl);
        }

        if (entityData.get(SPOTTED_ACCESSOR)) {
            playSpottedSound();
        }

        refreshDimensions(); // TODO :: Currently needed to make client stay in sync
        getNavigation().setSpeedModifier(getSpeedModifier());

        super.tick();
    }

    public double getSpeedModifier() {
        return isCrawling() ? 0.35 : isCrouching() ? 0.6 : 0.85;
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull final Pose pose) {
        if (entityData.get(CRAWLING_ACCESSOR)) { // TODO :: Allow config (for crawling through half-block space)?
            return new EntityDimensions(0.5F, 0.5F, true);
        } else if (entityData.get(CROUCHING_ACCESSOR)) {
            return new EntityDimensions(0.5F, 1.7F, true);
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

        if (getTarget() != null) {
            // TODO :: Not sure if the initial two checks are needed
            return !isCrawling() && !isCrouching() && entityData.get(CLIMBING_ACCESSOR);
        }

        return false;
    }

    public void setClimbing(boolean isClimbing) {
        entityData.set(CLIMBING_ACCESSOR, isClimbing);
    }

    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull final Level level) {
        WallClimberNavigation navigation = new WallClimberNavigation(this, level);
        navigation.setMaxVisitedNodesMultiplier(4);
        return navigation;
    }

    private PlayState predicate(final AnimationEvent<CaveDwellerEntity> event) {
        AnimationBuilder builder = new AnimationBuilder();
        AnimationController<CaveDwellerEntity> controller = event.getController();

        boolean isCurrentAboveSolid = level.getBlockState(blockPosition().above()).getMaterial().isSolid();
        boolean unsure = isCrawling() && level.getBlockState(blockPosition()).getMaterial().isSolid();
//        boolean isFacingAboveSolid = isCrawling() && level.getBlockState(blockPosition().offset(getDirectionVector()).above()).getMaterial().isSolid();
        boolean isCurrentTwoAboveSolid = level.getBlockState(blockPosition().above(2)).getMaterial().isSolid();
//        boolean isFacingTwoAboveSolid = isCrouching() && level.getBlockState(blockPosition().offset(getDirectionVector()).above(2)).getMaterial().isSolid();;

        // TODO :: Climbing animation
        if (isCurrentAboveSolid || unsure/* || isFacingAboveSolid*/) {
            // Crawling
            builder.addAnimation(CRAWL.animationName, CRAWL.loopType);
        } else if (isCurrentTwoAboveSolid /*|| isFacingTwoAboveSolid*/) {
            // Crouching
            if (event.isMoving()) {
                builder.addAnimation(CROUCH_RUN.animationName, CROUCH_RUN.loopType);
            } else {
                builder.addAnimation(CROUCH_IDLE.animationName, CROUCH_IDLE.loopType);
            }
        } else if (isAggressive()) {
            // Chase
            if (event.isMoving()) {
                builder.addAnimation(CHASE.animationName, CHASE.loopType);
            } else {
                builder.addAnimation(CHASE_IDLE.animationName, CHASE_IDLE.loopType);
            }
        } else if (entityData.get(FLEEING_ACCESSOR)) {
            // Fleeing
            if (event.isMoving()) {
                builder.addAnimation(FLEE.animationName, FLEE.loopType);
            } else {
                builder.addAnimation(CHASE_IDLE.animationName, CHASE_IDLE.loopType);
            }
        } else if (pleaseStopMoving || entityData.get(SPOTTED_ACCESSOR) && !event.isMoving()) {
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
        return factory;
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
        level.playSound(null, this, soundEvent, SoundSource.HOSTILE, volume, pitch);
    }

    // TODO :: Is this needed? Why not just playEntitySound
    private void playBlockPosSound(final ResourceLocation soundResource, float volume, float pitch) {
        if (level instanceof ServerLevel serverLevel) {
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

    public void setCrawling(boolean shouldCrawl) {
        if (shouldCrawl) {
            getEntityData().set(CROUCHING_ACCESSOR, false);
        }

        getEntityData().set(CRAWLING_ACCESSOR, shouldCrawl);
        refreshDimensions();
    }

    public boolean isCrawling() {
        return entityData.get(CRAWLING_ACCESSOR);
    }


    /* TODO :: Check
    @Override
    public boolean isVisuallyCrawling() {
        return super.isVisuallyCrawling();
    }
    */

    @Override
    protected void tickDeath() {
        super.tickDeath();

        if (!alreadyPlayedDeathSound) {
            playBlockPosSound(ModSounds.DWELLER_DEATH.get().getLocation(), 2.0F, 1.0F);
            alreadyPlayedDeathSound = true;
        }
    }

    public boolean isLookingAtMe(final Entity target, boolean directlyLooking) {
        if (!Utils.isValidPlayer(target)) {
            return false;
        }

        if (target.getEyePosition(1).distanceTo(getPosition(1)) > ServerConfig.SPOTTING_RANGE.get()) {
            return false;
        }

        Vec3 viewVector = target.getViewVector(1.0F).normalize();
        Vec3 difference = new Vec3(getX() - target.getX(), getEyeY() - target.getEyeY(), getZ() - target.getZ());
        difference = difference.normalize();
        double dot = viewVector.dot(difference);

        if (directlyLooking && target instanceof Player player) {
            return dot > 0.99 && player.hasLineOfSight(this);
        }

        return dot > 0.3;
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
        while (validPosition.getY() > level.getMinBuildHeight() && !level.getBlockState(validPosition).getMaterial().blocksMotion()) {
            validPosition.move(Direction.DOWN);
        }

        teleportTo(validPosition.getX(), validPosition.getY(), validPosition.getZ());

        return true;
    }

    private Vec3i getDirectionVector() {
        return new Vec3i(getDirection().getStepX(), getDirection().getStepY(), getDirection().getStepZ());
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