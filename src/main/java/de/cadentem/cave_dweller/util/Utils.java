package de.cadentem.cave_dweller.util;

import de.cadentem.cave_dweller.CaveDweller;
import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class Utils {
    public static int ticksToSeconds(int ticks) {
        return ticks / 20;
    }

    public static int secondsToTicks(int seconds) {
        return seconds * 20;
    }

    public static int minutesToTicks(int minutes) {
        return secondsToTicks(minutes * 60);
    }

    public static String getTextureAppend() {
//        if (ClientConfig.USE_UPDATED_TEXTURES.get()) {
//           return "";
//        }

        return "";
    }

    public static boolean isValidTarget(final Entity entity) {
        if (entity == null) {
            return false;
        }

        if (!entity.isAlive()) {
            return false;
        }

        if (!ServerConfig.TARGET_INVISIBLE.get() && entity.isInvisible()) {
            return false;
        }

        return !(entity instanceof Player player) || (!player.isCreative() && !player.isSpectator());
    }

    public static LivingEntity getValidTarget(@NotNull final CaveDwellerEntity caveDweller) {
        return caveDweller.level.getNearestPlayer(caveDweller.position().x, caveDweller.position().y, caveDweller.position().z, 128, Utils::isValidTarget);
    }

    public static boolean isOnSurface(@Nullable final Entity entity) {
        if (entity == null) {
            return false;
        }

        if (entity.getLevel() instanceof  ServerLevel serverLevel) {
            BlockPos blockPosition = entity.blockPosition();

            if (serverLevel.canSeeSky(blockPosition)) {
                return true;
            }

            Holder<Biome> biome = serverLevel.getBiome(blockPosition);

            if (biome.is(Tags.Biomes.IS_UNDERGROUND)) {
                return false;
            }

            // canSeeSky returns false when you stand below trees etc.
            int baseSkyLightLevel = serverLevel.getBrightness(LightLayer.SKY, blockPosition) - serverLevel.getSkyDarken();

            if (baseSkyLightLevel > 0) {
                return true;
            }
        }

        return false;
    }

    // net.minecraft.util.SpawnUtil
    public static <T extends Mob> Optional<T> trySpawnMob(@NotNull final Entity currentVictim, final EntityType<T> entityType, final MobSpawnType spawnType, final ServerLevel level, final BlockPos blockPosition, int attempts, int xzOffset, int yOffset) {
        BlockPos.MutableBlockPos mutableBlockPosition = blockPosition.mutable();

        for(int i = 0; i < attempts; ++i) {
            int xOffset = Mth.randomBetweenInclusive(level.random, -xzOffset, xzOffset);
            int zOffset = Mth.randomBetweenInclusive(level.random, -xzOffset, xzOffset);
            mutableBlockPosition.setWithOffset(blockPosition, xOffset, yOffset, zOffset);

            if (level.getWorldBorder().isWithinBounds(mutableBlockPosition) && moveToPossibleSpawnPosition(level, yOffset, mutableBlockPosition)) {
                T entity = entityType.create(level, null, null, null, mutableBlockPosition, spawnType, false, false);

                if (entity instanceof CaveDwellerEntity) {
                    if (entity.checkSpawnRules(level, spawnType) && entity.checkSpawnObstruction(level)) {
                        boolean isValidSpawn = entity.level.getNearestPlayer(entity, ServerConfig.SPAWN_DISTANCE.get()) == null;

                        if (isValidSpawn && ServerConfig.CHECK_PATH_TO_SPAWN.get()) {
                            Path path = entity.getNavigation().createPath(currentVictim, 0);
                            isValidSpawn = path != null && path.canReach();
                        }

                        if (isValidSpawn) {
                            // (Unsure) Keeping the `targetPos` makes it try to navigate to the player spot even after stopping the navigation
                            entity.getNavigation().createPath(entity.blockPosition(), 0);
                            entity.getNavigation().stop();
                            level.addFreshEntityWithPassengers(entity);
                            return Optional.of(entity);
                        }
                    }

                    entity.discard();
                }
            }
        }

        CaveDweller.LOG.debug("Cave Dweller could not pass the spawn checks, target: [{}]", currentVictim);

        return Optional.empty();
    }

    private static boolean moveToPossibleSpawnPosition(final ServerLevel level, int attempts, final BlockPos.MutableBlockPos mutableBlockPosition) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = (new BlockPos.MutableBlockPos()).set(mutableBlockPosition);
        BlockState blockstate = level.getBlockState(blockpos$mutableblockpos);

        for (int i = attempts; i >= -attempts; --i) {
            mutableBlockPosition.move(Direction.DOWN);
            blockpos$mutableblockpos.setWithOffset(mutableBlockPosition, Direction.UP);
            BlockState blockStateBelow = level.getBlockState(mutableBlockPosition);

            if (blockstate.getCollisionShape(level, blockpos$mutableblockpos).isEmpty() && Block.isFaceFull(blockStateBelow.getCollisionShape(level, mutableBlockPosition), Direction.UP)) {
                mutableBlockPosition.move(Direction.UP);
                return true;
            }

            blockstate = blockStateBelow;
        }

        return false;
    }
}
