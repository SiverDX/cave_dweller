package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

public class CaveDwellerBreakInvisGoal extends Goal {
    private final CaveDwellerEntity mob;

    private Player pendingTarget;

    public CaveDwellerBreakInvisGoal(final CaveDwellerEntity mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        pendingTarget = mob.level.getNearestPlayer(mob, 200.0);
        // Player is not looking
        return mob.isInvisible() && (!inPlayerLineOfSight() || !mob.isLookingAtMe(pendingTarget));
    }

    @Override
    public void start() {
        super.start();
        mob.setInvisible(false);
    }

    private boolean inPlayerLineOfSight() {
        return pendingTarget != null && pendingTarget.hasLineOfSight(mob);
    }
}