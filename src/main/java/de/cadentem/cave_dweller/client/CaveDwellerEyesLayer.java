package de.cadentem.cave_dweller.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.cadentem.cave_dweller.CaveDweller;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib3.renderers.geo.GeoLayerRenderer;
import software.bernie.geckolib3.renderers.geo.IGeoRenderer;

public class CaveDwellerEyesLayer extends GeoLayerRenderer<CaveDwellerEntity> {
    public static ResourceLocation TEXTURE = new ResourceLocation(CaveDweller.MODID, "textures/entity/cave_dweller_eyes_texture" + Utils.getTextureAppend() + ".png");

    public CaveDwellerEyesLayer(final IGeoRenderer<CaveDwellerEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(final PoseStack matrixStackIn, final MultiBufferSource bufferIn, int packedLightIn, final CaveDwellerEntity entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        packedLightIn = 15728880;
        RenderType eyesRenderType = RenderType.entityCutoutNoCull(TEXTURE);
        VertexConsumer vertexConsumer = bufferIn.getBuffer(eyesRenderType);

        this.getRenderer().render(getEntityModel().getModel(getEntityModel().getModelResource(entityLivingBaseIn)), entityLivingBaseIn, partialTicks, eyesRenderType, matrixStackIn, bufferIn, vertexConsumer, packedLightIn, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}