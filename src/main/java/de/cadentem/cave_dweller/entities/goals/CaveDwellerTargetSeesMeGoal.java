package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class CaveDwellerTargetSeesMeGoal extends NearestAttackableTargetGoal<Player> {
    private final CaveDwellerEntity caveDweller;

    private Player pendingTarget;

    public CaveDwellerTargetSeesMeGoal(final CaveDwellerEntity mob) {
        super(mob, Player.class, false);
        this.caveDweller = mob;
    }

    @Override
    public boolean canUse() {
        if (caveDweller.isInvisible()) {
            return false;
        } else {
            setPendingTarget(caveDweller.level().getNearestPlayer(caveDweller, 200.0));

            if (Utils.isValidPlayer(target)) {
                return false;
            } else {
                return inPlayerLineOfSight() && caveDweller.isLookingAtMe(pendingTarget);
            }
        }
    }

    @Override
    public void start() {
        super.target = pendingTarget;
        caveDweller.setTarget(pendingTarget);
        caveDweller.spottedByPlayer = true;
        caveDweller.getEntityData().set(CaveDwellerEntity.SPOTTED_ACCESSOR, true);
        caveDweller.reRoll();
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

    private void setPendingTarget(@Nullable final Player pendingTarget) {
        this.pendingTarget = pendingTarget;
    }

    private boolean inPlayerLineOfSight() {
        return pendingTarget != null && pendingTarget.hasLineOfSight(caveDweller);
    }
}
