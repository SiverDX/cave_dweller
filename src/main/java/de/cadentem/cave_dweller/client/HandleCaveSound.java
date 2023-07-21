package de.cadentem.cave_dweller.client;

import de.cadentem.cave_dweller.CaveDweller;
import de.cadentem.cave_dweller.network.CaveSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

/** Proxy class to avoid loading client code on the server-side */
public class HandleCaveSound {
    public static void handle(final CaveSound packet) {
        IForgeRegistry<SoundEvent> soundEvents = ForgeRegistries.SOUND_EVENTS;

        if (soundEvents == null) {
            CaveDweller.LOG.error("Forge Sound registry was null while handling packet");
            return;
        }

        SoundEvent soundEvent = soundEvents.getValue(packet.soundResource);

        if (soundEvent == null) {
            CaveDweller.LOG.error("Sound Event [" + packet.soundResource + "] was null while handling packet");
            return;
        }

        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(soundEvent, SoundSource.AMBIENT, 2.0F, 1.0F, RandomSource.create(), packet.playerPosition));
    }
}
