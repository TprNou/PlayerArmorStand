package com.tprno.playerarmorstand.client.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RenderUtil {
    public static void handleGameProfileAsync(String input, Consumer<GameProfile> postAction) {
        ResolvableProfile component = createProfileComponent(input);
        component.resolve()
            .thenApplyAsync(result -> {
                GameProfile profile = result.gameProfile();
                postAction.accept(profile);
                return profile;
            })
            .exceptionally(ex -> null);
    }

    private static ResolvableProfile createProfileComponent(String input) {
        try {
            UUID uuid = UUID.fromString(input);
            return new ResolvableProfile(Optional.empty(), Optional.of(uuid), new PropertyMap());
        } catch (IllegalArgumentException e) {
            return new ResolvableProfile(Optional.of(input), Optional.empty(), new PropertyMap());
        }
    }

    public static Supplier<PlayerSkin> texturesSupplier(GameProfile profile) {
        Minecraft minecraft = Minecraft.getInstance();
        SkinManager skinManager = minecraft.getSkinManager();
        CompletableFuture<PlayerSkin> completableFuture = skinManager.getOrLoad(profile);
        boolean isRemotePlayer = !minecraft.isLocalPlayer(profile.getId());
        PlayerSkin defaultSkin = DefaultPlayerSkin.get(profile);
        
        return () -> {
            PlayerSkin currentSkin = completableFuture.getNow(defaultSkin);
            return isRemotePlayer && !currentSkin.secure() ? defaultSkin : currentSkin;
        };
    }

    private static class ResolvableProfile {
        private final Optional<String> name;
        private final Optional<UUID> uuid;
        private final PropertyMap properties;

        public ResolvableProfile(Optional<String> name, Optional<UUID> uuid, PropertyMap properties) {
            this.name = name;
            this.uuid = uuid;
            this.properties = properties;
        }

        public CompletableFuture<ProfileResult> resolve() {
            // 实现解析逻辑
            return CompletableFuture.completedFuture(new ProfileResult(new GameProfile(uuid.orElse(null), name.orElse(null)), properties));
        }
    }

    private static class ProfileResult {
        private final GameProfile gameProfile;
        private final PropertyMap properties;

        public ProfileResult(GameProfile gameProfile, PropertyMap properties) {
            this.gameProfile = gameProfile;
            this.properties = properties;
        }

        public GameProfile gameProfile() {
            return gameProfile;
        }
    }

    private static class PropertyMap {}
}