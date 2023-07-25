package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CaveDwellerChaseGoal extends Goal {
    private final CaveDwellerEntity caveDweller;
    private final float ticksUntilChase;
    private final double speedModifier;
    private final int ticksToSqueeze;
    private final boolean canPenalize = false; // TODO :: Add config?
    private final boolean followTargetEvenIfNotSeen;

    private Path shortPath;
    private BlockPos nodePosition;
    private Vec3 vecNodePosition;
    private Vec3 vecMobPos;
    private Vec3 xPathStartVec;
    private Vec3 zPathStartVec;
    private Vec3 xPathTargetVec;
    private Vec3 zPathTargetVec;
    private Vec3 vecTargetPos;
    private Vec3 previousNodePosition;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private float currentTicksUntilChase;
    private long lastGameTimeCheck;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private int failedPathFindingPenalty;
    private int currentTicksToSqueeze;
    private int ticksUntilLeave;
    private boolean squeezing;

    public CaveDwellerChaseGoal(final CaveDwellerEntity caveDweller, double speedModifier, boolean followTargetEvenIfNotSeen, float ticksUntilChase) {
        this.caveDweller = caveDweller;
        this.speedModifier = speedModifier;
        this.followTargetEvenIfNotSeen = followTargetEvenIfNotSeen;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.ticksUntilChase = ticksUntilChase;
        this.currentTicksUntilChase = ticksUntilChase;
        this.ticksToSqueeze = 15;
        this.ticksUntilLeave = Utils.secondsToTicks(ServerConfig.TIME_UNTIL_LEAVE_CHASE.get());
    }

    @Override
    public boolean canUse() {
        if (caveDweller.isInvisible()) {
            return false;
        } else if (caveDweller.currentRoll != Roll.CHASE || !caveDweller.isLookingAtMe(caveDweller.getTarget())) {
            return false;
        } else {
            long ticks = caveDweller.level().getGameTime();

            if (ticks - lastGameTimeCheck < 20) {
                return false;
            } else {
                lastGameTimeCheck = ticks;
                LivingEntity target = caveDweller.getTarget();

                Path path;
                if (target == null) {
                    return false;
                } else if (!target.isAlive()) {
                    return false;
                } else if (canPenalize) { // TODO :: This is always false
                    if (--ticksUntilNextPathRecalculation <= 0) {
                        path = caveDweller.getNavigation().createPath(target, 0);
                        ticksUntilNextPathRecalculation = 2;

                        return path != null;
                    } else {
                        return true;
                    }
                } else {
                    path = caveDweller.getNavigation().createPath(target, 0);

                    if (path != null) {
                        return true;
                    } else {
                        boolean canAttack = getAttackReachSqr(target) >= caveDweller.distanceToSqr(target.getX(), target.getY(), target.getZ());

                        if (!canAttack) {
                            path = caveDweller.createShortPath(target);

                            return path != null;
                        }

                        return false;
                    }
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = caveDweller.getTarget();

        if (target == null) {
            return false;
        } else if (!target.isAlive()) {
            caveDweller.disappear();
            return false;
        } else if (!followTargetEvenIfNotSeen) {
            return !caveDweller.getNavigation().isDone();
        } else if (!caveDweller.isWithinRestriction(target.blockPosition())) {
            return false;
        } else {
            return Utils.isValidPlayer(target);
        }
    }

    @Override
    public void start() {
        this.caveDweller.setAggressive(true);
        ticksUntilNextPathRecalculation = 0;
        ticksUntilNextAttack = 0;
    }

    @Override
    public void stop() {
        LivingEntity target = caveDweller.getTarget();

        if (!Utils.isValidPlayer(target)) {
            caveDweller.setTarget(null);
        }

        caveDweller.squeezeCrawling = false;
        caveDweller.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, false);
        caveDweller.refreshDimensions();
        currentTicksUntilChase = ticksUntilChase;
        caveDweller.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        caveDweller.squeezeCrawling = squeezing;
        LivingEntity target = caveDweller.getTarget();

        tickAggroClock();

        if (!squeezing && target != null) {
            if (caveDweller.isAggressive()) {
                caveDweller.getLookControl().setLookAt(target, 90.0F, 90.0F);
            } else {
                caveDweller.getLookControl().setLookAt(target, 180.0F, 1.0F);
            }
        }

        if (caveDweller.getEntityData().get(CaveDwellerEntity.AGGRO_ACCESSOR)) {
            if (squeezing) {
                squeezingTick();
            } else {
                aggroTick();
            }
        }

        --ticksUntilLeave;
        if (ticksUntilLeave <= 0 && (!caveDweller.isLookingAtMe(target) || !inPlayerLineOfSight())) {
            caveDweller.disappear();
        }
    }

    private void tickAggroClock() {
        --currentTicksUntilChase;

        if (currentTicksUntilChase <= 0.0F) {
            caveDweller.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, true);
        }

        caveDweller.refreshDimensions();
    }

    private Path getShortPath(final LivingEntity target) {
        shortPath = caveDweller.createShortPath(target);
        return shortPath;
    }

    private void squeezingTick() {
        Path path = caveDweller.getNavigation().getPath();

        if (path != null && !path.isDone()) {
            nodePosition = path.getNextNodePos();
        }

        caveDweller.getNavigation().stop();

        if (nodePosition == null) {
            stopSqueezing();
        } else {
            if (vecNodePosition == null) {
                vecNodePosition = new Vec3(nodePosition.getX(), nodePosition.getY(), nodePosition.getZ());
            }

            previousNodePosition = vecNodePosition;
            Vec3 vecOldMobPos = caveDweller.getPosition(1.0F);

            if (xPathStartVec == null) {
                if (vecOldMobPos.x < vecNodePosition.x) {
                    xPathStartVec = new Vec3(vecNodePosition.x - 1.0, vecNodePosition.y - 1.0, vecNodePosition.z + 0.5);
                    xPathTargetVec = new Vec3(vecNodePosition.x + 1.0, vecNodePosition.y - 1.0, vecNodePosition.z + 0.5);
                } else {
                    xPathStartVec = new Vec3(vecNodePosition.x + 1.0, vecNodePosition.y - 1.0, vecNodePosition.z + 0.5);
                    xPathTargetVec = new Vec3(vecNodePosition.x - 1.0, vecNodePosition.y - 1.0, vecNodePosition.z + 0.5);
                }
            }

            if (zPathStartVec == null) {
                if (vecOldMobPos.z < vecNodePosition.z) {
                    zPathStartVec = new Vec3(vecNodePosition.x + 0.5, vecNodePosition.y - 1.0, vecNodePosition.z - 1.0);
                    zPathTargetVec = new Vec3(vecNodePosition.x + 0.5, vecNodePosition.y - 1.0, vecNodePosition.z + 1.0);
                } else {
                    zPathStartVec = new Vec3(vecNodePosition.x + 0.5, vecNodePosition.y - 1.0, vecNodePosition.z + 1.0);
                    zPathTargetVec = new Vec3(vecNodePosition.x + 0.5, vecNodePosition.y - 1.0, vecNodePosition.z - 1.0);
                }
            }

            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(xPathTargetVec.x, xPathTargetVec.y, xPathTargetVec.z);

            BlockState blockstate = caveDweller.level().getBlockState(blockpos$mutableblockpos);
            boolean xBlocked = blockstate.blocksMotion();

            blockpos$mutableblockpos = new BlockPos.MutableBlockPos(zPathTargetVec.x, zPathTargetVec.y, zPathTargetVec.z);
            blockstate = caveDweller.level().getBlockState(blockpos$mutableblockpos);
            boolean zBlocked = blockstate.blocksMotion();

            if (xBlocked) {
                vecMobPos = zPathStartVec;
                vecTargetPos = zPathTargetVec;
            }

            if (zBlocked) {
                vecMobPos = xPathStartVec;
                vecTargetPos = xPathTargetVec;
            }

            if (vecTargetPos != null && vecMobPos != null) {
                ++currentTicksToSqueeze;

                float tickF = (float) currentTicksToSqueeze / (float) ticksToSqueeze;

                Vec3 vecCurrentMobPos = new Vec3(
                        lerp(vecMobPos.x, vecTargetPos.x, tickF),
                        vecMobPos.y,
                        lerp(vecMobPos.z, vecTargetPos.z, tickF)
                );

                Vec3 rotAxis = new Vec3(vecTargetPos.x - vecMobPos.x, 0.0, vecTargetPos.z - vecMobPos.z);
                rotAxis = rotAxis.normalize();

                double rotAngle = Math.toDegrees(Math.atan2(-rotAxis.x, rotAxis.z));
                caveDweller.setYHeadRot((float) rotAngle);
                caveDweller.moveTo(vecCurrentMobPos.x, vecCurrentMobPos.y, vecCurrentMobPos.z, (float) rotAngle, (float) rotAngle);

                if (tickF >= 1.0F) {
                    caveDweller.setPos(vecTargetPos.x, vecTargetPos.y, vecTargetPos.z);
                    stopSqueezing();
                }
            } else {
                stopSqueezing();
            }
        }
    }

    private void stopSqueezing() {
        squeezing = false;
        caveDweller.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, false);
    }

    private void startSqueezing() {
        vecNodePosition = null;
        vecMobPos = null;
        xPathStartVec = null;
        zPathStartVec = null;
        xPathTargetVec = null;
        zPathTargetVec = null;
        vecTargetPos = null;
        currentTicksToSqueeze = 0;
        squeezing = true;
        caveDweller.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, true);
        nodePosition = null;
    }

    private boolean shouldSqueeze(final Path pathToCheck) {
        if (pathToCheck == null) {
            return false;
        } else {
            BlockPos blockPosition;

            if (!pathToCheck.isDone()) {
                blockPosition = pathToCheck.getNextNodePos();

                // Don't bother checking if the block to check is at the same location as the previous check
                if (previousNodePosition != null && blockPosition.getX() == (int) previousNodePosition.x && blockPosition.getY() == (int) previousNodePosition.y && blockPosition.getZ() == (int) previousNodePosition.z) {
                    return false;
                } else {
                    BlockState blockstate = caveDweller.level().getBlockState(blockPosition.above());
                    return blockstate.blocksMotion();
                }
            } else {
                return false;
            }
        }
    }

    private void aggroTick() {
        caveDweller.playChaseSound();
        LivingEntity target = caveDweller.getTarget();

        boolean shouldUseShortPath = true;
        Path path = caveDweller.getNavigation().getPath();

        if (path != null) {
            Node finalPathPoint = path.getEndNode();

            if (finalPathPoint != null && !path.isDone()) {
                shouldUseShortPath = false;
            }
        }

        if (shouldUseShortPath) {
            // No normal path could be found, try with smaller size
            path = getShortPath(target);
        }

        if (shouldSqueeze(path)) {
            startSqueezing();
            squeezing = true;
            caveDweller.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, true);
        } else if (target != null) {
            double distance = caveDweller.distanceToSqr(target);
            ticksUntilNextPathRecalculation = Math.max(ticksUntilNextPathRecalculation - 1, 0);

            if (ticksUntilNextPathRecalculation == 0
                    && (followTargetEvenIfNotSeen || caveDweller.getSensing().hasLineOfSight(target)) // Only chase if the player can see the cave dweller
                    && (
                    pathedTargetX == 0 && pathedTargetY == 0 && pathedTargetZ == 0 // First time entering this part?
                            || target.distanceToSqr(pathedTargetX, pathedTargetY, pathedTargetZ) >= 1 // More than 1 block apart to the target | TODO :: Why not update the location before this check?
                            || caveDweller.getRandom().nextFloat() < 0.05 // Why the random chance?
            )) {
                pathedTargetX = target.getX();
                pathedTargetY = target.getY();
                pathedTargetZ = target.getZ();
                ticksUntilNextPathRecalculation = 2;

                // If a path could not be found add a delay before the next check || TODO :: Currently not used
                if (canPenalize) {
                    ticksUntilNextPathRecalculation += failedPathFindingPenalty;

                    if (path != null) {
                        Node finalPathPoint = path.getEndNode();

                        if (finalPathPoint != null && target.distanceToSqr(finalPathPoint.x, finalPathPoint.y, finalPathPoint.z) < 1) {
                            failedPathFindingPenalty = 0;
                        } else {
                            failedPathFindingPenalty += 10;
                        }
                    } else {
                        failedPathFindingPenalty += 10;
                    }
                }

                // FIXME :: Not sure what the point of this is - to avoid doing long path calculations too often?
                if (distance > 1024.0) {
                    ticksUntilNextPathRecalculation += 10;
                } else if (distance > 256.0) {
                    ticksUntilNextPathRecalculation += 5;
                }

                boolean canMoveTo;

                if (shouldUseShortPath) {
                    canMoveTo = caveDweller.getNavigation().moveTo(shortPath, speedModifier);
                } else {
                    canMoveTo = caveDweller.getNavigation().moveTo(target, speedModifier);
                }

                if (!canMoveTo) {
                    ticksUntilNextPathRecalculation += 8;
                }

                ticksUntilNextPathRecalculation = adjustedTickDelay(ticksUntilNextPathRecalculation);
            }

            ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
            checkAndPerformAttack(target, distance);
        }
    }

    private boolean inPlayerLineOfSight() {
        LivingEntity target = caveDweller.getTarget();
        return target != null && target.hasLineOfSight(caveDweller);
    }

    private void checkAndPerformAttack(final LivingEntity target, double distanceToTarget) {
        double attackReach = getAttackReachSqr(target);

        if (distanceToTarget <= attackReach && ticksUntilNextAttack <= 0) {
            resetAttackCooldown();
            caveDweller.swing(InteractionHand.MAIN_HAND);
            caveDweller.doHurtTarget(target);
        }
    }

    private void resetAttackCooldown() {
        ticksUntilNextAttack = adjustedTickDelay(20);
    }

    /**
     * Basically overwrite of {@link net.minecraft.world.entity.monster.Monster#getMeleeAttackRangeSqr(LivingEntity)} but with a higher radius
     */
    private double getAttackReachSqr(final LivingEntity target) {
        // FIXME :: Why not just override the method in the CaveDwellerEntity?
        return caveDweller.getBbWidth() * 4.0F * caveDweller.getBbWidth() * 4.0F + target.getBbWidth();
    }

    private static double lerp(double a, double b, double f) {
        return (b - a) * f + a;
    }
}
