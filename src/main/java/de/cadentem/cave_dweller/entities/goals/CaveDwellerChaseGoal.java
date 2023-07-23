package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CaveDwellerChaseGoal extends Goal {
    protected final CaveDwellerEntity mob;
    private final double speedModifier;
    private final boolean followTargetEvenIfNotSeen;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private int failedPathFindingPenalty = 0;
    private final boolean canPenalize = false; // TODO :: Add config?
    private final float ticksUntilChase;
    private float currentTicksUntilChase;
    private boolean squeezing;
    private Path shortPath;
    private Vec3 vecNodePosition;
    private Vec3 vecMobPos;
    private final int ticksToSqueeze;
    private int currentTicksToSqueeze;
    private int ticksUntilLeave;
    private long lastGameTimeCheck;
    private Vec3 xPathStartVec;
    private Vec3 zPathStartVec;
    private Vec3 xPathTargetVec;
    private Vec3 zPathTargetVec;
    private Vec3 vecTargetPos;
    private Vec3 previousNodePosition;
    private BlockPos nodePosition;

    public CaveDwellerChaseGoal(final CaveDwellerEntity mob, double speedModifier, boolean followTargetEvenIfNotSeen, float ticksUntilChase) {
        this.mob = mob;
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
        if (mob.isInvisible()) {
            return false;
        } else if (mob.reRollResult != 0) {
            return false;
        } else {
            long ticks = mob.level.getGameTime();

            if (ticks - lastGameTimeCheck < 20) {
                return false;
            } else {
                lastGameTimeCheck = ticks;
                LivingEntity target = mob.getTarget();

                Path path;
                if (target == null) {
                    return false;
                } else if (!target.isAlive()) {
                    return false;
                } else if (canPenalize) { // TODO :: This is always false
                    if (--ticksUntilNextPathRecalculation <= 0) {
                        path = mob.getNavigation().createPath(target, 0);
                        ticksUntilNextPathRecalculation = 2;

                        return path != null;
                    } else {
                        return true;
                    }
                } else {
                    path = mob.getNavigation().createPath(target, 0);

                    if (path != null) {
                        return true;
                    } else {
                        boolean canAttack = getAttackReachSqr(target) >= mob.distanceToSqr(target.getX(), target.getY(), target.getZ());

                        if (!canAttack) {
                            path = mob.createShortPath(target);

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
        LivingEntity target = mob.getTarget();

        if (target == null) {
            return false;
        } else if (!target.isAlive()) {
            mob.discard();

            return false;
        } else if (!followTargetEvenIfNotSeen) {
            return !mob.getNavigation().isDone();
        } else if (!mob.isWithinRestriction(target.blockPosition())) {
            return false;
        } else {
            return !(target instanceof Player player) || !target.isSpectator() && !player.isCreative();
        }
    }

    @Override
    public void start() {
        ticksUntilNextPathRecalculation = 0;
        ticksUntilNextAttack = 0;
    }

    @Override
    public void stop() {
        LivingEntity target = mob.getTarget();

        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
            mob.setTarget(null);
        }

        mob.squeezeCrawling = false;
        mob.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, false);
        mob.isAggro = false;
        mob.refreshDimensions();
        currentTicksUntilChase = ticksUntilChase;
        mob.setAggressive(false);
        mob.getNavigation().stop();
        mob.setNoGravity(false);
        mob.noPhysics = false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        mob.squeezeCrawling = squeezing;
        LivingEntity target = mob.getTarget();

        tickAggroClock();

        if (!squeezing && target != null) {
            if (mob.isAggro) {
                mob.getLookControl().setLookAt(target, 90.0F, 90.0F);
            } else {
                mob.getLookControl().setLookAt(target, 180.0F, 1.0F);
            }
        }

        if (mob.getEntityData().get(CaveDwellerEntity.AGGRO_ACCESSOR)) {
            if (squeezing) {
                squeezingTick();
            } else {
                aggroTick();
            }
        }

        --ticksUntilLeave;
        if (ticksUntilLeave <= 0 && (!mob.isLookingAtMe(target) || !inPlayerLineOfSight())) {
            mob.discard();
        }
    }

    private void tickAggroClock() {
        --currentTicksUntilChase;

        if (currentTicksUntilChase <= 0.0F) {
            mob.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, true);
        }

        mob.isAggro = true;
        mob.refreshDimensions();
    }

    private Path getShortPath(final LivingEntity target) {
        shortPath = mob.createShortPath(target);
        return shortPath;
    }

    private void squeezingTick() {
        mob.setNoGravity(true);
        mob.noPhysics = true;

        Path path = mob.getNavigation().getPath();

        if (path != null && !path.isDone()) {
            nodePosition = path.getNextNodePos();
        }

        mob.getNavigation().stop();

        if (nodePosition == null) {
            stopSqueezing();
        } else {
            if (vecNodePosition == null) {
                vecNodePosition = new Vec3(nodePosition.getX(), nodePosition.getY(), nodePosition.getZ());
            }

            previousNodePosition = vecNodePosition;
            Vec3 vecOldMobPos = mob.getPosition(1.0F);

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

            BlockState blockstate = mob.level.getBlockState(blockpos$mutableblockpos);
            boolean xBlocked = blockstate.getMaterial().blocksMotion();

            blockpos$mutableblockpos = new BlockPos.MutableBlockPos(zPathTargetVec.x, zPathTargetVec.y, zPathTargetVec.z);
            blockstate = mob.level.getBlockState(blockpos$mutableblockpos);
            boolean zBlocked = blockstate.getMaterial().blocksMotion();

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
                mob.setYHeadRot((float) rotAngle);
                mob.moveTo(vecCurrentMobPos.x, vecCurrentMobPos.y, vecCurrentMobPos.z, (float) rotAngle, (float) rotAngle);

                if (tickF >= 1.0F) {
                    mob.setPos(vecTargetPos.x, vecTargetPos.y, vecTargetPos.z);
                    stopSqueezing();
                }
            } else {
                stopSqueezing();
            }
        }
    }

    private void stopSqueezing() {
        squeezing = false;
        mob.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, false);
        mob.setNoGravity(false);
        mob.noPhysics = false;
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
        mob.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, true);
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
                    BlockState blockstate = mob.level.getBlockState(blockPosition.above());
                    return blockstate.getMaterial().blocksMotion();
                }
            } else {
                return false;
            }
        }
    }

    private void aggroTick() {
        mob.playChaseSound();
        mob.noPhysics = false;
        mob.setNoGravity(false);

        LivingEntity target = mob.getTarget();

        boolean shouldUseShortPath = true;
        Path path = mob.getNavigation().getPath();

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
            mob.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, true);
        } else if (target != null) {
            double distance = mob.distanceToSqr(target);
            ticksUntilNextPathRecalculation = Math.max(ticksUntilNextPathRecalculation - 1, 0);

            if (ticksUntilNextPathRecalculation == 0
                    && (followTargetEvenIfNotSeen || mob.getSensing().hasLineOfSight(target)) // Only chase if the player can see the cave dweller
                    && (
                    pathedTargetX == 0 && pathedTargetY == 0 && pathedTargetZ == 0 // First time entering this part?
                            || target.distanceToSqr(pathedTargetX, pathedTargetY, pathedTargetZ) >= 1 // More than 1 block apart to the target | TODO :: Why not update the location before this check?
                            || mob.getRandom().nextFloat() < 0.05 // Why the random chance?
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
                    canMoveTo = mob.getNavigation().moveTo(shortPath, speedModifier);
                } else {
                    canMoveTo = mob.getNavigation().moveTo(target, speedModifier);
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
        LivingEntity target = mob.getTarget();
        return target != null && target.hasLineOfSight(mob);
    }

    private void checkAndPerformAttack(final LivingEntity target, double distanceToTarget) {
        double attackReach = getAttackReachSqr(target);

        if (distanceToTarget <= attackReach && ticksUntilNextAttack <= 0) {
            resetAttackCooldown();
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
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
        return mob.getBbWidth() * 4.0F * mob.getBbWidth() * 4.0F + target.getBbWidth();
    }

    private static double lerp(double a, double b, double f) {
        return (b - a) * f + a;
    }
}
