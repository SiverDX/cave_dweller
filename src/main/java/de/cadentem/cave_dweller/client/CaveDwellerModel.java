 package de.cadentem.cave_dweller.client;

import de.cadentem.cave_dweller.CaveDweller;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

 public class CaveDwellerModel extends GeoModel<CaveDwellerEntity> {
    @Override
    public ResourceLocation getModelResource(final CaveDwellerEntity ignored) {
        return new ResourceLocation(CaveDweller.MODID, "geo/cave_dweller.geo" + Utils.getTextureAppend() + ".json");
    }

    @Override
    public ResourceLocation getTextureResource(final CaveDwellerEntity ignored) {
        return new ResourceLocation(CaveDweller.MODID, "textures/entity/cave_dweller_texture" + Utils.getTextureAppend() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(final CaveDwellerEntity ignored) {
        return new ResourceLocation(CaveDweller.MODID, "animations/cave_dweller.animation.json");
    }

     @Override
     public void setCustomAnimations(final CaveDwellerEntity animatable, long instanceId, final AnimationState<CaveDwellerEntity> animationState) {
         CoreGeoBone head = getAnimationProcessor().getBone("head");

         if (head != null) {
             EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
             head.setRotX(entityData.headPitch() * ((float) (Math.PI / 180.0)));
             head.setRotY(entityData.netHeadYaw() * (float) (Math.PI / 180.0));
         }

         super.setCustomAnimations(animatable, instanceId, animationState);
     }
}