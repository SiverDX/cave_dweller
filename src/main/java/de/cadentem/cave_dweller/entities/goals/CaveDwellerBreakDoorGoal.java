package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;

import java.util.function.Predicate;

public class CaveDwellerBreakDoorGoal extends BreakDoorGoal {
    public CaveDwellerBreakDoorGoal(final Mob mob, final Predicate<Difficulty> validDifficulties) {
        super(mob, validDifficulties);
    }

    @Override
    protected int getDoorBreakTime() {
        return Utils.secondsToTicks(ServerConfig.BREAK_DOOR_TIME.get());
    }
}
