package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
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
            setPendingTarget(caveDweller.level.getNearestPlayer(caveDweller, distanceThreshold));
            return Utils.isValidPlayer(pendingTarget) && inPlayerLineOfSight();
        }
    }

    @Override
    public void start() {
        caveDweller.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, true);
        caveDweller.currentRoll = Roll.CHASE;
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
        return Utils.isValidPlayer(pendingTarget);
    }

    @Override
    public void tick() {
        super.tick();
    }
}
