package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CaveDwellerChaseGoal extends Goal {
    protected final PathfinderMob mob;
    private final CaveDwellerEntity cavedweller;
    private final double speedModifier;
    private final boolean followingTargetEvenIfNotSeen;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private int failedPathFindingPenalty = 0;
    private final boolean canPenalize = false;
    private final float ticksTillChase;
    private float currentTicksTillChase;
    private boolean shouldUseShortPath = false;
    private boolean squeezing = false;
    private Path shortPath;
    private Vec3 vecNodePos;
    private Vec3 vecMobPos;
    private final int ticksToSqueeze;
    private int currentTicksToSqueeze;
    private int currentTicksTillLeave;
    Vec3 xPathStartVec;
    Vec3 zPathStartVec;
    Vec3 xPathTargetVec;
    Vec3 zPathTargetVec;
    Vec3 vecTargetPos;
    Vec3 nodePositionCooldownPos;
    BlockPos nodePos;

    public CaveDwellerChaseGoal(final PathfinderMob pMob, final CaveDwellerEntity pCaveDweller, double pSpeedModifier, boolean pFollowingTargetEvenIfNotSeen, float pTicksTillChase) {
        this.mob = pMob;
        this.speedModifier = pSpeedModifier;
        this.followingTargetEvenIfNotSeen = pFollowingTargetEvenIfNotSeen;
        this.cavedweller = pCaveDweller;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.ticksTillChase = pTicksTillChase;
        this.currentTicksTillChase = pTicksTillChase;
        this.vecNodePos = null;
        this.ticksToSqueeze = 15;
        this.nodePos = null;
        this.currentTicksTillLeave = 600;
    }

    @Override
    public boolean canUse() {
        if (this.cavedweller.isInvisible()) {
            return false;
        } else if (this.cavedweller.reRollResult != 0) {
            return false;
        } else {
            long ticks = this.mob.level.getGameTime();

            if (ticks % 20 != 0) {
                return false;
            } else {
                LivingEntity target = this.mob.getTarget();

                Path path;
                if (target == null) {
                    return false;
                } else if (!target.isAlive()) {
                    return false;
                } else if (this.canPenalize) {
                    if (--this.ticksUntilNextPathRecalculation <= 0) {
                        path = this.mob.getNavigation().createPath(target, 0);
                        this.ticksUntilNextPathRecalculation = 2;

                        return path != null;
                    } else {
                        return true;
                    }
                } else {
                    path = this.mob.getNavigation().createPath(target, 0);

                    if (path != null) {
                        return true;
                    } else {
                        return this.getAttackReachSqr(target) >= this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
                    }
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();

        if (target == null) {
            return false;
        } else if (!target.isAlive()) {
            this.cavedweller.discard();

            return false;
        } else if (!this.followingTargetEvenIfNotSeen) {
            return !this.mob.getNavigation().isDone();
        } else if (!this.mob.isWithinRestriction(target.blockPosition())) {
            return false;
        } else {
            return !(target instanceof Player player) || !target.isSpectator() && !player.isCreative();
        }
    }

    @Override
    public void start() {
        this.ticksUntilNextPathRecalculation = 0;
        this.ticksUntilNextAttack = 0;
    }

    @Override
    public void stop() {
        LivingEntity target = this.mob.getTarget();

        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
            this.mob.setTarget(null);
        }

        this.cavedweller.squeezeCrawling = false;
        this.cavedweller.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, false);
        this.cavedweller.isAggro = false;
        this.cavedweller.refreshDimensions();
        this.currentTicksTillChase = this.ticksTillChase;
        this.mob.setAggressive(false);
        this.mob.getNavigation().stop();
        this.cavedweller.setNoGravity(false);
        this.cavedweller.noPhysics = false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        this.cavedweller.squeezeCrawling = this.squeezing;
        LivingEntity target = null;

        if (this.cavedweller.getTarget() != null) {
            target = this.mob.getTarget();
        }

        this.tickAggroClock();

        if (!this.squeezing && target != null) {
            if (this.cavedweller.isAggro) {
                this.mob.getLookControl().setLookAt(target, 90.0F, 90.0F);
            } else {
                this.mob.getLookControl().setLookAt(target, 180.0F, 1.0F);
            }
        }

        if (this.cavedweller.getEntityData().get(CaveDwellerEntity.AGGRO_ACCESSOR)) {
            if (this.squeezing) {
                this.squeezingTick();
            } else {
                this.aggroTick();
            }
        }

        --this.currentTicksTillLeave;
        if (this.currentTicksTillLeave <= 0 && (!cavedweller.isPlayerLookingTowards(target) || !this.inPlayerLineOfSight())) {
            this.cavedweller.discard();
        }
    }

    private void tickAggroClock() {
        --this.currentTicksTillChase;

        if (this.currentTicksTillChase <= 0.0F) {
            this.cavedweller.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, true);
        }

        this.cavedweller.isAggro = true;
        this.cavedweller.refreshDimensions();
    }

    private Path getShortPath(final LivingEntity target) {
        this.shortPath = this.cavedweller.createShortPath(target);
        return shortPath;
    }

    private void squeezingTick() {
        this.cavedweller.setNoGravity(true);
        this.cavedweller.noPhysics = true;

        if (this.mob.getNavigation().getPath() != null) {
            this.nodePos = this.mob.getNavigation().getPath().getNextNodePos();
        }

        this.mob.getNavigation().stop();

        if (this.nodePos == null) {
            this.stopSqueezing();
        } else {
            if (this.vecNodePos == null) {
                this.vecNodePos = new Vec3(this.nodePos.getX(), this.nodePos.getY(), this.nodePos.getZ());
            }

            this.nodePositionCooldownPos = this.vecNodePos;
            Vec3 vecOldMobPos = this.cavedweller.getPosition(1.0F);

            if (this.xPathStartVec == null) {
                if (vecOldMobPos.x < this.vecNodePos.x) {
                    this.xPathStartVec = new Vec3(this.vecNodePos.x - 1.0, this.vecNodePos.y - 1.0, this.vecNodePos.z + 0.5);
                    this.xPathTargetVec = new Vec3(this.vecNodePos.x + 1.0, this.vecNodePos.y - 1.0, this.vecNodePos.z + 0.5);
                } else {
                    this.xPathStartVec = new Vec3(this.vecNodePos.x + 1.0, this.vecNodePos.y - 1.0, this.vecNodePos.z + 0.5);
                    this.xPathTargetVec = new Vec3(this.vecNodePos.x - 1.0, this.vecNodePos.y - 1.0, this.vecNodePos.z + 0.5);
                }
            }

            if (this.zPathStartVec == null) {
                if (vecOldMobPos.z < this.vecNodePos.z) {
                    this.zPathStartVec = new Vec3(this.vecNodePos.x + 0.5, this.vecNodePos.y - 1.0, this.vecNodePos.z - 1.0);
                    this.zPathTargetVec = new Vec3(this.vecNodePos.x + 0.5, this.vecNodePos.y - 1.0, this.vecNodePos.z + 1.0);
                } else {
                    this.zPathStartVec = new Vec3(this.vecNodePos.x + 0.5, this.vecNodePos.y - 1.0, this.vecNodePos.z + 1.0);
                    this.zPathTargetVec = new Vec3(this.vecNodePos.x + 0.5, this.vecNodePos.y - 1.0, this.vecNodePos.z - 1.0);
                }
            }

            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(
                    this.xPathTargetVec.x, this.xPathTargetVec.y, this.xPathTargetVec.z
            );

            BlockState blockstate = this.cavedweller.level.getBlockState(blockpos$mutableblockpos);
            boolean xBlocked = blockstate.getMaterial().blocksMotion();

            blockpos$mutableblockpos = new BlockPos.MutableBlockPos(this.zPathTargetVec.x, this.zPathTargetVec.y, this.zPathTargetVec.z);
            blockstate = this.cavedweller.level.getBlockState(blockpos$mutableblockpos);
            boolean zBlocked = blockstate.getMaterial().blocksMotion();

            if (xBlocked) {
                this.vecMobPos = this.zPathStartVec;
                this.vecTargetPos = this.zPathTargetVec;
            }

            if (zBlocked) {
                this.vecMobPos = this.xPathStartVec;
                this.vecTargetPos = this.xPathTargetVec;
            }

            if (this.vecTargetPos != null && this.vecMobPos != null) {
                ++this.currentTicksToSqueeze;

                float tickF = (float) this.currentTicksToSqueeze / (float) this.ticksToSqueeze;
                Vec3 vecCurrentMobPos = new Vec3(
                        lerp(this.vecMobPos.x, this.vecTargetPos.x, tickF),
                        this.vecMobPos.y,
                        lerp(this.vecMobPos.z, this.vecTargetPos.z, tickF)
                );

                Vec3 rotAxis = new Vec3(this.vecTargetPos.x - this.vecMobPos.x, 0.0, this.vecTargetPos.z - this.vecMobPos.z);
                rotAxis = rotAxis.normalize();

                double rotAngle = Math.toDegrees(Math.atan2(-rotAxis.x, rotAxis.z));
                this.cavedweller.setYHeadRot((float) rotAngle);
                this.cavedweller.moveTo(vecCurrentMobPos.x, vecCurrentMobPos.y, vecCurrentMobPos.z, (float) rotAngle, (float) rotAngle);

                if (tickF >= 1.0F) {
                    this.cavedweller.setPos(this.vecTargetPos.x, this.vecTargetPos.y, this.vecTargetPos.z);
                    this.stopSqueezing();
                }
            } else {
                this.stopSqueezing();
            }
        }
    }

    private void stopSqueezing() {
        this.squeezing = false;
        this.cavedweller.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, false);
        this.cavedweller.setNoGravity(false);
        this.cavedweller.noPhysics = false;
    }

    private void startSqueezing() {
        this.vecNodePos = null;
        this.vecMobPos = null;
        this.xPathStartVec = null;
        this.zPathStartVec = null;
        this.xPathTargetVec = null;
        this.zPathTargetVec = null;
        this.vecTargetPos = null;
        this.currentTicksToSqueeze = 0;
        this.squeezing = true;
        this.cavedweller.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, true);
        this.nodePos = null;
    }

    private boolean checkIfShouldSqueeze(final Path pathToCheck) {
        if (pathToCheck == null) {
            return false;
        } else {
            BlockPos blockpos;

            if (!pathToCheck.isDone()) {
                blockpos = pathToCheck.getNextNodePos();
                if (this.nodePositionCooldownPos != null
                        && blockpos.getX() == (int) this.nodePositionCooldownPos.x
                        && blockpos.getY() == (int) this.nodePositionCooldownPos.y
                        && blockpos.getZ() == (int) this.nodePositionCooldownPos.z) {
                    return false;
                } else {
                    BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(blockpos.getX(), blockpos.getY() + 1, blockpos.getZ());
                    BlockState blockstate = this.cavedweller.level.getBlockState(blockpos$mutableblockpos);
                    return blockstate.getMaterial().blocksMotion();
                }
            } else {
                return false;
            }
        }
    }

    private void aggroTick() {
        this.cavedweller.playChaseSound();
        this.cavedweller.noPhysics = false;
        this.cavedweller.setNoGravity(false);

        LivingEntity target = this.mob.getTarget();

        Path path = this.mob.getNavigation().getPath();

        if (this.shouldUseShortPath) {
            path = this.getShortPath(target);
        }

        if (this.checkIfShouldSqueeze(path)) {
            this.startSqueezing();
            this.squeezing = true;
            this.cavedweller.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, true);
        } else {
            if (target != null) {
                double distance = this.mob.distanceToSqr(target);
                this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);

                if ((this.followingTargetEvenIfNotSeen || this.mob.getSensing().hasLineOfSight(target))
                        && this.ticksUntilNextPathRecalculation <= 0
                        && (
                        this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0
                                || target.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0
                                || this.mob.getRandom().nextFloat() < 0.05F
                )) {
                    this.pathedTargetX = target.getX();
                    this.pathedTargetY = target.getY();
                    this.pathedTargetZ = target.getZ();
                    this.ticksUntilNextPathRecalculation = 2;
                    if (this.canPenalize) {
                        this.ticksUntilNextPathRecalculation += this.failedPathFindingPenalty;

                        if (path != null) {
                            Node finalPathPoint = path.getEndNode();

                            if (finalPathPoint != null && target.distanceToSqr(finalPathPoint.x, finalPathPoint.y, finalPathPoint.z) < 1.0) {
                                this.failedPathFindingPenalty = 0;
                            } else {
                                this.failedPathFindingPenalty += 10;
                            }
                        } else {
                            this.failedPathFindingPenalty += 10;
                        }
                    }

                    if (path != null) {
                        Node finalPathPoint = path.getEndNode();

                        if (finalPathPoint == null || !(target.distanceToSqr(finalPathPoint.x, finalPathPoint.y, finalPathPoint.z) < 1.0)) {
                            this.shouldUseShortPath = true;
                        }
                    } else {
                        this.shouldUseShortPath = true;
                    }

                    if (distance > 1024.0) {
                        this.ticksUntilNextPathRecalculation += 10;
                    } else if (distance > 256.0) {
                        this.ticksUntilNextPathRecalculation += 5;
                    }

                    if (!this.shouldUseShortPath) {
                        if (!this.mob.getNavigation().moveTo(target, this.speedModifier)) {
                            this.cavedweller.startedMovingChase = true;
                            this.ticksUntilNextPathRecalculation += 8;
                        }
                    } else if (!this.mob.getNavigation().moveTo(this.shortPath, this.speedModifier)) {
                        this.cavedweller.startedMovingChase = true;
                        this.ticksUntilNextPathRecalculation += 8;
                    }

                    this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
                }

                this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
                this.checkAndPerformAttack(target, distance);
            }
        }
    }

    private boolean inPlayerLineOfSight() {
        LivingEntity target = this.cavedweller.getTarget();
        return target != null && target.hasLineOfSight(this.cavedweller);
    }

    private void checkAndPerformAttack(final LivingEntity target, double distanceToTarget) {
        double attackReach = this.getAttackReachSqr(target);

        if (distanceToTarget <= attackReach && this.ticksUntilNextAttack <= 0) {
            this.resetAttackCooldown();
            this.mob.swing(InteractionHand.MAIN_HAND);
            this.mob.doHurtTarget(target);
        }
    }

    private void resetAttackCooldown() {
        this.ticksUntilNextAttack = this.adjustedTickDelay(20);
    }

    private double getAttackReachSqr(final LivingEntity target) {
        return this.mob.getBbWidth() * 4.0F * this.mob.getBbWidth() * 4.0F + target.getBbWidth();
    }

    private static double lerp(double a, double b, double f) {
        return (b - a) * f + a;
    }
}
