package de.cadentem.cave_dweller.registry;

import de.cadentem.cave_dweller.CaveDweller;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, CaveDweller.MODID);
    public static final RegistryObject<SoundEvent> CAVENOISE_1 = registerSoundEvent("cavenoise_1");
    public static final RegistryObject<SoundEvent> CAVENOISE_2 = registerSoundEvent("cavenoise_2");
    public static final RegistryObject<SoundEvent> CAVENOISE_3 = registerSoundEvent("cavenoise_3");
    public static final RegistryObject<SoundEvent> CAVENOISE_4 = registerSoundEvent("cavenoise_4");
    public static final RegistryObject<SoundEvent> CHASE_STEP_1 = registerSoundEvent("chase_step_1");
    public static final RegistryObject<SoundEvent> CHASE_STEP_2 = registerSoundEvent("chase_step_2");
    public static final RegistryObject<SoundEvent> CHASE_STEP_3 = registerSoundEvent("chase_step_3");
    public static final RegistryObject<SoundEvent> CHASE_STEP_4 = registerSoundEvent("chase_step_4");
    public static final RegistryObject<SoundEvent> CHASE_1 = registerSoundEvent("chase_1");
    public static final RegistryObject<SoundEvent> CHASE_2 = registerSoundEvent("chase_2");
    public static final RegistryObject<SoundEvent> CHASE_3 = registerSoundEvent("chase_3");
    public static final RegistryObject<SoundEvent> CHASE_4 = registerSoundEvent("chase_4");
    public static final RegistryObject<SoundEvent> FLEE_1 = registerSoundEvent("flee_1");
    public static final RegistryObject<SoundEvent> FLEE_2 = registerSoundEvent("flee_2");
    public static final RegistryObject<SoundEvent> SPOTTED = registerSoundEvent("spotted");
    public static final RegistryObject<SoundEvent> DISAPPEAR = registerSoundEvent("disappear");
    public static final RegistryObject<SoundEvent> DWELLER_HURT_1 = registerSoundEvent("dweller_hurt_1");
    public static final RegistryObject<SoundEvent> DWELLER_HURT_2 = registerSoundEvent("dweller_hurt_2");
    public static final RegistryObject<SoundEvent> DWELLER_HURT_3 = registerSoundEvent("dweller_hurt_3");
    public static final RegistryObject<SoundEvent> DWELLER_HURT_4 = registerSoundEvent("dweller_hurt_4");
    public static final RegistryObject<SoundEvent> DWELLER_DEATH = registerSoundEvent("dweller_death");

    private static RegistryObject<SoundEvent> registerSoundEvent(final String name) {
        ResourceLocation id = new ResourceLocation(CaveDweller.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
