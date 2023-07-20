package de.cadentem.cave_dweller.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class CaveSound {
        private final ResourceLocation soundResource;
        private final BlockPos playerPosition;
        private final float volume, pitch;

        public CaveSound(final ResourceLocation soundResource, final BlockPos playerPosition, float volume, float pitch) {
            this.soundResource = soundResource;
            this.playerPosition = playerPosition;
            this.volume = volume;
            this.pitch = pitch;
        }

        public void encode(final FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(soundResource);
            buffer.writeBlockPos(playerPosition);
            buffer.writeFloat(volume);
            buffer.writeFloat(pitch);
        }

        public static CaveSound decode(final FriendlyByteBuf buffer) {
            return new CaveSound(
                    buffer.readResourceLocation(),
                    buffer.readBlockPos(),
                    buffer.readFloat(),
                    buffer.readFloat()
            );
        }

        public static void handle(final CaveSound packet, final Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                Minecraft.getInstance()
                        .getSoundManager()
                        .play(new SimpleSoundInstance(ForgeRegistries.SOUND_EVENTS.getValue(packet.soundResource), SoundSource.AMBIENT, 2.0F, 1.0F, RandomSource.create(), packet.playerPosition));
            });
            context.setPacketHandled(true);
        }
}
