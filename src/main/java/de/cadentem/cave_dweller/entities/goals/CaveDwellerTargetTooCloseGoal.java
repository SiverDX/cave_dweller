package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

public class CaveDwellerTargetTooCloseGoal extends NearestAttackableTargetGoal<Player> {
    private final CaveDwellerEntity caveDweller;
    private final float distanceThreshold;

    public CaveDwellerTargetTooCloseGoal(final CaveDwellerEntity mob, float distanceThreshold) {
        super(mob, Player.class, false);
        this.caveDweller = mob;
        this.distanceThreshold = distanceThreshold;
    }

    @Override
    public boolean canUse() {
        if (!caveDweller.isInvisible()) {
            LivingEntity target = caveDweller.level.getNearestPlayer(caveDweller, distanceThreshold);

            if (Utils.isValidPlayer(target) && caveDweller.targetIsFacingMe) {
                this.target = target;
                return true;
            }
        }

        return false;
    }

    @Override
    public void start() {
        caveDweller.setAggressive(true);
        caveDweller.currentRoll = Roll.CHASE;
        caveDweller.setTarget(target);
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public boolean canContinueToUse() {
        return Utils.isValidPlayer(target);
    }

    @Override
    public void tick() {
        super.tick();
    }
}
