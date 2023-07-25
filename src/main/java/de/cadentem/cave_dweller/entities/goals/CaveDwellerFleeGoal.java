package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class CaveDwellerFleeGoal extends Goal {
    private final CaveDwellerEntity caveDweller;
    private final double speedModifier;

    private float ticksUntilLeave;
    private float ticksUntilFlee;
    private boolean shouldLeave;
    private Path fleePath;
    private int ticksUntilNextPathRecalculation;

    public CaveDwellerFleeGoal(final CaveDwellerEntity caveDweller, float ticksUntilLeave, double speedModifier) {
        this.caveDweller = caveDweller;
        this.ticksUntilLeave = ticksUntilLeave;
        this.ticksUntilFlee = 10.0F;
        this.speedModifier = speedModifier;
    }

    @Override
    public boolean canUse() {
        if (caveDweller.isInvisible()) {
            return false;
        } else if (caveDweller.currentRoll != Roll.FLEE) {
            return false;
        } else {
            return caveDweller.getTarget() != null;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (caveDweller.currentRoll != Roll.FLEE) {
            return false;
        } else {
            return caveDweller.getTarget() != null;
        }
    }

    @Override
    public void start() {
        setFleePath();
        caveDweller.spottedByPlayer = false;
        shouldLeave = false;
    }

    @Override
    public void tick() {
        LivingEntity target = caveDweller.getTarget();

        if (shouldLeave && (!caveDweller.isLookingAtMe(target) || !caveDweller.inTargetLineOfSight())) {
            caveDweller.disappear();
        }

        --ticksUntilFlee;
        tickStareClock();

        if (ticksUntilFlee <= 0.0F) {
            fleeTick();
            caveDweller.isFleeing = true;
            caveDweller.getEntityData().set(CaveDwellerEntity.FLEEING_ACCESSOR, true);
        } else if (target != null) {
            caveDweller.getLookControl().setLookAt(target, 180.0F, 1.0F);
        }
    }

    private void setFleePath() {
        LivingEntity target = caveDweller.getTarget();

        if (target == null) {
            return;
        }

        Vec3 fleePosition = DefaultRandomPos.getPosAway(this.caveDweller, 32, 7, target.position());

        if (fleePosition != null) {
            fleePath = caveDweller.getNavigation().createPath(fleePosition.x, fleePosition.y, fleePosition.z, 0);

            if (fleePath == null) {
                fleePath = caveDweller.createShortPath(fleePosition);
            }
        }
    }

    public void tickStareClock() {
        --ticksUntilLeave;

        if (ticksUntilLeave < 0.0F) {
            shouldLeave = true;
        }
    }

    public void fleeTick() {
        if (fleePath == null || fleePath.isDone()) {
            setFleePath();
        }

        caveDweller.playFleeSound();
        ticksUntilNextPathRecalculation = Math.max(ticksUntilNextPathRecalculation - 1, 0);

        if (ticksUntilNextPathRecalculation == 0) {
            ticksUntilNextPathRecalculation = 2;

            if (!caveDweller.getNavigation().moveTo(fleePath, speedModifier)) {
                ticksUntilNextPathRecalculation += 2;
            }

            ticksUntilNextPathRecalculation = adjustedTickDelay(ticksUntilNextPathRecalculation);
        }
    }
}
