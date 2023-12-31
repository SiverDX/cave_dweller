package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class CaveDwellerTargetSeesMeGoal extends NearestAttackableTargetGoal<Player> {
    private final CaveDwellerEntity caveDweller;

    public CaveDwellerTargetSeesMeGoal(final CaveDwellerEntity mob) {
        super(mob, Player.class, false);
        this.caveDweller = mob;
    }

    @Override
    public boolean canUse() {
        if (caveDweller.isInvisible()) {
            return false;
        } else {
           target = Utils.getValidTarget(caveDweller);

            if (!Utils.isValidTarget(target)) {
                return false;
            } else {
                return caveDweller.isLookingAtMe(target, true);
            }
        }
    }

    @Override
    public void start() {
        caveDweller.setTarget(target);
        caveDweller.setSpotted(true);

        if (target != null) {
            caveDweller.pickRoll(List.of(Roll.CHASE, Roll.STARE, Roll.STARE, Roll.FLEE));
        }

        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public boolean canContinueToUse() {
        return Utils.isValidTarget(target);
    }

    @Override
    public void tick() {
        super.tick();
    }
}
