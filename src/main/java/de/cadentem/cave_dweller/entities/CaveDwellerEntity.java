package de.cadentem.cave_dweller.entities;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.goals.*;
import de.cadentem.cave_dweller.network.CaveSound;
import de.cadentem.cave_dweller.network.NetworkHandler;
import de.cadentem.cave_dweller.registry.ModSounds;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.pathfinder.Path;
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

    public Roll currentRoll = Roll.STROLL;
    public boolean fakeSize = false;
    private boolean inTwoBlockSpace = false;
    public boolean spottedByPlayer = false;
    public boolean squeezeCrawling = false;
    public boolean isFleeing;
    private int ticksTillRemove;

    // TODO :: 2 unused animations
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
        this.refreshDimensions();
        this.twoBlockSpaceCooldown = 5.0F;
        this.ticksTillRemove = Utils.secondsToTicks(ServerConfig.TIME_UNTIL_LEAVE_CHASE.get());
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NotNull final ServerLevelAccessor level, @NotNull final DifficultyInstance difficulty, @NotNull final MobSpawnType reason, @Nullable final SpawnGroupData spawnData, @Nullable final CompoundTag tagData) {
        setAttribute(getAttribute(Attributes.MAX_HEALTH), ServerConfig.MAX_HEALTH.get());
        setAttribute(getAttribute(Attributes.ATTACK_DAMAGE), ServerConfig.ATTACK_DAMAGE.get());
        setAttribute(getAttribute(Attributes.ATTACK_SPEED), ServerConfig.ATTACK_SPEED.get());
        setAttribute(getAttribute(Attributes.MOVEMENT_SPEED), ServerConfig.MOVEMENT_SPEED.get());
        setAttribute(getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get()), 0.4); // LivingEntity default is 0.6

