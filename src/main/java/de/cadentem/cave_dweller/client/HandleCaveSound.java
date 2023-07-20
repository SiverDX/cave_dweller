package de.cadentem.cave_dweller.client;

import de.cadentem.cave_dweller.network.CaveSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;

public class HandleCaveSound {
    public static void handle(final CaveSound packet) {
        Minecraft.getInstance()
                .getSoundManager()
                .play(new SimpleSoundInstance(ForgeRegistries.SOUND_EVENTS.getValue(packet.soundResource), SoundSource.AMBIENT, 2.0F, 1.0F, RandomSource.create(), packet.playerPosition));
    }
}
