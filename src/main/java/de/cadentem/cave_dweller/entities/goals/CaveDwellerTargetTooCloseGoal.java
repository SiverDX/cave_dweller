package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

public class CaveDwellerTargetTooCloseGoal extends NearestAttackableTargetGoal<Player> {
    private final CaveDwellerEntity caveDweller;
    private final float distanceThreshold;

    private Player pendingTarget;

    public CaveDwellerTargetTooCloseGoal(final CaveDwellerEntity mob, float distanceThreshold) {
        super(mob, Player.class, false);
        this.caveDweller = mob;
        this.distanceThreshold = distanceThreshold;
    }

    public void setPendingTarget(@Nullable final Player pendingTarget) {
        this.pendingTarget = pendingTarget;
    }

    public boolean inPlayerLineOfSight() {
        return pendingTarget != null && pendingTarget.hasLineOfSight(caveDweller);
    }

    @Override
    public boolean canUse() {
        if (caveDweller.isInvisible()) {
            return false;
        } else {
            setPendingTarget(caveDweller.level().getNearestPlayer(caveDweller, distanceThreshold));

            if (pendingTarget == null) {
                return false;
            } else {
                return !pendingTarget.isCreative() && inPlayerLineOfSight();
            }
        }
    }

    @Override
    public void start() {
        caveDweller.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, true);
        caveDweller.isAggro = true;
        caveDweller.reRollResult = 0;
        super.target = pendingTarget;
        caveDweller.setTarget(pendingTarget);
        super.start();
    }

    @Override
    public void stop() {
        pendingTarget = null;
        super.stop();
    }

    @Override
    public boolean canContinueToUse() {
        if (pendingTarget.isCreative()) {
            return false;
        } else {
            return pendingTarget != null;
        }
    }

    @Override
    public void tick() {
        super.tick();
    }
}
