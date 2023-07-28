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
    private final boolean canPenalize = false; // TODO :: Add config?
    private final boolean followTargetEvenIfNotSeen;

    private long lastGameTimeCheck;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilLeave;
    private int ticksUntilNextAttack;

    public CaveDwellerChaseGoal(final CaveDwellerEntity caveDweller, double speedModifier, boolean followTargetEvenIfNotSeen, float ticksUntilChase) {
        this.caveDweller = caveDweller;
        this.followTargetEvenIfNotSeen = followTargetEvenIfNotSeen;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.ticksUntilLeave = Utils.secondsToTicks(ServerConfig.TIME_UNTIL_LEAVE_CHASE.get());
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

        // TODO :: Not entirely sure why this is here, performance?
        long ticks = caveDweller.level.getGameTime();

        if (ticks - lastGameTimeCheck < 20) {
            return false;
        }

        lastGameTimeCheck = ticks;
        LivingEntity target = caveDweller.getTarget();

        if (!Utils.isValidPlayer(target)) {
            return false;
        }

        Path path;

        if (canPenalize) { // TODO :: This is currently always false
            if (--ticksUntilNextPathRecalculation <= 0) {
                path = caveDweller.getNavigation().createPath(target, 0);
                ticksUntilNextPathRecalculation = 2;

                return path != null;
            } else {
                return true;
            }
        }

        path = caveDweller.getNavigation().createPath(target, 0);

        if (path != null) {
            return true;
        }

        // Check if the Cave Dweller can already reach the target
        boolean canAttack = getAttackReachSqr(target) >= caveDweller.distanceToSqr(target.getX(), target.getY(), target.getZ());

        if (!canAttack) {
            path = caveDweller.createShortPath(target);

            return path != null;
        }

        return false;
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

        caveDweller.isSqueezing = false;
        caveDweller.refreshDimensions();
        caveDweller.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        // FIXME :: fix climbing
        // TODO :: Move slow for 1-2 seconds then run
        if (ticksUntilLeave <= 0 && !caveDweller.targetIsLookingAtMe) {
            caveDweller.disappear();
        }

        LivingEntity target = caveDweller.getTarget();

        if (!Utils.isValidPlayer(target)) {
            return;
        }

        Path path = caveDweller.getNavigation().getPath();

        if (path == null || path.isDone()) {
            path = caveDweller.getNavigation().createPath(target, 0);
        }

        // No path could be found, try with smaller size
        if (path == null || path.isDone()) {
            caveDweller.isSqueezing = true;
            caveDweller.refreshDimensions();
            path = caveDweller.getNavigation().createPath(target, 0);
        }

        if (path != null && !path.isDone()) {
            boolean isAboveSolid = caveDweller.level.getBlockState(caveDweller.blockPosition().above()).getMaterial().isSolid();
            boolean isNextAboveSolid = caveDweller.level.getBlockState(path.getNextNodePos().above()).getMaterial().isSolid();

            caveDweller.isSqueezing = isAboveSolid || isNextAboveSolid;
            caveDweller.getEntityData().set(CaveDwellerEntity.CRAWLING_ACCESSOR, caveDweller.isSqueezing);
            caveDweller.refreshDimensions();
        }

        caveDweller.getNavigation().moveTo(path, caveDweller.isSqueezing ? 0.3 : 1);

        if (!caveDweller.isSqueezing) {
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
}
