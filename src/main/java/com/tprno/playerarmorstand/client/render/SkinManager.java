package com.tprno.playerarmorstand.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkinManager {
    private static final String SKIN_DIR = "./config/playerarmorstand/skins/";
    private static final String MOJANG_API = "https://mineskin.eu/skin/";
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final Map<String, Boolean> DOWNLOADING = new HashMap<>();
    private static final Map<String, Boolean> DOWNLOAD_FAILED = new HashMap<>();

    static {
        File dir = new File(SKIN_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    public static ResourceLocation getCachedSkin(String playerName) {
        // 优先返回已缓存的资源
        if (CACHE.containsKey(playerName)) {
            if (!Boolean.TRUE.equals(DOWNLOADING.get(playerName))) {
                System.out.println("[SkinManager] Cache hit: " + playerName + ", ResourceLocation: " + CACHE.get(playerName));
            }
            return CACHE.get(playerName);
        }
        // 检查是否正在下载，避免重复下载
        if (Boolean.TRUE.equals(DOWNLOADING.get(playerName))) {
            return null;
        }
        // 检查是否已下载失败，避免反复尝试
        if (Boolean.TRUE.equals(DOWNLOAD_FAILED.get(playerName))) {
            System.err.println("[SkinManager] Previous skin download failed, skip retry: " + playerName);
            return null;
        }
        System.out.println("[SkinManager] Cache miss, preparing to download skin: " + playerName);
        downloadSkinIfNeeded(playerName);
        return null;
    }

    private static void downloadSkinIfNeeded(String playerName) {
        File skinFile = new File(SKIN_DIR + playerName + ".png");
        if (skinFile.exists()) {
            System.out.println("[SkinManager] Local skin file exists, registering directly: " + skinFile.getAbsolutePath());
            registerSkin(skinFile, playerName);
            return;
        }
        DOWNLOADING.put(playerName, true);
        DOWNLOAD_FAILED.remove(playerName);
        System.out.println("[SkinManager] Start async download skin: " + playerName);
        new Thread(() -> {
            int maxRetry = 3;
            int retry = 0;
            boolean success = false;
            Exception lastException = null;
            while (retry < maxRetry && !success) {
                try {
                    URL url = new URL(MOJANG_API + playerName);
                    System.out.println("[SkinManager] Request URL: " + url);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    int responseCode = conn.getResponseCode();
                    System.out.println("[SkinManager] HTTP response code: " + responseCode);
                    if (responseCode == 200) {
                        try (InputStream in = conn.getInputStream();
                             FileOutputStream out = new FileOutputStream(skinFile)) {
                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = in.read(buffer)) > 0) {
                                out.write(buffer, 0, len);
                            }
                        }
                        System.out.println("[SkinManager] Skin downloaded successfully: " + skinFile.getAbsolutePath());
                        Minecraft.getInstance().execute(() -> registerSkin(skinFile, playerName));
                        success = true;
                    } else {
                        lastException = new IOException("HTTP response code: " + responseCode);
                        System.err.println("[SkinManager] Skin download failed, response code: " + responseCode + ", attempt " + (retry+1));
                    }
                } catch (Exception e) {
                    lastException = e;
                    System.err.println("[SkinManager] Exception while downloading skin: " + e.getMessage() + ", attempt " + (retry+1));
                }
                if (!success) {
                    retry++;
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
            DOWNLOADING.remove(playerName);
            if (!success) {
                DOWNLOAD_FAILED.put(playerName, true);
                System.err.println("[SkinManager] Skin download failed: " + playerName + ", reason: " + (lastException != null ? lastException.getMessage() : "Unknown error"));
            }
        }).start();
    }

    private static void registerSkin(File skinFile, String playerName) {
        try {
            System.out.println("[SkinManager] Registering skin: " + skinFile.getAbsolutePath() + ", player: " + playerName);
            // 规范化playerName，全部转小写并替换非法字符
            String safeName = playerName.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
            ResourceLocation location = ResourceLocation.tryParse("playerarmorstand:skins/" + safeName);
            if (location == null) {
                System.err.println("[SkinManager] ResourceLocation parse failed: " + playerName + ", safeName: " + safeName + ", full: playerarmorstand:skins/" + safeName);
                DOWNLOAD_FAILED.put(playerName, true);
                CACHE.remove(playerName);
                return;
            }
            NativeImage image = NativeImage.read(new FileInputStream(skinFile));
            if (image == null || (image.getWidth() != 64 && image.getWidth() != 128)) {
                System.err.println("[SkinManager] Skin image format error: " + skinFile.getAbsolutePath());
                DOWNLOAD_FAILED.put(playerName, true);
                CACHE.remove(playerName);
                return;
            }
            if (image.getWidth() == 64 && image.getHeight() == 32) {
                System.out.println("[SkinManager] Detected old 32px height skin, converting to 64px: " + playerName);
                NativeImage newImage = new NativeImage(64, 64, true);
                for (int y = 0; y < 32; y++) {
                    for (int x = 0; x < 64; x++) {
                        newImage.setPixelRGBA(x, y, image.getPixelRGBA(x, y));
                    }
                }
                for (int y = 32; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        newImage.setPixelRGBA(x, y, 0x00000000);
                    }
                }
                copyRegion(newImage, 4, 16, 4, 32, 4, 4);
                copyRegion(newImage, 8, 16, 8, 32, 4, 4);
                copyRegion(newImage, 0, 20, 0, 36, 4, 12);
                copyRegion(newImage, 4, 20, 4, 36, 4, 12);
                copyRegion(newImage, 8, 20, 8, 36, 4, 12);
                copyRegion(newImage, 12, 20, 12, 36, 4, 12);
                copyRegion(newImage, 4, 16, 20, 48, 4, 4);
                copyRegion(newImage, 8, 16, 24, 48, 4, 4);
                copyRegion(newImage, 0, 20, 16, 52, 4, 12);
                copyRegion(newImage, 4, 20, 20, 52, 4, 12);
                copyRegion(newImage, 8, 20, 24, 52, 4, 12);
                copyRegion(newImage, 12, 20, 28, 52, 4, 12);
                copyRegion(newImage, 44, 16, 44, 32, 4, 4);
                copyRegion(newImage, 48, 16, 48, 32, 4, 4);
                copyRegion(newImage, 40, 20, 40, 36, 4, 12);
                copyRegion(newImage, 44, 20, 44, 36, 4, 12);
                copyRegion(newImage, 48, 20, 48, 36, 4, 12);
                copyRegion(newImage, 52, 20, 52, 36, 4, 12);
                copyRegion(newImage, 44, 16, 36, 48, 4, 4);
                copyRegion(newImage, 48, 16, 40, 48, 4, 4);
                copyRegion(newImage, 40, 20, 32, 52, 4, 12);
                copyRegion(newImage, 44, 20, 36, 52, 4, 12);
                copyRegion(newImage, 48, 20, 40, 52, 4, 12);
                copyRegion(newImage, 52, 20, 44, 52, 4, 12);
                copyRegion(newImage, 40, 0, 32, 32, 8, 8);
                copyRegion(newImage, 48, 0, 40, 32, 8, 8);
                copyRegion(newImage, 32, 8, 32, 40, 8, 8);
                copyRegion(newImage, 40, 8, 40, 40, 8, 8);
                copyRegion(newImage, 48, 8, 48, 40, 8, 8);
                copyRegion(newImage, 56, 8, 56, 40, 8, 8);
                image.close();
                image = newImage;
            }
            DynamicTexture texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(location, texture);
            CACHE.put(playerName, location);
            System.out.println("[SkinManager] Skin registered: " + playerName + ", ResourceLocation: " + location);
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().level != null) {
                    Minecraft.getInstance().level.entitiesForRendering().forEach(entity -> {
                        if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand armorStand && armorStand.hasCustomName() && armorStand.getCustomName() != null) {
                            String name = armorStand.getCustomName().getString();
                            if (name.equals(playerName)) {
                                System.out.println("[SkinManager] Refresh ArmorStand entity: " + name);
                                armorStand.level().sendBlockUpdated(armorStand.blockPosition(), armorStand.level().getBlockState(armorStand.blockPosition()), armorStand.level().getBlockState(armorStand.blockPosition()), 3);
                            }
                        }
                    });
                }
            });
        } catch (IOException e) {
            System.err.println("[SkinManager] Skin registration failed: " + skinFile.getAbsolutePath() + ", reason: " + e.getMessage());
            DOWNLOAD_FAILED.put(playerName, true);
            CACHE.remove(playerName);
        }
    }

    private static void copyRegion(NativeImage img, int srcX, int srcY, int dstX, int dstY, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setPixelRGBA(dstX + x, dstY + y, img.getPixelRGBA(srcX + x, srcY + y));
            }
        }
    }
}