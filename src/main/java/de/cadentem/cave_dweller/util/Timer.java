package de.cadentem.cave_dweller.util;

import de.cadentem.cave_dweller.CaveDweller;
import de.cadentem.cave_dweller.config.ServerConfig;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

public class Timer {
    @Nullable
    public Entity currentVictim;
    public int currentSpawn;
    public int currentNoise;
    public int targetSpawn;
    public int targetNoise;

    public Timer() {
        resetSpawnTimer();
        resetNoiseTimer();
    }

    public Timer(@Nullable final Entity currentVictim) {
        super();
        this.currentVictim = currentVictim;
    }

    public boolean isSpawnTimerReached() {
        if (Utils.isOnSurface(currentVictim)) {
            return currentSpawn >= (int) (targetSpawn * ServerConfig.SURFACE_TIMER_MULTIPLIER.get());
        }

        return currentSpawn >= targetSpawn;
    }

    public boolean isNoiseTimerReached() {
        if (Utils.isOnSurface(currentVictim)) {
            return currentNoise >= (int) (targetNoise * ServerConfig.SURFACE_TIMER_MULTIPLIER.get());
        }

        return currentNoise >= targetNoise;
    }

    public void resetNoiseTimer() {
        int min = ServerConfig.RESET_NOISE_MIN.get();
        int max = ServerConfig.RESET_NOISE_MAX.get();

        if (max < min) {
            int temp = min;
            min = max;
            max = temp;

            CaveDweller.LOG.error("Configuration for `RESET_NOISE` was wrong - max [{}] was smaller than min [{}] - values have been switched to prevent a crash", max, min);
        }

        int noiseTimer = CaveDweller.RANDOM.nextInt(Utils.secondsToTicks(min), Utils.secondsToTicks(max + 1));

        currentNoise = 0;
        targetNoise = noiseTimer;
    }

    public void resetSpawnTimer() {
        int spawnTimer;

        if (CaveDweller.RANDOM.nextDouble() <= ServerConfig.CAN_SPAWN_COOLDOWN_CHANCE.get()) {
            spawnTimer = Utils.secondsToTicks(ServerConfig.CAN_SPAWN_COOLDOWN.get());
        } else {
            int min = ServerConfig.CAN_SPAWN_MIN.get();
            int max = ServerConfig.CAN_SPAWN_MAX.get();

            if (max < min) {
                int temp = min;
                min = max;
                max = temp;

                CaveDweller.LOG.error("Configuration for `RESET_CALM` was wrong - max [{}] was smaller than min [{}] - values have been switched to prevent a crash", max, min);
            }

            spawnTimer = CaveDweller.RANDOM.nextInt(Utils.secondsToTicks(min), Utils.secondsToTicks(max + 1));
        }

        currentSpawn = 0;
        targetSpawn = spawnTimer;
    }

    @Override
    public String toString() {
        String name = currentVictim != null ? currentVictim.getName().getString() : "NONE";
        return name + " | " + currentSpawn + "/" + targetSpawn + " | " + currentNoise + "/" + targetNoise;
    }
}
