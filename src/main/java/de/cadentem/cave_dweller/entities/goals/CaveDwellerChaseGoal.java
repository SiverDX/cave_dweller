package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

public class CaveDwellerChaseGoal extends Goal {
    private final CaveDwellerEntity caveDweller;
    private final int maxSpeedReached;
    private final boolean followTargetEvenIfNotSeen;

    private long lastGameTimeCheck;
    private int ticksUntilLeave;
    private int ticksUntilNextAttack;
    private int speedUp;

    public CaveDwellerChaseGoal(final CaveDwellerEntity caveDweller, boolean followTargetEvenIfNotSeen) {
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.caveDweller = caveDweller;
        this.followTargetEvenIfNotSeen = followTargetEvenIfNotSeen;
        this.ticksUntilLeave = Utils.secondsToTicks(ServerConfig.TIME_UNTIL_LEAVE_CHASE.get());
        this.maxSpeedReached = Utils.secondsToTicks(3);
    }

    @Override
    public boolean canUse() {
        if (caveDweller.isInvisible()) {
            return false;
        }

        if (caveDweller.currentRoll != Roll.CHASE) {
            return false;
        }

        if (!caveDweller.targetIsLookingAtMe) {
            return false;
        }

        long ticks = caveDweller.level().getGameTime();

        if (ticks - lastGameTimeCheck < 20) {
            return false;
        }

        lastGameTimeCheck = ticks;
        LivingEntity target = caveDweller.getTarget();

        if (!Utils.isValidPlayer(target)) {
            return false;
        }

        Path path = caveDweller.getNavigation().createPath(target, 0);

        if (path != null) {
            return true;
        }

        // Check if the Cave Dweller can already reach the target
        boolean canAttack = getAttackReachSqr(target) >= caveDweller.distanceToSqr(target);

        if (canAttack) {
            return true;
        }

        // Try path with smaller size
        caveDweller.getEntityData().set(CaveDwellerEntity.CRAWLING_ACCESSOR, true);
        caveDweller.refreshDimensions();
        path = caveDweller.getNavigation().createPath(target, 0);

        return path != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = caveDweller.getTarget();

        if (!Utils.isValidPlayer(target)) {
            // Most likely killed the target in this case
            caveDweller.disappear();
            return false;
        }

        if (!followTargetEvenIfNotSeen) {
            return !caveDweller.getNavigation().isDone();
        }

        return caveDweller.isWithinRestriction(target.blockPosition());
    }

    @Override
    public void start() {
        caveDweller.setAggressive(true);
        ticksUntilNextAttack = 0;
    }

    @Override
    public void stop() {
        LivingEntity target = caveDweller.getTarget();

        if (!Utils.isValidPlayer(target)) {
            caveDweller.setTarget(null);
        }

        speedUp = 0;

        caveDweller.setAggressive(false);
        caveDweller.getEntityData().set(CaveDwellerEntity.CRAWLING_ACCESSOR, false);
        caveDweller.getNavigation().stop();
        caveDweller.refreshDimensions();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (ticksUntilLeave <= 0 && !caveDweller.targetIsLookingAtMe) {
            caveDweller.disappear();
        }

        LivingEntity target = caveDweller.getTarget();

        if (!Utils.isValidPlayer(target)) {
            return;
        }

        Path path = caveDweller.getNavigation().getPath();

        if (path == null || path.isDone() || path.getEndNode() == null || path.getEndNode().distanceToSqr(target.blockPosition())  > 0.5) {
            path = caveDweller.getNavigation().createPath(target, 0);
        }

        boolean isCrawling = false;
        boolean shouldClimb = target.getY() > caveDweller.getY(); // TODO :: Add `path != null` and a try-climb logic if it does not get closer to target within x seconds?

        // When the node count is 1 it usually means that no actual path can be found (and the node point is just the target location)
        if (!shouldClimb && (caveDweller.distanceToSqr(target) > 0.3 && (path == null || path.isDone() || path.getNodeCount() == 1))) {
            // No path could be found, try with smaller size
            isCrawling = true;
            caveDweller.getEntityData().set(CaveDwellerEntity.CRAWLING_ACCESSOR, true);
            caveDweller.refreshDimensions();
            path = caveDweller.getNavigation().createPath(target, 0);
        }

        if (path != null && !path.isDone()) {
            if (caveDweller.hasLineOfSight(target)) {
                caveDweller.playChaseSound();
            }

            boolean isAboveSolid = caveDweller.level().getBlockState(caveDweller.blockPosition().above()).isSolid();
            boolean isNextAboveSolid = caveDweller.level().getBlockState(path.getNextNodePos().above()).isSolid();

            /* [x = blocks | o = cave dweller]
             xxxx
              o x
            x o x
            xxxxx
            */
            boolean isFacingSolid = caveDweller.level().getBlockState(caveDweller.blockPosition().relative(caveDweller.getDirection())).isSolid();
            boolean isFacingAboveSolid = caveDweller.level().getBlockState(caveDweller.blockPosition().relative(caveDweller.getDirection()).above()).isSolid();
            boolean extraCheck = isFacingSolid && !isFacingAboveSolid;

            isCrawling = isAboveSolid || isNextAboveSolid || (caveDweller.getEntityData().get(CaveDwellerEntity.CROUCHING_ACCESSOR) && extraCheck);
            caveDweller.getEntityData().set(CaveDwellerEntity.CRAWLING_ACCESSOR, isCrawling);
            caveDweller.refreshDimensions();
        }

        double speedModifier = (0.85 / maxSpeedReached) * speedUp; // FIXME :: Makes animation / movement wonky
        caveDweller.getNavigation().moveTo(path, isCrawling ? 0.3 : 0.85);

        if (!isCrawling) {
            if (caveDweller.isAggressive()) {
                caveDweller.getLookControl().setLookAt(target, 90.0F, 90.0F);
            } else {
                caveDweller.getLookControl().setLookAt(target, 180.0F, 1.0F);
            }
        }

        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
        double distance = caveDweller.distanceToSqr(target);
        checkAndPerformAttack(target, distance);

        ticksUntilLeave--;

        if (speedUp < maxSpeedReached) {
            speedUp++;
        }
    }

    private void checkAndPerformAttack(final LivingEntity target, double distanceToTarget) {
        // TODO :: Check if there is a wall between the player and the cave dweller?
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
}
