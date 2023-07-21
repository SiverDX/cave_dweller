package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class CaveDwellerFleeGoal extends Goal {
    private final CaveDwellerEntity mob;
    private final double speedModifier;

    private float ticksUntilLeave;
    private float ticksUntilFlee;
    private boolean shouldLeave;
    private Path fleePath;
    private int ticksUntilNextPathRecalculation;

    public CaveDwellerFleeGoal(final CaveDwellerEntity mob, float ticksUntilLeave, double speedModifier) {
        this.mob = mob;
        this.ticksUntilLeave = ticksUntilLeave;
        this.ticksUntilFlee = 10.0F;
        this.speedModifier = speedModifier;
    }

    @Override
    public boolean canUse() {
        if (mob.isInvisible()) {
            return false;
        } else if (mob.reRollResult != 2) {
            return false;
        } else {
            return mob.getTarget() != null;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.reRollResult != 2) {
            return false;
        } else {
            return mob.getTarget() != null;
        }
    }

    @Override
    public void start() {
        setFleePath();
        mob.spottedByPlayer = false;
        shouldLeave = false;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();

        if (shouldLeave && (!mob.isLookingAtMe(target) || !inPlayerLineOfSight())) {
            mob.playDisappearSound();
            mob.discard();
        }

        tickFleeClock();
        tickStareClock();

        if (ticksUntilFlee <= 0.0F) {
            fleeTick();
            mob.isFleeing = true;
            mob.getEntityData().set(CaveDwellerEntity.FLEEING_ACCESSOR, true);
        } else if (target != null) {
            mob.getLookControl().setLookAt(target, 180.0F, 1.0F);
        }
    }

    private boolean inPlayerLineOfSight() {
        return mob.getTarget() != null && mob.getTarget().hasLineOfSight(mob);
    }

    private void setFleePath() {
        LivingEntity target = mob.getTarget();

        if (target == null) {
            return;
        }

        Vec3 fleePosition = DefaultRandomPos.getPosAway(this.mob, 32, 7, target.position());

        if (fleePosition != null) {
            fleePath = mob.getNavigation().createPath(fleePosition.x, fleePosition.y, fleePosition.z, 0);

            if (fleePath == null) {
                fleePath = mob.createShortPath(fleePosition);
            }
        }
    }

    public void tickStareClock() {
        --ticksUntilLeave;

        if (ticksUntilLeave < 0.0F) {
            shouldLeave = true;
        }
    }

    void tickFleeClock() {
        --ticksUntilFlee;
    }

    public void fleeTick() {
        if (fleePath == null) {
            setFleePath();
        }

        mob.playFleeSound();
        ticksUntilNextPathRecalculation = Math.max(ticksUntilNextPathRecalculation - 1, 0);

        if (ticksUntilNextPathRecalculation == 0) {
            ticksUntilNextPathRecalculation = 2;

            if (!mob.getNavigation().moveTo(fleePath, speedModifier)) {
                ticksUntilNextPathRecalculation += 2;
            }

            ticksUntilNextPathRecalculation = adjustedTickDelay(ticksUntilNextPathRecalculation);
        }
    }
}
