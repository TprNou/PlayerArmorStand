package com.tprno.playerarmorstand.client.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerArmorStandRenderer extends LivingEntityRenderer<ArmorStand, PlayerModel<ArmorStand>> {
    public static final ResourceLocation STEVE_SKIN = ResourceLocation.tryParse("minecraft:textures/entity/player/wide/steve.png");

    public PlayerArmorStandRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        PlayerModel<ArmorStand> model = this.getModel();
        model.young = false;
        model.crouching = false;
    }

    private final Map<String, Boolean> refreshed = new HashMap<>();

    @Override
    public ResourceLocation getTextureLocation(ArmorStand entity) {
        if (entity.hasCustomName() && entity.getCustomName() != null) {
            String playerName = entity.getCustomName().getString();
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());
            boolean wasRefreshed = Boolean.TRUE.equals(refreshed.get(playerName));
            ResourceLocation skin = SkinManager.getCachedSkin(playerName);
            if (skin != null) {
                if (!wasRefreshed) {
                    System.out.println("[PlayerArmorStandRenderer] Skin cached: " + playerName + ", ResourceLocation: " + skin);
                    refreshed.put(playerName, true);
                    Minecraft.getInstance().execute(() -> {
                        System.out.println("[PlayerArmorStandRenderer] Skin loaded asynchronously, refreshing entity: " + playerName);
                        if (entity.level() != null) {
                            entity.level().sendBlockUpdated(entity.blockPosition(), entity.level().getBlockState(entity.blockPosition()), entity.level().getBlockState(entity.blockPosition()), 3);
                        }
                    });
                }
            } else {
                if (wasRefreshed) {
                    System.out.println("[PlayerArmorStandRenderer] Skin not loaded, returning default texture: " + playerName);
                }
                refreshed.remove(playerName);
            }
            // 判空，防止skin为null导致渲染崩溃
            return skin != null ? skin : STEVE_SKIN;
        }
        return STEVE_SKIN;
    }
    @Override
    protected void setupRotations(ArmorStand entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, float attackAnim) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks, attackAnim);
        // 保持盔甲架的旋转
        Quaternionf rotation = new Quaternionf().rotateY((float)Math.toRadians(entity.getYRot()));
        poseStack.mulPose(rotation);
    }
}