//        ((WallClimberNavigation) getNavigation()).setCanOpenDoors(true);

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
        entityData.define(AGGRO_ACCESSOR, false);
        entityData.define(SQUEEZING_ACCESSOR, false);
        entityData.define(SPOTTED_ACCESSOR, false);
        entityData.define(CLIMBING_ACCESSOR, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new CaveDwellerChaseGoal(this,  0.85F, true, 20.0F));
        goalSelector.addGoal(1, new CaveDwellerFleeGoal(this, 20.0F, 1.0));
        goalSelector.addGoal(2, new CaveDwellerBreakInvisGoal(this));
        goalSelector.addGoal(2, new CaveDwellerStareGoal(this, Utils.secondsToTicks(ServerConfig.TIME_STARING.get())));
        if (ServerConfig.CAN_BREAK_DOOR.get()) {
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
        double posX = playerPos.x + randX;
        double posY = playerPos.y + 10.0;
        double posZ = playerPos.z + randZ;

        for (int runFor = 100; runFor >= 0; --posY) {
            BlockPos blockPosition = new BlockPos(posX, posY, posZ);
            BlockPos blockPosition2 = new BlockPos(posX, posY + 1.0, posZ);
            BlockPos blockPosition3 = new BlockPos(posX, posY + 2.0, posZ);
            BlockPos blockPosition4 = new BlockPos(posX, posY - 1.0, posZ);
            --runFor;

            if (!level.getBlockState(blockPosition).getMaterial().blocksMotion()
                    && !level.getBlockState(blockPosition2).getMaterial().blocksMotion()
                    && !level.getBlockState(blockPosition3).getMaterial().blocksMotion()
                    && level.getBlockState(blockPosition4).getMaterial().blocksMotion()) {
                break;
            }
        }

        return new Vec3(posX, posY, posZ);
    }

    @Override
    public void tick() {
        --ticksTillRemove;

        if (ticksTillRemove <= 0) {
            playDisappearSound();
            discard();
        }

        if (goalSelector.getAvailableGoals().isEmpty()) {
            registerGoals();
            goalSelector.tick();
        }

        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(position().x, position().y + 2.0, position().z);
        BlockState above = level.getBlockState(blockpos$mutableblockpos);
        boolean blocksMotion = above.getMaterial().blocksMotion();

        if (blocksMotion) {
            twoBlockSpaceTimer = twoBlockSpaceCooldown;
            inTwoBlockSpace = true;
        } else {
            --twoBlockSpaceTimer;

            if (twoBlockSpaceTimer <= 0.0F) {
                inTwoBlockSpace = false;
            }
        }

        if (isAggressive() || isFleeing) {
            spottedByPlayer = false;
            entityData.set(SPOTTED_ACCESSOR, false);
        }

        super.tick();

        entityData.set(CROUCHING_ACCESSOR, inTwoBlockSpace);

        if (entityData.get(SPOTTED_ACCESSOR)) {
            playSpottedSound();
        }

        if (!level.isClientSide) {
            setClimbing(horizontalCollision);
        }
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull final Pose pose) {
        return fakeSize ? new EntityDimensions(0.5F, 0.9F, true) : new EntityDimensions(0.5F, 1.9F, true);
    }

    private boolean isMoving() {
        Vec3 velocity = getDeltaMovement();
        float avgVelocity = (float) (Math.abs(velocity.x) + Math.abs(velocity.z)) / 2.0F;

        return avgVelocity > 0.03F;
    }

    public void reRoll() {
        /* TODO
        Rolling STROLL (3) here causes it to just stand in place and play the stare animation
        (And playing the stare animation when it stops moving)
        */
        currentRoll = Roll.fromValue(new Random().nextInt(3));
    }

    public void pickRoll(@NotNull final List<Roll> rolls) {
        currentRoll = rolls.get(new Random().nextInt(rolls.size()));
    }

    public Path createShortPath(final LivingEntity target) {
        fakeSize = true;
        refreshDimensions();
        Path shortPath = getNavigation().createPath(target, 0);
        fakeSize = false;
        refreshDimensions();
        return shortPath;
    }

    public Path createShortPath(@NotNull final Vec3 position) {
        fakeSize = true;
        refreshDimensions();
        Path shortPath = getNavigation().createPath(position.x, position.y, position.z, 0);
        fakeSize = false;
        refreshDimensions();
        return shortPath;
    }

    @Override
    public boolean onClimbable() {
        return isClimbing();
    }

    public boolean isClimbing() {
        if (!ServerConfig.CAN_CLIMB.get()) {
            return false;
        }

        if (getTarget() != null && getTarget().getPosition(1).y > getY()) {
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

    private PlayState predicate(final AnimationEvent<CaveDwellerEntity> event) {
        AnimationBuilder builder = new AnimationBuilder();
        AnimationController<CaveDwellerEntity> controller = event.getController();

        if (entityData.get(AGGRO_ACCESSOR)) {
            if (entityData.get(SQUEEZING_ACCESSOR)) {
                // Squeezing
                builder.addAnimation(CRAWL.animationName, CRAWL.loopType);
            } else if (entityData.get(CROUCHING_ACCESSOR)) {
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
        } else if (entityData.get(FLEEING_ACCESSOR)) {
            // Fleeing
            if (event.isMoving()) {
                builder.addAnimation(FLEE.animationName, FLEE.loopType);
            } else {
                builder.addAnimation(CHASE_IDLE.animationName, CHASE_IDLE.loopType);
            }
        } else if (entityData.get(SPOTTED_ACCESSOR) && !event.isMoving()) {
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
            int radius = 10; // blocks
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

    /** Referenced from {@link net.minecraft.world.entity.monster.EnderMan#isLookingAtMe(Player)} */
    public boolean isLookingAtMe(final Entity pendingTarget) {
        if (!(pendingTarget instanceof Player player)) {
            return false;
        }

        if (player.isSpectator() || !player.isAlive()) {
            return false;
        }

        if (player.getEyePosition(1).distanceTo(getPosition(1)) > ServerConfig.SPOTTING_RANGE.get()) {
            return false;
        }

        Vec3 viewVector = player.getViewVector(1.0F).normalize();
        Vec3 difference = new Vec3(getX() - player.getX(), getEyeY() - player.getEyeY(), getZ() - player.getZ());
        double length = difference.length();
        difference = difference.normalize();
        double dot = viewVector.dot(difference);

        return dot > 1.0D - 0.025D / length && player.hasLineOfSight(this);
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