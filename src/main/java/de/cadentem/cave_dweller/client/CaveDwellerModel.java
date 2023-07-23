 package de.cadentem.cave_dweller.client;

import de.cadentem.cave_dweller.CaveDweller;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import software.bernie.geckolib3.model.provider.data.EntityModelData;

public class CaveDwellerModel extends AnimatedGeoModel<CaveDwellerEntity> {
    @Override
    public ResourceLocation getModelLocation(final CaveDwellerEntity ignored) {
        return new ResourceLocation(CaveDweller.MODID, "geo/cave_dweller.geo" + Utils.getTextureAppend() + ".json");
    }

    @Override
    public ResourceLocation getTextureLocation(final CaveDwellerEntity ignored) {
        return new ResourceLocation(CaveDweller.MODID, "textures/entity/cave_dweller_texture" + Utils.getTextureAppend() + ".png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(final CaveDwellerEntity ignored) {
        return new ResourceLocation(CaveDweller.MODID, "animations/cave_dweller.animation.json");
    }

    @Override
    public void setCustomAnimations(final CaveDwellerEntity animatable, int instanceId, final AnimationEvent animationEvent) {
        IBone head = getAnimationProcessor().getBone("head");

        if (head != null) {
            EntityModelData entityData = (EntityModelData) animationEvent.getExtraDataOfType(EntityModelData.class).get(0);
            head.setRotationX(entityData.headPitch * ((float) (Math.PI / 180.0)));
            head.setRotationY(entityData.netHeadYaw * (float) (Math.PI / 180.0));
        }

        super.setCustomAnimations(animatable, instanceId, animationEvent);
    }
}